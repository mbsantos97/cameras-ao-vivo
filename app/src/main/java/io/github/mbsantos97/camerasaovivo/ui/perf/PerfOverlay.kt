package io.github.mbsantos97.camerasaovivo.ui.perf

import android.os.Debug
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.tabular
import io.github.mbsantos97.camerasaovivo.ui.live.LiveViewModel
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Painel de desempenho (Diagnóstico → Painel de desempenho): FPS da interface, RAM (PSS),
 * streams ativos com decoder HW/SW e latência do último comando PTZ.
 */
@Composable
fun PerfOverlay(
    vm: LiveViewModel,
    modifier: Modifier = Modifier,
) {
    var fps by remember { mutableFloatStateOf(0f) }
    var slowFrames by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var frames = 0
        var slow = 0
        var t0 = System.nanoTime()
        var last = t0
        while (true) {
            withFrameNanos { now ->
                frames++
                if (now - last > SLOW_FRAME_NS) slow++
                last = now
            }
            val now = System.nanoTime()
            if (now - t0 >= 1_000_000_000L) {
                fps = frames * 1e9f / (now - t0)
                slowFrames = if (frames > 0) slow * 100f / frames else 0f
                frames = 0
                slow = 0
                t0 = now
            }
        }
    }
    val lines by produceState(emptyList<String>()) {
        while (true) {
            val mem = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
            value =
                buildList {
                    add(String.format(Locale.ROOT, "RAM (PSS): %d MB", mem.totalPss / 1024))
                    vm.streamsInfo().forEach { (name, s) ->
                        add(
                            String.format(
                                Locale.ROOT,
                                "%s: %s %.0f fps %s",
                                name,
                                s.videoSize?.let {
                                    "${it.width}x${it.height}"
                                } ?: "-",
                                s.fps,
                                if (s.hardwareDecoding) "HW" else "SW",
                            ),
                        )
                    }
                    add("PTZ: ${vm.fullscreen.ptzLatencyMs} ms")
                }
            delay(1_000)
        }
    }
    val c = AoVivoTheme.colors
    Column(
        modifier
            .padding(AoVivoTheme.spacing.md)
            .width(AoVivoTheme.sizes.panelMaxWidth / 1.4f)
            .background(c.scrim, AoVivoTheme.shapes.radiusMd)
            .padding(AoVivoTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xxs),
    ) {
        val style = AoVivoTheme.type.caption.tabular()
        Text(String.format(Locale.ROOT, "UI: %.0f fps · lentos %.1f%%", fps, slowFrames), style = style, color = c.textPrimary)
        lines.forEach { Text(it, style = style, color = c.textPrimary) }
    }
}

private const val SLOW_FRAME_NS = 32_000_000L
