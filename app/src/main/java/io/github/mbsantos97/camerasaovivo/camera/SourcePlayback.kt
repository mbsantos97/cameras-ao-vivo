package io.github.mbsantos97.camerasaovivo.camera

import androidx.compose.runtime.Immutable
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.data.config.CachedStreams
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.StreamQuality
import io.github.mbsantos97.camerasaovivo.player.DecoderBudget
import io.github.mbsantos97.camerasaovivo.player.NoVideoAction
import io.github.mbsantos97.camerasaovivo.player.PlayOptions
import io.github.mbsantos97.camerasaovivo.player.PlayerEvent
import io.github.mbsantos97.camerasaovivo.player.ReconnectPolicy
import io.github.mbsantos97.camerasaovivo.player.StreamPlayer
import io.github.mbsantos97.camerasaovivo.player.StreamSource
import io.github.mbsantos97.camerasaovivo.player.Transport
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Immutable
data class PlaybackStatus(
    val status: ConnectionStatus = ConnectionStatus.CONNECTING,
    val attempt: Int = 0,
    val videoVisible: Boolean = false,
)

/** Dependências da sessão (injetadas para testar sem rede nem LibVLC). */
interface PlaybackEnv {
    val budget: DecoderBudget

    fun cachingMs(): Int

    fun password(cam: CameraConfig): String

    /** Resolve e persiste os streams; null se falhar. */
    suspend fun resolve(
        cam: CameraConfig,
        password: String,
    ): CachedStreams?

    /** Grava algo aprendido (decoder por software, transporte que funcionou). */
    suspend fun learn(
        cameraId: String,
        transform: (CameraConfig) -> CameraConfig,
    )

    /** Tenta reencontrar a câmera pela identidade (serial/MAC) se o IP mudou. Devolve o novo host. */
    suspend fun relocate(cam: CameraConfig): String? = null

    fun now(): Long = System.currentTimeMillis()
}

/**
 * Mantém uma fonte tocando: escolhe o stream, abre, espera o primeiro frame, vigia com watchdog
 * e reconecta com backoff. Usada pelos quadrantes da grade (substream por padrão).
 */
