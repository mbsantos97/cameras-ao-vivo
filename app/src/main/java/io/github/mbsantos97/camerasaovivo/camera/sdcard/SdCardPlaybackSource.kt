package io.github.mbsantos97.camerasaovivo.camera.sdcard

import io.github.mbsantos97.camerasaovivo.core.network.IpUtils
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.recordings.Segment
import io.github.mbsantos97.camerasaovivo.onvif.OnvifClient
import io.github.mbsantos97.camerasaovivo.onvif.model.RecordingEvent
import io.github.mbsantos97.camerasaovivo.onvif.model.RecordingInfo
import io.github.mbsantos97.camerasaovivo.onvif.model.ServiceAddresses
import io.github.mbsantos97.camerasaovivo.onvif.parser.ReplayParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.SearchParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.XsdTime
import io.github.mbsantos97.camerasaovivo.onvif.soap.Requests
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/**
 * Gravações do cartão SD via ONVIF Profile G (Search + Replay), quando a câmera oferece.
 * Câmeras Yoosee normalmente não oferecem: aí a UI explica e sugere a gravação na TV Box.
 */
class SdCardPlaybackSource(
    private val client: OnvifClient,
    private val cam: CameraConfig,
) {
    private val dayCache = ConcurrentHashMap<Long, List<Segment>>()
    private var recordingsCache: List<RecordingInfo>? = null

    init {
        val s = cam.streams
        client.useCachedServices(
            ServiceAddresses(media = s?.mediaXAddr, ptz = s?.ptzXAddr, search = s?.searchXAddr, replay = s?.replayXAddr, recording = s?.recordingXAddr),
        )
    }

    private val searchUrl: String get() = client.services.search ?: client.deviceUrl
    private val replayUrl: String get() = client.services.replay ?: client.deviceUrl

    /** Gravações e o intervalo coberto por cada uma (GetRecordingSummary → FindRecordings → resultados). */
    suspend fun recordings(): List<RecordingInfo> {
        recordingsCache?.let { return it }
        if (!client.clockSynced) runCatching { client.syncClock() }
        runCatching { SearchParser.summary(client.authed(searchUrl, Requests.getRecordingSummary())) }
        val token = SearchParser.searchToken(client.authed(searchUrl, Requests.findRecordings())) ?: return emptyList()
        val out = mutableListOf<RecordingInfo>()
        repeat(MAX_POLLS) {
            val r = client.authed(searchUrl, Requests.getRecordingSearchResults(token))
            out += SearchParser.recordings(r)
            if (SearchParser.searchState(r)?.equals("Completed", true) != false) return out.distinctBy { it.token }.also { recordingsCache = it }
            delay(POLL_MS)
        }
        return out.distinctBy { it.token }.also { recordingsCache = it }
    }

    /** Trechos gravados no dia: eventos de histórico (FindEvents) ou, sem eles, o intervalo das tracks. */
    suspend fun segmentsForDay(
        dayStart: Long,
        dayEnd: Long,
    ): List<Segment> {
        dayCache[dayStart]?.let { return it }
        val recs = recordings()
        val result =
            recs.flatMap { rec ->
                val events = runCatching { findEvents(rec.token, dayStart, dayEnd) }.getOrDefault(emptyList())
                val fromEvents = eventsToSegments(events, dayStart, dayEnd)
                fromEvents.ifEmpty { tracksToSegments(rec, dayStart, dayEnd) }
            }
        dayCache[dayStart] = result
        return result
    }

    private suspend fun findEvents(
        token: String,
        from: Long,
        to: Long,
    ): List<RecordingEvent> {
        val search =
            SearchParser.searchToken(client.authed(searchUrl, Requests.findEvents(XsdTime.format(from), XsdTime.format(to), token))) ?: return emptyList()
        val out = mutableListOf<RecordingEvent>()
        repeat(MAX_POLLS) {
            val r = client.authed(searchUrl, Requests.getEventSearchResults(search))
            out += SearchParser.events(r)
            if (SearchParser.searchState(r)?.equals("Completed", true) != false) return out
            delay(POLL_MS)
        }
        return out
    }

    /** URI RTSP de replay (host:porta reescritos). */
    suspend fun replayUri(recordingToken: String): String? =
        ReplayParser.uri(client.authed(replayUrl, Requests.getReplayUri(recordingToken)))?.let { IpUtils.rewriteHostPort(it, cam.host, cam.rtspPort) }

    companion object {
        private const val MAX_POLLS = 10
        private const val POLL_MS = 500L

        /** Eventos "dados presentes = true/false" viram intervalos. */
        fun eventsToSegments(
            events: List<RecordingEvent>,
            dayStart: Long,
            dayEnd: Long,
        ): List<Segment> {
            val out = mutableListOf<Segment>()
            var openAt: Long? = null
            var token: String? = null
            for (e in events.filter { it.timeMillis != null }.sortedBy { it.timeMillis }) {
                if (e.isStart == true && openAt == null) {
                    openAt = e.timeMillis
                    token = e.recordingToken
                } else if (e.isStart == false && openAt != null) {
                    out += Segment(token ?: "", maxOf(openAt, dayStart), minOf(e.timeMillis!!, dayEnd), path = token)
                    openAt = null
                }
            }
            if (openAt != null) out += Segment(token ?: "", maxOf(openAt, dayStart), dayEnd, path = token)
            return out.filter { it.endMillis > it.startMillis }
        }

        fun tracksToSegments(
            rec: RecordingInfo,
            dayStart: Long,
            dayEnd: Long,
        ): List<Segment> {
            val from = rec.tracks.mapNotNull { it.fromMillis }.minOrNull() ?: rec.earliestMillis ?: return emptyList()
            val to = rec.tracks.mapNotNull { it.toMillis }.maxOrNull() ?: rec.latestMillis ?: return emptyList()
            val a = maxOf(from, dayStart)
            val b = minOf(to, dayEnd)
            return if (b > a) listOf(Segment(rec.token, a, b, path = rec.token)) else emptyList()
        }
    }
}
