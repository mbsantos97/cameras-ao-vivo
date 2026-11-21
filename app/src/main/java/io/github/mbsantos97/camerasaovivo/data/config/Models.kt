package io.github.mbsantos97.camerasaovivo.data.config

import io.github.mbsantos97.camerasaovivo.player.RenderMode
import io.github.mbsantos97.camerasaovivo.player.Transport
import io.github.mbsantos97.camerasaovivo.ui.live.GridLayoutMode
import kotlinx.serialization.Serializable

@Serializable
enum class StreamQuality { SUB, MAIN }

@Serializable
enum class PtzMode { CONTINUOUS, STEP }

@Serializable
enum class PtzSpeed(
    val value: Float,
) {
    SLOW(0.3f),
    MEDIUM(0.6f),
    FAST(1.0f),
}

/** Modo de exibição da lente dupla empilhada. */
@Serializable
enum class LensMode { AUTO, DUAL_COLUMN, TOP, BOTTOM, COMPACT }

/** Qual metade da imagem empilhada é a lente motorizada (PTZ). */
@Serializable
enum class MotorizedLens { AUTO, TOP, BOTTOM }

@Serializable
enum class Latency(
    val cachingMs: Int,
) {
    LOW(300),
    NORMAL(500),
    STABLE(1500),
}

@Serializable
enum class RecordingDestination { AUTO_USB, INTERNAL }

@Serializable
data class PtzSettings(
    val invertPan: Boolean = false,
    val invertTilt: Boolean = false,
    val mode: PtzMode = PtzMode.CONTINUOUS,
    val speed: PtzSpeed = PtzSpeed.MEDIUM,
    /** Ativado automaticamente quando a câmera ignora o Stop. */
    val autoStep: Boolean = false,
)

/** Streams de uma fonte de vídeo (video source ONVIF). */
@Serializable
data class SourceStreams(
    val videoSourceToken: String? = null,
    val profileToken: String? = null,
    val mainUri: String? = null,
    val subUri: String? = null,
    val mainWidth: Int = 0,
    val mainHeight: Int = 0,
    val subWidth: Int = 0,
    val subHeight: Int = 0,
    val mainCodec: String? = null,
    val subCodec: String? = null,
    /** Imagem com as duas lentes empilhadas na vertical (caso B da lente dupla). */
    val stacked: Boolean = false,
)

/** Resultado da resolução de streams e capacidades, guardado para abrir rápido na próxima vez. */
@Serializable
data class CachedStreams(
    val sources: List<SourceStreams> = emptyList(),
    val ptzSupported: Boolean = false,
    val ptzProfileToken: String? = null,
    val presetsSupported: Boolean = false,
    val eventsSupported: Boolean = false,
    val profileG: Boolean = false,
    val snapshotUri: String? = null,
    val mediaXAddr: String? = null,
    val ptzXAddr: String? = null,
    val eventsXAddr: String? = null,
    val searchXAddr: String? = null,
    val replayXAddr: String? = null,
    val recordingXAddr: String? = null,
    val resolvedAtMillis: Long = 0,
)

@Serializable
data class CameraConfig(
    val id: String,
    val name: String,
    val host: String,
    val onvifPort: Int = 5000,
    val rtspPort: Int = 554,
    val username: String = "admin",
    /** Senha cifrada (AES-GCM, chave no Android Keystore). Nunca em texto puro. */
    val passwordEnc: String = "",
    val transport: Transport = Transport.AUTO,
    val gridQuality: StreamQuality = StreamQuality.SUB,
    val fullQuality: StreamQuality = StreamQuality.MAIN,
    val ptz: PtzSettings = PtzSettings(),
    val lensMode: LensMode = LensMode.AUTO,
    val motorizedLens: MotorizedLens = MotorizedLens.AUTO,
    val detectedMotorizedLens: MotorizedLens? = null,
    val audioDefault: Boolean = false,
    val order: Int = 0,
    val hidden: Boolean = false,
    val serial: String? = null,
    val mac: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val firmware: String? = null,
    val streams: CachedStreams? = null,
    /** Decodificação por software lembrada (decoder de hardware falhou nesta câmera). */
    val forceSoftwareDecode: Boolean = false,
    /** Transporte que funcionou no modo Auto. */
    val learnedTransport: Transport? = null,
    val recordLocal: Boolean = false,
    val demo: Boolean = false,
) {
    /** Lente motorizada efetiva: configuração manual > detecção do Diagnóstico > Baixo. */
    val effectiveMotorizedLens: MotorizedLens
        get() =
            when (motorizedLens) {
                MotorizedLens.AUTO -> detectedMotorizedLens ?: MotorizedLens.BOTTOM
                else -> motorizedLens
            }
}

@Serializable
data class RecordingSettings(
    val destination: RecordingDestination = RecordingDestination.AUTO_USB,
    val quality: StreamQuality = StreamQuality.SUB,
    val segmentMinutes: Int = 10,
    val maxPercent: Int = 80,
    val minFreeBytesInternal: Long = 1_073_741_824L,
)

@Serializable
data class AppSettings(
    val layout: GridLayoutMode = GridLayoutMode.AUTO,
    val showClock: Boolean = false,
    val tourEnabled: Boolean = false,
    val tourSeconds: Int = 15,
    val renderMode: RenderMode = RenderMode.TEXTURE,
    val latency: Latency = Latency.NORMAL,
    val recording: RecordingSettings = RecordingSettings(),
    val startOnBoot: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val firstRunDone: Boolean = false,
    val demoMode: Boolean = false,
    val perfOverlay: Boolean = false,
)

@Serializable
data class AppConfig(
    val version: Int = CURRENT_VERSION,
    val cameras: List<CameraConfig> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
