package io.github.mbsantos97.camerasaovivo.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

val LocalAoVivoColors = staticCompositionLocalOf { AoVivoColors() }
val LocalAoVivoType = staticCompositionLocalOf { AoVivoType() }
val LocalAoVivoSpacing = staticCompositionLocalOf { AoVivoSpacing() }
val LocalAoVivoSizes = staticCompositionLocalOf { AoVivoSizes() }
val LocalAoVivoShapes = staticCompositionLocalOf { AoVivoShapes() }
val LocalAoVivoMotion = staticCompositionLocalOf { AoVivoMotion() }

/** Acesso aos tokens: `AoVivoTheme.colors.accent`, `AoVivoTheme.spacing.md` etc. */
object AoVivoTheme {
    val colors: AoVivoColors
        @Composable @ReadOnlyComposable
        get() = LocalAoVivoColors.current
    val type: AoVivoType
        @Composable @ReadOnlyComposable
        get() = LocalAoVivoType.current
    val spacing: AoVivoSpacing
        @Composable @ReadOnlyComposable
        get() = LocalAoVivoSpacing.current
    val sizes: AoVivoSizes
        @Composable @ReadOnlyComposable
        get() = LocalAoVivoSizes.current
    val shapes: AoVivoShapes
        @Composable @ReadOnlyComposable
        get() = LocalAoVivoShapes.current
    val motion: AoVivoMotion
        @Composable @ReadOnlyComposable
        get() = LocalAoVivoMotion.current
}

/** Tema único (escuro). Mapeia os tokens para o MaterialTheme do tv-material. */
@Composable
fun AoVivoTheme(content: @Composable () -> Unit) {
    val colors = AoVivoColors()
    val type = AoVivoType()
    val scheme =
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.surfaceHigh,
            onSecondary = colors.textPrimary,
            background = colors.bg,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceRaised,
            onSurfaceVariant = colors.textSecondary,
            error = colors.danger,
            onError = colors.textPrimary,
            border = colors.focus,
            borderVariant = colors.outline,
            scrim = colors.scrim,
        )
    val typography =
        Typography(
            displayMedium = type.display,
            headlineMedium = type.headline,
            titleMedium = type.title,
            bodyMedium = type.body,
            bodyLarge = type.body,
            labelMedium = type.label,
            labelSmall = type.caption,
        )
    CompositionLocalProvider(
        LocalAoVivoColors provides colors,
        LocalAoVivoType provides type,
        LocalAoVivoSpacing provides AoVivoSpacing(),
        LocalAoVivoSizes provides AoVivoSizes(),
        LocalAoVivoShapes provides AoVivoShapes(),
        LocalAoVivoMotion provides AoVivoMotion(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
