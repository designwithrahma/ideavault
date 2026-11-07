package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.CustomTypeEntity
import com.example.data.MethodImportMode
import com.example.data.MethodRouteWithSteps
import com.example.data.MethodTargetRouteChoice
import com.example.data.PlainTextMethodOption
import com.example.data.SmartImportExportEngine
import com.example.data.SmartImportFormat
import com.example.data.SmartImportValidationResult
import com.example.data.VaultCategory
import com.example.data.VaultItemWithDetails
import com.example.data.VaultJsonSerializers
import com.example.ui.theme.IdeaVaultTheme
import com.example.ui.theme.VaultYellow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Hybrid in-memory + disk-backed session buffer keyed by a lightweight session ID so that large
 * paste inputs (1–5 MB) survive both configuration changes (screen rotation) AND process death
 * without ever being serialized into the Android SavedInstanceState Bundle
 * (preventing TransactionTooLargeException — Audit P1-13 & FIX 05).
 *
 * Temporary session files are stored in the app's private cache directory (`smart_import_sessions`)
 * and are deleted immediately when the import is confirmed, cancelled, or dismissed.
 */
object SmartImportSessionBuffer {
    private const val RECOVERY_DIR_NAME = "smart_import_sessions"
    private val buffer = ConcurrentHashMap<String, String>()
    @Volatile
    private var configuredCacheRoot: File? = null

    fun configureCacheDir(cacheDir: File?) {
        configuredCacheRoot = cacheDir
    }

