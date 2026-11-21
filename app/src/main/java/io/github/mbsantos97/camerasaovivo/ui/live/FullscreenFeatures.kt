package io.github.mbsantos97.camerasaovivo.ui.live

import androidx.compose.runtime.Immutable
import io.github.mbsantos97.camerasaovivo.camera.ptz.PtzBackend
import io.github.mbsantos97.camerasaovivo.camera.ptz.PtzController
import io.github.mbsantos97.camerasaovivo.camera.ptz.PtzDirection
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.PtzSpeed
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import io.github.mbsantos97.camerasaovivo.onvif.OnvifClient
import io.github.mbsantos97.camerasaovivo.onvif.model.PtzPreset
import io.github.mbsantos97.camerasaovivo.onvif.model.ServiceAddresses
import io.github.mbsantos97.camerasaovivo.player.StreamPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class FullscreenUi(
    val controlsVisible: Boolean = false,
    val zoom: Float = 1f,
    val zoomCenterX: Float = 0.5f,
    val zoomCenterY: Float = 0.5f,
    val info: Boolean = false,
    val audio: Boolean = false,
    val ptz: Boolean = false,
    val presets: Boolean = false,
    val moving: Set<PtzDirection> = emptySet(),
    val speed: PtzSpeed = PtzSpeed.MEDIUM,
    val inverted: Boolean = false,
    val recording: Boolean = false,
)

/** PTZ do lado ONVIF. */
private class OnvifPtzBackend(
    private val client: OnvifClient,
    private val profile: String,
) : PtzBackend {
    override suspend fun continuousMove(
        x: Float,
        y: Float,
        timeout: String?,
    ) = client.continuousMove(profile, x, y, timeout)

    override suspend fun stop() = client.stop(profile)

    override suspend fun relativeMove(
        x: Float,
        y: Float,
    ) = client.relativeMove(profile, x, y)
}

/** PTZ do modo demonstração (só registra no log). */
private object DemoPtzBackend : PtzBackend {
    override suspend fun continuousMove(
        x: Float,
        y: Float,
        timeout: String?,
    ) = AppLogger.d("PTZ", "demo move $x,$y")

    override suspend fun stop() = AppLogger.d("PTZ", "demo stop")

    override suspend fun relativeMove(
        x: Float,
        y: Float,
    ) = AppLogger.d("PTZ", "demo passo $x,$y")
}

/**
 * Recursos da tela cheia: PTZ, presets, zoom digital, áudio, informações, foto e gravação.
 * Separado do LiveViewModel para manter cada parte pequena.
 */
