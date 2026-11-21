package io.github.mbsantos97.camerasaovivo.onvif.discovery

import android.content.Context
import android.net.wifi.WifiManager
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.network.IpUtils
import io.github.mbsantos97.camerasaovivo.onvif.model.DiscoveredDevice
import io.github.mbsantos97.camerasaovivo.onvif.parser.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLDecoder
import java.util.UUID

/** WS-Discovery: Probe multicast em 239.255.255.250:3702, 3 envios, janela de 4 s. */
class WsDiscovery(
    private val context: Context?,
) {
    suspend fun probe(windowMs: Long = 4_000): List<DiscoveredDevice> =
        withContext(Dispatchers.IO) {
            val wifi = context?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val lock = wifi?.createMulticastLock("aoVivo-discovery")?.apply { setReferenceCounted(false) }
            val found = linkedMapOf<String, DiscoveredDevice>()
            try {
                lock?.acquire()
                MulticastSocket().use { socket ->
                    socket.soTimeout = 300
                    val group = InetAddress.getByName(MULTICAST)
                    val msg = probeMessage().toByteArray()
                    repeat(3) {
                        socket.send(DatagramPacket(msg, msg.size, group, PORT))
                        Thread.sleep(100)
                    }
                    val buf = ByteArray(16 * 1024)
                    val end = System.currentTimeMillis() + windowMs
                    while (System.currentTimeMillis() < end) {
                        val p = DatagramPacket(buf, buf.size)
                        try {
                            socket.receive(p)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        val sender = p.address.hostAddress ?: continue
                        if (!IpUtils.isPrivate(p.address)) continue
                        parseProbeMatches(String(p.data, 0, p.length), sender).forEach { d -> found.putIfAbsent(d.host, d) }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "WS-Discovery falhou", e)
            } finally {
                runCatching { lock?.release() }
            }
            found.values.toList()
        }

    companion object {
        private const val TAG = "WsDiscovery"
        const val MULTICAST = "239.255.255.250"
        const val PORT = 3702

        fun probeMessage(id: String = UUID.randomUUID().toString()): String =
            """<?xml version="1.0" encoding="UTF-8"?>""" +
                """<e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing" """ +
                """xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl">""" +
                """<e:Header><w:MessageID>uuid:$id</w:MessageID><w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>""" +
                """<w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header>""" +
                """<e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>"""

        /** Extrai XAddrs e nome/hardware dos Scopes. O host é o do remetente (XAddr pode vir errado). */
        fun parseProbeMatches(
            xml: String,
            sender: String,
        ): List<DiscoveredDevice> {
            val root = runCatching { Xml.parse(xml) }.getOrNull() ?: return emptyList()
            return root.findAll("ProbeMatch").map { m ->
                val xaddrs = m.textOf("XAddrs")?.split(Regex("\\s+")).orEmpty()
                val best = xaddrs.firstOrNull { runCatching { URI(it).host == sender }.getOrDefault(false) } ?: xaddrs.firstOrNull()
                val port = best?.let { runCatching { URI(it).port }.getOrNull() }?.takeIf { it > 0 }
                val scopes = m.textOf("Scopes")?.split(Regex("\\s+")).orEmpty()

                fun scope(key: String) =
                    scopes.firstOrNull {
                        it.contains("/$key/")
                    }?.substringAfter("/$key/")?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
                DiscoveredDevice(host = sender, port = port, xaddr = best, name = scope("name"), hardware = scope("hardware"), viaDiscovery = true)
            }
        }
    }
}
