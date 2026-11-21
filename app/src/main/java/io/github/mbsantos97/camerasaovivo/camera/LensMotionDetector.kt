package io.github.mbsantos97.camerasaovivo.camera

import io.github.mbsantos97.camerasaovivo.data.config.MotorizedLens
import kotlin.math.abs

/**
 * "Identificar lente motorizada": com a imagem empilhada, captura um quadro, move a câmera 1 s,
 * captura de novo e compara quanto mudou cada metade. A metade que mais mudou é a motorizada.
 */
object LensMotionDetector {
    /** Diferença média de luminância (0..255) de cada metade. Pixels em ARGB. */
    fun halfDifferences(
        before: IntArray,
        after: IntArray,
        width: Int,
        height: Int,
        step: Int = 4,
    ): Pair<Double, Double> {
        require(before.size == width * height && after.size == before.size) { "Quadros de tamanhos diferentes" }
        val half = height / 2
        var top = 0.0
        var bottom = 0.0
        var nTop = 0
        var nBottom = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val i = y * width + x
                val d = abs(luma(before[i]) - luma(after[i]))
                if (y < half) {
                    top += d
                    nTop++
                } else {
                    bottom += d
                    nBottom++
                }
                x += step
            }
            y += step
        }
        return (top / nTop.coerceAtLeast(1)) to (bottom / nBottom.coerceAtLeast(1))
    }

    /** Decide a lente; null se a diferença não for clara (ex.: câmera não se mexeu). */
    fun decide(
        diffs: Pair<Double, Double>,
        minDiff: Double = 6.0,
        ratio: Double = 1.5,
    ): MotorizedLens? {
        val (t, b) = diffs
        return when {
            maxOf(t, b) < minDiff -> null
            t > b * ratio -> MotorizedLens.TOP
            b > t * ratio -> MotorizedLens.BOTTOM
            else -> null
        }
    }

    private fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
