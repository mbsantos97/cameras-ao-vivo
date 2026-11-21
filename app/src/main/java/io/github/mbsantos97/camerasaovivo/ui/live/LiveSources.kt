package io.github.mbsantos97.camerasaovivo.ui.live

import androidx.compose.runtime.Immutable
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.LensMode
import io.github.mbsantos97.camerasaovivo.data.config.MotorizedLens
import io.github.mbsantos97.camerasaovivo.data.config.SourceStreams
import io.github.mbsantos97.camerasaovivo.player.VideoRegion

/** Como uma fonte é exibida na grade. */
enum class SourceDisplay { NORMAL, DUAL_COLUMN, TOP, BOTTOM, COMPACT }

/** Qual parte de uma fonte está em foco ou em tela cheia. */
enum class LensSlot { WHOLE, TOP, BOTTOM }

/** Uma fonte de vídeo da grade (uma câmera, ou uma lente de câmera com vários video sources). */
@Immutable
data class LiveSource(
    val id: String,
    val cameraId: String,
    val name: String,
    val sourceIndex: Int,
    val display: SourceDisplay,
    val stacked: Boolean,
    val ptz: Boolean,
    val motorized: MotorizedLens,
    val demo: Boolean,
) {
    val tall: Boolean get() = display == SourceDisplay.DUAL_COLUMN

    /** Região do vídeo para a grade. [tallPlacement]=false: item alto colocado em quadrante simples. */
    fun gridRegion(tallPlacement: Boolean): VideoRegion =
        when (display) {
            SourceDisplay.NORMAL -> VideoRegion.FILL
            SourceDisplay.DUAL_COLUMN -> if (tallPlacement) VideoRegion.FILL else VideoRegion.FIT
            SourceDisplay.TOP -> VideoRegion.TOP_HALF_FILL
            SourceDisplay.BOTTOM -> VideoRegion.BOTTOM_HALF_FILL
            SourceDisplay.COMPACT -> VideoRegion.FIT
        }

    /** Região do vídeo em tela cheia para a lente escolhida. */
    fun fullscreenRegion(slot: LensSlot): VideoRegion =
        when {
            !stacked -> VideoRegion.FIT
            slot == LensSlot.TOP -> VideoRegion.TOP_HALF_FIT
            slot == LensSlot.BOTTOM -> VideoRegion.BOTTOM_HALF_FIT
            display == SourceDisplay.TOP -> VideoRegion.TOP_HALF_FIT
            display == SourceDisplay.BOTTOM -> VideoRegion.BOTTOM_HALF_FIT
            else -> VideoRegion.FIT
        }

    /** Lente em tela cheia ao abrir a partir de um quadrante simples. */
    fun defaultSlot(): LensSlot =
        when {
            !stacked -> LensSlot.WHOLE
            display == SourceDisplay.TOP -> LensSlot.TOP
            display == SourceDisplay.BOTTOM -> LensSlot.BOTTOM
            display == SourceDisplay.COMPACT -> LensSlot.WHOLE
            else -> if (motorized == MotorizedLens.TOP) LensSlot.TOP else LensSlot.BOTTOM
        }
}

object LiveSources {
    /** Monta as fontes da grade a partir da configuração (câmeras ocultas ficam de fora). */
    fun build(cameras: List<CameraConfig>): List<LiveSource> =
        cameras
            .filter { !it.hidden }
            .sortedBy { it.order }
            .flatMap { cam ->
                val sources = cam.streams?.sources.orEmpty().ifEmpty { listOf(SourceStreams()) }
                val multi = sources.size > 1
                sources.mapIndexed { i, s ->
                    val stacked = s.stacked || cam.lensMode in setOf(LensMode.DUAL_COLUMN, LensMode.TOP, LensMode.BOTTOM)
                    LiveSource(
                        id = if (multi) "${cam.id}#$i" else cam.id,
                        cameraId = cam.id,
                        name = if (multi) "${cam.name} – Lente ${i + 1}" else cam.name,
                        sourceIndex = i,
                        display = displayFor(stacked, cam.lensMode),
                        stacked = stacked,
                        ptz = cam.streams?.ptzSupported == true,
                        motorized = cam.effectiveMotorizedLens,
                        demo = cam.demo,
                    )
                }
            }

    fun displayFor(
        stacked: Boolean,
        mode: LensMode,
    ): SourceDisplay {
        if (!stacked) return SourceDisplay.NORMAL
        return when (mode) {
            LensMode.AUTO, LensMode.DUAL_COLUMN -> SourceDisplay.DUAL_COLUMN
            LensMode.TOP -> SourceDisplay.TOP
            LensMode.BOTTOM -> SourceDisplay.BOTTOM
            LensMode.COMPACT -> SourceDisplay.COMPACT
        }
    }
}
