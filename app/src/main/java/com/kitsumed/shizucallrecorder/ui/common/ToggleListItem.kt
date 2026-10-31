/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.kitsumed.shizucallrecorder.ui.theme.*

/**
 * A list row with a switch styled in the BRIK design system.
 */
@Composable
fun ToggleListItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    ListItem(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onCheckedChange(it) }
            ),
        headlineContent = {
            Text(
                text = label,
                color = BrikTextPrimary.copy(alpha = contentAlpha),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
        },
        supportingContent = if (description != null) {
            {
                Text(
                    text = description,
                    color = BrikTextSecondary.copy(alpha = contentAlpha),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        } else null,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor    = BrikOnLavender,
                    checkedTrackColor    = BrikLavender,
                    uncheckedThumbColor  = BrikTextSecondary,
                    uncheckedTrackColor  = BrikDarkCardElevated,
                    uncheckedBorderColor = BrikBorder
                )
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
