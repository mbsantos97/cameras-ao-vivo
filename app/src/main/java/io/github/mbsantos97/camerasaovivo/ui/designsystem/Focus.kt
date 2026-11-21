package io.github.mbsantos97.camerasaovivo.ui.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import io.github.mbsantos97.camerasaovivo.core.input.LongPressDetector
import io.github.mbsantos97.camerasaovivo.core.input.PressResult
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKey
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKeyMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Estilo de foco: quadrante (anel branco externo + escala 1.04) ou linha/botão (fundo + escala 1.02). */
enum class FocusStyle { TILE, ROW }

/**
 * Torna o elemento focável e desenha o foco do Ao Vivo DS.
 * A escala é aplicada na fase de desenho (graphicsLayer), sem relayout nem recomposição.
 */
@Composable
fun Modifier.aoVivoFocus(
    shape: Shape,
    style: FocusStyle,
    onFocusChange: (Boolean) -> Unit = {},
    enabled: Boolean = true,
    applyScale: Boolean = true,
): Modifier {
    val colors = AoVivoTheme.colors
    val motion = AoVivoTheme.motion
    val sizes = AoVivoTheme.sizes
    var focused by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (focused) 1f else 0f, motion.fast(), label = "focus")
    val targetScale =
        if (!applyScale) {
            1f
        } else if (style == FocusStyle.TILE) {
            motion.focusScale
        } else {
            motion.rowFocusScale
        }
    val ringPx = with(LocalDensity.current) { sizes.focusRing.toPx() }
    val innerPx = with(LocalDensity.current) { sizes.hairline.toPx() }
    return this
        .onFocusChanged {
            focused = it.isFocused
            onFocusChange(it.isFocused)
        }.graphicsLayer {
            val s = 1f + (targetScale - 1f) * progress
            scaleX = s
            scaleY = s
        }.drawWithContent {
            drawContent()
            if (style == FocusStyle.ROW && progress > 0f) {
                // Linhas e botões: borda branca interna (o fundo surfaceHigh é aplicado pelo componente).
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, colors.focus.copy(alpha = progress), style = Stroke(ringPx * 2 / 3))
            }
            if (style == FocusStyle.TILE && progress > 0f) {
                // Contorno interno preto de 1dp + anel branco de 3dp por fora.
                val inner = shape.createOutline(size, layoutDirection, this)
                drawOutline(inner, colors.focusInner.copy(alpha = progress), style = Stroke(innerPx * 2))
                val grow = ringPx
                val outer = shape.createOutline(Size(size.width + grow * 2, size.height + grow * 2), layoutDirection, this)
                translate(-grow, -grow) {
                    drawOutline(outer, colors.focus.copy(alpha = progress), style = Stroke(ringPx * 2))
                }
            }
        }.focusable(enabled)
}

/**
 * Clique pelo controle: OK curto dispara no KeyUp; OK longo dispara ao completar
 * [AoVivoMotion.longPressMillis] segurando, e o KeyUp seguinte é ignorado.
 * Aplique ANTES de [aoVivoFocus] na cadeia, para receber as teclas do nó focado.
 */
@Composable
fun Modifier.aoVivoClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    val longMillis = AoVivoTheme.motion.longPressMillis
    val detector = remember { LongPressDetector(longMillis) }
    val scope = rememberCoroutineScope()
    var timer by remember { mutableStateOf<Job?>(null) }
    return this.onKeyEvent { event ->
        if (!enabled || RemoteKeyMapper.map(event.nativeKeyEvent.keyCode) != RemoteKey.OK) return@onKeyEvent false
        val native = event.nativeKeyEvent
        when (event.type) {
            KeyEventType.KeyDown -> {
                val wasPressed = detector.isPressed
                val r = detector.onKeyDown(native.eventTime, native.repeatCount)
                if (r == PressResult.LONG) onLongClick?.invoke()
                if (!wasPressed && native.repeatCount == 0 && onLongClick != null) {
                    timer?.cancel()
                    timer =
                        scope.launch {
                            delay(longMillis)
                            if (detector.onTick(android.os.SystemClock.uptimeMillis()) == PressResult.LONG) onLongClick()
                        }
                }
                true
            }
            KeyEventType.KeyUp -> {
                timer?.cancel()
                when (detector.onKeyUp(native.eventTime)) {
                    PressResult.SHORT -> onClick()
                    PressResult.LONG -> (onLongClick ?: onClick).invoke()
                    PressResult.NONE -> Unit
                }
                true
            }
            else -> {
                false
            }
        }
    }
}
