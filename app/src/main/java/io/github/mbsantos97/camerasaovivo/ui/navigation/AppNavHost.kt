package io.github.mbsantos97.camerasaovivo.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import io.github.mbsantos97.camerasaovivo.remoteconfig.PhoneConfigScreen
import io.github.mbsantos97.camerasaovivo.ui.catalog.DesignCatalogScreen
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.diagnostics.DiagnosticsScreen
import io.github.mbsantos97.camerasaovivo.ui.diagnostics.DiagnosticsViewModel
import io.github.mbsantos97.camerasaovivo.ui.live.LiveScreen
import io.github.mbsantos97.camerasaovivo.ui.live.LiveViewModel
import io.github.mbsantos97.camerasaovivo.ui.recordings.PlaybackOrigin
import io.github.mbsantos97.camerasaovivo.ui.recordings.PlaybackScreen
import io.github.mbsantos97.camerasaovivo.ui.recordings.PlaybackViewModel
import io.github.mbsantos97.camerasaovivo.ui.recordings.RecordingsScreen
import io.github.mbsantos97.camerasaovivo.ui.recordings.RecordingsViewModel
import io.github.mbsantos97.camerasaovivo.ui.settings.DiscoveryViewModel
import io.github.mbsantos97.camerasaovivo.ui.setup.FirstRunWizard
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer não fornecido") }

object Routes {
    @Serializable data object Live

    @Serializable data class Recordings(
        val cameraId: String? = null,
    )

    @Serializable data class Playback(
        val cameraId: String,
        val origin: String,
        val startMillis: Long,
    )

    @Serializable data object Settings

    @Serializable data object CameraList

    @Serializable data class CameraEdit(
        val cameraId: String? = null,
        val host: String? = null,
    )

    @Serializable data object Discovery

    @Serializable data object PhoneConfig

    @Serializable data object Diagnostics

    @Serializable data object About

    @Serializable data object Catalog

    @Serializable data object Wizard
}

fun NavHostController.goTo(dest: MenuDestination) {
    when (dest) {
        MenuDestination.LIVE -> popBackStack(Routes.Live, inclusive = false)
        MenuDestination.RECORDINGS -> navigate(Routes.Recordings())
        MenuDestination.SETTINGS -> navigate(Routes.Settings)
        MenuDestination.DIAGNOSTICS -> navigate(Routes.Diagnostics)
        MenuDestination.ABOUT -> navigate(Routes.About)
        MenuDestination.CATALOG -> navigate(Routes.Catalog)
    }
}

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val motion = AoVivoTheme.motion
    // Primeira execução sem câmeras: abre o assistente.
    LaunchedEffect(Unit) {
        val cfg = container.configRepository.config.first()
        if (!cfg.settings.firstRunDone && cfg.cameras.isEmpty() && !cfg.settings.demoMode) nav.navigate(Routes.Wizard)
    }
    NavHost(
        navController = nav,
        startDestination = Routes.Live,
        enterTransition = { fadeIn(motion.slow()) },
        exitTransition = { fadeOut(motion.slow()) },
        popEnterTransition = { fadeIn(motion.slow()) },
        popExitTransition = { fadeOut(motion.slow()) },
    ) {
        composable<Routes.Live> {
            val vm = viewModel { LiveViewModel(container) }
            LiveScreen(
                vm = vm,
                onMenu = { nav.goTo(it) },
                onOpenRecordings = { id -> nav.navigate(Routes.Recordings(id)) },
            )
        }
        composable<Routes.Recordings> { entry ->
            val r = entry.toRoute<Routes.Recordings>()
            val vm = viewModel { RecordingsViewModel(container, r.cameraId) }
            RecordingsScreen(vm, onBack = { nav.popBackStack() }, onPlay = { id, origin, t -> nav.navigate(Routes.Playback(id, origin.name, t)) })
        }
        composable<Routes.Playback> { entry ->
            val r = entry.toRoute<Routes.Playback>()
            val vm = viewModel { PlaybackViewModel(container, r.cameraId, PlaybackOrigin.valueOf(r.origin), r.startMillis) }
            PlaybackScreen(vm, onBack = { nav.popBackStack() })
        }
        settingsGraph(nav, container)
        composable<Routes.Diagnostics> {
            val vm = viewModel { DiagnosticsViewModel(container) }
            DiagnosticsScreen(vm, onBack = { nav.popBackStack() })
        }
        composable<Routes.Wizard> {
            val discovery = viewModel { DiscoveryViewModel(container) }
            FirstRunWizard(
                container = container,
                discovery = discovery,
                onPhoneConfig = { nav.navigate(Routes.PhoneConfig) },
                onManual = { nav.navigate(Routes.CameraEdit()) { popUpTo(Routes.Live) } },
                onFinish = { nav.popBackStack(Routes.Live, inclusive = false) },
            )
        }
        composable<Routes.About> { PlaceholderScreen("Sobre", AoVivoIcons.Info) { nav.popBackStack() } }
        composable<Routes.PhoneConfig> { PhoneConfigScreen(container, onBack = { nav.popBackStack() }) }
        composable<Routes.Catalog> { DesignCatalogScreen(onBack = { nav.popBackStack() }) }
    }
}
