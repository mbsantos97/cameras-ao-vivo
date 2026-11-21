package io.github.mbsantos97.camerasaovivo.ui.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import io.github.mbsantos97.camerasaovivo.camera.ptz.PtzDirection
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKey
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKeyMapper
import io.github.mbsantos97.camerasaovivo.data.config.PtzSpeed
import io.github.mbsantos97.camerasaovivo.onvif.model.PtzPreset
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIconButton
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ControlBar
import io.github.mbsantos97.camerasaovivo.ui.designsystem.InfoRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.LocalToast
import io.github.mbsantos97.camerasaovivo.ui.designsystem.NavigationRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private fun RemoteKey.toDirection(): PtzDirection? =
    when (this) {
        RemoteKey.UP -> PtzDirection.UP
        RemoteKey.DOWN -> PtzDirection.DOWN
        RemoteKey.LEFT -> PtzDirection.LEFT
        RemoteKey.RIGHT -> PtzDirection.RIGHT
        else -> null
    }

private fun speedLabel(s: PtzSpeed) =
    when (s) {
        PtzSpeed.SLOW -> "Lenta"
        PtzSpeed.MEDIUM -> "Média"
        PtzSpeed.FAST -> "Rápida"
    }

/**
 * Camada de tela cheia sobre o vídeo: recebe as teclas (setas = PTZ enquanto seguradas),
 * mostra a seta de movimento, o painel de informações e a barra de controles (some em 5 s).
 */
