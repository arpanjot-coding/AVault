/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder

import android.app.Application
import com.kitsumed.shizucallrecorder.services.callDetection.CallDetectionOrchestrator
import com.kitsumed.shizucallrecorder.utils.AppLogger

/**
 * AVaultApplication is run when the app process is created. Can be seen as the very first entry point of the app.
 */
class AVaultApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(applicationContext)
        // Sync configurations down to PackageManager mapping immediately on launch
        CallDetectionOrchestrator(applicationContext).syncComponents()
    }
}