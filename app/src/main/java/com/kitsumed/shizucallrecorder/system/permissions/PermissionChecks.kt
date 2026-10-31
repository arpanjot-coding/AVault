/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.system.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.kitsumed.shizucallrecorder.utils.AppLogger

/**
 * PermissionChecks centralises all runtime permission queries used throughout the app.
 */
object PermissionChecks {

    /**
     * Returns true if the app is allowed to post notifications.
     *
     * @param context The app context.
     * @return true if the app can post notifications.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Returns true if [Manifest.permission.READ_CONTACTS] is granted.
     *
     * Required for:
     *  - [ContactLookup.isKnownContact] queries against the Contacts provider.
     *  - Loading the contact list in the picker dialog.
     *
     * @param context The app context.
     * @return true if the contacts permission is currently granted.
     */
    fun hasContactsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true if the app is exempt from Android's battery-optimisation restrictions.
     *
     * @param context The app context.
     * @return true if the app is on the battery-optimisation whitelist (or if the API is unavailable).
     */
    fun hasBatteryExemption(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Automatically ensures that the app is exempted from battery optimizations.
     * If not already exempted, fires the system battery optimization exemption request.
     */
    fun ensureBatteryExemption(context: Context) {
        if (hasBatteryExemption(context)) return

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            AppLogger.i("Automated battery optimization exemption request triggered")
        } catch (e: Throwable) {
            AppLogger.e("Failed to request battery exemption intent", e)
        }
    }

    /**
     * Returns true if the app is allowed to draw overlays on top of other apps.
     *
     * @param context The app context.
     * @return true if the app can draw overlays.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
