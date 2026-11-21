package io.github.mbsantos97.camerasaovivo.ui.diagnostics

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.BuildConfig
import io.github.mbsantos97.camerasaovivo.camera.ConnectionTester
import io.github.mbsantos97.camerasaovivo.camera.LensMotionDetector
import io.github.mbsantos97.camerasaovivo.camera.StepStatus
import io.github.mbsantos97.camerasaovivo.camera.TestStep
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.logging.LogMasker
import io.github.mbsantos97.camerasaovivo.core.storage.StorageLocations
import io.github.mbsantos97.camerasaovivo.core.time.Formats
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.MotorizedLens
import io.github.mbsantos97.camerasaovivo.data.config.StreamQuality
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import io.github.mbsantos97.camerasaovivo.onvif.model.ServiceAddresses
import io.github.mbsantos97.camerasaovivo.player.PlayOptions
import io.github.mbsantos97.camerasaovivo.player.StreamSource
import io.github.mbsantos97.camerasaovivo.player.VideoRegion
import io.github.mbsantos97.camerasaovivo.player.VideoSurface
import io.github.mbsantos97.camerasaovivo.player.VlcStreamPlayer
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoButton
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ButtonKind
import io.github.mbsantos97.camerasaovivo.ui.designsystem.EmptyState
import io.github.mbsantos97.camerasaovivo.ui.designsystem.LocalToast
import io.github.mbsantos97.camerasaovivo.ui.designsystem.SelectorRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ToggleRow
import io.github.mbsantos97.camerasaovivo.ui.settings.StepList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@Immutable
data class DiagnosticsState(
    val cameras: List<CameraConfig> = emptyList(),
    val cameraId: String? = null,
    val steps: List<TestStep> = emptyList(),
    val running: Boolean = false,
    val perfOverlay: Boolean = false,
) {
    val camera: CameraConfig? get() = cameras.firstOrNull { it.id == cameraId }
}

class DiagnosticsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state
    val preview = VlcStreamPlayer(container.libVlc, "Diagnóstico")
    private var tester: ConnectionTester? = null

    init {
        viewModelScope.launch {
            container.configRepository.config.collect { cfg ->
                val cams = cfg.cameras.filter { !it.demo }.sortedBy { it.order }
                _state.update { s -> s.copy(cameras = cams, cameraId = s.cameraId ?: cams.firstOrNull()?.id, perfOverlay = cfg.settings.perfOverlay) }
            }
        }
    }

    fun select(id: String) = _state.update { it.copy(cameraId = id, steps = emptyList()) }

    fun setPerfOverlay(on: Boolean) {
        viewModelScope.launch { container.configRepository.updateSettings { it.copy(perfOverlay = on) } }
    }

    private fun addStep(s: TestStep) = _state.update { st -> st.copy(steps = st.steps.filterNot { it.title == s.title } + s) }

    fun runAll() {
        val cam = _state.value.camera ?: return
        if (_state.value.running) return
        viewModelScope.launch {
            _state.update { it.copy(running = true, steps = emptyList()) }
            val pwd = container.configRepository.password(cam)
            val t = container.newConnectionTester().also { tester = it }
            t.run(cam, pwd).collect { steps -> _state.update { it.copy(steps = steps) } }
            t.lastResult?.let { r -> container.configRepository.updateCamera(cam.id) { it.copy(streams = r.streams) } }
            // Estatísticas do player: abre o substream na prévia por alguns segundos.
            val uri = t.lastResult?.streams?.sources?.firstOrNull()?.let { if (cam.gridQuality == StreamQuality.SUB) it.subUri ?: it.mainUri else it.mainUri }
            if (uri != null) {
                addStep(TestStep("Player", StepStatus.RUNNING))
                preview.play(
                    StreamSource(uri, cam.username, pwd, cam.learnedTransport ?: cam.transport),
                    PlayOptions(hardwareDecoding = !cam.forceSoftwareDecode),
                )
                val ok = withTimeoutOrNull(FIRST_FRAME_MS) { preview.firstFrame.first { it } } != null
                delay(STATS_WAIT_MS)
                val s = preview.stats()
                addStep(
                    if (ok) {
                        TestStep(
                            "Player",
                            StepStatus.OK,
                            "${s.videoSize?.let {
                                "${it.width}x${it.height}"
                            } ?: "?"} · ${s.codec ?: "?"} · ${"%.1f".format(s.fps)} fps · ${s.bitrateKbps} kb/s · " +
                                if (s.hardwareDecoding) "decoder de hardware" else "decoder de software",
                        )
                    } else {
                        TestStep("Player", StepStatus.FAIL, "Nenhum frame em ${FIRST_FRAME_MS / 1000} s")
                    },
                )
            }
            _state.update { it.copy(running = false) }
        }
    }

    /** Move 0,5 s para a direita e para (só depois da confirmação na tela). */
    fun ptzTest() {
        val cam = _state.value.camera ?: return
        val profile = cam.streams?.ptzProfileToken
        viewModelScope.launch {
            addStep(TestStep("Teste de PTZ", StepStatus.RUNNING))
            val r =
                runCatching {
                    val c = client(cam)
                    c.continuousMove(profile ?: error("Câmera sem perfil PTZ"), 0.5f, 0f, "PT1S")
                    delay(PTZ_TEST_MS)
                    c.stop(profile)
                }
            addStep(
                r.fold({
                    TestStep("Teste de PTZ", StepStatus.OK, "Moveu para a direita e parou")
                }, { TestStep("Teste de PTZ", StepStatus.FAIL, ConnectionTester.friendly(it)) }),
            )
        }
    }

    private suspend fun client(cam: CameraConfig) =
        container.onvifFactory(cam, container.configRepository.password(cam)).also { c ->
            c.useCachedServices(ServiceAddresses(media = cam.streams?.mediaXAddr, ptz = cam.streams?.ptzXAddr))
            if (!c.clockSynced) runCatching { c.syncClock() }
        }

    /** Compara as metades da imagem empilhada antes e depois de mover 1 s. */
    fun identifyLens(snapshot: () -> Bitmap?) {
        val cam = _state.value.camera ?: return
        val profile = cam.streams?.ptzProfileToken
        viewModelScope.launch {
            addStep(TestStep("Lente motorizada", StepStatus.RUNNING))
            val result =
                runCatching {
                    if (!preview.firstFrame.value) error("Execute o diagnóstico antes (a prévia precisa estar tocando)")
                    val before = withContext(Dispatchers.Main) { snapshot() } ?: error("Sem imagem")
                    val c = client(cam)
                    c.continuousMove(profile ?: error("Câmera sem PTZ"), 0.6f, 0f, "PT2S")
                    delay(1_000)
                    c.stop(profile)
                    delay(1_500)
                    val after = withContext(Dispatchers.Main) { snapshot() } ?: error("Sem imagem")
                    val w = SAMPLE_W
                    val h = (before.height * w / before.width).coerceAtLeast(2)
                    val a = Bitmap.createScaledBitmap(before, w, h, true)
                    val b = Bitmap.createScaledBitmap(after, w, h, true)
                    val pa = IntArray(w * h).also { a.getPixels(it, 0, w, 0, 0, w, h) }
                    val pb = IntArray(w * h).also { b.getPixels(it, 0, w, 0, 0, w, h) }
                    val diffs = LensMotionDetector.halfDifferences(pa, pb, w, h, step = 1)
                    LensMotionDetector.decide(diffs) ?: error("Inconclusivo (cima ${"%.1f".format(diffs.first)}, baixo ${"%.1f".format(diffs.second)})")
                }
            result.onSuccess { lens ->
                container.configRepository.updateCamera(cam.id) { it.copy(detectedMotorizedLens = lens) }
                addStep(TestStep("Lente motorizada", StepStatus.OK, if (lens == MotorizedLens.TOP) "Lente de cima" else "Lente de baixo"))
            }
            result.onFailure { addStep(TestStep("Lente motorizada", StepStatus.FAIL, it.message)) }
        }
    }

    private fun outDir(): File? = StorageLocations.preferRemovable(container.appContext)?.dir

    suspend fun saveReport(): String? =
        withContext(Dispatchers.IO) {
            val dir = outDir() ?: return@withContext null
            val cam = _state.value.camera
            val text =
                buildString {
                    appendLine("Câmeras ao Vivo ${BuildConfig.VERSION_NAME} — relatório de diagnóstico")
                    appendLine("Data: ${Formats.dateTime(System.currentTimeMillis())}")
                    cam?.let { appendLine("Câmera: ${it.name} (${it.host}) · ${listOfNotNull(it.manufacturer, it.model, it.firmware).joinToString(" ")}") }
                    appendLine()
                    _state.value.steps.forEach { s ->
                        appendLine("[${if (s.status == StepStatus.OK) "OK" else s.status.name}] ${s.title}: ${s.detail.orEmpty()}")
                    }
                    appendLine()
                    appendLine("Log (últimas linhas):")
                    AppLogger.lines().takeLast(REPORT_LOG_LINES).forEach(::appendLine)
                }
            val f = File(dir, "cameras-ao-vivo-diagnostico-${Formats.fileStamp(System.currentTimeMillis())}.txt")
            f.writeText(LogMasker.mask(text))
            f.absolutePath
        }

    /** Respostas ONVIF reais da câmera, sem senhas, serial e MAC, para virar fixtures de teste. */
    suspend fun exportOnvif(): String? =
        withContext(Dispatchers.IO) {
            val raw = tester?.lastClient?.rawResponses?.toMap().orEmpty()
            if (raw.isEmpty()) return@withContext null
            val dir = File(outDir() ?: return@withContext null, "onvif-respostas-${Formats.fileStamp(System.currentTimeMillis())}").apply { mkdirs() }
            raw.forEach { (op, xml) -> File(dir, "$op.xml").writeText(LogMasker.mask(xml)) }
            dir.absolutePath
        }

    override fun onCleared() = preview.release()

    private companion object {
        const val FIRST_FRAME_MS = 8_000L
        const val STATS_WAIT_MS = 3_000L
        const val PTZ_TEST_MS = 500L
        const val SAMPLE_W = 96
        const val REPORT_LOG_LINES = 300
    }
}

