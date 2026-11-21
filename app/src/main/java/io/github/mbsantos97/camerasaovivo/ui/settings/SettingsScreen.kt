package io.github.mbsantos97.camerasaovivo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mbsantos97.camerasaovivo.BuildConfig
import io.github.mbsantos97.camerasaovivo.data.config.Latency
import io.github.mbsantos97.camerasaovivo.data.config.RecordingDestination
import io.github.mbsantos97.camerasaovivo.data.config.StreamQuality
import io.github.mbsantos97.camerasaovivo.player.RenderMode
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.DangerRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.LocalToast
import io.github.mbsantos97.camerasaovivo.ui.designsystem.NavigationRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.SectionHeader
import io.github.mbsantos97.camerasaovivo.ui.designsystem.SelectorRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextInputDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextInputKind
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ToggleRow
import io.github.mbsantos97.camerasaovivo.ui.live.GridLayoutMode
import io.github.mbsantos97.camerasaovivo.ui.navigation.ScreenScaffold
import kotlinx.coroutines.launch

private enum class PinFlow { NONE, SET, EXPORT, IMPORT }

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit,
    onCameras: () -> Unit,
    onPhoneConfig: () -> Unit,
    onDiagnostics: () -> Unit,
    onAbout: () -> Unit,
    onCleared: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val cfg by vm.config.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var pinFlow by remember { mutableStateOf(PinFlow.NONE) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    var removePin by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val config = cfg ?: return
    val s = config.settings

    ScreenScaffold("Configurações", icon = AoVivoIcons.Settings) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
            item { SectionHeader("Câmeras") }
            item {
                NavigationRow(
                    "Câmeras",
                    onCameras,
                    value = config.cameras.size.toString(),
                    icon = AoVivoIcons.Camera,
                    modifier = Modifier.focusRequester(first),
                )
            }
            item { SectionHeader("Visualização") }
            item {
                SelectorRow(
                    "Layout padrão",
                    listOf("Automático", "1 grande + 2", "1 grande + 3"),
                    s.layout.ordinal,
                    { i -> vm.updateSettings { it.copy(layout = GridLayoutMode.entries[i]) } },
                    icon = AoVivoIcons.Grid,
                )
            }
            item { ToggleRow("Mostrar relógio", s.showClock, { v -> vm.updateSettings { it.copy(showClock = v) } }) }
            item {
                ToggleRow(
                    "Ronda automática",
                    s.tourEnabled,
                    { v -> vm.updateSettings { it.copy(tourEnabled = v) } },
                    subtitle = "Troca de câmera em tela cheia a cada ${s.tourSeconds} s",
                )
            }
            item {
                val opts = listOf(10, 15, 30, 60)
                SelectorRow(
                    "Intervalo da ronda",
                    opts.map { "$it s" },
                    opts.indexOf(s.tourSeconds).coerceAtLeast(0),
                    { i -> vm.updateSettings { it.copy(tourSeconds = opts[i]) } },
                    enabled = s.tourEnabled,
                )
            }
            item {
                SelectorRow(
                    "Renderização",
                    listOf("Padrão", "Desempenho"),
                    s.renderMode.ordinal,
                    { i -> vm.updateSettings { it.copy(renderMode = RenderMode.entries[i]) } },
                    subtitle = "Desempenho usa SurfaceView: sem recorte de lente e sem zoom digital",
                )
            }
            item {
                SelectorRow(
                    "Latência",
                    listOf("Baixa (300 ms)", "Normal (500 ms)", "Estável (1500 ms)"),
                    s.latency.ordinal,
                    { i -> vm.updateSettings { it.copy(latency = Latency.entries[i]) } },
                )
            }
            item { SectionHeader("Gravação na TV Box") }
            item {
                SelectorRow(
                    "Destino",
                    listOf("Pendrive (se houver)", "Memória interna"),
                    s.recording.destination.ordinal,
                    { i -> vm.updateSettings { it.copy(recording = it.recording.copy(destination = RecordingDestination.entries[i])) } },
                    icon = AoVivoIcons.Usb,
                )
            }
            item {
                SelectorRow(
                    "Qualidade da gravação",
                    listOf("Substream (recomendado)", "Principal"),
                    s.recording.quality.ordinal,
                    { i -> vm.updateSettings { it.copy(recording = it.recording.copy(quality = StreamQuality.entries[i])) } },
                )
            }
            item {
                val opts = listOf(50, 70, 80, 90)
                SelectorRow(
                    "Limite de espaço",
                    opts.map { "$it% do volume" },
                    opts.indexOf(s.recording.maxPercent).coerceAtLeast(0),
                    { i -> vm.updateSettings { it.copy(recording = it.recording.copy(maxPercent = opts[i])) } },
                    subtitle = "Os trechos mais antigos são apagados; sempre sobra 1 GB na memória interna",
                )
            }
            item { SectionHeader("Sistema") }
            item {
                ToggleRow(
                    "Iniciar com a TV Box",
                    s.startOnBoot,
                    { v -> vm.updateSettings { it.copy(startOnBoot = v) } },
                    subtitle = "Abre o app quando a TV Box liga (se o sistema permitir)",
                )
            }
            item {
                NavigationRow(
                    if (s.pinHash == null) "Definir PIN" else "PIN de Configurações",
                    { if (s.pinHash == null) pinFlow = PinFlow.SET else removePin = true },
                    value = if (s.pinHash == null) "Desligado" else "Ligado",
                    icon = AoVivoIcons.Lock,
                )
            }
            item { NavigationRow("Configurar pelo celular", onPhoneConfig, icon = AoVivoIcons.Phone, subtitle = "QR code na TV, formulário no celular") }
            item {
                NavigationRow("Exportar configuração", { pinFlow = PinFlow.EXPORT }, icon = AoVivoIcons.Usb, subtitle = "Arquivo cifrado com PIN, no pendrive")
            }
            item { NavigationRow("Importar configuração", { confirmImport = true }, icon = AoVivoIcons.Usb) }
            if (BuildConfig.DEBUG || s.demoMode) {
                item {
                    ToggleRow(
                        "Modo demonstração",
                        s.demoMode,
                        { v -> vm.updateSettings { it.copy(demoMode = v) } },
                        subtitle = "Câmeras falsas para testar a navegação",
                    )
                }
            }
            item { ToggleRow("Painel de desempenho", s.perfOverlay, { v -> vm.updateSettings { it.copy(perfOverlay = v) } }, icon = AoVivoIcons.Speed) }
            item { NavigationRow("Diagnóstico", onDiagnostics, icon = AoVivoIcons.Diagnostics) }
            item { NavigationRow("Sobre", onAbout, icon = AoVivoIcons.Info) }
            item { DangerRow("Limpar dados", { confirmClear = true }, subtitle = "Apaga câmeras, senhas e preferências desta TV Box") }
        }
    }

    when (pinFlow) {
        PinFlow.SET ->
            TextInputDialog(
                title = "Novo PIN (4 dígitos)",
                initial = "",
                kind = TextInputKind.PIN,
                maxLength = 4,
                onDismiss = { pinFlow = PinFlow.NONE },
                onConfirm = { pin ->
                    if (pin.length == 4) {
                        vm.setPin(pin)
                        toast.show("PIN definido")
                        pinFlow = PinFlow.NONE
                    } else {
                        toast.show("O PIN precisa ter 4 dígitos")
                    }
                },
            )
        PinFlow.EXPORT ->
            TextInputDialog(
                title = "PIN para cifrar o arquivo",
                hint = "Será pedido na importação. Use 4 a 12 dígitos.",
                initial = "",
                kind = TextInputKind.PIN,
                maxLength = 12,
                onDismiss = { pinFlow = PinFlow.NONE },
                onConfirm = { pin ->
                    if (pin.length < 4) {
                        toast.show("Use pelo menos 4 dígitos")
                    } else {
                        pinFlow = PinFlow.NONE
                        scope.launch {
                            vm.export(pin).fold({ toast.show("Exportado — $it") }, { toast.show("Falha ao exportar: ${it.message}") })
                        }
                    }
                },
            )
        PinFlow.IMPORT ->
            TextInputDialog(
                title = "PIN do arquivo",
                initial = "",
                kind = TextInputKind.PIN,
                maxLength = 12,
                onDismiss = { pinFlow = PinFlow.NONE },
                onConfirm = { pin ->
                    pinFlow = PinFlow.NONE
                    scope.launch {
                        vm.import(pin).fold({ toast.show("$it câmera(s) importada(s)") }, { toast.show(it.message ?: "Falha ao importar") })
                    }
                },
            )
        PinFlow.NONE -> Unit
    }
    if (removePin) {
        AoVivoDialog(
            title = "PIN de Configurações",
            message = "O PIN é pedido para entrar em Configurações.",
            confirmText = "Remover PIN",
            onConfirm = {
                vm.setPin(null)
                removePin = false
                toast.show("PIN removido")
            },
            dismissText = "Manter",
            onDismiss = { removePin = false },
            danger = true,
        )
    }
    if (confirmImport) {
        val found = vm.findBackup()
        AoVivoDialog(
            title = "Importar configuração?",
            message =
                if (found != null) {
                    "A configuração atual será substituída pela do arquivo encontrado em ${found.parent}."
                } else {
                    "Conecte o pendrive com o arquivo cameras-ao-vivo-config.json e tente de novo."
                },
            confirmText = if (found != null) "Importar" else null,
            onConfirm = {
                confirmImport = false
                pinFlow = PinFlow.IMPORT
            },
            onDismiss = { confirmImport = false },
        )
    }
    if (confirmClear) {
        AoVivoDialog(
            title = "Limpar todos os dados?",
            message = "Câmeras, senhas e preferências serão apagadas desta TV Box. Gravações e fotos continuam no armazenamento.",
            confirmText = "Apagar",
            onConfirm = {
                confirmClear = false
                vm.clearAll(onCleared)
            },
            onDismiss = { confirmClear = false },
            danger = true,
        )
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
