package io.github.mbsantos97.camerasaovivo.player

/** Backoff de reconexão: 2, 4, 8, 16 e 30 s (máximo), zerando ao conectar. */
object ReconnectPolicy {
    private val steps = longArrayOf(2_000, 4_000, 8_000, 16_000, 30_000)

    fun delayFor(attempt: Int): Long = steps[(attempt - 1).coerceIn(0, steps.lastIndex)]
}

/** O que fazer quando o primeiro frame não chega a tempo. */
enum class NoVideoAction { SWITCH_TO_SOFTWARE, SWITCH_TO_TCP, RETRY }

/**
 * Orçamento de decodificadores (SoCs Amlogic limitam instâncias de hardware simultâneas).
 * - no máximo [maxStreams] streams de exibição ao mesmo tempo;
 * - sem vídeo em [firstFrameTimeoutMs] com dados chegando → decodificação por software (lembrada por câmera);
 * - sem dados chegando no transporte Auto → tenta TCP (o UDP pode estar bloqueado).
 */
class DecoderBudget(
    val maxStreams: Int = 4,
    val firstFrameTimeoutMs: Long = 12_000,
    val watchdogMs: Long = 10_000,
) {
    fun canStart(active: Int): Boolean = active < maxStreams

    fun onNoVideo(
        hardware: Boolean,
        bytesReceived: Long,
        transport: Transport,
        triedTcp: Boolean,
    ): NoVideoAction =
        when {
            bytesReceived > 0 && hardware -> NoVideoAction.SWITCH_TO_SOFTWARE
            bytesReceived <= 0 && transport == Transport.AUTO && !triedTcp -> NoVideoAction.SWITCH_TO_TCP
            else -> NoVideoAction.RETRY
        }

    /** Watchdog: sem frames novos por [watchdogMs] → reconectar. */
    fun isStalled(
        lastFrameAtMs: Long,
        nowMs: Long,
    ): Boolean = nowMs - lastFrameAtMs >= watchdogMs
}
