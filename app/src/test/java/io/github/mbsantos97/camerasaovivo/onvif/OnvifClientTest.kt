package io.github.mbsantos97.camerasaovivo.onvif

import io.github.mbsantos97.camerasaovivo.camera.LensLayoutDetector
import io.github.mbsantos97.camerasaovivo.camera.StreamResolver
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Câmera Yoosee simulada com MockWebServer: responde por operação SOAP, com os quirks reais
 * (XAddr 0.0.0.0:80, URI com IP antigo, Faults, Digest, SOAP 1.1).
 */
class OnvifClientTest {
    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<String>()

    /** Variante da câmera simulada. */
    private var profilesFixture = "profiles_yoosee"
    private var servicesFixture = "services_no_profile_g"
    private var requireDigest = false
    private var only11 = false
    private var rejectWsse = false
    private var dropStop = false
    private var capsFixture = "capabilities_yoosee"

    private val http = OkHttpClient.Builder().connectTimeout(2, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).build()

    @Before
    fun start() {
        TestXml.install()
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val body = request.body?.utf8().orEmpty()
                    val op = Regex("""<\w+:(\w+)[ >/]""").findAll(body.substringAfter("Body>")).firstOrNull()?.groupValues?.get(1) ?: "?"
                    requests += op
                    val ct = request.headers["Content-Type"].orEmpty()
                    if (only11 && ct.startsWith("application/soap+xml")) return MockResponse.Builder().code(415).build()
                    if (requireDigest && request.headers["Authorization"]?.startsWith("Digest") != true) {
                        return MockResponse
                            .Builder()
                            .code(401)
                            .addHeader("WWW-Authenticate", "Digest realm=\"IPC\", qop=\"auth\", nonce=\"n123\"")
                            .build()
                    }
                    val hasWsse = body.contains("UsernameToken")
                    if (op == "Stop" && dropStop) return MockResponse.Builder().onResponseStart(mockwebserver3.SocketEffect.ShutdownConnection).build()
                    if (op != "GetSystemDateAndTime" && rejectWsse && hasWsse) return xml(fixture("fault_not_authorized"), 400)
                    val name =
                        when (op) {
                            "GetSystemDateAndTime" -> "system_date_time"
                            "GetDeviceInformation" -> "device_information"
                            "GetCapabilities" -> capsFixture
                            "GetServices" -> servicesFixture
                            "GetNetworkInterfaces" -> "network_interfaces"
                            "GetProfiles" -> profilesFixture
                            "GetStreamUri" -> if (body.contains("Profile_2") || body.contains("_sub")) "stream_uri_sub" else "stream_uri_main"
                            "GetSnapshotUri" -> "snapshot_uri"
                            "GetPresets" -> "presets"
                            "ContinuousMove", "Stop", "RelativeMove", "GetStatus" -> "ptz_ok"
                            else -> return xml(fixture("fault_action_not_supported"), 500)
                        }
                    return xml(fixture(name), 200)
                }
            }
        server.start()
    }

    private fun xml(
        text: String,
        code: Int,
    ) = MockResponse
        .Builder()
        .code(code)
        .addHeader("Content-Type", "application/soap+xml; charset=utf-8")
        .body(text)
        .build()

    @After
    fun stop() = server.close()

    private fun client(password: String = "segredo") = OnvifClient(http, server.hostName, server.port, "admin", password)

    private fun cam() = CameraConfig(id = "c1", name = "Lâmpada", host = server.hostName, onvifPort = server.port, rtspPort = 554)

    @Test
    fun `sincroniza relogio e calcula offset`() =
        runBlocking {
            val c = OnvifClient(http, server.hostName, server.port, "admin", "x", clock = { 1_795_143_845_000L - 3_600_000L })
            c.syncClock()
            assertEquals(3_600_000L, c.clockOffsetMs)
            assertTrue(c.clockSynced)
        }

    @Test
    fun `XAddr 0_0_0_0 porta 80 e reescrito para o host e porta configurados`() =
        runBlocking {
            val s = client().discoverServices()
            assertEquals("http://${server.hostName}:${server.port}/onvif/media_service", s.media)
            assertEquals("http://${server.hostName}:${server.port}/onvif/ptz_service", s.ptz)
        }

    @Test
    fun `URI RTSP com IP antigo e reescrita`() =
        runBlocking {
            val c = client()
            c.discoverServices()
            assertEquals("rtsp://${server.hostName}:554/onvif1", c.streamUri("Profile_1", 554))
            assertEquals("rtsp://${server.hostName}:8554/onvif2", c.streamUri("Profile_2", 8554))
        }

    @Test
    fun `autenticacao por HTTP Digest quando a camera responde 401`() =
        runBlocking {
            requireDigest = true
            val c = client()
            assertEquals("Yoosee-8177", c.deviceInformation().model)
            assertEquals(OnvifClient.AuthMode.DIGEST, c.authMode)
        }

    @Test
    fun `cai para SOAP 1_1 quando a camera recusa 1_2`() =
        runBlocking {
            only11 = true
            assertEquals("IPC", client().deviceInformation().manufacturer)
        }

    @Test
    fun `camera que recusa WS-Security mas aceita sem autenticacao`() =
        runBlocking {
            rejectWsse = true
            val c = client()
            assertEquals("IPC", c.deviceInformation().manufacturer)
            assertEquals(OnvifClient.AuthMode.NONE, c.authMode)
        }

    @Test
    fun `Fault de acao nao suportada vira OnvifException com o fault`() =
        runBlocking {
            val c = client()
            c.discoverServices()
            try {
                c.call(c.deviceUrl, io.github.mbsantos97.camerasaovivo.onvif.soap.Requests.getRecordingSummary())
                fail("deveria lançar")
            } catch (e: OnvifException) {
                assertTrue(e.fault!!.actionNotSupported)
            }
        }

    @Test
    fun `ContinuousMove nunca e repetido em falha`() =
        runBlocking {
            val c = client()
            c.discoverServices()
            server.close()
            requests.clear()
            runCatching { c.continuousMove("P1", 0.5f, 0f, "PT10S") }
            assertTrue(requests.isEmpty())
        }

    @Test
    fun `resolver monta principal e sub e detecta PTZ e presets`() =
        runBlocking {
            val r = StreamResolver({ _, p -> OnvifClient(http, server.hostName, server.port, "admin", p) }, { _, _, _ -> false }).resolve(cam(), "segredo")
            assertTrue(r.viaOnvif)
            val s = r.streams.sources.single()
            assertEquals("rtsp://${server.hostName}:554/onvif1", s.mainUri)
            assertEquals("rtsp://${server.hostName}:554/onvif2", s.subUri)
            assertEquals(1920, s.mainWidth)
            assertEquals(640, s.subWidth)
            assertFalse(s.stacked)
            assertTrue(r.streams.ptzSupported)
            assertTrue(r.streams.presetsSupported)
            assertTrue(r.streams.eventsSupported)
            assertFalse(r.streams.profileG)
            assertEquals("00:11:22:AA:BB:CC", r.mac)
        }

    @Test
    fun `resolver detecta lente dupla empilhada`() =
        runBlocking {
            profilesFixture = "profiles_dual_stacked"
            val r = StreamResolver({ _, p -> OnvifClient(http, server.hostName, server.port, "admin", p) }).resolve(cam(), "segredo")
            assertTrue(r.streams.sources.single().stacked)
        }

    @Test
    fun `resolver separa dois video sources`() =
        runBlocking {
            profilesFixture = "profiles_dual_sources"
            servicesFixture = "services_profile_g"
            val r = StreamResolver({ _, p -> OnvifClient(http, server.hostName, server.port, "admin", p) }).resolve(cam(), "segredo")
            assertEquals(listOf("VideoSource_1", "VideoSource_2"), r.streams.sources.map { it.videoSourceToken })
            assertEquals("P2_main", r.streams.ptzProfileToken)
        }

    @Test
    fun `sem ONVIF usa caminhos conhecidos validados por DESCRIBE`() =
        runBlocking {
            server.close()
            val tried = mutableListOf<String>()
            val r =
                StreamResolver(
                    { _, p -> OnvifClient(http, "127.0.0.1", 1, "admin", p) },
                    { url, _, _ ->
                        tried += url
                        url.endsWith("/live/ch00_0") || url.endsWith("/live/ch00_1")
                    },
                ).resolve(cam().copy(host = "127.0.0.1"), "x")
            assertFalse(r.viaOnvif)
            assertEquals("rtsp://127.0.0.1:554/live/ch00_0", r.streams.sources.single().mainUri)
            assertEquals("rtsp://127.0.0.1:554/live/ch00_1", r.streams.sources.single().subUri)
            assertTrue(tried.first().endsWith("/onvif1"))
        }

    @Test
    fun `respostas cruas ficam guardadas para exportar`() =
        runBlocking {
            val c = client()
            c.deviceInformation()
            assertNotNull(c.rawResponses["GetDeviceInformation"])
        }

    @Test
    fun `XAddrs deslocados do firmware real caem para o device_service`() =
        runBlocking {
            capsFixture = "real_dual_GetCapabilities"
            servicesFixture = "real_dual_GetServices"
            val s = client().discoverServices()
            val device = "http://${server.hostName}:${server.port}/onvif/device_service"
            assertEquals(device, s.media)
            assertEquals(device, s.ptz)
            assertEquals(device, s.events)
        }

    @Test
    fun `Stop recusado pela camera vira ContinuousMove com velocidade zero`() =
        runBlocking {
            dropStop = true
            val c = client()
            c.discoverServices()
            requests.clear()
            c.stop("Profile_1")
            assertTrue(c.stopByZeroVelocity)
            assertEquals("ContinuousMove", requests.last())
            requests.clear()
            c.stop("Profile_1")
            assertEquals(listOf("ContinuousMove"), requests)
        }

    @Test
    fun `perfis reais da lente dupla e da lampada`() {
        val dual = io.github.mbsantos97.camerasaovivo.onvif.parser.MediaParser.profiles(fixtureXml("real_dual_GetProfiles"))
        assertEquals(1920 to 2160, dual[0].width to dual[0].height)
        assertTrue(LensLayoutDetector.isStacked(dual[0].width, dual[0].height))
        val lamp = io.github.mbsantos97.camerasaovivo.onvif.parser.MediaParser.profiles(fixtureXml("real_lamp_GetProfiles"))
        assertFalse(LensLayoutDetector.isStacked(lamp[0].width, lamp[0].height))
        assertTrue(lamp.all { it.hasPtz })
    }

    @Test
    fun `deteccao de proporcao empilhada`() {
        assertTrue(LensLayoutDetector.isStacked(1920, 2160))
        assertTrue(LensLayoutDetector.isStacked(1280, 1440))
        assertTrue(LensLayoutDetector.isStacked(640, 720))
        assertFalse(LensLayoutDetector.isStacked(1920, 1080))
        assertFalse(LensLayoutDetector.isStacked(1080, 1920))
        assertFalse(LensLayoutDetector.isStacked(0, 0))
    }
}
