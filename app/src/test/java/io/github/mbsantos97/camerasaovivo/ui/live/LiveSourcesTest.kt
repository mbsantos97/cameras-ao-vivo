package io.github.mbsantos97.camerasaovivo.ui.live

import io.github.mbsantos97.camerasaovivo.data.config.CachedStreams
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.LensMode
import io.github.mbsantos97.camerasaovivo.data.config.MotorizedLens
import io.github.mbsantos97.camerasaovivo.data.config.SourceStreams
import io.github.mbsantos97.camerasaovivo.player.VideoRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSourcesTest {
    private fun cam(
        id: String,
        vararg sources: SourceStreams,
        lens: LensMode = LensMode.AUTO,
        hidden: Boolean = false,
        order: Int = 0,
    ) = CameraConfig(
        id = id,
        name = id,
        host = "192.168.0.10",
        lensMode = lens,
        hidden = hidden,
        order = order,
        streams = CachedStreams(sources = sources.toList()),
    )

    @Test
    fun `camera com varios video sources vira um quadrante por lente`() {
        val s = LiveSources.build(listOf(cam("ext", SourceStreams(), SourceStreams())))
        assertEquals(listOf("ext#0", "ext#1"), s.map { it.id })
        assertEquals("ext – Lente 2", s[1].name)
    }

    @Test
    fun `imagem empilhada em Auto vira coluna dupla`() {
        val s = LiveSources.build(listOf(cam("ext", SourceStreams(stacked = true))))
        assertEquals(SourceDisplay.DUAL_COLUMN, s[0].display)
        assertTrue(s[0].tall)
        assertEquals(VideoRegion.FILL, s[0].gridRegion(tallPlacement = true))
        assertEquals(VideoRegion.FIT, s[0].gridRegion(tallPlacement = false))
    }

    @Test
    fun `modos so cima, so baixo e compacto`() {
        fun display(mode: LensMode) = LiveSources.build(listOf(cam("a", SourceStreams(stacked = true), lens = mode)))[0].display
        assertEquals(SourceDisplay.TOP, display(LensMode.TOP))
        assertEquals(SourceDisplay.BOTTOM, display(LensMode.BOTTOM))
        assertEquals(SourceDisplay.COMPACT, display(LensMode.COMPACT))
    }

    @Test
    fun `camera oculta fica fora e a ordem e respeitada`() {
        val s = LiveSources.build(listOf(cam("b", order = 2), cam("x", hidden = true), cam("a", order = 1)))
        assertEquals(listOf("a", "b"), s.map { it.id })
    }

    @Test
    fun `lente motorizada padrao e a de baixo`() {
        val s = LiveSources.build(listOf(cam("ext", SourceStreams(stacked = true))))
        assertEquals(MotorizedLens.BOTTOM, s[0].motorized)
        assertEquals(LensSlot.BOTTOM, s[0].defaultSlot())
        assertEquals(VideoRegion.BOTTOM_HALF_FIT, s[0].fullscreenRegion(LensSlot.BOTTOM))
    }

    @Test
    fun `camera sem streams resolvidos ainda aparece`() {
        val s = LiveSources.build(listOf(CameraConfig(id = "n", name = "Nova", host = "192.168.0.9")))
        assertEquals(1, s.size)
        assertFalse(s[0].stacked)
    }
}
