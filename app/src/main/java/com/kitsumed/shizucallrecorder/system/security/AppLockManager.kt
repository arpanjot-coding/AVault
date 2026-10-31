/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.system.security

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AppLockManager {

    private const val TAG = "AppLockManager"
    private const val PBKDF2_ITERATIONS = 150_000
    private const val KEY_LENGTH_BITS = 256

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var lastBackgroundTimeMs: Long = 0L

    fun init(context: Context) {
        val prefs = AppPreferences(context)
        if (!prefs.isAppLockEnabled()) {
            _isUnlocked.value = true
        }
    }

    fun unlock() {
        _isUnlocked.value = true
    }

    fun lock() {
        _isUnlocked.value = false
    }

    fun onAppBackgrounded() {
        lastBackgroundTimeMs = System.currentTimeMillis()
    }

    fun onAppForegrounded(context: Context) {
        val prefs = AppPreferences(context)
        if (!prefs.isAppLockEnabled()) {
            _isUnlocked.value = true
            return
        }

        val timeoutSeconds = prefs.getAutoLockTimeoutSeconds()
        val elapsedMillis = System.currentTimeMillis() - lastBackgroundTimeMs
        if (timeoutSeconds == 0 || elapsedMillis > (timeoutSeconds * 1000L)) {
            _isUnlocked.value = false
        }
    }

    // --- PIN Management ---

    fun hasPin(context: Context): Boolean {
        return AppPreferences(context).getAppLockPinHash().isNotEmpty()
    }

    fun setPin(context: Context, pin: String) {
        val prefs = AppPreferences(context)
        if (pin.isEmpty()) {
            prefs.setAppLockPinHash("")
            prefs.setAppLockPinSalt("")
            prefs.resetLockout()
            return
        }

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = bytesToHex(salt)
        val hash = hashPinPbkdf2(pin, saltHex)

        prefs.setAppLockPinSalt(saltHex)
        prefs.setAppLockPinHash(hash)
        prefs.resetLockout()
    }

    fun getRemainingLockoutSeconds(context: Context): Int {
        val prefs = AppPreferences(context)
        val lockoutUntil = prefs.getLockoutUntilTimestamp()
        val now = System.currentTimeMillis()
        return if (lockoutUntil > now) {
            ((lockoutUntil - now + 999) / 1000).toInt()
        } else {
            0
        }
    }

    fun isLockedOut(context: Context): Boolean {
        return getRemainingLockoutSeconds(context) > 0
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val prefs = AppPreferences(context)

        // Check if user is currently locked out
        if (isLockedOut(context)) {
            return false
        }

        val storedHash = prefs.getAppLockPinHash()
        val storedSalt = prefs.getAppLockPinSalt()

        if (storedHash.isEmpty()) {
            prefs.resetLockout()
            return true
        }

        // Try PBKDF2 verification first
        var isMatch = false
        val computedPbkdf2 = hashPinPbkdf2(pin, storedSalt)
        if (computedPbkdf2.equals(storedHash, ignoreCase = true)) {
            isMatch = true
        } else {
            // Check legacy SHA-256 for seamless backward compatibility
            val legacySha256 = hashPinSha256(pin, storedSalt)
            if (legacySha256.equals(storedHash, ignoreCase = true)) {
                isMatch = true
                // Automatically upgrade to PBKDF2
                setPin(context, pin)
                AppLogger.i("AppLockManager: Automatically upgraded PIN hash to PBKDF2")
            }
        }

        if (isMatch) {
            prefs.resetLockout()
            return true
        } else {
            val failedAttempts = prefs.getFailedPinAttempts() + 1
            prefs.setFailedPinAttempts(failedAttempts)

            // Rate-limiting / lockout after 5 consecutive failures
            if (failedAttempts >= 5) {
                val lockoutDurationMs = if (failedAttempts >= 8) 60_000L else 30_000L
                prefs.setLockoutUntilTimestamp(System.currentTimeMillis() + lockoutDurationMs)
                AppLogger.w("AppLockManager: Rate-limit triggered ($failedAttempts failed attempts). Lockout for ${lockoutDurationMs / 1000}s")
            }
            return false
        }
    }

    private fun hashPinPbkdf2(pin: String, saltHex: String): String {
        val saltBytes = hexToBytes(saltHex)
        val chars = pin.toCharArray()
        return try {
            val spec = PBEKeySpec(chars, saltBytes, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val hash = skf.generateSecret(spec).encoded
            spec.clearPassword()
            bytesToHex(hash)
        } finally {
            chars.fill('0')
        }
    }

    private fun hashPinSha256(pin: String, saltHex: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(saltHex.toByteArray(Charsets.UTF_8))
        val digest = md.digest(pin.toByteArray(Charsets.UTF_8))
        return bytesToHex(digest)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    // --- Biometric Authentication ---

    fun canAuthenticateBiometrics(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val result = manager.canAuthenticate(authenticators)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticateWithBiometrics(
        activity: AppCompatActivity,
        title: String = "Unlock AVault",
        subtitle: String = "Confirm identity to access saved conversations",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()

        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    AppPreferences(activity).resetLockout()
                    _isUnlocked.value = true
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    AppLogger.d("Biometric error: $errorCode - $errString")
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Authentication failed")
                }
            }
        )

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            AppLogger.e("Failed to trigger BiometricPrompt: ${e.message}", e)
            onError(e.message ?: "Authentication error")
        }
    }
}
