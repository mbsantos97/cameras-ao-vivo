package io.github.mbsantos97.camerasaovivo.camera

import android.graphics.Bitmap
import io.github.mbsantos97.camerasaovivo.data.config.CachedStreams
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.SourceStreams
import io.github.mbsantos97.camerasaovivo.data.config.StreamQuality
import io.github.mbsantos97.camerasaovivo.player.DecoderBudget
import io.github.mbsantos97.camerasaovivo.player.PlayOptions
import io.github.mbsantos97.camerasaovivo.player.PlayerEvent
import io.github.mbsantos97.camerasaovivo.player.PlayerStats
import io.github.mbsantos97.camerasaovivo.player.ReconnectPolicy
import io.github.mbsantos97.camerasaovivo.player.StreamPlayer
import io.github.mbsantos97.camerasaovivo.player.StreamSource
import io.github.mbsantos97.camerasaovivo.player.Transport
import io.github.mbsantos97.camerasaovivo.player.VideoOutput
import io.github.mbsantos97.camerasaovivo.player.VideoSize
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Roteiro de uma tentativa de reprodução. */
private data class Attempt(
    val frames: Boolean,
    val bytes: Long = 0,
    /** Para de exibir frames depois de N ms (simula travamento). -1 = nunca. */
    val stallAfterMs: Long = -1,
)

/** Player roteirizado: cada play() consome o próximo [Attempt]. */
private class ScriptedPlayer(
    private val scope: TestScope,
    private val script: ArrayDeque<Attempt>,
) : StreamPlayer {
    val plays = mutableListOf<Pair<StreamSource, PlayOptions>>()
    private val _state = MutableStateFlow<PlayerEvent>(PlayerEvent.Idle)
    private val _first = MutableStateFlow(false)
    override val events: SharedFlow<PlayerEvent> = MutableSharedFlow()
    override val state: StateFlow<PlayerEvent> = _state
    override val videoSize: StateFlow<VideoSize?> = MutableStateFlow(null)
    override val firstFrame: StateFlow<Boolean> = _first
    private var current: Attempt? = null
    private var startedAt = 0L

    override fun attach(output: VideoOutput) = Unit

    override fun detach(output: VideoOutput) = Unit

    override fun onOutputSize(
        width: Int,
        height: Int,
    ) = Unit

    override fun play(
        source: StreamSource,
        options: PlayOptions,
    ) {
        plays += source to options
        val a = script.removeFirstOrNull() ?: Attempt(frames = false)
        current = a
        startedAt = scope.testScheduler.currentTime
        _state.value = PlayerEvent.Connecting
        _first.value = false
        if (a.frames) {
            _first.value = true
            _state.value = PlayerEvent.Playing(PlayerStats())
        }
    }

    override fun stop() {
        current = null
        _first.value = false
        _state.value = PlayerEvent.Idle
    }

    override fun release() = Unit

    override fun stats(): PlayerStats {
        val a = current ?: return PlayerStats()
        val elapsed = scope.testScheduler.currentTime - startedAt
        val frames =
            when {
                !a.frames -> 0L
                a.stallAfterMs in 0..elapsed -> a.stallAfterMs / 66
                else -> elapsed / 66 + 1
            }
        return PlayerStats(displayedFrames = frames, inputBytes = a.bytes, hardwareDecoding = true)
    }

    override fun snapshot(): Bitmap? = null
}

