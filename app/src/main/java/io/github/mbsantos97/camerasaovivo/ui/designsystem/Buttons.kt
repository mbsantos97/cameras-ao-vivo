package io.github.mbsantos97.camerasaovivo.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

enum class ButtonKind { PRIMARY, SECONDARY, GHOST, DANGER }

/** Botão do Ao Vivo DS: primário (accent), secundário (surfaceRaised), fantasma ou perigoso. */
@Composable
fun AoVivoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val c = AoVivoTheme.colors
    val shape = AoVivoTheme.shapes.radiusMd
    var focused by remember { mutableStateOf(false) }
    val bg =
        when {
            kind == ButtonKind.PRIMARY && enabled -> c.accent
            kind == ButtonKind.DANGER && enabled -> c.danger
            kind == ButtonKind.GHOST && !focused -> c.bg.copy(alpha = 0f)
            focused -> c.surfaceHigh
            else -> c.surfaceRaised
        }
    val fg =
        when {
            !enabled -> c.textDisabled
            kind == ButtonKind.PRIMARY -> c.onAccent
            kind == ButtonKind.DANGER -> c.bg
            else -> c.textPrimary
        }
    Row(
        modifier =
            modifier
                .aoVivoClickable(enabled = enabled, onClick = onClick)
                .aoVivoFocus(shape, FocusStyle.ROW, onFocusChange = { focused = it }, enabled = enabled)
                .height(AoVivoTheme.sizes.buttonHeight)
                .background(bg, shape)
                .padding(horizontal = AoVivoTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(AoVivoTheme.sizes.iconTile))
        }
        Text(text, style = AoVivoTheme.type.label, color = fg, maxLines = 1)
    }
}

/** Botão de ícone da barra de controles: o rótulo aparece abaixo quando focado. */
@Composable
fun AoVivoIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    val c = AoVivoTheme.colors
    val shape = AoVivoTheme.shapes.radiusMd
    var focused by remember { mutableStateOf(false) }
    val bg =
        when {
            focused -> c.surfaceHigh
            active -> c.accent
            else -> c.scrim
        }
    val tint =
        when {
            !enabled -> c.textDisabled
            active && !focused -> c.onAccent
            else -> c.textPrimary
        }
    Column(modifier = modifier.width(AoVivoTheme.sizes.thumbnailWidth / 3), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .aoVivoClickable(enabled = enabled, onClick = onClick)
                    .aoVivoFocus(shape, FocusStyle.ROW, onFocusChange = { focused = it }, enabled = enabled)
                    .size(AoVivoTheme.sizes.buttonHeight)
                    .background(bg, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = label, tint = tint, modifier = Modifier.size(AoVivoTheme.sizes.iconBar))
        }
        Text(
            text = label,
            style = AoVivoTheme.type.caption.copy(shadow = AoVivoTheme.type.overlayShadow),
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = AoVivoTheme.spacing.xxs).alpha(if (focused) 1f else 0f),
        )
    }
}

@Preview(widthDp = 640, heightDp = 200)
@Composable
private fun ButtonsPreview() {
    AoVivoTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.md), modifier = Modifier.padding(AoVivoTheme.spacing.md)) {
            AoVivoButton("Primário", {})
            AoVivoButton("Secundário", {}, kind = ButtonKind.SECONDARY)
            AoVivoButton("Fantasma", {}, kind = ButtonKind.GHOST)
            AoVivoButton("Desabilitado", {}, enabled = false)
            AoVivoIconButton(AoVivoIcons.Snapshot, "Foto", {})
        }
    }
}
