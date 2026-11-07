package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CustomChecklistItem
import com.example.data.CustomFieldDefinition
import com.example.data.CustomFieldType
import com.example.data.CustomTypeEntity
import com.example.data.SmartImportExportEngine
import com.example.data.VaultJsonSerializers
import com.example.ui.theme.IdeaVaultTheme
import com.example.ui.theme.VaultYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

val CUSTOM_TYPE_COLOR_PRESETS = listOf(
    "#5E35B1",
    "#00897B",
    "#E53935",
    "#FB8C00",
    "#1E88E5",
    "#43A047",
    "#8E24AA",
    "#3949AB"
)

val CUSTOM_TYPE_ICON_PRESETS = listOf(
    "Extension",
    "Code",
    "MenuBook",
    "Science",
    "Build",
    "Layers",
    "Inventory",
    "Work",
    "Bookmark",
    "Star"
)

fun resolveCustomTypeIcon(iconName: String): ImageVector = when (iconName.trim().lowercase()) {
    "code" -> Icons.Default.Code
    "menubook", "book" -> Icons.Default.MenuBook
    "science", "lab" -> Icons.Default.Science
    "build", "tool" -> Icons.Default.Build
    "layers", "stack" -> Icons.Default.Layers
    "inventory", "box" -> Icons.Default.Inventory2
    "work", "project" -> Icons.Default.WorkOutline
    "bookmark" -> Icons.Default.Bookmark
    "star" -> Icons.Default.Star
    else -> Icons.Default.Extension
}

