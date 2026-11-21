package br.com.aoVivo.mockcamera

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.Base64
import kotlin.concurrent.thread

/**
 * Câmera Yoosee simulada.
 * - ONVIF (SOAP) em http://0.0.0.0:PORT/onvif/device_service, respondendo com as fixtures do app
 *   (app/src/test/resources/onvif), incluindo os quirks (XAddr 0.0.0.0:80, URI com IP antigo).
 * - Confere o WS-Security (PasswordDigest) quando --password é informado.
 * - URIs RTSP apontam para --rtsp-host:--rtsp-port (ex.: MediaMTX do docker-compose).
 * - Responde ao Probe do WS-Discovery em 239.255.255.250:3702.
 *
 * Variantes: yoosee (padrão), dual-stacked, dual-sources, no-ptz, profile-g.
 */
fun main(args: Array<String>) {
    val opts = args.toList().windowed(2, 1).filter { it[0].startsWith("--") }.associate { it[0].removePrefix("--") to it[1] }
    val port = opts["port"]?.toInt() ?: 5000
    val variant = opts["variant"] ?: "yoosee"
    val password = opts["password"]
    val rtspHost = opts["rtsp-host"] ?: localIp()
    val rtspPort = opts["rtsp-port"]?.toInt() ?: 8554
    val dir = File(opts["fixtures"] ?: "app/src/test/resources/onvif")
    require(dir.isDirectory) { "Pasta de fixtures não encontrada: ${dir.absolutePath}" }

    fun fx(name: String) = File(dir, "$name.xml").readText()

    val profiles =
        when (variant) {
            "dual-stacked" -> "profiles_dual_stacked"
            "dual-sources" -> "profiles_dual_sources"
            "no-ptz" -> "profiles_no_ptz"
            else -> "profiles_yoosee"
        }
    val services = if (variant == "profile-g") "services_profile_g" else "services_no_profile_g"
    val capabilities = if (variant == "no-ptz") "capabilities_no_ptz" else "capabilities_yoosee"

    val server = HttpServer.create(InetSocketAddress(port), 16)
    server.createContext("/") { ex ->
        val body = ex.requestBody.readBytes().decodeToString()
        val op = Regex("""<\w+:(\w+)[ >/]""").find(body.substringAfter("Body>"))?.groupValues?.get(1) ?: "?"
        val authOk = password == null || op == "GetSystemDateAndTime" || checkWsse(body, password)
        val (code, xml) =
            if (!authOk) {
                400 to fx("fault_not_authorized")
            } else {
                when (op) {
                    "GetSystemDateAndTime" -> 200 to systemTimeNow()
                    "GetDeviceInformation" -> 200 to fx("device_information")
                    "GetCapabilities" -> 200 to fx(capabilities)
                    "GetServices" -> 200 to fx(services)
                    "GetNetworkInterfaces" -> 200 to fx("network_interfaces")
                    "GetProfiles" -> 200 to fx(profiles)
                    "GetStreamUri" -> {
                        val sub = body.contains("Profile_2") || body.contains("_sub")
                        val path = if (variant == "dual-stacked") (if (sub) "/stacked_sub" else "/stacked") else (if (sub) "/onvif2" else "/onvif1")
                        200 to fx("stream_uri_main").replace("rtsp://192.168.1.120:554/onvif1", "rtsp://$rtspHost:$rtspPort$path")
                    }
                    "GetSnapshotUri" -> 200 to fx("snapshot_uri")
                    "GetPresets" -> 200 to fx("presets")
                    "ContinuousMove", "Stop", "RelativeMove", "GetStatus", "GotoPreset", "SetPreset" -> 200 to fx("ptz_ok")
                    "CreatePullPointSubscription" -> 200 to fx("create_pull_point")
                    "PullMessages" -> {
                        Thread.sleep(2_000)
                        200 to fx("pull_messages_motion")
                    }
                    "GetRecordingSummary" -> if (variant == "profile-g") 200 to fx("recording_summary") else 500 to fx("fault_action_not_supported")
                    "FindRecordings" -> 200 to fx("find_recordings")
                    "GetRecordingSearchResults" -> 200 to fx("recording_search_results")
                    "GetRecordingInformation" -> 200 to fx("recording_information")
                    "FindEvents" -> 200 to fx("find_events")
                    "GetEventSearchResults" -> 200 to fx("event_search_results")
                    "GetReplayUri" -> 200 to fx("replay_uri").replace("rtsp://0.0.0.0:554", "rtsp://$rtspHost:$rtspPort")
                    else -> 500 to fx("fault_action_not_supported")
                }
            }
        println("[mock] $op -> $code")
        val bytes = xml.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/soap+xml; charset=utf-8")
        ex.sendResponseHeaders(code, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }
    server.start()
    println("Câmera simulada '$variant' em http://${localIp()}:$port/onvif/device_service (RTSP em rtsp://$rtspHost:$rtspPort)")
    thread(isDaemon = true) { discoveryResponder(port, fx("probe_match")) }
}

private fun localIp(): String =
    NetworkInterface
        .getNetworkInterfaces()
        .toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it.isSiteLocalAddress && it.address.size == 4 }
        ?.hostAddress ?: "127.0.0.1"

