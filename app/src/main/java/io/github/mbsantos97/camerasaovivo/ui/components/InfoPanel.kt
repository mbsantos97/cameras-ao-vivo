package io.github.mbsantos97.camerasaovivo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.core.time.Formats
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectionStatus
import io.github.mbsantos97.camerasaovivo.ui.designsystem.StatusDot
import kotlinx.coroutines.delay

@Immutable
data class PanelCamera(
    val name: String,
    val status: ConnectionStatus,
)

@Immutable
data class PanelInfo(
    val cameras: List<PanelCamera> = emptyList(),
    /** Espaço livre do armazenamento de gravação local, ou null se a gravação estiver desligada. */
    val storageFree: String? = null,
)

/** Painel: relógio grande, data, status de cada câmera e espaço livre. */
@Composable
fun InfoPanel(
    info: PanelInfo,
    modifier: Modifier = Modifier,
) {
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.spacing
    Box(modifier.fillMaxSize().background(c.surface, AoVivoTheme.shapes.radiusMd), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = AoVivoTheme.sizes.panelMaxWidth).padding(s.lg),
            verticalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            Clock(style = AoVivoTheme.type.display, color = c.textPrimary)
            DateText()
            Column(Modifier.padding(top = s.sm), verticalArrangement = Arrangement.spacedBy(s.xxs)) {
                info.cameras.forEach { cam ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.xs)) {
                        StatusDot(cam.status)
                        Text(cam.name, style = AoVivoTheme.type.body, color = c.textPrimary, maxLines = 1)
                    }
                }
            }
            if (info.storageFree != null) {
                Text("Gravação local: ${info.storageFree} livres", style = AoVivoTheme.type.caption, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun DateText() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MINUTE - System.currentTimeMillis() % MINUTE)
            now = System.currentTimeMillis()
        }
    }
    Text(Formats.fullDate(now), style = AoVivoTheme.type.title, color = AoVivoTheme.colors.textSecondary)
}

private const val MINUTE = 60_000L
