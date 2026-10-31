/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.data.call

import android.content.Context
import com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager
import com.kitsumed.shizucallrecorder.system.storage.HierarchicalStorageManager
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CallLogRepository(private val context: Context) {

    companion object {
        private const val TAG = "CallLogRepository"
        private const val FILE_NAME = "call_logs.json"

        @Volatile
        private var instance: CallLogRepository? = null

        fun getInstance(context: Context): CallLogRepository {
            return instance ?: synchronized(this) {
                instance ?: CallLogRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val logFile: File
        get() = File(context.filesDir, FILE_NAME)

    private val _entriesFlow = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val entriesFlow: StateFlow<List<CallLogEntry>> = _entriesFlow.asStateFlow()

    init {
        refresh()
    }

    @Synchronized
    fun refresh() {
        val current = loadFromDisk()
        _entriesFlow.value = current
    }

    @Synchronized
    fun getAll(): List<CallLogEntry> {
        return _entriesFlow.value
    }

    @Synchronized
    fun add(entry: CallLogEntry) {
        val current = loadFromDisk().toMutableList()
        current.removeAll { it.id == entry.id }
        current.add(0, entry)
        saveToDisk(current)
        _entriesFlow.value = current
    }

    @Synchronized
    fun updateAll(updatedList: List<CallLogEntry>) {
        saveToDisk(updatedList)
        _entriesFlow.value = updatedList
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val current = loadFromDisk().toMutableList()
        val item = current.find { it.id == id }
        if (item != null) {
            try {
                val f = item.getEffectiveFile()
                if (f.exists()) {
                    f.delete()
                }
                val raw = File(item.filePath)
                if (raw.exists()) {
                    raw.delete()
                }
            } catch (e: Exception) {
                AppLogger.w("Failed to delete audio file: ${item.filePath}", e)
            }
            current.remove(item)
            saveToDisk(current)
            _entriesFlow.value = current
            return true
        }
        return false
    }

    @Synchronized
    fun toggleBookmark(id: String): Boolean {
        val current = loadFromDisk().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = current[index]
            val updated = item.copy(isBookmarked = !item.isBookmarked)
            current[index] = updated
            saveToDisk(current)
            _entriesFlow.value = current
            AppLogger.i("CallLogRepository: Toggled bookmark for ${item.contactName} -> ${updated.isBookmarked}")
            return updated.isBookmarked
        }
        return false
    }

    @Synchronized
    fun purgeExpiredRecordings(retentionDays: Int): Int {
        if (retentionDays <= 0) return 0
        val cutoffTimestamp = System.currentTimeMillis() - (retentionDays.toLong() * 24 * 60 * 60 * 1000L)
        val current = loadFromDisk().toMutableList()
        var deletedCount = 0

        val iterator = current.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            // Bookmarked calls are explicitly exempt and protected from auto-deletion
            if (!entry.isBookmarked && entry.timestamp < cutoffTimestamp) {
                try {
                    val f = entry.getEffectiveFile()
                    if (f.exists()) f.delete()
                    val raw = File(entry.filePath)
                    if (raw.exists()) raw.delete()
                    iterator.remove()
                    deletedCount++
                    AppLogger.i("Auto-deleted expired recording: ${entry.contactName} (${entry.getFormattedDate()})")
                } catch (e: Exception) {
                    AppLogger.w("Failed auto-deleting recording: ${entry.filePath}", e)
                }
            }
        }

        if (deletedCount > 0) {
            saveToDisk(current)
            _entriesFlow.value = current
            AppLogger.i("CallLogRepository: Auto-delete purged $deletedCount recording(s) older than $retentionDays day(s)")
        }
        return deletedCount
    }

    @Synchronized
    private fun loadFromDisk(): List<CallLogEntry> {
        val file = logFile
        if (!file.exists() || file.length() == 0L) return emptyList()

        return try {
            val bytes = file.readBytes()
            val jsonStr = if (VaultEncryptionManager.isEncryptedBytes(bytes)) {
                VaultEncryptionManager.decryptString(bytes) ?: ""
            } else {
                // Backward compatibility: existing plaintext JSON
                val plain = String(bytes, Charsets.UTF_8)
                // Transparently migrate and encrypt existing plaintext call logs immediately
                try {
                    val encryptedBytes = VaultEncryptionManager.encryptString(plain)
                    file.writeBytes(encryptedBytes)
                    AppLogger.i("CallLogRepository: Successfully migrated plaintext call logs to AES-256-GCM ciphertext")
                } catch (e: Exception) {
                    AppLogger.w("CallLogRepository: Failed auto-encrypting legacy logs: ${e.message}")
                }
                plain
            }

            if (jsonStr.isBlank()) return emptyList()

            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<CallLogEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val entry = CallLogEntry(
                    id = obj.optString("id"),
                    contactName = obj.optString("contactName", "Unknown"),
                    phoneNumberOrSource = obj.optString("phoneNumberOrSource", ""),
                    appType = obj.optString("appType", "Cellular"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    durationSeconds = obj.optLong("durationSeconds", 0L),
                    filePath = obj.optString("filePath", ""),
                    fileSize = obj.optLong("fileSize", 0L),
                    callDirection = obj.optString("callDirection", "INCOMING"),
                    audioRoute = obj.optString("audioRoute", "Speaker"),
                    isBookmarked = obj.optBoolean("isBookmarked", false)
                )
                if (entry.fileExists()) {
                    list.add(entry)
                }
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            AppLogger.e("Failed to load call logs: ${e.message}", e)
            emptyList()
        }
    }

    @Synchronized
    private fun saveToDisk(list: List<CallLogEntry>) {
        try {
            val jsonArray = JSONArray()
            for (entry in list) {
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("contactName", entry.contactName)
                    put("phoneNumberOrSource", entry.phoneNumberOrSource)
                    put("appType", entry.appType)
                    put("timestamp", entry.timestamp)
                    put("durationSeconds", entry.durationSeconds)
                    put("filePath", entry.filePath)
                    put("fileSize", entry.fileSize)
                    put("callDirection", entry.callDirection)
                    put("audioRoute", entry.audioRoute)
                    put("isBookmarked", entry.isBookmarked)
                }
                jsonArray.put(obj)
            }
            val plainJson = jsonArray.toString(2)
            val encryptedBytes = VaultEncryptionManager.encryptString(plainJson)
            logFile.writeBytes(encryptedBytes)
        } catch (e: Exception) {
            AppLogger.e("Failed to save call logs: ${e.message}", e)
        }
    }
}
