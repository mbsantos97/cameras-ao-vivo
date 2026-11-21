package io.github.mbsantos97.camerasaovivo.ui.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.ui.components.CameraTileOverlay
import io.github.mbsantos97.camerasaovivo.ui.components.InfoPanel
import io.github.mbsantos97.camerasaovivo.ui.components.PanelCamera
import io.github.mbsantos97.camerasaovivo.ui.components.PanelInfo
import io.github.mbsantos97.camerasaovivo.ui.components.TileUi
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoButton
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIconButton
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ButtonKind
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectionStatus
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ControlBar
import io.github.mbsantos97.camerasaovivo.ui.designsystem.DangerRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.DayItem
import io.github.mbsantos97.camerasaovivo.ui.designsystem.DayPicker
import io.github.mbsantos97.camerasaovivo.ui.designsystem.DrawerItem
import io.github.mbsantos97.camerasaovivo.ui.designsystem.EmptyState
import io.github.mbsantos97.camerasaovivo.ui.designsystem.FocusStyle
import io.github.mbsantos97.camerasaovivo.ui.designsystem.LocalToast
import io.github.mbsantos97.camerasaovivo.ui.designsystem.NavigationRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.SectionHeader
import io.github.mbsantos97.camerasaovivo.ui.designsystem.SelectorRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.Skeleton
import io.github.mbsantos97.camerasaovivo.ui.designsystem.StatusDot
import io.github.mbsantos97.camerasaovivo.ui.designsystem.Tag
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextEntryRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TimelineBar
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TimelineSegment
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ToggleRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.aoVivoClickable
import io.github.mbsantos97.camerasaovivo.ui.designsystem.aoVivoFocus
import io.github.mbsantos97.camerasaovivo.ui.navigation.ScreenScaffold

