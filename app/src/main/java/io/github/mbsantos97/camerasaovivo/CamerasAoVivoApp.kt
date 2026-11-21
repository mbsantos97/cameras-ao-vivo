package io.github.mbsantos97.camerasaovivo

import android.app.Application
import android.os.StrictMode
import io.github.mbsantos97.camerasaovivo.di.AppContainer

class CamerasAoVivoApp : Application() {
    /** Só o essencial aqui: o contêiner é preguiçoso e não toca em rede nem em LibVLC. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy
                    .Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy
                    .Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build(),
            )
        }
        io.github.mbsantos97.camerasaovivo.core.logging.AppLogger.init(java.io.File(filesDir, "logs"))
        container = AppContainer(this)
        container.applyFirstRunDefaults()
        container.watchRecorder()
    }
}
