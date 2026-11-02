package com.kitsumed.shizucallrecorder.screenshots

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.data.call.CallLogEntry
import com.kitsumed.shizucallrecorder.data.call.CallLogRepository
import com.kitsumed.shizucallrecorder.system.security.AppLockManager
import com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager
import com.kitsumed.shizucallrecorder.ui.screens.*
import com.kitsumed.shizucallrecorder.ui.theme.AVaultTheme
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Sample-only build, in its own application sandbox. No real contacts or audio. */
class ScreenshotActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = AppPreferences(this)
        prefs.setThemeMode(AppPreferences.ThemeMode.DARK)
        prefs.setBiometricLockEnabled(false)
        prefs.setAutoDeleteEnabled(true)
        prefs.setAutoDeleteDays(30)
        AppLockManager.init(this)
        AppLockManager.setPin(this, "2468")
        val repository = CallLogRepository.getInstance(this)
        if (repository.getAll().isEmpty()) {
            val wav = ByteBuffer.allocate(44 + 32000).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + 32000)
                put("WAVEfmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(16000); putInt(32000); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(32000)
                repeat(16000) { putShort(0) }
            }.array()
            val now = System.currentTimeMillis()
            listOf("Alex Morgan", "Jamie Chen", "Studio North", "Sam Taylor").withIndex().reversed().forEach { (i, name) ->
                val file = File(filesDir, "sample_$i.vault")
                file.writeBytes(VaultEncryptionManager.encryptBytes(wav))
                repository.add(CallLogEntry(id = "sample-$i", contactName = name,
                    phoneNumberOrSource = "Sample recording", appType = if (i == 1) "WhatsApp" else "Cellular",
                    timestamp = now - (i + 1) * 3600000L,
                    durationSeconds = listOf(142L, 85L, 310L, 64L)[i],
                    filePath = file.absolutePath, fileSize = file.length(),
                    callDirection = if (i % 2 == 0) "INCOMING" else "OUTGOING",
                    audioRoute = "Sample", isBookmarked = i == 0))
            }
        }
        val initial = intent.getStringExtra("screen") ?: "library"
        setContent {
            var screen by remember { mutableStateOf(initial) }
            AVaultTheme(darkTheme = intent.getBooleanExtra("dark", true)) {
                when (screen) {
                    "settings" -> SettingsScreen(viewModel = viewModel(), onBack = { screen = "library" })
                    "lock" -> LockScreen(onUnlocked = { screen = "library" })
                    else -> CallsDashboardScreen(onNavigateToSettings = { screen = "settings" })
                }
            }
        }
    }
}
