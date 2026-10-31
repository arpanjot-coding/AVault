/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsumed.shizucallrecorder.data.AppPreferences
import com.kitsumed.shizucallrecorder.onboarding.OnboardingStatus
import com.kitsumed.shizucallrecorder.ui.screens.CallsDashboardScreen
import com.kitsumed.shizucallrecorder.ui.screens.DisclaimerScreen
import com.kitsumed.shizucallrecorder.ui.screens.PermissionsScreen
import com.kitsumed.shizucallrecorder.ui.screens.SettingsScreen
import com.kitsumed.shizucallrecorder.ui.theme.AVaultTheme
import com.kitsumed.shizucallrecorder.ui.viewmodels.AppNavigationViewModel
import com.kitsumed.shizucallrecorder.ui.viewmodels.SettingsViewModel

/**
 * Top-level router composable called from [MainActivity].
 *
 * Decides which destination to display: the one-time disclaimer, the permissions checklist,
 * or the main Calls Dashboard (which contains navigation to the Settings panel).
 */
@Composable
fun AppNavigationScreen() {
    val activityContext = LocalContext.current

    val appNavViewModel: AppNavigationViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val onboardingStatus by appNavViewModel.onboardingStatus.collectAsState()
    val settingsViewModelUpdateTrigger by settingsViewModel.updateTrigger.collectAsState()

    val preferences = settingsViewModel.preferences

    LaunchedEffect(settingsViewModelUpdateTrigger) {
        appNavViewModel.refresh()
    }

    val screenState = resolveScreen(onboardingStatus)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        appNavViewModel.refresh()
    }

    val darkTheme = when (preferences.getThemeMode()) {
        AppPreferences.ThemeMode.LIGHT -> false
        AppPreferences.ThemeMode.DARK -> true
        AppPreferences.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val dynamicColor = preferences.isDynamicColorEnabled()

    val isAppUnlocked by com.kitsumed.shizucallrecorder.system.security.AppLockManager.isUnlocked.collectAsState()
    val isAppLockEnabled = preferences.isAppLockEnabled()

    AVaultTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        if (isAppLockEnabled && !isAppUnlocked) {
            com.kitsumed.shizucallrecorder.ui.screens.LockScreen(
                onUnlocked = {
                    com.kitsumed.shizucallrecorder.system.security.AppLockManager.unlock()
                }
            )
        } else {
            AnimatedContent(
                targetState = screenState,
            transitionSpec = {
                val movingForward = targetState.ordinal > initialState.ordinal
                val slideDirection = if (movingForward) 1 else -1

                val enterSpec = slideInHorizontally(tween(350)) { width -> width * slideDirection } + fadeIn(tween(350))
                val exitSpec = slideOutHorizontally(tween(300)) { width -> -width * slideDirection } + fadeOut(tween(300))

                enterSpec togetherWith exitSpec
            },
            label = "AppScreenNavigationTransition"
        ) { targetScreenState ->
            when (targetScreenState) {
                AppScreen.Disclaimer -> DisclaimerScreen(
                    onContinue = {
                        preferences.setDisclaimerAccepted(true)
                        appNavViewModel.refresh()
                    }
                )

                AppScreen.Permissions -> PermissionsScreen(
                    status = onboardingStatus,
                    onPermissionGranted = { appNavViewModel.refresh() }
                )

                AppScreen.Calls -> {
                    var showSettings by remember { mutableStateOf(false) }

                    BackHandler(enabled = showSettings) {
                        showSettings = false
                    }

                    AnimatedContent(
                        targetState = showSettings,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))) togetherWith
                                        (slideOutHorizontally(tween(250)) { -it } + fadeOut(tween(250)))
                            } else {
                                (slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300))) togetherWith
                                        (slideOutHorizontally(tween(250)) { it } + fadeOut(tween(250)))
                            }
                        },
                        label = "CallsToSettingsTransition"
                    ) { isSettingsVisible ->
                        if (isSettingsVisible) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onBack = { showSettings = false }
                            )
                        } else {
                            CallsDashboardScreen(
                                onNavigateToSettings = { showSettings = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

// -------- Private helpers

private enum class AppScreen {
    Disclaimer,
    Permissions,
    Calls
}

private fun resolveScreen(status: OnboardingStatus.Status): AppScreen {
    // Open directly to Calls Dashboard as permanent home screen
    return AppScreen.Calls
}
