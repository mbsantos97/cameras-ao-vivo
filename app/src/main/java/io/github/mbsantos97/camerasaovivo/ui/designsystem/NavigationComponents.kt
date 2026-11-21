package io.github.mbsantos97.camerasaovivo.ui.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

/** Item do menu lateral. Selecionado: marcador accent à esquerda. */
@Composable
fun DrawerItem(
    @DrawableRes icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.spacing
    val shape = AoVivoTheme.shapes.radiusMd
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .aoVivoClickable(onClick = onClick)
                .aoVivoFocus(shape, FocusStyle.ROW, onFocusChange = { focused = it })
                .height(AoVivoTheme.sizes.drawerItemHeight)
                .background(if (focused) c.surfaceHigh else c.surface, shape)
                .padding(horizontal = s.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.sm),
    ) {
        Box(
            Modifier
                .width(s.xxs)
                .height(s.lg)
                .background(if (selected) c.accent else c.surface.copy(alpha = 0f), AoVivoTheme.shapes.pill),
        )
        Icon(
            painterResource(icon),
            null,
            tint = if (selected || focused) c.textPrimary else c.textSecondary,
            modifier = Modifier.size(AoVivoTheme.sizes.iconBar),
        )
        Text(label, style = AoVivoTheme.type.body, color = if (selected || focused) c.textPrimary else c.textSecondary, maxLines = 1)
    }
}

/** Faixa inferior sobre gradiente estático de scrim, com ícones rotulados. */
@Composable
fun ControlBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.spacing
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.scrim.copy(alpha = 0f), c.scrim)))
            .padding(horizontal = s.safeHorizontal, vertical = s.lg),
        horizontalArrangement = Arrangement.spacedBy(s.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
        content = content,
    )
}
