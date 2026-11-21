package io.github.mbsantos97.camerasaovivo.core.network

import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

class BlockedDestinationException(
    host: String,
) : IOException("Destino fora da rede local bloqueado: $host")

/**
 * Garante que o app só converse com a rede local: 10/8, 172.16/12, 192.168/16, 169.254/16,
 * 127/8, fc00::/7 e fe80::/10. Usado no OkHttp (interceptor + DNS) e antes de abrir RTSP/sockets.
 */
object LanOnlyGuard {
    private const val TAG = "LanOnlyGuard"

    /** Resolve [host] (IP literal não consulta DNS) e exige que todos os endereços sejam privados. */
    fun isAllowed(host: String): Boolean {
        IpUtils.isPrivateLiteral(host)?.let { return it }
        return runCatching { InetAddress.getAllByName(host).let { it.isNotEmpty() && it.all(IpUtils::isPrivate) } }.getOrDefault(false)
    }

    @Throws(BlockedDestinationException::class)
    fun check(host: String) {
        if (!isAllowed(host)) {
            AppLogger.w(TAG, "Bloqueado: $host")
            throw BlockedDestinationException(host)
        }
    }

    /** DNS do OkHttp: devolve só endereços privados. */
    val dns: Dns =
        object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val all = Dns.SYSTEM.lookup(hostname)
                val ok = all.filter(IpUtils::isPrivate)
                if (ok.isEmpty()) {
                    AppLogger.w(TAG, "Bloqueado (DNS): $hostname")
                    throw UnknownHostException("Destino fora da rede local bloqueado: $hostname")
                }
                return ok
            }
        }

    val interceptor: Interceptor =
        Interceptor { chain ->
            val host = chain.request().url.host
            // Nomes passam pelo [dns]; IPs literais são checados aqui.
            if (IpUtils.isPrivateLiteral(host) == false) {
                AppLogger.w(TAG, "Bloqueado: $host")
                throw BlockedDestinationException(host)
            }
            val r: Response = chain.proceed(chain.request())
            r
        }
}
