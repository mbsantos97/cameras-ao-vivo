package io.github.mbsantos97.camerasaovivo.camera.ptz

import io.github.mbsantos97.camerasaovivo.data.config.PtzMode
import io.github.mbsantos97.camerasaovivo.data.config.PtzSettings
import io.github.mbsantos97.camerasaovivo.data.config.PtzSpeed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingBackend(
    private val moveDelayMs: Long = 0,
    var relativeFails: Boolean = false,
) : PtzBackend {
    val log = mutableListOf<String>()

    override suspend fun continuousMove(
        x: Float,
        y: Float,
        timeout: String?,
    ) {
        delay(moveDelayMs)
        log += "move($x,$y,$timeout)"
    }

    override suspend fun stop() {
        log += "stop"
    }

    override suspend fun relativeMove(
        x: Float,
        y: Float,
    ) {
        if (relativeFails) error("não suportado")
        log += "rel($x,$y)"
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PtzControllerTest {
    private fun TestScope.controller(
        backend: RecordingBackend,
        settings: PtzSettings = PtzSettings(speed = PtzSpeed.FAST),
    ) = PtzController(backend, backgroundScope, settings) { testScheduler.currentTime }

    @Test
    fun `segurar seta move e soltar para`() =
        runTest {
            val b = RecordingBackend()
            val c = controller(b)
            c.press(PtzDirection.RIGHT)
            runCurrent()
            c.release(PtzDirection.RIGHT)
            runCurrent()
            assertEquals(listOf("move(1.0,0.0,PT10S)", "stop"), b.log)
        }

    @Test
    fun `Stop nunca chega antes do Start mesmo com rede lenta`() =
        runTest {
            val b = RecordingBackend(moveDelayMs = 300)
            val c = controller(b)
            c.press(PtzDirection.UP)
            c.release(PtzDirection.UP)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf("move(0.0,1.0,PT10S)", "stop"), b.log)
        }

    @Test
    fun `duas setas fazem diagonal e soltar uma mantem a outra`() =
        runTest {
            val b = RecordingBackend()
            val c = controller(b)
            c.press(PtzDirection.LEFT)
            c.press(PtzDirection.UP)
            c.release(PtzDirection.LEFT)
            c.release(PtzDirection.UP)
            runCurrent()
            assertEquals(listOf("move(-1.0,0.0,PT10S)", "move(-1.0,1.0,PT10S)", "move(0.0,1.0,PT10S)", "stop"), b.log)
        }

    @Test
    fun `repeticao da tecla e ignorada`() =
        runTest {
            val b = RecordingBackend()
            val c = controller(b)
            repeat(5) { c.press(PtzDirection.DOWN) }
            runCurrent()
            assertEquals(1, b.log.size)
        }

    @Test
    fun `inversao de eixos para camera de cabeca para baixo`() =
        runTest {
            val c = controller(RecordingBackend(), PtzSettings(invertPan = true, invertTilt = true, speed = PtzSpeed.MEDIUM))
            assertEquals(-0.6f to -0.6f, c.vector(setOf(PtzDirection.RIGHT, PtzDirection.UP)))
        }

    @Test
    fun `velocidades normalizadas`() {
        assertEquals(listOf(0.3f, 0.6f, 1.0f), PtzSpeed.entries.map { it.value })
    }

    @Test
    fun `sem KeyUp em 8 s envia Stop`() =
        runTest {
            val b = RecordingBackend()
            val c = controller(b)
            c.press(PtzDirection.RIGHT)
            advanceTimeBy(7_900)
            runCurrent()
            assertEquals(1, b.log.size)
            advanceTimeBy(200)
            runCurrent()
            assertEquals("stop", b.log.last())
            assertTrue(c.moving.value.isEmpty())
        }

    @Test
    fun `perder o foco para o movimento`() =
        runTest {
            val b = RecordingBackend()
            val c = controller(b)
            c.press(PtzDirection.LEFT)
            c.stopNow()
            runCurrent()
            assertEquals("stop", b.log.last())
            c.release(PtzDirection.LEFT)
            runCurrent()
            assertEquals(2, b.log.size)
        }

    @Test
    fun `modo passo usa RelativeMove`() =
        runTest {
            val b = RecordingBackend()
            val c = controller(b, PtzSettings(mode = PtzMode.STEP, speed = PtzSpeed.FAST))
            c.press(PtzDirection.RIGHT)
            c.release(PtzDirection.RIGHT)
            runCurrent()
            assertEquals(listOf("rel(0.1,0.0)"), b.log)
        }

    @Test
    fun `modo passo sem RelativeMove usa ContinuousMove curto e Stop`() =
        runTest {
            val b = RecordingBackend(relativeFails = true)
            val c = controller(b, PtzSettings(mode = PtzMode.STEP, speed = PtzSpeed.SLOW))
            c.press(PtzDirection.LEFT)
            advanceTimeBy(500)
            runCurrent()
            assertEquals(listOf("move(-0.3,0.0,PT0.4S)", "stop"), b.log)
        }
}
