package io.github.mbsantos97.camerasaovivo.data.recordings

import io.github.mbsantos97.camerasaovivo.core.time.Formats
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TimelineSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class RecordingsTest {
    private val tz = TimeZone.getDefault()
    private val day = Formats.parseFileStamp("20261120_000000")!!
    private val min = 60_000L

    private fun file(
        cam: String,
        stamp: String,
        minutes: Long,
        size: Long = 100,
    ): FileInfo {
        val start = Formats.parseFileStamp(stamp)!!
        return FileInfo("/r/${cam}_$stamp.ts", "${cam}_$stamp.ts", size, start + minutes * min)
    }

    @Test
    fun `nome do segmento ida e volta`() {
        val n = SegmentNames.name("abc-123", day + 3_723_000)
        assertEquals("abc-123_20261120_010203.ts", n)
        assertEquals("abc-123" to day + 3_723_000, SegmentNames.parse(n))
        assertNull(SegmentNames.parse("lixo.ts"))
        assertNull(SegmentNames.parse("cam_20261320_000000.ts"))
    }

    @Test
    fun `id com caracteres especiais vira seguro`() {
        assertEquals("a-b-c", SegmentNames.safeId("a#b c"))
    }

    @Test
    fun `segmentos usam o inicio do proximo como fim`() {
        val segs =
            SegmentIndex.build(
                listOf(file("cam", "20261120_100000", 10), file("cam", "20261120_100800", 10), file("cam", "20261120_120000", 3)),
                maxSegmentMillis = 10 * min,
            )
        assertEquals(3, segs.size)
        assertEquals(8 * min, segs[0].durationMillis)
        assertEquals(10 * min, segs[1].durationMillis)
        assertEquals(3 * min, segs[2].durationMillis)
    }

    @Test
    fun `cameras diferentes nao se misturam`() {
        val segs = SegmentIndex.build(listOf(file("a", "20261120_100000", 10), file("b", "20261120_100500", 10)), 10 * min)
        assertEquals(10 * min, segs.first { it.cameraId == "a" }.durationMillis)
    }

    @Test
    fun `linha do tempo une trechos colados e recorta o dia`() {
        val segs =
            SegmentIndex.build(
                listOf(
                    file("cam", "20261119_235500", 10),
                    file("cam", "20261120_100000", 10),
                    file("cam", "20261120_101000", 10),
                ),
                10 * min,
            )
        val t = SegmentIndex.timeline(segs, day, tz)
        assertEquals(listOf(TimelineSegment(0, 300), TimelineSegment(36_000, 37_200)), t)
    }

    @Test
    fun `dias com gravacao incluem a virada da meia-noite`() {
        val segs = SegmentIndex.build(listOf(file("cam", "20261119_235500", 10)), 10 * min)
        val days = SegmentIndex.days(segs, tz)
        assertEquals(2, days.size)
        assertTrue(day in days)
    }

    @Test
    fun `busca no segmento ou no proximo depois de um buraco`() {
        val segs = SegmentIndex.build(listOf(file("cam", "20261120_100000", 10), file("cam", "20261120_120000", 10)), 10 * min)
        assertEquals(segs[0], SegmentIndex.find(segs, day + 10 * 3_600_000L + 5 * min))
        assertEquals(segs[1], SegmentIndex.find(segs, day + 11 * 3_600_000L))
        assertNull(SegmentIndex.find(segs, day + 13 * 3_600_000L))
    }

    @Test
    fun `retencao apaga os mais antigos ate 80 por cento`() {
        val files = (0 until 10).map { StoredFile("/f$it", 10, it.toLong()) }
        // Volume de 100, 15 livres (85% usado): precisa liberar 5 → 1 arquivo de 10.
        val del = RetentionPolicy.selectForDeletion(files, totalBytes = 100, freeBytes = 15, maxPercent = 80, minFreeBytes = 0, internal = false)
        assertEquals(listOf("/f0"), del.map { it.path })
    }

    @Test
    fun `memoria interna sempre deixa 1 GB livre`() {
        val gb = 1_073_741_824L
        val files = (0 until 5).map { StoredFile("/f$it", gb / 2, it.toLong()) }
        val del = RetentionPolicy.selectForDeletion(files, totalBytes = 8 * gb, freeBytes = gb / 4, maxPercent = 100, minFreeBytes = gb, internal = true)
        assertEquals(listOf("/f0", "/f1"), del.map { it.path })
    }

    @Test
    fun `nada a apagar quando ha espaco`() {
        assertTrue(RetentionPolicy.selectForDeletion(listOf(StoredFile("/a", 1, 0)), 100, 50, 80, 0, false).isEmpty())
    }
}
