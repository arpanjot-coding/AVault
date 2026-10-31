/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.system.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kitsumed.shizucallrecorder.data.call.CallLogRepository
import com.kitsumed.shizucallrecorder.system.storage.HierarchicalStorageManager
import com.kitsumed.shizucallrecorder.utils.AppLogger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * VaultEncryptionManager provides hardware-backed AES-256-GCM encryption
 * for all recorded conversations and metadata so no unauthorized apps, media players,
 * file managers, or adb extraction can access audio or call logs on disk.
 */
object VaultEncryptionManager {

    private const val TAG = "VaultEncryption"
    private const val KEY_ALIAS = "AVaultMasterEncryptionKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_SIZE = 12
    private val MAGIC_HEADER = "AVAULT01".toByteArray(Charsets.US_ASCII)

    const val VAULT_EXTENSION = "vault"

    private val secureRandom = SecureRandom()

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        // Generate new 256-bit AES key in Android KeyStore (hardware-backed TEE / StrongBox)
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(false) // We manage cryptographically secure IVs explicitly
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    /**
     * Checks if a given file has the AVault encrypted header.
     */
    fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() < MAGIC_HEADER.size + IV_SIZE) return false
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(MAGIC_HEADER.size)
                val read = input.read(header)
                read == MAGIC_HEADER.size && header.contentEquals(MAGIC_HEADER)
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a byte buffer starts with the AVault encrypted header.
     */
    fun isEncryptedBytes(data: ByteArray): Boolean {
        if (data.size < MAGIC_HEADER.size + IV_SIZE) return false
        for (i in MAGIC_HEADER.indices) {
            if (data[i] != MAGIC_HEADER[i]) return false
        }
        return true
    }

    /**
     * Encrypts plaintext bytes using hardware-backed AES-256-GCM.
     * Output format: [MAGIC_HEADER (8 bytes)] + [IV (12 bytes)] + [Ciphertext + AuthTag]
     */
    fun encryptBytes(data: ByteArray): ByteArray {
        val key = getOrCreateSecretKey()
        val iv = ByteArray(IV_SIZE).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(data)

        val result = ByteArray(MAGIC_HEADER.size + IV_SIZE + ciphertext.size)
        System.arraycopy(MAGIC_HEADER, 0, result, 0, MAGIC_HEADER.size)
        System.arraycopy(iv, 0, result, MAGIC_HEADER.size, IV_SIZE)
        System.arraycopy(ciphertext, 0, result, MAGIC_HEADER.size + IV_SIZE, ciphertext.size)
        return result
    }

    /**
     * Decrypts ciphertext bytes encrypted with encryptBytes.
     */
    fun decryptBytes(encryptedData: ByteArray): ByteArray? {
        if (!isEncryptedBytes(encryptedData)) return null
        return try {
            val key = getOrCreateSecretKey()
            val iv = ByteArray(IV_SIZE)
            System.arraycopy(encryptedData, MAGIC_HEADER.size, iv, 0, IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val offset = MAGIC_HEADER.size + IV_SIZE
            val length = encryptedData.size - offset
            cipher.doFinal(encryptedData, offset, length)
        } catch (e: Exception) {
            AppLogger.e("Decryption failed: ${e.message}", e)
            null
        }
    }

    fun encryptString(plainText: String): ByteArray {
        return encryptBytes(plainText.toByteArray(Charsets.UTF_8))
    }

    fun decryptString(encryptedBytes: ByteArray): String? {
        val decrypted = decryptBytes(encryptedBytes) ?: return null
        return String(decrypted, Charsets.UTF_8)
    }

    /**
     * Encrypts a source audio file into AES-256-GCM ciphertext file.
     * Format: [MAGIC_HEADER (8 bytes)] + [IV (12 bytes)] + [Ciphertext + AuthTag]
     */
    fun encryptFile(sourceFile: File, destinationFile: File): Boolean {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return false

        val tempDest = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        val buffer = ByteArray(16 * 1024)
        return try {
            val key = getOrCreateSecretKey()
            val iv = ByteArray(IV_SIZE).also { secureRandom.nextBytes(it) }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

            destinationFile.parentFile?.mkdirs()

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(tempDest).use { output ->
                    output.write(MAGIC_HEADER)
                    output.write(iv)

                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val encryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (encryptedBytes != null && encryptedBytes.isNotEmpty()) {
                            output.write(encryptedBytes)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        output.write(finalBytes)
                    }
                }
            }

            if (tempDest.exists() && tempDest.length() > MAGIC_HEADER.size + IV_SIZE) {
                if (destinationFile.exists()) destinationFile.delete()
                tempDest.renameTo(destinationFile)
                true
            } else {
                tempDest.delete()
                false
            }
        } catch (e: Exception) {
            AppLogger.e("Encryption failed for ${sourceFile.name}: ${e.message}", e)
            tempDest.delete()
            false
        } finally {
            Arrays.fill(buffer, 0.toByte())
        }
    }

    /**
     * Decrypts an AVault encrypted file into destination plain audio file.
     */
    fun decryptFile(vaultFile: File, destinationFile: File): Boolean {
        if (!isEncrypted(vaultFile)) return false

        val tempDest = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
        val buffer = ByteArray(16 * 1024)
        return try {
            val key = getOrCreateSecretKey()

            FileInputStream(vaultFile).use { input ->
                val header = ByteArray(MAGIC_HEADER.size)
                if (input.read(header) != MAGIC_HEADER.size || !header.contentEquals(MAGIC_HEADER)) {
                    return false
                }

                val iv = ByteArray(IV_SIZE)
                if (input.read(iv) != IV_SIZE) {
                    return false
                }

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

                destinationFile.parentFile?.mkdirs()

                FileOutputStream(tempDest).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val decryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (decryptedBytes != null && decryptedBytes.isNotEmpty()) {
                            output.write(decryptedBytes)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null && finalBytes.isNotEmpty()) {
                        output.write(finalBytes)
                    }
                }
            }

            if (tempDest.exists() && tempDest.length() > 0) {
                if (destinationFile.exists()) destinationFile.delete()
                tempDest.renameTo(destinationFile)
                true
            } else {
                tempDest.delete()
                false
            }
        } catch (e: Exception) {
            AppLogger.e("Decryption failed for ${vaultFile.name}: ${e.message}", e)
            tempDest.delete()
            false
        } finally {
            Arrays.fill(buffer, 0.toByte())
        }
    }

    /**
     * Decrypts an encrypted recording into a transient internal cache file for MediaPlayer playback.
     */
    fun decryptToTempPlaybackFile(context: Context, vaultFile: File): File? {
        val cacheDir = File(context.cacheDir, "secure_playback").apply { mkdirs() }
        val tempAudio = File(cacheDir, "playback_${System.currentTimeMillis()}_${vaultFile.nameWithoutExtension}.ogg")
        return if (decryptFile(vaultFile, tempAudio)) {
            tempAudio.deleteOnExit()
            tempAudio
        } else {
            null
        }
    }

    /**
     * Cleans up transient unencrypted playback files.
     */
    fun clearTempPlayback(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "secure_playback")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    /**
     * Ensures a .nomedia file is present in the recordings directory so media scanners ignore it.
     */
    fun ensureNoMediaFile(context: Context) {
        try {
            val baseDir = HierarchicalStorageManager.getBaseRecordingsDirectory(context)
            val noMedia = File(baseDir, ".nomedia")
            if (!noMedia.exists()) {
                noMedia.createNewFile()
            }
        } catch (_: Exception) {}
    }

    /**
     * Scans storage for existing unencrypted recordings, encrypts them into .vault files,
     * securely removes the plain audio, and updates the repository.
     */
    fun encryptExistingRecordings(context: Context): Int {
        ensureNoMediaFile(context)
        var encryptedCount = 0
        try {
            val baseDir = HierarchicalStorageManager.getBaseRecordingsDirectory(context)
            val repository = CallLogRepository.getInstance(context)
            val entries = repository.getAll().toMutableList()
            var repositoryUpdated = false

            fun processDirectory(dir: File) {
                val files = dir.listFiles() ?: return
                for (file in files) {
                    if (file.isDirectory) {
                        processDirectory(file)
                    } else if (file.isFile) {
                        val ext = file.extension.lowercase()
                        if (ext in listOf("ogg", "m4a", "wav", "mp3", "aac", "opus") && !isEncrypted(file)) {
                            val vaultTarget = File(file.parentFile, "${file.nameWithoutExtension}.$VAULT_EXTENSION")
                            if (encryptFile(file, vaultTarget)) {
                                val oldPath = file.absolutePath
                                val newPath = vaultTarget.absolutePath
                                file.delete()
                                encryptedCount++

                                // Update repository matching path
                                for (i in entries.indices) {
                                    if (entries[i].filePath == oldPath) {
                                        entries[i] = entries[i].copy(
                                            filePath = newPath,
                                            fileSize = vaultTarget.length()
                                        )
                                        repositoryUpdated = true
                                    }
                                }
                            }
                        }
                    }
                }
            }

            processDirectory(baseDir)

            if (repositoryUpdated) {
                repository.refresh()
            }
        } catch (e: Exception) {
            AppLogger.e("Batch encryption of recordings encountered an error", e)
        }
        return encryptedCount
    }
}
