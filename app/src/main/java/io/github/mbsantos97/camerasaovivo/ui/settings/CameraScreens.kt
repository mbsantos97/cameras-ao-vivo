package io.github.mbsantos97.camerasaovivo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.LensMode
import io.github.mbsantos97.camerasaovivo.data.config.MotorizedLens
import io.github.mbsantos97.camerasaovivo.data.config.PtzMode
import io.github.mbsantos97.camerasaovivo.data.config.PtzSpeed
import io.github.mbsantos97.camerasaovivo.data.config.StreamQuality
import io.github.mbsantos97.camerasaovivo.player.Transport
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoButton
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ButtonKind
import io.github.mbsantos97.camerasaovivo.ui.designsystem.DangerRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.EmptyState
import io.github.mbsantos97.camerasaovivo.ui.designsystem.LocalToast
import io.github.mbsantos97.camerasaovivo.ui.designsystem.NavigationRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.SectionHeader
import io.github.mbsantos97.camerasaovivo.ui.designsystem.SelectorRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextEntryRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextInputDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextInputKind
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ToggleRow
import io.github.mbsantos97.camerasaovivo.ui.navigation.ScreenScaffold

@Composable
fun CameraListScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onEdit: (String?) -> Unit,
    onDiscover: () -> Unit,
    onPhoneConfig: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val cfg by vm.config.collectAsStateWithLifecycle()
    var addDialog by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val cameras = cfg?.cameras.orEmpty().sortedBy { it.order }
    ScreenScaffold(
        "Câmeras",
        icon = AoVivoIcons.Camera,
        trailing = { AoVivoButton("Adicionar câmera", { addDialog = true }, icon = AoVivoIcons.Add, modifier = Modifier.focusRequester(first)) },
    ) {
        if (cameras.isEmpty()) {
            EmptyState(
                title = "Nenhuma câmera ainda",
                message = "Ative a \"Conexão NVR\" (RTSP/ONVIF) no app Yoosee e adicione a câmera aqui.",
                icon = AoVivoIcons.Camera,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
                items(cameras, key = { it.id }) { cam ->
                    NavigationRow(
                        title = cam.name.ifBlank { "Sem nome" },
                        subtitle = listOfNotNull(cam.manufacturer, cam.model, if (cam.hidden) "oculta na grade" else null).joinToString(" · ").ifBlank { null },
                        value = cam.host,
                        icon = if (cam.hidden) AoVivoIcons.CameraOff else AoVivoIcons.Camera,
                        onClick = { onEdit(cam.id) },
                    )
                }
            }
        }
    }
    if (addDialog) {
        AoVivoDialog(title = "Adicionar câmera", onDismiss = { addDialog = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
                NavigationRow("Buscar na rede", {
                    addDialog = false
                    onDiscover()
                }, icon = AoVivoIcons.Search, subtitle = "WS-Discovery e varredura da rede local")
                NavigationRow("Manual", {
                    addDialog = false
                    onEdit(null)
                }, icon = AoVivoIcons.Add, subtitle = "Digitar IP, usuário e senha pelo controle")
                NavigationRow("Configurar pelo celular", {
                    addDialog = false
                    onPhoneConfig()
                }, icon = AoVivoIcons.Phone, subtitle = "Formulário no celular via QR code")
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private enum class Field { NAME, HOST, ONVIF_PORT, RTSP_PORT, USER, PASSWORD }

/** Edição de uma câmera. [initial] = null cria uma nova (ex.: vinda da busca com o IP preenchido). */
@Composable
fun CameraEditScreen(
    vm: SettingsViewModel,
    cameraId: String?,
    prefillHost: String?,
    onBack: () -> Unit,
) {
    val cfg by vm.config.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val existing = cfg?.cameras?.firstOrNull { it.id == cameraId }
    if (cfg == null || (cameraId != null && existing == null)) return
    var draft by remember(existing?.id) { mutableStateOf(existing ?: vm.newCamera().copy(host = prefillHost.orEmpty())) }
    var password by remember(existing?.id) { mutableStateOf(existing?.let { vm.password(it) } ?: "") }
    var dirty by remember { mutableStateOf(existing == null) }
    var editing by remember { mutableStateOf<Field?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    fun change(t: (CameraConfig) -> CameraConfig) {
        draft = t(draft)
        dirty = true
    }

    fun save() {
        when {
            draft.name.isBlank() -> toast.show("Dê um nome à câmera")
            draft.host.isBlank() -> toast.show("Informe o IP da câmera")
            draft.host.all { it.isDigit() } -> toast.show("Isso parece o ID do app Yoosee. Use o IP da câmera (ex.: 192.168.1.50)")
            else ->
                vm.saveCamera(draft, password) {
                    dirty = false
                    toast.show("Câmera salva")
                    onBack()
                }
        }
    }

    BackHandler { if (dirty) confirmDiscard = true else onBack() }

    ScreenScaffold(
        if (existing == null) "Nova câmera" else draft.name.ifBlank { "Câmera" },
        icon = AoVivoIcons.Camera,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm)) {
                AoVivoButton("Testar conexão", {
                    if (draft.host.isBlank()) toast.show("Informe o IP da câmera") else testing = true
                }, kind = ButtonKind.SECONDARY, icon = AoVivoIcons.Diagnostics)
                AoVivoButton("Salvar", ::save, icon = AoVivoIcons.Check, modifier = Modifier.focusRequester(first))
            }
        },
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
            item { SectionHeader("Conexão") }
            item { TextEntryRow("Nome", draft.name, { editing = Field.NAME }, placeholder = "Ex.: Câmera Externa") }
            item { TextEntryRow("IP", draft.host, { editing = Field.HOST }, placeholder = "192.168.0.101") }
            item { TextEntryRow("Porta ONVIF", draft.onvifPort.toString(), { editing = Field.ONVIF_PORT }) }
            item { TextEntryRow("Porta RTSP", draft.rtspPort.toString(), { editing = Field.RTSP_PORT }) }
            item { TextEntryRow("Usuário", draft.username, { editing = Field.USER }) }
            item {
                TextEntryRow("Senha", password, { editing = Field.PASSWORD }, masked = true, placeholder = "Definida no app Yoosee", icon = AoVivoIcons.Lock)
            }
            item {
                SelectorRow("Transporte", listOf("Auto", "UDP", "TCP"), draft.transport.ordinal, { i -> change { it.copy(transport = Transport.entries[i]) } })
            }
            item { SectionHeader("Imagem") }
            item {
                SelectorRow("Qualidade na grade", listOf("Substream", "Principal"), draft.gridQuality.ordinal, { i ->
                    change { it.copy(gridQuality = StreamQuality.entries[i]) }
                })
            }
            item {
                SelectorRow("Qualidade em tela cheia", listOf("Principal", "Substream"), if (draft.fullQuality == StreamQuality.MAIN) 0 else 1, { i ->
                    change { it.copy(fullQuality = if (i == 0) StreamQuality.MAIN else StreamQuality.SUB) }
                })
            }
            item {
                SelectorRow(
                    "Modo de lente",
                    listOf("Auto", "Coluna dupla", "Só cima", "Só baixo", "Compacto"),
                    draft.lensMode.ordinal,
                    { i -> change { it.copy(lensMode = LensMode.entries[i]) } },
                    subtitle = "Para câmeras de lente dupla com as imagens empilhadas",
                )
            }
            item {
                SelectorRow(
                    "Lente motorizada",
                    listOf("Auto", "Cima", "Baixo"),
                    draft.motorizedLens.ordinal,
                    { i -> change { it.copy(motorizedLens = MotorizedLens.entries[i]) } },
                    subtitle = "Auto usa o resultado do Diagnóstico; sem ele, Baixo",
                )
            }
            item { ToggleRow("Áudio ligado por padrão", draft.audioDefault, { v -> change { it.copy(audioDefault = v) } }) }
            item { SectionHeader("PTZ") }
            item { ToggleRow("Inverter horizontal", draft.ptz.invertPan, { v -> change { it.copy(ptz = it.ptz.copy(invertPan = v)) } }) }
            item {
                ToggleRow(
                    "Inverter vertical",
                    draft.ptz.invertTilt,
                    { v -> change { it.copy(ptz = it.ptz.copy(invertTilt = v)) } },
                    subtitle = "Útil para a lâmpada instalada de cabeça para baixo",
                )
            }
            item {
                SelectorRow("Modo", listOf("Contínuo", "Passo"), draft.ptz.mode.ordinal, { i ->
                    change { it.copy(ptz = it.ptz.copy(mode = PtzMode.entries[i])) }
                })
            }
            item {
                SelectorRow("Velocidade", listOf("Lenta", "Média", "Rápida"), draft.ptz.speed.ordinal, { i ->
                    change { it.copy(ptz = it.ptz.copy(speed = PtzSpeed.entries[i])) }
                })
            }
            item { SectionHeader("Grade e gravação") }
            item { ToggleRow("Gravar na TV Box", draft.recordLocal, { v -> change { it.copy(recordLocal = v) } }, icon = AoVivoIcons.Record) }
            item { ToggleRow("Ocultar da grade", draft.hidden, { v -> change { it.copy(hidden = v) } }) }
            if (existing != null) {
                item { NavigationRow("Mover para cima na grade", { vm.moveCamera(existing.id, -1) }) }
                item { NavigationRow("Mover para baixo na grade", { vm.moveCamera(existing.id, 1) }) }
                item { DangerRow("Remover câmera", { confirmRemove = true }) }
            }
        }
    }

    editing?.let { field ->
        val (title, value, kind) =
            when (field) {
                Field.NAME -> Triple("Nome da câmera", draft.name, TextInputKind.TEXT)
                Field.HOST -> Triple("IP da câmera", draft.host, TextInputKind.IP)
                Field.ONVIF_PORT -> Triple("Porta ONVIF", draft.onvifPort.toString(), TextInputKind.NUMBER)
                Field.RTSP_PORT -> Triple("Porta RTSP", draft.rtspPort.toString(), TextInputKind.NUMBER)
                Field.USER -> Triple("Usuário", draft.username, TextInputKind.TEXT)
                Field.PASSWORD -> Triple("Senha", password, TextInputKind.PASSWORD)
            }
        TextInputDialog(
            title = title,
            initial = value,
            kind = kind,
            onDismiss = { editing = null },
            onConfirm = { v ->
                when (field) {
                    Field.NAME -> change { it.copy(name = v) }
                    Field.HOST -> change { it.copy(host = v.trim()) }
                    Field.ONVIF_PORT -> v.toIntOrNull()?.takeIf { it in 1..65535 }?.let { p -> change { it.copy(onvifPort = p) } }
                    Field.RTSP_PORT -> v.toIntOrNull()?.takeIf { it in 1..65535 }?.let { p -> change { it.copy(rtspPort = p) } }
                    Field.USER -> change { it.copy(username = v.trim()) }
                    Field.PASSWORD -> {
                        password = v
                        dirty = true
                    }
                }
                editing = null
            },
        )
    }
    if (testing) {
        val tester = remember { vm.newTester() }
        ConnectionTestDialog(draft, password, tester) { result ->
            testing = false
            if (result != null) {
                change {
                    it.copy(
                        streams = result.streams,
                        manufacturer = result.info?.manufacturer ?: it.manufacturer,
                        model = result.info?.model ?: it.model,
                        firmware = result.info?.firmware ?: it.firmware,
                        serial = result.info?.serial ?: it.serial,
                        mac = result.mac ?: it.mac,
                    )
                }
                toast.show("Resultado aplicado. Salve para guardar.")
            }
        }
    }
    if (confirmDiscard) {
        AoVivoDialog(
            title = "Descartar alterações?",
            confirmText = "Descartar",
            onConfirm = {
                confirmDiscard = false
                onBack()
            },
            dismissText = "Continuar editando",
            onDismiss = { confirmDiscard = false },
            danger = true,
        )
    }
    if (confirmRemove && existing != null) {
        AoVivoDialog(
            title = "Remover ${existing.name}?",
            message = "A câmera sai da grade e a senha é apagada desta TV Box.",
            confirmText = "Remover",
            onConfirm = {
                confirmRemove = false
                vm.removeCamera(existing.id, onBack)
            },
            onDismiss = { confirmRemove = false },
            danger = true,
        )
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
