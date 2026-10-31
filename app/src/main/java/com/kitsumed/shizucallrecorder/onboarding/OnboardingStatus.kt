/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.onboarding

import android.content.Context
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.integrations.shizuku.ShizukuConnectionManager
import com.kitsumed.shizucallrecorder.services.callDetection.CallDetectionMode
import com.kitsumed.shizucallrecorder.system.permissions.AppPermission
import com.kitsumed.shizucallrecorder.system.permissions.PermissionChecks
import com.kitsumed.shizucallrecorder.ui.viewmodels.AppNavigationViewModel

/**
 * OnboardingStatus aggregates all permission and setup states needed for the app to operate.
 *
 * Used by [AppNavigationViewModel] to decide which screen to show
 */
object OnboardingStatus {

    /**
     * An object that contains the state of every prerequisite (permissions) the app requires to work.
     *
     * @param disclaimerAccepted        True if the user has accepted the app disclaimer.
     * @param notificationsGranted      True if the app can post notifications.
     * @param contactsGranted           True if READ_CONTACTS is granted.
     * @param batteryExempted           True if the app is on the battery-optimisation whitelist.
     * @param storageSelected           True if storage is ready (automatically configured).
     * @param shizukuRunning            True if the Shizuku service is currently active or ShizukuAutoManage was enabled in the app settings.
     * @param shizukuPermissionGranted  True if the user has granted Shizuku permission to this app.
     * @param callDetectionMode         The currently selected call detection mode.
     * @param callDetectionModeGrantedPermissions True if the app has all permissions required for the current call detection mode.
     */
    data class Status(
        val disclaimerAccepted: Boolean,
        val notificationsGranted: Boolean,
        val contactsGranted: Boolean,
        val batteryExempted: Boolean,
        val storageSelected: Boolean,
        val shizukuRunning: Boolean,
        val shizukuPermissionGranted: Boolean,
        val callDetectionMode: CallDetectionMode,
        val callDetectionModeGrantedPermissions: Set<AppPermission>
    ) {
        /**
         * Returns true only when every prerequisite is satisfied, including the disclaimer.
         */
        fun isComplete(): Boolean {
            val globalsMet = disclaimerAccepted && notificationsGranted &&
                    contactsGranted && batteryExempted &&
                    storageSelected && shizukuRunning && shizukuPermissionGranted

            val modeMet = callDetectionModeGrantedPermissions.containsAll(callDetectionMode.requiredPermissions)

            return globalsMet && modeMet
        }
    }

    /**
     * Reads the current state of permissions and other requirements and returns a [Status] data class/object.
     *
     * @param context     App context used for permission checks.
     * @param preferences The app-wide [AppPreferences] to perform checks based on user app settings.
     * @return A fully populated [Status] reflecting the current device state.
     */
    fun getStatus(context: Context, preferences: AppPreferences): Status {
        val currentCallDetectionMode = preferences.getCallDetectionMode()

        val currentModeDynamicallyGrantedList = currentCallDetectionMode.requiredPermissions.filterTo(mutableSetOf()) {
            it.isGranted(context)
        }

        return Status(
            disclaimerAccepted       = preferences.isDisclaimerAccepted(),
            notificationsGranted     = PermissionChecks.hasNotificationPermission(context),
            contactsGranted          = PermissionChecks.hasContactsPermission(context),
            batteryExempted          = PermissionChecks.hasBatteryExemption(context),
            // Automated hierarchical storage is always ready and requires zero user SAF prompt
            storageSelected          = true,
            shizukuRunning           = ShizukuConnectionManager.isAvailable() || preferences.isShizukuAutoManageEnabled(),
            shizukuPermissionGranted = ShizukuConnectionManager.hasPermission(context),
            callDetectionMode        = currentCallDetectionMode,
            callDetectionModeGrantedPermissions = currentModeDynamicallyGrantedList
        )
    }
}
