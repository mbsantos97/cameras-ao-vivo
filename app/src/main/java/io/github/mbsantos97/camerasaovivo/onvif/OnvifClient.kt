package io.github.mbsantos97.camerasaovivo.onvif

import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.network.IpUtils
import io.github.mbsantos97.camerasaovivo.onvif.model.DeviceInfo
import io.github.mbsantos97.camerasaovivo.onvif.model.MediaProfile
import io.github.mbsantos97.camerasaovivo.onvif.model.OnvifFault
import io.github.mbsantos97.camerasaovivo.onvif.model.PtzPreset
import io.github.mbsantos97.camerasaovivo.onvif.model.ServiceAddresses
import io.github.mbsantos97.camerasaovivo.onvif.parser.DeviceParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.FaultParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.MediaParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.PtzParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.Xml
import io.github.mbsantos97.camerasaovivo.onvif.parser.XmlNode
import io.github.mbsantos97.camerasaovivo.onvif.soap.HttpDigest
import io.github.mbsantos97.camerasaovivo.onvif.soap.OnvifRequest
import io.github.mbsantos97.camerasaovivo.onvif.soap.Requests
import io.github.mbsantos97.camerasaovivo.onvif.soap.SoapEnvelope
import io.github.mbsantos97.camerasaovivo.onvif.soap.WsSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class OnvifException(
    message: String,
    val fault: OnvifFault? = null,
    val httpCode: Int = 0,
) : IOException(message)

/**
 * Cliente ONVIF de uma câmera. Tolerante a firmware barato: relógio errado, XAddr com IP/porta
 * errados, SOAP 1.1, autenticação por WS-Security, HTTP Digest ou nenhuma.
 */
