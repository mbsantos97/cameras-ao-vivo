package io.github.mbsantos97.camerasaovivo.player

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.min

/**
 * Região do vídeo a exibir, em coordenadas normalizadas (0..1), e o modo de escala.
 * fit=false preenche a área (recorta o excesso); fit=true mostra a região inteira (letterbox).
 */
@Immutable
data class VideoRegion(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 1f,
    val h: Float = 1f,
    val fit: Boolean = false,
) {
    /** Aplica zoom digital centrado em (centerX, centerY) da própria região, limitado às bordas. */
    fun zoomed(
        factor: Float,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
    ): VideoRegion {
        if (factor <= 1f) return this
        val nw = w / factor
        val nh = h / factor
        val cx = (x + w * centerX).coerceIn(x + nw / 2, x + w - nw / 2)
        val cy = (y + h * centerY).coerceIn(y + nh / 2, y + h - nh / 2)
        return copy(x = cx - nw / 2, y = cy - nh / 2, w = nw, h = nh)
    }

    companion object {
        val FILL = VideoRegion()
        val FIT = VideoRegion(fit = true)
        val TOP_HALF_FILL = VideoRegion(h = 0.5f)
        val BOTTOM_HALF_FILL = VideoRegion(y = 0.5f, h = 0.5f)
        val TOP_HALF_FIT = VideoRegion(h = 0.5f, fit = true)
        val BOTTOM_HALF_FIT = VideoRegion(y = 0.5f, h = 0.5f, fit = true)
    }
}

/** Escala e translação para o Matrix do TextureView. */
@Immutable
data class Transform(
    val scaleX: Float,
    val scaleY: Float,
    val translateX: Float,
    val translateY: Float,
) {
    companion object {
        val IDENTITY = Transform(1f, 1f, 0f, 0f)
    }
}

object VideoTransform {
    /**
     * O player desenha o quadro inteiro esticado para ocupar a view (W×H).
     * Calcula a matriz (escala a partir da origem, depois translação) que mostra só [region]
     * com a proporção correta do vídeo (w×h), centralizada na view.
     */
    fun compute(
        viewW: Int,
        viewH: Int,
        videoW: Int,
        videoH: Int,
        region: VideoRegion,
    ): Transform {
        if (viewW <= 0 || viewH <= 0 || videoW <= 0 || videoH <= 0) return Transform.IDENTITY
        val regionW = region.w * videoW
        val regionH = region.h * videoH
        val kx = viewW / regionW
        val ky = viewH / regionH
        val k = if (region.fit) min(kx, ky) else max(kx, ky)
        val displayW = videoW * k
        val displayH = videoH * k
        val centerX = (region.x + region.w / 2f) * displayW
        val centerY = (region.y + region.h / 2f) * displayH
        return Transform(
            scaleX = displayW / viewW,
            scaleY = displayH / viewH,
            translateX = viewW / 2f - centerX,
            translateY = viewH / 2f - centerY,
        )
    }
}
