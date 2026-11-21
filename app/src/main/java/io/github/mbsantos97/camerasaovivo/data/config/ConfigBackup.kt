package io.github.mbsantos97.camerasaovivo.data.config

import io.github.mbsantos97.camerasaovivo.core.security.PinCrypto
import kotlinx.serialization.Serializable

/** Arquivo de exportação: configuração inteira cifrada com o PIN (PBKDF2 + AES-GCM). */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val app: String = "Câmeras ao Vivo",
    val iterations: Int,
    val salt: String,
    val iv: String,
    val data: String,
) {
    companion object {
        const val FORMAT = "cameras-ao-vivo-backup"
        const val FILE_NAME = "cameras-ao-vivo-config.json"
    }
}

class BackupException(
    message: String,
) : Exception(message)

/**
 * Exporta/importa a configuração. Dentro do arquivo cifrado as senhas vão em texto puro
 * (o campo passwordEnc carrega a senha decifrada), porque a chave do Keystore não sai da TV Box.
 * Na importação, elas são cifradas de novo com a chave do aparelho de destino.
 */
object ConfigBackup {
    fun export(
        config: AppConfig,
        decrypt: (CameraConfig) -> String,
        pin: String,
    ): String {
        val plain = config.copy(cameras = config.cameras.filterNot { it.demo }.map { it.copy(passwordEnc = decrypt(it)) })
        val sealed = PinCrypto.seal(ConfigJson.encodeToString(AppConfig.serializer(), plain).encodeToByteArray(), pin)
        val file = BackupFile(iterations = sealed.iterations, salt = sealed.salt, iv = sealed.iv, data = sealed.data)
        return ConfigJson.encodeToString(BackupFile.serializer(), file)
    }

    fun import(
        text: String,
        pin: String,
        encrypt: (String) -> String,
    ): AppConfig {
        val file =
            runCatching { ConfigJson.decodeFromString(BackupFile.serializer(), text) }
                .getOrElse { throw BackupException("Arquivo de configuração inválido") }
        if (file.format != BackupFile.FORMAT) throw BackupException("Este arquivo não é uma exportação do Câmeras ao Vivo")
        val bytes =
            runCatching { PinCrypto.open(PinCrypto.Sealed(file.salt, file.iv, file.data, file.iterations), pin) }
                .getOrElse { throw BackupException("PIN incorreto ou arquivo danificado") }
        val cfg = ConfigJson.decodeFromString(AppConfig.serializer(), bytes.decodeToString())
        return cfg.copy(cameras = cfg.cameras.map { it.copy(passwordEnc = encrypt(it.passwordEnc)) })
    }
}
