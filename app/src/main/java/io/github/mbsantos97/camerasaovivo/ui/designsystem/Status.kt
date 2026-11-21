package io.github.mbsantos97.camerasaovivo.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.tv.material3.Text

enum class ConnectionStatus { ONLINE, CONNECTING, OFFLINE }

@Composable
fun statusColor(status: ConnectionStatus): Color {
    val c = AoVivoTheme.colors
    return when (status) {
        ConnectionStatus.ONLINE -> c.online
        ConnectionStatus.CONNECTING -> c.connecting
        ConnectionStatus.OFFLINE -> c.danger
    }
}

/** Bolinha de status (verde online, amarelo conectando, vermelho offline). */
@Composable
fun StatusDot(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(AoVivoTheme.sizes.statusDot).background(statusColor(status), AoVivoTheme.shapes.pill))
}

/** Etiqueta curta ("PTZ", "REC", "SD", "Movimento"). */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AoVivoTheme.colors.scrim,
    textColor: Color = AoVivoTheme.colors.textPrimary,
) {
    Box(
        modifier
            .background(color, AoVivoTheme.shapes.radiusSm)
            .padding(horizontal = AoVivoTheme.spacing.xs, vertical = AoVivoTheme.spacing.xxs / 2),
    ) {
        Text(text, style = AoVivoTheme.type.caption, color = textColor, maxLines = 1)
    }
}

/** Ponto de REC piscando a 1 Hz (alpha na fase de desenho). */
@Composable
fun RecBlink(modifier: Modifier = Modifier) {
    val motion = AoVivoTheme.motion
    val t = rememberInfiniteTransition(label = "rec")
    val alpha =
        t.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(tween(motion.recBlinkMillis, easing = LinearEasing), RepeatMode.Reverse),
            label = "recAlpha",
        )
    Box(
        modifier
            .graphicsLayer { this.alpha = alpha.value }
            .size(AoVivoTheme.sizes.statusDot)
            .background(AoVivoTheme.colors.danger, AoVivoTheme.shapes.pill),
    )
}

/** Indicador de "conectando": arco girando. Só existe enquanto o vídeo não toca. */
@Composable
fun ConnectingIndicator(modifier: Modifier = Modifier) {
    val c = AoVivoTheme.colors
    val strokePx = AoVivoTheme.sizes.progressStroke
    val t = rememberInfiniteTransition(label = "connecting")
    val angle =
        t.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(AoVivoTheme.motion.slowMillis * 4, easing = LinearEasing)),
            label = "angle",
        )
    Canvas(modifier.size(AoVivoTheme.sizes.progress).graphicsLayer { rotationZ = angle.value }) {
        drawArc(
            color = c.textPrimary,
            startAngle = 0f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = strokePx.toPx(), cap = StrokeCap.Round),
        )
    }
}
