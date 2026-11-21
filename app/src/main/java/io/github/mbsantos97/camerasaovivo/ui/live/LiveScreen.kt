package io.github.mbsantos97.camerasaovivo.ui.live

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKey
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKeyMapper
import io.github.mbsantos97.camerasaovivo.player.VideoSurface
import io.github.mbsantos97.camerasaovivo.ui.components.CameraTileOverlay
import io.github.mbsantos97.camerasaovivo.ui.components.InfoPanel
import io.github.mbsantos97.camerasaovivo.ui.components.OverlayClock
import io.github.mbsantos97.camerasaovivo.ui.components.TileUi
import io.github.mbsantos97.camerasaovivo.ui.components.TvSafeArea
import io.github.mbsantos97.camerasaovivo.ui.components.Watermark
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.EmptyState
import io.github.mbsantos97.camerasaovivo.ui.designsystem.FocusStyle
import io.github.mbsantos97.camerasaovivo.ui.designsystem.LocalToast
import io.github.mbsantos97.camerasaovivo.ui.designsystem.NavigationRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.aoVivoClickable
import io.github.mbsantos97.camerasaovivo.ui.designsystem.aoVivoFocus
import io.github.mbsantos97.camerasaovivo.ui.navigation.MenuDestination
import io.github.mbsantos97.camerasaovivo.ui.navigation.SideMenu

/** Chave de um alvo de foco na grade (uma fonte, ou uma das lentes da coluna dupla). */
private data class SlotKey(
    val sourceId: String,
    val slot: LensSlot,
)

