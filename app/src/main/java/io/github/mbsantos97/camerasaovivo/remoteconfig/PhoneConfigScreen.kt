package io.github.mbsantos97.camerasaovivo.remoteconfig

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.network.IpUtils
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoButton
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.EmptyState
import io.github.mbsantos97.camerasaovivo.ui.designsystem.InfoRow
import io.github.mbsantos97.camerasaovivo.ui.designsystem.tabular
import io.github.mbsantos97.camerasaovivo.ui.navigation.ScreenScaffold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Liga o servidor aos dados do app. */
class AppRemoteConfigBackend(
    private val container: AppContainer,
) : RemoteConfigBackend {
    private val repo = container.configRepository

    override fun list(): List<CameraSummary> =
        (repo.state.value?.cameras ?: emptyList()).filter { !it.demo }.map {
            CameraSummary(it.id, it.name, it.host, it.onvifPort, it.rtspPort, it.username, it.passwordEnc.isNotEmpty())
        }

    override suspend fun save(form: CameraForm): String {
        val existing = form.id?.let { repo.camera(it) }
        val base = existing ?: CameraConfig(id = repo.newCameraId(), name = form.name, host = form.host)
        val cam =
            base.copy(
                name = form.name.trim(),
                host = form.host.trim(),
                onvifPort = form.onvifPort,
                rtspPort = form.rtspPort,
                username = form.username.trim(),
                passwordEnc = if (form.password.isNotEmpty()) repo.encryptPassword(form.password) else base.passwordEnc,
                streams = if (existing?.host == form.host.trim()) existing.streams else null,
            )
        repo.upsertCamera(cam)
        repo.updateSettings { it.copy(firstRunDone = true) }
        AppLogger.i("PhoneConfig", "Câmera salva pelo celular: ${cam.name}")
        // Testa e resolve os streams em segundo plano.
        container.appScope.launch {
            runCatching { container.streamResolver.resolve(cam, repo.password(cam)) }.onSuccess { r ->
                repo.updateCamera(cam.id) {
                    it.copy(
                        streams = r.streams,
                        manufacturer = r.info?.manufacturer,
                        model = r.info?.model,
                        firmware = r.info?.firmware,
                        serial = r.info?.serial,
                        mac = r.mac,
                    )
                }
            }
        }
        return cam.id
    }

    override suspend fun delete(id: String) {
        repo.removeCamera(id)
    }
}

/** QR code com ZXing (só o core, sem câmera nem UI). */
fun qrBitmap(
    text: String,
    size: Int,
    dark: Int,
    light: Int,
): ImageBitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) dark else light }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.RGB_565).asImageBitmap()
}

@Composable
fun PhoneConfigScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val ip = remember { IpUtils.localIpv4() }
    val server = remember { LocalWebConfigServer(AppRemoteConfigBackend(container)) }
    var remaining by remember { mutableLongStateOf(TIMEOUT_MS) }
    val cfg by container.configRepository.config.collectAsStateWithLifecycle(null)
    val done = remember { FocusRequester() }
    val c = AoVivoTheme.colors

    DisposableEffect(server) {
        runCatching { server.start(SOCKET_TIMEOUT_MS, true) }.onFailure { AppLogger.e("PhoneConfig", "Servidor não iniciou", it) }
        AppLogger.i("PhoneConfig", "Servidor ligado na porta ${LocalWebConfigServer.PORT}")
        onDispose {
            server.stop()
            AppLogger.i("PhoneConfig", "Servidor desligado")
        }
    }
    LaunchedEffect(Unit) {
        runCatching { done.requestFocus() }
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1_000
        }
        onBack()
    }

    ScreenScaffold("Configurar pelo celular", icon = AoVivoIcons.Phone, trailing = {
        AoVivoButton("Concluir", onBack, icon = AoVivoIcons.Check, modifier = Modifier.focusRequester(done))
    }) {
        if (ip == null) {
            EmptyState("Sem rede", icon = AoVivoIcons.Wifi, message = "Conecte a TV Box ao Wi-Fi de casa e tente de novo.")
            return@ScreenScaffold
        }
        val url = "http://$ip:${LocalWebConfigServer.PORT}"
        val qrPx = 440
        val qr = remember(url) { qrBitmap(url, qrPx, c.bg.toArgb(), c.textPrimary.toArgb()) }
        Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xl)) {
            Box(Modifier.background(c.textPrimary, AoVivoTheme.shapes.radiusLg).padding(AoVivoTheme.spacing.sm)) {
                Image(qr, "QR code para $url", modifier = Modifier.size(AoVivoTheme.sizes.qrSize))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm)) {
                Text("1. No celular, conectado ao mesmo Wi-Fi, aponte a câmera para o QR code ou abra:", style = AoVivoTheme.type.body, color = c.textSecondary)
                Text(url, style = AoVivoTheme.type.headline, color = c.accent)
                Text("2. Digite o PIN:", style = AoVivoTheme.type.body, color = c.textSecondary)
                Text(server.pin.chunked(3).joinToString(" "), style = AoVivoTheme.type.display, color = c.textPrimary)
                Text("3. Adicione as câmeras com IP e a senha da \"Conexão NVR\" do app Yoosee.", style = AoVivoTheme.type.body, color = c.textSecondary)
                Text(
                    "Desliga sozinho em ${"%d:%02d".format(remaining / 60_000, remaining / 1000 % 60)}",
                    style = AoVivoTheme.type.caption.tabular(),
                    color = c.textSecondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
                Text("Câmeras nesta TV Box", style = AoVivoTheme.type.title, color = c.textPrimary)
                val cams = cfg?.cameras.orEmpty().filter { !it.demo }
                if (cams.isEmpty()) Text("Nenhuma ainda.", style = AoVivoTheme.type.body, color = c.textSecondary)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xxs)) {
                    items(cams, key = { it.id }) { cam ->
                        InfoRow(cam.name, if (cam.streams?.sources?.isNotEmpty() == true) "${cam.host} ✓" else cam.host)
                    }
                }
            }
        }
    }
}

private const val TIMEOUT_MS = 10 * 60_000L
private const val SOCKET_TIMEOUT_MS = 5_000
