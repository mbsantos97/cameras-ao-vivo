package io.github.mbsantos97.camerasaovivo.data.recordings

import android.content.Context
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.storage.StorageLocations
import io.github.mbsantos97.camerasaovivo.core.storage.StorageVolume
import io.github.mbsantos97.camerasaovivo.data.config.RecordingDestination
import io.github.mbsantos97.camerasaovivo.data.config.RecordingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gravações feitas na TV Box: segmentos MPEG-TS em <volume>/recordings/<câmera>/.
 * Procura em todos os volumes (pendrive e interno), para achar gravações antigas mesmo se o destino mudou.
 */
class LocalRecordingRepository(
    private val context: Context,
) {
    fun volumeFor(settings: RecordingSettings): StorageVolume? {
        val vols = StorageLocations.volumes(context)
        return when (settings.destination) {
            RecordingDestination.AUTO_USB -> vols.firstOrNull { it.removable } ?: vols.firstOrNull()
            RecordingDestination.INTERNAL -> vols.firstOrNull { !it.removable } ?: StorageLocations.internal(context)
        }
    }

    fun dirFor(
        volume: StorageVolume,
        cameraId: String,
    ): File = File(volume.dir, "recordings/${SegmentNames.safeId(cameraId)}").apply { mkdirs() }

    private fun allDirs(cameraId: String): List<File> =
        (StorageLocations.volumes(context) + StorageLocations.internal(context))
            .map { File(it.dir, "recordings/${SegmentNames.safeId(cameraId)}") }
            .filter { it.isDirectory }

    suspend fun segments(
        cameraId: String,
        settings: RecordingSettings,
    ): List<Segment> =
        withContext(Dispatchers.IO) {
            val files =
                allDirs(cameraId).flatMap { d ->
                    d.listFiles { f -> f.name.endsWith(".ts") }.orEmpty().map { FileInfo(it.path, it.name, it.length(), it.lastModified()) }
                }
            SegmentIndex.build(files, settings.segmentMinutes * 60_000L).filter { it.cameraId == SegmentNames.safeId(cameraId) }
        }

    /** Aplica a retenção no volume de gravação (apaga só segmentos do app, os mais antigos primeiro). */
    suspend fun enforceRetention(settings: RecordingSettings) =
        withContext(Dispatchers.IO) {
            val vol = volumeFor(settings) ?: return@withContext
            val root = File(vol.dir, "recordings")
            val files =
                root
                    .walkTopDown()
                    .filter { it.isFile && it.name.endsWith(".ts") }
                    .mapNotNull { f -> SegmentNames.parse(f.name)?.let { StoredFile(f.path, f.length(), it.second) } }
                    .toList()
            val del =
                RetentionPolicy.selectForDeletion(
                    files = files,
                    totalBytes = vol.totalBytes,
                    freeBytes = vol.freeBytes,
                    maxPercent = settings.maxPercent,
                    minFreeBytes = settings.minFreeBytesInternal,
                    internal = !vol.removable,
                )
            del.forEach { File(it.path).delete() }
            if (del.isNotEmpty()) AppLogger.i("Retention", "${del.size} segmento(s) antigo(s) apagado(s) em ${vol.label}")
        }
}
