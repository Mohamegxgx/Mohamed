package com.example.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class EncryptedBackupEnvelope(
    val encryptedFormat: String = FORMAT_V1,
    val iterations: Int = PBKDF2_ITERATIONS,
    val saltB64: String,
    val ivB64: String,
    val cipherTextB64: String
) {
    companion object {
        const val FORMAT_V1 = "ELBAGORRY_AES256_GCM_V1"
        const val PBKDF2_ITERATIONS = 100_000
    }
}

object BackupEncryptionHelper {

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun isEncryptedBackup(rawText: String): Boolean {
        val trimmed = rawText.trim()
        return trimmed.contains(EncryptedBackupEnvelope.FORMAT_V1) &&
            trimmed.contains("cipherTextB64")
    }

    fun encrypt(plainText: String, password: String): String {
        require(password.length >= 4) { "كلمة المرور يجب أن تكون 4 أحرف على الأقل" }
        val rng = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { rng.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { rng.nextBytes(it) }

        val key = deriveKey(password, salt, EncryptedBackupEnvelope.PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val encoder = Base64.getEncoder()
        val envelope = EncryptedBackupEnvelope(
            encryptedFormat = EncryptedBackupEnvelope.FORMAT_V1,
            iterations = EncryptedBackupEnvelope.PBKDF2_ITERATIONS,
            saltB64 = encoder.encodeToString(salt),
            ivB64 = encoder.encodeToString(iv),
            cipherTextB64 = encoder.encodeToString(cipherBytes)
        )
        return json.encodeToString(EncryptedBackupEnvelope.serializer(), envelope)
    }

    fun decrypt(rawEnvelope: String, password: String): Result<String> = runCatching {
        require(password.isNotBlank()) { "أدخل كلمة مرور النسخة الاحتياطية المشفرة" }
        val envelope = json.decodeFromString(EncryptedBackupEnvelope.serializer(), rawEnvelope.trim())
        require(envelope.encryptedFormat == EncryptedBackupEnvelope.FORMAT_V1) {
            "صيغة التشفير غير مدعومة"
        }
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(envelope.saltB64)
        val iv = decoder.decode(envelope.ivB64)
        val cipherBytes = decoder.decode(envelope.cipherTextB64)

        val key = deriveKey(password, salt, envelope.iterations.coerceIn(10_000, 300_000))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val plainBytes = try {
            cipher.doFinal(cipherBytes)
        } catch (_: Exception) {
            throw IllegalArgumentException("كلمة المرور غير صحيحة أو الملف تالف")
        }
        String(plainBytes, Charsets.UTF_8)
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
