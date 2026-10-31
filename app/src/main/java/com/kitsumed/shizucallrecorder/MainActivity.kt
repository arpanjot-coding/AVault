/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.system.permissions.PermissionChecks
import com.kitsumed.shizucallrecorder.system.security.AppLockManager
import com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MainActivity is the single Android Activity entry point for AVault.
 * This is called when Android wants to show the application UI to the user.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appPreferences = AppPreferences(this)

        // Initialize AppLock state
        AppLockManager.init(this)

        // Manage 'ignore battery' automatically by itself
        PermissionChecks.ensureBatteryExemption(this)

        // Apply screen privacy if enabled
        applyWindowSecurityFlags()

        // Automatically encrypt any legacy or existing plain audio recordings in the background
        lifecycleScope.launch(Dispatchers.IO) {
            VaultEncryptionManager.encryptExistingRecordings(applicationContext)
        }

        // Automatically purge expired recordings if auto-delete is enabled
        if (appPreferences.isAutoDeleteEnabled()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val days = appPreferences.getAutoDeleteDays()
                com.kitsumed.shizucallrecorder.data.call.CallLogRepository.getInstance(applicationContext).purgeExpiredRecordings(days)
            }
        }

        if (BuildConfig.DEBUG) {
            appPreferences.setAppLockEnabled(true)
            if (!AppLockManager.hasPin(this)) {
                AppLockManager.setPin(this, "1234")
            }
            val repo = com.kitsumed.shizucallrecorder.data.call.CallLogRepository.getInstance(applicationContext)
            if (repo.getAll().isEmpty()) {
                val dir = java.io.File(filesDir, "Recordings").apply { mkdirs() }
                val now = System.currentTimeMillis()
                val f3 = java.io.File(dir, "call_legal.vault").apply { if (!exists()) writeBytes("AVAULT01demo".toByteArray()) }
                val f2 = java.io.File(dir, "call_michael.vault").apply { if (!exists()) writeBytes("AVAULT01demo".toByteArray()) }
                val f1 = java.io.File(dir, "call_sarah.vault").apply { if (!exists()) writeBytes("AVAULT01demo".toByteArray()) }
                repo.add(com.kitsumed.shizucallrecorder.data.call.CallLogEntry(
                    id = "vault-03",
                    contactName = "Apex Legal Advisory",
                    phoneNumberOrSource = "+44 20 7946 0912",
                    appType = "Cellular",
                    timestamp = now - 28800000,
                    durationSeconds = 310,
                    filePath = f3.absolutePath,
                    fileSize = 2480000,
                    callDirection = "INCOMING",
                    audioRoute = "Bluetooth",
                    isBookmarked = true
                ))
                repo.add(com.kitsumed.shizucallrecorder.data.call.CallLogEntry(
                    id = "vault-02",
                    contactName = "Michael Scott",
                    phoneNumberOrSource = "+1 (555) 890-1234",
                    appType = "WhatsApp",
                    timestamp = now - 7200000,
                    durationSeconds = 85,
                    filePath = f2.absolutePath,
                    fileSize = 680000,
                    callDirection = "OUTGOING",
                    audioRoute = "Speaker",
                    isBookmarked = false
                ))
                repo.add(com.kitsumed.shizucallrecorder.data.call.CallLogEntry(
                    id = "vault-01",
                    contactName = "Sarah Jenkins",
                    phoneNumberOrSource = "+1 (555) 234-8901",
                    appType = "Cellular",
                    timestamp = now - 1800000,
                    durationSeconds = 142,
                    filePath = f1.absolutePath,
                    fileSize = 1136000,
                    callDirection = "INCOMING",
                    audioRoute = "Earpiece",
                    isBookmarked = true
                ))
            }
        }

        setContent {
            AppNavigationScreen()
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure battery exemption is kept active
        PermissionChecks.ensureBatteryExemption(this)
        // Verify lock state on resume
        AppLockManager.onAppForegrounded(this)
        applyWindowSecurityFlags()
    }

    override fun onPause() {
        super.onPause()
        // Mark background time for auto-lock
        AppLockManager.onAppBackgrounded()
        // Wipe any transient unencrypted playback files from cache
        VaultEncryptionManager.clearTempPlayback(this)
    }

    private fun applyWindowSecurityFlags() {
        if (!BuildConfig.DEBUG && appPreferences.isAppLockEnabled() && appPreferences.isHideRecentsContentEnabled()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
