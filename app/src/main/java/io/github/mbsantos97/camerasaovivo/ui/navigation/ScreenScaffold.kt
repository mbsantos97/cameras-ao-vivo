package io.github.mbsantos97.camerasaovivo.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.EmptyState

/** Estrutura comum das telas fora do vídeo: fundo surface, título e conteúdo na área segura. */
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .background(c.surface)
            .padding(horizontal = s.safeHorizontal, vertical = s.safeVertical),
        verticalArrangement = Arrangement.spacedBy(s.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.sm)) {
            if (icon != null) Icon(painterResource(icon), null, tint = c.accent, modifier = Modifier.size(AoVivoTheme.sizes.iconBar))
            Text(title, style = AoVivoTheme.type.headline, color = c.textPrimary, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    @DrawableRes icon: Int,
    onBack: () -> Unit,
) {
    ScreenScaffold(title, icon = icon) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(title = "$title chega numa próxima fase", icon = icon, actionText = "Voltar", onAction = onBack)
        }
    }
}
