package io.github.mbsantos97.camerasaovivo.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LogMaskerTest {
    @Test
    fun `mascara senha em URL`() {
        assertEquals("rtsp://admin:***@192.168.0.10:554/onvif1", LogMasker.mask("rtsp://admin:segredo@192.168.0.10:554/onvif1"))
    }

    @Test
    fun `mascara Password do WS-Security e opcao do LibVLC`() {
        val xml = """<wsse:Password Type="x">abcDEF==</wsse:Password>"""
        assertEquals("""<wsse:Password Type="x">***</wsse:Password>""", LogMasker.mask(xml))
        assertEquals(":rtsp-pwd=***", LogMasker.mask(":rtsp-pwd=segredo"))
    }

    @Test
    fun `mascara pares chave e valor`() {
        assertEquals("senha=*** ok", LogMasker.mask("senha=1234abcd ok"))
        assertEquals("password: ***", LogMasker.mask("password: hunter2"))
    }

    @Test
    fun `mascara serial, MAC e cabecalho Authorization`() {
        assertEquals("<tds:SerialNumber>***</tds:SerialNumber>", LogMasker.mask("<tds:SerialNumber>ABC123</tds:SerialNumber>"))
        assertEquals("<tt:HwAddress>***</tt:HwAddress>", LogMasker.mask("<tt:HwAddress>00:11:22:33:44:55</tt:HwAddress>"))
        assertEquals("Authorization: Digest ***", LogMasker.mask("Authorization: Digest username=\"admin\", response=\"abc\""))
    }

    @Test
    fun `segredo registrado some de qualquer texto`() {
        LogMasker.register("Zq9!senhaForte")
        assertFalse(LogMasker.mask("falhou com Zq9!senhaForte no meio").contains("Zq9!senhaForte"))
    }
}
