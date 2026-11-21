package io.github.mbsantos97.camerasaovivo.onvif.soap

import okio.ByteString.Companion.toByteString
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Ns {
    const val SOAP12 = "http://www.w3.org/2003/05/soap-envelope"
    const val SOAP11 = "http://schemas.xmlsoap.org/soap/envelope/"
    const val DEVICE = "http://www.onvif.org/ver10/device/wsdl"
    const val MEDIA = "http://www.onvif.org/ver10/media/wsdl"
    const val MEDIA2 = "http://www.onvif.org/ver20/media/wsdl"
    const val PTZ = "http://www.onvif.org/ver20/ptz/wsdl"
    const val EVENTS = "http://www.onvif.org/ver10/events/wsdl"
    const val SEARCH = "http://www.onvif.org/ver10/search/wsdl"
    const val REPLAY = "http://www.onvif.org/ver10/replay/wsdl"
    const val RECORDING = "http://www.onvif.org/ver10/recording/wsdl"
    const val SCHEMA = "http://www.onvif.org/ver10/schema"
    const val WSA = "http://www.w3.org/2005/08/addressing"
    const val WSNT = "http://docs.oasis-open.org/wsn/b-2"
    const val WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
    const val WSU = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
    const val PASSWORD_DIGEST = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest"
    const val BASE64_BINARY = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"
}

object SoapEnvelope {
    fun build(
        body: String,
        header: String? = null,
        soap12: Boolean = true,
    ): String {
        val ns = if (soap12) Ns.SOAP12 else Ns.SOAP11
        val h = if (header.isNullOrEmpty()) "" else "<s:Header>$header</s:Header>"
        return """<?xml version="1.0" encoding="UTF-8"?><s:Envelope xmlns:s="$ns">$h<s:Body>$body</s:Body></s:Envelope>"""
    }
}

/** WS-Security UsernameToken com PasswordDigest = Base64(SHA1(nonce + created + senha)). */
object WsSecurity {
    private val random = SecureRandom()

    fun digest(
        nonce: ByteArray,
        created: String,
        password: String,
    ): String {
        val sha = MessageDigest.getInstance("SHA-1")
        sha.update(nonce)
        sha.update(created.toByteArray(Charsets.UTF_8))
        sha.update(password.toByteArray(Charsets.UTF_8))
        return sha.digest().toByteString().base64()
    }

    fun created(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    /** [nowMillis] já corrigido pelo offset do relógio da câmera. */
    fun header(
        username: String,
        password: String,
        nowMillis: Long,
        nonce: ByteArray = ByteArray(16).also(random::nextBytes),
    ): String {
        val created = created(nowMillis)
        val d = digest(nonce, created, password)
        return "<wsse:Security xmlns:wsse=\"${Ns.WSSE}\" xmlns:wsu=\"${Ns.WSU}\"><wsse:UsernameToken>" +
            "<wsse:Username>${escape(username)}</wsse:Username>" +
            "<wsse:Password Type=\"${Ns.PASSWORD_DIGEST}\">$d</wsse:Password>" +
            "<wsse:Nonce EncodingType=\"${Ns.BASE64_BINARY}\">${nonce.toByteString().base64()}</wsse:Nonce>" +
            "<wsu:Created>$created</wsu:Created></wsse:UsernameToken></wsse:Security>"
    }
}

fun escape(s: String): String =
    s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

data class DigestChallenge(
    val realm: String,
    val nonce: String,
    val qop: String? = null,
    val opaque: String? = null,
    val algorithm: String? = null,
)

/** HTTP Digest (RFC 2617), usado no ONVIF (HTTP 401) e no RTSP DESCRIBE. */
object HttpDigest {
    private val param = Regex("""(\w+)=(?:"([^"]*)"|([^,\s]*))""")
    private val random = SecureRandom()

    fun parseChallenge(header: String): DigestChallenge? {
        if (!header.trimStart().startsWith("Digest", ignoreCase = true)) return null
        val map = param.findAll(header.substringAfter(' ')).associate { it.groupValues[1].lowercase() to (it.groupValues[2].ifEmpty { it.groupValues[3] }) }
        val realm = map["realm"] ?: return null
        val nonce = map["nonce"] ?: return null
        return DigestChallenge(realm, nonce, map["qop"], map["opaque"], map["algorithm"])
    }

    fun md5(s: String): String = MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun response(
        ch: DigestChallenge,
        user: String,
        password: String,
        method: String,
        uri: String,
        nc: String,
        cnonce: String,
    ): String {
        val ha1 = md5("$user:${ch.realm}:$password")
        val ha2 = md5("$method:$uri")
        val qop = ch.qop?.split(',')?.map { it.trim() }?.firstOrNull { it == "auth" }
        return if (qop != null) md5("$ha1:${ch.nonce}:$nc:$cnonce:$qop:$ha2") else md5("$ha1:${ch.nonce}:$ha2")
    }

    fun authorization(
        ch: DigestChallenge,
        user: String,
        password: String,
        method: String,
        uri: String,
        nc: Int = 1,
        cnonce: String = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) },
    ): String {
        val ncs = "%08x".format(nc)
        val resp = response(ch, user, password, method, uri, ncs, cnonce)
        val qop = ch.qop?.split(',')?.map { it.trim() }?.firstOrNull { it == "auth" }
        return buildString {
            append("Digest username=\"$user\", realm=\"${ch.realm}\", nonce=\"${ch.nonce}\", uri=\"$uri\", response=\"$resp\"")
            if (ch.algorithm != null) append(", algorithm=${ch.algorithm}")
            if (ch.opaque != null) append(", opaque=\"${ch.opaque}\"")
            if (qop != null) append(", qop=$qop, nc=$ncs, cnonce=\"$cnonce\"")
        }
    }

    fun basic(
        user: String,
        password: String,
    ): String = "Basic " + "$user:$password".toByteArray(Charsets.UTF_8).toByteString().base64()
}
