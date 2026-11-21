package io.github.mbsantos97.camerasaovivo.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.tv.material3.Text

/** Trecho gravado, em segundos desde a meia-noite (0..86400). */
@Immutable
data class TimelineSegment(
    val startSecond: Int,
    val endSecond: Int,
)

/**
 * Linha do tempo de 24 h desenhada inteira em um único Canvas.
 * O cursor é lido por lambda na fase de desenho: mover o cursor não recompõe nada.
 */
@Composable
fun TimelineBar(
    segments: List<TimelineSegment>,
    cursorSecond: () -> Int,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = AoVivoTheme.colors
    val sizes = AoVivoTheme.sizes
    val shape = AoVivoTheme.shapes
    val measurer = rememberTextMeasurer()
    val labelStyle = AoVivoTheme.type.caption.tabular().copy(color = c.textSecondary)
    val cursorStyle = AoVivoTheme.type.label.tabular().copy(color = c.textPrimary)
    val labels: List<TextLayoutResult> =
        remember(measurer, labelStyle) {
            (0..24 step 3).map { h -> measurer.measure("%02d:00".format(h), labelStyle) }
        }
    Canvas(modifier.fillMaxWidth().height(sizes.timelineHeight * 2)) {
        val trackTop = size.height / 2
        val trackH = sizes.timelineHeight.toPx() / 2
        val radius = CornerRadius(shape.radiusMdValue.toPx() / 2)
        val w = size.width
        drawRoundRect(c.surfaceRaised, Offset(0f, trackTop), Size(w, trackH), radius)
        for (seg in segments) {
            val x0 = w * seg.startSecond / DAY
            val x1 = w * seg.endSecond / DAY
            drawRect(c.accent, Offset(x0, trackTop), Size((x1 - x0).coerceAtLeast(1f), trackH))
        }
        if (focused) {
            drawRoundRect(c.focus, Offset(0f, trackTop), Size(w, trackH), radius, style = Stroke(sizes.hairline.toPx() * 2))
        }
        labels.forEachIndexed { i, layout ->
            val hour = i * 3
            val x = w * hour / 24f
            drawLine(c.outline, Offset(x, trackTop + trackH), Offset(x, trackTop + trackH + trackH / 3), sizes.hairline.toPx())
            val lx = (x - layout.size.width / 2f).coerceIn(0f, w - layout.size.width)
            drawText(layout, topLeft = Offset(lx, trackTop + trackH + trackH / 3))
        }
        val cur = cursorSecond().coerceIn(0, DAY)
        val cx = w * cur / DAY
        drawLine(c.focus, Offset(cx, trackTop - trackH / 3), Offset(cx, trackTop + trackH), sizes.timelineCursor.toPx())
        val text = "%02d:%02d:%02d".format(cur / 3600, cur / 60 % 60, cur % 60)
        val layout = measurer.measure(text, cursorStyle)
        val tx = (cx - layout.size.width / 2f).coerceIn(0f, w - layout.size.width)
        drawText(layout, topLeft = Offset(tx, trackTop - trackH / 3 - layout.size.height))
    }
}

@Immutable
data class DayItem(
    val epochDay: Long,
    val label: String,
    val weekday: String,
    val hasRecordings: Boolean,
)

/** Seletor de dia navegável pelo D-pad. Dias com gravação têm um ponto accent. */
@Composable
fun DayPicker(
    days: List<DayItem>,
    selectedEpochDay: Long?,
    onSelect: (DayItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
        items(days, key = { it.epochDay }) { d -> DayChip(d, d.epochDay == selectedEpochDay) { onSelect(d) } }
    }
}

@Composable
private fun DayChip(
    day: DayItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.spacing
    val shape = AoVivoTheme.shapes.radiusMd
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .aoVivoClickable(onClick = onClick)
            .aoVivoFocus(shape, FocusStyle.ROW, onFocusChange = { focused = it })
            .background(
                when {
                    focused -> c.surfaceHigh
                    selected -> c.accent
                    else -> c.surfaceRaised
                },
                shape,
            ).padding(horizontal = s.md, vertical = s.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val fg =
            if (selected && !focused) {
                c.onAccent
            } else if (day.hasRecordings) {
                c.textPrimary
            } else {
                c.textDisabled
            }
        Text(day.weekday, style = AoVivoTheme.type.caption, color = fg)
        Text(day.label, style = AoVivoTheme.type.label.tabular(), color = fg)
        Box(
            Modifier
                .padding(top = s.xxs)
                .size(AoVivoTheme.sizes.statusDot / 2)
                .background(if (day.hasRecordings) (if (selected && !focused) c.onAccent else c.accent) else c.surfaceRaised.copy(alpha = 0f), shape),
        )
    }
}

private const val DAY = 86_400
