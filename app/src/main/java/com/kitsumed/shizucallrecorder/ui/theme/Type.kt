/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Consistent rhythm across setup, library, playback and settings.
private fun text(size: Int, line: Int, weight: FontWeight = FontWeight.Normal,
                 spacing: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight,
    fontSize = size.sp, lineHeight = line.sp, letterSpacing = spacing.sp
)

val Typography = Typography(
    displaySmall = text(36, 42, FontWeight.SemiBold, -1.0),
    headlineLarge = text(32, 38, FontWeight.SemiBold, -0.8),
    headlineMedium = text(28, 34, FontWeight.SemiBold, -0.5),
    headlineSmall = text(24, 30, FontWeight.SemiBold, -0.3),
    titleLarge = text(22, 28, FontWeight.SemiBold, -0.3),
    titleMedium = text(16, 22, FontWeight.Medium),
    titleSmall = text(14, 20, FontWeight.Medium),
    bodyLarge = text(16, 25), bodyMedium = text(14, 21),
    bodySmall = text(12, 18),
    labelLarge = text(14, 20, FontWeight.Medium),
    labelMedium = text(12, 18, FontWeight.Medium),
    labelSmall = text(11, 16, FontWeight.Medium, 0.3)
)
