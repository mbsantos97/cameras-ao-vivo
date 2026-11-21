package io.github.mbsantos97.camerasaovivo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.camera.ConnectionTester
import io.github.mbsantos97.camerasaovivo.camera.ResolveResult
import io.github.mbsantos97.camerasaovivo.camera.StepStatus
import io.github.mbsantos97.camerasaovivo.camera.TestStep
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectingIndicator

/** Lista de passos com ✓/✗ e detalhe (usada no teste de conexão e no Diagnóstico). */
@Composable
fun StepList(
    steps: List<TestStep>,
    modifier: Modifier = Modifier,
) {
    val c = AoVivoTheme.colors
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
        items(steps, key = { it.title }) { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm), verticalAlignment = Alignment.Top) {
                when (s.status) {
                    StepStatus.RUNNING -> ConnectingIndicator(Modifier.size(AoVivoTheme.sizes.iconTile))
                    StepStatus.OK -> Icon(painterResource(AoVivoIcons.Check), null, tint = c.online, modifier = Modifier.size(AoVivoTheme.sizes.iconTile))
                    StepStatus.FAIL -> Icon(painterResource(AoVivoIcons.Close), null, tint = c.danger, modifier = Modifier.size(AoVivoTheme.sizes.iconTile))
                    StepStatus.SKIPPED ->
                        Icon(
                            painterResource(AoVivoIcons.Info),
                            null,
                            tint = c.textSecondary,
                            modifier = Modifier.size(AoVivoTheme.sizes.iconTile),
                        )
                }
                Column {
                    Text(s.title, style = AoVivoTheme.type.body, color = c.textPrimary)
                    if (s.detail != null) Text(s.detail, style = AoVivoTheme.type.caption, color = c.textSecondary)
                }
            }
        }
    }
}

@Immutable
private data class TestState(
    val steps: List<TestStep> = emptyList(),
    val done: Boolean = false,
)

@Composable
fun ConnectionTestDialog(
    camera: CameraConfig,
    password: String,
    tester: ConnectionTester,
    onClose: (ResolveResult?) -> Unit,
) {
    var state by remember { mutableStateOf(TestState()) }
    LaunchedEffect(camera, password) {
        tester.run(camera, password).collect { state = state.copy(steps = it) }
        state = state.copy(done = true)
    }
    val ok = state.done && tester.lastResult != null
    AoVivoDialog(
        title = "Testar conexão — ${camera.name.ifBlank { camera.host }}",
        message =
            when {
                !state.done -> "Testando…"
                ok -> "Tudo certo. Os endereços encontrados serão usados pela câmera."
                else -> "Algo falhou. Confira IP, senha e se a \"Conexão NVR\" está ativa no app Yoosee."
            },
        dismissText = "Fechar",
        onDismiss = { onClose(tester.lastResult) },
        confirmText = if (ok) "Usar resultado" else null,
        onConfirm = { onClose(tester.lastResult) },
    ) {
        StepList(state.steps, Modifier.heightIn(max = AoVivoTheme.sizes.dialogWidth / 1.4f))
    }
}
