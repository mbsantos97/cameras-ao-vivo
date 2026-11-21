package io.github.mbsantos97.camerasaovivo.core.input

/** Resultado de uma interação com o botão OK. */
enum class PressResult { NONE, SHORT, LONG }

/**
 * Máquina de estados que diferencia toque curto e longo no OK sem disparar os dois:
 * - ação curta no KeyUp;
 * - ação longa ao atingir [longPressMillis] com a tecla pressionada (via repetição ou [onTick]),
 *   e o KeyUp seguinte é ignorado.
 */
class LongPressDetector(
    private val longPressMillis: Long = 600,
) {
    private var downAt: Long = NOT_PRESSED
    private var longFired = false

    val isPressed: Boolean get() = downAt != NOT_PRESSED

    fun onKeyDown(
        timeMillis: Long,
        repeatCount: Int,
    ): PressResult {
        if (repeatCount == 0 || downAt == NOT_PRESSED) {
            if (repeatCount == 0) {
                downAt = timeMillis
                longFired = false
            }
            return PressResult.NONE
        }
        return check(timeMillis)
    }

    /** Chamado por um temporizador enquanto a tecla está pressionada (o controle nem sempre repete). */
    fun onTick(timeMillis: Long): PressResult = check(timeMillis)

    fun onKeyUp(timeMillis: Long): PressResult {
        if (downAt == NOT_PRESSED) return PressResult.NONE
        val start = downAt
        val alreadyFired = longFired
        reset()
        return when {
            alreadyFired -> PressResult.NONE
            timeMillis - start >= longPressMillis -> PressResult.LONG
            else -> PressResult.SHORT
        }
    }

    fun reset() {
        downAt = NOT_PRESSED
        longFired = false
    }

    private fun check(timeMillis: Long): PressResult {
        if (downAt == NOT_PRESSED || longFired) return PressResult.NONE
        if (timeMillis - downAt >= longPressMillis) {
            longFired = true
            return PressResult.LONG
        }
        return PressResult.NONE
    }

    private companion object {
        const val NOT_PRESSED = -1L
    }
}
