package io.github.mbsantos97.camerasaovivo.core.network

import io.github.mbsantos97.camerasaovivo.rtsp.SdpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class NetworkTest {
    @Test
    fun `faixas privadas sao permitidas`() {
        listOf("10.1.2.3", "172.16.0.1", "172.31.255.254", "192.168.0.50", "169.254.10.1", "127.0.0.1", "fc00::1", "fd12:3456::1", "fe80::1", "::1").forEach {
            assertTrue(it, LanOnlyGuard.isAllowed(it))
        }
    }

    @Test
    fun `destinos publicos sao bloqueados`() {
        listOf("8.8.8.8", "172.32.0.1", "172.15.255.255", "192.169.0.1", "1.1.1.1", "2001:4860:4860::8888", "100.64.0.1").forEach {
            assertFalse(it, LanOnlyGuard.isAllowed(it))
        }
    }

    @Test(expected = BlockedDestinationException::class)
    fun `check lanca excecao para destino externo`() {
        LanOnlyGuard.check("8.8.4.4")
    }

    @Test
    fun `isPrivate em enderecos resolvidos`() {
        assertTrue(IpUtils.isPrivate(InetAddress.getByName("192.168.1.1")))
        assertFalse(IpUtils.isPrivate(InetAddress.getByName("200.160.2.3")))
    }

    @Test
    fun `literal ou nome`() {
        assertEquals(true, IpUtils.isPrivateLiteral("10.0.0.1"))
        assertEquals(false, IpUtils.isPrivateLiteral("8.8.8.8"))
        assertNull(IpUtils.isPrivateLiteral("camera.local"))
        assertFalse(IpUtils.isIpv4Literal("256.1.1.1"))
    }

    @Test
    fun `sub-rede 24 sem o proprio IP`() {
        val s = IpUtils.subnet24("192.168.0.50")
        assertEquals(253, s.size)
        assertFalse("192.168.0.50" in s)
        assertEquals("192.168.0.1", s.first())
    }

    @Test
    fun `reescrita de host e porta preserva caminho e query`() {
        assertEquals("http://192.168.0.10:5000/onvif/media_service", IpUtils.rewriteHostPort("http://0.0.0.0:80/onvif/media_service", "192.168.0.10", 5000))
        assertEquals(
            "rtsp://192.168.0.10:554/cam/realmonitor?channel=1&subtype=0",
            IpUtils.rewriteHostPort("rtsp://10.0.0.9/cam/realmonitor?channel=1&subtype=0", "192.168.0.10", 554),
        )
        assertEquals("rtsp://192.168.0.10:554/", IpUtils.rewriteHostPort("rtsp://1.2.3.4:554", "192.168.0.10", 554))
        assertEquals("http://[fe80::1]:5000/x", IpUtils.rewriteHostPort("http://0.0.0.0/x", "fe80::1", 5000))
    }

    @Test
    fun `SDP com H265, PCMA e dimensoes`() {
        val sdp =
            """
            v=0
            o=- 0 0 IN IP4 0.0.0.0
            s=Session
            m=video 0 RTP/AVP 96
            a=rtpmap:96 H265/90000
            a=control:trackID=0
            a=framerate:15
            a=x-dimensions:1920,2160
            m=audio 0 RTP/AVP 8
            a=rtpmap:8 PCMA/16000
            a=control:trackID=1
            """.trimIndent()
        val info = SdpParser.parse(sdp)
        assertEquals("H265", info.video!!.codec)
        assertEquals(1920 to 2160, info.video!!.width to info.video!!.height)
        assertEquals(15f, info.video!!.fps)
        assertEquals("PCMA", info.audio!!.codec)
        assertEquals(16000, info.audio!!.clockRate)
        assertEquals("trackID=1", info.audio!!.control)
    }
}
