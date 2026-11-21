package io.github.mbsantos97.camerasaovivo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.network.IpUtils
import io.github.mbsantos97.camerasaovivo.core.network.SubnetScanner
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import io.github.mbsantos97.camerasaovivo.onvif.model.DiscoveredDevice
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoButton
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoIcons
import io.github.mbsantos97.camerasaovivo.ui.designsystem.AoVivoTheme
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ButtonKind
import io.github.mbsantos97.camerasaovivo.ui.designsystem.ConnectingIndicator
import io.github.mbsantos97.camerasaovivo.ui.designsystem.EmptyState
import io.github.mbsantos97.camerasaovivo.ui.designsystem.NavigationRow
import io.github.mbsantos97.camerasaovivo.ui.navigation.ScreenScaffold
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class DiscoveryState(
    val searching: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
    val knownHosts: Set<String> = emptySet(),
    val localIp: String? = null,
)

/** Busca câmeras: WS-Discovery e varredura TCP da /24 em paralelo, juntando por IP. */
class DiscoveryViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val _state = MutableStateFlow(DiscoveryState())
    val state: StateFlow<DiscoveryState> = _state

    fun search() {
        if (_state.value.searching) return
        viewModelScope.launch {
            val ip = IpUtils.localIpv4()
            val known = container.configRepository.config.first().cameras.map { it.host }.toSet()
            _state.value = DiscoveryState(searching = true, knownHosts = known, localIp = ip)
            val wsd = async { container.wsDiscovery.probe() }
            val scan = async { if (ip != null) SubnetScanner.scan(ip) else emptyList() }
            val found = linkedMapOf<String, DiscoveredDevice>()
            wsd.await().forEach { found[it.host] = it }
            scan.await().forEach { o ->
                found[o.host] =
                    found[o.host]?.copy(openPorts = o.ports) ?: DiscoveredDevice(o.host, if (5000 in o.ports) 5000 else null, null, openPorts = o.ports)
            }
            AppLogger.i("Discovery", "${found.size} aparelho(s) encontrado(s)")
            _state.update {
                it.copy(searching = false, devices = found.values.sortedBy { d -> d.host.substringAfterLast('.').toIntOrNull() ?: 0 })
            }
        }
    }
}

@Composable
fun DiscoveryScreen(
    vm: DiscoveryViewModel,
    onBack: () -> Unit,
    onPick: (DiscoveredDevice) -> Unit,
    onManual: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val st by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.search() }
    ScreenScaffold(
        "Buscar câmeras na rede",
        icon = AoVivoIcons.Search,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm)) {
                AoVivoButton("Buscar de novo", vm::search, kind = ButtonKind.SECONDARY, icon = AoVivoIcons.Refresh, enabled = !st.searching)
                AoVivoButton("Digitar IP", onManual, kind = ButtonKind.SECONDARY, icon = AoVivoIcons.Add)
            }
        },
    ) {
        if (st.searching) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.sm)) {
                ConnectingIndicator()
                Text("Procurando na rede ${st.localIp ?: ""}… (até 10 s)", style = AoVivoTheme.type.body, color = AoVivoTheme.colors.textSecondary)
            }
        }
        if (!st.searching && st.devices.isEmpty()) {
            EmptyState(
                title = "Nenhuma câmera encontrada",
                message = "Confira se a TV Box e as câmeras estão no mesmo roteador e se a \"Conexão NVR\" está ativa no app Yoosee.",
                icon = AoVivoIcons.Wifi,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(AoVivoTheme.spacing.xs)) {
            items(st.devices, key = { it.host }) { d ->
                val ports =
                    d.openPorts.sorted().joinToString(", ") {
                        if (it == 5000) {
                            "ONVIF"
                        } else if (it == 554) {
                            "RTSP"
                        } else {
                            "$it"
                        }
                    }
                NavigationRow(
                    title = d.name ?: d.hardware ?: "Câmera em ${d.host}",
                    subtitle =
                        listOfNotNull(
                            d.hardware?.takeIf {
                                d.name != null
                            },
                            ports.ifEmpty { null },
                            "já adicionada".takeIf { d.host in st.knownHosts },
                        ).joinToString(" · ").ifEmpty { null },
                    value = d.host,
                    icon = AoVivoIcons.Camera,
                    onClick = { onPick(d) },
                )
            }
        }
    }
}