class SourcePlayback(
    initial: CameraConfig,
    private val sourceIndex: Int,
    val player: StreamPlayer,
    private val env: PlaybackEnv,
    private val scope: CoroutineScope,
    private val quality: (CameraConfig) -> StreamQuality = { it.gridQuality },
    private val audio: Boolean = false,
) {
    @Volatile var camera: CameraConfig = initial
        private set

    private val _status = MutableStateFlow(PlaybackStatus())
    val status: StateFlow<PlaybackStatus> = _status

    private var job: Job? = null
    private var tcpOverride = false

    val running: Boolean get() = job?.isActive == true

    fun start() {
        if (running) return
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        player.stop()
        _status.value = _status.value.copy(status = ConnectionStatus.CONNECTING, attempt = 0, videoVisible = false)
    }

    fun restart() {
        stop()
        start()
    }

    /** Atualiza a configuração; reinicia só se algo que afeta a conexão mudou. */
    fun update(cam: CameraConfig) {
        val old = camera
        camera = cam
        if (running && connectionKey(old) != connectionKey(cam)) restart()
    }

    private fun connectionKey(c: CameraConfig) =
        listOf(
            c.host,
            c.rtspPort,
            c.onvifPort,
            c.username,
            c.passwordEnc,
            c.transport,
            quality(c),
            c.forceSoftwareDecode,
            c.streams?.sources?.getOrNull(sourceIndex)?.let { it.mainUri to it.subUri },
        )

    private fun pickUri(cam: CameraConfig): String? {
        val s = cam.streams?.sources?.getOrNull(sourceIndex) ?: return null
        return if (quality(cam) == StreamQuality.SUB) s.subUri ?: s.mainUri else s.mainUri ?: s.subUri
    }

    private suspend fun loop() {
        var attempt = 0
        var failures = 0
        while (scope.isActive) {
            val cam = camera
            if (cam.demo) {
                player.play(StreamSource("demo://${cam.id}"))
                player.firstFrame.first { it }
                _status.value = PlaybackStatus(ConnectionStatus.ONLINE, 0, true)
                return
            }
            val password = env.password(cam)
            var uri = pickUri(cam)
            if (uri == null || failures >= RESOLVE_AFTER_FAILURES) {
                env.resolve(cam, password)?.let { s -> camera = camera.copy(streams = s) }
                uri = pickUri(camera)
            }
            if (failures >= RELOCATE_AFTER_FAILURES) {
                env.relocate(camera)?.let { host ->
                    AppLogger.i(TAG, "${cam.name}: câmera encontrada em novo IP")
                    camera = camera.copy(host = host)
                    env.resolve(camera, password)?.let { s -> camera = camera.copy(streams = s) }
                    uri = pickUri(camera)
                }
            }
            val ok = uri != null && playOnce(camera, uri, password)
            if (ok) {
                attempt = 0
                failures = 0
                continue
            }
            attempt++
            failures++
            _status.value = PlaybackStatus(ConnectionStatus.OFFLINE, attempt, player.firstFrame.value)
            delay(ReconnectPolicy.delayFor(attempt))
        }
    }

    /**
     * Abre e acompanha uma reprodução. Devolve true quando chegou a tocar e caiu depois
     * (reconectar já), false quando nem chegou a tocar (backoff).
     */
    private suspend fun playOnce(
        cam: CameraConfig,
        uri: String,
        password: String,
    ): Boolean {
        val transport = if (tcpOverride || cam.learnedTransport == Transport.TCP) Transport.TCP else cam.transport
        val hw = !cam.forceSoftwareDecode
        _status.value = _status.value.copy(status = ConnectionStatus.CONNECTING)
        player.play(
            StreamSource(uri, cam.username, password, transport),
            PlayOptions(networkCachingMs = env.cachingMs(), hardwareDecoding = hw, audio = audio),
        )
        val first =
            withTimeoutOrNull(env.budget.firstFrameTimeoutMs) {
                player.firstFrame.first { it }
            }
        if (first == null) {
            val stats = player.stats()
            when (env.budget.onNoVideo(hw, stats.inputBytes, cam.transport, tcpOverride || transport == Transport.TCP)) {
                NoVideoAction.SWITCH_TO_SOFTWARE -> {
                    AppLogger.i(TAG, "${cam.name}: sem vídeo com dados chegando; decodificação por software")
                    env.learn(cam.id) { it.copy(forceSoftwareDecode = true) }
                    camera = camera.copy(forceSoftwareDecode = true)
                }
                NoVideoAction.SWITCH_TO_TCP -> {
                    AppLogger.i(TAG, "${cam.name}: nada chegou por UDP; tentando TCP")
                    tcpOverride = true
                }
                NoVideoAction.RETRY ->
                    if (tcpOverride && cam.transport == Transport.AUTO) {
                        // TCP também não trouxe nada: volta para UDP (há câmeras que não aceitam TCP).
                        AppLogger.i(TAG, "${cam.name}: TCP sem dados; voltando para UDP")
                        tcpOverride = false
                    }
            }
            player.stop()
            return false
        }
        if (transport == Transport.TCP && cam.transport == Transport.AUTO && cam.learnedTransport != Transport.TCP) {
            env.learn(cam.id) { it.copy(learnedTransport = Transport.TCP) }
        }
        _status.value = PlaybackStatus(ConnectionStatus.ONLINE, 0, true)
        // Watchdog: sem frames novos por 10 s, ou erro/fim → reconectar.
        var lastFrames = player.stats().displayedFrames
        var lastFrameAt = env.now()
        while (scope.isActive) {
            delay(WATCH_INTERVAL_MS)
            val st = player.state.value
            if (st is PlayerEvent.Error || st == PlayerEvent.Ended || st == PlayerEvent.Idle) break
            val frames = player.stats().displayedFrames
            if (frames != lastFrames) {
                lastFrames = frames
                lastFrameAt = env.now()
            } else if (env.budget.isStalled(lastFrameAt, env.now())) {
                AppLogger.w(TAG, "${cam.name}: sem frames há ${env.budget.watchdogMs / 1000} s; reconectando")
                break
            }
        }
        player.stop()
        _status.value = PlaybackStatus(ConnectionStatus.CONNECTING, 0, true)
        return true
    }

    private companion object {
        const val TAG = "Playback"
        const val WATCH_INTERVAL_MS = 2_000L
        const val RESOLVE_AFTER_FAILURES = 3
        const val RELOCATE_AFTER_FAILURES = 5
    }
}
