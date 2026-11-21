package io.github.mbsantos97.camerasaovivo.core.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

/** Um volume onde o app pode gravar sem permissões (getExternalFilesDirs). */
data class StorageVolume(
    val dir: File,
    val removable: Boolean,
) {
    val freeBytes: Long get() = runCatching { StatFs(dir.path).availableBytes }.getOrDefault(0L)
    val totalBytes: Long get() = runCatching { StatFs(dir.path).totalBytes }.getOrDefault(0L)
    val label: String get() = if (removable) "Pendrive / cartão" else "Memória interna"
}

object StorageLocations {
    fun volumes(context: Context): List<StorageVolume> =
        context
            .getExternalFilesDirs(null)
            .filterNotNull()
            .mapNotNull { dir ->
                val state = runCatching { Environment.getExternalStorageState(dir) }.getOrNull()
                if (state != Environment.MEDIA_MOUNTED) return@mapNotNull null
                val removable = runCatching { Environment.isExternalStorageRemovable(dir) }.getOrDefault(false)
                StorageVolume(dir, removable)
            }

    /** Pendrive se houver; senão a memória interna (área externa do app). */
    fun preferRemovable(context: Context): StorageVolume? = volumes(context).let { v -> v.firstOrNull { it.removable } ?: v.firstOrNull() }

    fun internal(context: Context): StorageVolume = StorageVolume(context.filesDir, removable = false)
}