/** Catálogo do Ao Vivo DS (só no debug): todos os tokens e componentes, navegável pelo controle. */
@Composable
fun DesignCatalogScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.spacing
    val toast = LocalToast.current
    var dialog by remember { mutableStateOf(false) }
    var toggle by remember { mutableStateOf(true) }
    var selector by remember { mutableIntStateOf(1) }
    var cursor by remember { mutableIntStateOf(13 * 3600) }
    ScreenScaffold("Catálogo do Ao Vivo DS", icon = AoVivoIcons.Grid) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(s.sm)) {
            item { SectionHeader("Cores") }
            item {
                val swatches =
                    listOf(
                        "bg" to c.bg,
                        "surface" to c.surface,
                        "surfaceRaised" to c.surfaceRaised,
                        "surfaceHigh" to c.surfaceHigh,
                        "scrim" to c.scrim,
                        "outline" to c.outline,
                        "textPrimary" to c.textPrimary,
                        "textSecondary" to c.textSecondary,
                        "textDisabled" to c.textDisabled,
                        "accent" to c.accent,
                        "onAccent" to c.onAccent,
                        "focus" to c.focus,
                        "online" to c.online,
                        "connecting" to c.connecting,
                        "danger" to c.danger,
                    )
                Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
                    swatches.chunked(5).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(s.xs)) { row.forEach { (n, col) -> Swatch(n, col) } }
                    }
                }
            }
            item { SectionHeader("Tipografia") }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(s.xxs)) {
                    val t = AoVivoTheme.type
                    Text("12:34 display 56", style = t.display, color = c.textPrimary)
                    Text("Título de tela — headline 28", style = t.headline, color = c.textPrimary)
                    Text("Seção — title 20", style = t.title, color = c.textPrimary)
                    Text("Texto de configuração — body 16", style = t.body, color = c.textPrimary)
                    Text("Rótulo de quadrante — label 14", style = t.label, color = c.textSecondary)
                    Text("Metadados e marca d'água — caption 13", style = t.caption, color = c.textSecondary)
                }
            }
            item { SectionHeader("Espaçamento (grade de 4 dp)") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(s.md)) {
                    listOf("xxs" to s.xxs, "xs" to s.xs, "sm" to s.sm, "md" to s.md, "lg" to s.lg, "xl" to s.xl, "xxl" to s.xxl).forEach { (n, d) ->
                        SpaceSample(n, d)
                    }
                }
            }
            item { SectionHeader("Botões") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                    AoVivoButton("Primário", { toast.show("Primário") }, icon = AoVivoIcons.Check)
                    AoVivoButton("Secundário", { toast.show("Secundário") }, kind = ButtonKind.SECONDARY)
                    AoVivoButton("Fantasma", { toast.show("Fantasma") }, kind = ButtonKind.GHOST)
                    AoVivoButton("Perigoso", { toast.show("Perigoso") }, kind = ButtonKind.DANGER)
                    AoVivoButton("Desabilitado", {}, enabled = false)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(s.xs)) {
                    AoVivoIconButton(AoVivoIcons.Snapshot, "Foto", {})
                    AoVivoIconButton(AoVivoIcons.Record, "Gravar", {}, active = true)
                    AoVivoIconButton(AoVivoIcons.VolumeOff, "Áudio", {})
                    AoVivoIconButton(AoVivoIcons.Preset, "Presets", {})
                    AoVivoIconButton(AoVivoIcons.ZoomIn, "Zoom", {})
                    AoVivoIconButton(AoVivoIcons.Lens, "Lente", {}, enabled = false)
                }
            }
            item { SectionHeader("Status e etiquetas") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(s.sm)) {
                    StatusDot(ConnectionStatus.ONLINE)
                    StatusDot(ConnectionStatus.CONNECTING)
                    StatusDot(ConnectionStatus.OFFLINE)
                    Tag("PTZ")
                    Tag("REC", color = c.danger, textColor = c.bg)
                    Tag("SD")
                    Tag("Movimento", color = c.danger, textColor = c.bg)
                }
            }
            item { SectionHeader("Linhas de configuração") }
            item { NavigationRow("Câmeras", {}, value = "2", icon = AoVivoIcons.Camera) }
            item { ToggleRow("Mostrar relógio", toggle, { toggle = it }, subtitle = "Canto superior direito") }
            item { SelectorRow("Latência", listOf("Baixa", "Normal", "Estável"), selector, { selector = it }) }
            item { TextEntryRow("Senha", "segredo", {}, masked = true, icon = AoVivoIcons.Lock) }
            item { DangerRow("Remover câmera", { dialog = true }) }
            item { NavigationRow("Desabilitada", {}, enabled = false) }
            item { SectionHeader("Quadrantes") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(s.md)) {
                    TileSample(TileUi("Conectando", ConnectionStatus.CONNECTING, ptz = true))
                    TileSample(TileUi("Ao vivo", ConnectionStatus.ONLINE, videoVisible = true, ptz = true, recording = true))
                    TileSample(TileUi("Offline", ConnectionStatus.OFFLINE, attempt = 3))
                    TileSample(TileUi("Movimento", ConnectionStatus.ONLINE, videoVisible = true, motion = true))
                }
            }
            item { SectionHeader("Linha do tempo e dias") }
            item {
                DayPicker(
                    days = (0 until 7).map { DayItem(it.toLong(), "${15 + it}/11", listOf("Sáb", "Dom", "Seg", "Ter", "Qua", "Qui", "Sex")[it], it % 3 != 1) },
                    selectedEpochDay = 2,
                    onSelect = { toast.show(it.label) },
                )
            }
            item {
                var focused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .aoVivoClickable { cursor = (cursor + 3600) % 86_400 }
                        .aoVivoFocus(AoVivoTheme.shapes.radiusMd, FocusStyle.ROW, onFocusChange = { focused = it }, applyScale = false),
                ) {
                    TimelineBar(
                        segments = listOf(TimelineSegment(0, 7200), TimelineSegment(30_000, 45_000), TimelineSegment(60_000, 61_000)),
                        cursorSecond = { cursor },
                        focused = focused,
                    )
                }
            }
            item { SectionHeader("Menu, Painel e barra de controles") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(s.md)) {
                    Column(Modifier.width(AoVivoTheme.sizes.drawerWidth).background(c.surface), verticalArrangement = Arrangement.spacedBy(s.xxs)) {
                        DrawerItem(AoVivoIcons.Grid, "Ao Vivo", true, {})
                        DrawerItem(AoVivoIcons.Recordings, "Gravações", false, {})
                    }
                    Box(Modifier.width(AoVivoTheme.sizes.panelMaxWidth).aspectRatio(16f / 9f)) {
                        InfoPanel(
                            PanelInfo(
                                listOf(PanelCamera("Câmera Externa", ConnectionStatus.ONLINE), PanelCamera("Lâmpada", ConnectionStatus.OFFLINE)),
                                "12,4 GB",
                            ),
                        )
                    }
                }
            }
            item {
                Box(Modifier.fillMaxWidth().background(c.surfaceRaised)) {
                    ControlBar {
                        AoVivoIconButton(AoVivoIcons.Snapshot, "Foto", {})
                        AoVivoIconButton(AoVivoIcons.Record, "Gravar", {})
                        AoVivoIconButton(AoVivoIcons.Info, "Informações", {})
                    }
                }
            }
            item { SectionHeader("Estados vazios e carregamento") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(s.md)) {
                    EmptyState("Sem gravações neste dia", icon = AoVivoIcons.Recordings, message = "Escolha outro dia.")
                    Skeleton(Modifier.width(AoVivoTheme.sizes.thumbnailWidth).height(AoVivoTheme.sizes.rowHeight * 2))
                }
            }
            item { AoVivoButton("Abrir diálogo", { dialog = true }, kind = ButtonKind.SECONDARY) }
        }
    }
    if (dialog) {
        AoVivoDialog(
            title = "Remover câmera?",
            message = "A configuração desta câmera será apagada da TV Box.",
            confirmText = "Remover",
            onConfirm = { dialog = false },
            onDismiss = { dialog = false },
            danger = true,
        )
    }
}

@Composable
private fun Swatch(
    name: String,
    color: Color,
) {
    val s = AoVivoTheme.spacing
    Column(Modifier.width(AoVivoTheme.sizes.thumbnailWidth / 1.5f)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(s.xxl)
                .background(color, AoVivoTheme.shapes.radiusSm)
                .border(AoVivoTheme.sizes.hairline, AoVivoTheme.colors.outline, AoVivoTheme.shapes.radiusSm),
        )
        Text(name, style = AoVivoTheme.type.caption, color = AoVivoTheme.colors.textSecondary)
    }
}

@Composable
private fun SpaceSample(
    name: String,
    size: Dp,
) {
    Column {
        Box(Modifier.size(size).background(AoVivoTheme.colors.accent))
        Text(name, style = AoVivoTheme.type.caption, color = AoVivoTheme.colors.textSecondary, modifier = Modifier.padding(top = AoVivoTheme.spacing.xxs))
    }
}

@Composable
private fun TileSample(tile: TileUi) {
    Box(
        Modifier
            .width(AoVivoTheme.sizes.thumbnailWidth * 1.3f)
            .aspectRatio(16f / 9f)
            .background(if (tile.videoVisible) AoVivoTheme.colors.outline else AoVivoTheme.colors.bg, AoVivoTheme.shapes.radiusMd)
            .aoVivoClickable {}
            .aoVivoFocus(AoVivoTheme.shapes.radiusMd, FocusStyle.TILE),
    ) {
        CameraTileOverlay(tile, thumbnail = null)
    }
}
