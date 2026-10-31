/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.system.security.AppLockManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? AppCompatActivity
    val preferences = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()

    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    var hasCustomPin by remember { mutableStateOf(AppLockManager.hasPin(context)) }
    var showSetPinDialog by remember { mutableStateOf(false) }

    val isBiometricEnabled = remember { preferences.isBiometricLockEnabled() }
    val shakeOffset = remember { Animatable(0f) }

    var lockoutSeconds by remember { mutableStateOf(AppLockManager.getRemainingLockoutSeconds(context)) }

    fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                v.vibrate(50)
            }
        } catch (_: Exception) {}
    }

    fun triggerBiometrics() {
        if (activity != null && isBiometricEnabled) {
            AppLockManager.authenticateWithBiometrics(
                activity = activity,
                onSuccess = {
                    preferences.resetLockout()
                    onUnlocked()
                },
                onError = {
                    // Stay on lockscreen for PIN fallback
                }
            )
        }
    }

    // Auto-prompt biometrics when screen first appears and purge any transient playback cache
    LaunchedEffect(Unit) {
        com.kitsumed.shizucallrecorder.system.security.VaultEncryptionManager.clearTempPlayback(context)
        if (isBiometricEnabled && activity != null) {
            delay(200)
            triggerBiometrics()
        }
    }

    // Rate-limiting lockout countdown ticker
    LaunchedEffect(Unit) {
        while (true) {
            val remaining = AppLockManager.getRemainingLockoutSeconds(context)
            lockoutSeconds = remaining
            if (remaining > 0) {
                errorMessage = "Too many attempts. Try again in ${remaining}s"
                isError = true
            } else if (errorMessage.startsWith("Too many attempts")) {
                errorMessage = ""
                isError = false
            }
            delay(1000)
        }
    }

    fun onNumberClick(digit: String) {
        if (lockoutSeconds > 0) {
            triggerVibration()
            return
        }
        if (enteredPin.length < 4) {
            triggerVibration()
            val newPin = enteredPin + digit
            enteredPin = newPin
            isError = false
            errorMessage = ""

            if (newPin.length == 4) {
                if (hasCustomPin) {
                    if (AppLockManager.verifyPin(context, newPin)) {
                        AppLockManager.unlock()
                        onUnlocked()
                    } else {
                        val remaining = AppLockManager.getRemainingLockoutSeconds(context)
                        isError = true
                        if (remaining > 0) {
                            lockoutSeconds = remaining
                            errorMessage = "Too many attempts. Try again in ${remaining}s"
                        } else {
                            errorMessage = "Incorrect PIN"
                        }
                        scope.launch {
                            repeat(3) {
                                shakeOffset.animateTo(12f, tween(50))
                                shakeOffset.animateTo(-12f, tween(50))
                            }
                            shakeOffset.animateTo(0f, tween(50))
                            delay(300)
                            enteredPin = ""
                            if (remaining == 0) {
                                isError = false
                            }
                        }
                    }
                } else {
                    AppLockManager.unlock()
                    onUnlocked()
                }
            }
        }
    }

    fun onDeleteClick() {
        if (lockoutSeconds > 0) return
        if (enteredPin.isNotEmpty()) {
            triggerVibration()
            enteredPin = enteredPin.dropLast(1)
            isError = false
            errorMessage = ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0C0E))
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .offset(x = shakeOffset.value.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF13161C))
                    .border(1.dp, Color(0xFF222834), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (lockoutSeconds > 0) Color(0xFFFF5252) else Color(0xFF5390F5),
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "AVault Locked",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasCustomPin) "Enter your 4-digit PIN" else "Confirm identity to access conversations",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF8E95A5)
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (hasCustomPin) {
                // PIN indicator dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        val isFilled = index < enteredPin.length
                        val dotColor = when {
                            isError -> Color(0xFFFF5252)
                            isFilled -> Color(0xFF5390F5)
                            else -> Color(0xFF222834)
                        }
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                                .border(
                                    1.dp,
                                    if (isFilled) Color(0xFF5390F5) else Color(0xFF333D4F),
                                    CircleShape
                                )
                        )
                    }
                }

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF5252)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val rows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("BIO", "0", "DEL")
                    )

                    for (row in rows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (key in row) {
                                when (key) {
                                    "BIO" -> {
                                        if (isBiometricEnabled) {
                                            Box(
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF13161C))
                                                    .border(1.dp, Color(0xFF1F242E), CircleShape)
                                                    .clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = ripple(bounded = true, color = Color(0xFF5390F5)),
                                                        onClick = { triggerBiometrics() }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Fingerprint,
                                                    contentDescription = "Biometric Unlock",
                                                    tint = Color(0xFF5390F5),
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.size(72.dp))
                                        }
                                    }
                                    "DEL" -> {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .alpha(if (lockoutSeconds > 0) 0.35f else 1f)
                                                .clickable(
                                                    enabled = lockoutSeconds == 0,
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = ripple(bounded = true, color = Color.White),
                                                    onClick = { onDeleteClick() }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Backspace,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFC0C7D5),
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(CircleShape)
                                                .alpha(if (lockoutSeconds > 0) 0.35f else 1f)
                                                .background(Color(0xFF13161C))
                                                .border(1.dp, Color(0xFF1F242E), CircleShape)
                                                .clickable(
                                                    enabled = lockoutSeconds == 0,
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = ripple(bounded = true, color = Color(0xFF5390F5)),
                                                    onClick = { onNumberClick(key) }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = key,
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 24.sp
                                                ),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = { triggerBiometrics() }) {
                    Text(
                        text = "Unlock with Biometrics / Screen Lock",
                        color = Color(0xFF8E95A5),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

            } else {
                // If user hasn't set a custom PIN yet
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = { triggerBiometrics() },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF5390F5),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Unlock with Biometrics / Screen Lock",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { showSetPinDialog = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF5390F5)),
                    border = BorderStroke(1.dp, Color(0xFF222834)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Set 4-Digit App PIN",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }

    if (showSetPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var confirmPinInput by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text("Set 4-Digit App PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a 4-digit numeric PIN for AVault:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("4-digit PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    OutlinedTextField(
                        value = confirmPinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                        label = { Text("Confirm PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    )
                    if (pinError.isNotEmpty()) {
                        Text(pinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput.length != 4) {
                        pinError = "PIN must be exactly 4 digits"
                    } else if (pinInput != confirmPinInput) {
                        pinError = "PINs do not match"
                    } else {
                        AppLockManager.setPin(context, pinInput)
                        hasCustomPin = true
                        Toast.makeText(context, "App PIN configured", Toast.LENGTH_SHORT).show()
                        showSetPinDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
