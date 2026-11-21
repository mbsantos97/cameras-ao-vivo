package io.github.mbsantos97.camerasaovivo.ui.recordings

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKey
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKeyMapper
import io.github.mbsantos97.camerasaovivo.core.time.Formats
import io.github.mbsantos97.camerasaovivo.data.snapshots.Snapshot
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoButton
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ButtonKind
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectingIndicator
import io.github.mbsantos97.camerasaovivo.ui.designsystem.DayPicker
import io.github.mbsantos97.camerasaovivo.ui.designsystem.EmptyState
import io.github.mbsantos97.camerasaovivo.ui.designsystem.FocusStyle
import io.github.mbsantos97.camerasaovivo.ui.designsystem.SelectorRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TimelineBar
import io.github.mbsantos97.camerasaovivo.ui.designsystem.aoVivoClickable
import io.github.mbsantos97.camerasaovivo.ui.designsystem.aoVivoFocus
import io.github.mbsantos97.camerasaovivo.ui.navigation.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Origem de uma reprodução de gravação. */
enum class PlaybackOrigin { LOCAL, SD }

@Composable
fun RecordingsScreen(
    vm: RecordingsViewModel,
    onBack: () -> Unit,
    onPlay: (cameraId: String, origin: PlaybackOrigin, startMillis: Long) -> Unit,
) {
    BackHandler(onBack = onBack)
    val st by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold("Gravações", icon = AoVivoIcons.Recordings) {
        if (st.cameras.isEmpty() && !st.loading) {
            EmptyState("Nenhuma câmera configurada", icon = AoVivoIcons.Camera, message = "Adicione câmeras em Configurações.")
            return@ScreenScaffold
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Tab("Cartão SD", AoVivoIcons.SdCard, st.tab == RecordingsTab.SD) { vm.selectTab(RecordingsTab.SD) }
            Tab("TV Box", AoVivoIcons.Tv, st.tab == RecordingsTab.LOCAL) { vm.selectTab(RecordingsTab.LOCAL) }
            Tab("Fotos", AoVivoIcons.Photos, st.tab == RecordingsTab.PHOTOS) { vm.selectTab(RecordingsTab.PHOTOS) }
            if (st.loading) ConnectingIndicator()
        }
        if (st.tab != RecordingsTab.PHOTOS && st.cameras.size > 1) {
            SelectorRow(
                "Câmera",
                st.cameras.map { it.name },
                st.cameras.indexOfFirst { it.id == st.cameraId }.coerceAtLeast(0),
                { i -> vm.selectCamera(st.cameras[i].id) },
                icon = AoVivoIcons.Camera,
            )
        }
        when (st.tab) {
            RecordingsTab.PHOTOS -> PhotosGrid(st.photos, vm::deletePhoto)
            RecordingsTab.SD ->
                if (!st.sdSupported) {
                    SdUnsupported(st.camera?.recordLocal == true) {
                        vm.enableLocalRecording()
                        vm.selectTab(RecordingsTab.LOCAL)
                    }
                } else {
                    DayAndTimeline(st, vm) { t -> st.cameraId?.let { onPlay(it, PlaybackOrigin.SD, t) } }
                }
            RecordingsTab.LOCAL ->
                if (st.segments.isEmpty() && !st.loading) {
                    EmptyState(
                        title = "Nenhuma gravação na TV Box",
                        icon = AoVivoIcons.Tv,
                        message =
                            if (st.camera?.recordLocal == true) {
                                "A gravação está ligada; os primeiros trechos aparecem em até 10 minutos."
                            } else {
                                "Ative a gravação desta câmera na TV Box (recomendado usar um pendrive)."
                            },
                        actionText = if (st.camera?.recordLocal == true) null else "Ativar gravação na TV Box",
                        onAction = vm::enableLocalRecording,
                    )
                } else {
                    DayAndTimeline(st, vm) { t -> st.cameraId?.let { onPlay(it, PlaybackOrigin.LOCAL, t) } }
                }
        }
        st.error?.let { Text(it, style = AoVivoTheme.type.body, color = AoVivoTheme.colors.textSecondary) }
    }
}

@Composable
private fun Tab(
    label: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
) = AoVivoButton(label, onClick, kind = if (selected) ButtonKind.PRIMARY else ButtonKind.SECONDARY, icon = icon)

@Composable
private fun SdUnsupported(
    alreadyLocal: Boolean,
    onEnable: () -> Unit,
) {
    EmptyState(
        title = "Cartão SD indisponível por protocolo aberto",
        icon = AoVivoIcons.SdCard,
        message =
            "Esta câmera não disponibiliza o cartão de memória por protocolo aberto. As gravações do cartão continuam acessíveis " +
                "pelo app Yoosee no celular. Para ver gravações aqui na TV, ative a Gravação na TV Box (recomendado usar um pendrive).",
        actionText = if (alreadyLocal) "Ver gravações da TV Box" else "Ativar gravação na TV Box",
        onAction = onEnable,
    )
}

