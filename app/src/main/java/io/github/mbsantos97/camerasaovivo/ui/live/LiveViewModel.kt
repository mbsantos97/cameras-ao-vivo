package io.github.mbsantos97.camerasaovivo.ui.live

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mbsantos97.camerasaovivo.camera.LensLayoutDetector
import io.github.mbsantos97.camerasaovivo.camera.PlaybackEnv
import io.github.mbsantos97.camerasaovivo.camera.SourcePlayback
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.storage.StorageLocations
import io.github.mbsantos97.camerasaovivo.core.time.Formats
import io.github.mbsantos97.camerasaovivo.data.config.AppConfig
import io.github.mbsantos97.camerasaovivo.data.config.AppSettings
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.LensMode
import io.github.mbsantos97.camerasaovivo.data.config.StreamQuality
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import io.github.mbsantos97.camerasaovivo.player.RenderMode
import io.github.mbsantos97.camerasaovivo.player.StreamPlayer
import io.github.mbsantos97.camerasaovivo.ui.components.PanelCamera
import io.github.mbsantos97.camerasaovivo.ui.components.PanelInfo
import io.github.mbsantos97.camerasaovivo.ui.components.TileUi
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class FullscreenTarget(
    val sourceId: String,
    val slot: LensSlot,
)

@Immutable
data class LiveUiState(
    val loading: Boolean = true,
    val sources: List<LiveSource> = emptyList(),
    val grid: GridSpec = GridSpec.EMPTY,
    val tiles: Map<String, TileUi> = emptyMap(),
    val fullscreen: FullscreenTarget? = null,
    /** true quando o stream principal da tela cheia já exibiu o primeiro frame (crossfade). */
    val mainReady: Boolean = false,
    val showClock: Boolean = false,
    val panel: PanelInfo = PanelInfo(),
    val renderMode: RenderMode = RenderMode.TEXTURE,
    val layoutMode: GridLayoutMode = GridLayoutMode.AUTO,
    val page: Int = 0,
    val thumbVersion: Int = 0,
    val tourEnabled: Boolean = false,
    val tourSeconds: Int = 15,
    val perfOverlay: Boolean = false,
) {
    val noCameras: Boolean get() = !loading && sources.isEmpty()

    fun source(id: String): LiveSource? = sources.firstOrNull { it.id == id }
}

/**
 * Orquestra a tela Ao Vivo: uma [SourcePlayback] por fonte visível (substream), o stream
 * principal da tela cheia, miniaturas, ciclo de vida e reconexão quando a rede volta.
 */
class LiveViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(LiveUiState())
    val state: StateFlow<LiveUiState> = _state

    private val page = MutableStateFlow(0)
    private val layoutOverride = MutableStateFlow<GridLayoutMode?>(null)
    private val sessions = mutableMapOf<String, SourcePlayback>()
    private val watchers = mutableMapOf<String, Job>()
    private var cameras: Map<String, CameraConfig> = emptyMap()
    private var settings = AppSettings()
    private var started = false
    private var main: SourcePlayback? = null
    private var mainWatch: Job? = null
    private val validated = mutableSetOf<String>()
    private val env: PlaybackEnv = container.playbackEnv { settings }

    /** PTZ, zoom, áudio, presets e barra de controles da tela cheia. */
    val fullscreen = FullscreenFeatures(container, viewModelScope)
    private var tourJob: Job? = null
    private val motionMonitors = mutableMapOf<String, io.github.mbsantos97.camerasaovivo.camera.events.MotionEventMonitor>()
    private val motionClear = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            combine(container.liveConfig, page, layoutOverride) { cfg, p, lo -> Triple(cfg, p, lo) }.collect { (cfg, p, lo) -> apply(cfg, p, lo) }
        }
        viewModelScope.launch { container.thumbnails.version.collect { v -> _state.update { it.copy(thumbVersion = v) } } }
        viewModelScope.launch {
            container.networkMonitor.reconnects.drop(1).collect {
                AppLogger.i(TAG, "Rede voltou; reconectando câmeras")
                sessions.values.filter { it.status.value.status != ConnectionStatus.ONLINE }.forEach { if (it.running) it.restart() }
            }
        }
        viewModelScope.launch {
            // Miniatura a cada 60 s de reprodução.
            while (true) {
                delay(THUMB_INTERVAL_MS)
                saveThumbnails()
            }
        }
    }

    private fun apply(
        cfg: AppConfig,
        p: Int,
        lo: GridLayoutMode?,
    ) {
        settings = cfg.settings
        cameras = cfg.cameras.associateBy { it.id }
        val sources = LiveSources.build(cfg.cameras)
        val mode = lo ?: cfg.settings.layout
        val grid = GridLayoutEngine.compute(sources.map { GridItem(it.id, it.tall) }, mode, p)
        _state.update { st ->
            st.copy(
                loading = false,
                sources = sources,
                grid = grid,
                page = grid.page,
                layoutMode = mode,
                showClock = cfg.settings.showClock,
                renderMode = cfg.settings.renderMode,
                tourEnabled = cfg.settings.tourEnabled,
                tourSeconds = cfg.settings.tourSeconds,
                perfOverlay = cfg.settings.perfOverlay,
                tiles = sources.associate { s -> s.id to tileFor(s, st.tiles[s.id]) },
                fullscreen = st.fullscreen?.takeIf { fs -> sources.any { it.id == fs.sourceId } },
            )
        }
        syncSessions(sources)
        cfg.cameras.forEach { c -> viewModelScope.launch { container.thumbnails.load(c.id) } }
        refreshPanel()
    }

    private fun tileFor(
        s: LiveSource,
        old: TileUi?,
    ): TileUi {
        val cam = cameras[s.cameraId]
        return (old ?: TileUi(name = s.name, status = ConnectionStatus.CONNECTING)).copy(
            name = s.name,
            ptz = s.ptz,
            recording = cam?.recordLocal == true,
            metadata = if (cam?.gridQuality == StreamQuality.MAIN) "HD" else null,
        )
    }

    private fun gridSourceIds(): List<String> =
        _state.value.grid.cells
            .mapNotNull { (it.content as? CellContent.Source)?.id }
            .take(container.decoderBudget.maxStreams)

    private fun syncSessions(sources: List<LiveSource>) {
        val ids = sources.map { it.id }.toSet()
        (sessions.keys - ids).forEach { id ->
            watchers.remove(id)?.cancel()
            sessions.remove(id)?.let {
                it.stop()
                it.player.release()
            }
        }
        sources.forEachIndexed { i, s ->
            val cam = cameras[s.cameraId] ?: return@forEachIndexed
            val existing = sessions[s.id]
            if (existing != null) {
                existing.update(cam)
            } else {
                val player = container.newPlayer(cam, s.name, s.stacked, (i * 67f + 200f) % 360f)
                val session = SourcePlayback(cam, s.sourceIndex, player, env, viewModelScope)
                sessions[s.id] = session
                watchers[s.id] = watch(s, session)
            }
        }
        if (started) applyRunning()
    }

    /** Liga as sessões da página visível (até o limite de decodificadores) e para as demais. */
    private fun applyRunning() {
        val fs = _state.value.fullscreen
        val visible = if (fs != null) setOf(fs.sourceId) else gridSourceIds().toSet()
        sessions.forEach { (id, s) ->
            val keep = id in visible && !(fs != null && _state.value.mainReady && main != null)
            if (keep && started) {
                s.start()
            } else if (!keep && s.running) {
                s.stop()
            }
        }
    }

    private fun watch(
        source: LiveSource,
        session: SourcePlayback,
    ): Job =
        viewModelScope.launch {
            launch {
                session.status.collect { st ->
                    _state.update { s ->
                        val t = s.tiles[source.id] ?: return@update s
                        s.copy(tiles = s.tiles + (source.id to t.copy(status = st.status, attempt = st.attempt, videoVisible = st.videoVisible)))
                    }
                    refreshPanel()
                    if (st.status == ConnectionStatus.ONLINE) onOnline(source)
                }
            }
            launch {
                // Lente dupla detectada pelo tamanho reportado pelo player.
                session.player.videoSize.collect { size ->
                    if (size == null || source.stacked) return@collect
                    val cam = cameras[source.cameraId] ?: return@collect
                    if (cam.lensMode == LensMode.AUTO && LensLayoutDetector.isStacked(size.width, size.height) && !cam.demo) {
                        AppLogger.i(TAG, "${cam.name}: imagem empilhada detectada (${size.width}x${size.height})")
                        markStacked(cam.id, source.sourceIndex)
                    }
                }
            }
        }

    private fun markStacked(
        cameraId: String,
        index: Int,
    ) {
        viewModelScope.launch {
            container.configRepository.updateCamera(cameraId) { c ->
                val st = c.streams ?: return@updateCamera c
                c.copy(streams = st.copy(sources = st.sources.mapIndexed { i, s -> if (i == index) s.copy(stacked = true) else s }))
            }
        }
    }

    /** Depois do primeiro frame: valida ONVIF e capacidades em segundo plano (uma vez por câmera). */
    private fun onOnline(source: LiveSource) {
        val cam = cameras[source.cameraId] ?: return
        if (cam.demo || !validated.add(cam.id)) return
        val age = System.currentTimeMillis() - (cam.streams?.resolvedAtMillis ?: 0)
        if (age < REVALIDATE_MS) return
        viewModelScope.launch(Dispatchers.IO) {
            delay(VALIDATE_DELAY_MS)
            env.resolve(cam, env.password(cam))
        }
    }

    private fun refreshPanel() {
        val recording = cameras.values.any { it.recordLocal }
        _state.update { st ->
            val cams =
                st.sources.distinctBy { it.cameraId }.map { s ->
                    PanelCamera(cameras[s.cameraId]?.name ?: s.name, st.tiles[s.id]?.status ?: ConnectionStatus.CONNECTING)
                }
            val free = if (recording) StorageLocations.preferRemovable(container.appContext)?.freeBytes?.let(Formats::bytes) else null
            st.copy(panel = PanelInfo(cams, free))
        }
    }

    fun playerFor(sourceId: String): StreamPlayer? = sessions[sourceId]?.player

    /** Player do stream principal em tela cheia (null enquanto não existir). */
    fun mainPlayerFor(sourceId: String): StreamPlayer? = main?.takeIf { _state.value.fullscreen?.sourceId == sourceId }?.player

    fun thumbnail(cameraId: String): ImageBitmap? = container.thumbnails.get(cameraId)

    fun openFullscreen(
        sourceId: String,
        slot: LensSlot,
    ) {
        val source = _state.value.source(sourceId) ?: return
        _state.update { it.copy(fullscreen = FullscreenTarget(sourceId, slot), mainReady = false) }
        // Libera decodificadores: só a fonte aberta continua (substream ampliado até o principal chegar).
        sessions.forEach { (id, s) -> if (id != sourceId) s.stop() }
        sessions[sourceId]?.let { if (started) it.start() }
        startMain(source)
        cameras[source.cameraId]?.let { fullscreen.enter(it) }
        armTour()
    }

    private fun startMain(source: LiveSource) {
        stopMain()
        val cam = cameras[source.cameraId] ?: return
        val gridQ = cam.gridQuality
        if (cam.fullQuality == gridQ || !started) return
        val player = container.newPlayer(cam, "${source.name} (principal)", source.stacked, 210f)
        val session = SourcePlayback(cam, source.sourceIndex, player, env, viewModelScope, quality = { it.fullQuality }, audio = true)
        player.setVolume(if (cam.audioDefault) 100 else 0)
        main = session
        session.start()
        mainWatch =
            viewModelScope.launch {
                player.firstFrame.first { it }
                _state.update { it.copy(mainReady = true) }
                // Crossfade de 150 ms e libera o substream.
                delay(CROSSFADE_MS)
                if (main === session) sessions[source.id]?.stop()
            }
    }

    private fun stopMain() {
        mainWatch?.cancel()
        mainWatch = null
        main?.let {
            it.stop()
            it.player.release()
        }
        main = null
    }

    fun exitFullscreen() {
        val fs = _state.value.fullscreen ?: return
        viewModelScope.launch { snapshotThumb(fs.sourceId, preferMain = true) }
        fullscreen.leave()
        tourJob?.cancel()
        stopMain()
        _state.update { it.copy(fullscreen = null, mainReady = false) }
        applyRunning()
    }

    fun nextFullscreen(delta: Int) {
        val st = _state.value
        val fs = st.fullscreen ?: return
        if (st.sources.size < 2) return
        val idx = st.sources.indexOfFirst { it.id == fs.sourceId }
        val next = st.sources[(idx + delta + st.sources.size) % st.sources.size]
        sessions[fs.sourceId]?.stop()
        openFullscreen(next.id, next.defaultSlot())
    }

    fun setFullscreenSlot(slot: LensSlot) {
        _state.update { s -> s.fullscreen?.let { s.copy(fullscreen = it.copy(slot = slot)) } ?: s }
    }

    fun setAudio(on: Boolean) {
        main?.player?.setVolume(if (on) 100 else 0) ?: _state.value.fullscreen?.let { sessions[it.sourceId]?.player?.setVolume(if (on) 100 else 0) }
    }

    fun changePage(delta: Int) {
        val count = _state.value.grid.pageCount
        if (count <= 1) return
        page.value = (page.value + delta + count) % count
    }

    fun cycleLayout() {
        val modes = GridLayoutMode.entries
        layoutOverride.value = modes[(modes.indexOf(_state.value.layoutMode) + 1) % modes.size]
        page.value = 0
    }

    fun reconnect(sourceId: String) {
        sessions[sourceId]?.restart()
    }

    fun toggleQuality(sourceId: String) =
        editCamera(sourceId) {
            it.copy(
                gridQuality =
                    if (it.gridQuality ==
                        StreamQuality.SUB
                    ) {
                        StreamQuality.MAIN
                    } else {
                        StreamQuality.SUB
                    },
            )
        }

    fun cycleLensMode(sourceId: String) = editCamera(sourceId) { it.copy(lensMode = LensMode.entries[(it.lensMode.ordinal + 1) % LensMode.entries.size]) }

    fun hide(sourceId: String) = editCamera(sourceId) { it.copy(hidden = true) }

    fun cameraOf(sourceId: String): CameraConfig? = _state.value.source(sourceId)?.let { cameras[it.cameraId] }

    private fun editCamera(
        sourceId: String,
        t: (CameraConfig) -> CameraConfig,
    ) {
        val cam = cameraOf(sourceId) ?: return
        if (cam.demo) return
        viewModelScope.launch { container.configRepository.updateCamera(cam.id, t) }
    }

    private suspend fun snapshotThumb(
        sourceId: String,
        preferMain: Boolean = false,
    ) {
        val source = _state.value.source(sourceId) ?: return
        val player = (if (preferMain) main?.player else null) ?: sessions[sourceId]?.player ?: return
        if (!player.firstFrame.value) return
        val bmp = withContext(Dispatchers.Main) { runCatching { player.snapshot() }.getOrNull() } ?: return
        runCatching { container.thumbnails.save(source.cameraId, bmp) }
    }

    private suspend fun saveThumbnails() {
        sessions.filter { it.value.status.value.status == ConnectionStatus.ONLINE }.keys.forEach { snapshotThumb(it) }
    }

    fun onStart() {
        started = true
        val fs = _state.value.fullscreen
        applyRunning()
        if (fs != null) _state.value.source(fs.sourceId)?.let { startMain(it) }
    }

    /** Home/standby: para e libera tudo (0% de CPU em segundo plano). */
    fun onStop() {
        fullscreen.ptzStop()
        motionMonitors.values.forEach { it.stop() }
        motionMonitors.clear()
        viewModelScope.launch { saveThumbnails() }
        started = false
        stopMain()
        _state.update { it.copy(mainReady = false) }
        sessions.values.forEach { it.stop() }
    }

    /** Movimento via ONVIF Events para as câmeras visíveis que suportam. */
    private fun syncMotion() {
        val visibleCams =
            if (!started) {
                emptySet()
            } else {
                _state.value.grid.cells
                    .mapNotNull { (it.content as? CellContent.Source)?.id }
                    .mapNotNull { _state.value.source(it)?.cameraId }
                    .toSet()
            }
        (motionMonitors.keys - visibleCams).forEach { motionMonitors.remove(it)?.stop() }
        visibleCams.forEach { id ->
            val cam = cameras[id] ?: return@forEach
            val url = cam.streams?.eventsXAddr
            if (cam.demo || cam.streams?.eventsSupported != true || url == null || id in motionMonitors) return@forEach
            val client = container.onvifFactory(cam, container.configRepository.password(cam))
            motionMonitors[id] =
                io.github.mbsantos97.camerasaovivo.camera.events.MotionEventMonitor(client, url, viewModelScope) { onMotion(id) }.also { it.start() }
        }
    }

    private fun onMotion(cameraId: String) {
        viewModelScope.launch {
            setMotion(cameraId, true)
            motionClear.remove(cameraId)?.cancel()
            motionClear[cameraId] =
                viewModelScope.launch {
                    delay(MOTION_MS)
                    setMotion(cameraId, false)
                }
        }
    }

    private fun setMotion(
        cameraId: String,
        on: Boolean,
    ) {
        _state.update { s ->
            val ids = s.sources.filter { it.cameraId == cameraId }.map { it.id }.toSet()
            s.copy(tiles = s.tiles.mapValues { (k, t) -> if (k in ids) t.copy(motion = on) else t })
        }
    }

    /** Ronda automática: em tela cheia, troca de câmera a cada N s (com a barra escondida). */
    private fun armTour() {
        tourJob?.cancel()
        if (!settings.tourEnabled) return
        tourJob =
            viewModelScope.launch {
                while (true) {
                    delay(settings.tourSeconds * 1_000L)
                    if (_state.value.fullscreen == null) break
                    if (!fullscreen.ui.value.controlsVisible) nextFullscreen(1)
                }
            }
    }

    /** Streams tocando agora (para o painel de desempenho). */
    fun streamsInfo(): List<Pair<String, io.github.mbsantos97.camerasaovivo.player.PlayerStats>> =
        sessions.filter { it.value.running }.mapNotNull { (id, s) -> _state.value.source(id)?.name?.let { it to s.player.stats() } } +
            listOfNotNull(main?.let { "Principal" to it.player.stats() })

    /** Player exibido agora em tela cheia (principal se pronto, senão o substream). */
    fun activePlayer(): StreamPlayer? {
        val fs = _state.value.fullscreen ?: return null
        return main?.player?.takeIf { _state.value.mainReady } ?: sessions[fs.sourceId]?.player
    }

    fun activeStats() = activePlayer()?.stats()

    fun toggleAudio() = fullscreen.setAudio(!fullscreen.ui.value.audio, main?.player ?: activePlayer())

    /** Foto do que está na tela (TextureView.getBitmap). */
    suspend fun takeSnapshot(): java.io.File? {
        val fs = _state.value.fullscreen ?: return null
        val name = _state.value.source(fs.sourceId)?.name ?: "camera"
        val bmp = withContext(Dispatchers.Main) { runCatching { activePlayer()?.snapshot() }.getOrNull() } ?: return null
        return runCatching { container.snapshots.save(name, bmp) }.getOrNull()
    }

    override fun onCleared() {
        fullscreen.leave()
        stopMain()
        sessions.values.forEach {
            it.stop()
            it.player.release()
        }
        sessions.clear()
    }

    private companion object {
        const val TAG = "Live"
        const val THUMB_INTERVAL_MS = 60_000L
        const val CROSSFADE_MS = 150L
        const val VALIDATE_DELAY_MS = 3_000L
        const val REVALIDATE_MS = 12 * 3_600_000L
        const val MOTION_MS = 10_000L
    }
}
