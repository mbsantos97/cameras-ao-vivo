package io.github.mbsantos97.camerasaovivo.onvif

import io.github.mbsantos97.camerasaovivo.camera.StreamResolver
import io.github.mbsantos97.camerasaovivo.core.logging.LogMasker
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.onvif.parser.EventsParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.SearchParser
import io.github.mbsantos97.camerasaovivo.onvif.soap.Requests
import io.github.mbsantos97.camerasaovivo.rtsp.RtspProbe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Teste com as câmeras reais, a partir do PC, lendo cameras.local.json (fora do git).
 * Uso: ./gradlew :app:testDebugUnitTest --tests "*RealCamera*" -PrealCameras=<caminho> -PrealOut=<pasta> -i
 * Imprime o resultado e salva as respostas ONVIF mascaradas (senha, serial, MAC e IP) em realOut.
 */
class RealCameraTest {
    private val http = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()

    @Test
    fun `cameras reais`() =
        runBlocking {
            val path = System.getProperty("realCameras").orEmpty()
            assumeTrue(path.isNotEmpty() && File(path).exists())
            TestXml.install()
            val out = File(System.getProperty("realOut").orEmpty().ifEmpty { "build/real-onvif" })
            val root = Json.parseToJsonElement(File(path).readText()).jsonObject
            root["cameras"]!!.jsonArray.forEachIndexed { idx, el ->
                val o = el.jsonObject
                val ip = o["ip"]!!.jsonPrimitive.content
                val user = o["usuario"]!!.jsonPrimitive.content
                val pwd = o["senha"]!!.jsonPrimitive.content
                LogMasker.register(pwd)
                if (ip.isEmpty()) return@forEachIndexed
                val tag = "cam${idx + 1}"

                fun p(s: String) = println("REAL $tag: ${LogMasker.mask(s).replace(ip, "<ip-$tag>")}")
                val client = OnvifClient(http, ip, 5000, user, pwd)
                runCatching { client.syncClock() }.onSuccess { p("relógio offset=${client.clockOffsetMs / 1000}s") }.onFailure { p("relógio FALHOU: $it") }
                runCatching {
                    client.deviceInformation()
                }.onSuccess {
                    p("aparelho: ${it.manufacturer} | ${it.model} | fw ${it.firmware} | hw ${it.hardwareId}")
                }.onFailure { p("deviceInfo FALHOU: $it") }
                p("auth=${client.authMode}")
                runCatching { client.discoverServices() }.onSuccess { p("serviços: $it") }.onFailure { p("serviços FALHOU: $it") }
                runCatching { client.profiles() }.onSuccess { l ->
                    l.forEach { pr ->
                        p("perfil ${pr.token} '${pr.name}' src=${pr.videoSourceToken} ${pr.encoding} ${pr.width}x${pr.height} ${pr.fps}fps ptz=${pr.hasPtz}")
                    }
                }
                    .onFailure { p("perfis FALHOU: $it") }
                val cam = CameraConfig(id = tag, name = tag, host = ip, username = user)
                val res = runCatching { StreamResolver({ _, pw -> OnvifClient(http, ip, 5000, user, pw) }).resolve(cam, pwd) }
                res.onSuccess { r ->
                    r.streams.sources.forEach { s ->
                        p("fonte main=${s.mainUri} ${s.mainWidth}x${s.mainHeight} sub=${s.subUri} ${s.subWidth}x${s.subHeight} empilhada=${s.stacked}")
                    }
                    p(
                        "ptz=${r.streams.ptzSupported} perfilPtz=${r.streams.ptzProfileToken} presets=${r.streams.presetsSupported} eventos=${r.streams.eventsSupported} profileG=${r.streams.profileG} snapshot=${r.streams.snapshotUri}",
                    )
                    r.streams.sources.flatMap { listOfNotNull(it.mainUri, it.subUri) }.distinct().forEach { uri ->
                        runCatching { RtspProbe.describe(uri, user, pwd) }
                            .onSuccess { d ->
                                p(
                                    "RTSP $uri -> ${d.status} auth=${d.authUsed} vídeo=${d.sdp?.video?.codec} ${d.sdp?.video?.width}x${d.sdp?.video?.height} áudio=${d.sdp?.audio?.codec}/${d.sdp?.audio?.clockRate}",
                                )
                            }
                            .onFailure { p("RTSP $uri FALHOU: $it") }
                    }
                    if (r.streams.profileG) {
                        runCatching { SearchParser.summary(client.authed(client.services.search!!, Requests.getRecordingSummary())) }
                            .onSuccess { p("Profile G resumo: $it") }
                            .onFailure { p("Profile G resumo FALHOU: $it") }
                    }
                    r.streams.eventsXAddr?.let { ev ->
                        runCatching { EventsParser.subscriptionAddress(client.authed(ev, Requests.createPullPointSubscription())) }
                            .onSuccess { p("eventos: assinatura=${it != null}") }
                            .onFailure { p("eventos FALHOU: $it") }
                    }
                }.onFailure { p("resolver FALHOU: $it") }
                for (path2 in listOf("/onvif1", "/onvif2")) {
                    runCatching { RtspProbe.describe("rtsp://$ip:554$path2", user, pwd) }.onSuccess { d ->
                        p("RTSP direto $path2 -> ${d.status} ${d.sdp?.video?.codec}")
                    }
                }
                val dir = File(out, tag).apply { mkdirs() }
                client.rawResponses.forEach { (op, xml) -> File(dir, "$op.xml").writeText(LogMasker.mask(xml).replace(ip, "192.168.0.10${idx + 1}")) }
                p("respostas salvas: ${client.rawResponses.keys.sorted()}")
            }
        }
}
