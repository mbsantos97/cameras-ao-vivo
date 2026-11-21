package io.github.mbsantos97.camerasaovivo.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTransformTest {
    private fun assertT(
        expected: Transform,
        actual: Transform,
    ) {
        assertEquals(expected.scaleX, actual.scaleX, 0.001f)
        assertEquals(expected.scaleY, actual.scaleY, 0.001f)
        assertEquals(expected.translateX, actual.translateX, 0.5f)
        assertEquals(expected.translateY, actual.translateY, 0.5f)
    }

    @Test
    fun `mesma proporcao preenche sem transformacao`() {
        assertT(Transform.IDENTITY, VideoTransform.compute(1920, 1080, 1280, 720, VideoRegion.FILL))
    }

    @Test
    fun `fit de 4x3 numa tela 16x9 gera barras laterais`() {
        assertT(Transform(0.75f, 1f, 200f, 0f), VideoTransform.compute(1600, 900, 640, 480, VideoRegion.FIT))
    }

    @Test
    fun `fill de 4x3 numa tela 16x9 recorta em cima e embaixo`() {
        assertT(Transform(1f, 1200f / 900f, 0f, -150f), VideoTransform.compute(1600, 900, 640, 480, VideoRegion.FILL))
    }

    @Test
    fun `metade de cima de imagem empilhada ocupa a tela inteira`() {
        assertT(Transform(1f, 2f, 0f, 0f), VideoTransform.compute(1920, 1080, 1920, 2160, VideoRegion.TOP_HALF_FIT))
    }

    @Test
    fun `metade de baixo de imagem empilhada desloca para cima`() {
        assertT(Transform(1f, 2f, 0f, -1080f), VideoTransform.compute(1920, 1080, 1920, 2160, VideoRegion.BOTTOM_HALF_FIT))
    }

    @Test
    fun `coluna dupla com metades 16x9 alinha cada lente a um quadrante`() {
        assertT(Transform.IDENTITY, VideoTransform.compute(800, 900, 1280, 1440, VideoRegion.FILL))
    }

    @Test
    fun `zoom 2x no centro mostra a metade central`() {
        val r = VideoRegion.FIT.zoomed(2f)
        assertEquals(0.25f, r.x, 0.0001f)
        assertEquals(0.5f, r.w, 0.0001f)
        assertT(Transform(2f, 2f, -960f, -540f), VideoTransform.compute(1920, 1080, 1920, 1080, r))
    }

    @Test
    fun `zoom nao sai das bordas`() {
        val r = VideoRegion.FIT.zoomed(2f, centerX = 0f, centerY = 1f)
        assertEquals(0f, r.x, 0.0001f)
        assertEquals(0.5f, r.y, 0.0001f)
    }

    @Test
    fun `tamanho desconhecido devolve identidade`() {
        assertT(Transform.IDENTITY, VideoTransform.compute(0, 0, 1280, 720, VideoRegion.FILL))
        assertT(Transform.IDENTITY, VideoTransform.compute(100, 100, 0, 0, VideoRegion.FILL))
    }
}
