package io.github.mbsantos97.camerasaovivo.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import io.github.mbsantos97.camerasaovivo.ui.designsystem.LocalToast
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextInputDialog
import io.github.mbsantos97.camerasaovivo.ui.designsystem.TextInputKind
import io.github.mbsantos97.camerasaovivo.ui.settings.CameraEditScreen
import io.github.mbsantos97.camerasaovivo.ui.settings.CameraListScreen
import io.github.mbsantos97.camerasaovivo.ui.settings.DiscoveryScreen
import io.github.mbsantos97.camerasaovivo.ui.settings.DiscoveryViewModel
import io.github.mbsantos97.camerasaovivo.ui.settings.SettingsScreen
import io.github.mbsantos97.camerasaovivo.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

fun NavGraphBuilder.settingsGraph(
    nav: NavHostController,
    container: AppContainer,
) {
    composable<Routes.Settings> {
        val vm = viewModel { SettingsViewModel(container) }
        val cfg by vm.config.collectAsStateWithLifecycle()
        var unlocked by remember { mutableStateOf(container.isSettingsUnlocked()) }
        val toast = LocalToast.current
        val scope = rememberCoroutineScope()
        val config = cfg ?: return@composable
        if (config.settings.pinHash != null && !unlocked) {
            TextInputDialog(
                title = "PIN de Configurações",
                initial = "",
                kind = TextInputKind.PIN,
                maxLength = 4,
                onDismiss = { nav.popBackStack() },
                onConfirm = { pin ->
                    scope.launch {
                        if (vm.checkPin(pin)) {
                            container.unlockSettings()
                            unlocked = true
                        } else {
                            toast.show("PIN incorreto")
                        }
                    }
                },
            )
        } else {
            SettingsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onCameras = { nav.navigate(Routes.CameraList) },
                onPhoneConfig = { nav.navigate(Routes.PhoneConfig) },
                onDiagnostics = { nav.navigate(Routes.Diagnostics) },
                onAbout = { nav.navigate(Routes.About) },
                onCleared = { nav.popBackStack(Routes.Live, inclusive = false) },
            )
        }
    }
    composable<Routes.CameraList> {
        val vm = viewModel { SettingsViewModel(container) }
        CameraListScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onEdit = { id -> nav.navigate(Routes.CameraEdit(id)) },
            onDiscover = { nav.navigate(Routes.Discovery) },
            onPhoneConfig = { nav.navigate(Routes.PhoneConfig) },
        )
    }
    composable<Routes.CameraEdit> { entry ->
        val route = entry.toRoute<Routes.CameraEdit>()
        val vm = viewModel { SettingsViewModel(container) }
        CameraEditScreen(
            vm = vm,
            cameraId = route.cameraId,
            prefillHost = route.host,
            onBack = { nav.popBackStack() },
        )
    }
    composable<Routes.Discovery> {
        val vm = viewModel { DiscoveryViewModel(container) }
        DiscoveryScreen(
            vm = vm,
            onBack = { nav.popBackStack() },
            onPick = { d -> nav.navigate(Routes.CameraEdit(host = d.host)) },
            onManual = { nav.navigate(Routes.CameraEdit()) },
        )
    }
}
