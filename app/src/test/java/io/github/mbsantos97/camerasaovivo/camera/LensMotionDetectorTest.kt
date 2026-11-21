package io.github.mbsantos97.camerasaovivo.camera

import io.github.mbsantos97.camerasaovivo.data.config.MotorizedLens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LensMotionDetectorTest {
    private val w = 64
    private val h = 72

    private fun frame(
        topGray: Int,
        bottomGray: Int,
    ) = IntArray(w * h) { i ->
        val g = if (i / w < h / 2) topGray else bottomGray
        (0xFF shl 24) or (g shl 16) or (g shl 8) or g
    }

    @Test
    fun `metade de baixo mudou entao a lente de baixo e a motorizada`() {
        val d = LensMotionDetector.halfDifferences(frame(100, 100), frame(102, 160), w, h)
        assertEquals(MotorizedLens.BOTTOM, LensMotionDetector.decide(d))
    }

    @Test
    fun `metade de cima mudou`() {
        val d = LensMotionDetector.halfDifferences(frame(50, 80), frame(140, 81), w, h)
        assertEquals(MotorizedLens.TOP, LensMotionDetector.decide(d))
    }

    @Test
    fun `sem movimento ou mudanca parecida nao decide`() {
        assertNull(LensMotionDetector.decide(LensMotionDetector.halfDifferences(frame(10, 10), frame(12, 11), w, h)))
        assertNull(LensMotionDetector.decide(40.0 to 35.0))
    }
}
