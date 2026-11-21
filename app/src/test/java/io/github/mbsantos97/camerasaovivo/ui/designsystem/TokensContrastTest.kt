package io.github.mbsantos97.camerasaovivo.ui.designsystem

import org.junit.Assert.assertTrue
import org.junit.Test

class TokensContrastTest {
    private val c = AoVivoColors()

    @Test
    fun `pares de texto e fundo dos tokens tem contraste minimo de 4,5`() {
        val pairs =
            listOf(
                "textPrimary/bg" to (c.textPrimary to c.bg),
                "textPrimary/surface" to (c.textPrimary to c.surface),
                "textPrimary/surfaceRaised" to (c.textPrimary to c.surfaceRaised),
                "textPrimary/surfaceHigh" to (c.textPrimary to c.surfaceHigh),
                "textSecondary/bg" to (c.textSecondary to c.bg),
                "textSecondary/surface" to (c.textSecondary to c.surface),
                "textSecondary/surfaceRaised" to (c.textSecondary to c.surfaceRaised),
                "textSecondary/surfaceHigh" to (c.textSecondary to c.surfaceHigh),
                "onAccent/accent" to (c.onAccent to c.accent),
                "accent/surface" to (c.accent to c.surface),
                "bg/danger (botão perigoso)" to (c.bg to c.danger),
            )
        for ((name, pair) in pairs) {
            val ratio = contrastRatio(pair.first, pair.second)
            assertTrue("$name com contraste $ratio", ratio >= 4.5)
        }
    }

    @Test
    fun `contraste de branco sobre preto e 21`() {
        assertTrue(contrastRatio(c.focus, c.bg) > 20.9)
    }
}
