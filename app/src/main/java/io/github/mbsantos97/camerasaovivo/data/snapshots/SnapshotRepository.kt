package io.github.mbsantos97.camerasaovivo.data.snapshots

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.runtime.Immutable
import io.github.mbsantos97.camerasaovivo.core.time.Formats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class Snapshot(
    val file: File,
    val cameraName: String,
    val takenAtMillis: Long,
)

/** Fotos tiradas pelo botão "Foto" (JPEG local, na área do app; sem permissões). */
class SnapshotRepository(
    private val context: Context,
) {
    private val dir: File get() = (context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "photos")).apply { mkdirs() }

    suspend fun save(
        cameraName: String,
        bitmap: Bitmap,
    ): File =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val safe = cameraName.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifEmpty { "camera" }
            val f = File(dir, "${safe}_${Formats.fileStamp(now)}.jpg")
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            f
        }

    suspend fun list(): List<Snapshot> =
        withContext(Dispatchers.IO) {
            dir
                .listFiles { f -> f.extension.equals("jpg", true) }
                .orEmpty()
                .map { f ->
                    val stamp = f.nameWithoutExtension.takeLast(STAMP_LEN)
                    Snapshot(f, f.nameWithoutExtension.dropLast(STAMP_LEN + 1).replace('-', ' '), Formats.parseFileStamp(stamp) ?: f.lastModified())
                }.sortedByDescending { it.takenAtMillis }
        }

    suspend fun delete(s: Snapshot) = withContext(Dispatchers.IO) { s.file.delete() }

    private companion object {
        const val QUALITY = 90
        const val STAMP_LEN = 15
    }
}
