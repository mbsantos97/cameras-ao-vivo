package io.github.mbsantos97.camerasaovivo.player

import android.content.Context
import io.github.mbsantos97.camerasaovivo.BuildConfig
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import org.videolan.libvlc.LibVLC

/**
 * Instância única do LibVLC, criada sob demanda e fora da main thread (nunca no Application.onCreate).
 */
class LibVlcProvider(
    private val context: Context,
) {
    @Volatile private var instance: LibVLC? = null

    fun get(): LibVLC =
        instance ?: synchronized(this) {
            instance ?: create().also { instance = it }
        }

    private fun create(): LibVLC {
        val t0 = System.currentTimeMillis()
        val options =
            arrayListOf(
                "--drop-late-frames",
                "--skip-frames",
                "--avcodec-skiploopfilter=1",
                "--rtsp-frame-buffer-size=1000000",
                "--no-sub-autodetect-file",
                "--no-spu",
                "--no-osd",
                "--stats",
                "--audio-time-stretch",
                if (BuildConfig.DEBUG) "-vv" else "--verbose=0",
            )
        val vlc = LibVLC(context.applicationContext, options)
        AppLogger.i("LibVLC", "Inicializado em ${System.currentTimeMillis() - t0} ms")
        return vlc
    }
}
