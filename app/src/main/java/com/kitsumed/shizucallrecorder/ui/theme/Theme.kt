/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BrikLavender,
    onPrimary = BrikOnLavender,
    primaryContainer = BrikLavenderContainer,
    onPrimaryContainer = BrikLavenderText,

    secondary = Green80,
    onSecondary = DeepDarkGreen,
    secondaryContainer = GreenContainerDark,
    onSecondaryContainer = GreenContainerLight,

    tertiary = AccentGreen80,
    onTertiary = AccentGreenDark,

    background = DarkBackground,
    onBackground = DarkTextPrimary,

    surface = DarkSurface,
    onSurface = DarkTextPrimary,

    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,

    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerHigh = DarkSurfaceContainerHigh,

    outline = DarkOutline,
    outlineVariant = AVaultBorder,

    error = Color(0xFFFF6B6B),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF315D99), onPrimary = Color.White,
    primaryContainer = Color(0xFFDFEAFA), onPrimaryContainer = Color(0xFF193758),
    secondary = Color(0xFF3F695A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFEEE5), onSecondaryContainer = Color(0xFF254D3F),
    tertiary = Color(0xFF876521), onTertiary = Color.White,
    background = Color(0xFFF7F7F3), onBackground = Color(0xFF19212B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF19212B),
    surfaceVariant = Color(0xFFEDF0F3), onSurfaceVariant = Color(0xFF536170),
    surfaceContainer = Color(0xFFF0F2F4), surfaceContainerLow = Color(0xFFF7F7F3),
    surfaceContainerHigh = Color(0xFFE6EBF1),
    outline = Color(0xFF778391), outlineVariant = Color(0xFFD9DFE5)
)

@Composable
fun AVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    // Dynamic color scheme
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // System Bar icon theme sync
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
