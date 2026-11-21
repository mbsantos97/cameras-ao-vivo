package io.github.mbsantos97.camerasaovivo.core.input

import org.junit.Assert.assertEquals
import org.junit.Test

class LongPressDetectorTest {
    private val d = LongPressDetector(600)

    @Test
    fun `toque curto dispara no KeyUp`() {
        assertEquals(PressResult.NONE, d.onKeyDown(0, 0))
        assertEquals(PressResult.SHORT, d.onKeyUp(150))
    }

    @Test
    fun `toque longo dispara ao atingir 600 ms e ignora o KeyUp`() {
        d.onKeyDown(0, 0)
        assertEquals(PressResult.NONE, d.onKeyDown(400, 1))
        assertEquals(PressResult.LONG, d.onKeyDown(650, 2))
        assertEquals(PressResult.NONE, d.onKeyDown(700, 3))
        assertEquals(PressResult.NONE, d.onKeyUp(900))
    }

    @Test
    fun `temporizador dispara o longo sem repeticao do controle`() {
        d.onKeyDown(1000, 0)
        assertEquals(PressResult.NONE, d.onTick(1500))
        assertEquals(PressResult.LONG, d.onTick(1600))
        assertEquals(PressResult.NONE, d.onKeyUp(2000))
    }

    @Test
    fun `KeyUp tardio sem tick vira longo, nunca os dois`() {
        d.onKeyDown(0, 0)
        assertEquals(PressResult.LONG, d.onKeyUp(800))
        assertEquals(PressResult.NONE, d.onKeyUp(900))
    }

    @Test
    fun `KeyUp sem KeyDown e repeticao orfa sao ignorados`() {
        assertEquals(PressResult.NONE, d.onKeyUp(10))
        assertEquals(PressResult.NONE, d.onKeyDown(10, 5))
        assertEquals(PressResult.NONE, d.onTick(5000))
    }

    @Test
    fun `nova pressao depois de um longo funciona normalmente`() {
        d.onKeyDown(0, 0)
        d.onTick(700)
        d.onKeyUp(800)
        d.onKeyDown(1000, 0)
        assertEquals(PressResult.SHORT, d.onKeyUp(1100))
    }
}