@Composable
fun DiagnosticsScreen(
    vm: DiagnosticsViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val st by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var confirmPtz by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.preview.stop() }

    io.github.mbsantos97.camerasaovivo.ui.navigation.ScreenScaffold("Diagnóstico", icon = AoVivoIcons.Diagnostics) {
        if (st.cameras.isEmpty()) {
            EmptyState("Nenhuma câmera para diagnosticar", icon = AoVivoIcons.Camera)
            ToggleRow("Painel de desempenho", st.perfOverlay, vm::setPerfOverlay, icon = AoVivoIcons.Speed)
            return@ScreenScaffold
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.lg)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm)) {
                SelectorRow(
                    "Câmera",
                    st.cameras.map {
                        "${it.name} (${it.host})"
                    },
                    st.cameras.indexOfFirst { it.id == st.cameraId }.coerceAtLeast(0),
                    { i -> vm.select(st.cameras[i].id) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
                    AoVivoButton(if (st.running) "Testando…" else "Executar diagnóstico", vm::runAll, icon = AoVivoIcons.Diagnostics, enabled = !st.running)
                    if (st.camera?.streams?.ptzSupported == true) AoVivoButton("Testar PTZ", { confirmPtz = true }, kind = ButtonKind.SECONDARY)
                    if (st.camera?.streams?.sources?.any { it.stacked } == true) {
                        AoVivoButton("Identificar lente motorizada", { vm.identifyLens { vm.preview.snapshot() } }, kind = ButtonKind.SECONDARY)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
                    AoVivoButton("Salvar relatório", {
                        scope.launch { toast.show(vm.saveReport()?.let { "Relatório salvo: $it" } ?: "Nenhum armazenamento disponível") }
                    }, kind = ButtonKind.SECONDARY, icon = AoVivoIcons.Usb)
                    AoVivoButton("Ver log", { showLog = true }, kind = ButtonKind.SECONDARY)
                    AoVivoButton("Exportar respostas ONVIF", {
                        scope.launch { toast.show(vm.exportOnvif()?.let { "Salvo em $it" } ?: "Execute o diagnóstico antes") }
                    }, kind = ButtonKind.SECONDARY)
                }
                ToggleRow(
                    "Painel de desempenho",
                    st.perfOverlay,
                    vm::setPerfOverlay,
                    icon = AoVivoIcons.Speed,
                    subtitle = "FPS da interface, RAM, streams, decoder e latência do PTZ",
                )
                StepList(st.steps, Modifier.heightIn(max = AoVivoTheme.sizes.dialogWidth))
            }
            Column(Modifier.width(AoVivoTheme.sizes.panelMaxWidth / 1.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
                Text("Prévia", style = AoVivoTheme.type.label, color = AoVivoTheme.colors.textSecondary)
                VideoSurface(vm.preview, VideoRegion.FIT, Modifier.aspectRatio(16f / 9f), cornerRadius = AoVivoTheme.shapes.radiusMdValue)
            }
        }
    }
    if (confirmPtz) {
        AoVivoDialog(
            title = "Testar PTZ?",
            message = "A câmera vai girar para a direita por meio segundo e parar.",
            confirmText = "Testar",
            onConfirm = {
                confirmPtz = false
                vm.ptzTest()
            },
            onDismiss = { confirmPtz = false },
        )
    }
    if (showLog) {
        AoVivoDialog(title = "Log (últimas 1000 linhas)", onDismiss = { showLog = false }, dismissText = "Fechar") {
            LazyColumn(Modifier.heightIn(max = AoVivoTheme.sizes.dialogWidth)) {
                items(AppLogger.lines().reversed()) { line -> Text(line, style = AoVivoTheme.type.caption, color = AoVivoTheme.colors.textSecondary) }
            }
        }
    }
}