class FullscreenFeatures(
    private val container: AppContainer,
    private val scope: CoroutineScope,
) {
    private val _ui = MutableStateFlow(FullscreenUi())
    val ui: StateFlow<FullscreenUi> = _ui

    private var camera: CameraConfig? = null
    private var client: OnvifClient? = null
    private var ptz: PtzController? = null
    private var movingJob: Job? = null
    private var hideJob: Job? = null

    fun enter(cam: CameraConfig) {
        leave()
        camera = cam
        val st = cam.streams
        val profile = st?.ptzProfileToken
        val hasPtz = st?.ptzSupported == true && (profile != null || cam.demo)
        _ui.value =
            FullscreenUi(
                audio = cam.audioDefault,
                ptz = hasPtz,
                presets = st?.presetsSupported == true,
                speed = cam.ptz.speed,
                inverted = cam.ptz.invertPan && cam.ptz.invertTilt,
                recording = cam.recordLocal,
            )
        if (!hasPtz) return
        val backend: PtzBackend =
            if (cam.demo) {
                DemoPtzBackend
            } else {
                val c = container.onvifFactory(cam, container.configRepository.password(cam))
                c.useCachedServices(ServiceAddresses(media = st?.mediaXAddr, ptz = st?.ptzXAddr, events = st?.eventsXAddr))
                client = c
                // Aquecer a conexão: o primeiro movimento não paga o handshake.
                scope.launch(Dispatchers.IO) {
                    if (!c.clockSynced) runCatching { c.syncClock() }
                    c.warmUp(profile)
                }
                OnvifPtzBackend(c, profile!!)
            }
        val controller = PtzController(backend, scope, cam.ptz)
        ptz = controller
        movingJob = scope.launch { controller.moving.collect { m -> _ui.update { it.copy(moving = m) } } }
    }

    fun leave() {
        ptz?.close()
        ptz = null
        movingJob?.cancel()
        hideJob?.cancel()
        client = null
        camera = null
        _ui.value = FullscreenUi()
    }

    val ptzLatencyMs: Long get() = ptz?.lastLatencyMs ?: 0

    fun showControls() {
        ptz?.stopNow()
        _ui.update { it.copy(controlsVisible = true) }
        touch()
    }

    fun hideControls() {
        hideJob?.cancel()
        _ui.update { it.copy(controlsVisible = false) }
    }

    /** Reinicia o temporizador de 5 s da barra. */
    fun touch() {
        hideJob?.cancel()
        hideJob =
            scope.launch {
                delay(CONTROLS_HIDE_MS)
                _ui.update { it.copy(controlsVisible = false) }
            }
    }

    fun ptzPress(dir: PtzDirection) = ptz?.press(dir)

    fun ptzRelease(dir: PtzDirection) = ptz?.release(dir)

    fun ptzStop() = ptz?.stopNow()

    fun toggleInfo() = _ui.update { it.copy(info = !it.info) }

    fun setAudio(
        on: Boolean,
        player: StreamPlayer?,
    ) {
        player?.setVolume(if (on) 100 else 0)
        _ui.update { it.copy(audio = on) }
    }

    fun zoom(delta: Float) =
        _ui.update {
            val z = (it.zoom + delta).coerceIn(1f, MAX_ZOOM)
            if (z == 1f) it.copy(zoom = 1f, zoomCenterX = 0.5f, zoomCenterY = 0.5f) else it.copy(zoom = z)
        }

    /** Com zoom ativo, o D-pad move a área visível. */
    fun pan(dir: PtzDirection) =
        _ui.update {
            val step = PAN_STEP / it.zoom
            it.copy(
                zoomCenterX = (it.zoomCenterX + dir.dx * step).coerceIn(0f, 1f),
                zoomCenterY = (it.zoomCenterY - dir.dy * step).coerceIn(0f, 1f),
            )
        }

    fun cycleSpeed() {
        val cam = camera ?: return
        val next = PtzSpeed.entries[(_ui.value.speed.ordinal + 1) % PtzSpeed.entries.size]
        _ui.update { it.copy(speed = next) }
        ptz?.let { it.settings = it.settings.copy(speed = next) }
        persist(cam) { it.copy(ptz = it.ptz.copy(speed = next)) }
    }

    fun toggleInvert() {
        val cam = camera ?: return
        val inv = !_ui.value.inverted
        _ui.update { it.copy(inverted = inv) }
        ptz?.let { it.settings = it.settings.copy(invertPan = inv, invertTilt = inv) }
        persist(cam) { it.copy(ptz = it.ptz.copy(invertPan = inv, invertTilt = inv)) }
    }

    fun toggleRecording() {
        val cam = camera ?: return
        val rec = !_ui.value.recording
        _ui.update { it.copy(recording = rec) }
        persist(cam) { it.copy(recordLocal = rec) }
    }

    private fun persist(
        cam: CameraConfig,
        t: (CameraConfig) -> CameraConfig,
    ) {
        if (cam.demo) return
        scope.launch { container.configRepository.updateCamera(cam.id, t) }
    }

    suspend fun presets(): List<PtzPreset> {
        val cam = camera ?: return emptyList()
        if (cam.demo) return listOf(PtzPreset("1", "Portão"), PtzPreset("2", "Garagem"))
        val c = client ?: return emptyList()
        val profile = cam.streams?.ptzProfileToken ?: return emptyList()
        return withContext(Dispatchers.IO) { runCatching { c.presets(profile) }.getOrDefault(emptyList()) }
    }

    suspend fun gotoPreset(token: String): Boolean {
        val cam = camera ?: return false
        if (cam.demo) return true
        val profile = cam.streams?.ptzProfileToken ?: return false
        return withContext(Dispatchers.IO) { runCatching { client?.gotoPreset(profile, token) }.isSuccess }
    }

    /** Preset pela tecla numérica: 1 = primeiro da lista. */
    suspend fun gotoPresetNumber(n: Int): String? {
        val p = presets().getOrNull(n - 1) ?: return null
        return if (gotoPreset(p.token)) p.name ?: "Preset $n" else null
    }

    suspend fun savePreset(name: String): Boolean {
        val cam = camera ?: return false
        if (cam.demo) return true
        val profile = cam.streams?.ptzProfileToken ?: return false
        return withContext(Dispatchers.IO) { runCatching { client?.setPreset(profile, name) }.isSuccess }
    }

    private companion object {
        const val CONTROLS_HIDE_MS = 5_000L
        const val MAX_ZOOM = 4f
        const val PAN_STEP = 0.1f
    }
}