@Composable
fun LiveScreen(
    vm: LiveViewModel,
    onMenu: (MenuDestination) -> Unit,
    onOpenRecordings: (cameraId: String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fsUi by vm.fullscreen.ui.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val activity = LocalActivity.current
    var menuOpen by remember { mutableStateOf(false) }
    var tileMenu by remember { mutableStateOf<SlotKey?>(null) }
    var lastBack by remember { mutableLongStateOf(0L) }
    val requesters = remember { mutableStateMapOf<SlotKey, FocusRequester>() }
    var lastFocused by remember { mutableStateOf<SlotKey?>(null) }
    val fullscreenFocus = remember { FocusRequester() }
    val exitMillis = AoVivoTheme.motion.exitConfirmMillis

    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }

    BackHandler {
        when {
            menuOpen -> {
                menuOpen = false
            }
            state.fullscreen != null -> {
                vm.exitFullscreen()
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBack < exitMillis) {
                    activity?.finish()
                } else {
                    lastBack = now
                    toast.show("Pressione Voltar novamente para sair")
                }
            }
        }
    }

    fun requesterFor(k: SlotKey) = requesters.getOrPut(k) { FocusRequester() }

    // Foco inicial e restauração ao sair da tela cheia / fechar o menu.
    LaunchedEffect(state.fullscreen, menuOpen, state.grid) {
        if (state.fullscreen != null) {
            runCatching { fullscreenFocus.requestFocus() }
        } else if (!menuOpen) {
            val target = lastFocused?.takeIf { it in requesters } ?: requesters.keys.firstOrNull()
            target?.let { runCatching { requesters[it]?.requestFocus() } }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AoVivoTheme.colors.bg)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || menuOpen) return@onPreviewKeyEvent false
                val key = RemoteKeyMapper.map(e.nativeKeyEvent.keyCode) ?: return@onPreviewKeyEvent false
                if (state.fullscreen != null) {
                    when (key) {
                        RemoteKey.CHANNEL_UP, RemoteKey.MEDIA_NEXT -> vm.nextFullscreen(1)
                        RemoteKey.CHANNEL_DOWN, RemoteKey.MEDIA_PREVIOUS -> vm.nextFullscreen(-1)
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                } else {
                    when (key) {
                        RemoteKey.CHANNEL_UP -> if (state.grid.pageCount > 1) vm.changePage(1) else vm.cycleLayout()
                        RemoteKey.CHANNEL_DOWN -> if (state.grid.pageCount > 1) vm.changePage(-1) else vm.cycleLayout()
                        RemoteKey.MEDIA_NEXT, RemoteKey.MEDIA_PREVIOUS -> vm.cycleLayout()
                        RemoteKey.MENU -> tileMenu = lastFocused
                        else -> return@onPreviewKeyEvent false
                    }
                    true
                }
            },
    ) {
        if (state.noCameras) {
            TvSafeArea {
                EmptyState(
                    title = "Nenhuma câmera configurada",
                    message = "Adicione uma câmera em Configurações, pelo controle ou pelo celular.",
                    icon = AoVivoIcons.Camera,
                    actionText = "Configurar",
                    onAction = { onMenu(MenuDestination.SETTINGS) },
                    modifier = Modifier.align(Alignment.Center),
                )
                Watermark()
            }
        } else {
            LiveGrid(
                state = state,
                fsUi = fsUi,
                vm = vm,
                thumbnail = { id -> vm.thumbnail(id) },
                requesterFor = ::requesterFor,
                onSlotFocused = { lastFocused = it },
                onOpenMenu = { menuOpen = true },
                onTileMenu = { tileMenu = it },
            )
            if (state.fullscreen == null) {
                if (state.showClock) OverlayClock()
                TvSafeArea { Watermark() }
            } else {
                state.source(state.fullscreen!!.sourceId)?.let { src ->
                    FullscreenLayer(vm, src, fsUi, fullscreenFocus, onOpenRecordings)
                }
            }
        }

        if (state.perfOverlay) io.github.mbsantos97.camerasaovivo.ui.perf.PerfOverlay(vm, Modifier.align(Alignment.TopStart))

        SideMenu(
            open = menuOpen,
            selected = MenuDestination.LIVE,
            onSelect = { dest ->
                menuOpen = false
                if (dest != MenuDestination.LIVE) onMenu(dest)
            },
            onClose = { menuOpen = false },
        )
    }

    val menuTarget = tileMenu
    if (menuTarget != null) {
        val source = state.source(menuTarget.sourceId)
        if (source == null) {
            tileMenu = null
        } else {
            TileMenuDialog(
                source = source,
                onDismiss = { tileMenu = null },
                onFullscreen = {
                    tileMenu = null
                    vm.openFullscreen(source.id, if (menuTarget.slot == LensSlot.WHOLE) source.defaultSlot() else menuTarget.slot)
                },
                onRecordings = {
                    tileMenu = null
                    onOpenRecordings(source.cameraId)
                },
                onReconnect = {
                    tileMenu = null
                    vm.reconnect(source.id)
                    toast.show("Reconectando ${source.name}…")
                },
                quality =
                    if (vm.cameraOf(source.id)?.gridQuality ==
                        io.github.mbsantos97.camerasaovivo.data.config.StreamQuality.MAIN
                    ) {
                        "Principal"
                    } else {
                        "Substream"
                    },
                lensMode = lensModeLabel(vm.cameraOf(source.id)?.lensMode),
                onQuality = { vm.toggleQuality(source.id) },
                onLensMode = { vm.cycleLensMode(source.id) },
                onHide = {
                    tileMenu = null
                    vm.hide(source.id)
                    toast.show("${source.name} oculta. Para mostrar de novo: Configurações → Câmeras.")
                },
            )
        }
    }
}

