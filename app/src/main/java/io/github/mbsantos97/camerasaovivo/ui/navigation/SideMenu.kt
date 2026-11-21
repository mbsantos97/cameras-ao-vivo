package io.github.mbsantos97.camerasaovivo.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.BuildConfig
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKey
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKeyMapper
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.DrawerItem

enum class MenuDestination(
    val label: String,
    val icon: Int,
) {
    LIVE("Ao Vivo", AoVivoIcons.Grid),
    RECORDINGS("Gravações", AoVivoIcons.Recordings),
    SETTINGS("Configurações", AoVivoIcons.Settings),
    DIAGNOSTICS("Diagnóstico", AoVivoIcons.Diagnostics),
    ABOUT("Sobre", AoVivoIcons.Info),
    CATALOG("Catálogo do DS", AoVivoIcons.Grid),
}

/**
 * Menu lateral, aberto com ← a partir da coluna mais à esquerda da grade.
 * Fica totalmente oculto quando fechado (o vídeo usa a tela inteira).
 */
@Composable
fun SideMenu(
    open: Boolean,
    selected: MenuDestination,
    onSelect: (MenuDestination) -> Unit,
    onClose: () -> Unit,
) {
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.spacing
    val first = remember { FocusRequester() }
    val items =
        MenuDestination.entries.filter { it != MenuDestination.CATALOG || BuildConfig.DEBUG }
    AnimatedVisibility(open, enter = fadeIn(AoVivoTheme.motion.medium()), exit = fadeOut(AoVivoTheme.motion.medium())) {
        Box(Modifier.fillMaxSize().background(c.scrim)) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(AoVivoTheme.sizes.drawerWidth)
                    .background(c.surface)
                    .border(AoVivoTheme.sizes.hairline, c.outline)
                    .padding(horizontal = s.md, vertical = s.safeVertical)
                    .focusRestorer(first)
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && RemoteKeyMapper.map(e.nativeKeyEvent.keyCode) == RemoteKey.RIGHT) {
                            onClose()
                            true
                        } else {
                            false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(s.xxs),
            ) {
                Text(
                    "Câmeras ao Vivo",
                    style = AoVivoTheme.type.title,
                    color = c.accent,
                    modifier = Modifier.padding(start = s.sm, bottom = s.lg),
                )
                items.forEach { d ->
                    DrawerItem(
                        icon = d.icon,
                        label = d.label,
                        selected = d == selected,
                        onClick = { onSelect(d) },
                        modifier = if (d == selected) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}
