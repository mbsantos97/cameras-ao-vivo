package io.github.mbsantos97.camerasaovivo.core.input

import android.view.KeyEvent

/** Teclas do controle remoto que o app entende. */
enum class RemoteKey {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    OK,
    BACK,
    MENU,
    CHANNEL_UP,
    CHANNEL_DOWN,
    MEDIA_NEXT,
    MEDIA_PREVIOUS,
    PLAY_PAUSE,
    PLAY,
    PAUSE,
    FAST_FORWARD,
    REWIND,
    DIGIT_0,
    DIGIT_1,
    DIGIT_2,
    DIGIT_3,
    DIGIT_4,
    DIGIT_5,
    DIGIT_6,
    DIGIT_7,
    DIGIT_8,
    DIGIT_9,
    ;

    val isArrow: Boolean get() = this == UP || this == DOWN || this == LEFT || this == RIGHT

    val digit: Int? get() = if (ordinal in DIGIT_0.ordinal..DIGIT_9.ordinal) ordinal - DIGIT_0.ordinal else null
}

object RemoteKeyMapper {
    fun map(keyCode: Int): RemoteKey? =
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                RemoteKey.UP
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                RemoteKey.DOWN
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                RemoteKey.LEFT
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                RemoteKey.RIGHT
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            -> {
                RemoteKey.OK
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BUTTON_B -> {
                RemoteKey.BACK
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                RemoteKey.MENU
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                RemoteKey.CHANNEL_UP
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                RemoteKey.CHANNEL_DOWN
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                RemoteKey.MEDIA_NEXT
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                RemoteKey.MEDIA_PREVIOUS
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                RemoteKey.PLAY_PAUSE
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                RemoteKey.PLAY
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                RemoteKey.PAUSE
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                RemoteKey.FAST_FORWARD
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                RemoteKey.REWIND
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                RemoteKey.entries[RemoteKey.DIGIT_0.ordinal + keyCode - KeyEvent.KEYCODE_0]
            }
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                RemoteKey.entries[RemoteKey.DIGIT_0.ordinal + keyCode - KeyEvent.KEYCODE_NUMPAD_0]
            }
            else -> {
                null
            }
        }
}