private class FakeEnv(
    var resolved: CachedStreams? = null,
) : PlaybackEnv {
    var clock: () -> Long = { 0L }

    override fun now(): Long = clock()

    override val budget = DecoderBudget()
    val learned = mutableListOf<CameraConfig>()
    var resolveCalls = 0

    override fun cachingMs() = 500

    override fun password(cam: CameraConfig) = "segredo"

    override suspend fun resolve(
        cam: CameraConfig,
        password: String,
    ): CachedStreams? {
        resolveCalls++
        return resolved
    }

    override suspend fun learn(
        cameraId: String,
        transform: (CameraConfig) -> CameraConfig,
    ) {
        learned += transform(CameraConfig(id = cameraId, name = "x", host = "h"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SourcePlaybackTest {
    private val streams = CachedStreams(sources = listOf(SourceStreams(mainUri = "rtsp://h/onvif1", subUri = "rtsp://h/onvif2")))
    private val cam = CameraConfig(id = "c", name = "Lâmpada", host = "192.168.0.102", streams = streams)

    private fun TestScope.session(
        vararg attempts: Attempt,
        env: FakeEnv = FakeEnv(),
        camera: CameraConfig = cam,
    ): Triple<SourcePlayback, ScriptedPlayer, FakeEnv> {
        val p = ScriptedPlayer(this, ArrayDeque(attempts.toList()))
        env.clock = { testScheduler.currentTime }
        return Triple(SourcePlayback(camera, 0, p, env, backgroundScope), p, env)
    }

    @Test
    fun `grade usa o substream e fica online no primeiro frame`() =
        runTest {
            val (s, p, _) = session(Attempt(frames = true))
            s.start()
            runCurrent()
            assertEquals("rtsp://h/onvif2", p.plays.single().first.uri)
            assertEquals(ConnectionStatus.ONLINE, s.status.value.status)
            assertTrue(s.status.value.videoVisible)
            assertTrue(p.plays.single().second.hardwareDecoding)
        }

    @Test
    fun `tela cheia usa o principal com audio`() =
        runTest {
            val p = ScriptedPlayer(this, ArrayDeque(listOf(Attempt(frames = true))))
            val s = SourcePlayback(cam, 0, p, FakeEnv(), backgroundScope, quality = { StreamQuality.MAIN }, audio = true)
            s.start()
            runCurrent()
            assertEquals("rtsp://h/onvif1", p.plays.single().first.uri)
            assertTrue(p.plays.single().second.audio)
        }

    @Test
    fun `dados chegando sem video em 6 s troca para decodificacao por software e lembra`() =
        runTest {
            val (s, p, env) = session(Attempt(frames = false, bytes = 50_000), Attempt(frames = true))
            s.start()
            advanceTimeBy(12_001)
            runCurrent()
            assertTrue(env.learned.single().forceSoftwareDecode)
            advanceTimeBy(ReconnectPolicy.delayFor(1) + 10)
            runCurrent()
            assertEquals(false, p.plays[1].second.hardwareDecoding)
            assertEquals(ConnectionStatus.ONLINE, s.status.value.status)
        }

    @Test
    fun `nada chegando no modo Auto tenta TCP e aprende`() =
        runTest {
            val (s, p, env) = session(Attempt(frames = false, bytes = 0), Attempt(frames = true))
            s.start()
            advanceTimeBy(12_001 + ReconnectPolicy.delayFor(1) + 10)
            runCurrent()
            assertEquals(Transport.AUTO, p.plays[0].first.transport)
            assertEquals(Transport.TCP, p.plays[1].first.transport)
            assertEquals(Transport.TCP, env.learned.single().learnedTransport)
        }

    @Test
    fun `falhas seguidas mostram tentativa e respeitam o backoff`() =
        runTest {
            val (s, p, _) = session(env = FakeEnv(), camera = cam.copy(transport = Transport.UDP))
            s.start()
            advanceTimeBy(12_001)
            runCurrent()
            assertEquals(ConnectionStatus.OFFLINE, s.status.value.status)
            assertEquals(1, s.status.value.attempt)
            advanceTimeBy(2_000 + 12_001)
            runCurrent()
            assertEquals(2, s.status.value.attempt)
            assertEquals(2, p.plays.size)
            advanceTimeBy(4_000 - 100)
            runCurrent()
            assertEquals(2, p.plays.size)
        }

    @Test
    fun `watchdog reconecta quando os frames param por 10 s`() =
        runTest {
            val (s, p, _) = session(Attempt(frames = true, stallAfterMs = 4_000), Attempt(frames = true))
            s.start()
            runCurrent()
            advanceTimeBy(4_000 + 10_000 + 2_100)
            runCurrent()
            assertEquals(2, p.plays.size)
            assertEquals(ConnectionStatus.ONLINE, s.status.value.status)
        }

    @Test
    fun `sem streams em cache resolve antes de tocar`() =
        runTest {
            val env = FakeEnv(resolved = streams)
            val (s, p, _) = session(Attempt(frames = true), env = env, camera = cam.copy(streams = null))
            s.start()
            runCurrent()
            assertEquals(1, env.resolveCalls)
            assertEquals("rtsp://h/onvif2", p.plays.single().first.uri)
        }

    @Test
    fun `apos 3 falhas tenta resolver de novo`() =
        runTest {
            val env = FakeEnv(resolved = streams)
            val (s, _, _) = session(env = env, camera = cam.copy(transport = Transport.UDP))
            s.start()
            advanceTimeBy(3 * 12_001 + 2_000 + 4_000 + 8_000 + 100)
            runCurrent()
            assertTrue(env.resolveCalls >= 1)
        }

    @Test
    fun `mudanca irrelevante na configuracao nao reinicia`() =
        runTest {
            val (s, p, _) = session(Attempt(frames = true), Attempt(frames = true))
            s.start()
            runCurrent()
            s.update(cam.copy(name = "Outro nome", hidden = false))
            runCurrent()
            assertEquals(1, p.plays.size)
            s.update(cam.copy(gridQuality = StreamQuality.MAIN))
            runCurrent()
            assertEquals(2, p.plays.size)
            assertEquals("rtsp://h/onvif1", p.plays[1].first.uri)
        }

    @Test
    fun `backoff 2, 4, 8, 16, 30 e maximo`() {
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (1..6).map { ReconnectPolicy.delayFor(it) })
    }

    @Test
    fun `orcamento de decodificadores`() {
        val b = DecoderBudget()
        assertTrue(b.canStart(3))
        assertTrue(!b.canStart(4))
        assertTrue(b.isStalled(0, 10_000))
        assertTrue(!b.isStalled(0, 9_999))
    }
}
