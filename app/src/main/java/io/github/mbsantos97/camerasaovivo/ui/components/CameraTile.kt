package io.github.mbsantos97.camerasaovivo.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectingIndicator
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectionStatus
import io.github.mbsantos97.camerasaovivo.ui.designsystem.RecBlink
import io.github.mbsantos97.camerasaovivo.ui.designsystem.StatusDot
import io.github.mbsantos97.camerasaovivo.ui.designsystem.Tag

/** Estado visual de um quadrante (imutável: só recompõe quando algo muda de fato). */
@Immutable
data class TileUi(
    val name: String,
    val status: ConnectionStatus,
    val attempt: Int = 0,
    val videoVisible: Boolean = false,
    val recording: Boolean = false,
    val ptz: Boolean = false,
    val motion: Boolean = false,
    val metadata: String? = null,
    val subtitle: String? = null,
    val motorized: Boolean = false,
)

/**
 * Camada de informação de um quadrante, desenhada por cima do vídeo (irmã do VideoSurface).
 * - conectando: última miniatura esmaecida + indicador; nunca um quadrado preto vazio;
 * - offline: último frame escurecido + "Reconectando… (tentativa N)";
 * - movimento: borda danger de 3 dp + etiqueta.
 */
@Composable
fun CameraTileOverlay(
    tile: TileUi,
    thumbnail: ImageBitmap?,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
) {
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.spacing
    val shape = AoVivoTheme.shapes.radiusMd
    Box(modifier.fillMaxSize()) {
        if (!tile.videoVisible) {
            Box(Modifier.fillMaxSize().background(c.surfaceRaised, shape))
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(1f - c.dimAlpha),
                )
            }
            Column(
                Modifier.align(Alignment.Center).padding(s.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(s.xs),
            ) {
                if (tile.status != ConnectionStatus.OFFLINE) ConnectingIndicator()
                Text(
                    text =
                        when (tile.status) {
                            ConnectionStatus.OFFLINE -> {
                                if (tile.attempt > 0) "Reconectando… (tentativa ${tile.attempt})" else "Câmera offline"
                            }
                            else -> {
                                "Conectando…"
                            }
                        },
                    style = AoVivoTheme.type.label.copy(shadow = AoVivoTheme.type.overlayShadow),
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (tile.status == ConnectionStatus.OFFLINE) {
            Box(Modifier.fillMaxSize().background(c.scrim, shape))
            Text(
                if (tile.attempt > 0) "Reconectando… (tentativa ${tile.attempt})" else "Câmera offline",
                style = AoVivoTheme.type.label,
                color = c.textPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (showLabels) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(s.xs)
                    .background(c.scrim, AoVivoTheme.shapes.pill)
                    .padding(horizontal = s.sm, vertical = s.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.xs),
            ) {
                StatusDot(tile.status)
                Text(tile.name, style = AoVivoTheme.type.label, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tile.subtitle != null) {
                    Text(tile.subtitle, style = AoVivoTheme.type.caption, color = c.textSecondary, maxLines = 1)
                }
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(s.xs),
                horizontalArrangement = Arrangement.spacedBy(s.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tile.recording) {
                    Row(
                        Modifier.background(c.scrim, AoVivoTheme.shapes.radiusSm).padding(horizontal = s.xs, vertical = s.xxs / 2),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(s.xxs),
                    ) {
                        RecBlink()
                        Text("REC", style = AoVivoTheme.type.caption, color = c.textPrimary)
                    }
                }
                if (tile.motion) Tag("Movimento", color = c.danger, textColor = c.bg)
                if (tile.motorized) Tag("Motorizada")
                if (tile.ptz) Tag("PTZ")
                if (tile.metadata != null) Tag(tile.metadata)
            }
        }
        if (tile.motion) {
            Box(Modifier.fillMaxSize().border(AoVivoTheme.sizes.motionBorder, c.danger, shape))
        }
    }
}
