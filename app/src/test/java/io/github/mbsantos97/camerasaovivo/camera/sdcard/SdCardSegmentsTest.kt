package io.github.mbsantos97.camerasaovivo.camera.sdcard

import io.github.mbsantos97.camerasaovivo.onvif.fixtureXml
import io.github.mbsantos97.camerasaovivo.onvif.model.RecordingEvent
import io.github.mbsantos97.camerasaovivo.onvif.parser.SearchParser
import io.github.mbsantos97.camerasaovivo.onvif.parser.XsdTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdCardSegmentsTest {
    private val day = XsdTime.parse("2026-11-19T00:00:00Z")!!
    private val end = day + 86_400_000L

    @Test
    fun `eventos de dados presentes viram intervalos`() {
        val ev = SearchParser.events(fixtureXml("event_search_results"))
        val segs = SdCardPlaybackSource.eventsToSegments(ev, day, end)
        assertEquals(1, segs.size)
        assertEquals(XsdTime.parse("2026-11-19T10:00:00Z"), segs[0].startMillis)
        assertEquals(30 * 60_000L, segs[0].durationMillis)
        assertEquals("REC_1", segs[0].path)
    }

    @Test
    fun `intervalo aberto vai ate o fim do dia`() {
        val segs = SdCardPlaybackSource.eventsToSegments(listOf(RecordingEvent("R", day + 3_600_000, null, true)), day, end)
        assertEquals(end, segs.single().endMillis)
    }

    @Test
    fun `sem eventos usa o intervalo das tracks recortado ao dia`() {
        val rec = SearchParser.recordings(fixtureXml("recording_search_results")).single()
        val segs = SdCardPlaybackSource.tracksToSegments(rec, day, end)
        assertEquals(day, segs.single().startMillis)
        assertEquals(end, segs.single().endMillis)
        assertTrue(SdCardPlaybackSource.tracksToSegments(rec, end + 86_400_000L * 5, end + 86_400_000L * 6).isEmpty())
    }
}
