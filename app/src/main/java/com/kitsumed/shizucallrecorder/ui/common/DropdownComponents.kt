/*
 * AVault: Secure Hardware-Encrypted Call Management
 *  Copyright (C) 2026 AVault Project
 *  This software is licensed under the GNU General Public License v3 or later, 
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.kitsumed.shizucallrecorder.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitsumed.shizucallrecorder.ui.theme.*

/**
 * A key/label/description/etc... data class used to populate dropdown menus.
 */
data class OptionItem(
    val key: String,
    val label: String,
    val description: String? = null,
    val enabled: Boolean = true
)

/**
 * A Material 3 [ExposedDropdownMenuBox]-based dropdown field styled in the BRIK design system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3DropdownField(
    label: String,
    selected: OptionItem,
    options: List<OptionItem>,
    onOptionSelected: (OptionItem) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded         = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier        = modifier
    ) {
        OutlinedTextField(
            value         = selected.label,
            onValueChange = {},
            readOnly      = true,
            label         = { Text(label, color = BrikTextSecondary) },
            isError       = isError,
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape         = RoundedCornerShape(16.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedContainerColor   = BrikDarkCardElevated,
                unfocusedContainerColor = BrikDarkCardElevated,
                focusedBorderColor      = BrikLavender,
                unfocusedBorderColor    = BrikBorder,
                focusedTextColor        = BrikTextPrimary,
                unfocusedTextColor      = BrikTextPrimary,
                focusedLabelColor       = BrikLavender,
                unfocusedLabelColor     = BrikTextSecondary,
                focusedTrailingIconColor = BrikLavender,
                unfocusedTrailingIconColor = BrikTextSecondary
            ),
            modifier      = Modifier
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false },
            shape            = RoundedCornerShape(18.dp),
            containerColor   = BrikDarkCardElevated,
            border           = BorderStroke(1.dp, BrikBorder)
        ) {
            options.forEach { option ->
                val isSelected = option.key == selected.key
                DropdownMenuItem(
                    text = {
                        Column(
                            modifier = Modifier.padding(vertical = if (option.description != null) 4.dp else 2.dp)
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) BrikLavender else BrikTextPrimary
                            )
                            option.description?.let { desc ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrikTextSecondary
                                )
                            }
                        }
                    },
                    onClick = {
                        if (option.enabled) {
                            onOptionSelected(option)
                            expanded = false
                        }
                    },
                    enabled        = option.enabled,
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}
