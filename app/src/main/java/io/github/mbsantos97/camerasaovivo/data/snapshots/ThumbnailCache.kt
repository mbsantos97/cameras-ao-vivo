package io.github.mbsantos97.camerasaovivo.data.snapshots

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Última miniatura de cada câmera (JPEG de 320 px): a grade abre na hora com ela, em vez de um
 * quadrado preto. Carregamento próprio (sem Coil/Glide), decodificado no tamanho exato + LruCache.
 */
class ThumbnailCache(
    private val dir: File,
) {
    private val memory = LruCache<String, ImageBitmap>(16)
    private val _version = MutableStateFlow(0)

    /** Muda quando alguma miniatura é atualizada (a UI observa para recompor). */
    val version: StateFlow<Int> = _version

    private fun file(cameraId: String) = File(dir, "${cameraId.replace(Regex("[^A-Za-z0-9_-]"), "_")}.jpg")

    fun get(cameraId: String): ImageBitmap? = memory.get(cameraId)

    suspend fun load(cameraId: String): ImageBitmap? =
        memory.get(cameraId) ?: withContext(Dispatchers.IO) {
            val f = file(cameraId)
            if (!f.exists()) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            val opts =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, WIDTH)
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
            BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()?.also {
                memory.put(cameraId, it)
                _version.update { v -> v + 1 }
            }
        }

    /** Guarda um frame (reduzido para 320 px de largura). */
    suspend fun save(
        cameraId: String,
        frame: Bitmap,
    ) = withContext(Dispatchers.IO) {
        val h = (frame.height * WIDTH / frame.width.coerceAtLeast(1)).coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(frame, WIDTH, h, true)
        dir.mkdirs()
        file(cameraId).outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        memory.put(cameraId, small.asImageBitmap())
        if (small != frame) frame.recycle()
        _version.update { it + 1 }
    }

    companion object {
        const val WIDTH = 320
        private const val QUALITY = 80

        fun sampleSize(
            width: Int,
            target: Int,
        ): Int {
            var s = 1
            while (width / (s * 2) >= target) s *= 2
            return s
        }
    }
}
