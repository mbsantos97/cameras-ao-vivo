package io.github.mbsantos97.camerasaovivo.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.R
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import io.github.mbsantos97.camerasaovivo.onvif.model.DiscoveredDevice
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoButton
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ButtonKind
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectingIndicator
import io.github.mbsantos97.camerasaovivo.ui.designsystem.LocalToast
import io.github.mbsantos97.camerasaovivo.ui.designsystem.NavigationRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextEntryRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextInputDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextInputKind
import io.github.mbsantos97.camerasaovivo.ui.navigation.ScreenScaffold
import io.github.mbsantos97.camerasaovivo.ui.settings.ConnectionTestDialog
import io.github.mbsantos97.camerasaovivo.ui.settings.DiscoveryViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class Step { WELCOME, HOW, SEARCH, CAMERA }

/** Sugestões de nome na ordem em que as câmeras são adicionadas. */
private fun suggestedName(index: Int) =
    when (index) {
        0 -> "Câmera Externa"
        1 -> "Lâmpada"
        else -> "Câmera ${index + 1}"
    }

/**
 * Assistente de primeira execução: boas-vindas → como ativar RTSP/ONVIF no Yoosee →
 * configurar pelo celular (em destaque) ou buscar na rede → nome + senha → teste → Ao Vivo.
 */
@Composable
fun FirstRunWizard(
    container: AppContainer,
    discovery: DiscoveryViewModel,
    onPhoneConfig: () -> Unit,
    onManual: () -> Unit,
    onFinish: () -> Unit,
) {
    var step by remember { mutableStateOf(Step.WELCOME) }
    var picked by remember { mutableStateOf<DiscoveredDevice?>(null) }
    val added = remember { mutableStateListOf<String>() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun finish() {
        scope.launch {
            container.configRepository.updateSettings { it.copy(firstRunDone = true) }
            onFinish()
        }
    }

    // Voltando do "Configurar pelo celular" com câmeras já salvas: segue direto para a grade.
    LaunchedEffect(Unit) {
        if (container.configRepository.config.first().cameras.any { !it.demo }) finish()
    }

    BackHandler {
        step =
            when (step) {
                Step.WELCOME -> Step.WELCOME
                Step.HOW -> Step.WELCOME
                Step.SEARCH -> Step.HOW
                Step.CAMERA -> Step.SEARCH
            }
    }

    when (step) {
        Step.WELCOME -> Welcome(onNext = { step = Step.HOW })
        Step.HOW ->
            HowStep(
                onPhone = onPhoneConfig,
                onSearch = { step = Step.SEARCH },
                onManual = {
                    scope.launch {
                        container.configRepository.updateSettings { it.copy(firstRunDone = true) }
                        onManual()
                    }
                },
            )
        Step.SEARCH ->
            SearchStep(discovery, added, onPick = {
                picked = it
                step = Step.CAMERA
            }, onFinish = ::finish)
        Step.CAMERA ->
            picked?.let { d ->
                CameraStep(container, d, suggestedName(added.size), onSaved = {
                    added += d.host
                    step = Step.SEARCH
                }, onCancel = { step = Step.SEARCH })
            }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    val first = remember { FocusRequester() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = AoVivoTheme.sizes.dialogWidth * 1.4f),
            verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, modifier = Modifier.size(AoVivoTheme.sizes.qrSize / 1.5f))
            Text("Bem-vindo ao Câmeras ao Vivo", style = AoVivoTheme.type.headline, color = AoVivoTheme.colors.textPrimary)
            Text(
                "Veja e controle suas câmeras Yoosee pela TV, só na rede de casa: sem nuvem, sem conta e sem cabos.",
                style = AoVivoTheme.type.body,
                color = AoVivoTheme.colors.textSecondary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
                Text("Antes de começar, no app Yoosee do celular, em cada câmera:", style = AoVivoTheme.type.title, color = AoVivoTheme.colors.textPrimary)
                Text(
                    "1. Abra as configurações da câmera → \"Conexão NVR\" (RTSP/ONVIF) e ative.",
                    style = AoVivoTheme.type.body,
                    color = AoVivoTheme.colors.textSecondary,
                )
                Text("2. Defina a senha da conexão (usuário admin).", style = AoVivoTheme.type.body, color = AoVivoTheme.colors.textSecondary)
                Text(
                    "3. Deixe a TV Box no mesmo roteador das câmeras (as câmeras usam Wi-Fi 2,4 GHz).",
                    style = AoVivoTheme.type.body,
                    color = AoVivoTheme.colors.textSecondary,
                )
            }
            AoVivoButton("Começar", onNext, modifier = Modifier.focusRequester(first))
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun HowStep(
    onPhone: () -> Unit,
    onSearch: () -> Unit,
    onManual: () -> Unit,
) {
    val phone = remember { FocusRequester() }
    ScreenScaffold("Como você quer configurar?", icon = AoVivoIcons.Settings) {
        Column(Modifier.widthIn(max = AoVivoTheme.sizes.dialogWidth * 1.4f), verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.md)) {
            // Opção em destaque (requisito "sem cabos"): digitar IP e senha no celular é bem mais rápido.
            AoVivoButton("Configurar pelo celular (recomendado)", onPhone, icon = AoVivoIcons.Phone, modifier = Modifier.focusRequester(phone))
            Text(
                "Aparece um QR code na TV; no celular, você preenche câmeras e senhas num formulário.",
                style = AoVivoTheme.type.caption,
                color = AoVivoTheme.colors.textSecondary,
            )
            AoVivoButton("Buscar câmeras na rede e usar o controle", onSearch, kind = ButtonKind.SECONDARY, icon = AoVivoIcons.Search)
            AoVivoButton("Pular e adicionar manualmente", onManual, kind = ButtonKind.GHOST)
        }
    }
    LaunchedEffect(Unit) { runCatching { phone.requestFocus() } }
}

@Composable
private fun SearchStep(
    discovery: DiscoveryViewModel,
    added: List<String>,
    onPick: (DiscoveredDevice) -> Unit,
    onFinish: () -> Unit,
) {
    val st by discovery.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { discovery.search() }
    ScreenScaffold(
        "Câmeras encontradas",
        icon = AoVivoIcons.Search,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm)) {
                AoVivoButton("Buscar de novo", discovery::search, kind = ButtonKind.SECONDARY, enabled = !st.searching)
                AoVivoButton(if (added.isEmpty()) "Pular" else "Concluir (${added.size})", onFinish, icon = AoVivoIcons.Check)
            }
        },
    ) {
        if (st.searching) {
            Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                ConnectingIndicator()
                Text("Procurando câmeras na rede…", style = AoVivoTheme.type.body, color = AoVivoTheme.colors.textSecondary)
            }
        } else if (st.devices.isEmpty()) {
            Text(
                "Nenhuma câmera respondeu. Confira a \"Conexão NVR\" no app Yoosee e se a TV Box está no mesmo roteador.",
                style = AoVivoTheme.type.body,
                color = AoVivoTheme.colors.textSecondary,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
            items(st.devices, key = { it.host }) { d ->
                NavigationRow(
                    title = d.name ?: d.hardware ?: "Câmera em ${d.host}",
                    value = if (d.host in added || d.host in st.knownHosts) "Adicionada" else d.host,
                    icon = if (d.host in added) AoVivoIcons.Check else AoVivoIcons.Camera,
                    onClick = { onPick(d) },
                )
            }
        }
    }
}