    private fun resolveSessionFile(key: String, context: Context? = null): File? {
        val baseDir = context?.cacheDir ?: configuredCacheRoot ?: return null
        val safeName = key.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "default" }
        val dir = File(baseDir, RECOVERY_DIR_NAME)
        return File(dir, "$safeName.tmp")
    }

    fun get(key: String, context: Context? = null): String {
        if (context != null) configureCacheDir(context.cacheDir)
        buffer[key]?.let { return it }
        val sessionFile = resolveSessionFile(key, context) ?: return ""
        return try {
            if (sessionFile.exists() && sessionFile.isFile) {
                val recovered = sessionFile.readText(Charsets.UTF_8)
                buffer[key] = recovered
                recovered
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun put(key: String, value: String, context: Context? = null) {
        if (context != null) configureCacheDir(context.cacheDir)
        buffer[key] = value
        val sessionFile = resolveSessionFile(key, context) ?: return
        try {
            val parent = sessionFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            if (value.isEmpty()) {
                sessionFile.delete()
            } else {
                val tempFile = File(parent, "${sessionFile.name}.writing")
                tempFile.writeText(value, Charsets.UTF_8)
                if (!tempFile.renameTo(sessionFile)) {
                    sessionFile.writeText(value, Charsets.UTF_8)
                    tempFile.delete()
                }
            }
        } catch (_: Exception) {
            // Best-effort disk cache fallback; in-memory buffer remains authoritative
        }
    }

    fun remove(key: String, context: Context? = null) {
        if (context != null) configureCacheDir(context.cacheDir)
        buffer.remove(key)
        try {
            val sessionFile = resolveSessionFile(key, context)
            sessionFile?.delete()
            sessionFile?.parentFile?.let { dir ->
                File(dir, "${sessionFile.name}.writing").delete()
            }
        } catch (_: Exception) {
        }
    }

    fun hasDiskRecoveryFile(key: String, context: Context? = null): Boolean {
        val sessionFile = resolveSessionFile(key, context) ?: return false
        return sessionFile.exists() && sessionFile.length() > 0L
    }

    /**
     * Clears only the in-memory map (used by tests to simulate Android process death
     * while preserving the private disk cache directory).
     */
    fun clearMemoryOnly() {
        buffer.clear()
    }

    fun clearAll(context: Context? = null) {
        if (context != null) configureCacheDir(context.cacheDir)
        buffer.clear()
        try {
            val baseDir = context?.cacheDir ?: configuredCacheRoot ?: return
            val dir = File(baseDir, RECOVERY_DIR_NAME)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {
        }
    }
}

/**
 * Copies text to the Android system clipboard safely.
 */
fun copyTextToSystemClipboard(
    context: Context,
    label: String,
    text: String
): Boolean {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Reads plain text from the Android system clipboard safely.
 */
fun readTextFromSystemClipboard(context: Context): String? {
    return try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        if (!clipboard.hasPrimaryClip()) return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) return null
        val item = clip.getItemAt(0) ?: return null
        val text = item.coerceToText(context)?.toString()
        text?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

/**
 * Complete import request confirmed by the user from [SmartImportBottomSheet].
 */
data class ConfirmedSmartImportPayload(
    val validationResult: SmartImportValidationResult,
    val methodImportMode: MethodImportMode,
    val methodTargetRouteChoice: MethodTargetRouteChoice,
    val targetExistingRouteId: String?,
    val newAlternativeRouteName: String,
    val confirmCreateNewCustomType: Boolean,
    val resolvedIdeaDescriptionChoice: String, // "BOTH", "DESCRIPTION", or "SUMMARY"
    val keepOriginalCategory: Boolean = false,
    val useSecondaryMainSteps: Boolean = false,
    val fallbackExistingCustomTypeId: String? = null,
    val preserveUnmappedFieldsInNotes: Boolean = true,
    val mergeTitle: Boolean = true,
    val mergeDescription: Boolean = true,
    val mergeMainSteps: Boolean = true,
    val mergeAltRoutes: Boolean = true,
    val mergeTags: Boolean = true,
    val mergeNotes: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SmartImportBottomSheet(
    defaultCategory: VaultCategory,
    initialMethodImportMode: MethodImportMode = MethodImportMode.FULL_REPLACE,
    activeCustomType: CustomTypeEntity? = null,
    existingCustomTypes: List<CustomTypeEntity> = emptyList(),
    existingAlternativeRoutes: List<MethodRouteWithSteps> = emptyList(),
    hasExistingDraftContent: Boolean = false,
    onDismiss: () -> Unit,
    onShowMessage: (String) -> Unit,
    onConfirmImport: (ConfirmedSmartImportPayload) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Audit P1-13 & FIX 05: Store only a small UUID key in Bundle; keep raw text in hybrid memory+disk SmartImportSessionBuffer
    val sessionKey = rememberSaveable { UUID.randomUUID().toString() }
    var rawInput by remember(sessionKey) {
        mutableStateOf(SmartImportSessionBuffer.get(sessionKey, context))
    }
    fun updateRawInput(newText: String) {
        rawInput = newText
        SmartImportSessionBuffer.put(sessionKey, newText, context)
    }

    var selectedFormat by rememberSaveable { mutableStateOf(SmartImportFormat.AUTO) }
    var methodImportMode by rememberSaveable(initialMethodImportMode) {
        mutableStateOf(initialMethodImportMode)
    }
    var methodTargetChoice by rememberSaveable {
        mutableStateOf(MethodTargetRouteChoice.MAIN_METHOD)
    }
    var selectedExistingAltRouteId by rememberSaveable {
        mutableStateOf(existingAlternativeRoutes.firstOrNull()?.id ?: "")
    }
    var newAlternativeRouteName by rememberSaveable {
        mutableStateOf("Alternative Method ${existingAlternativeRoutes.size + 1}")
    }
    var plainTextMethodOption by rememberSaveable {
        mutableStateOf(PlainTextMethodOption.NUMBERED_LINES_TO_STEPS)
    }
    var allowSkipEmptySteps by rememberSaveable { mutableStateOf(false) }
    var allowUnknownRichBlocks by rememberSaveable { mutableStateOf(false) }

    // Audit P1-14: Keep selectedExistingAltRouteId synchronized with live routes
    LaunchedEffect(existingAlternativeRoutes) {
        if (existingAlternativeRoutes.isEmpty()) {
            if (methodTargetChoice == MethodTargetRouteChoice.EXISTING_ALTERNATIVE) {
                methodTargetChoice = MethodTargetRouteChoice.MAIN_METHOD
            }
            selectedExistingAltRouteId = ""
        } else if (selectedExistingAltRouteId.isBlank()) {
            selectedExistingAltRouteId = existingAlternativeRoutes.first().id
        }
    }

    val activeCustomFields = remember(activeCustomType) {
        activeCustomType?.let { VaultJsonSerializers.decodeCustomFieldDefinitions(it.fieldsJson) }.orEmpty()
    }
    var targetCustomFieldId by rememberSaveable(activeCustomType?.id) {
        mutableStateOf(activeCustomFields.firstOrNull()?.id ?: "")
    }

    // Audit P1-08: Revision-tracked validation to prevent stale async validation results
    var inputRevision by remember { mutableLongStateOf(0L) }
    var validatedRevision by remember { mutableLongStateOf(-1L) }
    var validationJob by remember { mutableStateOf<Job?>(null) }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<SmartImportValidationResult?>(null) }

    var inlineNoticeMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmCreateNewCustomType by rememberSaveable { mutableStateOf(true) }
    var fallbackExistingCustomTypeId by rememberSaveable { mutableStateOf<String?>(null) }
    var preserveUnmappedFieldsInNotes by rememberSaveable { mutableStateOf(true) }
    var ideaConflictChoice by rememberSaveable { mutableStateOf("BOTH") }
    var useSecondaryMainSteps by rememberSaveable { mutableStateOf(false) }
    var keepOriginalCategory by rememberSaveable { mutableStateOf(false) }
    var showFullPreview by rememberSaveable { mutableStateOf(false) }
    var showReplaceConfirmationDialog by rememberSaveable { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }

    // Audit P1-07: Merge Selected Fields toggles
    var mergeTitle by rememberSaveable { mutableStateOf(true) }
    var mergeDescription by rememberSaveable { mutableStateOf(true) }
    var mergeMainSteps by rememberSaveable { mutableStateOf(true) }
    var mergeAltRoutes by rememberSaveable { mutableStateOf(true) }
    var mergeTags by rememberSaveable { mutableStateOf(true) }
    var mergeNotes by rememberSaveable { mutableStateOf(true) }

    fun invalidateCurrentValidation() {
        validationJob?.cancel()
        validationJob = null
        isValidating = false
        inputRevision += 1L
        validationResult = null
    }

    fun triggerValidation(inputToValidate: String = rawInput) {
        validationJob?.cancel()
        validationJob = null
        inlineNoticeMessage = null
        val rev = inputRevision
        if (inputToValidate.length < 25_000) {
            isValidating = false
            val res = SmartImportExportEngine.validateAndPreviewImportSync(
                rawInput = inputToValidate,
                selectedFormat = selectedFormat,
                defaultCategory = defaultCategory,
                activeCustomType = activeCustomType,
                existingCustomTypes = existingCustomTypes,
                plainTextMethodOption = plainTextMethodOption,
                targetCustomFieldId = targetCustomFieldId.ifBlank { null },
                allowSkipEmptySteps = allowSkipEmptySteps,
                allowUnknownRichBlocks = allowUnknownRichBlocks
            )
            validationResult = res
            validatedRevision = rev
        } else {
            isValidating = true
            validationJob = scope.launch {
                val res = SmartImportExportEngine.validateAndPreviewImport(
                    rawInput = inputToValidate,
                    selectedFormat = selectedFormat,
                    defaultCategory = defaultCategory,
                    activeCustomType = activeCustomType,
                    existingCustomTypes = existingCustomTypes,
                    plainTextMethodOption = plainTextMethodOption,
                    targetCustomFieldId = targetCustomFieldId.ifBlank { null },
                    allowSkipEmptySteps = allowSkipEmptySteps,
                    allowUnknownRichBlocks = allowUnknownRichBlocks
                )
                if (rev == inputRevision && inputToValidate == rawInput) {
                    validationResult = res
                    validatedRevision = rev
                    isValidating = false
                }
            }
        }
    }

    fun buildPayload(confirmedRes: SmartImportValidationResult): ConfirmedSmartImportPayload {
        return ConfirmedSmartImportPayload(
            validationResult = confirmedRes,
            methodImportMode = methodImportMode,
            methodTargetRouteChoice = methodTargetChoice,
            targetExistingRouteId = selectedExistingAltRouteId.ifBlank { null },
            newAlternativeRouteName = newAlternativeRouteName.trim().ifBlank { "Alternative Method" },
            confirmCreateNewCustomType = confirmCreateNewCustomType,
            resolvedIdeaDescriptionChoice = ideaConflictChoice,
            keepOriginalCategory = keepOriginalCategory,
            useSecondaryMainSteps = useSecondaryMainSteps,
            fallbackExistingCustomTypeId = fallbackExistingCustomTypeId,
            preserveUnmappedFieldsInNotes = preserveUnmappedFieldsInNotes,
            mergeTitle = mergeTitle,
            mergeDescription = mergeDescription,
            mergeMainSteps = mergeMainSteps,
            mergeAltRoutes = mergeAltRoutes,
            mergeTags = mergeTags,
            mergeNotes = mergeNotes
        )
    }

    val vaultColors = IdeaVaultTheme.colors
    ModalBottomSheet(
        onDismissRequest = {
            SmartImportSessionBuffer.remove(sessionKey, context)
            onDismiss()
        },
        containerColor = vaultColors.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("smart_import_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (defaultCategory == VaultCategory.METHOD) {
                        methodImportMode.label
                    } else {
                        "Paste & Convert (Smart Import)"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = vaultColors.primaryText
                )
                Text(
                    text = "100% Offline • Paste JSON, Markdown, or Text to preview & import into draft",
                    style = MaterialTheme.typography.bodySmall,
                    color = vaultColors.secondaryText
                )
            }

            // Format Selector
            Text(
                text = "Input Format",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmartImportFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = selectedFormat == fmt,
                        onClick = {
                            selectedFormat = fmt
                            invalidateCurrentValidation()
                            if (rawInput.isNotBlank()) triggerValidation(rawInput)
                        },
                        label = { Text(fmt.label) },
                        modifier = Modifier.testTag("smart_import_format_${fmt.name.lowercase()}")
                    )
                }
            }

            // Method Mode Selector (Import Method vs Merge Selected Fields vs Bulk Add Steps)
            val effectiveTargetCategory = if (keepOriginalCategory) {
                defaultCategory
            } else {
                validationResult?.targetCategory ?: defaultCategory
            }
            if (defaultCategory == VaultCategory.METHOD ||
                effectiveTargetCategory == VaultCategory.METHOD
            ) {
                Text(
                    text = "Method Import Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MethodImportMode.entries.forEach { mode ->
                        FilterChip(
                            selected = methodImportMode == mode,
                            onClick = {
                                methodImportMode = mode
                                inlineNoticeMessage = null
                            },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("smart_import_method_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                if (methodImportMode == MethodImportMode.MERGE_SELECTED_FIELDS) {
                    Text(
                        text = "Fields to Merge into Draft",
                        style = MaterialTheme.typography.labelSmall,
                        color = vaultColors.secondaryText
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = mergeTitle,
                            onClick = { mergeTitle = !mergeTitle },
                            label = { Text("Title") },
                            modifier = Modifier.testTag("smart_import_merge_field_title")
                        )
                        FilterChip(
                            selected = mergeDescription,
                            onClick = { mergeDescription = !mergeDescription },
                            label = { Text("Description") },
                            modifier = Modifier.testTag("smart_import_merge_field_description")
                        )
                        FilterChip(
                            selected = mergeMainSteps,
                            onClick = { mergeMainSteps = !mergeMainSteps },
                            label = { Text("Main Steps") },
                            modifier = Modifier.testTag("smart_import_merge_field_main_steps")
                        )
                        FilterChip(
                            selected = mergeAltRoutes,
                            onClick = { mergeAltRoutes = !mergeAltRoutes },
                            label = { Text("Alternative Methods") },
                            modifier = Modifier.testTag("smart_import_merge_field_alt_routes")
                        )
                        FilterChip(
                            selected = mergeTags,
                            onClick = { mergeTags = !mergeTags },
                            label = { Text("Tags") },
                            modifier = Modifier.testTag("smart_import_merge_field_tags")
                        )
                        FilterChip(
                            selected = mergeNotes,
                            onClick = { mergeNotes = !mergeNotes },
                            label = { Text("Notes") },
                            modifier = Modifier.testTag("smart_import_merge_field_notes")
                        )
                    }
                }

                if (methodImportMode == MethodImportMode.BULK_APPEND_STEPS) {
                    Text(
                        text = "Append Steps To",
                        style = MaterialTheme.typography.labelSmall,
                        color = vaultColors.secondaryText
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MethodTargetRouteChoice.entries.forEach { targetChoice ->
                            if (targetChoice == MethodTargetRouteChoice.EXISTING_ALTERNATIVE &&
                                existingAlternativeRoutes.isEmpty()
                            ) {
                                return@forEach
                            }
                            FilterChip(
                                selected = methodTargetChoice == targetChoice,
                                onClick = {
                                    methodTargetChoice = targetChoice
                                    inlineNoticeMessage = null
                                },
                                label = { Text(targetChoice.label) },
                                modifier = Modifier.testTag("smart_import_route_target_${targetChoice.name.lowercase()}")
                            )
                        }
                    }

                    if (methodTargetChoice == MethodTargetRouteChoice.EXISTING_ALTERNATIVE &&
                        existingAlternativeRoutes.isNotEmpty()
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            existingAlternativeRoutes.forEach { altRoute ->
                                FilterChip(
                                    selected = selectedExistingAltRouteId == altRoute.id,
                                    onClick = {
                                        selectedExistingAltRouteId = altRoute.id
                                        inlineNoticeMessage = null
                                    },
                                    label = { Text(altRoute.name) },
                                    modifier = Modifier.testTag("smart_import_existing_alt_route_${altRoute.id}")
                                )
                            }
                        }
                    } else if (methodTargetChoice == MethodTargetRouteChoice.NEW_ALTERNATIVE) {
                        OutlinedTextField(
                            value = newAlternativeRouteName,
                            onValueChange = { newAlternativeRouteName = it },
                            label = { Text("New Alternative Method Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("smart_import_new_alt_route_name_input")
                        )
                    }
                }

                // Plain text method conversion options when format is Plain Text
                val effectiveFmt = if (selectedFormat == SmartImportFormat.AUTO) {
                    SmartImportExportEngine.detectFormat(rawInput, defaultCategory)
                } else selectedFormat
                if (effectiveFmt == SmartImportFormat.PLAIN_TEXT) {
                    Text(
                        text = "Plain Text Conversion",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PlainTextMethodOption.entries.forEach { opt ->
                            FilterChip(
                                selected = plainTextMethodOption == opt,
                                onClick = {
                                    plainTextMethodOption = opt
                                    invalidateCurrentValidation()
                                    if (rawInput.isNotBlank()) triggerValidation(rawInput)
                                },
                                label = { Text(opt.label) },
                                modifier = Modifier.testTag("smart_import_plaintext_opt_${opt.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Custom Type field target selector for plain text / markdown import
            if (activeCustomType != null && activeCustomFields.isNotEmpty() &&
                selectedFormat != SmartImportFormat.JSON
            ) {
                Text(
                    text = "Target Field in ${activeCustomType.name}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    activeCustomFields.forEach { field ->
                        FilterChip(
                            selected = targetCustomFieldId == field.id,
                            onClick = {
                                targetCustomFieldId = field.id
                                invalidateCurrentValidation()
                                if (rawInput.isNotBlank()) triggerValidation(rawInput)
                            },
                            label = { Text(field.name) }
                        )
                    }
                }
            }

            // Paste Input & Clipboard Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val clipText = readTextFromSystemClipboard(context)
                        if (clipText.isNullOrBlank()) {
                            val msg = "Clipboard is empty or does not contain text."
                            inlineNoticeMessage = msg
                            onShowMessage(msg)
                        } else {
                            inlineNoticeMessage = null
                            updateRawInput(clipText)
                            invalidateCurrentValidation()
                            triggerValidation(clipText)
                        }
                    },
                    modifier = Modifier.testTag("smart_import_paste_clipboard_button")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Paste from Clipboard")
                }

                Button(
                    onClick = { triggerValidation(rawInput) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.secondarySurface,
                        contentColor = vaultColors.primaryText
                    ),
                    modifier = Modifier.testTag("smart_import_validate_button")
                ) {
                    Text("Validate & Preview", fontWeight = FontWeight.SemiBold)
                }
            }

            // Audit P2-05 / P1-14 / P2-13: Inline notice/error banner visible directly inside ModalBottomSheet
            if (inlineNoticeMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = vaultColors.coral.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_import_inline_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = vaultColors.coral,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = inlineNoticeMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.primaryText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            OutlinedTextField(
                value = rawInput,
                onValueChange = {
                    updateRawInput(it)
                    inlineNoticeMessage = null
                    invalidateCurrentValidation()
                },
                label = {
                    Text(
                        if (defaultCategory == VaultCategory.METHOD) {
                            "Paste Method JSON, Markdown, or numbered steps"
                        } else {
                            "Paste JSON, Markdown, or Plain Text"
                        }
                    )
                },
                minLines = 5,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("smart_import_input")
            )

            // Validation options for strict steps / unknown rich blocks (Audit P1-15, P2-04)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = allowSkipEmptySteps,
                    onClick = {
                        allowSkipEmptySteps = !allowSkipEmptySteps
                        invalidateCurrentValidation()
                        if (rawInput.isNotBlank()) triggerValidation(rawInput)
                    },
                    label = { Text("Skip Empty Steps") },
                    modifier = Modifier.testTag("smart_import_skip_empty_steps_toggle")
                )
                FilterChip(
                    selected = allowUnknownRichBlocks,
                    onClick = {
                        allowUnknownRichBlocks = !allowUnknownRichBlocks
                        invalidateCurrentValidation()
                        if (rawInput.isNotBlank()) triggerValidation(rawInput)
                    },
                    label = { Text("Convert Unknown Rich Blocks to Text") },
                    modifier = Modifier.testTag("smart_import_allow_unknown_blocks_toggle")
                )
            }

            if (isValidating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("smart_import_validating_indicator")
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Validating large input...",
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.secondaryText
                    )
                }
            }

            // Validation & Preview Output
            val result = validationResult
            if (result != null) {
                HorizontalDivider(color = vaultColors.border)
                if (!result.isValid) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = vaultColors.coral.copy(alpha = 0.16f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart_import_error_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = vaultColors.coral
                                )
                                Text(
                                    text = "Validation Failed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = vaultColors.primaryText
                                )
                            }
                            result.errors.forEach { err ->
                                Text(
                                    text = "• $err",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vaultColors.primaryText
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = vaultColors.secondarySurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("smart_import_preview_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = vaultColors.lime
                                    )
                                    Text(
                                        text = "Preview Ready (${result.detectedFormat.label})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = vaultColors.primaryText
                                    )
                                }
                                TextButton(
                                    onClick = { showFullPreview = !showFullPreview },
                                    modifier = Modifier.testTag("smart_import_toggle_full_preview")
                                ) {
                                    Text(if (showFullPreview) "Compact Preview" else "Full Preview")
                                }
                            }

                            val typeSummary = result.customTypeName ?: result.targetCategory.label
                            Text(
                                text = "Detected Type: $typeSummary",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = vaultColors.primaryText
                            )

                            // Audit P2-12: Category switch confirmation when JSON type differs from current editor category
                            if (result.detectedCategoryDiffersFromDefault) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = vaultColors.actionYellow.copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("smart_import_category_switch_warning")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Category Change: Current editor is '${defaultCategory.label}', but input is '${result.targetCategory.label}'.",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(
                                                selected = !keepOriginalCategory,
                                                onClick = { keepOriginalCategory = false },
                                                label = { Text("Switch to ${result.targetCategory.label}") },
                                                modifier = Modifier.testTag("smart_import_confirm_switch_category")
                                            )
                                            FilterChip(
                                                selected = keepOriginalCategory,
                                                onClick = { keepOriginalCategory = true },
                                                label = { Text("Keep ${defaultCategory.label}") },
                                                modifier = Modifier.testTag("smart_import_keep_original_category")
                                            )
                                        }
                                    }
                                }
                            }

                            if (result.title.isNotBlank()) {
                                Text(
                                    text = "Title: ${result.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("smart_import_preview_title")
                                )
                            }
                            if (result.description.isNotBlank()) {
                                val descText = if (showFullPreview || result.description.length <= 180) {
                                    result.description
                                } else {
                                    "${result.description.take(180)}..."
                                }
                                Text(
                                    text = "Description: $descText",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("smart_import_preview_description")
                                )
                            }
                            if (result.content.isNotBlank()) {
                                val contentText = if (showFullPreview || result.content.length <= 240) {
                                    result.content
                                } else {
                                    "${result.content.take(240)}..."
                                }
                                Text(
                                    text = "Content / Prompt: $contentText",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.testTag("smart_import_preview_content")
                                )
                            }
                            if (result.tags.isNotEmpty()) {
                                Text(
                                    text = "Tags (${result.tags.size}): ${result.tags.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (result.richBlocks.isNotEmpty()) {
                                Text(
                                    text = "Rich Blocks: ${result.richBlocks.size}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (result.customFieldValuesById.isNotEmpty() || result.unmappedCustomFields.isNotEmpty()) {
                                Text(
                                    text = "Custom Fields Mapped: ${result.customFieldValuesById.size}" +
                                        if (result.unmappedCustomFields.isNotEmpty()) {
                                            " (${result.unmappedCustomFields.size} extra unmapped field(s))"
                                        } else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            // Audit P1-04: Explicit visibility & control for unmapped custom fields
                            if (result.unmappedCustomFields.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = vaultColors.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("smart_import_unmapped_fields_preview")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "Unmapped Custom Fields (${result.unmappedCustomFields.size}):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        result.unmappedCustomFields.entries.forEach { (k, v) ->
                                            Text(
                                                text = "• $k: $v",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = preserveUnmappedFieldsInNotes,
                                                onCheckedChange = { preserveUnmappedFieldsInNotes = it },
                                                modifier = Modifier.testTag("smart_import_preserve_unmapped_checkbox")
                                            )
                                            Text(
                                                text = "Preserve unmapped fields in entry Notes",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }

                            // Field conflicts (Idea description vs summary OR Method mainMethod.steps vs steps)
                            if (result.fieldConflicts.isNotEmpty()) {
                                result.fieldConflicts.forEach { conflict ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = vaultColors.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Field Conflict: '${conflict.primaryLabel}' vs '${conflict.secondaryLabel}'",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (conflict.fieldName == "mainMethod.steps_vs_steps") {
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    FilterChip(
                                                        selected = !useSecondaryMainSteps,
                                                        onClick = { useSecondaryMainSteps = false },
                                                        label = { Text("Use ${conflict.primaryLabel}") },
                                                        modifier = Modifier.testTag("smart_import_conflict_use_main_method_steps")
                                                    )
                                                    FilterChip(
                                                        selected = useSecondaryMainSteps,
                                                        onClick = { useSecondaryMainSteps = true },
                                                        label = { Text("Use ${conflict.secondaryLabel}") },
                                                        modifier = Modifier.testTag("smart_import_conflict_use_root_steps")
                                                    )
                                                }
                                            } else {
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    FilterChip(
                                                        selected = ideaConflictChoice == "BOTH",
                                                        onClick = { ideaConflictChoice = "BOTH" },
                                                        label = { Text("Keep Both") }
                                                    )
                                                    FilterChip(
                                                        selected = ideaConflictChoice == "DESCRIPTION",
                                                        onClick = { ideaConflictChoice = "DESCRIPTION" },
                                                        label = { Text("Use ${conflict.primaryLabel}") }
                                                    )
                                                    FilterChip(
                                                        selected = ideaConflictChoice == "SUMMARY",
                                                        onClick = { ideaConflictChoice = "SUMMARY" },
                                                        label = { Text("Use ${conflict.secondaryLabel}") }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Confirmation for new Custom Type creation (Audit P1-05)
                            if (result.requiresCustomTypeCreationConfirmation && result.newCustomTypeDraft != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = vaultColors.lavender.copy(alpha = 0.2f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = confirmCreateNewCustomType,
                                                onCheckedChange = { confirmCreateNewCustomType = it },
                                                modifier = Modifier.testTag("smart_import_confirm_new_custom_type_checkbox")
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Create new Custom Type '${result.newCustomTypeDraft.name}' (${result.newCustomTypeDraft.fields.size} fields)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Uncheck to import without creating a new Custom Type (all field values will be formatted into readable notes or mapped to an existing Custom Type).",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        if (!confirmCreateNewCustomType && existingCustomTypes.isNotEmpty()) {
                                            Text(
                                                text = "Fallback Destination:",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                FilterChip(
                                                    selected = fallbackExistingCustomTypeId == null,
                                                    onClick = { fallbackExistingCustomTypeId = null },
                                                    label = { Text("Standard Entry (Notes)") },
                                                    modifier = Modifier.testTag("smart_import_fallback_standard_entry")
                                                )
                                                existingCustomTypes.forEach { ct ->
                                                    FilterChip(
                                                        selected = fallbackExistingCustomTypeId == ct.id,
                                                        onClick = { fallbackExistingCustomTypeId = ct.id },
                                                        label = { Text(ct.name) },
                                                        modifier = Modifier.testTag("smart_import_fallback_ct_${ct.id}")
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Warnings
                            result.warnings.forEach { warn ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = vaultColors.coral,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = warn,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = vaultColors.secondaryText
                                    )
                                }
                            }

                            // Lazy-loaded Steps Preview (1–500 steps)
                            val activeMainSteps = if (useSecondaryMainSteps && result.secondaryMainSteps.isNotEmpty()) {
                                result.secondaryMainSteps
                            } else {
                                result.mainSteps
                            }
                            val effectiveStepCount = activeMainSteps.size + result.alternativeRoutes.sumOf { it.steps.size }
                            if (effectiveStepCount > 0) {
                                Text(
                                    text = "Total Detected Steps: $effectiveStepCount" +
                                        if (result.alternativeRoutes.isNotEmpty()) {
                                            " (${activeMainSteps.size} in Main + ${result.alternativeRoutes.size} Alternative Method(s))"
                                        } else "",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("smart_import_detected_steps_count")
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = if (showFullPreview) 360.dp else 220.dp)
                                        .testTag("smart_import_steps_preview_list"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(
                                        items = activeMainSteps,
                                        key = { "main_${it.stepNumber}_${it.title}" }
                                    ) { step ->
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = vaultColors.surface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "${step.stepNumber}. ${step.title}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = vaultColors.primaryText
                                                )
                                                if (step.details.isNotBlank()) {
                                                    Text(
                                                        text = step.details,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = vaultColors.secondaryText,
                                                        maxLines = if (showFullPreview) Int.MAX_VALUE else 2,
                                                        overflow = if (showFullPreview) TextOverflow.Clip else TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (showFullPreview && step.notes.isNotBlank()) {
                                                    Text(
                                                        text = "Notes: ${step.notes}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = vaultColors.secondaryText
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    result.alternativeRoutes.forEachIndexed { routeIdx, altRoute ->
                                        item(key = "alt_header_$routeIdx") {
                                            Text(
                                                text = "Alternative Method: ${altRoute.name} (${altRoute.steps.size} steps)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = vaultColors.primaryText,
                                                modifier = Modifier.padding(top = 6.dp)
                                            )
                                        }
                                        items(
                                            items = altRoute.steps,
                                            key = { "alt_${routeIdx}_${it.stepNumber}_${it.title}" }
                                        ) { step ->
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = vaultColors.surface,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Text(
                                                        text = "${step.stepNumber}. ${step.title}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = vaultColors.primaryText
                                                    )
                                                    if (step.details.isNotBlank()) {
                                                        Text(
                                                            text = step.details,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = vaultColors.secondaryText,
                                                            maxLines = if (showFullPreview) Int.MAX_VALUE else 2,
                                                            overflow = if (showFullPreview) TextOverflow.Clip else TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        SmartImportSessionBuffer.remove(sessionKey, context)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("smart_import_cancel_button")
                ) {
                    Text("Cancel")
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (isImporting || isValidating) return@Button
                        val currentResult = if (validationResult != null && validatedRevision == inputRevision) {
                            validationResult!!
                        } else {
                            if (rawInput.length >= 25_000) {
                                triggerValidation(rawInput)
                                return@Button
                            }
                            SmartImportExportEngine.validateAndPreviewImportSync(
                                rawInput = rawInput,
                                selectedFormat = selectedFormat,
                                defaultCategory = defaultCategory,
                                activeCustomType = activeCustomType,
                                existingCustomTypes = existingCustomTypes,
                                plainTextMethodOption = plainTextMethodOption,
                                targetCustomFieldId = targetCustomFieldId.ifBlank { null },
                                allowSkipEmptySteps = allowSkipEmptySteps,
                                allowUnknownRichBlocks = allowUnknownRichBlocks
                            ).also {
                                validationResult = it
                                validatedRevision = inputRevision
                            }
                        }

                        if (!currentResult.isValid) return@Button

                        val effectiveCat = if (keepOriginalCategory) defaultCategory else currentResult.targetCategory
                        val activeMainSteps = if (useSecondaryMainSteps && currentResult.secondaryMainSteps.isNotEmpty()) {
                            currentResult.secondaryMainSteps
                        } else {
                            currentResult.mainSteps
                        }
                        val totalSteps = activeMainSteps.size + currentResult.alternativeRoutes.sumOf { it.steps.size }

                        // Audit P2-13: Block Bulk Append when 0 steps are detected
                        if (effectiveCat == VaultCategory.METHOD &&
                            methodImportMode == MethodImportMode.BULK_APPEND_STEPS &&
                            totalSteps == 0
                        ) {
                            val msg = "No steps detected to append."
                            inlineNoticeMessage = msg
                            onShowMessage(msg)
                            return@Button
                        }

                        // Audit P1-14: Verify target Existing Alternative Route still exists
                        if (effectiveCat == VaultCategory.METHOD &&
                            methodImportMode == MethodImportMode.BULK_APPEND_STEPS &&
                            methodTargetChoice == MethodTargetRouteChoice.EXISTING_ALTERNATIVE
                        ) {
                            val routeExists = existingAlternativeRoutes.any { it.id == selectedExistingAltRouteId }
                            if (!routeExists) {
                                val msg = "Selected Alternative Method route no longer exists. Please select a valid route."
                                inlineNoticeMessage = msg
                                onShowMessage(msg)
                                return@Button
                            }
                        }

                        val willReplaceExisting = hasExistingDraftContent &&
                            !(effectiveCat == VaultCategory.METHOD &&
                                methodImportMode == MethodImportMode.BULK_APPEND_STEPS)

                        if (willReplaceExisting) {
                            showReplaceConfirmationDialog = true
                        } else {
                            isImporting = true
                            SmartImportSessionBuffer.remove(sessionKey, context)
                            onConfirmImport(buildPayload(currentResult))
                        }
                    },
                    enabled = rawInput.isNotBlank() && !isImporting && !isValidating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultYellow,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("smart_import_confirm_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (defaultCategory == VaultCategory.METHOD &&
                            methodImportMode == MethodImportMode.BULK_APPEND_STEPS
                        ) {
                            "Append Steps"
                        } else {
                            "Import into Draft"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showReplaceConfirmationDialog && validationResult?.isValid == true) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirmationDialog = false },
            title = { Text("Replace Current Draft Content?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your current editor draft already has content. " +
                        "Importing in replace/merge mode will update fields in your unsaved draft (it will not save to the database until you tap Save)."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isImporting) return@Button
                        val confirmedRes = validationResult ?: return@Button
                        isImporting = true
                        showReplaceConfirmationDialog = false
                        SmartImportSessionBuffer.remove(sessionKey, context)
                        onConfirmImport(buildPayload(confirmedRes))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VaultYellow,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("confirm_replace_draft_button")
                ) {
                    Text("Replace Draft Content", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Offline "Copy for AI" Dialog (Section 22).
 * Wraps the current IDEAVAULT entry in a clean structured prompt with an optional custom user instruction.
 */
@Composable
fun CopyForAiDialog(
    item: VaultItemWithDetails,
    onDismiss: () -> Unit,
    onCopied: (String) -> Unit
) {
    val context = LocalContext.current
    val vaultColors = IdeaVaultTheme.colors
    var userRequest by rememberSaveable {
        mutableStateOf("Review this content and provide structured improvements and actionable next steps.")
    }
    var copyErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    val generatedPrompt = remember(item, userRequest) {
        SmartImportExportEngine.formatCopyForAi(
            item = item,
            customType = item.customType,
            userRequest = userRequest
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = vaultColors.surface,
        titleContentColor = vaultColors.primaryText,
        textContentColor = vaultColors.secondaryText,
        title = {
            Text(
                text = "Copy for AI",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = userRequest,
                    onValueChange = {
                        userRequest = it
                        copyErrorMessage = null
                    },
                    label = { Text("Instruction for AI (Optional)") },
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("copy_for_ai_request_input")
                )

                if (copyErrorMessage != null) {
                    Text(
                        text = copyErrorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.coral,
                        modifier = Modifier.testTag("copy_for_ai_error")
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = vaultColors.secondarySurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = generatedPrompt,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = vaultColors.primaryText,
                        maxLines = 14,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(12.dp)
                            .testTag("copy_for_ai_preview_text")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val copiedOk = copyTextToSystemClipboard(context, "IDEAVAULT Copy for AI", generatedPrompt)
                    if (copiedOk) {
                        copyErrorMessage = null
                        onCopied(generatedPrompt)
                    } else {
                        copyErrorMessage = "Unable to access system clipboard."
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = VaultYellow,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("confirm_copy_for_ai_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy for AI", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = vaultColors.secondaryText)
            }
        }
    )
}