class OnvifClient(
    private val http: OkHttpClient,
    val host: String,
    val port: Int,
    private val username: String,
    private val password: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class AuthMode { WSSE, DIGEST, NONE }

    val deviceUrl: String get() = "http://${hostPart()}:$port/onvif/device_service"

    @Volatile var clockOffsetMs: Long = 0
        private set

    @Volatile var clockSynced = false
        private set

    @Volatile var authMode: AuthMode = if (password.isEmpty()) AuthMode.NONE else AuthMode.WSSE
        private set

    @Volatile private var soap12 = true

    @Volatile var services = ServiceAddresses()
        private set

    /** Respostas cruas por operação (para "Exportar respostas ONVIF" no Diagnóstico). */
    val rawResponses: MutableMap<String, String> = ConcurrentHashMap()

    private fun hostPart() = if (host.contains(':')) "[$host]" else host

    /** Usa os endereços já resolvidos (cache da configuração), sem chamar GetServices. */
    fun useCachedServices(s: ServiceAddresses) {
        if (services.media == null && services.ptz == null) services = fixAll(s)
    }

    /** Reescreve XAddr devolvido pela câmera para o host:porta configurados. */
    fun fixXAddr(url: String?): String? = url?.takeIf { it.isNotBlank() }?.let { IpUtils.rewriteHostPort(it, host, port) }

    private fun fixAll(s: ServiceAddresses) =
        ServiceAddresses(
            fixXAddr(s.device),
            fixXAddr(s.media),
            fixXAddr(s.media2),
            fixXAddr(s.ptz),
            fixXAddr(s.events),
            fixXAddr(s.search),
            fixXAddr(s.replay),
            fixXAddr(s.recording),
        )

    // ---------- Transporte ----------

    /**
     * Envia uma operação. [idempotent]=false (ContinuousMove) nunca é repetida.
     * Lança [OnvifException] com o Fault quando a câmera recusa.
     */
    suspend fun call(
        url: String,
        req: OnvifRequest,
        idempotent: Boolean = true,
        extraHeader: String? = null,
    ): XmlNode =
        withContext(Dispatchers.IO) {
            var attempt = 0
            var lastError: IOException? = null
            while (attempt <= (if (idempotent) MAX_RETRIES else 0)) {
                if (attempt > 0) delay(RETRY_BACKOFF_MS * attempt)
                try {
                    return@withContext send(url, req, extraHeader, allowAuthFallback = true)
                } catch (e: OnvifException) {
                    throw e
                } catch (e: IOException) {
                    lastError = e
                }
                attempt++
            }
            throw lastError ?: IOException("Falha ao chamar ${req.operation}")
        }

    private fun security(): String? =
        if (authMode == AuthMode.WSSE &&
            username.isNotEmpty()
        ) {
            WsSecurity.header(username, password, clock() + clockOffsetMs)
        } else {
            null
        }

    private fun send(
        url: String,
        req: OnvifRequest,
        extraHeader: String?,
        allowAuthFallback: Boolean,
        digestAuth: String? = null,
    ): XmlNode {
        val header = listOfNotNull(security(), extraHeader).joinToString("").ifEmpty { null }
        val envelope = SoapEnvelope.build(req.body, header, soap12)
        val contentType =
            if (soap12) "application/soap+xml; charset=utf-8; action=\"${req.action}\"" else "text/xml; charset=utf-8"
        val builder =
            Request
                .Builder()
                .url(url)
                .post(envelope.toRequestBody(contentType.toMediaType()))
        // Firmware Yoosee fecha a conexão após cada resposta sem avisar: nada de keep-alive.
        builder.header("Connection", "close")
        if (!soap12) builder.header("SOAPAction", "\"${req.action}\"")
        if (digestAuth != null) builder.header("Authorization", digestAuth)
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body.string()
            val code = resp.code
            // 401 com desafio Digest: refaz com HTTP Digest (sem WS-Security).
            if (code == 401 && allowAuthFallback && username.isNotEmpty()) {
                val challenge = resp.headers("WWW-Authenticate").firstNotNullOfOrNull(HttpDigest::parseChallenge)
                if (challenge != null) {
                    val path = resp.request.url.encodedPath
                    val previous = authMode
                    authMode = AuthMode.DIGEST
                    return try {
                        send(url, req, extraHeader, false, HttpDigest.authorization(challenge, username, password, "POST", path)).also {
                            AppLogger.i(TAG, "$host: autenticação por HTTP Digest")
                        }
                    } catch (e: OnvifException) {
                        authMode = previous
                        throw e
                    }
                }
            }
            val parsed = runCatching { Xml.parse(text) }.getOrNull()
            if ((code == 400 || code == 415) && soap12 && allowAuthFallback && (parsed == null || FaultParser.parse(parsed) == null)) {
                soap12 = false
                AppLogger.i(TAG, "$host: usando SOAP 1.1")
                return send(url, req, extraHeader, false, digestAuth)
            }
            val root = parsed ?: throw OnvifException("Resposta inválida de ${req.operation} (HTTP $code)", httpCode = code)
            val fault = FaultParser.parse(root)
            if (fault != null || code >= 400) {
                throw OnvifException("${req.operation}: ${fault?.reason ?: fault?.subcode ?: "HTTP $code"}", fault, code)
            }
            rawResponses[req.operation] = text
            return root
        }
    }

    // ---------- Device ----------

    /**
     * GetSystemDateAndTime: calcula o offset de relógio usado no WS-Security. Tenta sem autenticação
     * (padrão ONVIF); câmeras Yoosee derrubam a conexão nesse caso, então repete autenticado.
     */
    suspend fun syncClock(): Long? {
        val saved = authMode
        authMode = AuthMode.NONE
        return try {
            val root =
                try {
                    call(deviceUrl, Requests.getSystemDateAndTime())
                } catch (e: IOException) {
                    if (password.isEmpty()) throw e
                    authMode = if (saved == AuthMode.DIGEST) AuthMode.DIGEST else AuthMode.WSSE
                    call(deviceUrl, Requests.getSystemDateAndTime())
                }
            DeviceParser.systemTime(root)?.also {
                clockOffsetMs = it - clock()
                clockSynced = true
            }
        } finally {
            authMode = if (saved == AuthMode.NONE && password.isNotEmpty()) AuthMode.WSSE else saved
        }
    }

    /**
     * Chama com recuperação: se a câmera disser NotAuthorized com WS-Security, ressincroniza o relógio
     * e tenta de novo; se ainda falhar, tenta sem autenticação (há câmeras que aceitam).
     */
    suspend fun authed(
        url: String,
        req: OnvifRequest,
        idempotent: Boolean = true,
        extraHeader: String? = null,
    ): XmlNode =
        try {
            call(url, req, idempotent, extraHeader)
        } catch (e: OnvifException) {
            if (e.fault?.notAuthorized != true && e.httpCode != 401) throw e
            if (authMode == AuthMode.WSSE && !clockSynced) {
                runCatching { syncClock() }
                try {
                    return call(url, req, idempotent, extraHeader)
                } catch (_: OnvifException) {
                }
            }
            val saved = authMode
            authMode = AuthMode.NONE
            try {
                call(url, req, idempotent, extraHeader)
            } catch (e2: OnvifException) {
                authMode = saved
                throw e
            }
        }

    /**
     * Chama no endereço do serviço e, se falhar por rede/HTTP (não por senha), repete no device_service:
     * firmware Yoosee anuncia XAddrs trocados, mas atende qualquer operação no device_service.
     */
    private suspend fun svc(
        url: String,
        req: OnvifRequest,
        idempotent: Boolean = true,
    ): XmlNode =
        try {
            authed(url, req, idempotent)
        } catch (e: IOException) {
            val auth = (e as? OnvifException)?.let { it.fault?.notAuthorized == true || it.httpCode == 401 } == true
            if (url == deviceUrl || auth) throw e
            AppLogger.d(TAG, "$host: ${req.operation} falhou em $url; tentando o device_service")
            authed(deviceUrl, req, idempotent)
        }

    suspend fun deviceInformation(): DeviceInfo = DeviceParser.deviceInfo(authed(deviceUrl, Requests.getDeviceInformation()))

    /** Endereços dos serviços: GetServices e/ou GetCapabilities, reescritos para host:porta configurados. */
    suspend fun discoverServices(): ServiceAddresses {
        val fromServices = runCatching { DeviceParser.services(authed(deviceUrl, Requests.getServices())) }.getOrNull()
        val fromCaps = runCatching { DeviceParser.capabilities(authed(deviceUrl, Requests.getCapabilities())) }.getOrNull()
        if (fromServices == null && fromCaps == null) {
            // Força a exceção real para o diagnóstico.
            DeviceParser.capabilities(authed(deviceUrl, Requests.getCapabilities()))
        }
        val merged = (fromServices ?: ServiceAddresses()).merge(fromCaps ?: ServiceAddresses())
        services = sanitize(fixAll(merged.copy(device = merged.device ?: deviceUrl)))
        return services
    }

    /** Descarta XAddr que aponta para o caminho de outro serviço (quirk Yoosee: lista deslocada). */
    private fun sanitize(s: ServiceAddresses): ServiceAddresses {
        fun ok(
            url: String?,
            own: String,
        ): String? {
            if (url == null) return null
            val path = runCatching { java.net.URI(url).path.orEmpty().lowercase() }.getOrDefault("")
            val foreign = SERVICE_KEYS.filter { it != own }.any { path.contains(it) } && !path.contains(own)
            return if (foreign) deviceUrl else url
        }
        return s.copy(
            media = ok(s.media, "media"),
            media2 = ok(s.media2, "media"),
            ptz = ok(s.ptz, "ptz"),
            events = ok(s.events, "event"),
            search = ok(s.search, "search"),
            replay = ok(s.replay, "replay"),
            recording = ok(s.recording, "recording"),
        )
    }

    suspend fun mac(): String? = runCatching { DeviceParser.mac(authed(deviceUrl, Requests.getNetworkInterfaces())) }.getOrNull()

    // ---------- Media ----------

    private fun mediaUrl() = services.media ?: deviceUrl

    suspend fun profiles(): List<MediaProfile> {
        val m1 = runCatching { MediaParser.profiles(svc(mediaUrl(), Requests.getProfiles())) }
        m1.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        val m2url = services.media2
        if (m2url != null) {
            runCatching { MediaParser.profiles(authed(m2url, Requests.getProfiles2())) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        m1.exceptionOrNull()?.let { throw it }
        return emptyList()
    }

    /** URI RTSP do perfil, com host:porta reescritos para os configurados. */
    suspend fun streamUri(
        profileToken: String,
        rtspPort: Int,
    ): String? {
        val raw =
            runCatching { MediaParser.uri(svc(mediaUrl(), Requests.getStreamUri(profileToken))) }.getOrNull()
                ?: services.media2?.let { u -> runCatching { MediaParser.uri(authed(u, Requests.getStreamUri2(profileToken))) }.getOrNull() }
        return raw?.let { IpUtils.rewriteHostPort(it, host, rtspPort) }
    }

    suspend fun snapshotUri(profileToken: String): String? =
        runCatching { MediaParser.uri(svc(mediaUrl(), Requests.getSnapshotUri(profileToken))) }.getOrNull()?.let { fixXAddr(it) }

    // ---------- PTZ ----------

    private fun ptzUrl() = services.ptz ?: deviceUrl

    suspend fun continuousMove(
        profile: String,
        x: Float,
        y: Float,
        timeout: String?,
    ) {
        svc(ptzUrl(), Requests.continuousMove(profile, x, y, timeout), idempotent = false)
    }

    /** true quando a câmera recusa o Stop (firmware Yoosee derruba a conexão): para com velocidade zero. */
    @Volatile var stopByZeroVelocity = false
        private set

    suspend fun stop(profile: String) {
        if (!stopByZeroVelocity) {
            try {
                svc(ptzUrl(), Requests.stop(profile))
                return
            } catch (e: IOException) {
                if ((e as? OnvifException)?.fault?.notAuthorized == true) throw e
                stopByZeroVelocity = true
                AppLogger.i(TAG, "$host: Stop recusado; parando com ContinuousMove(0, 0)")
            }
        }
        svc(ptzUrl(), Requests.continuousMove(profile, 0f, 0f, null), idempotent = false)
    }

    suspend fun relativeMove(
        profile: String,
        x: Float,
        y: Float,
    ) {
        svc(ptzUrl(), Requests.relativeMove(profile, x, y), idempotent = false)
    }

    suspend fun presets(profile: String): List<PtzPreset> = PtzParser.presets(svc(ptzUrl(), Requests.getPresets(profile)))

    suspend fun gotoPreset(
        profile: String,
        token: String,
    ) {
        svc(ptzUrl(), Requests.gotoPreset(profile, token), idempotent = false)
    }

    suspend fun setPreset(
        profile: String,
        name: String,
        token: String? = null,
    ) {
        svc(ptzUrl(), Requests.setPreset(profile, name, token), idempotent = false)
    }

    /** Chamada leve para "aquecer" a conexão antes do primeiro movimento. */
    suspend fun warmUp(profile: String?) {
        runCatching { if (profile != null && services.ptz != null) svc(ptzUrl(), Requests.getStatus(profile)) else syncClock() }
    }

    private companion object {
        const val TAG = "Onvif"
        const val MAX_RETRIES = 2
        const val RETRY_BACKOFF_MS = 250L
        val SERVICE_KEYS = listOf("media", "ptz", "event", "deviceio", "imaging", "analytics", "search", "replay", "recording")
    }
}
