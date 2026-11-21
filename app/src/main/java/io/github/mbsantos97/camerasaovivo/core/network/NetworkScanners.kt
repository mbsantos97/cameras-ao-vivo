package io.github.mbsantos97.camerasaovivo.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/** Estado da rede: dispara reconexão de tudo quando a rede volta. */
class NetworkMonitor(
    context: Context,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(true)
    val online: StateFlow<Boolean> = _online

    /** Incrementa a cada vez que a rede volta (os players observam para reconectar). */
    private val _reconnects = MutableStateFlow(0)
    val reconnects: StateFlow<Int> = _reconnects

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val was = _online.value
                _online.value = true
                if (!was) _reconnects.value++
            }

            override fun onLost(network: Network) {
                _online.value = hasNetwork()
            }
        }

    fun start() {
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        _online.value = hasNetwork()
    }

    private fun hasNetwork(): Boolean =
        runCatching {
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }.getOrDefault(true)
}

data class OpenPorts(
    val host: String,
    val ports: Set<Int>,
)

/** Varredura TCP da sub-rede /24 (portas ONVIF 5000 e RTSP 554), 300 ms por tentativa, até 64 em paralelo. */
object SubnetScanner {
    suspend fun scan(
        localIp: String,
        ports: List<Int> = listOf(5000, 554),
        timeoutMs: Int = 800,
        parallelism: Int = 64,
    ): List<OpenPorts> =
        withContext(Dispatchers.IO) {
            val sem = Semaphore(parallelism)
            coroutineScope {
                IpUtils
                    .subnet24(localIp)
                    .map { host ->
                        async {
                            sem.withPermit {
                                val open = ports.filter { isOpen(host, it, timeoutMs) }.toSet()
                                if (open.isEmpty()) null else OpenPorts(host, open)
                            }
                        }
                    }.awaitAll()
                    .filterNotNull()
            }
        }

    fun isOpen(
        host: String,
        port: Int,
        timeoutMs: Int,
    ): Boolean {
        if (!LanOnlyGuard.isAllowed(host)) return false
        return runCatching {
            Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
            true
        }.getOrDefault(false)
    }
}