@Composable
private fun LiveGrid(
    state: LiveUiState,
    fsUi: FullscreenUi,
    vm: LiveViewModel,
    thumbnail: (String) -> ImageBitmap?,
    requesterFor: (SlotKey) -> FocusRequester,
    onSlotFocused: (SlotKey) -> Unit,
    onOpenMenu: () -> Unit,
    onTileMenu: (SlotKey) -> Unit,
) {
    val spacing = AoVivoTheme.spacing
    val grid = state.grid
    val fs = state.fullscreen

    @Suppress("UNUSED_VARIABLE")
    val thumbVersion = state.thumbVersion
    val composedIds =
        buildList {
            grid.cells.forEach { c -> (c.content as? CellContent.Source)?.let { add(it.id) } }
            if (fs != null && fs.sourceId !in this) add(fs.sourceId)
        }
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            composedIds.forEach { id ->
                val source = state.source(id) ?: return@forEach
                val cell = grid.cellOf(id)
                key(id) {
                    SourceCell(
                        source = source,
                        fsUi = fsUi,
                        tile = state.tiles[id] ?: TileUi(source.name, io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectionStatus.CONNECTING),
                        cell = cell,
                        fullscreenSlot = if (fs?.sourceId == id) fs.slot else null,
                        state = state,
                        vm = vm,
                        thumbnail = thumbnail(source.cameraId),
                        requesterFor = requesterFor,
                        onSlotFocused = onSlotFocused,
                        onOpenMenu = onOpenMenu,
                        onTileMenu = onTileMenu,
                        modifier = Modifier.layoutId(id),
                    )
                }
            }
            if (fs == null) {
                grid.cells.filter { it.content == CellContent.Panel }.forEach { cell ->
                    key("panel") { InfoPanel(state.panel, Modifier.layoutId("panel")) }
                }
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val padH = spacing.safeHorizontal.roundToPx()
        val padV = spacing.safeVertical.roundToPx()
        val gap = spacing.gridGap.roundToPx()
        val areaW = w - padH * 2
        val areaH = h - padV * 2
        val cellW = (areaW - gap * (grid.columns - 1)) / grid.columns
        val cellH = (areaH - gap * (grid.rows - 1)) / grid.rows

        fun rect(c: GridCell): IntArray {
            val x = padH + c.col * (cellW + gap)
            val y = padV + c.row * (cellH + gap)
            return intArrayOf(x, y, c.colSpan * cellW + (c.colSpan - 1) * gap, c.rowSpan * cellH + (c.rowSpan - 1) * gap)
        }
        val placements =
            measurables.mapNotNull { m ->
                val id = m.layoutId as? String ?: return@mapNotNull null
                val r =
                    when {
                        fs != null && id == fs.sourceId -> intArrayOf(0, 0, w, h)
                        fs != null -> return@mapNotNull null
                        id == "panel" -> grid.cells.firstOrNull { it.content == CellContent.Panel }?.let(::rect)
                        else -> grid.cellOf(id)?.let(::rect)
                    } ?: return@mapNotNull null
                m.measure(Constraints.fixed(r[2].coerceAtLeast(0), r[3].coerceAtLeast(0))) to r
            }
        layout(w, h) {
            placements.forEach { (p, r) -> p.place(r[0], r[1]) }
        }
    }
}

