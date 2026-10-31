/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.theme

import androidx.compose.ui.graphics.Color

// --- AVAULT HIGH-ASSURANCE AUTHENTIC PALETTE ---
val AVaultDarkBackground = Color(0xFF0A0D14)        // True dark midnight canvas
val AVaultDarkCard = Color(0xFF121724)              // Sleek dark cyber card
val AVaultDarkCardElevated = Color(0xFF181F30)      // Elevated container
val AVaultBorder = Color(0xFF222B3D)                // Refined dark border
val AVaultBorderFocused = Color(0xFF3B82F6)         // Focused card border

// --- AUTHENTIC AVault ACCENT TOKENS ---
val AVaultAccentBlue = Color(0xFF5390F5)            // Iconic AVault signature electric blue
val AVaultOnAccentBlue = Color(0xFFFFFFFF)          // High contrast white on blue
val AVaultAccentBlueContainer = Color(0xFF17294A)   // Deep blue container
val AVaultAccentBlueText = Color(0xFFD3E4FD)        // Luminous blue text

val AVaultGreen = Color(0xFF10B981)                 // Rootless Active emerald green
val AVaultOnGreen = Color(0xFF022C22)
val AVaultAmber = Color(0xFFFBBF24)                 // Standby / Warning amber
val AVaultOnAmber = Color(0xFF451A03)

// --- TEXT TOKENS ---
val AVaultTextPrimary = Color(0xFFF1F5F9)           // Crisp white-slate headings
val AVaultTextSecondary = Color(0xFF94A3B8)         // Slate muted labels
val AVaultTextSub = Color(0xFF64748B)               // Subtle indicators
val AVaultWhite = Color.White

// Compatibility aliases for layout tokens
val BrikDarkBackground = AVaultDarkBackground
val BrikDarkCard = AVaultDarkCard
val BrikDarkCardElevated = AVaultDarkCardElevated
val BrikBorder = AVaultBorder
val BrikBorderFocused = AVaultBorderFocused

val BrikLavender = AVaultAccentBlue                 // Authentic AVault Blue!
val BrikOnLavender = AVaultOnAccentBlue
val BrikLavenderContainer = AVaultAccentBlueContainer
val BrikLavenderText = AVaultAccentBlueText

val BrikMint = AVaultGreen
val BrikOnMint = AVaultOnGreen
val BrikAmber = AVaultAmber
val BrikOnAmber = AVaultOnAmber

val BrikTextPrimary = AVaultTextPrimary
val BrikTextSecondary = AVaultTextSecondary
val BrikTextSub = AVaultTextSub
val BrikWhite = AVaultWhite

// --- LEGACY MATERIAL 3 COMPATIBILITY ---
val Green80 = AVaultGreen
val Green40 = Color(0xFF0F766E)
val GreenGrey80 = Color(0xFFB5C9C8)
val GreenGrey40 = Color(0xFF495E5E)
val AccentGreen80 = AVaultGreen
val AccentGreenDark = Color(0xFF00382B)
val DeepDarkGreen = Color(0xFF042F2E)
val VeryDarkForest = Color(0xFF063900)
val DarkGreyGreen = Color(0xFF243638)
val NearBlackText = AVaultDarkBackground
val OffWhiteText = AVaultTextPrimary
val White = Color.White

val DarkSurface = AVaultDarkCard
val LightSurface = Color(0xFFF7F8F6)
val GreenContainerDark = Color(0xFF133832)
val GreenContainerLight = Color(0xFFCCFBF1)
val GreyGreenOutline = AVaultBorder

val DarkBackground = AVaultDarkBackground
val DarkSurfaceVariant = AVaultDarkCard
val DarkSurfaceContainer = AVaultDarkCard
val DarkSurfaceContainerLow = AVaultDarkBackground
val DarkSurfaceContainerHigh = AVaultDarkCardElevated
val DarkTextPrimary = AVaultTextPrimary
val DarkTextSecondary = AVaultTextSecondary
val DarkOutline = AVaultBorder
val DarkOutlineVariant = Color(0xFF162729)
val DarkAccentBlue = AVaultAccentBlue
val DarkAccentBlueContainer = AVaultAccentBlueContainer
val DarkAccentBlueOnContainer = AVaultAccentBlueText
