package io.github.mbsantos97.camerasaovivo.core.security

import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Criptografia baseada em PIN (JVM pura, testável): hash do PIN de Configurações e
 * cifra do arquivo de exportação (PBKDF2-HMAC-SHA256 + AES-256-GCM).
 */
object PinCrypto {
    private const val HASH_ITERATIONS = 20_000
    private const val BACKUP_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    private fun enc(b: ByteArray): String = b.toByteString().base64()

    private fun dec(s: String): ByteArray = requireNotNull(s.decodeBase64()) { "Base64 inválido" }.toByteArray()

    fun newSalt(bytes: Int = 16): String = enc(ByteArray(bytes).also(random::nextBytes))

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also(random::nextBytes)

    fun hashPin(
        pin: String,
        salt: String,
    ): String = enc(derive(pin, dec(salt), HASH_ITERATIONS))

    /** Comparação em tempo constante. */
    fun verifyPin(
        pin: String,
        salt: String?,
        hash: String?,
    ): Boolean {
        if (salt.isNullOrEmpty() || hash.isNullOrEmpty()) return true
        return MessageDigest.isEqual(hashPin(pin, salt).toByteArray(), hash.toByteArray())
    }

    fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }

    data class Sealed(
        val salt: String,
        val iv: String,
        val data: String,
        val iterations: Int,
    )

    fun seal(
        plain: ByteArray,
        secret: String,
    ): Sealed {
        val salt = randomBytes(16)
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derive(secret, salt, BACKUP_ITERATIONS), "AES"), GCMParameterSpec(TAG_BITS, iv))
        return Sealed(enc(salt), enc(iv), enc(cipher.doFinal(plain)), BACKUP_ITERATIONS)
    }

    /** Lança exceção se o PIN estiver errado ou o arquivo adulterado (tag GCM inválida). */
    fun open(
        sealed: Sealed,
        secret: String,
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(derive(secret, dec(sealed.salt), sealed.iterations), "AES"),
            GCMParameterSpec(TAG_BITS, dec(sealed.iv)),
        )
        return cipher.doFinal(dec(sealed.data))
    }

    private fun derive(
        secret: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
