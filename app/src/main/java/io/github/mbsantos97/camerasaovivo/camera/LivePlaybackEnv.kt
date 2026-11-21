package io.github.mbsantos97.camerasaovivo.camera

import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.network.IpUtils
import io.github.mbsantos97.camerasaovivo.core.network.SubnetScanner
import io.github.mbsantos97.camerasaovivo.data.config.AppSettings
import io.github.mbsantos97.camerasaovivo.data.config.CachedStreams
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.ConfigRepository
import io.github.mbsantos97.camerasaovivo.onvif.discovery.WsDiscovery
import io.github.mbsantos97.camerasaovivo.player.DecoderBudget

/**
 * Reencontra uma câmera que trocou de IP (DHCP) comparando serial/MAC com os aparelhos da rede.
 */
class CameraLocator(
    private val discovery: WsDiscovery,
    private val onvifFactory: OnvifFactory,
) {
    suspend fun relocate(
        cam: CameraConfig,
        password: String,
    ): String? {
        if (cam.serial == null && cam.mac == null) return null
        val local = IpUtils.localIpv4()
        val candidates =
            (
                discovery.probe().map { it.host to (it.port ?: cam.onvifPort) } +
                    (local?.let { SubnetScanner.scan(it, listOf(cam.onvifPort)) }.orEmpty().map { it.host to cam.onvifPort })
            )
                .distinctBy { it.first }
                .filter { it.first != cam.host }
        for ((host, port) in candidates) {
            val probe = cam.copy(id = "${cam.id}-probe-$host", host = host, onvifPort = port)
            val client = onvifFactory(probe, password)
            val serial = runCatching { client.deviceInformation().serial }.getOrNull()
            val mac = if (serial == null) client.mac() else null
            if ((serial != null && serial == cam.serial) || (mac != null && mac.equals(cam.mac, ignoreCase = true))) {
                AppLogger.i("Locator", "${cam.name} reencontrada pela identidade")
                return host
            }
        }
        return null
    }
}

/** Ambiente real das sessões de reprodução: repositório, resolvedor e orçamento de decodificadores. */
class LivePlaybackEnv(
    private val repo: ConfigRepository,
    private val resolver: StreamResolver,
    private val locator: CameraLocator,
    override val budget: DecoderBudget,
    private val settings: () -> AppSettings,
) : PlaybackEnv {
    override fun cachingMs(): Int = settings().latency.cachingMs

    override fun password(cam: CameraConfig): String = repo.password(cam)

    override suspend fun resolve(
        cam: CameraConfig,
        password: String,
    ): CachedStreams? =
        runCatching { resolver.resolve(cam, password) }
            .onFailure { AppLogger.w("Playback", "${cam.name}: não foi possível resolver os streams", it) }
            .getOrNull()
            ?.also { r ->
                repo.updateCamera(cam.id) {
                    it.copy(
                        streams = r.streams,
                        manufacturer = r.info?.manufacturer ?: it.manufacturer,
                        model = r.info?.model ?: it.model,
                        firmware = r.info?.firmware ?: it.firmware,
                        serial = r.info?.serial ?: it.serial,
                        mac = r.mac ?: it.mac,
                    )
                }
            }?.streams

    override suspend fun learn(
        cameraId: String,
        transform: (CameraConfig) -> CameraConfig,
    ) {
        repo.updateCamera(cameraId, transform)
    }

    override suspend fun relocate(cam: CameraConfig): String? =
        locator.relocate(cam, repo.password(cam))?.also { host ->
            repo.updateCamera(cam.id) { it.copy(host = host) }
        }
}
