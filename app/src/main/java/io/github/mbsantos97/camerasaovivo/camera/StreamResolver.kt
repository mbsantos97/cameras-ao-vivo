package io.github.mbsantos97.camerasaovivo.camera

import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.data.config.CachedStreams
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.SourceStreams
import io.github.mbsantos97.camerasaovivo.onvif.OnvifClient
import io.github.mbsantos97.camerasaovivo.onvif.model.DeviceInfo
import io.github.mbsantos97.camerasaovivo.onvif.model.MediaProfile
import io.github.mbsantos97.camerasaovivo.rtsp.RtspProbe
import kotlin.math.abs

/** Detecta a imagem das duas lentes empilhadas na vertical (ex.: 1920x2160, 1280x1440, 640x720). */
object LensLayoutDetector {
    private const val WIDE = 16f / 9f
    private const val TOLERANCE = 0.1f

    fun isStacked(
        width: Int,
        height: Int,
    ): Boolean {
        if (width <= 0 || height <= 0 || height < width) return false
        val halfAspect = width / (height / 2f)
        return abs(halfAspect - WIDE) / WIDE <= TOLERANCE
    }
}

/** Principal = maior resolução, sub = menor, por video source. */
object ProfileSelector {
    data class Pair(
        val videoSource: String?,
        val main: MediaProfile,
        val sub: MediaProfile,
    )

    fun select(profiles: List<MediaProfile>): List<Pair> =
        profiles
            .groupBy { it.videoSourceToken }
            .entries
            .sortedBy { it.key ?: "" }
            .map { (src, list) ->
                val sorted = list.sortedByDescending { it.pixels }
                Pair(src, sorted.first(), sorted.last())
            }
}

data class ResolveResult(
    val streams: CachedStreams,
    val info: DeviceInfo?,
    val mac: String?,
    val viaOnvif: Boolean,
)

typealias OnvifFactory = (CameraConfig, String) -> OnvifClient
typealias RtspDescribe = suspend (url: String, user: String?, password: String?) -> Boolean

/**
 * Descobre os streams de uma câmera: 1) ONVIF GetProfiles + GetStreamUri; 2) caminhos conhecidos
 * validados com RTSP DESCRIBE. O resultado é guardado na configuração (abrir rápido na próxima vez).
 */
class StreamResolver(
    private val onvifFactory: OnvifFactory,
    private val describe: RtspDescribe = { url, u, p -> runCatching { RtspProbe.describe(url, u, p).status == 200 }.getOrDefault(false) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun resolve(
        cam: CameraConfig,
        password: String,
    ): ResolveResult {
        val client = onvifFactory(cam, password)
        val onvif = runCatching { viaOnvif(client, cam) }
        onvif.getOrNull()?.let { if (it.streams.sources.any { s -> s.mainUri != null }) return it }
        onvif.exceptionOrNull()?.let { AppLogger.w(TAG, "${cam.host}: ONVIF falhou, tentando caminhos conhecidos", it) }
        val known = viaKnownPaths(cam, password)
        if (known != null) {
            val base = onvif.getOrNull()?.streams ?: CachedStreams()
            return ResolveResult(base.copy(sources = listOf(known), resolvedAtMillis = now()), onvif.getOrNull()?.info, onvif.getOrNull()?.mac, false)
        }
        throw onvif.exceptionOrNull() ?: IllegalStateException("Nenhum stream encontrado em ${cam.host}")
    }

    private suspend fun viaOnvif(
        client: OnvifClient,
        cam: CameraConfig,
    ): ResolveResult {
        runCatching { client.syncClock() }
        val services = client.discoverServices()
        val info = runCatching { client.deviceInformation() }.getOrNull()
        val mac = client.mac()
        val profiles = client.profiles()
        val pairs = ProfileSelector.select(profiles)
        val sources =
            pairs.map { p ->
                val main = client.streamUri(p.main.token, cam.rtspPort)
                val sub = if (p.sub.token == p.main.token) main else client.streamUri(p.sub.token, cam.rtspPort) ?: main
                SourceStreams(
                    videoSourceToken = p.videoSource,
                    profileToken = p.main.token,
                    mainUri = main,
                    subUri = sub,
                    mainWidth = p.main.width,
                    mainHeight = p.main.height,
                    subWidth = p.sub.width,
                    subHeight = p.sub.height,
                    mainCodec = p.main.encoding,
                    subCodec = p.sub.encoding,
                    stacked = LensLayoutDetector.isStacked(p.main.width, p.main.height),
                )
            }
        val ptzProfile = profiles.firstOrNull { it.hasPtz }?.token ?: profiles.firstOrNull()?.token
        val ptz = services.ptz != null && profiles.any { it.hasPtz }
        val presets = ptz && ptzProfile != null && runCatching { client.presets(ptzProfile) }.isSuccess
        val snapshot = pairs.firstOrNull()?.let { client.snapshotUri(it.main.token) }
        return ResolveResult(
            CachedStreams(
                sources = sources,
                ptzSupported = ptz,
                ptzProfileToken = ptzProfile,
                presetsSupported = presets,
                eventsSupported = services.events != null,
                profileG = services.profileG,
                snapshotUri = snapshot,
                mediaXAddr = services.media,
                ptzXAddr = services.ptz,
                eventsXAddr = services.events,
                searchXAddr = services.search,
                replayXAddr = services.replay,
                recordingXAddr = services.recording,
                resolvedAtMillis = now(),
            ),
            info,
            mac,
            true,
        )
    }

    /** Caminhos típicos de câmeras baratas, em pares (principal, sub). */
    private suspend fun viaKnownPaths(
        cam: CameraConfig,
        password: String,
    ): SourceStreams? {
        val base = "rtsp://${cam.host}:${cam.rtspPort}"
        for ((main, sub) in KNOWN_PATHS) {
            val mainUrl = base + main
            if (!describe(mainUrl, cam.username, password)) continue
            val subUrl = (base + sub).takeIf { describe(it, cam.username, password) } ?: mainUrl
            AppLogger.i(TAG, "${cam.host}: caminho conhecido $main")
            return SourceStreams(mainUri = mainUrl, subUri = subUrl)
        }
        return null
    }

    companion object {
        private const val TAG = "StreamResolver"
        val KNOWN_PATHS =
            listOf(
                "/onvif1" to "/onvif2",
                "/live/ch00_0" to "/live/ch00_1",
                "/cam/realmonitor?channel=1&subtype=0" to "/cam/realmonitor?channel=1&subtype=1",
                "/11" to "/12",
            )
    }
}
