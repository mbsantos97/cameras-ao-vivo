package io.github.mbsantos97.camerasaovivo.data.config

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.logging.LogMasker
import io.github.mbsantos97.camerasaovivo.core.security.PinCrypto
import io.github.mbsantos97.camerasaovivo.core.security.SecretCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

val ConfigJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
    }

object AppConfigSerializer : Serializer<AppConfig> {
    override val defaultValue: AppConfig = AppConfig()

    override suspend fun readFrom(input: InputStream): AppConfig =
        try {
            ConfigJson.decodeFromString(AppConfig.serializer(), input.readBytes().decodeToString())
        } catch (e: SerializationException) {
            throw CorruptionException("Configuração ilegível", e)
        }

    override suspend fun writeTo(
        t: AppConfig,
        output: OutputStream,
    ) {
        output.write(ConfigJson.encodeToString(AppConfig.serializer(), t).encodeToByteArray())
    }
}

/**
 * Fonte única da configuração (câmeras + preferências), persistida com DataStore em JSON.
 * Senhas ficam cifradas pelo [SecretCipher]; o texto puro só existe em memória, quando necessário.
 */
class ConfigRepository(
    private val store: DataStore<AppConfig>,
    private val cipher: SecretCipher,
    scope: CoroutineScope,
) {
    val config: Flow<AppConfig> = store.data

    /** Estado atual compartilhado (lido de forma síncrona pela UI). */
    val state: StateFlow<AppConfig?> = store.data.stateIn(scope, SharingStarted.Eagerly, null)

    suspend fun current(): AppConfig = store.data.first()

    suspend fun update(transform: (AppConfig) -> AppConfig): AppConfig = store.updateData(transform)

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) = update { it.copy(settings = transform(it.settings)) }

    suspend fun camera(id: String): CameraConfig? = current().cameras.firstOrNull { it.id == id }

    suspend fun upsertCamera(camera: CameraConfig) =
        update { cfg ->
            val exists = cfg.cameras.any { it.id == camera.id }
            val cams =
                if (exists) {
                    cfg.cameras.map { if (it.id == camera.id) camera else it }
                } else {
                    cfg.cameras + camera.copy(order = (cfg.cameras.maxOfOrNull { it.order } ?: -1) + 1)
                }
            cfg.copy(cameras = cams)
        }

    suspend fun updateCamera(
        id: String,
        transform: (CameraConfig) -> CameraConfig,
    ) = update { cfg -> cfg.copy(cameras = cfg.cameras.map { if (it.id == id) transform(it) else it }) }

    suspend fun removeCamera(id: String) = update { cfg -> cfg.copy(cameras = cfg.cameras.filterNot { it.id == id }) }

    /** Move a câmera uma posição para cima (-1) ou para baixo (+1) na grade. */
    suspend fun moveCamera(
        id: String,
        delta: Int,
    ) = update { cfg ->
        val sorted = cfg.cameras.sortedBy { it.order }.toMutableList()
        val i = sorted.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in sorted.indices) return@update cfg
        val tmp = sorted[i]
        sorted[i] = sorted[j]
        sorted[j] = tmp
        cfg.copy(cameras = sorted.mapIndexed { idx, c -> c.copy(order = idx) })
    }

    fun newCameraId(): String = UUID.randomUUID().toString()

    fun encryptPassword(plain: String): String = cipher.encrypt(plain)

    /** Senha em texto puro (registrada no mascarador de log). */
    fun password(camera: CameraConfig): String = cipher.decrypt(camera.passwordEnc).also(LogMasker::register)

    suspend fun setPin(pin: String?) =
        updateSettings { s ->
            if (pin == null) {
                s.copy(pinHash = null, pinSalt = null)
            } else {
                val salt = PinCrypto.newSalt()
                s.copy(pinSalt = salt, pinHash = PinCrypto.hashPin(pin, salt))
            }
        }

    suspend fun checkPin(pin: String): Boolean {
        val s = current().settings
        return PinCrypto.verifyPin(pin, s.pinSalt, s.pinHash)
    }

    suspend fun clearAll() {
        store.updateData { AppConfig() }
        AppLogger.i(TAG, "Dados do app apagados")
    }

    companion object {
        private const val TAG = "Config"

        fun create(
            context: Context,
            cipher: SecretCipher,
            scope: CoroutineScope,
        ): ConfigRepository {
            val store =
                DataStoreFactory.create(
                    serializer = AppConfigSerializer,
                    corruptionHandler =
                        ReplaceFileCorruptionHandler {
                            AppLogger.e(TAG, "Configuração corrompida; recomeçando do zero", it)
                            AppConfig()
                        },
                    scope = scope,
                    produceFile = { context.dataStoreFile("config.json") },
                )
            return ConfigRepository(store, cipher, scope)
        }
    }
}
