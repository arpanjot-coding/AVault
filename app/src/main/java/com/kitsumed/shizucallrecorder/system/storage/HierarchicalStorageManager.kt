/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.system.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.kitsumed.shizucallrecorder.data.call.CallDirection
import com.kitsumed.shizucallrecorder.data.call.EnrichedCallData
import com.kitsumed.shizucallrecorder.data.recording.RecordingCodec
import com.kitsumed.shizucallrecorder.utils.AppLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HierarchicalStorageManager {

    data class OutputResult(
        val file: File,
        val uri: Uri,
        val descriptor: ParcelFileDescriptor,
        val displayName: String
    )

    fun getBaseRecordingsDirectory(context: Context): File {
        // Option A: Primary AVault folder on external storage with seamless legacy migration
        val avaultDir = File(Environment.getExternalStorageDirectory(), "Recordings/AVault")
        val legacyDir = File(Environment.getExternalStorageDirectory(), "Recordings/ShizuCalls")
        if (legacyDir.exists()) {
            if (!avaultDir.exists()) avaultDir.mkdirs()
            legacyDir.listFiles()?.forEach { file ->
                val target = File(avaultDir, file.name)
                if (!target.exists()) {
                    file.renameTo(target)
                }
            }
            if (legacyDir.listFiles()?.isEmpty() == true) {
                legacyDir.delete()
            }
        }
        if (avaultDir.exists() || avaultDir.mkdirs()) {
            return avaultDir
        }

        // Option B: App-specific external recordings directory (always writable on Android 11-16)
        val extDir = context.getExternalFilesDir(Environment.DIRECTORY_RECORDINGS)
        if (extDir != null && (extDir.exists() || extDir.mkdirs())) {
            return extDir
        }

        // Option C: Internal storage fallback
        val fallback = File(context.filesDir, "Recordings")
        fallback.mkdirs()
        return fallback
    }

    fun resolveAppType(metadata: EnrichedCallData): String {
        val pkg = metadata.packageName?.lowercase(Locale.ROOT) ?: ""
        return when {
            pkg.contains("whatsapp") -> "WhatsApp"
            pkg.contains("telegram") -> "Telegram"
            pkg.contains("signal") -> "Signal"
            pkg.contains("viber") -> "Viber"
            pkg.contains("skype") -> "Skype"
            metadata.isCallAudioVoIpBased -> "VoIP"
            else -> "Cellular"
        }
    }

    private fun sanitizePathComponent(raw: String): String {
        val sanitized = raw.replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim()
        return sanitized.ifBlank { "Unknown" }
    }

    fun getDestinationFile(
        context: Context,
        metadata: EnrichedCallData,
        codec: RecordingCodec
    ): File {
        val baseDir = getBaseRecordingsDirectory(context)
        val appType = resolveAppType(metadata)

        val contactName = metadata.callerName?.takeIf { it.isNotBlank() }
            ?: metadata.getBestNumber().takeIf { it.isNotBlank() }
            ?: "Unknown"
        val safeContact = sanitizePathComponent(contactName)

        val now = Date()
        val monthFolder = SimpleDateFormat("yyyy-MM", Locale.US).format(now)
        val datePrefix = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(now)

        val directionStr = if (metadata.direction == CallDirection.OUTGOING) "OUTGOING" else "INCOMING"
        val extension = codec.containerExtension.trimStart('.')

        val targetDir = File(baseDir, "$appType/$safeContact/$monthFolder")
        targetDir.mkdirs()

        val fileName = "${datePrefix}_${directionStr}_${safeContact}.$extension"
        return File(targetDir, fileName)
    }

    fun createRecordingFile(
        context: Context,
        metadata: EnrichedCallData,
        codec: RecordingCodec
    ): OutputResult? {
        return try {
            val file = getDestinationFile(context, metadata, codec)
            file.parentFile?.mkdirs()
            if (file.exists()) {
                file.delete()
            }
            file.createNewFile()

            val pfd = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_WRITE
            ) ?: return null

            val uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Uri.fromFile(file)
            }

            AppLogger.i("Hierarchical storage allocated: ${file.absolutePath}")
            OutputResult(
                file = file,
                uri = uri,
                descriptor = pfd,
                displayName = "${resolveAppType(metadata)}/${file.name}"
            )
        } catch (e: Exception) {
            AppLogger.e("Failed to create hierarchical audio file", e)
            null
        }
    }

    fun getShareableUri(context: Context, file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }
}
