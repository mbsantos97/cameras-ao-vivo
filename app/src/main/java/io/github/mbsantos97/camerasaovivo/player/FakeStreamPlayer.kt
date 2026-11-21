package io.github.mbsantos97.camerasaovivo.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import io.github.mbsantos97.camerasaovivo.core.time.Formats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/**
 * Player do modo demonstração: desenha um canvas animado com o nome da câmera e o relógio.
 * Valida toda a navegação sem câmeras reais. [stacked] simula a lente dupla empilhada.
 */
class FakeStreamPlayer(
    private val name: String,
    private val hue: Float,
    private val stacked: Boolean = false,
    private val connectDelayMs: Long = 600,
    private val fps: Int = 15,
) : StreamPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 8)
    private val _state = MutableStateFlow<PlayerEvent>(PlayerEvent.Idle)
    private val _videoSize = MutableStateFlow<VideoSize?>(null)
    private val _firstFrame = MutableStateFlow(false)
    override val events: SharedFlow<PlayerEvent> = _events
    override val state: StateFlow<PlayerEvent> = _state
    override val videoSize: StateFlow<VideoSize?> = _videoSize
    override val firstFrame: StateFlow<Boolean> = _firstFrame

    private val size = if (stacked) VideoSize(1280, 1440) else VideoSize(1280, 720)
    private var output: VideoOutput? = null
    private var surface: Surface? = null
    private var ownsSurface = false
    private var playing = false
    private var renderJob: Job? = null
    private var frames = 0L
    private var paused = false

    private val textPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    private val barPaint = Paint().apply { color = Color.argb(60, 255, 255, 255) }
    private val gridPaint =
        Paint().apply {
            color = Color.argb(40, 255, 255, 255)
            strokeWidth = 2f
        }

    override fun attach(output: VideoOutput) {
        this.output = output
        when (output) {
            is VideoOutput.Texture -> {
                val tv = output.view
                tv.surfaceTextureListener =
                    object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            st: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) = useSurface(Surface(st), owns = true)

                        override fun onSurfaceTextureSizeChanged(
                            st: SurfaceTexture,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            dropSurface()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                tv.surfaceTexture?.let { useSurface(Surface(it), owns = true) }
            }
            is VideoOutput.Surface -> {
                val holder = output.view.holder
                holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) = useSurface(h.surface, owns = false)

                        override fun surfaceChanged(
                            h: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(h: SurfaceHolder) = dropSurface()
                    },
                )
                if (holder.surface?.isValid == true) useSurface(holder.surface, owns = false)
            }
        }
    }

    override fun detach(output: VideoOutput) {
        if (this.output != output) return
        if (output is VideoOutput.Texture) output.view.surfaceTextureListener = null
        dropSurface()
        this.output = null
    }

    override fun onOutputSize(
        width: Int,
        height: Int,
    ) = Unit

    @Synchronized
    private fun useSurface(
        s: Surface,
        owns: Boolean,
    ) {
        dropSurface()
        surface = s
        ownsSurface = owns
        if (playing) startRendering()
    }

    @Synchronized
    private fun dropSurface() {
        renderJob?.cancel()
        renderJob = null
        if (ownsSurface) surface?.release()
        surface = null
    }

    override fun play(
        source: StreamSource,
        options: PlayOptions,
    ) {
        playing = true
        paused = false
        _firstFrame.value = false
        emit(PlayerEvent.Connecting)
        startRendering()
    }

    @Synchronized
    private fun startRendering() {
        renderJob?.cancel()
        val s = surface ?: return
        renderJob =
            scope.launch {
                delay(connectDelayMs)
                _videoSize.value = size
                val frameMs = 1000L / fps
                while (isActive && playing) {
                    if (!paused) drawFrame(s)
                    delay(frameMs)
                }
            }
    }

    private fun drawFrame(s: Surface) {
        val canvas =
            try {
                s.lockCanvas(null)
            } catch (_: Exception) {
                return
            } ?: return
        try {
            canvas.save()
            canvas.scale(canvas.width / size.width.toFloat(), canvas.height / size.height.toFloat())
            if (stacked) {
                drawScene(canvas, 0f, size.height / 2f, hue, "$name · Lente 1")
                canvas.translate(0f, size.height / 2f)
                drawScene(canvas, 0f, size.height / 2f, (hue + 40f) % 360f, "$name · Lente 2")
            } else {
                drawScene(canvas, 0f, size.height.toFloat(), hue, name)
            }
            canvas.restore()
        } finally {
            runCatching { s.unlockCanvasAndPost(canvas) }
        }
        frames++
        if (!_firstFrame.value) {
            _firstFrame.value = true
            emit(PlayerEvent.Playing(stats()))
        }
    }

    private fun drawScene(
        c: Canvas,
        top: Float,
        height: Float,
        h: Float,
        label: String,
    ) {
        val w = size.width.toFloat()
        val dark = Color.HSVToColor(floatArrayOf(h, 0.55f, 0.18f))
        val light = Color.HSVToColor(floatArrayOf(h, 0.45f, 0.42f))
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, height, light, dark, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w, height, bg)
        // Grade e "horizonte" para dar noção de recorte e zoom.
        var x = 0f
        while (x < w) {
            c.drawLine(x, 0f, x, height, gridPaint)
            x += w / 8
        }
        c.drawLine(0f, height * 0.62f, w, height * 0.62f, gridPaint)
        val t = (System.currentTimeMillis() % 4000L) / 4000f
        c.drawRect(w * t - w * 0.08f, 0f, w * t, height, barPaint)
        textPaint.textSize = height * 0.075f
        c.drawText(label, w * 0.05f, height * 0.16f, textPaint)
        textPaint.textSize = height * 0.06f
        c.drawText("DEMO  ${Formats.hourMinuteSecond(System.currentTimeMillis())}", w * 0.05f, height * 0.9f, textPaint)
    }

    private fun emit(e: PlayerEvent) {
        _state.value = e
        _events.tryEmit(e)
    }

    override fun stop() {
        playing = false
        renderJob?.cancel()
        renderJob = null
        _firstFrame.value = false
        emit(PlayerEvent.Idle)
    }

    override fun release() {
        stop()
        output?.let { detach(it) }
        scope.cancel()
    }

    override fun stats(): PlayerStats =
        PlayerStats(
            videoSize = _videoSize.value,
            codec = "Demo",
            fps = fps.toFloat(),
            bitrateKbps = 0,
            hardwareDecoding = false,
            displayedFrames = frames,
        )

    override fun snapshot(): Bitmap? = (output as? VideoOutput.Texture)?.view?.bitmap

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
    }

    override val isPaused: Boolean get() = paused
}
