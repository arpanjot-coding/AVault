/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.data.call

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CallLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val contactName: String,
    val phoneNumberOrSource: String,
    val appType: String, // "Cellular", "WhatsApp", "Telegram", "Signal", "VoIP"
    val timestamp: Long,
    val durationSeconds: Long,
    val filePath: String,
    val fileSize: Long,
    val callDirection: String = "INCOMING", // "INCOMING", "OUTGOING", "MISSED"
    val audioRoute: String = "Speaker",
    val isBookmarked: Boolean = false
) {
    fun isOutgoing(): Boolean = callDirection.equals("OUTGOING", ignoreCase = true)
    fun isIncoming(): Boolean = !isOutgoing()

    fun getFormattedTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    fun getFormattedDate(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = sdfTime.format(Date(timestamp))

        return when {
            diff < 24 * 60 * 60 * 1000L -> timeStr
            diff < 48 * 60 * 60 * 1000L -> "Yesterday, $timeStr"
            else -> {
                val sdfDate = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                sdfDate.format(Date(timestamp))
            }
        }
    }

    fun getDateGroup(): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 24 * 60 * 60 * 1000L -> "Today"
            diff < 48 * 60 * 60 * 1000L -> "Yesterday"
            else -> {
                val sdfDate = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                sdfDate.format(Date(timestamp))
            }
        }
    }

    fun getFormattedDuration(): String {
        if (durationSeconds <= 0) return "< 5s"
        val min = durationSeconds / 60
        val sec = durationSeconds % 60
        return String.format(Locale.US, "%02d:%02d", min, sec)
    }

    fun getFormattedSize(): String {
        val mb = fileSize / (1024.0 * 1024.0)
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val kb = fileSize / 1024.0
            String.format(Locale.US, "%.0f KB", kb)
        }
    }

    fun getAvatarInitial(): String {
        val trimmed = contactName.trim()
        return if (trimmed.isNotEmpty()) {
            trimmed.take(1).uppercase(Locale.getDefault())
        } else "?"
    }

    fun fileExists(): Boolean {
        val f = File(filePath)
        if (f.exists()) return true
        val vault = File(f.parentFile, "${f.nameWithoutExtension}.vault")
        return vault.exists()
    }

    fun getEffectiveFile(): File {
        val f = File(filePath)
        if (f.exists()) return f
        val vault = File(f.parentFile, "${f.nameWithoutExtension}.vault")
        if (vault.exists()) return vault
        return f
    }
}
