package io.github.mbsantos97.camerasaovivo.onvif

import io.github.mbsantos97.camerasaovivo.onvif.parser.Xml
import io.github.mbsantos97.camerasaovivo.onvif.soap.HttpDigest
import io.github.mbsantos97.camerasaovivo.onvif.soap.Ns
import io.github.mbsantos97.camerasaovivo.onvif.soap.Requests
import io.github.mbsantos97.camerasaovivo.onvif.soap.SoapEnvelope
import io.github.mbsantos97.camerasaovivo.onvif.soap.WsSecurity
import okio.ByteString.Companion.decodeBase64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SoapAuthTest {
    @Before
    fun setup() = TestXml.install()

    @Test
    fun `digest do WS-Security bate com o vetor do guia ONVIF`() {
        val nonce = "LKqI6G/AikKCQrN0zqZFlg==".decodeBase64()!!.toByteArray()
        assertEquals("tuOSpGlFlIXsozq4HFNeeGeFLEI=", WsSecurity.digest(nonce, "2010-09-16T07:50:45Z", "userpassword"))
    }

    @Test
    fun `created usa UTC no formato xs dateTime`() {
        assertEquals("2026-11-20T03:04:05Z", WsSecurity.created(1_795_143_845_000L))
    }

    @Test
    fun `cabecalho UsernameToken tem usuario, digest, nonce e created`() {
        val h = WsSecurity.header("admin", "senha", 1_795_143_845_000L, ByteArray(16) { it.toByte() })
        val node = Xml.parse("<x>$h</x>")
        assertEquals("admin", node.textOf("Username"))
        assertEquals(Ns.PASSWORD_DIGEST, node.find("Password")!!.attr("Type"))
        assertEquals("2026-11-20T03:04:05Z", node.textOf("Created"))
        assertNotNull(node.textOf("Nonce"))
    }

    @Test
    fun `HTTP Digest bate com o exemplo do RFC 2617`() {
        val ch =
            HttpDigest.parseChallenge(
                """Digest realm="testrealm@host.com", qop="auth,auth-int", nonce="dcd98b7102dd2f0e8b11d0f600bfb0c093", opaque="5ccc069c403ebaf9f0171e9517f40e41"""",
            )!!
        assertEquals("testrealm@host.com", ch.realm)
        val r = HttpDigest.response(ch, "Mufasa", "Circle Of Life", "GET", "/dir/index.html", "00000001", "0a4f113b")
        assertEquals("6629fae49393a05397450978507c4ef1", r)
        val auth = HttpDigest.authorization(ch, "Mufasa", "Circle Of Life", "GET", "/dir/index.html", 1, "0a4f113b")
        assertTrue(auth.contains("response=\"6629fae49393a05397450978507c4ef1\""))
        assertTrue(auth.contains("qop=auth, nc=00000001"))
    }

    @Test
    fun `desafio sem qop e desafio Basic`() {
        val ch = HttpDigest.parseChallenge("Digest realm=\"IPC\", nonce=abc123")!!
        assertNull(ch.qop)
        assertEquals("abc123", ch.nonce)
        assertNull(HttpDigest.parseChallenge("Basic realm=\"x\""))
        assertEquals("Basic YWRtaW46MTIzNA==", HttpDigest.basic("admin", "1234"))
    }

    @Test
    fun `envelope SOAP 1_2 e 1_1 com corpo e cabecalho`() {
        val body = Requests.getProfiles().body
        val e12 = Xml.parse(SoapEnvelope.build(body, "<h/>", soap12 = true))
        assertNotNull(e12.find("GetProfiles"))
        assertNotNull(e12.find("Header"))
        assertTrue(SoapEnvelope.build(body, null, soap12 = false).contains(Ns.SOAP11))
    }

    @Test
    fun `ContinuousMove leva velocidade e timeout, Stop para so pan e tilt`() {
        val mv = Xml.parse(Requests.continuousMove("P1", -0.6f, 0.3f, "PT10S").body)
        assertEquals("-0.600", mv.find("PanTilt")!!.attr("x"))
        assertEquals("0.300", mv.find("PanTilt")!!.attr("y"))
        assertEquals("PT10S", mv.textOf("Timeout"))
        val stop = Xml.parse(Requests.stop("P1").body)
        assertEquals("true", stop.textOf("PanTilt"))
        assertEquals("false", stop.textOf("Zoom"))
    }

    @Test
    fun `tokens sao escapados no XML`() {
        assertTrue(Requests.getStreamUri("a<b&c").body.contains("a&lt;b&amp;c"))
    }
}
