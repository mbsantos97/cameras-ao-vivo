package io.github.mbsantos97.camerasaovivo.ui.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.camera.sdcard.SdCardPlaybackSource
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKey
import io.github.mbsantos97.camerasaovivo.core.input.RemoteKeyMapper
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.time.Formats
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.recordings.Segment
import io.github.mbsantos97.camerasaovivo.data.recordings.SegmentIndex
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import io.github.mbsantos97.camerasaovivo.player.PlayOptions
import io.github.mbsantos97.camerasaovivo.player.PlayerEvent
import io.github.mbsantos97.camerasaovivo.player.StreamPlayer
import io.github.mbsantos97.camerasaovivo.player.StreamSource
import io.github.mbsantos97.camerasaovivo.player.VideoRegion
import io.github.mbsantos97.camerasaovivo.player.VideoSurface
import io.github.mbsantos97.camerasaovivo.player.VlcStreamPlayer
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectingIndicator
import io.github.mbsantos97.camerasaovivo.ui.designsystem.EmptyState
import io.github.mbsantos97.camerasaovivo.ui.designsystem.tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class PlaybackState(
    val cameraName: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val segment: Segment? = null,
    val rate: Float = 1f,
    val paused: Boolean = false,
)

class PlaybackViewModel(
    private val container: AppContainer,
    private val cameraId: String,
    private val origin: PlaybackOrigin,
    private val startMillis: Long,
) : ViewModel() {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state
    val player: StreamPlayer = VlcStreamPlayer(container.libVlc, "Gravação")
    private var segments: List<Segment> = emptyList()
    private var cam: CameraConfig? = null
    private var sd: SdCardPlaybackSource? = null

    init {
        viewModelScope.launch {
            val c = container.configRepository.config.first().cameras.firstOrNull { it.id == cameraId }
            cam = c
            if (c == null) {
                _state.update { it.copy(loading = false, error = "Câmera não encontrada") }
                return@launch
            }
            _state.update { it.copy(cameraName = c.name) }
            segments =
                when (origin) {
                    PlaybackOrigin.LOCAL -> container.recordings.segments(c.id, container.configRepository.current().settings.recording)
                    PlaybackOrigin.SD -> {
                        val src = SdCardPlaybackSource(container.onvifFactory(c, container.configRepository.password(c)), c).also { sd = it }
                        val day = SegmentIndex.dayStart(startMillis)
                        runCatching { withContext(Dispatchers.IO) { src.segmentsForDay(day, SegmentIndex.nextDayStart(day)) } }.getOrDefault(emptyList())
                    }
                }
            playAt(startMillis)
        }
        viewModelScope.launch {
            player.events.collect { e -> if (e == PlayerEvent.Ended) next() }
        }
    }

    /** Horário real do que está na tela. */
    fun currentMillis(): Long {
        val s = _state.value.segment ?: return startMillis
        return s.startMillis + player.positionMs
    }

    private suspend fun playAt(t: Long) {
        val seg = SegmentIndex.find(segments, t)
        if (seg == null) {
            _state.update { it.copy(loading = false, error = "Não há gravação a partir de ${Formats.dateTime(t)}") }
            return
        }
        val offsetSec = ((t - seg.startMillis).coerceAtLeast(0) / 1000.0)
        val c = cam ?: return
        val source =
            when (origin) {
                PlaybackOrigin.LOCAL -> StreamSource(File(seg.path!!).toURI().toString(), live = false)
                PlaybackOrigin.SD -> {
                    val uri = withContext(Dispatchers.IO) { runCatching { sd?.replayUri(seg.path ?: "") }.getOrNull() }
                    if (uri == null) {
                        _state.update { it.copy(loading = false, error = "A reprodução do cartão SD via ONVIF não funcionou nesta câmera") }
                        return
                    }
                    StreamSource(uri, c.username, container.configRepository.password(c), c.learnedTransport ?: c.transport, live = false)
                }
            }
        AppLogger.i("Playback", "${c.name}: gravação de ${Formats.dateTime(seg.startMillis)} (+${offsetSec.toInt()} s)")
        _state.update { it.copy(segment = seg, loading = true, error = null, rate = 1f, paused = false) }
        player.play(source, PlayOptions(networkCachingMs = 1000, audio = true, startTimeSeconds = offsetSec.takeIf { it > 0 }))
        player.firstFrame.first { it }
        _state.update { it.copy(loading = false) }
    }

    private fun next() {
        val cur = _state.value.segment ?: return
        viewModelScope.launch { playAt(cur.endMillis + 1) }
    }

    fun togglePause() {
        if (player.isPaused) player.resume() else player.pause()
        _state.update { it.copy(paused = player.isPaused) }
    }

    fun seekBy(deltaMs: Long) {
        val seg = _state.value.segment ?: return
        val target = currentMillis() + deltaMs
        if (target in seg.startMillis until seg.endMillis) {
            player.seekTo(target - seg.startMillis)
        } else {
            viewModelScope.launch { playAt(target.coerceAtLeast(segments.firstOrNull()?.startMillis ?: target)) }
        }
    }

    fun changeRate(dir: Int) {
        val i = (RATES.indexOf(_state.value.rate) + dir).coerceIn(0, RATES.lastIndex)
        player.setRate(RATES[i])
        _state.update { it.copy(rate = RATES[i]) }
    }

    fun onStop() = player.pause()

    override fun onCleared() = player.release()

    companion object {
        val RATES = listOf(0.5f, 1f, 2f, 4f)
    }
}

