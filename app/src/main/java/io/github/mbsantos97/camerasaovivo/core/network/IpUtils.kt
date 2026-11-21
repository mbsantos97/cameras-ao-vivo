package io.github.mbsantos97.camerasaovivo.core.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI

object IpUtils {
    private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /** Faixas privadas permitidas pelo LanOnlyGuard. */
    fun isPrivate(addr: InetAddress): Boolean {
        if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress) return true
        if (addr is Inet4Address) {
            val b = addr.address
            val o0 = b[0].toInt() and 0xFF
            val o1 = b[1].toInt() and 0xFF
            return o0 == 10 || (o0 == 172 && o1 in 16..31) || (o0 == 192 && o1 == 168) || (o0 == 169 && o1 == 254) || o0 == 127
        }
        if (addr is Inet6Address) {
            val first = addr.address[0].toInt() and 0xFF
            val second = addr.address[1].toInt() and 0xFF
            return (first and 0xFE) == 0xFC || (first == 0xFE && (second and 0xC0) == 0x80)
        }
        return false
    }

    fun isIpv4Literal(host: String): Boolean = ipv4.matchEntire(host)?.groupValues?.drop(1)?.all { it.toInt() in 0..255 } == true

    fun isIpLiteral(host: String): Boolean =
        isIpv4Literal(host) || (host.contains(':') && host.trim('[', ']').all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '%' })

    /** Verifica um IP literal sem consultar DNS. Devolve null se [host] for um nome. */
    fun isPrivateLiteral(host: String): Boolean? {
        val h = host.trim('[', ']')
        if (!isIpLiteral(h)) return null
        return runCatching { isPrivate(InetAddress.getByName(h)) }.getOrDefault(false)
    }

    /** IPv4 privado desta TV Box (prefere Wi-Fi/Ethernet). */
    fun localIpv4(): String? =
        runCatching {
            NetworkInterface
                .getNetworkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { isPrivate(it) && !it.isLoopbackAddress }
                ?.hostAddress
        }.getOrNull()

    /** Os 254 endereços da sub-rede /24 de [ip], exceto o próprio. */
    fun subnet24(ip: String): List<String> {
        val m = ipv4.matchEntire(ip) ?: return emptyList()
        val prefix = m.groupValues.subList(1, 4).joinToString(".")
        return (1..254).map { "$prefix.$it" }.filter { it != ip }
    }

    /**
     * Reescreve host e porta de uma URL devolvida pela câmera (quirk de firmware barato:
     * 0.0.0.0, IP antigo, porta 80…) para os valores configurados. Mantém caminho e query.
     */
    fun rewriteHostPort(
        url: String,
        host: String,
        port: Int,
    ): String {
        val u = runCatching { URI(url.trim()) }.getOrNull() ?: return url
        val scheme = u.scheme ?: return url
        val path = u.rawPath.orEmpty().ifEmpty { "/" }
        val query = u.rawQuery?.let { "?$it" }.orEmpty()
        val hostPart = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
        return "$scheme://$hostPart:$port$path$query"
    }
}
