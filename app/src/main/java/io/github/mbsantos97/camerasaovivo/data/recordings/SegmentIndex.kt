package io.github.mbsantos97.camerasaovivo.data.recordings

import androidx.compose.runtime.Immutable
import io.github.mbsantos97.camerasaovivo.core.time.Formats
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TimelineSegment
import java.util.Calendar
import java.util.TimeZone

/** Trecho gravado (arquivo local ou intervalo do cartão SD), em epoch ms. */
@Immutable
data class Segment(
    val cameraId: String,
    val startMillis: Long,
    val endMillis: Long,
    val path: String? = null,
    val sizeBytes: Long = 0,
) {
    val durationMillis: Long get() = endMillis - startMillis

    fun contains(t: Long) = t in startMillis until endMillis
}

data class FileInfo(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/** Nomes dos segmentos: CAMID_YYYYMMDD_HHMMSS.ts (hora local do aparelho). */
object SegmentNames {
    private val re = Regex("""^(.+)_(\d{8}_\d{6})\.ts$""")

    fun name(
        cameraId: String,
        startMillis: Long,
    ): String = "${safeId(cameraId)}_${Formats.fileStamp(startMillis)}.ts"

    fun safeId(cameraId: String) = cameraId.replace(Regex("[^A-Za-z0-9-]"), "-")

    fun parse(fileName: String): Pair<String, Long>? {
        val m = re.matchEntire(fileName) ?: return null
        val t = Formats.parseFileStamp(m.groupValues[2]) ?: return null
        return m.groupValues[1] to t
    }
}

object SegmentIndex {
    /**
     * Monta a lista de segmentos. Fim = o menor entre a última modificação do arquivo,
     * o início do próximo segmento e início + duração máxima (com folga).
     */
    fun build(
        files: List<FileInfo>,
        maxSegmentMillis: Long,
    ): List<Segment> {
        val parsed =
            files
                .mapNotNull { f -> SegmentNames.parse(f.name)?.let { (id, start) -> Triple(id, start, f) } }
                .sortedWith(compareBy({ it.first }, { it.second }))
        return parsed.mapIndexed { i, (id, start, f) ->
            val next = parsed.getOrNull(i + 1)?.takeIf { it.first == id }?.second
            var end = minOf(f.lastModified, start + maxSegmentMillis + GRACE_MS)
            if (next != null) end = minOf(end, next)
            Segment(id, start, end.coerceAtLeast(start + 1), f.path, f.sizeBytes)
        }
    }

    /** Início do dia local (meia-noite) de [millis]. */
    fun dayStart(
        millis: Long,
        tz: TimeZone = TimeZone.getDefault(),
    ): Long =
        Calendar
            .getInstance(tz)
            .apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

    fun nextDayStart(
        dayStart: Long,
        tz: TimeZone = TimeZone.getDefault(),
    ): Long = Calendar.getInstance(tz).apply { timeInMillis = dayStart + 36 * 3_600_000L }.let { dayStart(it.timeInMillis, tz) }

    /** Dias (início local) que têm alguma gravação. */
    fun days(
        segments: List<Segment>,
        tz: TimeZone = TimeZone.getDefault(),
    ): Set<Long> {
        val out = sortedSetOf<Long>()
        for (s in segments) {
            var d = dayStart(s.startMillis, tz)
            while (d < s.endMillis) {
                out += d
                d = nextDayStart(d, tz)
            }
        }
        return out
    }

    /** Trechos do dia em segundos desde a meia-noite, já recortados e unidos. */
    fun timeline(
        segments: List<Segment>,
        dayStart: Long,
        tz: TimeZone = TimeZone.getDefault(),
    ): List<TimelineSegment> {
        val dayEnd = nextDayStart(dayStart, tz)
        val raw =
            segments
                .filter { it.endMillis > dayStart && it.startMillis < dayEnd }
                .map { s ->
                    val a = ((maxOf(s.startMillis, dayStart) - dayStart) / 1000).toInt()
                    val b = ((minOf(s.endMillis, dayEnd) - dayStart) / 1000).toInt()
                    a to b
                }.sortedBy { it.first }
        val merged = mutableListOf<TimelineSegment>()
        for ((a, b) in raw) {
            val last = merged.lastOrNull()
            if (last != null && a <= last.endSecond + MERGE_GAP_S) {
                merged[merged.lastIndex] = last.copy(endSecond = maxOf(last.endSecond, b))
            } else {
                merged += TimelineSegment(a, b)
            }
        }
        return merged
    }

    /** Segmento que contém [t]; se cair num buraco, o próximo depois de [t]. */
    fun find(
        segments: List<Segment>,
        t: Long,
    ): Segment? = segments.firstOrNull { it.contains(t) } ?: segments.filter { it.startMillis > t }.minByOrNull { it.startMillis }

    private const val GRACE_MS = 60_000L
    private const val MERGE_GAP_S = 5
}

data class StoredFile(
    val path: String,
    val sizeBytes: Long,
    val startMillis: Long,
)

/**
 * Retenção: apaga os segmentos mais antigos até o uso do volume ficar em [maxPercent]% e,
 * na memória interna, sobrar pelo menos [minFreeBytes].
 */
object RetentionPolicy {
    fun selectForDeletion(
        files: List<StoredFile>,
        totalBytes: Long,
        freeBytes: Long,
        maxPercent: Int,
        minFreeBytes: Long,
        internal: Boolean,
    ): List<StoredFile> {
        if (totalBytes <= 0) return emptyList()
        val maxUsed = totalBytes * maxPercent.coerceIn(1, 100) / 100
        var free = freeBytes
        val out = mutableListOf<StoredFile>()
        for (f in files.sortedBy { it.startMillis }) {
            val used = totalBytes - free
            val needPercent = used > maxUsed
            val needFree = internal && free < minFreeBytes
            if (!needPercent && !needFree) break
            out += f
            free += f.sizeBytes
        }
        return out
    }
}