@Composable
private fun SourceCell(
    source: LiveSource,
    fsUi: FullscreenUi,
    tile: TileUi,
    cell: GridCell?,
    fullscreenSlot: LensSlot?,
    state: LiveUiState,
    vm: LiveViewModel,
    thumbnail: ImageBitmap?,
    requesterFor: (SlotKey) -> FocusRequester,
    onSlotFocused: (SlotKey) -> Unit,
    onOpenMenu: () -> Unit,
    onTileMenu: (SlotKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = AoVivoTheme.motion
    val fullscreen = fullscreenSlot != null
    val tallPlacement = (cell?.content as? CellContent.Source)?.tallPlacement == true
    val split = source.tall && tallPlacement && !fullscreen
    var focusedSlots by remember { mutableIntStateOf(0) }
    val scale by animateFloatAsState(
        if (focusedSlots > 0 && !split && !fullscreen) motion.focusScale else 1f,
        motion.fast(),
        label = "cellScale",
    )
    val player = remember(source.id) { vm.playerFor(source.id) }
    val region =
        if (fullscreenSlot != null) {
            source.fullscreenRegion(fullscreenSlot).zoomed(fsUi.zoom, fsUi.zoomCenterX, fsUi.zoomCenterY)
        } else {
            source.gridRegion(tallPlacement)
        }
    Box(
        modifier
            .zIndex(if (focusedSlots > 0) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        VideoSurface(
            player = player,
            region = region,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = if (fullscreen) null else AoVivoTheme.shapes.radiusMdValue,
            renderMode = state.renderMode,
        )
        if (fullscreen) {
            val mainPlayer = vm.mainPlayerFor(source.id)
            if (mainPlayer != null) {
                val alpha by animateFloatAsState(
                    if (state.mainReady) 1f else 0f,
                    tween(motion.crossfadeMillis),
                    label = "crossfade",
                )
                key(mainPlayer) {
                    VideoSurface(
                        player = mainPlayer,
                        region = region,
                        modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                        renderMode = state.renderMode,
                        fallbackSize =
                            vm.cameraOf(source.id)?.streams?.sources?.getOrNull(source.sourceIndex)?.let {
                                io.github.mbsantos97.camerasaovivo.player.VideoSize(it.mainWidth, it.mainHeight)
                            }?.takeIf { it.width > 0 && it.height > 0 },
                    )
                }
            }
        }
        if (fullscreen) {
            CameraTileOverlay(
                tile =
                    tile.copy(
                        subtitle = lensLabel(source, fullscreenSlot),
                        status = if (state.mainReady) io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectionStatus.ONLINE else tile.status,
                        videoVisible = state.mainReady || tile.videoVisible,
                        attempt = if (state.mainReady) 0 else tile.attempt,
                    ),
                thumbnail = thumbnail,
                modifier = Modifier.padding(horizontal = AoVivoTheme.spacing.safeHorizontal, vertical = AoVivoTheme.spacing.safeVertical),
            )
        } else if (split) {
            Column(Modifier.fillMaxSize()) {
                listOf(LensSlot.TOP, LensSlot.BOTTOM).forEachIndexed { i, slot ->
                    if (i == 1) LensSeparator()
                    FocusSlot(
                        key = SlotKey(source.id, slot),
                        col = cell?.col ?: 0,
                        tile =
                            tile.copy(
                                subtitle = lensLabel(source, slot),
                                motorized = false,
                                ptz = source.ptz && isMotorized(source, slot),
                            ),
                        thumbnail = if (slot == LensSlot.TOP) thumbnail else null,
                        requesterFor = requesterFor,
                        onFocusChange = { f ->
                            focusedSlots += if (f) 1 else -1
                            if (f) onSlotFocused(SlotKey(source.id, slot))
                        },
                        onClick = { vm.openFullscreen(source.id, slot) },
                        onLongClick = { onTileMenu(SlotKey(source.id, slot)) },
                        onOpenMenu = onOpenMenu,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        } else {
            FocusSlot(
                key = SlotKey(source.id, LensSlot.WHOLE),
                col = cell?.col ?: 0,
                tile = tile,
                thumbnail = thumbnail,
                requesterFor = requesterFor,
                onFocusChange = { f ->
                    focusedSlots += if (f) 1 else -1
                    if (f) onSlotFocused(SlotKey(source.id, LensSlot.WHOLE))
                },
                onClick = { vm.openFullscreen(source.id, source.defaultSlot()) },
                onLongClick = { onTileMenu(SlotKey(source.id, LensSlot.WHOLE)) },
                onOpenMenu = onOpenMenu,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FocusSlot(
    key: SlotKey,
    col: Int,
    tile: TileUi,
    thumbnail: ImageBitmap?,
    requesterFor: (SlotKey) -> FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = AoVivoTheme.shapes.radiusMd
    Box(
        modifier
            .onPreviewKeyEvent { e ->
                if (col == 0 && e.type == KeyEventType.KeyDown && RemoteKeyMapper.map(e.nativeKeyEvent.keyCode) == RemoteKey.LEFT) {
                    onOpenMenu()
                    true
                } else {
                    false
                }
            }.focusRequester(requesterFor(key))
            .aoVivoClickable(onLongClick = onLongClick, onClick = onClick)
            .aoVivoFocus(shape, FocusStyle.TILE, onFocusChange = onFocusChange, applyScale = false),
    ) {
        CameraTileOverlay(tile, thumbnail)
    }
}

private fun lensLabel(
    source: LiveSource,
    slot: LensSlot,
): String? =
    when {
        !source.stacked -> null
        slot == LensSlot.TOP -> if (isMotorized(source, slot)) "Móvel" else "Fixa"
        slot == LensSlot.BOTTOM -> if (isMotorized(source, slot)) "Móvel" else "Fixa"
        else -> null
    }

private fun isMotorized(
    source: LiveSource,
    slot: LensSlot,
): Boolean =
    (slot == LensSlot.TOP && source.motorized == io.github.mbsantos97.camerasaovivo.data.config.MotorizedLens.TOP) ||
        (slot == LensSlot.BOTTOM && source.motorized != io.github.mbsantos97.camerasaovivo.data.config.MotorizedLens.TOP)

@Composable
private fun TileMenuDialog(
    source: LiveSource,
    onDismiss: () -> Unit,
    onFullscreen: () -> Unit,
    onRecordings: () -> Unit,
    onReconnect: () -> Unit,
    quality: String,
    lensMode: String,
    onQuality: () -> Unit,
    onLensMode: () -> Unit,
    onHide: () -> Unit,
) {
    AoVivoDialog(title = source.name, onDismiss = onDismiss, dismissText = "Fechar") {
        Column(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
            NavigationRow("Tela cheia", onFullscreen, icon = AoVivoIcons.Fullscreen)
            NavigationRow("Gravações desta câmera", onRecordings, icon = AoVivoIcons.Recordings)
            NavigationRow("Reconectar", onReconnect, icon = AoVivoIcons.Refresh)
            NavigationRow("Qualidade na grade", onQuality, value = quality, icon = AoVivoIcons.Speed)
            if (source.stacked) NavigationRow("Modo de lente", onLensMode, value = lensMode, icon = AoVivoIcons.Lens)
            NavigationRow("Ocultar da grade", onHide, icon = AoVivoIcons.CameraOff)
        }
    }
}

private fun lensModeLabel(mode: io.github.mbsantos97.camerasaovivo.data.config.LensMode?): String =
    when (mode) {
        io.github.mbsantos97.camerasaovivo.data.config.LensMode.DUAL_COLUMN -> "Coluna dupla"
        io.github.mbsantos97.camerasaovivo.data.config.LensMode.TOP -> "Só cima"
        io.github.mbsantos97.camerasaovivo.data.config.LensMode.BOTTOM -> "Só baixo"
        io.github.mbsantos97.camerasaovivo.data.config.LensMode.COMPACT -> "Compacto"
        else -> "Auto"
    }

/**
 * Separa as duas lentes da imagem empilhada em dois quadrados: faixa da cor do fundo (mesmo espaço da grade)
 * com cantos arredondados de cada lado. Um único vídeo/decodificador por baixo.
 */
@Composable
private fun LensSeparator() {
    val bg = AoVivoTheme.colors.bg
    val gap = AoVivoTheme.spacing.gridGap
    val radius = AoVivoTheme.shapes.radiusMdValue
    androidx.compose.foundation.Canvas(Modifier.zIndex(-1f).fillMaxWidth().height(gap)) {
        drawRect(bg)
        val r = radius.toPx()
        val w = size.width
        val h = size.height

        fun corner(
            x: Float,
            y: Float,
            cx: Float,
            cy: Float,
        ) {
            val square = androidx.compose.ui.graphics.Path().apply { addRect(androidx.compose.ui.geometry.Rect(x, y, x + r, y + r)) }
            val circle = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)) }
            drawPath(androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, square, circle), bg)
        }
        corner(0f, -r, r, -r) // canto inferior esquerdo do quadrado de cima
        corner(w - r, -r, w - r, -r)
        corner(0f, h, r, h + r) // canto superior esquerdo do quadrado de baixo
        corner(w - r, h, w - r, h + r)
    }
}
