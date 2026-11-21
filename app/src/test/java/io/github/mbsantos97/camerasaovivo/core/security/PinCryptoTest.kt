package io.github.mbsantos97.camerasaovivo.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinCryptoTest {
    @Test
    fun `hash do PIN confere so com o PIN certo`() {
        val salt = PinCrypto.newSalt()
        val hash = PinCrypto.hashPin("1234", salt)
        assertTrue(PinCrypto.verifyPin("1234", salt, hash))
        assertFalse(PinCrypto.verifyPin("4321", salt, hash))
    }

    @Test
    fun `sem PIN configurado tudo passa`() {
        assertTrue(PinCrypto.verifyPin("0000", null, null))
    }

    @Test
    fun `sais diferentes geram hashes diferentes`() {
        assertNotEquals(PinCrypto.hashPin("1234", PinCrypto.newSalt()), PinCrypto.hashPin("1234", PinCrypto.newSalt()))
    }

    @Test
    fun `selar e abrir devolve o conteudo original`() {
        val data = "configuração com acentuação".encodeToByteArray()
        val sealed = PinCrypto.seal(data, "2468")
        assertArrayEquals(data, PinCrypto.open(sealed, "2468"))
    }

    @Test(expected = Exception::class)
    fun `PIN errado falha ao abrir`() {
        PinCrypto.open(PinCrypto.seal("x".encodeToByteArray(), "2468"), "1357")
    }

    @Test
    fun `validacao de PIN de 4 digitos`() {
        assertTrue(PinCrypto.isValidPin("0912"))
        assertFalse(PinCrypto.isValidPin("12a4"))
        assertFalse(PinCrypto.isValidPin("123"))
    }
}
