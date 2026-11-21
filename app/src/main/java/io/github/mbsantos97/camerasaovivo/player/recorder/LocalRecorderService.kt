package io.github.mbsantos97.camerasaovivo.player.recorder

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import io.github.mbsantos97.camerasaovivo.CamerasAoVivoApp
import io.github.mbsantos97.camerasaovivo.R
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.data.config.AppConfig
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.StreamQuality
import io.github.mbsantos97.camerasaovivo.data.recordings.SegmentNames
import io.github.mbsantos97.camerasaovivo.player.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.io.File

/**
 * Grava, sem decodificar (remux), o stream de cada câmera marcada em segmentos MPEG-TS de 10 min.
 * Sessão separada da exibição. Serviço em primeiro plano com wake lock parcial e WifiLock.
 */
class LocalRecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writers = mutableMapOf<String, Job>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        acquireLocks()
        val container = (application as CamerasAoVivoApp).container
        scope.launch {
            container.configRepository.config
                .map { cfg -> cfg to cfg.cameras.filter { it.recordLocal && !it.demo && !it.hidden } }
                .distinctUntilChanged { a, b -> a.second.map(::key) == b.second.map(::key) && a.first.settings.recording == b.first.settings.recording }
                .collect { (cfg, cams) -> sync(cfg, cams) }
        }
    }

    private fun key(c: CameraConfig) =
        listOf(
            c.id,
            c.host,
            c.rtspPort,
            c.username,
            c.passwordEnc,
            c.streams?.sources?.firstOrNull()?.subUri,
            c.streams?.sources?.firstOrNull()?.mainUri,
            c.learnedTransport,
        )

    private fun sync(
        cfg: AppConfig,
        cams: List<CameraConfig>,
    ) {
        writers.values.forEach { it.cancel() }
        writers.clear()
        if (cams.isEmpty()) {
            AppLogger.i(TAG, "Nenhuma câmera com gravação; encerrando o serviço")
            stopSelf()
            return
        }
        cams.forEach { cam -> writers[cam.id] = scope.launch { record(cam, cfg) } }
    }

    private suspend fun record(
        cam: CameraConfig,
        cfg: AppConfig,
    ) {
        val container = (application as CamerasAoVivoApp).container
        val repo = container.recordings
        val settings = cfg.settings.recording
        val segmentMs = settings.segmentMinutes * 60_000L
        val password = container.configRepository.password(cam)
        var failures = 0
        while (scope.isActive) {
            val src = cam.streams?.sources?.firstOrNull()
            val uri = if (settings.quality == StreamQuality.SUB) src?.subUri ?: src?.mainUri else src?.mainUri ?: src?.subUri
            val vol = repo.volumeFor(settings)
            if (uri == null || vol == null) {
                AppLogger.w(TAG, "${cam.name}: sem stream ou sem armazenamento para gravar")
                delay(RETRY_MS)
                continue
            }
            repo.enforceRetention(settings)
            val start = System.currentTimeMillis()
            val file = File(repo.dirFor(vol, cam.id), SegmentNames.name(cam.id, start))
            val mp = MediaPlayer(container.libVlc.get())
            try {
                val media = Media(container.libVlc.get(), Uri.parse(uri))
                media.setHWDecoderEnabled(false, false)
                media.addOption(":network-caching=1000")
                if (cam.transport == Transport.TCP || cam.learnedTransport == Transport.TCP) media.addOption(":rtsp-tcp")
                if (cam.username.isNotEmpty()) media.addOption(":rtsp-user=${cam.username}")
                if (password.isNotEmpty()) media.addOption(":rtsp-pwd=$password")
                media.addOption(":sout=#std{access=file,mux=ts,dst=${file.absolutePath}}")
                media.addOption(":sout-keep")
                media.addOption(":no-sout-all")
                mp.media = media
                media.release()
                mp.play()
                AppLogger.i(TAG, "${cam.name}: gravando ${file.name} em ${vol.label}")
                // Grava até completar o segmento (ou a sessão cair).
                val end = start + segmentMs
                while (scope.isActive && System.currentTimeMillis() < end) {
                    delay(CHECK_MS)
                    val state = mp.playerState
                    if (state == IMedia.State.Error || state == IMedia.State.Ended) break
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "${cam.name}: falha na gravação", e)
            } finally {
                runCatching {
                    mp.stop()
                    mp.release()
                }
            }
            val ok = file.exists() && file.length() > MIN_SEGMENT_BYTES
            if (!ok) {
                file.delete()
                failures++
                delay((RETRY_MS * failures).coerceAtMost(MAX_RETRY_MS))
            } else {
                failures = 0
            }
        }
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW))
        }
        val notification =
            (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Notification.Builder(this, CHANNEL)
                } else {
                    @Suppress("DEPRECATION")
                    Notification.Builder(
                        this,
                    )
                }
            )
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.recording_notification))
                .setSmallIcon(R.drawable.ic_record)
                .setOngoing(true)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        wakeLock = getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CamerasAoVivo:recorder")?.apply { acquire() }
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CamerasAoVivo:recorder")?.apply { acquire() }
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Recorder"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 42
        private const val CHECK_MS = 2_000L
        private const val RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 60_000L
        private const val MIN_SEGMENT_BYTES = 16 * 1024L

        /** Liga ou desliga o serviço conforme haja câmeras com gravação. */
        fun sync(
            context: Context,
            anyRecording: Boolean,
        ) {
            val intent = Intent(context, LocalRecorderService::class.java)
            if (anyRecording) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
                }.onFailure { AppLogger.w(TAG, "Não foi possível iniciar a gravação em segundo plano", it) }
            } else {
                context.stopService(intent)
            }
        }
    }
}
