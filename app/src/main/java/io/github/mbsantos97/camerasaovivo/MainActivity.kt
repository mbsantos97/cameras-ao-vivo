package io.github.mbsantos97.camerasaovivo

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.LocalToast
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ToastState
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TvToastHost
import io.github.mbsantos97.camerasaovivo.ui.navigation.AppNavHost
import io.github.mbsantos97.camerasaovivo.ui.navigation.LocalAppContainer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Telas de vídeo: o protetor de tela não deve entrar.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val container = (application as CamerasAoVivoApp).container
        setContent {
            AoVivoTheme {
                val toast = remember { ToastState() }
                CompositionLocalProvider(LocalToast provides toast, LocalAppContainer provides container) {
                    Box(Modifier.fillMaxSize().background(AoVivoTheme.colors.bg)) {
                        AppNavHost(container)
                        TvToastHost(toast)
                    }
                }
            }
        }
    }
}
