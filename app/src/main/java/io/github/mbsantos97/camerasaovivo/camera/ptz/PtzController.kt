package io.github.mbsantos97.camerasaovivo.camera.ptz

import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.data.config.PtzMode
import io.github.mbsantos97.camerasaovivo.data.config.PtzSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Operações PTZ de uma câmera (ONVIF na prática; falso nos testes). */
interface PtzBackend {
    suspend fun continuousMove(
        x: Float,
        y: Float,
        timeout: String?,
    )

    suspend fun stop()

    suspend fun relativeMove(
        x: Float,
        y: Float,
    )
}

enum class PtzDirection(
    val dx: Int,
    val dy: Int,
) {
    LEFT(-1, 0),
    RIGHT(1, 0),
    UP(0, 1),
    DOWN(0, -1),
}

/** Comandos da fila (processados em ordem: um Start nunca roda depois do seu Stop). */
private sealed interface PtzCommand {
    data class Move(
        val x: Float,
        val y: Float,
    ) : PtzCommand

    data class Step(
        val x: Float,
        val y: Float,
    ) : PtzCommand

    data object Stop : PtzCommand
}

/**
 * Controle PTZ pelo controle remoto.
 * - KeyDown (sem repetição) → ContinuousMove; KeyUp → Stop; duas setas → diagonal.
 * - Segurança: sem KeyUp em 8 s (ou ao perder o foco/pausar) → Stop. ContinuousMove leva Timeout PT10S.
 * - Modo Passo: cada toque = movimento curto (RelativeMove, ou ContinuousMove PT0.4S + Stop).
 */
class PtzController(
    private val backend: PtzBackend,
    private val scope: CoroutineScope,
    settings: PtzSettings,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile var settings: PtzSettings = settings

    private val queue = Channel<PtzCommand>(Channel.UNLIMITED)
    private val pressed = linkedSetOf<PtzDirection>()
    private var watchdog: Job? = null
    private var relativeWorks = true

    private val _moving = MutableStateFlow<Set<PtzDirection>>(emptySet())

    /** Direções em movimento (para a seta translúcida na borda). */
    val moving: StateFlow<Set<PtzDirection>> = _moving

    /** Latência da última tecla até o envio do comando (ms), para o PerfOverlay. */
    @Volatile var lastLatencyMs: Long = 0
        private set

    @Volatile private var lastEnqueuedAt = 0L

    init {
        scope.launch {
            for (cmd in queue) {
                lastLatencyMs = now() - lastEnqueuedAt
                runCatching { execute(cmd) }.onFailure { AppLogger.w(TAG, "Comando PTZ falhou: $cmd", it) }
            }
        }
    }

    private val stepMode: Boolean get() = settings.mode == PtzMode.STEP || settings.autoStep

    private fun enqueue(cmd: PtzCommand) {
        lastEnqueuedAt = now()
        queue.trySend(cmd)
    }

    private suspend fun execute(cmd: PtzCommand) {
        when (cmd) {
            is PtzCommand.Move -> backend.continuousMove(cmd.x, cmd.y, MOVE_TIMEOUT)
            PtzCommand.Stop -> backend.stop()
            is PtzCommand.Step -> {
                if (relativeWorks) {
                    val ok = runCatching { backend.relativeMove(cmd.x * STEP_SIZE, cmd.y * STEP_SIZE) }.isSuccess
                    if (ok) return
                    relativeWorks = false
                }
                backend.continuousMove(cmd.x, cmd.y, STEP_TIMEOUT)
                delay(STEP_MILLIS)
                backend.stop()
            }
        }
    }

    /** Vetor combinado das setas pressionadas, com inversão e velocidade. */
    internal fun vector(dirs: Set<PtzDirection>): Pair<Float, Float> {
        val v = settings.speed.value
        var x = dirs.sumOf { it.dx }.coerceIn(-1, 1) * v
        var y = dirs.sumOf { it.dy }.coerceIn(-1, 1) * v
        if (settings.invertPan) x = -x
        if (settings.invertTilt) y = -y
        return x to y
    }

    fun press(dir: PtzDirection) {
        if (!pressed.add(dir)) return
        if (stepMode) {
            val (x, y) = vector(setOf(dir))
            enqueue(PtzCommand.Step(x, y))
            pressed.remove(dir)
            return
        }
        val (x, y) = vector(pressed)
        _moving.value = pressed.toSet()
        enqueue(PtzCommand.Move(x, y))
        armWatchdog()
    }

    fun release(dir: PtzDirection) {
        if (!pressed.remove(dir)) return
        if (pressed.isEmpty()) {
            stopNow()
        } else {
            val (x, y) = vector(pressed)
            _moving.value = pressed.toSet()
            enqueue(PtzCommand.Move(x, y))
        }
    }

    /** Para tudo (tela perdeu o foco, pausou, trocou de câmera…). */
    fun stopNow() {
        watchdog?.cancel()
        val wasMoving = _moving.value.isNotEmpty() || pressed.isNotEmpty()
        pressed.clear()
        _moving.value = emptySet()
        if (wasMoving || !stepMode) enqueue(PtzCommand.Stop)
    }

    private fun armWatchdog() {
        watchdog?.cancel()
        watchdog =
            scope.launch {
                delay(WATCHDOG_MILLIS)
                if (pressed.isNotEmpty()) {
                    AppLogger.w(TAG, "Sem KeyUp em ${WATCHDOG_MILLIS / 1000} s; enviando Stop")
                    stopNow()
                }
            }
    }

    fun close() {
        stopNow()
        queue.close()
    }

    companion object {
        private const val TAG = "PTZ"
        const val MOVE_TIMEOUT = "PT10S"
        const val STEP_TIMEOUT = "PT0.4S"
        const val STEP_MILLIS = 400L
        const val STEP_SIZE = 0.1f
        const val WATCHDOG_MILLIS = 8_000L
    }
}