@Composable
fun PlaybackScreen(
    vm: PlaybackViewModel,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val st by vm.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    var lastKeyAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(0L) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onStop() }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        while (true) {
            now = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    val barVisible = now - lastKeyAt < BAR_MS || st.paused || st.loading
    Box(
        Modifier
            .fillMaxSize()
            .background(AoVivoTheme.colors.bg)
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val n = e.nativeKeyEvent
                lastKeyAt = System.currentTimeMillis()
                val held = n.repeatCount > 0
                when (RemoteKeyMapper.map(n.keyCode)) {
                    RemoteKey.OK, RemoteKey.PLAY_PAUSE, RemoteKey.PLAY, RemoteKey.PAUSE -> if (!held) vm.togglePause()
                    RemoteKey.LEFT, RemoteKey.REWIND -> vm.seekBy(if (held) -LONG_SEEK else -SHORT_SEEK)
                    RemoteKey.RIGHT, RemoteKey.FAST_FORWARD -> vm.seekBy(if (held) LONG_SEEK else SHORT_SEEK)
                    RemoteKey.UP -> if (!held) vm.changeRate(1)
                    RemoteKey.DOWN -> if (!held) vm.changeRate(-1)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }.focusable(),
    ) {
        VideoSurface(vm.player, VideoRegion.FIT, Modifier.fillMaxSize())
        if (st.loading && st.error == null) ConnectingIndicator(Modifier.align(Alignment.Center))
        st.error?.let { EmptyState(it, icon = AoVivoIcons.Recordings, modifier = Modifier.align(Alignment.Center)) }
        AnimatedVisibility(
            barVisible,
            enter = fadeIn(AoVivoTheme.motion.medium()),
            exit = fadeOut(AoVivoTheme.motion.medium()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlaybackBar(st, currentMillis = { vm.currentMillis() }, tick = now)
        }
    }
}

@Composable
private fun PlaybackBar(
    st: PlaybackState,
    currentMillis: () -> Long,
    tick: Long,
) {
    val c = AoVivoTheme.colors
    val s = AoVivoTheme.spacing
    val t = remember(tick) { currentMillis() }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(c.scrim.copy(alpha = 0f), c.scrim)))
            .padding(horizontal = s.safeHorizontal, vertical = s.lg),
        verticalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.sm)) {
            Icon(
                painterResource(if (st.paused) AoVivoIcons.Pause else AoVivoIcons.Play),
                null,
                tint = c.textPrimary,
                modifier = Modifier.size(AoVivoTheme.sizes.iconBar),
            )
            Text(Formats.hourMinuteSecond(t), style = AoVivoTheme.type.title.tabular(), color = c.textPrimary)
            Text(Formats.dayMonth(t), style = AoVivoTheme.type.label, color = c.textSecondary)
            Text(st.cameraName, style = AoVivoTheme.type.label, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text("${if (st.rate % 1f == 0f) st.rate.toInt() else st.rate}x", style = AoVivoTheme.type.label.tabular(), color = c.textPrimary)
        }
        val seg = st.segment
        Canvas(Modifier.fillMaxWidth().height(s.xxs)) {
            drawRect(c.surfaceHigh, size = size)
            if (seg != null && seg.durationMillis > 0) {
                val p = ((t - seg.startMillis).toFloat() / seg.durationMillis).coerceIn(0f, 1f)
                drawRect(c.accent, Offset.Zero, Size(size.width * p, size.height))
            }
        }
        Text("OK pausa · ←/→ 10 s (segure: 60 s) · ↑/↓ velocidade · Voltar sai", style = AoVivoTheme.type.caption, color = c.textSecondary)
    }
}

private const val SHORT_SEEK = 10_000L
private const val LONG_SEEK = 60_000L
private const val BAR_MS = 5_000L
private const val TICK_MS = 500L