@Composable
fun FullscreenLayer(
    vm: LiveViewModel,
    source: LiveSource,
    ui: FullscreenUi,
    focus: FocusRequester,
    onOpenRecordings: (String) -> Unit,
) {
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var warnedNoPtz by remember { mutableStateOf(false) }
    var presetsOpen by remember { mutableStateOf(false) }
    val firstButton = remember { FocusRequester() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focus)
                .onFocusChanged { if (!it.isFocused) vm.fullscreen.ptzStop() }
                .onPreviewKeyEvent { e ->
                    val native = e.nativeKeyEvent
                    val key = RemoteKeyMapper.map(native.keyCode) ?: return@onPreviewKeyEvent false
                    val dir = key.toDirection()
                    val down = e.type == KeyEventType.KeyDown
                    when {
                        dir != null && ui.zoom > 1f -> {
                            if (down) vm.fullscreen.pan(dir)
                            true
                        }
                        dir != null && ui.ptz -> {
                            if (down && native.repeatCount == 0) {
                                vm.fullscreen.ptzPress(dir)
                            } else if (!down) {
                                vm.fullscreen.ptzRelease(dir)
                            }
                            true
                        }
                        dir != null -> {
                            if (down && native.repeatCount == 0) {
                                if (!warnedNoPtz) {
                                    toast.show("Esta câmera não tem PTZ. ← e → trocam de câmera.")
                                    warnedNoPtz = true
                                }
                                when (dir) {
                                    PtzDirection.LEFT -> vm.nextFullscreen(-1)
                                    PtzDirection.RIGHT -> vm.nextFullscreen(1)
                                    else -> Unit
                                }
                            }
                            true
                        }
                        key == RemoteKey.OK || key == RemoteKey.MENU -> {
                            if (!down) vm.fullscreen.showControls()
                            true
                        }
                        key.digit != null && key.digit != 0 -> {
                            if (down && ui.ptz) {
                                scope.launch {
                                    val name = vm.fullscreen.gotoPresetNumber(key.digit!!)
                                    toast.show(if (name != null) "Indo para $name" else "Preset ${key.digit} não existe")
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }.focusable(),
        )

        PtzArrows(ui.moving)

        if (ui.info) {
            InfoPanelOverlay(vm, Modifier.align(Alignment.TopEnd))
        }

        AnimatedVisibility(
            visible = ui.controlsVisible,
            enter = fadeIn(AoVivoTheme.motion.medium()),
            exit = fadeOut(AoVivoTheme.motion.medium()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ControlBar(
                Modifier.onPreviewKeyEvent {
                    vm.fullscreen.touch()
                    false
                },
            ) {
                AoVivoIconButton(AoVivoIcons.Snapshot, "Foto", {
                    scope.launch {
                        val f = vm.takeSnapshot()
                        toast.show(if (f != null) "Foto salva em Gravações → Fotos" else "Não foi possível tirar a foto")
                    }
                }, modifier = Modifier.focusRequester(firstButton))
                if (!source.demo) {
                    AoVivoIconButton(AoVivoIcons.Record, if (ui.recording) "Parar gravação" else "Gravar na TV Box", {
                        vm.fullscreen.toggleRecording()
                        toast.show(if (!ui.recording) "Gravação na TV Box ligada" else "Gravação na TV Box desligada")
                    }, active = ui.recording)
                }
                AoVivoIconButton(
                    if (ui.audio) AoVivoIcons.VolumeOn else AoVivoIcons.VolumeOff,
                    if (ui.audio) "Áudio ligado" else "Áudio",
                    vm::toggleAudio,
                    active = ui.audio,
                )
                if (ui.ptz && ui.presets) AoVivoIconButton(AoVivoIcons.Preset, "Presets", { presetsOpen = true })
                AoVivoIconButton(AoVivoIcons.ZoomIn, "Zoom +", { vm.fullscreen.zoom(ZOOM_STEP) })
                AoVivoIconButton(AoVivoIcons.ZoomOut, "Zoom −", { vm.fullscreen.zoom(-ZOOM_STEP) }, enabled = ui.zoom > 1f)
                if (source.stacked) {
                    AoVivoIconButton(AoVivoIcons.Lens, "Lente", {
                        val cur = vm.state.value.fullscreen?.slot ?: LensSlot.WHOLE
                        val next =
                            when (cur) {
                                LensSlot.TOP -> LensSlot.BOTTOM
                                LensSlot.BOTTOM -> LensSlot.WHOLE
                                LensSlot.WHOLE -> LensSlot.TOP
                            }
                        vm.setFullscreenSlot(next)
                    })
                }
                if (ui.ptz) {
                    AoVivoIconButton(AoVivoIcons.Invert, "Inverter controles", vm.fullscreen::toggleInvert, active = ui.inverted)
                    AoVivoIconButton(AoVivoIcons.Speed, "Velocidade: ${speedLabel(ui.speed)}", vm.fullscreen::cycleSpeed)
                }
                AoVivoIconButton(AoVivoIcons.Recordings, "Gravações", { onOpenRecordings(source.cameraId) })
                AoVivoIconButton(AoVivoIcons.Info, "Informações", vm.fullscreen::toggleInfo, active = ui.info)
            }
            LaunchedEffect(Unit) { runCatching { firstButton.requestFocus() } }
        }
    }

    LaunchedEffect(ui.controlsVisible) {
        if (!ui.controlsVisible) runCatching { focus.requestFocus() }
    }

    if (presetsOpen) {
        PresetsDialog(vm, onClose = { presetsOpen = false })
    }
}

@Composable
private fun PtzArrows(moving: Set<PtzDirection>) {
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.sizes
    Box(Modifier.fillMaxSize().padding(AoVivoTheme.spacing.xl)) {
        moving.forEach { d ->
            val (align, angle) =
                when (d) {
                    PtzDirection.UP -> Alignment.TopCenter to 0f
                    PtzDirection.RIGHT -> Alignment.CenterEnd to 90f
                    PtzDirection.DOWN -> Alignment.BottomCenter to 180f
                    PtzDirection.LEFT -> Alignment.CenterStart to 270f
                }
            Box(
                Modifier
                    .align(align)
                    .size(s.ptzArrow)
                    .background(c.scrim, AoVivoTheme.shapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(AoVivoIcons.Arrow),
                    null,
                    tint = c.textPrimary.copy(alpha = c.watermarkAlpha + 0.2f),
                    modifier =
                        Modifier.size(s.iconBar * 1.5f).rotate(angle),
                )
            }
        }
    }
}

@Composable
private fun InfoPanelOverlay(
    vm: LiveViewModel,
    modifier: Modifier,
) {
    val stats by produceState(vm.activeStats()) {
        while (true) {
            value = vm.activeStats()
            delay(1_000)
        }
    }
    val st = stats
    Column(
        modifier
            .padding(horizontal = AoVivoTheme.spacing.safeHorizontal, vertical = AoVivoTheme.spacing.safeVertical + AoVivoTheme.spacing.xl)
            .width(AoVivoTheme.sizes.dialogWidth / 1.3f)
            .background(AoVivoTheme.colors.scrim, AoVivoTheme.shapes.radiusLg)
            .padding(AoVivoTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xxs),
    ) {
        InfoRow("Resolução", st?.videoSize?.let { "${it.width}x${it.height}" } ?: "—")
        InfoRow("Codec", st?.codec ?: "—")
        InfoRow("FPS", st?.let { String.format(Locale.ROOT, "%.1f", it.fps) } ?: "—")
        InfoRow("Bitrate", st?.let { "${it.bitrateKbps} kb/s" } ?: "—")
        InfoRow(
            "Decodificador",
            if (st == null) {
                "—"
            } else if (st.hardwareDecoding) {
                "Hardware"
            } else {
                "Software"
            },
        )
        InfoRow("Quadros perdidos", st?.lostFrames?.toString() ?: "—")
        InfoRow("Latência PTZ", "${vm.fullscreen.ptzLatencyMs} ms")
    }
}

@Composable
private fun PresetsDialog(
    vm: LiveViewModel,
    onClose: () -> Unit,
) {
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val presets by produceState<List<PtzPreset>?>(null) { value = vm.fullscreen.presets() }
    AoVivoDialog(title = "Presets", onDismiss = onClose, dismissText = "Fechar") {
        Column(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
            val list = presets
            if (list == null) {
                InfoRow("Carregando…", "")
            } else {
                list.forEachIndexed { i, p ->
                    NavigationRow("${i + 1}. ${p.name ?: "Preset ${p.token}"}", {
                        scope.launch {
                            toast.show(if (vm.fullscreen.gotoPreset(p.token)) "Indo para ${p.name ?: p.token}" else "A câmera não aceitou o preset")
                            onClose()
                        }
                    }, icon = AoVivoIcons.Preset)
                }
                NavigationRow("Salvar posição atual", {
                    scope.launch {
                        val ok = vm.fullscreen.savePreset("Posição ${list.size + 1}")
                        toast.show(if (ok) "Posição salva como preset ${list.size + 1}" else "A câmera não aceitou salvar o preset")
                        onClose()
                    }
                }, icon = AoVivoIcons.Add)
            }
        }
    }
}

private const val ZOOM_STEP = 0.5f
