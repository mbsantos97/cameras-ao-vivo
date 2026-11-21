package io.github.mbsantos97.camerasaovivo.data.config

import androidx.datastore.core.DataStore
import io.github.mbsantos97.camerasaovivo.core.security.SecretCipher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cifra falsa (reversível) para testar sem Android Keystore. */
private object FakeCipher : SecretCipher {
    override fun encrypt(plain: String) = if (plain.isEmpty()) "" else "enc:" + plain.reversed()

    override fun decrypt(encoded: String) = if (encoded.startsWith("enc:")) encoded.removePrefix("enc:").reversed() else ""
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigTest {
    /** DataStore em memória: no Windows o rename atômico do DataStore em arquivo falha em gravações seguidas. */
    private class MemoryStore : DataStore<AppConfig> {
        private val state = MutableStateFlow(AppConfig())
        private val lock = Mutex()
        override val data: Flow<AppConfig> = state

        override suspend fun updateData(transform: suspend (t: AppConfig) -> AppConfig): AppConfig =
            lock.withLock { transform(state.value).also { state.value = it } }
    }

    private fun TestScope.repo(): ConfigRepository = ConfigRepository(MemoryStore(), FakeCipher, backgroundScope)

    private fun cam(
        id: String,
        name: String = id,
    ) = CameraConfig(id = id, name = name, host = "192.168.0.10")

    @Test
    fun `adicionar, editar, mover e remover camera`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            r.upsertCamera(cam("a"))
            r.upsertCamera(cam("b"))
            assertEquals(listOf(0, 1), r.current().cameras.map { it.order })
            r.updateCamera("a") { it.copy(name = "Externa") }
            assertEquals("Externa", r.camera("a")?.name)
            r.moveCamera("b", -1)
            assertEquals(listOf("b", "a"), r.current().cameras.sortedBy { it.order }.map { it.id })
            r.removeCamera("a")
            assertEquals(listOf("b"), r.current().cameras.map { it.id })
        }

    @Test
    fun `senha fica cifrada no arquivo`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            r.upsertCamera(cam("a").copy(passwordEnc = r.encryptPassword("minhaSenha")))
            val raw = ConfigJson.encodeToString(AppConfig.serializer(), r.current())
            assertFalse(raw.contains("minhaSenha"))
            assertEquals("minhaSenha", r.password(r.camera("a")!!))
        }

    @Test
    fun `PIN definido, conferido e removido`() =
        runTest(UnconfinedTestDispatcher()) {
            val r = repo()
            r.setPin("1234")
            assertNotNull(r.current().settings.pinHash)
            assertTrue(r.checkPin("1234"))
            assertFalse(r.checkPin("0000"))
            r.setPin(null)
            assertTrue(r.checkPin("0000"))
        }

    @Test
    fun `configuracao serializada ignora campos desconhecidos`() {
        val json = """{"version":1,"campoNovo":true,"cameras":[{"id":"x","name":"X","host":"10.0.0.2","futuro":1}]}"""
        val cfg = ConfigJson.decodeFromString(AppConfig.serializer(), json)
        assertEquals("X", cfg.cameras.single().name)
        assertEquals(5000, cfg.cameras.single().onvifPort)
    }

    @Test
    fun `exportar e importar com PIN preserva cameras e senhas`() {
        val cfg =
            AppConfig(
                cameras = listOf(cam("a", "Externa").copy(passwordEnc = FakeCipher.encrypt("s3nha")), DemoData.cameras().first()),
                settings = AppSettings(showClock = true),
            )
        val text = ConfigBackup.export(cfg, { FakeCipher.decrypt(it.passwordEnc) }, "2468")
        assertFalse(text.contains("s3nha"))
        assertFalse(text.contains("Externa"))
        val back = ConfigBackup.import(text, "2468", FakeCipher::encrypt)
        assertEquals(listOf("Externa"), back.cameras.map { it.name })
        assertEquals("s3nha", FakeCipher.decrypt(back.cameras.single().passwordEnc))
        assertTrue(back.settings.showClock)
    }

    @Test(expected = BackupException::class)
    fun `importar com PIN errado falha com mensagem`() {
        val text = ConfigBackup.export(AppConfig(), { "" }, "2468")
        ConfigBackup.import(text, "1111", FakeCipher::encrypt)
    }

    @Test(expected = BackupException::class)
    fun `arquivo que nao e backup e recusado`() {
        ConfigBackup.import("{\"foo\":1}", "1111", FakeCipher::encrypt)
    }

    @Test
    fun `lente motorizada efetiva`() {
        assertEquals(MotorizedLens.BOTTOM, cam("a").effectiveMotorizedLens)
        assertEquals(MotorizedLens.TOP, cam("a").copy(detectedMotorizedLens = MotorizedLens.TOP).effectiveMotorizedLens)
        assertEquals(
            MotorizedLens.BOTTOM,
            cam("a").copy(motorizedLens = MotorizedLens.BOTTOM, detectedMotorizedLens = MotorizedLens.TOP).effectiveMotorizedLens,
        )
    }

    @Test
    fun `fluxo de configuracao emite o valor padrao`() =
        runTest(UnconfinedTestDispatcher()) {
            assertEquals(AppConfig(), repo().config.first())
        }
}