private fun systemTimeNow(): String {
    val c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))

    fun t(
        tag: String,
        v: Int,
    ) = "<tt:$tag>$v</tt:$tag>"
    return """<?xml version="1.0" encoding="UTF-8"?><s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:tt="http://www.onvif.org/ver10/schema" xmlns:tds="http://www.onvif.org/ver10/device/wsdl"><s:Body><tds:GetSystemDateAndTimeResponse><tds:SystemDateAndTime><tt:UTCDateTime><tt:Time>${t(
        "Hour",
        c.get(11),
    )}${t("Minute", c.get(12))}${t("Second", c.get(13))}</tt:Time><tt:Date>${t("Year", c.get(1))}${t(
        "Month",
        c.get(2) + 1,
    )}${t("Day", c.get(5))}</tt:Date></tt:UTCDateTime></tds:SystemDateAndTime></tds:GetSystemDateAndTimeResponse></s:Body></s:Envelope>"""
}

/** Confere PasswordDigest = Base64(SHA1(nonce + created + senha)). */
private fun checkWsse(
    body: String,
    password: String,
): Boolean {
    fun tag(name: String) = Regex("""<\w*:?$name[^>]*>([^<]*)<""").find(body)?.groupValues?.get(1)
    val digest = tag("Password") ?: return false
    val nonce = tag("Nonce")?.let { Base64.getDecoder().decode(it) } ?: return false
    val created = tag("Created") ?: return false
    val sha = MessageDigest.getInstance("SHA-1")
    sha.update(nonce)
    sha.update(created.toByteArray())
    sha.update(password.toByteArray())
    return Base64.getEncoder().encodeToString(sha.digest()) == digest
}

private fun discoveryResponder(
    port: Int,
    template: String,
) {
    runCatching {
        MulticastSocket(3702).use { s ->
            s.joinGroup(InetAddress.getByName("239.255.255.250"))
            val buf = ByteArray(8192)
            println("[mock] WS-Discovery ouvindo em 239.255.255.250:3702")
            while (true) {
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                val msg = String(p.data, 0, p.length)
                if (!msg.contains("Probe") || msg.contains("ProbeMatches")) continue
                val ip = localIp()
                val reply = template.replace(Regex("<d:XAddrs>[^<]*</d:XAddrs>"), "<d:XAddrs>http://$ip:$port/onvif/device_service</d:XAddrs>").toByteArray()
                s.send(DatagramPacket(reply, reply.size, p.address, p.port))
                println("[mock] ProbeMatch enviado para ${p.address.hostAddress}")
            }
        }
    }.onFailure { println("[mock] WS-Discovery indisponível: ${it.message}") }
}
