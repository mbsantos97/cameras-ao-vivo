package io.github.mbsantos97.camerasaovivo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mbsantos97.camerasaovivo.core.logging.AppLogger
import io.github.mbsantos97.camerasaovivo.core.storage.StorageLocations
import io.github.mbsantos97.camerasaovivo.data.config.AppConfig
import io.github.mbsantos97.camerasaovivo.data.config.AppSettings
import io.github.mbsantos97.camerasaovivo.data.config.BackupException
import io.github.mbsantos97.camerasaovivo.data.config.BackupFile
import io.github.mbsantos97.camerasaovivo.data.config.CameraConfig
import io.github.mbsantos97.camerasaovivo.data.config.ConfigBackup
import io.github.mbsantos97.camerasaovivo.di.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val repo = container.configRepository

    val config: StateFlow<AppConfig?> = repo.config.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.updateSettings(transform) }
    }

    fun setPin(pin: String?) {
        viewModelScope.launch { repo.setPin(pin) }
    }

    suspend fun checkPin(pin: String): Boolean = repo.checkPin(pin)

    fun password(camera: CameraConfig): String = repo.password(camera)

    fun newTester() = container.newConnectionTester()

    fun newCamera(): CameraConfig = CameraConfig(id = repo.newCameraId(), name = "", host = "")

    fun saveCamera(
        camera: CameraConfig,
        plainPassword: String?,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            val enc = if (plainPassword != null) repo.encryptPassword(plainPassword) else camera.passwordEnc
            repo.upsertCamera(camera.copy(passwordEnc = enc, name = camera.name.trim(), host = camera.host.trim()))
            AppLogger.i(TAG, "Câmera salva: ${camera.name} (${camera.host})")
            onDone()
        }
    }

    fun removeCamera(
        id: String,
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            repo.removeCamera(id)
            onDone()
        }
    }

    fun moveCamera(
        id: String,
        delta: Int,
    ) {
        viewModelScope.launch { repo.moveCamera(id, delta) }
    }

    /** Exporta para o pendrive (ou memória interna). Devolve o caminho do arquivo. */
    suspend fun export(pin: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val vol = StorageLocations.preferRemovable(container.appContext) ?: error("Nenhum armazenamento disponível")
                val text = ConfigBackup.export(repo.current(), repo::password, pin)
                val f = File(vol.dir, BackupFile.FILE_NAME)
                f.writeText(text)
                AppLogger.i(TAG, "Configuração exportada para ${vol.label}")
                "${vol.label}: ${f.absolutePath}"
            }
        }

    /** Procura o arquivo de exportação nos volumes disponíveis (pendrive primeiro). */
    fun findBackup(): File? =
        StorageLocations
            .volumes(container.appContext)
            .sortedByDescending { it.removable }
            .map { File(it.dir, BackupFile.FILE_NAME) }
            .firstOrNull { it.exists() }

    suspend fun import(pin: String): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val f = findBackup() ?: throw BackupException("Arquivo ${BackupFile.FILE_NAME} não encontrado no pendrive")
                val cfg = ConfigBackup.import(f.readText(), pin, repo::encryptPassword)
                repo.update { cfg.copy(settings = cfg.settings.copy(firstRunDone = true)) }
                AppLogger.i(TAG, "Configuração importada: ${cfg.cameras.size} câmera(s)")
                cfg.cameras.size
            }
        }

    fun clearAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.clearAll()
            onDone()
        }
    }

    private companion object {
        const val TAG = "Settings"
    }
}
