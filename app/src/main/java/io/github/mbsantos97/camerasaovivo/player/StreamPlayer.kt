package io.github.mbsantos97.camerasaovivo.player

import android.graphics.Bitmap
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class Transport { AUTO, UDP, TCP }

/** Fonte de vídeo. Credenciais vão separadas da URL (opções do LibVLC), nunca embutidas nela. */
@Immutable
data class StreamSource(
    val uri: String,
    val username: String? = null,
    val password: String? = null,
    val transport: Transport = Transport.AUTO,
    val live: Boolean = true,
) {
    override fun toString(): String = "StreamSource(uri=$uri, user=$username, pwd=***, transport=$transport)"
}

@Immutable
data class PlayOptions(
    val networkCachingMs: Int = 500,
    val hardwareDecoding: Boolean = true,
    val audio: Boolean = false,
    /** Início em segundos (gravações). */
    val startTimeSeconds: Double? = null,
    /** Opções extras do LibVLC (ex.: cabeçalhos de replay). */
    val extraOptions: List<String> = emptyList(),
)

@Immutable
data class VideoSize(
    val width: Int,
    val height: Int,
)

@Immutable
data class PlayerStats(
    val videoSize: VideoSize? = null,
    val codec: String? = null,
    val fps: Float = 0f,
    val bitrateKbps: Int = 0,
    val hardwareDecoding: Boolean = true,
    val displayedFrames: Long = 0,
    val lostFrames: Long = 0,
    val inputBytes: Long = 0,
)

enum class PlayerErrorReason { NETWORK, AUTH, DECODER, NOT_FOUND, UNKNOWN }

sealed interface PlayerEvent {
    data object Idle : PlayerEvent

    data object Connecting : PlayerEvent

    data class Playing(
        val stats: PlayerStats,
    ) : PlayerEvent

    data class Buffering(
        val percent: Float,
    ) : PlayerEvent

    data class Error(
        val reason: PlayerErrorReason,
        val message: String,
    ) : PlayerEvent

    data object Ended : PlayerEvent
}

/** Saída de vídeo: TextureView (padrão, com recorte/zoom) ou SurfaceView (modo Desempenho). */
sealed interface VideoOutput {
    val width: Int
    val height: Int

    data class Texture(
        val view: TextureView,
    ) : VideoOutput {
        override val width get() = view.width
        override val height get() = view.height
    }

    data class Surface(
        val view: SurfaceView,
    ) : VideoOutput {
        override val width get() = view.width
        override val height get() = view.height
    }
}

/**
 * Player de stream usado por quadrantes, tela cheia e gravações.
 * Implementações: [VlcStreamPlayer] (LibVLC) e [FakeStreamPlayer] (modo demonstração).
 */
interface StreamPlayer {
    val events: SharedFlow<PlayerEvent>
    val state: StateFlow<PlayerEvent>
    val videoSize: StateFlow<VideoSize?>

    /** true depois que o primeiro frame foi exibido nesta reprodução. */
    val firstFrame: StateFlow<Boolean>

    fun attach(output: VideoOutput)

    fun detach(output: VideoOutput)

    /** Informa o tamanho da área de exibição (necessário para o LibVLC escalar o vídeo). */
    fun onOutputSize(
        width: Int,
        height: Int,
    )

    fun play(
        source: StreamSource,
        options: PlayOptions = PlayOptions(),
    )

    fun stop()

    fun release()

    fun stats(): PlayerStats

    fun snapshot(): Bitmap?

    fun setVolume(percent: Int) {}

    fun pause() {}

    fun resume() {}

    /** Posição em ms (gravações). */
    fun seekTo(positionMs: Long) {}

    fun setRate(rate: Float) {}

    val positionMs: Long get() = 0

    val lengthMs: Long get() = 0

    val isPaused: Boolean get() = false
}
