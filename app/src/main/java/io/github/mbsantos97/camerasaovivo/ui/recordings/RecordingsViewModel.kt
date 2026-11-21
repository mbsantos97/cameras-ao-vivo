package io.github.mbsantos97.camerasaovivo.ui.recordings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mbsantos97.camerasaovivo.camera.sdcard.SdCardPlaybackSource
import io.github.mbsantos97.camerasaovivo.core.time.Formats
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.recordings.Segment
import io.github.mbsantos97.camerasaovivo.data.recordings.SegmentIndex
import io.github.mbsantos97.camerasaovivo.data.snapshots.Snapshot
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import io.github.mbsantos97.camerasaovivo.ui.designsystem.DayItem
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TimelineSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RecordingsTab { SD, LOCAL, PHOTOS }

@Immutable
data class RecordingsState(
    val cameras: List<CameraConfig> = emptyList(),
    val cameraId: String? = null,
    val tab: RecordingsTab = RecordingsTab.LOCAL,
    val days: List<DayItem> = emptyList(),
    val selectedDay: Long? = null,
    val timeline: List<TimelineSegment> = emptyList(),
    val segments: List<Segment> = emptyList(),
    val loading: Boolean = false,
    val sdSupported: Boolean = false,
    val error: String? = null,
    val photos: List<Snapshot> = emptyList(),
) {
    val camera: CameraConfig? get() = cameras.firstOrNull { it.id == cameraId }
}

class RecordingsViewModel(
    private val container: AppContainer,
    initialCameraId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(RecordingsState())
    val state: StateFlow<RecordingsState> = _state
    private var sd: SdCardPlaybackSource? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            val cfg = container.configRepository.config.first()
            val cams = cfg.cameras.filter { !it.demo }.sortedBy { it.order }
            val cam = cams.firstOrNull { it.id == initialCameraId } ?: cams.firstOrNull()
            val tab = if (cam?.streams?.profileG == true) RecordingsTab.SD else RecordingsTab.LOCAL
            _state.value = RecordingsState(cameras = cams, cameraId = cam?.id, tab = tab, sdSupported = cam?.streams?.profileG == true)
            reload()
        }
    }

    fun selectCamera(id: String) {
        val cam = _state.value.cameras.firstOrNull { it.id == id } ?: return
        sd = null
        _state.update { it.copy(cameraId = id, sdSupported = cam.streams?.profileG == true, selectedDay = null) }
        reload()
    }

    fun selectTab(tab: RecordingsTab) {
        _state.update { it.copy(tab = tab, selectedDay = null) }
        reload()
    }

    fun selectDay(day: Long) {
        _state.update { it.copy(selectedDay = day) }
        loadDay(day)
    }

    private fun reload() {
        loadJob?.cancel()
        val st = _state.value
        loadJob =
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null, timeline = emptyList(), segments = emptyList()) }
                when (st.tab) {
                    RecordingsTab.PHOTOS -> _state.update { it.copy(photos = container.snapshots.list(), loading = false) }
                    RecordingsTab.LOCAL -> loadDays(localSegments())
                    RecordingsTab.SD -> loadSdDays()
                }
            }
    }

    private suspend fun localSegments(): List<Segment> {
        val cam = _state.value.camera ?: return emptyList()
        val settings = container.configRepository.current().settings.recording
        return container.recordings.segments(cam.id, settings)
    }

    private fun lastDays(withRecordings: Set<Long>): List<DayItem> {
        val today = SegmentIndex.dayStart(System.currentTimeMillis())
        val out = mutableListOf<DayItem>()
        var d = today
        repeat(DAYS) {
            out += DayItem(d, Formats.dayMonth(d), if (d == today) "Hoje" else Formats.weekdayShort(d), d in withRecordings)
            d = SegmentIndex.dayStart(d - HOURS_12)
        }
        return out
    }

    private suspend fun loadDays(segments: List<Segment>) {
        val days = SegmentIndex.days(segments)
        val items = lastDays(days)
        val selected = items.firstOrNull { it.hasRecordings }?.epochDay ?: items.first().epochDay
        _state.update { it.copy(days = items, selectedDay = selected, segments = segments, loading = false) }
        _state.update { it.copy(timeline = SegmentIndex.timeline(segments, selected)) }
    }

    private suspend fun loadSdDays() {
        val cam = _state.value.camera
        if (cam == null || cam.streams?.profileG != true) {
            _state.update { it.copy(loading = false) }
            return
        }
        try {
            val src = sd ?: SdCardPlaybackSource(container.onvifFactory(cam, container.configRepository.password(cam)), cam).also { sd = it }
            val recs = withContext(Dispatchers.IO) { src.recordings() }
            val spans =
                recs.mapNotNull { r ->
                    (r.earliestMillis ?: r.tracks.mapNotNull { it.fromMillis }.minOrNull())?.let { a ->
                        a to
                            (r.latestMillis ?: System.currentTimeMillis())
                    }
                }
            val ranges = spans.map { (a, b) -> Segment(cam.id, a, b) }
            val days = SegmentIndex.days(ranges)
            val items = lastDays(days)
            val selected = items.firstOrNull { it.hasRecordings }?.epochDay ?: items.first().epochDay
            _state.update { it.copy(days = items, selectedDay = selected, loading = false) }
            loadDay(selected)
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = "Não foi possível ler o cartão SD: ${e.message}") }
        }
    }

    private fun loadDay(day: Long) {
        val st = _state.value
        when (st.tab) {
            RecordingsTab.LOCAL -> _state.update { it.copy(timeline = SegmentIndex.timeline(it.segments, day)) }
            RecordingsTab.SD ->
                viewModelScope.launch {
                    val src = sd ?: return@launch
                    _state.update { it.copy(loading = true) }
                    val segs = runCatching { withContext(Dispatchers.IO) { src.segmentsForDay(day, SegmentIndex.nextDayStart(day)) } }.getOrDefault(emptyList())
                    _state.update { it.copy(segments = segs, timeline = SegmentIndex.timeline(segs, day), loading = false) }
                }
            RecordingsTab.PHOTOS -> Unit
        }
    }

    fun enableLocalRecording() {
        val cam = _state.value.camera ?: return
        viewModelScope.launch { container.configRepository.updateCamera(cam.id) { it.copy(recordLocal = true) } }
    }

    fun deletePhoto(s: Snapshot) {
        viewModelScope.launch {
            container.snapshots.delete(s)
            _state.update { it.copy(photos = container.snapshots.list()) }
        }
    }

    private companion object {
        const val DAYS = 30
        const val HOURS_12 = 12 * 3_600_000L
    }
}
