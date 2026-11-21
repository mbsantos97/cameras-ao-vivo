package io.github.mbsantos97.camerasaovivo.onvif

import io.github.mbsantos97.camerasaovivo.onvif.discovery.WsDiscovery
import io.github.mbsantos97.camerasaovivo.onvif.parser.DeviceParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.EventsParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.FaultParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.MediaParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.PtzParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.ReplayParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.SearchParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.Xml
import io.github.mbsantos97.camerasaovivo.onvif.parser.XsdTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    @Test
    fun `hora do sistema em UTC`() {
        assertEquals(XsdTime.parse("2026-11-20T03:04:05Z"), DeviceParser.systemTime(fixtureXml("system_date_time")))
    }

    @Test
    fun `informacoes do aparelho`() {
        val i = DeviceParser.deviceInfo(fixtureXml("device_information"))
        assertEquals("IPC", i.manufacturer)
        assertEquals("Yoosee-8177", i.model)
        assertEquals("14.00.07.01", i.firmware)
        assertEquals("SERIAL-FIXTURE", i.serial)
    }

    @Test
    fun `capabilities com XAddr quebrado e sem PTZ`() {
        val c = DeviceParser.capabilities(fixtureXml("capabilities_yoosee"))
        assertEquals("http://0.0.0.0:80/onvif/media_service", c.media)
        assertEquals("http://0.0.0.0:80/onvif/ptz_service", c.ptz)
        assertFalse(c.profileG)
        assertNull(DeviceParser.capabilities(fixtureXml("capabilities_no_ptz")).ptz)
    }

    @Test
    fun `GetServices detecta Profile G pelos namespaces`() {
        assertTrue(DeviceParser.services(fixtureXml("services_profile_g")).profileG)
        assertFalse(DeviceParser.services(fixtureXml("services_no_profile_g")).profileG)
    }

    @Test
    fun `MAC da interface de rede`() {
        assertEquals("00:11:22:AA:BB:CC", DeviceParser.mac(fixtureXml("network_interfaces")))
    }

    @Test
    fun `perfis Yoosee tipicos`() {
        val p = MediaParser.profiles(fixtureXml("profiles_yoosee"))
        assertEquals(2, p.size)
        assertEquals(1920 to 1080, p[0].width to p[0].height)
        assertEquals("VideoSource_1", p[1].videoSourceToken)
        assertEquals(15f, p[0].fps)
        assertTrue(p.all { it.hasPtz })
    }

    @Test
    fun `perfis de lente dupla com dois video sources e empilhada`() {
        assertEquals(
            setOf("VideoSource_1", "VideoSource_2"),
            MediaParser.profiles(fixtureXml("profiles_dual_sources")).mapNotNull {
                it.videoSourceToken
            }.toSet(),
        )
        val st = MediaParser.profiles(fixtureXml("profiles_dual_stacked"))
        assertEquals(1920 to 2160, st[0].width to st[0].height)
        assertEquals("H265", st[0].encoding)
    }

    @Test
    fun `perfis Media2`() {
        val p = MediaParser.profiles(fixtureXml("profiles_media2")).single()
        assertEquals("M2_main", p.token)
        assertEquals(2304, p.width)
        assertTrue(p.hasPtz)
    }

    @Test
    fun `URI de stream, snapshot e replay`() {
        assertEquals("rtsp://192.168.1.120:554/onvif1", MediaParser.uri(fixtureXml("stream_uri_main")))
        assertEquals("http://0.0.0.0:80/snapshot.jpg", MediaParser.uri(fixtureXml("snapshot_uri")))
        assertEquals("rtsp://0.0.0.0:554/recording/REC_1", ReplayParser.uri(fixtureXml("replay_uri")))
    }

    @Test
    fun `presets com acentos`() {
        val p = PtzParser.presets(fixtureXml("presets"))
        assertEquals(listOf("1", "2"), p.map { it.token })
        assertEquals("Portão", p[0].name)
    }

    @Test
    fun `faults de autenticacao, acao nao suportada e SOAP 1_1`() {
        assertTrue(FaultParser.parse(fixtureXml("fault_not_authorized"))!!.notAuthorized)
        val ns = FaultParser.parse(fixtureXml("fault_action_not_supported"))!!
        assertTrue(ns.actionNotSupported)
        assertEquals("ter:NoRecording", ns.subcode)
        assertTrue(FaultParser.parse(fixtureXml("fault_soap11"))!!.notAuthorized)
        assertNull(FaultParser.parse(fixtureXml("device_information")))
    }

    @Test
    fun `eventos de movimento e assinatura`() {
        assertEquals("http://0.0.0.0:80/onvif/event_service/sub1", EventsParser.subscriptionAddress(fixtureXml("create_pull_point")))
        val m = EventsParser.motion(fixtureXml("pull_messages_motion"))
        assertEquals(1, m.size)
        assertTrue(m[0].active)
    }

    @Test
    fun `busca de gravacoes Profile G`() {
        val s = SearchParser.summary(fixtureXml("recording_summary"))!!
        assertEquals(1, s.count)
        assertEquals("search-1", SearchParser.searchToken(fixtureXml("find_recordings")))
        val r = SearchParser.recordings(fixtureXml("recording_search_results")).single()
        assertEquals("REC_1", r.token)
        assertEquals(XsdTime.parse("2026-11-18T00:00:00Z"), r.tracks.single().fromMillis)
        val info = SearchParser.recordings(fixtureXml("recording_information")).single()
        assertEquals(XsdTime.parse("2026-11-20T06:00:00.500Z"), info.latestMillis)
        val ev = SearchParser.events(fixtureXml("event_search_results"))
        assertEquals(listOf(true, false), ev.map { it.isStart })
    }

    @Test
    fun `xs dateTime com fracao e fuso`() {
        assertEquals(0L, XsdTime.parse("1970-01-01T00:00:00Z"))
        assertEquals(3 * 3600_000L + 250, XsdTime.parse("1970-01-01T00:00:00.25-03:00"))
        assertNull(XsdTime.parse("lixo"))
    }

    @Test
    fun `WS-Discovery usa o IP do remetente e le os scopes`() {
        val d = WsDiscovery.parseProbeMatches(fixture("probe_match"), "192.168.0.102").single()
        assertEquals("192.168.0.102", d.host)
        assertEquals("http://192.168.0.102:5000/onvif/device_service", d.xaddr)
        assertEquals(5000, d.port)
        assertEquals("Yoosee IPC", d.name)
        assertEquals("IPC-8177", d.hardware)
    }

    @Test
    fun `parser tolera prefixos e caixa diferentes`() {
        val n = Xml.parse("<a:Root xmlns:a=\"x\"><b:WIDTH>10</b:WIDTH></a:Root>")
        assertEquals("10", n.textOf("Width"))
    }
}