/** Seletor de dia + linha do tempo. ←/→ movem o cursor (segurar acelera: 1 min → 10 min → 1 h); OK reproduz. */
@Composable
private fun DayAndTimeline(
    st: RecordingsState,
    vm: RecordingsViewModel,
    onPlay: (Long) -> Unit,
) {
    var cursor by remember(st.selectedDay) { mutableIntStateOf(st.timeline.firstOrNull()?.startSecond ?: (12 * 3600)) }
    var focused by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.md)) {
        DayPicker(st.days, st.selectedDay, { vm.selectDay(it.epochDay) })
        Box(
            Modifier
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val n = e.nativeKeyEvent
                    val dir =
                        when (RemoteKeyMapper.map(n.keyCode)) {
                            RemoteKey.LEFT -> -1
                            RemoteKey.RIGHT -> 1
                            else -> return@onPreviewKeyEvent false
                        }
                    val held = n.eventTime - n.downTime
                    val step =
                        when {
                            held < 1_000 -> 60
                            held < 3_000 -> 600
                            else -> 3_600
                        }
                    cursor = (cursor + dir * step).coerceIn(0, DAY_SECONDS - 1)
                    true
                }.aoVivoClickable { st.selectedDay?.let { onPlay(it + cursor * 1000L) } }
                .aoVivoFocus(AoVivoTheme.shapes.radiusMd, FocusStyle.ROW, onFocusChange = { focused = it }, applyScale = false)
                .padding(AoVivoTheme.spacing.xs),
        ) {
            TimelineBar(st.timeline, cursorSecond = { cursor }, focused = focused)
        }
        Text(
            if (st.timeline.isEmpty()) {
                "Sem gravações neste dia."
            } else {
                "←/→ escolhem o horário (segure para acelerar) · OK reproduz a partir de ${Formats.secondsOfDay(
                    cursor,
                )}"
            },
            style = AoVivoTheme.type.caption,
            color = AoVivoTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun PhotosGrid(
    photos: List<Snapshot>,
    onDelete: (Snapshot) -> Unit,
) {
    var open by remember { mutableStateOf<Snapshot?>(null) }
    var confirm by remember { mutableStateOf<Snapshot?>(null) }
    if (photos.isEmpty()) {
        EmptyState("Nenhuma foto ainda", icon = AoVivoIcons.Photos, message = "Em tela cheia, use o botão Foto da barra de controles.")
        return
    }
    LazyVerticalGrid(
        GridCells.Fixed(4),
        horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.md),
    ) {
        items(photos, key = { it.file.path }) { p ->
            Column(
                Modifier
                    .aoVivoClickable(onLongClick = { confirm = p }) { open = p }
                    .aoVivoFocus(AoVivoTheme.shapes.radiusMd, FocusStyle.TILE),
            ) {
                val bmp = rememberThumb(p.file, THUMB_PX)
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(AoVivoTheme.colors.surfaceRaised, AoVivoTheme.shapes.radiusMd)) {
                    if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Text(p.cameraName, style = AoVivoTheme.type.label, color = AoVivoTheme.colors.textPrimary, maxLines = 1)
                Text(Formats.dateTime(p.takenAtMillis), style = AoVivoTheme.type.caption, color = AoVivoTheme.colors.textSecondary)
            }
        }
    }
    open?.let { p ->
        Dialog(onDismissRequest = { open = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(AoVivoTheme.colors.bg)) {
                val bmp = rememberThumb(p.file, FULL_PX)
                if (bmp != null) Image(bmp, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(AoVivoTheme.spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm),
                ) {
                    AoVivoButton("Fechar", { open = null }, kind = ButtonKind.SECONDARY)
                    AoVivoButton("Apagar", {
                        confirm = p
                        open = null
                    }, kind = ButtonKind.DANGER, icon = AoVivoIcons.Delete)
                }
            }
        }
    }
    confirm?.let { p ->
        AoVivoDialog(
            title = "Apagar esta foto?",
            message = "${p.cameraName} · ${Formats.dateTime(p.takenAtMillis)}",
            confirmText = "Apagar",
            onConfirm = {
                onDelete(p)
                confirm = null
            },
            onDismiss = { confirm = null },
            danger = true,
        )
    }
}

/** Decodifica a imagem já no tamanho de exibição (inSampleSize), fora da main thread. */
@Composable
private fun rememberThumb(
    file: File,
    targetWidth: Int,
): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, file, targetWidth) {
        value =
            withContext(Dispatchers.IO) {
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, b)
                var s = 1
                while (b.outWidth / (s * 2) >= targetWidth) s *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }
    }
    return bmp
}

private const val DAY_SECONDS = 86_400
private const val THUMB_PX = 320
private const val FULL_PX = 1920
