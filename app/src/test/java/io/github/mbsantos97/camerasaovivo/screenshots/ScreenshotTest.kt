package io.github.mbsantos97.camerasaovivo.screenshots

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import io.github.mbsantos97.camerasaovivo.ui.catalog.DesignCatalogScreen
import io.github.mbsantos97.camerasaovivo.ui.components.CameraTileOverlay
import io.github.mbsantos97.camerasaovivo.ui.components.InfoPanel
import io.github.mbsantos97.camerasaovivo.ui.components.OverlayClock
import io.github.mbsantos97.camerasaovivo.ui.components.PanelCamera
import io.github.mbsantos97.camerasaovivo.ui.components.PanelInfo
import io.github.mbsantos97.camerasaovivo.ui.components.TileUi
import io.github.mbsantos97.camerasaovivo.ui.components.TvSafeArea
import io.github.mbsantos97.camerasaovivo.ui.components.Watermark
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectionStatus
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Gera prints das telas pela JVM para docs/screens (não roda no CI por padrão).
 * Uso: ./gradlew :app:testDebugUnitTest --tests "*ScreenshotTest*" -Pscreenshots
 * O vídeo (TextureView) não é renderizado pela JVM: os quadrantes usam um fundo simulado.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-land-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val outDir = File(System.getProperty("screenshotsDir") ?: "build/screens")

    @Before
    fun only() {
        assumeTrue(System.getProperty("screenshots") == "true")
        outDir.mkdirs()
    }

    private fun capture(
        name: String,
        content: @Composable () -> Unit,
    ) {
        rule.setContent { AoVivoTheme { content() } }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(qualifiers = "w960dp-h3400dp-xhdpi")
    fun catalogo() = capture("catalogo_ds") { DesignCatalogScreen(onBack = {}) }

    @Test
    fun gradeAoVivo() =
        capture("grade_ao_vivo") {
            val s = AoVivoTheme.spacing
            Box(Modifier.fillMaxSize().background(AoVivoTheme.colors.bg)) {
                TvSafeArea {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(s.gridGap)) {
                        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dpZero())) {
                            FakeVideo(Color(0xFF2B4A63), Modifier.weight(1f)) {
                                CameraTileOverlay(TileUi("Câmera Externa", ConnectionStatus.ONLINE, videoVisible = true, subtitle = "Lente de cima"), null)
                            }
                            FakeVideo(Color(0xFF2E5A4B), Modifier.weight(1f)) {
                                CameraTileOverlay(
                                    TileUi(
                                        "Câmera Externa",
                                        ConnectionStatus.ONLINE,
                                        videoVisible = true,
                                        subtitle = "Lente de baixo",
                                        motorized = true,
                                        recording = true,
                                    ),
                                    null,
                                )
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(s.gridGap)) {
                            FakeVideo(Color(0xFF5A3E2B), Modifier.weight(1f)) {
                                CameraTileOverlay(TileUi("Lâmpada", ConnectionStatus.ONLINE, videoVisible = true, ptz = true, motion = true), null)
                            }
                            Box(Modifier.weight(1f)) {
                                InfoPanel(
                                    PanelInfo(
                                        listOf(PanelCamera("Câmera Externa", ConnectionStatus.ONLINE), PanelCamera("Lâmpada", ConnectionStatus.ONLINE)),
                                        "41,2 GB",
                                    ),
                                )
                            }
                        }
                    }
                    Watermark()
                }
                OverlayClock()
            }
        }

    @Test
    fun estadosDeConexao() =
        capture("estados_conexao") {
            val s = AoVivoTheme.spacing
            Box(Modifier.fillMaxSize().background(AoVivoTheme.colors.bg)) {
                TvSafeArea {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(s.gridGap)) {
                        Box(Modifier.weight(1f)) { CameraTileOverlay(TileUi("Câmera Externa", ConnectionStatus.CONNECTING), null) }
                        Box(Modifier.weight(1f)) { CameraTileOverlay(TileUi("Lâmpada", ConnectionStatus.OFFLINE, attempt = 3), null) }
                    }
                    Watermark()
                }
            }
        }
}

private fun Float.dpZero() = androidx.compose.ui.unit.Dp(0f)

private fun Int.dpZero() = androidx.compose.ui.unit.Dp(0f)

@Composable
private fun FakeVideo(
    base: Color,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(base, Color.Black)), AoVivoTheme.shapes.radiusMd)
            .padding(0.dpZero()),
    ) { content() }
}
