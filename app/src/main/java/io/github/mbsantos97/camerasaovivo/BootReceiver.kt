package io.github.mbsantos97.camerasaovivo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Iniciar com a TV Box" (desligado por padrão): abre o app quando o aparelho liga.
 * Se o sistema bloquear a abertura em segundo plano, ignora em silêncio (ver docs/LIMITACOES.md).
 * A gravação local é religada pelo observador do Application, que sobe com o processo.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        val app = context.applicationContext as? CamerasAoVivoApp ?: return
        val pending = goAsync()
        app.container.appScope.launch {
            try {
                val cfg = app.container.configRepository.config.first()
                if (cfg.settings.startOnBoot) {
                    runCatching {
                        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure { AppLogger.i("Boot", "Sistema não permitiu abrir o app no boot") }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
