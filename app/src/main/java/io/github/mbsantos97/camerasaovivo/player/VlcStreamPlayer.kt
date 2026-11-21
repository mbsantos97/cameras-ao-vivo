package io.github.mbsantos97.camerasaovivo.player

import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.network.LanOnlyGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import java.net.URI

/**
 * Player LibVLC. Um MediaPlayer por quadrante (a instância LibVLC é única).
 * Operações pesadas (play/stop/release) rodam numa fila serial própria, nunca na main thread;
 * vout (views) só na main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VlcStreamPlayer(
    private val provider: LibVlcProvider,
    private val label: String,
) : StreamPlayer {
    private val worker = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + worker)
    private val main = Handler(Looper.getMainLooper())

    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 16)
    private val _state = MutableStateFlow<PlayerEvent>(PlayerEvent.Idle)
    private val _videoSize = MutableStateFlow<VideoSize?>(null)
    private val _firstFrame = MutableStateFlow(false)
    override val events: SharedFlow<PlayerEvent> = _events
    override val state: StateFlow<PlayerEvent> = _state
    override val videoSize: StateFlow<VideoSize?> = _videoSize
    override val firstFrame: StateFlow<Boolean> = _firstFrame

    @Volatile private var mp: MediaPlayer? = null

    @Volatile private var output: VideoOutput? = null

    @Volatile private var outW = 0

    @Volatile private var outH = 0

    @Volatile private var hwDecoding = true

    @Volatile private var lastStats = PlayerStats()

    @Volatile private var paused = false

    private var statsJob: Job? = null
    private var lastDisplayed = 0L
    private var lastStatsAt = 0L
    private var lastReadBytes = 0L

    private val layoutListener =
        IVLCVout.OnNewVideoLayoutListener { _, width, height, visibleWidth, visibleHeight, _, _ ->
            val w = if (visibleWidth > 0) visibleWidth else width
            val h = if (visibleHeight > 0) visibleHeight else height
            if (w > 0 && h > 0) _videoSize.value = VideoSize(w, h)
        }

    private fun player(): MediaPlayer =
        mp ?: MediaPlayer(provider.get()).also { p ->
            p.setEventListener(::onEvent)
            mp = p
        }

    override fun attach(output: VideoOutput) {
        this.output = output
        val p = mp ?: return
        main.post { attachViews(p, output) }
    }

    private fun attachViews(
        p: MediaPlayer,
        out: VideoOutput,
    ) {
        if (output != out) return
        val vout = p.vlcVout
        if (vout.areViewsAttached()) vout.detachViews()
        when (out) {
            is VideoOutput.Texture -> vout.setVideoView(out.view)
            is VideoOutput.Surface -> vout.setVideoView(out.view)
        }
        vout.attachViews(layoutListener)
        if (out.width > 0) applyWindow(p, out.width, out.height)
    }

    override fun detach(output: VideoOutput) {
        if (this.output != output) return
        this.output = null
        val p = mp ?: return
        main.post { runCatching { if (p.vlcVout.areViewsAttached()) p.vlcVout.detachViews() } }
    }

    override fun onOutputSize(
        width: Int,
        height: Int,
    ) {
        outW = width
        outH = height
        val p = mp ?: return
        main.post { applyWindow(p, width, height) }
    }

    /** No modo TextureView o vídeo é esticado para a view inteira; a matriz do TextureView corrige a proporção. */
    private fun applyWindow(
        p: MediaPlayer,
        w: Int,
        h: Int,
    ) {
        if (w <= 0 || h <= 0) return
        p.vlcVout.setWindowSize(w, h)
        if (output is VideoOutput.Texture) {
            p.aspectRatio = "$w:$h"
            p.scale = 0f
        } else {
            p.aspectRatio = null
            p.scale = 0f
        }
    }

    override fun play(
        source: StreamSource,
        options: PlayOptions,
    ) {
        _firstFrame.value = false
        emit(PlayerEvent.Connecting)
        scope.launch {
            try {
                val host = runCatching { URI(source.uri).host }.getOrNull()
                if (host != null && source.uri.contains("://") && !source.uri.startsWith("file:")) LanOnlyGuard.check(host)
                val p = player()
                if (p.isPlaying || p.media != null) p.stop()
                val media = Media(provider.get(), Uri.parse(source.uri))
                hwDecoding = options.hardwareDecoding
                media.setHWDecoderEnabled(options.hardwareDecoding, false)
                media.addOption(":network-caching=${options.networkCachingMs}")
                media.addOption(":clock-jitter=0")
                media.addOption(":clock-synchro=0")
                if (source.transport == Transport.TCP) media.addOption(":rtsp-tcp")
                if (!source.username.isNullOrEmpty()) media.addOption(":rtsp-user=${source.username}")
                if (!source.password.isNullOrEmpty()) media.addOption(":rtsp-pwd=${source.password}")
                if (!options.audio) media.addOption(":no-audio")
                options.startTimeSeconds?.let { media.addOption(":start-time=$it") }
                options.extraOptions.forEach(media::addOption)
                p.media = media
                media.release()
                val out = output
                if (out != null) main.post { attachViews(p, out) }
                lastDisplayed = 0
                lastReadBytes = 0
                lastStatsAt = System.currentTimeMillis()
                paused = false
                p.play()
                startStats()
                AppLogger.d(TAG, "$label: play ${source.uri} hw=${options.hardwareDecoding} transporte=${source.transport}")
            } catch (e: Exception) {
                AppLogger.w(TAG, "$label: falha ao abrir", e)
                emit(PlayerEvent.Error(PlayerErrorReason.NETWORK, e.message ?: "falha"))
            }
        }
    }

    private fun startStats() {
        statsJob?.cancel()
        statsJob =
            scope.launch {
                while (isActive) {
                    delay(STATS_INTERVAL_MS)
                    readStats()
                }
            }
    }

    private fun readStats() {
        val p = mp ?: return
        val s = runCatching { p.media?.stats }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val dt = (now - lastStatsAt).coerceAtLeast(1) / 1000f
        // Com MediaCodec renderizando direto na superfície, "displayed" pode não subir: usa também os decodificados.
        val displayed = maxOf(s.displayedPictures, s.decodedVideo).toLong()
        val fps = ((displayed - lastDisplayed).coerceAtLeast(0) / dt)
        val track = runCatching { p.currentVideoTrack }.getOrNull()
        val stats =
            PlayerStats(
                videoSize = track?.let { VideoSize(it.width, it.height) }?.takeIf { it.width > 0 } ?: _videoSize.value,
                codec = track?.codec,
                fps = fps,
                bitrateKbps = (s.inputBitrate * 8000f).toInt(),
                hardwareDecoding = hwDecoding,
                displayedFrames = displayed,
                lostFrames = s.lostPictures.toLong(),
                inputBytes = s.readBytes.toLong(),
            )
        lastStats = stats
        lastDisplayed = displayed
        lastStatsAt = now
        lastReadBytes = stats.inputBytes
        if (_videoSize.value == null && stats.videoSize != null) _videoSize.value = stats.videoSize
        if (displayed > 0 && !_firstFrame.value) {
            _firstFrame.value = true
            emit(PlayerEvent.Playing(stats))
        }
    }

    private fun onEvent(e: MediaPlayer.Event) {
        when (e.type) {
            MediaPlayer.Event.Opening -> emit(PlayerEvent.Connecting)
            MediaPlayer.Event.Buffering -> if (!_firstFrame.value) emit(PlayerEvent.Buffering(e.buffering))
            MediaPlayer.Event.Vout ->
                if (e.voutCount > 0 && !_firstFrame.value) {
                    // Saída de vídeo criada = o decodificador já entregou imagem.
                    _firstFrame.value = true
                    emit(PlayerEvent.Playing(lastStats))
                }
            MediaPlayer.Event.EncounteredError -> emit(PlayerEvent.Error(PlayerErrorReason.NETWORK, "Erro do LibVLC"))
            MediaPlayer.Event.EndReached -> emit(PlayerEvent.Ended)
            else -> Unit
        }
    }

    private fun emit(e: PlayerEvent) {
        _state.value = e
        _events.tryEmit(e)
    }

    override fun stop() {
        statsJob?.cancel()
        _firstFrame.value = false
        emit(PlayerEvent.Idle)
        scope.launch { runCatching { mp?.stop() } }
    }

    override fun release() {
        statsJob?.cancel()
        val p = mp
        mp = null
        output = null
        main.post {
            runCatching { if (p?.vlcVout?.areViewsAttached() == true) p.vlcVout.detachViews() }
            scope.launch {
                runCatching {
                    p?.setEventListener(null)
                    p?.stop()
                    p?.release()
                }
                scope.cancel()
            }
        }
    }

    override fun stats(): PlayerStats = lastStats

    override fun snapshot(): Bitmap? = (output as? VideoOutput.Texture)?.view?.bitmap

    override fun setVolume(percent: Int) {
        mp?.volume = percent
    }

    override fun pause() {
        paused = true
        mp?.pause()
    }

    override fun resume() {
        paused = false
        mp?.play()
    }

    override fun seekTo(positionMs: Long) {
        mp?.setTime(positionMs, true)
    }

    override fun setRate(rate: Float) {
        mp?.rate = rate
    }

    override val positionMs: Long get() = mp?.time ?: 0

    override val lengthMs: Long get() = mp?.length ?: 0

    override val isPaused: Boolean get() = paused

    private companion object {
        const val TAG = "Player"
        const val STATS_INTERVAL_MS = 1_000L
    }
}
