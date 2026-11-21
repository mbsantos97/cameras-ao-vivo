package io.github.mbsantos97.camerasaovivo.rtsp

import io.github.mbsantos97.camerasaovivo.core.network.LanOnlyGuard
import io.github.mbsantos97.camerasaovivo.onvif.soap.HttpDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

data class SdpMedia(
    val type: String,
    val codec: String?,
    val clockRate: Int?,
    val control: String?,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Float? = null,
)

data class SdpInfo(
    val media: List<SdpMedia>,
) {
    val video: SdpMedia? get() = media.firstOrNull { it.type == "video" }
    val audio: SdpMedia? get() = media.firstOrNull { it.type == "audio" }
}

/** Parser mínimo de SDP: m=, a=rtpmap, a=control, a=framerate e dimensões (x-dimensions/framesize/cliprect). */
object SdpParser {
    fun parse(sdp: String): SdpInfo {
        val out = mutableListOf<SdpMedia>()
        var cur: SdpMedia? = null
        for (raw in sdp.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("m=") -> {
                    cur?.let(out::add)
                    cur = SdpMedia(line.removePrefix("m=").substringBefore(' '), null, null, null)
                }
                cur == null -> Unit
                line.startsWith("a=rtpmap:") -> {
                    val enc = line.substringAfter(' ', "")
                    cur = cur.copy(codec = enc.substringBefore('/').uppercase(), clockRate = enc.split('/').getOrNull(1)?.toIntOrNull())
                }
                line.startsWith("a=control:") -> cur = cur.copy(control = line.removePrefix("a=control:"))
                line.startsWith("a=framerate:") -> cur = cur.copy(fps = line.removePrefix("a=framerate:").trim().toFloatOrNull())
                line.startsWith("a=x-dimensions:") -> {
                    val (w, h) = line.removePrefix("a=x-dimensions:").split(',').map { it.trim().toIntOrNull() }.let { it.getOrNull(0) to it.getOrNull(1) }
                    cur = cur.copy(width = w, height = h)
                }
                line.startsWith("a=framesize:") -> {
                    val dims = line.substringAfter(' ').split('-').map { it.trim().toIntOrNull() }
                    cur = cur.copy(width = dims.getOrNull(0), height = dims.getOrNull(1))
                }
                line.startsWith("a=cliprect:") -> {
                    val v = line.removePrefix("a=cliprect:").split(',').map { it.trim().toIntOrNull() }
                    if (v.size == 4 && v.all { it != null }) cur = cur.copy(width = v[3]!! - v[1]!!, height = v[2]!! - v[0]!!)
                }
            }
        }
        cur?.let(out::add)
        return SdpInfo(out)
    }
}

data class RtspResult(
    val status: Int,
    val sdp: SdpInfo?,
    val authUsed: String?,
)

/**
 * Sonda RTSP mínima: DESCRIBE com Basic/Digest para validar um caminho e ler os codecs do SDP.
 * Não é um cliente RTP (a reprodução é do LibVLC).
 */
object RtspProbe {
    suspend fun describe(
        url: String,
        user: String?,
        password: String?,
        timeoutMs: Int = 3_000,
    ): RtspResult =
        withContext(Dispatchers.IO) {
            val u = URI(url)
            val host = u.host ?: throw IOException("URL RTSP inválida")
            LanOnlyGuard.check(host)
            val port = if (u.port > 0) u.port else 554
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                s.soTimeout = timeoutMs + 1_000
                val input = BufferedInputStream(s.getInputStream())
                val out = s.getOutputStream()
                var cseq = 1

                fun request(auth: String?): Pair<Int, Map<String, List<String>>> {
                    val sb = StringBuilder()
                    sb.append("DESCRIBE $url RTSP/1.0\r\nCSeq: ${cseq++}\r\nAccept: application/sdp\r\nUser-Agent: CamerasAoVivo\r\n")
                    if (auth != null) sb.append("Authorization: $auth\r\n")
                    sb.append("\r\n")
                    out.write(sb.toString().toByteArray())
                    out.flush()
                    return readResponse(input)
                }
                var (status, headers) = request(null)
                var used: String? = null
                if (status == 401 && !user.isNullOrEmpty()) {
                    val challenges = headers["www-authenticate"].orEmpty()
                    val digest = challenges.firstNotNullOfOrNull(HttpDigest::parseChallenge)
                    val auth =
                        if (digest != null) {
                            used = "Digest"
                            HttpDigest.authorization(digest, user, password.orEmpty(), "DESCRIBE", url)
                        } else {
                            used = "Basic"
                            HttpDigest.basic(user, password.orEmpty())
                        }
                    val r = request(auth)
                    status = r.first
                    headers = r.second
                }
                val body = headers[BODY]?.firstOrNull()
                RtspResult(status, if (status == 200 && body != null) SdpParser.parse(body) else null, used)
            }
        }

    private const val BODY = "\u0000body"

    private fun readLine(i: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val c = i.read()
            if (c < 0) throw IOException("Conexão RTSP encerrada")
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun readResponse(i: InputStream): Pair<Int, Map<String, List<String>>> {
        val status = readLine(i).split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("Resposta RTSP inválida")
        val headers = mutableMapOf<String, MutableList<String>>()
        while (true) {
            val line = readLine(i)
            if (line.isEmpty()) break
            val k = line.substringBefore(':').trim().lowercase()
            headers.getOrPut(k) { mutableListOf() } += line.substringAfter(':').trim()
        }
        val len = headers["content-length"]?.firstOrNull()?.toIntOrNull() ?: 0
        if (len > 0) {
            val buf = ByteArray(len)
            var read = 0
            while (read < len) {
                val n = i.read(buf, read, len - read)
                if (n < 0) break
                read += n
            }
            headers[BODY] = mutableListOf(String(buf, 0, read))
        }
        return status to headers
    }
}
