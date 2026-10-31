/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.services.callDetection.phoneState

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.kitsumed.shizucallrecorder.utils.AppLogger

/**
 * PhoneStateReceiver is a [BroadcastReceiver] that listens for phone call state changes INTENTS.
 * It extracts the relevant extras from the intent and forwards them to [PhoneStateSessionManager], which owns the recording decision logic.
 *
 * It is registered in AndroidManifest.xml to receive [TelephonyManager.ACTION_PHONE_STATE_CHANGED]
 * broadcasts. Android delivers this broadcast whenever a call starts ringing, is answered, or ends.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                AppLogger.d("PhoneStateReceiver: Non-matching action: ${intent.action}. Ignored.")
                return
            }
            // EXTRA_STATE is one of "IDLE", "RINGING", or "OFFHOOK".
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            if (state.isNullOrBlank()) {
                AppLogger.w("PhoneStateReceiver: Received intent missing EXTRA_STATE. Ignored safely.")
                return
            }

            // EXTRA_INCOMING_NUMBER is set for both incoming and outgoing calls
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            AppLogger.v("Raw broadcast received: state=$state number=$number")

            // Forward the phone state and number to the session manager.
            PhoneStateSessionManager.getInstance(context).handlePhoneState(state, number)
        } catch (e: Exception) {
            AppLogger.e("PhoneStateReceiver: Safely handled error processing phone state broadcast: ${e.message}", e)
        }
    }
}