fun parseHexColorSafe(hex: String, fallback: Color = Color(0xFF5E35B1)): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        val value = clean.toLong(16)
        when (clean.length) {
            6 -> Color(value or 0xFF000000L)
            8 -> Color(value)
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Audit P2-16: Computes an accessible foreground color (Black or White) with high contrast
 * against the given background color using relative luminance.
 */
fun accessibleTextColorForBackground(backgroundColor: Color): Color {
    return if (backgroundColor.luminance() > 0.45f) Color(0xFF121212) else Color.White
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomTypeBuilderDialog(
    initialCustomType: CustomTypeEntity? = null,
    existingCustomTypes: List<CustomTypeEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (CustomTypeEntity, List<CustomFieldDefinition>) -> Unit
) {
    val initialFields = remember(initialCustomType) {
        if (initialCustomType != null) {
            VaultJsonSerializers.decodeCustomFieldDefinitions(initialCustomType.fieldsJson)
        } else {
            listOf(
                CustomFieldDefinition(
                    id = "field_${UUID.randomUUID()}",
                    name = "Summary",
                    type = CustomFieldType.LONG_TEXT,
                    required = false,
                    orderIndex = 0
                )
            )
        }
    }

    var name by rememberSaveable(initialCustomType?.id) {
        mutableStateOf(initialCustomType?.name ?: "")
    }
    var description by rememberSaveable(initialCustomType?.id) {
        mutableStateOf(initialCustomType?.description ?: "")
    }
    var iconName by rememberSaveable(initialCustomType?.id) {
        mutableStateOf(initialCustomType?.iconName ?: "Extension")
    }
    var colorHex by rememberSaveable(initialCustomType?.id) {
        mutableStateOf(initialCustomType?.colorHex ?: "#5E35B1")
    }

    // Audit P2-07: Persist field definitions across configuration changes (screen rotation) via JSON in rememberSaveable
    var fieldsJsonState by rememberSaveable(initialCustomType?.id) {
        mutableStateOf(VaultJsonSerializers.encodeCustomFieldDefinitions(initialFields))
    }
    val fields = remember(fieldsJsonState) {
        VaultJsonSerializers.decodeCustomFieldDefinitions(fieldsJsonState)
    }
    fun updateFields(updated: List<CustomFieldDefinition>) {
        fieldsJsonState = VaultJsonSerializers.encodeCustomFieldDefinitions(
            updated.mapIndexed { idx, f -> f.copy(orderIndex = idx) }
        )
    }

    var removedExistingFieldCount by rememberSaveable(initialCustomType?.id) {
        mutableIntStateOf(0)
    }
    // Audit P2-08: Support editing existing fields in-place while preserving their field.id
    var editingFieldId by rememberSaveable(initialCustomType?.id) {
        mutableStateOf<String?>(null)
    }
    var newFieldName by rememberSaveable { mutableStateOf("") }
    var newFieldPlaceholder by rememberSaveable { mutableStateOf("") }
    var newFieldType by rememberSaveable { mutableStateOf(CustomFieldType.SHORT_TEXT) }
    var newFieldRequired by rememberSaveable { mutableStateOf(false) }
    var newFieldOptionsRaw by rememberSaveable { mutableStateOf("") }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmTypeConversion by rememberSaveable(editingFieldId, newFieldType.name) { mutableStateOf(false) }

    val editingOriginalField = remember(editingFieldId, initialFields) {
        initialFields.firstOrNull { it.id == editingFieldId }
    }
    val hasTypeConversionWarning = initialCustomType != null &&
        editingOriginalField != null &&
        editingOriginalField.type != newFieldType

    val vaultColors = IdeaVaultTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = vaultColors.surface,
        titleContentColor = vaultColors.primaryText,
        textContentColor = vaultColors.primaryText,
        title = {
            Text(
                text = if (initialCustomType == null) "Create Custom Type" else "Edit Custom Type",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = vaultColors.primaryText
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Type Name (e.g., Research Paper, API Spec)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_type_name_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_type_description_input")
                )

                Text(
                    text = "Color Badge",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CUSTOM_TYPE_COLOR_PRESETS.forEach { presetHex ->
                        val isSelected = colorHex.equals(presetHex, ignoreCase = true)
                        val swatchColor = parseHexColorSafe(presetHex)
                        // Audit P2-09: 48.dp touch target for accessibility
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { colorHex = presetHex }
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) vaultColors.primaryText else Color.Transparent,
                                    shape = CircleShape
                                )
                                .testTag("custom_type_color_swatch_${presetHex.removePrefix("#")}"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected color $presetHex",
                                    tint = accessibleTextColorForBackground(swatchColor),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CUSTOM_TYPE_ICON_PRESETS.forEach { presetIcon ->
                        FilterChip(
                            selected = iconName.equals(presetIcon, ignoreCase = true),
                            onClick = { iconName = presetIcon },
                            label = { Text(presetIcon) },
                            leadingIcon = {
                                Icon(
                                    imageVector = resolveCustomTypeIcon(presetIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(color = vaultColors.border)

                Text(
                    text = "Custom Fields (${fields.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (removedExistingFieldCount > 0 && initialCustomType != null) {
                    Surface(
                        color = vaultColors.secondarySurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Note: Any saved values from removed fields will be safely preserved in the entry notes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.primaryText,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                fields.forEachIndexed { index, field ->
                    val isBeingEdited = editingFieldId == field.id
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBeingEdited) {
                            vaultColors.actionYellow.copy(alpha = 0.18f)
                        } else {
                            vaultColors.secondarySurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_type_field_row_$index")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${field.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = vaultColors.primaryText
                                    )
                                    Text(
                                        text = buildString {
                                            append(field.type.label)
                                            if (field.required) append(" • Required")
                                            if (field.options.isNotEmpty()) {
                                                append(" • Options: ${field.options.joinToString(", ")}")
                                            }
                                            if (field.placeholder.isNotBlank()) {
                                                append(" • Hint: ${field.placeholder}")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = vaultColors.secondaryText
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Audit P2-08: Edit existing field in-place (preserving field.id)
                                    IconButton(
                                        onClick = {
                                            editingFieldId = field.id
                                            newFieldName = field.name
                                            newFieldPlaceholder = field.placeholder
                                            newFieldType = field.type
                                            newFieldRequired = field.required
                                            newFieldOptionsRaw = field.options.joinToString(", ")
                                            errorMessage = null
                                        },
                                        modifier = Modifier.testTag("custom_type_edit_field_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit field ${field.name}"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val mutable = fields.toMutableList()
                                                val item = mutable.removeAt(index)
                                                mutable.add(index - 1, item)
                                                updateFields(mutable)
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.testTag("custom_type_move_up_field_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move field up"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (index < fields.lastIndex) {
                                                val mutable = fields.toMutableList()
                                                val item = mutable.removeAt(index)
                                                mutable.add(index + 1, item)
                                                updateFields(mutable)
                                            }
                                        },
                                        enabled = index < fields.lastIndex,
                                        modifier = Modifier.testTag("custom_type_move_down_field_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move field down"
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (editingFieldId == field.id) {
                                                editingFieldId = null
                                                newFieldName = ""
                                                newFieldPlaceholder = ""
                                                newFieldOptionsRaw = ""
                                                newFieldRequired = false
                                            }
                                            if (initialCustomType != null && initialFields.any { it.id == field.id }) {
                                                removedExistingFieldCount++
                                            }
                                            updateFields(fields.filterIndexed { i, _ -> i != index })
                                        },
                                        modifier = Modifier.testTag("custom_type_remove_field_$index")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove field ${field.name}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add / Edit Field Builder Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = vaultColors.secondarySurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (editingFieldId != null) "Edit Field" else "Add Field",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (editingFieldId != null) {
                                TextButton(
                                    onClick = {
                                        editingFieldId = null
                                        newFieldName = ""
                                        newFieldPlaceholder = ""
                                        newFieldOptionsRaw = ""
                                        newFieldRequired = false
                                        errorMessage = null
                                    },
                                    modifier = Modifier.testTag("custom_type_cancel_edit_field_button")
                                ) {
                                    Text("Cancel Edit")
                                }
                            }
                        }
                        OutlinedTextField(
                            value = newFieldName,
                            onValueChange = {
                                newFieldName = it
                                errorMessage = null
                            },
                            label = { Text("Field Label (e.g., Endpoint URL, Code Snippet)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_type_new_field_name_input")
                        )

                        OutlinedTextField(
                            value = newFieldPlaceholder,
                            onValueChange = { newFieldPlaceholder = it },
                            label = { Text("Placeholder / Hint (Optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_type_new_field_placeholder_input")
                        )

                        Text(
                            text = "Field Type",
                            style = MaterialTheme.typography.labelSmall
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CustomFieldType.entries.forEach { fType ->
                                FilterChip(
                                    selected = newFieldType == fType,
                                    onClick = { newFieldType = fType },
                                    label = { Text(fType.label) },
                                    modifier = Modifier.testTag("custom_field_type_chip_${fType.jsonKey}")
                                )
                            }
                        }

                        if (hasTypeConversionWarning) {
                            Surface(
                                color = vaultColors.secondarySurface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_type_field_conversion_warning")
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Changing type from '${editingOriginalField?.type?.label}' to '${newFieldType.label}' keeps the field ID. Existing values that cannot be converted to '${newFieldType.label}' will be preserved in entry Notes.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = vaultColors.primaryText
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable { confirmTypeConversion = !confirmTypeConversion }
                                            .testTag("custom_type_confirm_type_conversion_checkbox")
                                    ) {
                                        Checkbox(
                                            checked = confirmTypeConversion,
                                            onCheckedChange = { confirmTypeConversion = it }
                                        )
                                        Text(
                                            text = "Confirm field type change & preserve incompatible values in Notes",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = vaultColors.primaryText
                                        )
                                    }
                                }
                            }
                        }

                        if (newFieldType == CustomFieldType.SINGLE_SELECT ||
                            newFieldType == CustomFieldType.MULTI_SELECT
                        ) {
                            OutlinedTextField(
                                value = newFieldOptionsRaw,
                                onValueChange = { newFieldOptionsRaw = it },
                                label = { Text("Select Options (comma-separated, e.g. Low, Medium, High)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_type_new_field_options_input")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { newFieldRequired = !newFieldRequired }
                                    .testTag("custom_type_new_field_required_toggle")
                            ) {
                                Checkbox(
                                    checked = newFieldRequired,
                                    onCheckedChange = { newFieldRequired = it }
                                )
                                Text(
                                    text = "Required field",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val trimmedField = newFieldName.trim()
                                    if (trimmedField.isEmpty()) {
                                        errorMessage = "Enter a field name before saving field."
                                        return@OutlinedButton
                                    }
                                    val duplicateField = fields.any {
                                        it.id != editingFieldId && it.name.equals(trimmedField, ignoreCase = true)
                                    }
                                    if (duplicateField) {
                                        errorMessage = "A field named '$trimmedField' already exists."
                                        return@OutlinedButton
                                    }
                                    val options = if (
                                        newFieldType == CustomFieldType.SINGLE_SELECT ||
                                        newFieldType == CustomFieldType.MULTI_SELECT
                                    ) {
                                        newFieldOptionsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    } else {
                                        emptyList()
                                    }

                                    if (editingFieldId != null) {
                                        if (hasTypeConversionWarning && !confirmTypeConversion) {
                                            errorMessage = "Please confirm the field type change before updating '${trimmedField}'."
                                            return@OutlinedButton
                                        }
                                        val targetId = editingFieldId!!
                                        val updatedList = fields.map { existing ->
                                            if (existing.id == targetId) {
                                                existing.copy(
                                                    name = trimmedField,
                                                    type = newFieldType,
                                                    required = newFieldRequired,
                                                    options = options,
                                                    placeholder = newFieldPlaceholder.trim()
                                                )
                                            } else {
                                                existing
                                            }
                                        }
                                        updateFields(updatedList)
                                        editingFieldId = null
                                    } else {
                                        updateFields(
                                            fields + CustomFieldDefinition(
                                                id = "field_${UUID.randomUUID()}",
                                                name = trimmedField,
                                                type = newFieldType,
                                                required = newFieldRequired,
                                                options = options,
                                                placeholder = newFieldPlaceholder.trim(),
                                                orderIndex = fields.size
                                            )
                                        )
                                    }
                                    newFieldName = ""
                                    newFieldPlaceholder = ""
                                    newFieldOptionsRaw = ""
                                    newFieldRequired = false
                                    errorMessage = null
                                },
                                modifier = Modifier.testTag("custom_type_add_field_button")
                            ) {
                                Icon(
                                    imageVector = if (editingFieldId != null) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (editingFieldId != null) "Update Field" else "Add Field")
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.coral,
                        modifier = Modifier.testTag("custom_type_error_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isEmpty()) {
                        errorMessage = "Custom Type name is required."
                        return@Button
                    }
                    val duplicate = existingCustomTypes.any {
                        it.id != initialCustomType?.id && it.name.equals(trimmedName, ignoreCase = true)
                    }
                    if (duplicate) {
                        errorMessage = "A Custom Type named '$trimmedName' already exists."
                        return@Button
                    }
                    // Apply pending field edit or pending new field if typed in input box
                    val pendingTrimmed = newFieldName.trim()
                    val finalFields = if (pendingTrimmed.isNotEmpty()) {
                        val opts = if (
                            newFieldType == CustomFieldType.SINGLE_SELECT ||
                            newFieldType == CustomFieldType.MULTI_SELECT
                        ) {
                            newFieldOptionsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        } else emptyList()

                        if (editingFieldId != null) {
                            if (hasTypeConversionWarning && !confirmTypeConversion) {
                                errorMessage = "Please confirm the field type change before saving."
                                return@Button
                            }
                            val targetId = editingFieldId!!
                            if (fields.any { it.id != targetId && it.name.equals(pendingTrimmed, ignoreCase = true) }) {
                                errorMessage = "A field named '$pendingTrimmed' already exists."
                                return@Button
                            }
                            fields.map { existing ->
                                if (existing.id == targetId) {
                                    existing.copy(
                                        name = pendingTrimmed,
                                        type = newFieldType,
                                        required = newFieldRequired,
                                        options = opts,
                                        placeholder = newFieldPlaceholder.trim()
                                    )
                                } else existing
                            }
                        } else if (fields.none { it.name.equals(pendingTrimmed, ignoreCase = true) }) {
                            fields + CustomFieldDefinition(
                                id = "field_${UUID.randomUUID()}",
                                name = pendingTrimmed,
                                type = newFieldType,
                                required = newFieldRequired,
                                options = opts,
                                placeholder = newFieldPlaceholder.trim(),
                                orderIndex = fields.size
                            )
                        } else {
                            fields
                        }
                    } else {
                        fields
                    }
                    if (finalFields.isEmpty()) {
                        errorMessage = "Add at least 1 custom field to this Custom Type."
                        return@Button
                    }
                    val entity = CustomTypeEntity(
                        id = initialCustomType?.id ?: "ctype_${UUID.randomUUID()}",
                        name = trimmedName,
                        iconName = iconName,
                        colorHex = colorHex,
                        description = description.trim(),
                        fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(finalFields),
                        createdAt = initialCustomType?.createdAt ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(entity, finalFields)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = vaultColors.actionYellow,
                    contentColor = vaultColors.darkInk
                ),
                modifier = Modifier.testTag("custom_type_save_button")
            ) {
                Text("Save Custom Type", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("custom_type_cancel_button")
            ) {
                Text("Cancel", color = vaultColors.secondaryText)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomTypeManagerSheet(
    customTypes: List<CustomTypeEntity>,
    onDismiss: () -> Unit,
    onCreateNewType: () -> Unit,
    onEditType: (CustomTypeEntity) -> Unit,
    onDeleteTypeSafely: (CustomTypeEntity) -> Unit,
    onCreateEntryWithType: (CustomTypeEntity) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    var pendingDeleteType by remember { mutableStateOf<CustomTypeEntity?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = vaultColors.surface,
        contentColor = vaultColors.primaryText,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Custom Content Types",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = vaultColors.primaryText
                    )
                    Text(
                        text = "Create reusable templates with custom fields",
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.secondaryText
                    )
                }
                Button(
                    onClick = onCreateNewType,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.actionYellow,
                        contentColor = vaultColors.darkInk
                    ),
                    modifier = Modifier.testTag("manage_custom_types_add_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("+ Create Type", fontWeight = FontWeight.Bold)
                }
            }

            if (customTypes.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = vaultColors.secondarySurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = vaultColors.lavender
                        )
                        Text(
                            text = "No Custom Types yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = vaultColors.primaryText
                        )
                        Text(
                            text = "Tap '+ Create Type' to define your own structured fields (Short Text, Code, Checklist, URL, Select, and more).",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(customTypes, key = { it.id }) { ct ->
                        val fields = remember(ct.fieldsJson) {
                            VaultJsonSerializers.decodeCustomFieldDefinitions(ct.fieldsJson)
                        }
                        val badgeBg = parseHexColorSafe(ct.colorHex)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = vaultColors.secondarySurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("custom_type_item_${ct.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCreateEntryWithType(ct) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(badgeBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = resolveCustomTypeIcon(ct.iconName),
                                            contentDescription = null,
                                            tint = accessibleTextColorForBackground(badgeBg)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = ct.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = vaultColors.primaryText
                                        )
                                        Text(
                                            text = "${fields.size} ${if (fields.size == 1) "field" else "fields"}" +
                                                if (ct.description.isNotBlank()) " • ${ct.description}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = vaultColors.secondaryText,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { onEditType(ct) },
                                        modifier = Modifier.testTag("edit_custom_type_${ct.id}")
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit ${ct.name}", tint = vaultColors.primaryText)
                                    }
                                    IconButton(
                                        onClick = { pendingDeleteType = ct },
                                        modifier = Modifier.testTag("delete_custom_type_${ct.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete ${ct.name}",
                                            tint = vaultColors.coral
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    val toDelete = pendingDeleteType
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteType = null },
            containerColor = vaultColors.surface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Delete '${toDelete.name}'?", fontWeight = FontWeight.Bold, color = vaultColors.primaryText) },
            text = {
                Text(
                    "Deleting this Custom Type will NOT delete any saved entries. " +
                        "Any entries using '${toDelete.name}' will have their custom field values safely preserved in their content.",
                    color = vaultColors.secondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteType = null
                        onDeleteTypeSafely(toDelete)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.coral,
                        contentColor = vaultColors.darkInk
                    ),
                    modifier = Modifier.testTag("confirm_delete_custom_type_button")
                ) {
                    Text("Delete Type & Preserve Entries", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteType = null }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }
}

/**
 * Interactive Editor section for filling out all custom fields of the active CustomTypeEntity.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomFieldsEditorSection(
    customType: CustomTypeEntity,
    customFieldsMap: Map<String, String>,
    onFieldValueChange: (String, String) -> Unit,
    onEditCustomTypeDefinition: () -> Unit,
    onCopyCodeSnippet: (String) -> Unit,
    showRequiredErrors: Boolean = false,
    modifier: Modifier = Modifier
) {
    val vaultColors = IdeaVaultTheme.colors
    val fields = remember(customType.fieldsJson) {
        VaultJsonSerializers.decodeCustomFieldDefinitions(customType.fieldsJson)
            .sortedBy { it.orderIndex }
    }
    val badgeColor = parseHexColorSafe(customType.colorHex)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = vaultColors.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("editor_custom_fields_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(badgeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = resolveCustomTypeIcon(customType.iconName),
                            contentDescription = null,
                            tint = accessibleTextColorForBackground(badgeColor),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "${customType.name} Fields",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = vaultColors.primaryText
                        )
                        if (customType.description.isNotBlank()) {
                            Text(
                                text = customType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = vaultColors.secondaryText
                            )
                        }
                    }
                }
                TextButton(
                    onClick = onEditCustomTypeDefinition,
                    modifier = Modifier.testTag("editor_edit_custom_type_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit Fields")
                }
            }

            fields.forEach { field ->
                val rawValue = customFieldsMap[field.id].orEmpty()
                val labelText = if (field.required) "${field.name} *" else field.name
                val fieldError = remember(field, rawValue, showRequiredErrors) {
                    SmartImportExportEngine.validateCustomFieldValue(
                        fieldDef = field,
                        rawValue = rawValue,
                        checkRequired = showRequiredErrors
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (field.type) {
                        CustomFieldType.SHORT_TEXT -> {
                            OutlinedTextField(
                                value = rawValue,
                                onValueChange = { onFieldValueChange(field.id, it) },
                                label = { Text(labelText) },
                                placeholder = if (field.placeholder.isNotBlank()) {
                                    { Text(field.placeholder) }
                                } else null,
                                isError = fieldError != null,
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_field_input_${field.id}")
                            )
                        }
                        CustomFieldType.LONG_TEXT -> {
                            OutlinedTextField(
                                value = rawValue,
                                onValueChange = { onFieldValueChange(field.id, it) },
                                label = { Text(labelText) },
                                isError = fieldError != null,
                                minLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_field_input_${field.id}")
                            )
                        }
                        CustomFieldType.NUMBER -> {
                            OutlinedTextField(
                                value = rawValue,
                                onValueChange = { onFieldValueChange(field.id, it) },
                                label = { Text(labelText) },
                                isError = fieldError != null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_field_input_${field.id}")
                            )
                        }
                        CustomFieldType.DATE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = rawValue,
                                    onValueChange = { onFieldValueChange(field.id, it) },
                                    label = { Text("$labelText (YYYY-MM-DD)") },
                                    isError = fieldError != null,
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("custom_field_input_${field.id}")
                                )
                                OutlinedButton(
                                    onClick = {
                                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                        onFieldValueChange(field.id, today)
                                    },
                                    modifier = Modifier.testTag("custom_field_today_${field.id}")
                                ) {
                                    Text("Today")
                                }
                            }
                        }
                        CustomFieldType.CHECKBOX -> {
                            val isChecked = rawValue.equals("true", ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = vaultColors.secondarySurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onFieldValueChange(field.id, (!isChecked).toString())
                                    }
                                    .testTag("custom_field_checkbox_${field.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { onFieldValueChange(field.id, it.toString()) }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = vaultColors.primaryText
                                    )
                                }
                            }
                        }
                        CustomFieldType.SINGLE_SELECT -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (field.options.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        field.options.forEach { option ->
                                            val selected = rawValue.equals(option, ignoreCase = true)
                                            FilterChip(
                                                selected = selected,
                                                onClick = {
                                                    onFieldValueChange(field.id, if (selected) "" else option)
                                                },
                                                label = { Text(option) },
                                                modifier = Modifier.testTag("custom_field_single_opt_${field.id}_$option")
                                            )
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = rawValue,
                                    onValueChange = { onFieldValueChange(field.id, it) },
                                    label = { Text("Selected ${field.name}") },
                                    isError = fieldError != null,
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_field_input_${field.id}")
                                )
                            }
                        }
                        CustomFieldType.MULTI_SELECT -> {
                            val selectedList = remember(rawValue) {
                                VaultJsonSerializers.decodeStringList(rawValue)
                            }
                            var customOptionText by rememberSaveable(field.id) { mutableStateOf("") }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = labelText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val allOptions = (field.options + selectedList).distinct()
                                if (allOptions.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        allOptions.forEach { option ->
                                            val isSelected = selectedList.any { it.equals(option, ignoreCase = true) }
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    val updated = if (isSelected) {
                                                        selectedList.filterNot { it.equals(option, ignoreCase = true) }
                                                    } else {
                                                        selectedList + option
                                                    }
                                                    onFieldValueChange(field.id, VaultJsonSerializers.encodeStringList(updated))
                                                },
                                                label = { Text(option) },
                                                modifier = Modifier.testTag("custom_field_multi_opt_${field.id}_$option")
                                            )
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customOptionText,
                                        onValueChange = { customOptionText = it },
                                        label = { Text("Add option to ${field.name}") },
                                        isError = fieldError != null,
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("custom_field_input_${field.id}")
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val trimmed = customOptionText.trim()
                                            if (trimmed.isNotEmpty()) {
                                                val updated = (selectedList + trimmed).distinct()
                                                onFieldValueChange(field.id, VaultJsonSerializers.encodeStringList(updated))
                                                customOptionText = ""
                                            }
                                        }
                                    ) {
                                        Text("Add")
                                    }
                                }
                            }
                        }
                        CustomFieldType.URL -> {
                            OutlinedTextField(
                                value = rawValue,
                                onValueChange = { onFieldValueChange(field.id, it) },
                                label = { Text("$labelText (https://...)") },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                isError = fieldError != null,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("custom_field_input_${field.id}")
                            )
                        }
                        CustomFieldType.CODE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (rawValue.isNotEmpty()) {
                                        TextButton(
                                            onClick = { onCopyCodeSnippet(rawValue) },
                                            modifier = Modifier.testTag("custom_field_editor_copy_code_${field.id}")
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Copy Code")
                                        }
                                    }
                                }
                                OutlinedTextField(
                                    value = rawValue,
                                    onValueChange = { onFieldValueChange(field.id, it) },
                                    label = { Text("Code (${field.name})") },
                                    isError = fieldError != null,
                                    minLines = 4,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_field_input_${field.id}")
                                )
                            }
                        }
                        CustomFieldType.CHECKLIST -> {
                            val checklistItems = remember(rawValue) {
                                VaultJsonSerializers.decodeCustomChecklistItems(rawValue)
                            }
                            var newItemText by rememberSaveable(field.id) { mutableStateOf("") }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "$labelText (${checklistItems.count { it.checked }}/${checklistItems.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                checklistItems.forEachIndexed { idx, chk ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    val updated = checklistItems.toMutableList()
                                                    updated[idx] = chk.copy(checked = !chk.checked)
                                                    onFieldValueChange(
                                                        field.id,
                                                        VaultJsonSerializers.encodeCustomChecklistItems(updated)
                                                    )
                                                }
                                        ) {
                                            Checkbox(
                                                checked = chk.checked,
                                                onCheckedChange = { checked ->
                                                    val updated = checklistItems.toMutableList()
                                                    updated[idx] = chk.copy(checked = checked)
                                                    onFieldValueChange(
                                                        field.id,
                                                        VaultJsonSerializers.encodeCustomChecklistItems(updated)
                                                    )
                                                }
                                            )
                                            Text(
                                                text = chk.text,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textDecoration = if (chk.checked) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val updated = checklistItems.filterIndexed { i, _ -> i != idx }
                                                onFieldValueChange(
                                                    field.id,
                                                    VaultJsonSerializers.encodeCustomChecklistItems(updated)
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove checklist item")
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newItemText,
                                        onValueChange = { newItemText = it },
                                        label = { Text("Add item to ${field.name}") },
                                        isError = fieldError != null,
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("custom_field_input_${field.id}")
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val trimmed = newItemText.trim()
                                            if (trimmed.isNotEmpty()) {
                                                val updated = checklistItems + CustomChecklistItem(
                                                    id = "chk_${UUID.randomUUID()}",
                                                    text = trimmed,
                                                    checked = false
                                                )
                                                onFieldValueChange(
                                                    field.id,
                                                    VaultJsonSerializers.encodeCustomChecklistItems(updated)
                                                )
                                                newItemText = ""
                                            }
                                        },
                                        modifier = Modifier.testTag("custom_field_add_checklist_${field.id}")
                                    ) {
                                        Text("Add")
                                    }
                                }
                            }
                        }
                    }

                    if (fieldError != null) {
                        Text(
                            text = fieldError,
                            style = MaterialTheme.typography.labelSmall,
                            color = vaultColors.coral,
                            modifier = Modifier.testTag("custom_field_error_${field.id}")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detail View section displaying all custom fields of a CustomTypeEntity in ItemDetailScreen.
 */
@Composable
fun CustomFieldsDetailSection(
    customType: CustomTypeEntity,
    customFieldsMap: Map<String, String>,
    onUpdateCustomFields: (Map<String, String>) -> Unit,
    onCopyRawCode: (String, String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val vaultColors = IdeaVaultTheme.colors
    val fields = remember(customType.fieldsJson) {
        VaultJsonSerializers.decodeCustomFieldDefinitions(customType.fieldsJson)
            .sortedBy { it.orderIndex }
    }
    val nonEmptyFields = fields.filter { !customFieldsMap[it.id].isNullOrBlank() }
    if (nonEmptyFields.isEmpty()) return

    val badgeColor = parseHexColorSafe(customType.colorHex)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = vaultColors.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("detail_custom_fields_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = resolveCustomTypeIcon(customType.iconName),
                        contentDescription = null,
                        tint = accessibleTextColorForBackground(badgeColor),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = customType.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = vaultColors.primaryText
                )
            }

            nonEmptyFields.forEach { field ->
                val rawValue = customFieldsMap[field.id].orEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_custom_field_${field.id}"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = field.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = vaultColors.secondaryText,
                        fontWeight = FontWeight.SemiBold
                    )

                    when (field.type) {
                        CustomFieldType.CODE -> {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = vaultColors.secondarySurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Code",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = vaultColors.secondaryText
                                        )
                                        OutlinedButton(
                                            onClick = { onCopyRawCode(field.name, rawValue) },
                                            modifier = Modifier.testTag("custom_field_copy_code_${field.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy Code for ${field.name}",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Copy Code")
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = rawValue,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = vaultColors.primaryText
                                    )
                                }
                            }
                        }
                        CustomFieldType.CHECKLIST -> {
                            val items = VaultJsonSerializers.decodeCustomChecklistItems(rawValue)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items.forEachIndexed { idx, chk ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val updatedList = items.toMutableList()
                                                updatedList[idx] = chk.copy(checked = !chk.checked)
                                                val updatedMap = customFieldsMap.toMutableMap()
                                                updatedMap[field.id] = VaultJsonSerializers.encodeCustomChecklistItems(updatedList)
                                                onUpdateCustomFields(updatedMap)
                                            }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (chk.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                            contentDescription = if (chk.checked) "Completed" else "Incomplete",
                                            tint = if (chk.checked) vaultColors.lime else vaultColors.secondaryText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = chk.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (chk.checked) vaultColors.secondaryText else vaultColors.primaryText,
                                            textDecoration = if (chk.checked) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    }
                                }
                            }
                        }
                        CustomFieldType.URL -> {
                            Text(
                                text = rawValue,
                                style = MaterialTheme.typography.bodyMedium,
                                color = vaultColors.coral,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { onOpenUrl(rawValue) }
                            )
                        }
                        else -> {
                            Text(
                                text = SmartImportExportEngine.formatCustomFieldValueReadable(field, rawValue),
                                style = MaterialTheme.typography.bodyLarge,
                                color = vaultColors.primaryText
                            )
                        }
                    }
                }
            }
        }
    }
}
