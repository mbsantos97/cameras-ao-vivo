package io.github.mbsantos97.camerasaovivo.ui.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Tokens do Ao Vivo DS. Nenhuma tela usa cor, tamanho, espaçamento ou duração avulsos:
 * tudo vem daqui (ver docs/DESIGN_SYSTEM.md).
 */
@Immutable
data class AoVivoColors(
    val bg: Color = Color(0xFF000000),
    val surface: Color = Color(0xFF121417),
    val surfaceRaised: Color = Color(0xFF1B1E23),
    val surfaceHigh: Color = Color(0xFF252931),
    val scrim: Color = Color(0x99000000),
    val outline: Color = Color(0xFF2C313A),
    val textPrimary: Color = Color(0xFFF5F7FA),
    val textSecondary: Color = Color(0xFFA9B1BE),
    val textDisabled: Color = Color(0xFF5E6672),
    val accent: Color = Color(0xFF38BDF8),
    val onAccent: Color = Color(0xFF00131D),
    val focus: Color = Color(0xFFFFFFFF),
    val online: Color = Color(0xFF22C55E),
    val connecting: Color = Color(0xFFF59E0B),
    val danger: Color = Color(0xFFEF4444),
    /** Contorno interno do anel de foco: garante contraste sobre vídeo claro. */
    val focusInner: Color = Color(0xFF000000),
    /** Opacidades usadas sobre os tokens acima. */
    val watermarkAlpha: Float = 0.55f,
    val dimAlpha: Float = 0.45f,
)

@Immutable
data class AoVivoType(
    val display: TextStyle = style(56, FontWeight.Light, tabular = true),
    val headline: TextStyle = style(28, FontWeight.Medium),
    val title: TextStyle = style(20, FontWeight.Medium),
    val body: TextStyle = style(16, FontWeight.Normal),
    val label: TextStyle = style(14, FontWeight.Medium),
    val caption: TextStyle = style(13, FontWeight.Medium),
    /** Sombra de texto para legibilidade sobre vídeo (marca d'água, rótulos). */
    val overlayShadow: Shadow = Shadow(color = Color(0x99000000), offset = Offset(0f, 1f), blurRadius = 2f),
) {
    companion object {
        private fun style(
            size: Int,
            weight: FontWeight,
            tabular: Boolean = false,
        ) = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = size.sp,
            fontWeight = weight,
            lineHeight = (size * 1.3f).sp,
            fontFeatureSettings = if (tabular) "tnum" else null,
        )
    }
}

/** Números tabulares (relógio, linha do tempo, estatísticas). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

@Immutable
data class AoVivoSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val safeHorizontal: Dp = 48.dp,
    val safeVertical: Dp = 27.dp,
    val gridGap: Dp = 8.dp,
)

@Immutable
data class AoVivoSizes(
    val hairline: Dp = 1.dp,
    val focusRing: Dp = 3.dp,
    val motionBorder: Dp = 3.dp,
    val iconBar: Dp = 24.dp,
    val iconTile: Dp = 20.dp,
    val statusDot: Dp = 8.dp,
    val drawerWidth: Dp = 280.dp,
    val drawerItemHeight: Dp = 48.dp,
    val rowHeight: Dp = 56.dp,
    val buttonHeight: Dp = 48.dp,
    val dialogWidth: Dp = 520.dp,
    val switchWidth: Dp = 40.dp,
    val switchHeight: Dp = 22.dp,
    val timelineHeight: Dp = 56.dp,
    val timelineCursor: Dp = 2.dp,
    val panelMaxWidth: Dp = 560.dp,
    val thumbnailWidth: Dp = 240.dp,
    val ptzArrow: Dp = 64.dp,
    val qrSize: Dp = 220.dp,
    val progress: Dp = 28.dp,
    val progressStroke: Dp = 3.dp,
)

@Immutable
data class AoVivoShapes(
    val radiusSm: RoundedCornerShape = RoundedCornerShape(4.dp),
    val radiusMd: RoundedCornerShape = RoundedCornerShape(8.dp),
    val radiusLg: RoundedCornerShape = RoundedCornerShape(12.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
    val radiusMdValue: Dp = 8.dp,
)

@Immutable
data class AoVivoMotion(
    val fastMillis: Int = 120,
    val mediumMillis: Int = 200,
    val slowMillis: Int = 250,
    val crossfadeMillis: Int = 150,
    val toastMillis: Long = 3_000,
    val controlBarHideMillis: Long = 5_000,
    val exitConfirmMillis: Long = 2_000,
    val longPressMillis: Long = 600,
    val recBlinkMillis: Int = 500,
    val focusScale: Float = 1.04f,
    val rowFocusScale: Float = 1.02f,
) {
    fun <T> fast(): TweenSpec<T> = tween(fastMillis, easing = FastOutSlowInEasing)

    fun <T> medium(): TweenSpec<T> = tween(mediumMillis, easing = FastOutSlowInEasing)

    fun <T> slow(): TweenSpec<T> = tween(slowMillis, easing = FastOutSlowInEasing)
}

/** Contraste WCAG entre duas cores opacas (1..21). */
fun contrastRatio(
    foreground: Color,
    background: Color,
): Double {
    val l1 = relativeLuminance(foreground)
    val l2 = relativeLuminance(background)
    return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
}

private fun relativeLuminance(c: Color): Double {
    fun channel(v: Float): Double {
        val d = v.toDouble()
        return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
}