@Composable
private fun CameraStep(
    container: AppContainer,
    device: DiscoveredDevice,
    suggestion: String,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
) {
    val toast = LocalToast.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var name by remember { mutableStateOf(suggestion) }
    var password by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val repo = container.configRepository
    val draft = remember(name, password) { CameraConfig(id = repo.newCameraId(), name = name, host = device.host, onvifPort = device.port ?: 5000) }
    val first = remember { FocusRequester() }
    ScreenScaffold("Câmera em ${device.host}", icon = AoVivoIcons.Camera) {
        Column(Modifier.widthIn(max = AoVivoTheme.sizes.dialogWidth * 1.4f), verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
            TextEntryRow("Nome", name, { editing = "name" }, modifier = Modifier.focusRequester(first))
            TextEntryRow("Senha da Conexão NVR", password, {
                editing = "password"
            }, masked = true, placeholder = "Definida no app Yoosee", icon = AoVivoIcons.Lock)
            Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm)) {
                AoVivoButton("Testar e salvar", { testing = true }, icon = AoVivoIcons.Check)
                AoVivoButton("Voltar", onCancel, kind = ButtonKind.SECONDARY)
            }
        }
    }
    editing?.let { field ->
        TextInputDialog(
            title = if (field == "name") "Nome da câmera" else "Senha",
            initial = if (field == "name") name else password,
            kind = if (field == "name") TextInputKind.TEXT else TextInputKind.PASSWORD,
            onDismiss = { editing = null },
            onConfirm = { v ->
                if (field == "name") name = v else password = v
                editing = null
            },
        )
    }
    if (testing) {
        val tester = remember { container.newConnectionTester() }
        ConnectionTestDialog(draft, password, tester) { result ->
            testing = false
            scope.launch {
                val existing = repo.config.first().cameras.firstOrNull { it.host == device.host }
                val cam =
                    (existing ?: draft).copy(
                        name = name,
                        passwordEnc = repo.encryptPassword(password),
                        streams = result?.streams ?: existing?.streams,
                        manufacturer = result?.info?.manufacturer,
                        model = result?.info?.model,
                        firmware = result?.info?.firmware,
                        serial = result?.info?.serial,
                        mac = result?.mac,
                    )
                repo.upsertCamera(cam)
                toast.show(if (result != null) "$name adicionada" else "$name salva; o teste falhou, confira depois em Diagnóstico")
                onSaved()
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
