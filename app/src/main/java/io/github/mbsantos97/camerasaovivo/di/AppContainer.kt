package io.github.mbsantos97.camerasaovivo.di

import android.content.Context
import io.github.mbsantos97.camerasaovivo.BuildConfig
import io.github.mbsantos97.camerasaovivo.camera.CameraLocator
import io.github.mbsantos97.camerasaovivo.camera.ConnectionTester
import io.github.mbsantos97.camerasaovivo.camera.LivePlaybackEnv
import io.github.mbsantos97.camerasaovivo.camera.OnvifFactory
import io.github.mbsantos97.camerasaovivo.camera.PlaybackEnv
import io.github.mbsantos97.camerasaovivo.camera.StreamResolver
import io.github.mbsantos97.camerasaovivo.core.network.LanOnlyGuard
import io.github.mbsantos97.camerasaovivo.core.network.NetworkMonitor
import io.github.mbsantos97.camerasaovivo.core.security.KeystoreCipher
import io.github.mbsantos97.camerasaovivo.core.security.SecretCipher
import io.github.mbsantos97.camerasaovivo.data.config.AppConfig
import io.github.mbsantos97.camerasaovivo.data.config.AppSettings
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.ConfigRepository
import io.github.mbsantos97.camerasaovivo.data.config.DemoData
import io.github.mbsantos97.camerasaovivo.data.recordings.LocalRecordingRepository
import io.github.mbsantos97.camerasaovivo.data.snapshots.SnapshotRepository
import io.github.mbsantos97.camerasaovivo.data.snapshots.ThumbnailCache
import io.github.mbsantos97.camerasaovivo.onvif.OnvifClient
import io.github.mbsantos97.camerasaovivo.onvif.discovery.WsDiscovery
import io.github.mbsantos97.camerasaovivo.player.DecoderBudget
import io.github.mbsantos97.camerasaovivo.player.FakeStreamPlayer
import io.github.mbsantos97.camerasaovivo.player.LibVlcProvider
import io.github.mbsantos97.camerasaovivo.player.StreamPlayer
import io.github.mbsantos97.camerasaovivo.player.VlcStreamPlayer
import io.github.mbsantos97.camerasaovivo.player.recorder.LocalRecorderService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Injeção de dependências manual. Tudo é `lazy`: nada de rede nem LibVLC na abertura do app.
 */
class AppContainer(
    private val context: Context,
) {
    val appContext: Context get() = context

    /** Escopo do processo (não morre com telas). */
    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    val cipher: SecretCipher by lazy { KeystoreCipher() }

    val configRepository: ConfigRepository by lazy { ConfigRepository.create(context, cipher, appScope) }

    /** Um único OkHttpClient (pool + keep-alive) para todo o ONVIF; só destinos da rede local. */
    val http: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .dns(LanOnlyGuard.dns)
            .addInterceptor(LanOnlyGuard.interceptor)
            .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Clientes ONVIF reaproveitados por câmera (mantêm offset de relógio e modo de autenticação). */
    private val onvifClients = java.util.concurrent.ConcurrentHashMap<String, OnvifClient>()

    val onvifFactory: OnvifFactory = { cam, password ->
        val key = "${cam.id}|${cam.host}|${cam.onvifPort}|${cam.username}|${password.hashCode()}"
        onvifClients.getOrPut(key) { OnvifClient(http, cam.host, cam.onvifPort, cam.username, password) }
    }

    val streamResolver: StreamResolver by lazy { StreamResolver(onvifFactory) }

    fun newConnectionTester(): ConnectionTester = ConnectionTester(onvifFactory, streamResolver)

    val wsDiscovery: WsDiscovery by lazy { WsDiscovery(context) }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(context).also { it.start() } }

    val libVlc: LibVlcProvider by lazy { LibVlcProvider(context) }

    val decoderBudget = DecoderBudget()

    val recordings: LocalRecordingRepository by lazy { LocalRecordingRepository(context) }

    /** Liga/desliga o serviço de gravação conforme a configuração (em segundo plano, sem travar a abertura). */
    fun watchRecorder() {
        appScope.launch {
            configRepository.config
                .map { cfg -> cfg.cameras.any { it.recordLocal && !it.demo && !it.hidden } }
                .distinctUntilChanged()
                .collect { LocalRecorderService.sync(context, it) }
        }
    }

    val snapshots: SnapshotRepository by lazy { SnapshotRepository(context) }

    val thumbnails: ThumbnailCache by lazy { ThumbnailCache(java.io.File(context.filesDir, "thumbs")) }

    val cameraLocator: CameraLocator by lazy { CameraLocator(wsDiscovery, onvifFactory) }

    fun playbackEnv(settings: () -> AppSettings): PlaybackEnv = LivePlaybackEnv(configRepository, streamResolver, cameraLocator, decoderBudget, settings)

    /** Player para uma câmera: falso no modo demonstração, LibVLC nas reais. */
    fun newPlayer(
        cam: CameraConfig,
        label: String,
        stacked: Boolean,
        hue: Float,
    ): StreamPlayer = if (cam.demo) FakeStreamPlayer(label, hue, stacked) else VlcStreamPlayer(libVlc, label)

    /** Configuração vista pela tela Ao Vivo: no modo demonstração, inclui as câmeras falsas. */
    val liveConfig: Flow<AppConfig> by lazy {
        configRepository.config.map { cfg ->
            if (cfg.settings.demoMode) cfg.copy(cameras = cfg.cameras + DemoData.cameras()) else cfg
        }
    }

    /** Desbloqueio do PIN de Configurações vale por alguns minutos na mesma sessão. */
    @Volatile
    var settingsUnlockedUntil: Long = 0L

    fun isSettingsUnlocked(): Boolean = System.currentTimeMillis() < settingsUnlockedUntil

    fun unlockSettings() {
        settingsUnlockedUntil = System.currentTimeMillis() + UNLOCK_MILLIS
    }

    /** No debug, a primeira execução já abre no modo demonstração (sem câmeras reais). */
    fun applyFirstRunDefaults() {
        if (!BuildConfig.DEBUG) return
        appScope.launch {
            val cfg = configRepository.current()
            if (!cfg.settings.firstRunDone && cfg.cameras.isEmpty() && !cfg.settings.demoMode) {
                configRepository.updateSettings { it.copy(demoMode = true) }
            }
        }
    }

    private companion object {
        const val UNLOCK_MILLIS = 5 * 60_000L
    }
}
