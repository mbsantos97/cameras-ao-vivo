package io.github.mbsantos97.camerasaovivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.core.time.Formats
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import kotlinx.coroutines.delay

/** Área segura da TV: 48 dp horizontal, 27 dp vertical. */
@Composable
fun TvSafeArea(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = AoVivoTheme.spacing.safeHorizontal, vertical = AoVivoTheme.spacing.safeVertical),
        content = content,
    )
}

/** "Feito por mbsantos97" — canto inferior direito, não focável. */
@Composable
fun BoxScope.Watermark() {
    val c = AoVivoTheme.colors
    val t = AoVivoTheme.type
    Text(
        text = "Feito por mbsantos97",
        style = t.caption.copy(shadow = t.overlayShadow),
        color = c.textPrimary.copy(alpha = c.watermarkAlpha),
        modifier = Modifier.align(Alignment.BottomEnd),
    )
}

/** Relógio que se atualiza sozinho uma vez por segundo, sem recompor quem o contém. */
@Composable
fun Clock(
    modifier: Modifier = Modifier,
    style: TextStyle = AoVivoTheme.type.label,
    color: Color = AoVivoTheme.colors.textPrimary,
    withSeconds: Boolean = false,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(withSeconds) {
        while (true) {
            val step = 1_000L
            delay(step - System.currentTimeMillis() % step)
            now = System.currentTimeMillis()
        }
    }
    Text(
        text = if (withSeconds) Formats.hourMinuteSecond(now) else Formats.hourMinute(now),
        style = style,
        color = color,
        modifier = modifier,
    )
}

/**
 * Relógio opcional (pílula scrim, canto superior direito). Fica na margem superior da área segura,
 * fora da grade, para não cobrir as etiquetas do quadrante do canto. Use num Box de tela inteira.
 */
@Composable
fun BoxScope.OverlayClock() {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(end = AoVivoTheme.spacing.safeHorizontal, top = AoVivoTheme.spacing.xxs / 2)
            .background(AoVivoTheme.colors.scrim, AoVivoTheme.shapes.pill)
            .padding(horizontal = AoVivoTheme.spacing.sm, vertical = AoVivoTheme.spacing.xxs / 2),
    ) {
        Clock(style = AoVivoTheme.type.label)
    }
}
