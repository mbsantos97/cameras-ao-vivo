package io.github.mbsantos97.camerasaovivo.core.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteKeyMapperTest {
    @Test
    fun `mapeia D-pad, OK e voltar`() {
        assertEquals(RemoteKey.UP, RemoteKeyMapper.map(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(RemoteKey.OK, RemoteKeyMapper.map(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(RemoteKey.OK, RemoteKeyMapper.map(KeyEvent.KEYCODE_ENTER))
        assertEquals(RemoteKey.BACK, RemoteKeyMapper.map(KeyEvent.KEYCODE_BACK))
        assertEquals(RemoteKey.CHANNEL_UP, RemoteKeyMapper.map(KeyEvent.KEYCODE_CHANNEL_UP))
    }

    @Test
    fun `mapeia digitos do teclado e do numpad`() {
        assertEquals(3, RemoteKeyMapper.map(KeyEvent.KEYCODE_3)?.digit)
        assertEquals(9, RemoteKeyMapper.map(KeyEvent.KEYCODE_NUMPAD_9)?.digit)
        assertNull(RemoteKey.UP.digit)
    }

    @Test
    fun `tecla desconhecida devolve null`() {
        assertNull(RemoteKeyMapper.map(KeyEvent.KEYCODE_VOLUME_UP))
    }
}
