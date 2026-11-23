package com.example.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.FinanceAccountEntity
import com.example.data.local.FinanceTransactionEntity
import com.example.data.local.OfferEntity
import com.example.data.local.OrderEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.ScheduledSmsEntity
import com.example.data.local.SenderRuleEntity
import com.example.data.local.SmsMessageEntity
import com.example.data.local.UserPreferences
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@JsonClass(generateAdapter = true)
data class KmsBackupPayload(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val messages: List<SmsMessageEntity>,
    val senderRules: List<SenderRuleEntity>,
    val accounts: List<FinanceAccountEntity>,
    val transactions: List<FinanceTransactionEntity>,
    val reminders: List<ReminderEntity>,
    val orders: List<OrderEntity>,
    val offers: List<OfferEntity>,
    val scheduledMessages: List<ScheduledSmsEntity>,
    val preferences: UserPreferences
)

/**
 * Passphrase-encrypted AES-256-GCM (.kmsbackup) file exporter/importer via Android SAF.
 * File format: [MAGIC 4 bytes "KMS1"][SALT 16 bytes][IV 12 bytes][AES-GCM CIPHERTEXT + TAG]
 */
class EncryptedBackupManager(private val context: Context) {

    private val magic = byteArrayOf('K'.code.toByte(), 'M'.code.toByte(), 'S'.code.toByte(), '1'.code.toByte())
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(KmsBackupPayload::class.java)

    fun exportEncryptedBackup(uri: Uri, passphrase: String, payload: KmsBackupPayload): Result<Int> {
        if (passphrase.length < 4) {
            return Result.failure(IllegalArgumentException("Passphrase must be at least 4 characters."))
        }
        return try {
            val jsonBytes = adapter.toJson(payload).toByteArray(Charsets.UTF_8)
            val random = SecureRandom()
            val salt = ByteArray(16).also { random.nextBytes(it) }
            val iv = ByteArray(12).also { random.nextBytes(it) }
            val key = deriveAesKey(passphrase, salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(jsonBytes)

            val outStream = ByteArrayOutputStream(4 + salt.size + iv.size + encrypted.size)
            outStream.write(magic)
            outStream.write(salt)
            outStream.write(iv)
            outStream.write(encrypted)

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(outStream.toByteArray())
                os.flush()
            } ?: return Result.failure(IllegalStateException("Unable to open destination file for writing."))

            Result.success(payload.messages.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importEncryptedBackup(uri: Uri, passphrase: String): Result<KmsBackupPayload> {
        if (passphrase.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter the backup passphrase."))
        }
        return try {
            val allBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalStateException("Unable to read selected backup file."))

            if (allBytes.size < 4 + 16 + 12 + 16) {
                return Result.failure(IllegalArgumentException("Invalid or corrupted KMS-MS backup file."))
            }

            val buffer = ByteBuffer.wrap(allBytes)
            val fileMagic = ByteArray(4).also { buffer.get(it) }
            if (!fileMagic.contentEquals(magic)) {
                return Result.failure(IllegalArgumentException("File is not a valid KMS-MS encrypted backup."))
            }

            val salt = ByteArray(16).also { buffer.get(it) }
            val iv = ByteArray(12).also { buffer.get(it) }
            val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }

            val key = deriveAesKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(ciphertext)

            val json = String(plainBytes, Charsets.UTF_8)
            val payload = adapter.fromJson(json)
                ?: return Result.failure(IllegalStateException("Backup contents could not be parsed."))

            Result.success(payload)
        } catch (e: javax.crypto.AEADBadTagException) {
            Result.failure(IllegalArgumentException("Incorrect passphrase or tampered backup file."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, 120_000, 256)
        val encoded = factory.generateSecret(spec).encoded
        return SecretKeySpec(encoded, "AES")
    }
}
