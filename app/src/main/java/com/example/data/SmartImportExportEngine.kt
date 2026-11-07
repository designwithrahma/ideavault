package com.example.data

import com.example.ui.components.RichTextSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

/**
 * Supported custom field types for user-created Custom Content Types in IDEAVAULT V2.4.
 */
enum class CustomFieldType(val label: String, val jsonKey: String) {
    SHORT_TEXT("Short Text", "short_text"),
    LONG_TEXT("Long Text", "long_text"),
    NUMBER("Number", "number"),
    DATE("Date", "date"),
    CHECKBOX("Checkbox", "checkbox"),
    SINGLE_SELECT("Single Select", "single_select"),
    MULTI_SELECT("Multiple Select", "multi_select"),
    URL("URL", "url"),
    CODE("Code", "code"),
    CHECKLIST("Checklist", "checklist");

    companion object {
        fun fromKeyOrLabel(raw: String?): CustomFieldType {
            val normalized = raw?.trim()?.lowercase()?.replace("-", "_")?.replace(" ", "_") ?: return SHORT_TEXT
            return entries.firstOrNull {
                it.jsonKey == normalized ||
                    it.name.lowercase() == normalized ||
                    it.label.lowercase().replace(" ", "_") == normalized
            } ?: when (normalized) {
                "text", "string", "shorttext" -> SHORT_TEXT
                "longtext", "textarea", "multiline", "paragraph" -> LONG_TEXT
                "int", "integer", "float", "double", "numeric" -> NUMBER
                "bool", "boolean", "toggle" -> CHECKBOX
                "select", "dropdown", "enum" -> SINGLE_SELECT
                "multiselect", "multiple_select", "tags" -> MULTI_SELECT
                "link", "uri", "website" -> URL
                "code_block", "snippet", "script" -> CODE
                "todo", "tasks", "check_list" -> CHECKLIST
                else -> SHORT_TEXT
            }
        }
    }
}

/**
 * Definition of a single field inside a CustomTypeEntity.
 */
data class CustomFieldDefinition(
    val id: String = "field_${UUID.randomUUID()}",
    val name: String,
    val type: CustomFieldType = CustomFieldType.SHORT_TEXT,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val placeholder: String = "",
    val orderIndex: Int = 0
)

/**
 * Single item inside a CustomFieldType.CHECKLIST field value.
 */
data class CustomChecklistItem(
    val id: String = "chk_${UUID.randomUUID()}",
    val text: String,
    val checked: Boolean = false
)

enum class SmartImportFormat(val label: String) {
    AUTO("Auto-Detect"),
    JSON("JSON"),
    MARKDOWN("Markdown"),
    PLAIN_TEXT("Text")
}

enum class MethodImportMode(val label: String) {
    FULL_REPLACE("Import Method"),
    BULK_APPEND_STEPS("Bulk Add Steps"),
    MERGE_SELECTED_FIELDS("Merge Selected Fields")
}

enum class MethodTargetRouteChoice(val label: String) {
    MAIN_METHOD("Main Method"),
    EXISTING_ALTERNATIVE("Existing Alternative Method"),
    NEW_ALTERNATIVE("New Alternative Method")
}

enum class PlainTextMethodOption(val label: String) {
    NUMBERED_LINES_TO_STEPS("Convert numbered lines into steps"),
    LINE_SEPARATED_TO_STEPS("Convert line-separated text into steps"),
    AS_DESCRIPTION("Import as description")
}

data class SmartImportStepDraft(
    val stepNumber: Int,
    val title: String,
    val details: String,
    val notes: String = "",
    val links: List<String> = emptyList()
)

data class SmartImportAltRouteDraft(
    val name: String,
    val description: String = "",
    val steps: List<SmartImportStepDraft> = emptyList()
)

data class SmartImportFieldConflict(
    val fieldName: String,
    val primaryLabel: String,
    val primaryValue: String,
    val secondaryLabel: String,
    val secondaryValue: String
)

data class SmartImportCustomTypeDraft(
    val name: String,
    val iconName: String = "Extension",
    val colorHex: String = "#5E35B1",
    val description: String = "",
    val fields: List<CustomFieldDefinition> = emptyList()
)

data class SmartImportValidationResult(
    val isValid: Boolean,
    val detectedFormat: SmartImportFormat = SmartImportFormat.JSON,
    val targetCategory: VaultCategory = VaultCategory.IDEA,
    val originalDefaultCategory: VaultCategory = VaultCategory.IDEA,
    val detectedCategoryDiffersFromDefault: Boolean = false,
    val title: String = "",
    val description: String = "",
    val content: String = "",
    val summary: String = "",
    val notes: String = "",
    val systemInstructions: String = "",
    val tags: List<String> = emptyList(),
    val status: IdeaStatus? = null,
    val platform: String = "",
    val estimatedTime: String = "",
    val mainRouteName: String = "Main Method",
    val mainRouteDescription: String = "",
    val mainSteps: List<SmartImportStepDraft> = emptyList(),
    val secondaryMainSteps: List<SmartImportStepDraft> = emptyList(),
    val alternativeRoutes: List<SmartImportAltRouteDraft> = emptyList(),
    val richBlocks: List<RichContentBlock> = emptyList(),
    val hasUnknownRichBlocks: Boolean = false,
    val customTypeName: String? = null,
    val matchedCustomTypeId: String? = null,
    val newCustomTypeDraft: SmartImportCustomTypeDraft? = null,
    val requiresCustomTypeCreationConfirmation: Boolean = false,
    val customFieldValuesById: Map<String, String> = emptyMap(),
    val unmappedCustomFields: Map<String, String> = emptyMap(),
    val fieldConflicts: List<SmartImportFieldConflict> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
) {
    val totalStepCount: Int
        get() = mainSteps.size + alternativeRoutes.sumOf { it.steps.size }
}

/**
 * 100% Offline Universal Smart Import & Copy/Export Engine for IDEAVAULT V2.4.
 */
object SmartImportExportEngine {

    const val MAX_INPUT_BYTES = 5 * 1024 * 1024 // 5 MB safety limit
    const val MAX_IMPORT_STEPS = 500
    const val MAX_JSON_DEPTH = 24
    const val SCHEMA_FORMAT_NAME = "IDEAVAULT"
    const val CURRENT_SCHEMA_VERSION = 1

    suspend fun validateAndPreviewImport(
        rawInput: String,
        selectedFormat: SmartImportFormat = SmartImportFormat.AUTO,
        defaultCategory: VaultCategory = VaultCategory.IDEA,
        activeCustomType: CustomTypeEntity? = null,
        existingCustomTypes: List<CustomTypeEntity> = emptyList(),
        plainTextMethodOption: PlainTextMethodOption = PlainTextMethodOption.NUMBERED_LINES_TO_STEPS,
        targetCustomFieldId: String? = null,
        allowSkipEmptySteps: Boolean = false,
        allowUnknownRichBlocks: Boolean = false
    ): SmartImportValidationResult = withContext(Dispatchers.Default) {
        validateAndPreviewImportSync(
            rawInput = rawInput,
            selectedFormat = selectedFormat,
            defaultCategory = defaultCategory,
            activeCustomType = activeCustomType,
            existingCustomTypes = existingCustomTypes,
            plainTextMethodOption = plainTextMethodOption,
            targetCustomFieldId = targetCustomFieldId,
            allowSkipEmptySteps = allowSkipEmptySteps,
            allowUnknownRichBlocks = allowUnknownRichBlocks
        )
    }

    fun validateAndPreviewImportSync(
        rawInput: String,
        selectedFormat: SmartImportFormat = SmartImportFormat.AUTO,
        defaultCategory: VaultCategory = VaultCategory.IDEA,
        activeCustomType: CustomTypeEntity? = null,
        existingCustomTypes: List<CustomTypeEntity> = emptyList(),
        plainTextMethodOption: PlainTextMethodOption = PlainTextMethodOption.NUMBERED_LINES_TO_STEPS,
        targetCustomFieldId: String? = null,
        allowSkipEmptySteps: Boolean = false,
        allowUnknownRichBlocks: Boolean = false
    ): SmartImportValidationResult {
        if (rawInput.isEmpty() || rawInput.isBlank()) {
            return SmartImportValidationResult(
                isValid = false,
                targetCategory = defaultCategory,
                originalDefaultCategory = defaultCategory,
                errors = listOf("Input is empty. Paste JSON, Markdown, or text to validate and preview.")
            )
        }

        val byteLength = rawInput.toByteArray(Charsets.UTF_8).size
        if (byteLength > MAX_INPUT_BYTES) {
            return SmartImportValidationResult(
                isValid = false,
                targetCategory = defaultCategory,
                originalDefaultCategory = defaultCategory,
                errors = listOf("Input size (${byteLength / 1024} KB) exceeds the 5 MB safety limit.")
            )
        }

        val resolvedFormat = when (selectedFormat) {
            SmartImportFormat.AUTO -> detectFormat(rawInput, defaultCategory)
            else -> selectedFormat
        }

        val rawResult = when (resolvedFormat) {
            SmartImportFormat.JSON -> parseJsonImport(
                rawInput = rawInput,
                defaultCategory = defaultCategory,
                activeCustomType = activeCustomType,
                existingCustomTypes = existingCustomTypes,
                allowSkipEmptySteps = allowSkipEmptySteps,
                allowUnknownRichBlocks = allowUnknownRichBlocks
            )
            SmartImportFormat.MARKDOWN -> parseMarkdownImport(
                rawInput = rawInput,
                defaultCategory = defaultCategory,
                activeCustomType = activeCustomType,
                targetCustomFieldId = targetCustomFieldId
            )
            SmartImportFormat.PLAIN_TEXT, SmartImportFormat.AUTO -> parsePlainTextImport(
                rawInput = rawInput,
                defaultCategory = defaultCategory,
                activeCustomType = activeCustomType,
                plainTextMethodOption = plainTextMethodOption,
                targetCustomFieldId = targetCustomFieldId
            )
        }

        val categorySwitched = rawResult.targetCategory != defaultCategory &&
            rawResult.customTypeName == null &&
            rawResult.matchedCustomTypeId == null
        val updatedWarnings = if (categorySwitched) {
            rawResult.warnings + "Detected content type '${rawResult.targetCategory.singularLabel}' differs from current editor type '${defaultCategory.singularLabel}'."
        } else {
            rawResult.warnings
        }

        return rawResult.copy(
            originalDefaultCategory = defaultCategory,
            detectedCategoryDiffersFromDefault = categorySwitched,
            warnings = updatedWarnings
        )
    }

    fun detectFormat(rawInput: String, defaultCategory: VaultCategory): SmartImportFormat {
        // P1-03: When target type is PROMPT and the user did not explicitly select JSON format,
        // ALWAYS treat raw input as exact literal prompt text by default so prompts starting with
        // '{', '[', or '```json' are preserved byte-for-byte without being misclassified as JSON.
        if (defaultCategory == VaultCategory.PROMPT) {
            return SmartImportFormat.PLAIN_TEXT
        }
        val trimmed = rawInput.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return SmartImportFormat.JSON
        }
        // Check if wrapped in a single ```json ... ``` fence
        if (trimmed.startsWith("```json", ignoreCase = true) && trimmed.endsWith("```")) {
            val inner = extractSingleJsonCodeFence(trimmed)
            if (inner != null && (inner.startsWith("{") || inner.startsWith("["))) {
                return SmartImportFormat.JSON
            }
        }
        val lines = rawInput.lines()
        val hasMarkdownMarkers = lines.any { line ->
            val t = line.trimStart()
            t.startsWith("# ") ||
                t.startsWith("## ") ||
                t.startsWith("### ") ||
                t.startsWith("- [ ] ") ||
                t.startsWith("- [x] ", ignoreCase = true) ||
                t.startsWith("```") ||
                t.startsWith("> ")
        }
        return if (hasMarkdownMarkers) SmartImportFormat.MARKDOWN else SmartImportFormat.PLAIN_TEXT
    }

    private fun extractSingleJsonCodeFence(trimmed: String): String? {
        val firstNewline = trimmed.indexOf('\n')
        if (firstNewline <= 0) return null
        val body = trimmed.substring(firstNewline + 1).removeSuffix("```").trim()
        return body.takeIf { it.isNotEmpty() }
    }

    private fun checkJsonDepth(value: Any?, currentDepth: Int = 0): Boolean {
        if (currentDepth > MAX_JSON_DEPTH) return false
        return when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (!checkJsonDepth(value.opt(k), currentDepth + 1)) return false
                }
                true
            }
            is JSONArray -> {
                for (i in 0 until value.length()) {
                    if (!checkJsonDepth(value.opt(i), currentDepth + 1)) return false
                }
                true
            }
            else -> true
        }
    }

    private fun parseJsonImport(
        rawInput: String,
        defaultCategory: VaultCategory,
        activeCustomType: CustomTypeEntity?,
        existingCustomTypes: List<CustomTypeEntity>,
        allowSkipEmptySteps: Boolean = false,
        allowUnknownRichBlocks: Boolean = false
    ): SmartImportValidationResult {
        val trimmed = rawInput.trim()
        val candidateJson = if (trimmed.startsWith("```")) {
            extractSingleJsonCodeFence(trimmed) ?: trimmed
        } else {
            trimmed
        }

        val parsedRoot = try {
            val tokener = JSONTokener(candidateJson)
            val root = tokener.nextValue()
            if (tokener.more()) {
                val remaining = StringBuilder()
                while (tokener.more()) {
                    remaining.append(tokener.next())
                }
                if (remaining.toString().isNotBlank()) {
                    return SmartImportValidationResult(
                        isValid = false,
                        detectedFormat = SmartImportFormat.JSON,
                        targetCategory = defaultCategory,
                        errors = listOf("Invalid JSON: Unexpected trailing content after root JSON element.")
                    )
                }
            }
            root
        } catch (e: Exception) {
            return SmartImportValidationResult(
                isValid = false,
                detectedFormat = SmartImportFormat.JSON,
                targetCategory = defaultCategory,
                errors = listOf("Malformed JSON: ${e.message ?: "Check brackets, quotes, and commas."}")
            )
        }

        if (!checkJsonDepth(parsedRoot, 0)) {
            return SmartImportValidationResult(
                isValid = false,
                detectedFormat = SmartImportFormat.JSON,
                targetCategory = defaultCategory,
                errors = listOf("JSON nesting depth exceeds maximum safety limit of $MAX_JSON_DEPTH.")
            )
        }

        // Support root JSONArray of steps when importing into a Method
        if (parsedRoot is JSONArray) {
            val warnings = mutableListOf<String>()
            val errors = mutableListOf<String>()
            val steps = parseStepsArray(parsedRoot, "steps", warnings, errors, allowSkipEmptySteps)
            if (errors.isNotEmpty()) {
                return SmartImportValidationResult(
                    isValid = false,
                    detectedFormat = SmartImportFormat.JSON,
                    targetCategory = VaultCategory.METHOD,
                    errors = errors,
                    warnings = warnings
                )
            }
            if (steps.isEmpty()) {
                return SmartImportValidationResult(
                    isValid = false,
                    detectedFormat = SmartImportFormat.JSON,
                    targetCategory = VaultCategory.METHOD,
                    errors = listOf("Method steps array is empty. Provide at least 1 step.")
                )
            }
            return SmartImportValidationResult(
                isValid = true,
                detectedFormat = SmartImportFormat.JSON,
                targetCategory = VaultCategory.METHOD,
                mainSteps = steps,
                warnings = warnings
            )
        }

        if (parsedRoot !is JSONObject) {
            return SmartImportValidationResult(
                isValid = false,
                detectedFormat = SmartImportFormat.JSON,
                targetCategory = defaultCategory,
                errors = listOf("Invalid JSON root structure: Expected a JSON object {...}.")
            )
        }

        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val conflicts = mutableListOf<SmartImportFieldConflict>()

        // P1-02: Validate "format" and "schemaVersion" when present
        val hasFormatKey = parsedRoot.has("format") && !parsedRoot.isNull("format")
        val hasSchemaVersionKey = parsedRoot.has("schemaVersion") && !parsedRoot.isNull("schemaVersion")
        if (hasFormatKey) {
            val formatVal = parsedRoot.optString("format", "").trim()
            if (!formatVal.equals(SCHEMA_FORMAT_NAME, ignoreCase = true)) {
                return SmartImportValidationResult(
                    isValid = false,
                    detectedFormat = SmartImportFormat.JSON,
                    targetCategory = defaultCategory,
                    errors = listOf("Unsupported JSON format '$formatVal'. Expected format '$SCHEMA_FORMAT_NAME'.")
                )
            }
        }
        if (hasSchemaVersionKey) {
            val rawVer = parsedRoot.opt("schemaVersion")
            val verInt = when (rawVer) {
                is Int -> rawVer
                is Long -> rawVer.toInt()
                is Number -> if (rawVer.toDouble() == rawVer.toInt().toDouble()) rawVer.toInt() else null
                is String -> rawVer.trim().toIntOrNull()
                else -> null
            }
            if (verInt == null || verInt != CURRENT_SCHEMA_VERSION) {
                return SmartImportValidationResult(
                    isValid = false,
                    detectedFormat = SmartImportFormat.JSON,
                    targetCategory = defaultCategory,
                    errors = listOf("Unsupported schemaVersion '$rawVer'. Supported schemaVersion is $CURRENT_SCHEMA_VERSION.")
                )
            }
        }
        if (!hasFormatKey && !hasSchemaVersionKey) {
            warnings.add("Legacy JSON without 'format'/'schemaVersion' envelope detected; imported using compatibility mode.")
        }

        if (parsedRoot.has("id") || parsedRoot.has("itemId") || parsedRoot.has("databaseId")) {
            warnings.add("External IDs in JSON were ignored; IDEAVAULT will assign safe local IDs.")
        }

        val rawType = parsedRoot.optString("type", "").trim().uppercase()
        val mainMethodObj = parsedRoot.optJSONObject("mainMethod")
        val hasStepsKey = parsedRoot.has("steps") ||
            parsedRoot.has("alternativeMethods") ||
            parsedRoot.has("alternativeRoutes") ||
            parsedRoot.has("routes") ||
            (mainMethodObj != null && mainMethodObj.has("steps"))
        val hasPromptKey = parsedRoot.has("prompt") || parsedRoot.has("promptText") || parsedRoot.has("systemInstructions")
        val hasBlocksKey = parsedRoot.has("blocks") || parsedRoot.has("richBlocks")
        val hasCustomFieldsKey = parsedRoot.has("customTypeName") || parsedRoot.has("customTypeDefinition") ||
            (parsedRoot.has("fields") && parsedRoot.opt("fields") is JSONObject)

        val resolvedType = when {
            rawType == "METHOD" -> "METHOD"
            rawType == "PROMPT" -> "PROMPT"
            rawType == "NOTE" || rawType == "KNOWLEDGE" -> "NOTE"
            rawType == "CUSTOM" -> "CUSTOM"
            rawType == "IDEA" -> "IDEA"
            rawType.isNotEmpty() -> {
                errors.add("Unsupported content type '$rawType'. Expected IDEA, METHOD, PROMPT, NOTE, or CUSTOM.")
                return SmartImportValidationResult(
                    isValid = false,
                    detectedFormat = SmartImportFormat.JSON,
                    targetCategory = defaultCategory,
                    errors = errors
                )
            }
            hasCustomFieldsKey -> "CUSTOM"
            hasStepsKey -> "METHOD"
            hasPromptKey -> "PROMPT"
            hasBlocksKey -> "NOTE"
            activeCustomType != null -> "CUSTOM"
            defaultCategory == VaultCategory.METHOD -> "METHOD"
            defaultCategory == VaultCategory.PROMPT -> "PROMPT"
            defaultCategory == VaultCategory.KNOWLEDGE -> "NOTE"
            else -> "IDEA"
        }

        val tags = parseStringArray(parsedRoot.opt("tags"))

        return when (resolvedType) {
            "METHOD" -> parseMethodJsonObject(parsedRoot, tags, conflicts, warnings, errors, allowSkipEmptySteps)
            "PROMPT" -> parsePromptJsonObject(parsedRoot, tags, warnings, errors)
            "NOTE" -> parseNoteJsonObject(parsedRoot, tags, warnings, errors, allowUnknownRichBlocks)
            "CUSTOM" -> parseCustomJsonObject(
                obj = parsedRoot,
                tags = tags,
                activeCustomType = activeCustomType,
                existingCustomTypes = existingCustomTypes,
                warnings = warnings,
                errors = errors
            )
            else -> parseIdeaJsonObject(parsedRoot, tags, conflicts, warnings, errors)
        }
    }

    private fun parseIdeaJsonObject(
        obj: JSONObject,
        tags: List<String>,
        conflicts: MutableList<SmartImportFieldConflict>,
        warnings: MutableList<String>,
        errors: MutableList<String>
    ): SmartImportValidationResult {
        val title = obj.optString("title", "").trim()
        val description = obj.optString("richDescription", "").ifEmpty { obj.optString("description", "") }
        val summary = obj.optString("summary", "")
        val content = obj.optString("richContent", "").ifEmpty { obj.optString("content", "") }
        val notes = obj.optString("richNotes", "").ifEmpty { obj.optString("notes", "") }
        val platform = obj.optString("platform", "").trim()
        val estimatedTime = obj.optString("estimatedTime", "").trim()
        val rawStatus = obj.optString("status", "").trim().uppercase()

        val status = if (rawStatus.isNotEmpty()) {
            IdeaStatus.entries.firstOrNull {
                it.name == rawStatus || it.label.uppercase().replace(" ", "_") == rawStatus
            } ?: run {
                warnings.add("Unrecognized Idea status '$rawStatus'; defaulting to current editor status.")
                null
            }
        } else null

        val plainDescription = RichTextSerializer.toPlainText(description)
        if (plainDescription.isNotBlank() && summary.isNotBlank() && plainDescription.trim() != summary.trim()) {
            conflicts.add(
                SmartImportFieldConflict(
                    fieldName = "description_vs_summary",
                    primaryLabel = "Description",
                    primaryValue = description,
                    secondaryLabel = "Summary",
                    secondaryValue = summary
                )
            )
            warnings.add("Both 'description' and 'summary' have different values. Both are preserved in preview.")
        }

        if (title.isBlank() && content.isBlank() && description.isBlank() && summary.isBlank()) {
            errors.add("Idea JSON must include at least a 'title', 'content', or 'description'.")
        }

        val resolvedDescription = when {
            plainDescription.isNotBlank() && summary.isNotBlank() && plainDescription.trim() != summary.trim() ->
                "$description\n\nSummary:\n$summary"
            description.isNotBlank() -> description
            else -> summary
        }

        // P1-09: Do NOT append notes into content; keep content and notes in their dedicated fields.
        return SmartImportValidationResult(
            isValid = errors.isEmpty(),
            detectedFormat = SmartImportFormat.JSON,
            targetCategory = VaultCategory.IDEA,
            title = title,
            description = resolvedDescription,
            content = content,
            summary = summary,
            notes = notes,
            tags = tags,
            status = status,
            platform = platform,
            estimatedTime = estimatedTime,
            fieldConflicts = conflicts,
            warnings = warnings,
            errors = errors
        )
    }

    private fun parseMethodJsonObject(
        obj: JSONObject,
        tags: List<String>,
        conflicts: MutableList<SmartImportFieldConflict>,
        warnings: MutableList<String>,
        errors: MutableList<String>,
        allowSkipEmptySteps: Boolean = false
    ): SmartImportValidationResult {
        val title = obj.optString("title", "").trim()
        val description = obj.optString("richDescription", "").ifEmpty { obj.optString("description", "") }
        val content = obj.optString("richContent", "").ifEmpty { obj.optString("content", "") }
        val notes = obj.optString("richNotes", "").ifEmpty { obj.optString("notes", "") }
        val platform = obj.optString("platform", "").trim()
        val estimatedTime = obj.optString("estimatedTime", "").trim()

        // P1-01: Support both mainMethod.steps and root steps
        val mainMethodObj = obj.optJSONObject("mainMethod")
        val mainRouteName = (
            mainMethodObj?.optString("name", "")?.trim()?.ifEmpty {
                mainMethodObj.optString("title", "").trim()
            }
        ).orEmpty().ifBlank {
            obj.optString("mainMethodName", "Main Method").trim().ifBlank { "Main Method" }
        }
        val mainRouteDescription = (
            mainMethodObj?.optString("richDescription", "")?.takeIf { it.isNotEmpty() }
                ?: mainMethodObj?.optString("description", "")?.takeIf { it.isNotEmpty() }
        ) ?: obj.optString("richMainMethodDescription", "").ifEmpty {
            obj.optString("mainMethodDescription", "")
        }

        val nestedStepsValue = mainMethodObj?.opt("steps")
        val nestedSteps = if (nestedStepsValue != null && nestedStepsValue != JSONObject.NULL) {
            if (nestedStepsValue !is JSONArray) {
                errors.add("Invalid 'mainMethod.steps' field: Expected a JSON array of step objects.")
                emptyList()
            } else {
                parseStepsArray(nestedStepsValue, "mainMethod.steps", warnings, errors, allowSkipEmptySteps)
            }
        } else {
            emptyList()
        }

        val rootStepsValue = obj.opt("steps")
        val rootSteps = if (rootStepsValue != null && rootStepsValue != JSONObject.NULL) {
            if (rootStepsValue !is JSONArray) {
                errors.add("Invalid 'steps' field: Expected a JSON array of step objects.")
                emptyList()
            } else {
                parseStepsArray(rootStepsValue, "steps", warnings, errors, allowSkipEmptySteps)
            }
        } else {
            emptyList()
        }

        val mainSteps: List<SmartImportStepDraft>
        val secondaryMainSteps: List<SmartImportStepDraft>
        if (nestedSteps.isNotEmpty() && rootSteps.isNotEmpty() && nestedSteps != rootSteps) {
            mainSteps = nestedSteps
            secondaryMainSteps = rootSteps
            conflicts.add(
                SmartImportFieldConflict(
                    fieldName = "mainMethod.steps_vs_steps",
                    primaryLabel = "mainMethod.steps (${nestedSteps.size} steps)",
                    primaryValue = nestedSteps.joinToString("\n") { "${it.stepNumber}. ${it.title}" },
                    secondaryLabel = "root steps (${rootSteps.size} steps)",
                    secondaryValue = rootSteps.joinToString("\n") { "${it.stepNumber}. ${it.title}" }
                )
            )
            warnings.add("Both 'mainMethod.steps' and root 'steps' are present with different steps; using 'mainMethod.steps' by default.")
        } else {
            mainSteps = if (nestedSteps.isNotEmpty()) nestedSteps else rootSteps
            secondaryMainSteps = emptyList()
        }

        val altRoutes = mutableListOf<SmartImportAltRouteDraft>()
        val altValue = obj.opt("alternativeMethods") ?: obj.opt("alternativeRoutes") ?: obj.opt("routes")
        if (altValue != null && altValue != JSONObject.NULL) {
            if (altValue !is JSONArray) {
                errors.add("Invalid 'alternativeMethods' field: Expected a JSON array.")
            } else {
                for (i in 0 until altValue.length()) {
                    val altObj = altValue.optJSONObject(i)
                    if (altObj == null) {
                        errors.add("Alternative Method at index ${i + 1} must be a JSON object.")
                        continue
                    }
                    val routeName = altObj.optString("name", altObj.optString("title", "")).trim()
                        .ifBlank { "Alternative Method ${i + 1}" }
                    val routeDesc = altObj.optString("richDescription", "").ifEmpty {
                        altObj.optString("description", altObj.optString("details", ""))
                    }
                    val routeStepsArr = altObj.optJSONArray("steps")
                    val routeSteps = if (routeStepsArr != null) {
                        parseStepsArray(routeStepsArr, "alternativeMethods[$i].steps", warnings, errors, allowSkipEmptySteps)
                    } else {
                        emptyList()
                    }
                    altRoutes.add(
                        SmartImportAltRouteDraft(
                            name = routeName,
                            description = routeDesc,
                            steps = routeSteps
                        )
                    )
                }
            }
        }

        val totalSteps = mainSteps.size + altRoutes.sumOf { it.steps.size }
        if (totalSteps == 0 && errors.isEmpty()) {
            errors.add("Method JSON must contain at least 1 step in 'mainMethod.steps', 'steps', or 'alternativeMethods'.")
        }
        if (totalSteps > MAX_IMPORT_STEPS) {
            errors.add("Total imported steps ($totalSteps) exceeds the maximum allowed limit of $MAX_IMPORT_STEPS steps.")
        }

        return SmartImportValidationResult(
            isValid = errors.isEmpty(),
            detectedFormat = SmartImportFormat.JSON,
            targetCategory = VaultCategory.METHOD,
            title = title,
            description = description,
            content = content,
            notes = notes,
            platform = platform,
            estimatedTime = estimatedTime,
            tags = tags,
            mainRouteName = mainRouteName,
            mainRouteDescription = mainRouteDescription,
            mainSteps = mainSteps,
            secondaryMainSteps = secondaryMainSteps,
            alternativeRoutes = altRoutes,
            fieldConflicts = conflicts,
            warnings = warnings,
            errors = errors
        )
    }

    private fun parseStepsArray(
        arr: JSONArray,
        pathLabel: String,
        warnings: MutableList<String>,
        errors: MutableList<String>,
        allowSkipEmptySteps: Boolean = false
    ): List<SmartImportStepDraft> {
        if (arr.length() > MAX_IMPORT_STEPS) {
            errors.add("'$pathLabel' has ${arr.length()} steps, exceeding the maximum limit of $MAX_IMPORT_STEPS steps.")
            return emptyList()
        }
        val rawSteps = mutableListOf<Pair<Int?, SmartImportStepDraft>>()
        for (i in 0 until arr.length()) {
            val item = arr.opt(i)
            when (item) {
                is JSONObject -> {
                    val explicitStepNo = if (item.has("stepNumber")) {
                        item.optInt("stepNumber", i + 1)
                    } else if (item.has("orderIndex")) {
                        item.optInt("orderIndex", i + 1)
                    } else null

                    val rawTitle = item.optString("title", item.optString("name", "")).trim()
                    val details = when {
                        item.has("richDetails") && !item.isNull("richDetails") && item.optString("richDetails", "").isNotEmpty() ->
                            item.optString("richDetails", "")
                        item.has("details") && !item.isNull("details") -> item.optString("details", "")
                        item.has("description") && !item.isNull("description") -> item.optString("description", "")
                        item.has("content") && !item.isNull("content") -> item.optString("content", "")
                        else -> ""
                    }
                    val notes = when {
                        item.has("richNotes") && !item.isNull("richNotes") && item.optString("richNotes", "").isNotEmpty() ->
                            item.optString("richNotes", "")
                        item.has("notes") && !item.isNull("notes") -> item.optString("notes", "")
                        else -> ""
                    }
                    val links = parseStringArray(item.opt("links"))

                    // P1-15: Do not silently skip empty step objects unless explicitly allowed
                    if (rawTitle.isBlank() && details.isBlank()) {
                        if (allowSkipEmptySteps) {
                            warnings.add("Skipped empty step at $pathLabel[${i + 1}].")
                        } else {
                            errors.add(
                                "Invalid step at $pathLabel[${i + 1}]: Step object is empty. " +
                                    "Expected non-empty 'title' or 'details' (e.g. {\"title\": \"Step ${i + 1}\", \"details\": \"...\"})."
                            )
                        }
                        continue
                    }
                    val resolvedTitle = rawTitle.ifBlank { "Step ${i + 1}" }
                    rawSteps.add(
                        explicitStepNo to SmartImportStepDraft(
                            stepNumber = i + 1,
                            title = resolvedTitle,
                            details = details,
                            notes = notes,
                            links = links
                        )
                    )
                }
                is String -> {
                    val text = item.trim()
                    if (text.isNotEmpty()) {
                        rawSteps.add(
                            null to SmartImportStepDraft(
                                stepNumber = i + 1,
                                title = "Step ${i + 1}",
                                details = item
                            )
                        )
                    } else {
                        if (allowSkipEmptySteps) {
                            warnings.add("Skipped blank step at $pathLabel[${i + 1}].")
                        } else {
                            errors.add("Invalid step at $pathLabel[${i + 1}]: Step string cannot be blank.")
                        }
                    }
                }
                else -> {
                    errors.add("Invalid step at $pathLabel[${i + 1}]: Expected an object with 'title' and 'details'/'description'.")
                }
            }
        }

        // Preserve array order unless explicit stepNumbers are strictly out of order
        val allHaveExplicitNumbers = rawSteps.isNotEmpty() && rawSteps.all { it.first != null }
        val ordered = if (allHaveExplicitNumbers) {
            rawSteps.sortedBy { it.first ?: Int.MAX_VALUE }.map { it.second }
        } else {
            rawSteps.map { it.second }
        }

        return ordered.mapIndexed { idx, draft ->
            draft.copy(stepNumber = idx + 1)
        }
    }

    private fun parsePromptJsonObject(
        obj: JSONObject,
        tags: List<String>,
        warnings: MutableList<String>,
        errors: MutableList<String>
    ): SmartImportValidationResult {
        // CRITICAL: Never trim prompt content! Preserve exact spaces, newlines, tabs, markdown, JSON, code fences.
        val title = obj.optString("title", "").trim()
        val hasPrompt = obj.has("prompt") && !obj.isNull("prompt")
        val hasPromptText = obj.has("promptText") && !obj.isNull("promptText")
        val hasContent = obj.has("content") && !obj.isNull("content")
        val exactPrompt = when {
            hasPrompt -> obj.optString("prompt", "")
            hasPromptText -> obj.optString("promptText", "")
            hasContent -> obj.optString("content", "")
            else -> ""
        }
        val systemInstructions = if (obj.has("systemInstructions") && !obj.isNull("systemInstructions")) {
            obj.optString("systemInstructions", "")
        } else ""
        val description = if (obj.has("richDescription") && !obj.isNull("richDescription") && obj.optString("richDescription", "").isNotEmpty()) {
            obj.optString("richDescription", "")
        } else if (obj.has("description") && !obj.isNull("description")) {
            obj.optString("description", "")
        } else ""

        val notes = if (obj.has("richNotes") && !obj.isNull("richNotes") && obj.optString("richNotes", "").isNotEmpty()) {
            obj.optString("richNotes", "")
        } else if (obj.has("notes") && !obj.isNull("notes")) {
            obj.optString("notes", "")
        } else ""

        if (exactPrompt.isEmpty() && title.isBlank()) {
            errors.add("Prompt JSON must include a non-empty 'prompt' (or 'content') field.")
        }

        val combinedNotes = buildString {
            if (systemInstructions.isNotEmpty()) {
                append("System Instructions:\n").append(systemInstructions)
            }
            if (notes.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(notes)
            }
        }

        return SmartImportValidationResult(
            isValid = errors.isEmpty(),
            detectedFormat = SmartImportFormat.JSON,
            targetCategory = VaultCategory.PROMPT,
            title = title,
            description = description,
            content = exactPrompt,
            notes = combinedNotes,
            systemInstructions = systemInstructions,
            tags = tags,
            warnings = warnings,
            errors = errors
        )
    }

    private fun parseNoteJsonObject(
        obj: JSONObject,
        tags: List<String>,
        warnings: MutableList<String>,
        errors: MutableList<String>,
        allowUnknownRichBlocks: Boolean = false
    ): SmartImportValidationResult {
        val title = obj.optString("title", "").trim()
        val description = obj.optString("richDescription", "").ifEmpty { obj.optString("description", "") }
        val rawContent = obj.optString("richContent", "").ifEmpty {
            if (obj.has("content") && !obj.isNull("content")) obj.optString("content", "") else ""
        }
        val notes = obj.optString("richNotes", "").ifEmpty { obj.optString("notes", "") }

        var hasUnknownBlocks = false
        val richBlocks = mutableListOf<RichContentBlock>()
        val blocksArr = obj.optJSONArray("blocks") ?: obj.optJSONArray("richBlocks")
        if (blocksArr != null) {
            for (i in 0 until blocksArr.length()) {
                val bObj = blocksArr.optJSONObject(i)
                if (bObj == null) {
                    errors.add("Invalid block at blocks[${i + 1}]: Expected a JSON object.")
                    continue
                }
                val rawBlockType = bObj.optString("type", "PARAGRAPH").trim().uppercase()
                val blockType = when (rawBlockType) {
                    "HEADING", "HEADER", "H1", "H2", "H3" -> RichBlockType.HEADING
                    "PARAGRAPH", "TEXT" -> RichBlockType.PARAGRAPH
                    "BULLET_LIST", "BULLET", "UL" -> RichBlockType.BULLET_LIST
                    "NUMBERED_LIST", "NUMBERED", "OL" -> RichBlockType.NUMBERED_LIST
                    "CHECKLIST", "TODO", "TASK" -> RichBlockType.CHECKLIST
                    "CODE", "CODE_BLOCK" -> RichBlockType.CODE_BLOCK
                    "QUOTE", "BLOCKQUOTE" -> RichBlockType.QUOTE
                    "DIVIDER", "HR" -> RichBlockType.DIVIDER
                    else -> {
                        hasUnknownBlocks = true
                        if (allowUnknownRichBlocks) {
                            warnings.add("Unknown block type '$rawBlockType' at blocks[${i + 1}] preserved as paragraph.")
                        } else {
                            errors.add(
                                "Unsupported block type '$rawBlockType' at blocks[${i + 1}]. " +
                                    "Supported types: heading, paragraph, bullet_list, numbered_list, checklist, code, quote, divider."
                            )
                        }
                        RichBlockType.PARAGRAPH
                    }
                }
                val bTitle = bObj.optString("title", bObj.optString("language", ""))
                // P1-10: Support "code" -> "content" -> "text" -> "items" precedence
                val bContent = when {
                    bObj.has("code") && !bObj.isNull("code") -> {
                        if (bObj.has("content") && !bObj.isNull("content") &&
                            bObj.optString("content", "") != bObj.optString("code", "")
                        ) {
                            warnings.add("Block at blocks[${i + 1}] has both 'code' and 'content'; using 'code'.")
                        }
                        bObj.optString("code", "")
                    }
                    bObj.has("content") && !bObj.isNull("content") -> bObj.optString("content", "")
                    bObj.has("text") && !bObj.isNull("text") -> bObj.optString("text", "")
                    bObj.has("items") && bObj.opt("items") is JSONArray -> {
                        parseStringArray(bObj.opt("items")).joinToString("\n")
                    }
                    else -> ""
                }
                val checked = bObj.optBoolean("checked", false)
                richBlocks.add(
                    RichContentBlock(
                        id = "block_${UUID.randomUUID()}",
                        type = blockType,
                        title = bTitle,
                        content = bContent,
                        checked = checked
                    )
                )
            }
        }

        // Prevent duplicate content between plain text and rich blocks
        val deduplicatedBlocks = if (rawContent.isNotBlank() && richBlocks.size == 1 &&
            richBlocks.first().type == RichBlockType.PARAGRAPH &&
            richBlocks.first().content.trim() == rawContent.trim()
        ) {
            warnings.add("Removed duplicate paragraph block that matched main Note content.")
            emptyList()
        } else {
            richBlocks
        }

        if (title.isBlank() && rawContent.isBlank() && deduplicatedBlocks.isEmpty()) {
            errors.add("Note JSON must contain a 'title', 'content', or 'blocks'.")
        }

        return SmartImportValidationResult(
            isValid = errors.isEmpty(),
            detectedFormat = SmartImportFormat.JSON,
            targetCategory = VaultCategory.KNOWLEDGE,
            title = title,
            description = description,
            content = rawContent,
            notes = notes,
            tags = tags,
            richBlocks = deduplicatedBlocks,
            hasUnknownRichBlocks = hasUnknownBlocks,
            warnings = warnings,
            errors = errors
        )
    }

    private fun parseCustomJsonObject(
        obj: JSONObject,
        tags: List<String>,
        activeCustomType: CustomTypeEntity?,
        existingCustomTypes: List<CustomTypeEntity>,
        warnings: MutableList<String>,
        errors: MutableList<String>
    ): SmartImportValidationResult {
        val title = obj.optString("title", "").trim()
        val description = obj.optString("richDescription", "").ifEmpty { obj.optString("description", "") }
        val content = obj.optString("richContent", "").ifEmpty { obj.optString("content", "") }
        val notes = obj.optString("richNotes", "").ifEmpty { obj.optString("notes", "") }

        val defObj = obj.optJSONObject("customTypeDefinition")
        val explicitCustomTypeName = obj.optString(
            "customTypeName",
            defObj?.optString("name", "") ?: ""
        ).trim()
        val customTypeName = explicitCustomTypeName.ifBlank { activeCustomType?.name ?: "" }.trim()

        // P1-16: When JSON explicitly specifies a customTypeName, match ONLY by name (never fall back to an unrelated activeCustomType)
        val matchedExisting = if (explicitCustomTypeName.isNotEmpty()) {
            existingCustomTypes.firstOrNull { it.name.equals(explicitCustomTypeName, ignoreCase = true) }
                ?: activeCustomType?.takeIf { it.name.equals(explicitCustomTypeName, ignoreCase = true) }
        } else {
            activeCustomType
        }

        var parsedNewDraft: SmartImportCustomTypeDraft? = null
        val candidateDefinitions = mutableListOf<CustomFieldDefinition>()

        if (defObj != null) {
            val defName = defObj.optString("name", customTypeName).trim().ifBlank { "Custom Type" }
            val defIcon = defObj.optString("icon", defObj.optString("iconName", "Extension")).trim().ifBlank { "Extension" }
            val defColor = defObj.optString("color", defObj.optString("colorHex", "#5E35B1")).trim().ifBlank { "#5E35B1" }
            val defDesc = defObj.optString("description", "")
            val defFieldsArr = defObj.optJSONArray("fields")
            if (defFieldsArr != null) {
                for (i in 0 until defFieldsArr.length()) {
                    val fObj = defFieldsArr.optJSONObject(i) ?: continue
                    val fName = fObj.optString("name", fObj.optString("label", "")).trim()
                    if (fName.isBlank()) continue
                    val fType = CustomFieldType.fromKeyOrLabel(fObj.optString("type", "short_text"))
                    val fReq = fObj.optBoolean("required", false)
                    val fOptions = parseStringArray(fObj.opt("options"))
                    val fPlaceholder = fObj.optString("placeholder", "")
                    candidateDefinitions.add(
                        CustomFieldDefinition(
                            id = "field_${UUID.randomUUID()}",
                            name = fName,
                            type = fType,
                            required = fReq,
                            options = fOptions,
                            placeholder = fPlaceholder,
                            orderIndex = i
                        )
                    )
                }
            }
            parsedNewDraft = SmartImportCustomTypeDraft(
                name = defName,
                iconName = defIcon,
                colorHex = defColor,
                description = defDesc,
                fields = candidateDefinitions
            )
        }

        val fieldsObj = obj.optJSONObject("fields") ?: obj.optJSONObject("customFields")
        val rawFieldEntries = linkedMapOf<String, String>()
        if (fieldsObj != null) {
            val keys = fieldsObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val rawVal = fieldsObj.opt(key)
                val serializedVal = serializeJsonCustomFieldValue(rawVal)
                rawFieldEntries[key] = serializedVal
            }
        }

        // If no existing custom type matched and no explicit definition was given, infer fields from rawFieldEntries
        if (matchedExisting == null && parsedNewDraft == null && customTypeName.isNotBlank()) {
            val inferredFields = rawFieldEntries.entries.mapIndexed { idx, (k, v) ->
                val inferredType = when {
                    v.equals("true", ignoreCase = true) || v.equals("false", ignoreCase = true) -> CustomFieldType.CHECKBOX
                    v.startsWith("http://") || v.startsWith("https://") -> CustomFieldType.URL
                    v.contains("\n") -> CustomFieldType.LONG_TEXT
                    else -> CustomFieldType.SHORT_TEXT
                }
                CustomFieldDefinition(
                    id = "field_${UUID.randomUUID()}",
                    name = k,
                    type = inferredType,
                    orderIndex = idx
                )
            }
            parsedNewDraft = SmartImportCustomTypeDraft(
                name = customTypeName,
                fields = inferredFields
            )
        }

        val effectiveDefs: List<CustomFieldDefinition> = when {
            matchedExisting != null -> VaultJsonSerializers.decodeCustomFieldDefinitions(matchedExisting.fieldsJson)
            parsedNewDraft != null -> parsedNewDraft.fields
            else -> emptyList()
        }

        val mappedValuesById = linkedMapOf<String, String>()
        val unmappedFields = linkedMapOf<String, String>()

        for ((rawKey, rawValue) in rawFieldEntries) {
            val matchedField = effectiveDefs.firstOrNull {
                it.id.equals(rawKey, ignoreCase = true) ||
                    it.name.equals(rawKey, ignoreCase = true)
            }
            if (matchedField != null) {
                val normalizedVal = normalizeValueForFieldType(rawValue, matchedField)
                val typeErr = validateCustomFieldValue(matchedField, normalizedVal, checkRequired = false)
                if (typeErr != null) {
                    errors.add(typeErr)
                }
                mappedValuesById[matchedField.id] = normalizedVal
            } else {
                unmappedFields[rawKey] = rawValue
            }
        }

        // Warn if any required fields on the target Custom Type are not yet populated in the imported JSON
        for (def in effectiveDefs) {
            if (def.required) {
                val reqErr = validateCustomFieldValue(def, mappedValuesById[def.id], checkRequired = true)
                if (reqErr != null && mappedValuesById[def.id].isNullOrBlank()) {
                    warnings.add("Required custom field '${def.name}' is empty in imported JSON; you must fill it before saving.")
                }
            }
        }

        if (unmappedFields.isNotEmpty() && matchedExisting != null) {
            warnings.add(
                "Detected ${unmappedFields.size} field(s) not in '${matchedExisting.name}': ${unmappedFields.keys.joinToString(", ")}. " +
                    "They will be preserved in entry notes/content unless mapped."
            )
        }

        val requiresNewTypeConfirmation = matchedExisting == null && parsedNewDraft != null
        if (matchedExisting != null && defObj != null) {
            warnings.add("Custom Type '${matchedExisting.name}' already exists; existing definition will be preserved.")
        }

        if (title.isBlank() && rawFieldEntries.isEmpty() && content.isBlank()) {
            errors.add("Custom Type JSON must include a 'title', 'fields', or 'content'.")
        }

        return SmartImportValidationResult(
            isValid = errors.isEmpty(),
            detectedFormat = SmartImportFormat.JSON,
            targetCategory = VaultCategory.IDEA,
            title = title,
            description = description,
            content = content,
            notes = notes,
            tags = tags,
            customTypeName = matchedExisting?.name ?: parsedNewDraft?.name ?: customTypeName.ifBlank { null },
            matchedCustomTypeId = matchedExisting?.id,
            newCustomTypeDraft = if (matchedExisting == null) parsedNewDraft else null,
            requiresCustomTypeCreationConfirmation = requiresNewTypeConfirmation,
            customFieldValuesById = mappedValuesById,
            unmappedCustomFields = unmappedFields,
            warnings = warnings,
            errors = errors
        )
    }

    private fun serializeJsonCustomFieldValue(rawVal: Any?): String {
        if (rawVal == null || rawVal == JSONObject.NULL) return ""
        return when (rawVal) {
            is JSONArray -> {
                val first = if (rawVal.length() > 0) rawVal.opt(0) else null
                if (first is JSONObject) {
                    val items = mutableListOf<CustomChecklistItem>()
                    for (i in 0 until rawVal.length()) {
                        val obj = rawVal.optJSONObject(i) ?: continue
                        val text = obj.optString("text", obj.optString("title", "")).trim()
                        if (text.isNotEmpty()) {
                            items.add(
                                CustomChecklistItem(
                                    id = "chk_${UUID.randomUUID()}",
                                    text = text,
                                    checked = obj.optBoolean("checked", false)
                                )
                            )
                        }
                    }
                    VaultJsonSerializers.encodeCustomChecklistItems(items)
                } else {
                    val list = parseStringArray(rawVal)
                    VaultJsonSerializers.encodeStringList(list)
                }
            }
            else -> rawVal.toString()
        }
    }

    fun normalizeValueForFieldType(rawValue: String, field: CustomFieldDefinition): String {
        return when (field.type) {
            CustomFieldType.CHECKBOX -> {
                when (val normalized = rawValue.trim().lowercase()) {
                    "true", "1", "yes", "checked" -> "true"
                    "false", "0", "no", "unchecked" -> "false"
                    "" -> ""
                    else -> rawValue.trim() // Keep unrecognized value so validateCustomFieldValue reports an error
                }
            }
            CustomFieldType.MULTI_SELECT -> {
                val trimmed = rawValue.trim()
                if (trimmed.startsWith("[")) {
                    trimmed
                } else if (trimmed.isEmpty()) {
                    "[]"
                } else {
                    val items = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    VaultJsonSerializers.encodeStringList(items)
                }
            }
            CustomFieldType.CHECKLIST -> {
                val trimmed = rawValue.trim()
                if (trimmed.startsWith("[")) {
                    trimmed
                } else {
                    val items = trimmed.lines().mapNotNull { line ->
                        val t = line.trim()
                        if (t.isEmpty()) null
                        else {
                            val isChecked = t.startsWith("- [x]", ignoreCase = true) || t.startsWith("[x]", ignoreCase = true)
                            val cleanText = t
                                .removePrefix("- [x]").removePrefix("- [X]")
                                .removePrefix("- [ ]").removePrefix("[x]")
                                .removePrefix("[X]").removePrefix("[ ]")
                                .removePrefix("- ").trim()
                            CustomChecklistItem(text = cleanText.ifEmpty { t }, checked = isChecked)
                        }
                    }
                    VaultJsonSerializers.encodeCustomChecklistItems(items)
                }
            }
            else -> rawValue
        }
    }

    /**
     * P1-11 & P2-14: Authoritative validation for Custom Field values (both requiredness and type rules).
     * Returns a human-readable error message if invalid, or null if valid.
     */
    fun validateCustomFieldValue(
        fieldDef: CustomFieldDefinition,
        rawValue: String?,
        checkRequired: Boolean = true
    ): String? {
        val raw = rawValue.orEmpty()
        val isEffectivelyEmpty = when (fieldDef.type) {
            CustomFieldType.MULTI_SELECT -> {
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed == "[]") true
                else if (trimmed.startsWith("[")) VaultJsonSerializers.decodeStringList(trimmed).all { it.isBlank() }
                else trimmed.split(",").all { it.isBlank() }
            }
            CustomFieldType.CHECKLIST -> {
                val trimmed = raw.trim()
                if (trimmed.isEmpty() || trimmed == "[]") true
                else if (trimmed.startsWith("[")) {
                    val isValidJsonArr = runCatching { JSONArray(trimmed); true }.getOrDefault(false)
                    if (!isValidJsonArr) false
                    else VaultJsonSerializers.decodeCustomChecklistItems(trimmed).all { it.text.isBlank() }
                } else false
            }
            else -> raw.isBlank()
        }

        if (isEffectivelyEmpty) {
            return if (checkRequired && fieldDef.required) {
                "Required field '${fieldDef.name}' cannot be empty."
            } else {
                null
            }
        }

        val trimmed = raw.trim()
        return when (fieldDef.type) {
            CustomFieldType.NUMBER -> {
                if (trimmed.toBigDecimalOrNull() == null) {
                    "Field '${fieldDef.name}' must be a valid number (got '$trimmed')."
                } else null
            }
            CustomFieldType.DATE -> {
                val isValidDate = runCatching {
                    java.time.LocalDate.parse(trimmed, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    true
                }.getOrDefault(false)
                if (!isValidDate) {
                    "Field '${fieldDef.name}' must be a valid calendar date in YYYY-MM-DD format (got '$trimmed')."
                } else null
            }
            CustomFieldType.URL -> {
                val lower = trimmed.lowercase()
                val hasScheme = lower.startsWith("http://") || lower.startsWith("https://")
                val hasNoSpaces = !trimmed.any { it.isWhitespace() }
                val hasValidHost = hasScheme && hasNoSpaces && runCatching {
                    val uri = java.net.URI(trimmed)
                    val host = uri.host
                    !host.isNullOrBlank() && (host.contains('.') || host.equals("localhost", ignoreCase = true))
                }.getOrDefault(false)
                if (!hasValidHost) {
                    "Field '${fieldDef.name}' must be a valid http:// or https:// URL (got '$trimmed')."
                } else null
            }
            CustomFieldType.CHECKBOX -> {
                val lower = trimmed.lowercase()
                if (lower != "true" && lower != "false") {
                    "Field '${fieldDef.name}' must be a boolean ('true' or 'false', got '$trimmed')."
                } else null
            }
            CustomFieldType.SINGLE_SELECT -> {
                if (fieldDef.options.isNotEmpty() &&
                    fieldDef.options.none { it.equals(trimmed, ignoreCase = true) }
                ) {
                    "Field '${fieldDef.name}' value '$trimmed' is not in allowed options (${fieldDef.options.joinToString(", ")})."
                } else null
            }
            CustomFieldType.MULTI_SELECT -> {
                val selected = if (trimmed.startsWith("[")) {
                    VaultJsonSerializers.decodeStringList(trimmed)
                } else {
                    trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                }
                if (fieldDef.options.isNotEmpty()) {
                    val disallowed = selected.filter { sel ->
                        fieldDef.options.none { opt -> opt.equals(sel, ignoreCase = true) }
                    }
                    if (disallowed.isNotEmpty()) {
                        "Field '${fieldDef.name}' contains disallowed option(s): ${disallowed.joinToString(", ")}. Allowed: ${fieldDef.options.joinToString(", ")}."
                    } else null
                } else null
            }
            CustomFieldType.CHECKLIST -> {
                if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                    val validJson = runCatching { JSONArray(trimmed); true }.getOrDefault(false)
                    if (!validJson) "Field '${fieldDef.name}' has invalid checklist data." else null
                } else null
            }
            CustomFieldType.SHORT_TEXT, CustomFieldType.LONG_TEXT, CustomFieldType.CODE -> null
        }
    }

    /**
     * Validates all custom fields for a Custom Type definition and returns a map of `fieldId -> errorMessage`.
     */
    fun validateAllCustomFields(
        definitions: List<CustomFieldDefinition>,
        valuesMap: Map<String, String>,
        checkRequired: Boolean = true
    ): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        for (def in definitions.sortedBy { it.orderIndex }) {
            val err = validateCustomFieldValue(def, valuesMap[def.id], checkRequired = checkRequired)
            if (err != null) {
                errors[def.id] = err
            }
        }
        return errors
    }

    fun formatUnmappedCustomFieldsBlock(unmapped: Map<String, String>, headerLabel: String = "Imported Unmapped Fields"): String {
        if (unmapped.isEmpty()) return ""
        return buildString {
            appendLine("[$headerLabel]")
            unmapped.forEach { (k, v) ->
                appendLine("$k: $v")
            }
        }.trimEnd()
    }

    private fun parseMarkdownImport(
        rawInput: String,
        defaultCategory: VaultCategory,
        activeCustomType: CustomTypeEntity?,
        targetCustomFieldId: String?
    ): SmartImportValidationResult {
        // Prompt category always preserves exact text byte-for-byte
        if (defaultCategory == VaultCategory.PROMPT && activeCustomType == null) {
            return SmartImportValidationResult(
                isValid = true,
                detectedFormat = SmartImportFormat.MARKDOWN,
                targetCategory = VaultCategory.PROMPT,
                content = rawInput
            )
        }

        val lines = rawInput.lines()
        var extractedTitle = ""
        val richBlocks = mutableListOf<RichContentBlock>()
        val extractedSteps = mutableListOf<SmartImportStepDraft>()
        val descriptionLines = mutableListOf<String>()

        var inCodeFence = false
        var codeFenceLanguage = ""
        val codeFenceBuffer = StringBuilder()

        val bulletBuffer = mutableListOf<String>()
        val numberedBuffer = mutableListOf<String>()
        val paragraphBuffer = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                val text = paragraphBuffer.joinToString("\n").trimEnd()
                if (text.isNotBlank()) {
                    richBlocks.add(
                        RichContentBlock(
                            id = "block_${UUID.randomUUID()}",
                            type = RichBlockType.PARAGRAPH,
                            content = text
                        )
                    )
                    descriptionLines.add(text)
                }
                paragraphBuffer.clear()
            }
        }

        fun flushBullets() {
            if (bulletBuffer.isNotEmpty()) {
                richBlocks.add(
                    RichContentBlock(
                        id = "block_${UUID.randomUUID()}",
                        type = RichBlockType.BULLET_LIST,
                        content = bulletBuffer.joinToString("\n")
                    )
                )
                bulletBuffer.clear()
            }
        }

        fun flushNumbered() {
            if (numberedBuffer.isNotEmpty()) {
                richBlocks.add(
                    RichContentBlock(
                        id = "block_${UUID.randomUUID()}",
                        type = RichBlockType.NUMBERED_LIST,
                        content = numberedBuffer.joinToString("\n")
                    )
                )
                numberedBuffer.clear()
            }
        }

        val numberedRegex = Regex("""^\s*(\d+)[\.\)]\s+(.+)$""")
        var currentStepTitle: String? = null
        val currentStepDetails = mutableListOf<String>()

        fun flushCurrentStep() {
            val stTitle = currentStepTitle
            if (stTitle != null) {
                extractedSteps.add(
                    SmartImportStepDraft(
                        stepNumber = extractedSteps.size + 1,
                        title = stTitle,
                        details = currentStepDetails.joinToString("\n").trim()
                    )
                )
                currentStepTitle = null
                currentStepDetails.clear()
            }
        }

        for (line in lines) {
            val trimmedStart = line.trimStart()
            if (trimmedStart.startsWith("```")) {
                if (!inCodeFence) {
                    flushParagraph()
                    flushBullets()
                    flushNumbered()
                    inCodeFence = true
                    codeFenceLanguage = trimmedStart.removePrefix("```").trim()
                    codeFenceBuffer.clear()
                } else {
                    inCodeFence = false
                    val codeContent = codeFenceBuffer.toString().removeSuffix("\n")
                    richBlocks.add(
                        RichContentBlock(
                            id = "block_${UUID.randomUUID()}",
                            type = RichBlockType.CODE_BLOCK,
                            title = codeFenceLanguage,
                            content = codeContent
                        )
                    )
                    if (currentStepTitle != null) {
                        currentStepDetails.add("```$codeFenceLanguage\n$codeContent\n```")
                    }
                }
                continue
            }

            if (inCodeFence) {
                // CRITICAL: Inside fenced code blocks, never parse numbered lines or headings as steps!
                codeFenceBuffer.append(line).append('\n')
                continue
            }

            when {
                trimmedStart.startsWith("# ") && extractedTitle.isEmpty() -> {
                    flushParagraph()
                    flushBullets()
                    flushNumbered()
                    flushCurrentStep()
                    extractedTitle = trimmedStart.removePrefix("# ").trim()
                }
                trimmedStart.startsWith("# ") || trimmedStart.startsWith("## ") || trimmedStart.startsWith("### ") -> {
                    flushParagraph()
                    flushBullets()
                    flushNumbered()
                    flushCurrentStep()
                    val headingText = trimmedStart.trimStart('#').trim()
                    if (headingText.isNotEmpty()) {
                        richBlocks.add(
                            RichContentBlock(
                                id = "block_${UUID.randomUUID()}",
                                type = RichBlockType.HEADING,
                                title = headingText,
                                content = headingText
                            )
                        )
                    }
                }
                trimmedStart.startsWith("- [ ] ") || trimmedStart.startsWith("- [x] ", ignoreCase = true) -> {
                    flushParagraph()
                    flushBullets()
                    flushNumbered()
                    val isChecked = trimmedStart.startsWith("- [x] ", ignoreCase = true)
                    val itemText = trimmedStart.substring(6).trim()
                    richBlocks.add(
                        RichContentBlock(
                            id = "block_${UUID.randomUUID()}",
                            type = RichBlockType.CHECKLIST,
                            title = itemText,
                            content = itemText,
                            checked = isChecked
                        )
                    )
                    if (currentStepTitle != null) {
                        currentStepDetails.add(line)
                    }
                }
                trimmedStart.startsWith("- ") || trimmedStart.startsWith("* ") -> {
                    flushParagraph()
                    flushNumbered()
                    val bulletText = trimmedStart.substring(2).trim()
                    bulletBuffer.add(bulletText)
                    if (currentStepTitle != null) {
                        currentStepDetails.add("- $bulletText")
                    }
                }
                numberedRegex.matches(trimmedStart) -> {
                    flushParagraph()
                    flushBullets()
                    val match = numberedRegex.matchEntire(trimmedStart)!!
                    val stepBody = match.groupValues[2].trim()
                    numberedBuffer.add(stepBody)

                    flushCurrentStep()
                    val colonIndex = stepBody.indexOf(':')
                    if (colonIndex in 1..60 && colonIndex < stepBody.length - 1) {
                        currentStepTitle = stepBody.substring(0, colonIndex).trim()
                        currentStepDetails.add(stepBody.substring(colonIndex + 1).trim())
                    } else {
                        currentStepTitle = stepBody
                    }
                }
                trimmedStart.startsWith("> ") -> {
                    flushParagraph()
                    flushBullets()
                    flushNumbered()
                    val quoteText = trimmedStart.removePrefix("> ").trim()
                    richBlocks.add(
                        RichContentBlock(
                            id = "block_${UUID.randomUUID()}",
                            type = RichBlockType.QUOTE,
                            content = quoteText
                        )
                    )
                    if (currentStepTitle != null) {
                        currentStepDetails.add(line)
                    }
                }
                trimmedStart == "---" || trimmedStart == "***" -> {
                    flushParagraph()
                    flushBullets()
                    flushNumbered()
                    richBlocks.add(
                        RichContentBlock(
                            id = "block_${UUID.randomUUID()}",
                            type = RichBlockType.DIVIDER
                        )
                    )
                }
                else -> {
                    flushBullets()
                    flushNumbered()
                    if (currentStepTitle != null) {
                        if (line.isNotBlank() || currentStepDetails.isNotEmpty()) {
                            currentStepDetails.add(line)
                        }
                    } else {
                        paragraphBuffer.add(line)
                    }
                }
            }
        }

        if (inCodeFence && codeFenceBuffer.isNotEmpty()) {
            val codeContent = codeFenceBuffer.toString().removeSuffix("\n")
            richBlocks.add(
                RichContentBlock(
                    id = "block_${UUID.randomUUID()}",
                    type = RichBlockType.CODE_BLOCK,
                    title = codeFenceLanguage,
                    content = codeContent
                )
            )
        }
        flushParagraph()
        flushBullets()
        flushNumbered()
        flushCurrentStep()

        if (extractedSteps.size > MAX_IMPORT_STEPS) {
            return SmartImportValidationResult(
                isValid = false,
                detectedFormat = SmartImportFormat.MARKDOWN,
                targetCategory = defaultCategory,
                errors = listOf("Markdown contains ${extractedSteps.size} steps, exceeding the maximum limit of $MAX_IMPORT_STEPS steps.")
            )
        }

        // Handle Custom Type Markdown import
        if (activeCustomType != null) {
            val defs = VaultJsonSerializers.decodeCustomFieldDefinitions(activeCustomType.fieldsJson)
            val chosenFieldId = targetCustomFieldId ?: defs.firstOrNull {
                it.type == CustomFieldType.LONG_TEXT || it.type == CustomFieldType.SHORT_TEXT
            }?.id
            val fieldMap = if (chosenFieldId != null) mapOf(chosenFieldId to rawInput) else emptyMap()
            return SmartImportValidationResult(
                isValid = true,
                detectedFormat = SmartImportFormat.MARKDOWN,
                targetCategory = defaultCategory,
                title = extractedTitle,
                content = rawInput,
                customTypeName = activeCustomType.name,
                matchedCustomTypeId = activeCustomType.id,
                customFieldValuesById = fieldMap
            )
        }

        return when (defaultCategory) {
            VaultCategory.METHOD -> {
                val warnings = mutableListOf<String>()
                if (extractedSteps.isEmpty()) {
                    warnings.add("No numbered steps found in Markdown; importing content into Method description.")
                }
                SmartImportValidationResult(
                    isValid = true,
                    detectedFormat = SmartImportFormat.MARKDOWN,
                    targetCategory = VaultCategory.METHOD,
                    title = extractedTitle,
                    description = if (extractedSteps.isEmpty()) rawInput else descriptionLines.joinToString("\n\n"),
                    mainSteps = extractedSteps,
                    warnings = warnings
                )
            }
            VaultCategory.KNOWLEDGE -> {
                // Note import: keep rich blocks structured without duplicating everything into raw content
                SmartImportValidationResult(
                    isValid = true,
                    detectedFormat = SmartImportFormat.MARKDOWN,
                    targetCategory = VaultCategory.KNOWLEDGE,
                    title = extractedTitle,
                    content = if (richBlocks.isEmpty()) rawInput else "",
                    richBlocks = richBlocks
                )
            }
            else -> {
                SmartImportValidationResult(
                    isValid = true,
                    detectedFormat = SmartImportFormat.MARKDOWN,
                    targetCategory = defaultCategory,
                    title = extractedTitle,
                    content = rawInput
                )
            }
        }
    }

    private fun parsePlainTextImport(
        rawInput: String,
        defaultCategory: VaultCategory,
        activeCustomType: CustomTypeEntity?,
        plainTextMethodOption: PlainTextMethodOption,
        targetCustomFieldId: String?
    ): SmartImportValidationResult {
        // Prompt always preserves exact raw text byte-for-byte
        if (defaultCategory == VaultCategory.PROMPT && activeCustomType == null) {
            return SmartImportValidationResult(
                isValid = true,
                detectedFormat = SmartImportFormat.PLAIN_TEXT,
                targetCategory = VaultCategory.PROMPT,
                content = rawInput
            )
        }

        if (activeCustomType != null) {
            val defs = VaultJsonSerializers.decodeCustomFieldDefinitions(activeCustomType.fieldsJson)
            val chosenField = defs.firstOrNull { it.id == targetCustomFieldId }
                ?: defs.firstOrNull { it.type == CustomFieldType.LONG_TEXT || it.type == CustomFieldType.SHORT_TEXT }
                ?: defs.firstOrNull()
            val fieldMap = if (chosenField != null) {
                mapOf(chosenField.id to normalizeValueForFieldType(rawInput, chosenField))
            } else {
                emptyMap()
            }
            return SmartImportValidationResult(
                isValid = true,
                detectedFormat = SmartImportFormat.PLAIN_TEXT,
                targetCategory = defaultCategory,
                content = if (fieldMap.isEmpty()) rawInput else "",
                customTypeName = activeCustomType.name,
                matchedCustomTypeId = activeCustomType.id,
                customFieldValuesById = fieldMap
            )
        }

        if (defaultCategory == VaultCategory.METHOD) {
            return when (plainTextMethodOption) {
                PlainTextMethodOption.AS_DESCRIPTION -> {
                    SmartImportValidationResult(
                        isValid = true,
                        detectedFormat = SmartImportFormat.PLAIN_TEXT,
                        targetCategory = VaultCategory.METHOD,
                        description = rawInput
                    )
                }
                PlainTextMethodOption.NUMBERED_LINES_TO_STEPS -> {
                    val (extractedTitle, steps) = extractTitleAndNumberedStepsFromPlainText(rawInput)
                    if (steps.size > MAX_IMPORT_STEPS) {
                        return SmartImportValidationResult(
                            isValid = false,
                            detectedFormat = SmartImportFormat.PLAIN_TEXT,
                            targetCategory = VaultCategory.METHOD,
                            errors = listOf("Detected ${steps.size} steps, exceeding the maximum limit of $MAX_IMPORT_STEPS steps.")
                        )
                    }
                    val warnings = if (steps.isEmpty()) {
                        listOf("No numbered lines found; importing text as Method description.")
                    } else emptyList()
                    SmartImportValidationResult(
                        isValid = true,
                        detectedFormat = SmartImportFormat.PLAIN_TEXT,
                        targetCategory = VaultCategory.METHOD,
                        title = extractedTitle,
                        description = if (steps.isEmpty()) rawInput else "",
                        mainSteps = steps,
                        warnings = warnings
                    )
                }
                PlainTextMethodOption.LINE_SEPARATED_TO_STEPS -> {
                    val nonBlankLines = rawInput.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (nonBlankLines.size > MAX_IMPORT_STEPS) {
                        return SmartImportValidationResult(
                            isValid = false,
                            detectedFormat = SmartImportFormat.PLAIN_TEXT,
                            targetCategory = VaultCategory.METHOD,
                            errors = listOf("Detected ${nonBlankLines.size} lines, exceeding the maximum limit of $MAX_IMPORT_STEPS steps.")
                        )
                    }
                    val steps = nonBlankLines.mapIndexed { idx, line ->
                        val colonIdx = line.indexOf(':')
                        if (colonIdx in 1..60 && colonIdx < line.length - 1) {
                            SmartImportStepDraft(
                                stepNumber = idx + 1,
                                title = line.substring(0, colonIdx).trim(),
                                details = line.substring(colonIdx + 1).trim()
                            )
                        } else {
                            SmartImportStepDraft(
                                stepNumber = idx + 1,
                                title = line.take(80),
                                details = if (line.length > 80) line else ""
                            )
                        }
                    }
                    SmartImportValidationResult(
                        isValid = steps.isNotEmpty(),
                        detectedFormat = SmartImportFormat.PLAIN_TEXT,
                        targetCategory = VaultCategory.METHOD,
                        mainSteps = steps,
                        errors = if (steps.isEmpty()) listOf("No non-empty lines found to convert into steps.") else emptyList()
                    )
                }
            }
        }

        // IDEA or KNOWLEDGE (Note)
        return SmartImportValidationResult(
            isValid = true,
            detectedFormat = SmartImportFormat.PLAIN_TEXT,
            targetCategory = defaultCategory,
            content = rawInput
        )
    }

    private fun extractTitleAndNumberedStepsFromPlainText(rawInput: String): Pair<String, List<SmartImportStepDraft>> {
        val numberedRegex = Regex("""^\s*(?:Step\s*)?(\d+)[\.\)\:\-]\s*(.+)$""", RegexOption.IGNORE_CASE)
        val steps = mutableListOf<SmartImportStepDraft>()
        var extractedTitle = ""
        var currentTitle: String? = null
        val currentDetails = mutableListOf<String>()

        fun flush() {
            val t = currentTitle
            if (t != null) {
                steps.add(
                    SmartImportStepDraft(
                        stepNumber = steps.size + 1,
                        title = t,
                        details = currentDetails.joinToString("\n").trim()
                    )
                )
                currentTitle = null
                currentDetails.clear()
            }
        }

        var inCodeFence = false
        for (line in rawInput.lines()) {
            val trimmedStart = line.trimStart()
            if (trimmedStart.startsWith("```")) {
                inCodeFence = !inCodeFence
                if (currentTitle != null) currentDetails.add(line)
                continue
            }
            if (inCodeFence) {
                if (currentTitle != null) currentDetails.add(line)
                continue
            }
            val match = numberedRegex.matchEntire(trimmedStart)
            if (match != null) {
                flush()
                val rest = match.groupValues[2].trim()
                val colonIdx = rest.indexOf(':')
                if (colonIdx in 1..60 && colonIdx < rest.length - 1) {
                    currentTitle = rest.substring(0, colonIdx).trim()
                    currentDetails.add(rest.substring(colonIdx + 1).trim())
                } else {
                    currentTitle = rest
                }
            } else if (currentTitle != null) {
                currentDetails.add(line)
            } else if (steps.isEmpty() && extractedTitle.isEmpty() && trimmedStart.isNotEmpty()) {
                extractedTitle = trimmedStart.removePrefix("#").trim()
            }
        }
        flush()
        return extractedTitle to steps
    }

    private fun parseStringArray(raw: Any?): List<String> {
        if (raw == null || raw == JSONObject.NULL) return emptyList()
        if (raw is JSONArray) {
            val out = mutableListOf<String>()
            for (i in 0 until raw.length()) {
                val s = raw.optString(i, "").trim()
                if (s.isNotEmpty()) out.add(s)
            }
            return out
        }
        if (raw is String && raw.isNotBlank()) {
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return emptyList()
    }

    // =========================================================================
    // COPY & EXPORT ENGINE (100% OFFLINE)
    // =========================================================================

    fun formatCopyAll(
        item: VaultItemWithDetails,
        customType: CustomTypeEntity? = item.customType
    ): String {
        val e = item.entity
        if (item.categoryEnum == VaultCategory.PROMPT && customType == null) {
            return buildString {
                if (e.title.isNotBlank()) {
                    appendLine(e.title)
                    appendLine()
                }
                append(e.content)
                val promptNotes = RichTextSerializer.toPlainText(e.notes.ifBlank { e.description })
                if (promptNotes.isNotBlank()) {
                    appendLine()
                    appendLine()
                    appendLine("Notes:")
                    append(promptNotes)
                }
            }
        }

        return buildString {
            val typeLabel = customType?.name ?: item.categoryEnum.label
            appendLine("${e.title} [$typeLabel]")
            if (item.tags.isNotEmpty()) {
                appendLine("Tags: ${item.tags.joinToString(", ")}")
            }
            appendLine()

            if (customType != null) {
                val defs = VaultJsonSerializers.decodeCustomFieldDefinitions(customType.fieldsJson)
                    .sortedBy { it.orderIndex }
                val values = item.customFieldsMap
                if (defs.isNotEmpty()) {
                    defs.forEach { def ->
                        val rawVal = values[def.id].orEmpty()
                        if (rawVal.isNotBlank()) {
                            appendLine("${def.name}: ${formatCustomFieldValueReadable(def, rawVal)}")
                        }
                    }
                    appendLine()
                }
            }

            val cleanDesc = RichTextSerializer.toPlainText(e.description)
            if (cleanDesc.isNotBlank()) {
                appendLine(cleanDesc)
                appendLine()
            }

            if (item.categoryEnum == VaultCategory.METHOD) {
                val routes = item.effectiveMethodRoutes
                if (routes.isNotEmpty()) {
                    routes.forEach { rws ->
                        appendLine("=== ${rws.route.name} ===")
                        val rDesc = RichTextSerializer.toPlainText(rws.route.description)
                        if (rDesc.isNotBlank()) {
                            appendLine(rDesc)
                        }
                        rws.steps.sortedBy { it.stepOrder }.forEachIndexed { idx, step ->
                            appendLine("${idx + 1}. ${step.title}")
                            val sDesc = RichTextSerializer.toPlainText(step.description)
                            if (sDesc.isNotBlank()) {
                                appendLine("   $sDesc")
                            }
                            val sNotes = RichTextSerializer.toPlainText(step.notes)
                            if (sNotes.isNotBlank()) {
                                appendLine("   Note: $sNotes")
                            }
                        }
                        appendLine()
                    }
                } else {
                    item.steps.sortedBy { it.stepOrder }.forEachIndexed { idx, step ->
                        appendLine("${idx + 1}. ${step.title}")
                        val sDesc = RichTextSerializer.toPlainText(step.description)
                        if (sDesc.isNotBlank()) {
                            appendLine("   $sDesc")
                        }
                    }
                    appendLine()
                }
            }

            val cleanContent = RichTextSerializer.toPlainText(e.content)
            if (cleanContent.isNotBlank()) {
                appendLine(cleanContent)
                appendLine()
            }

            val cleanNotes = RichTextSerializer.toPlainText(e.notes)
            if (cleanNotes.isNotBlank() && cleanNotes != cleanDesc) {
                appendLine("Notes:")
                appendLine(cleanNotes)
                appendLine()
            }

            if (item.richBlocks.isNotEmpty()) {
                item.richBlocks.forEach { block ->
                    when (block.type) {
                        RichBlockType.HEADING -> appendLine("## ${block.title.ifBlank { block.content }}")
                        RichBlockType.PARAGRAPH -> appendLine(block.content)
                        RichBlockType.BULLET_LIST -> block.content.lines().filter { it.isNotBlank() }.forEach {
                            appendLine("• $it")
                        }
                        RichBlockType.NUMBERED_LIST -> block.content.lines().filter { it.isNotBlank() }.forEachIndexed { idx, line ->
                            appendLine("${idx + 1}. $line")
                        }
                        RichBlockType.CHECKLIST -> {
                            val mark = if (block.checked) "[x]" else "[ ]"
                            appendLine("$mark ${block.title.ifBlank { block.content }}")
                        }
                        RichBlockType.CODE_BLOCK -> {
                            appendLine(block.content)
                        }
                        RichBlockType.QUOTE -> appendLine("> ${block.content}")
                        RichBlockType.DIVIDER -> appendLine("---")
                    }
                }
            }
        }.trimEnd()
    }

    /**
     * Generates a clean, portable IDEAVAULT V2.4 JSON string without internal database IDs,
     * App Lock secrets, or private local file paths.
     */
    fun formatCopyAsJson(
        item: VaultItemWithDetails,
        customType: CustomTypeEntity? = item.customType
    ): String {
        val e = item.entity
        val root = JSONObject()
        root.put("format", "IDEAVAULT")
        root.put("schemaVersion", 1)

        val resolvedType = when {
            customType != null -> "CUSTOM"
            item.categoryEnum == VaultCategory.METHOD -> "METHOD"
            item.categoryEnum == VaultCategory.PROMPT -> "PROMPT"
            item.categoryEnum == VaultCategory.KNOWLEDGE -> "NOTE"
            else -> "IDEA"
        }
        root.put("type", resolvedType)
        root.put("title", e.title)

        val tagsArr = JSONArray()
        item.tags.forEach { tagsArr.put(it) }
        root.put("tags", tagsArr)

        when (resolvedType) {
            "IDEA" -> {
                root.put("description", RichTextSerializer.toPlainText(e.description))
                root.put("richDescription", e.description)
                root.put("content", RichTextSerializer.toPlainText(e.content))
                root.put("richContent", e.content)
                root.put("summary", RichTextSerializer.toPlainText(e.description))
                root.put("status", item.statusEnum.id)
                root.put("platform", e.platform)
                root.put("estimatedTime", e.estimatedTime)
                root.put("notes", RichTextSerializer.toPlainText(e.notes))
                root.put("richNotes", e.notes)
            }
            "METHOD" -> {
                root.put("description", RichTextSerializer.toPlainText(e.description))
                root.put("richDescription", e.description)
                root.put("content", RichTextSerializer.toPlainText(e.content))
                root.put("richContent", e.content)
                root.put("notes", RichTextSerializer.toPlainText(e.notes))
                root.put("richNotes", e.notes)
                root.put("platform", e.platform)
                root.put("estimatedTime", e.estimatedTime)

                val mainRoute = item.effectiveMethodRoutes.firstOrNull { it.route.isMain || it.route.id == MAIN_METHOD_ROUTE_ID }
                    ?: item.effectiveMethodRoutes.firstOrNull()
                val mainName = mainRoute?.route?.name?.ifBlank { DEFAULT_MAIN_METHOD_NAME } ?: DEFAULT_MAIN_METHOD_NAME
                val mainDescRaw = mainRoute?.route?.description.orEmpty()
                root.put("mainMethodName", mainName)
                root.put("mainMethodDescription", RichTextSerializer.toPlainText(mainDescRaw))
                root.put("richMainMethodDescription", mainDescRaw)

                val mainStepsList = mainRoute?.steps ?: item.steps
                val stepsArr = JSONArray()
                mainStepsList.sortedBy { it.stepOrder }.forEachIndexed { idx, step ->
                    val sObj = JSONObject()
                    sObj.put("stepNumber", idx + 1)
                    sObj.put("title", step.title)
                    sObj.put("description", RichTextSerializer.toPlainText(step.description))
                    sObj.put("details", RichTextSerializer.toPlainText(step.description))
                    sObj.put("richDetails", step.description)
                    sObj.put("notes", RichTextSerializer.toPlainText(step.notes))
                    sObj.put("richNotes", step.notes)
                    val linksArr = JSONArray()
                    VaultJsonSerializers.decodeStringList(step.linksJson).forEach { linksArr.put(it) }
                    sObj.put("links", linksArr)
                    stepsArr.put(sObj)
                }
                root.put("steps", stepsArr)
                val mainMethodObj = JSONObject()
                mainMethodObj.put("name", mainName)
                mainMethodObj.put("description", RichTextSerializer.toPlainText(mainDescRaw))
                mainMethodObj.put("richDescription", mainDescRaw)
                mainMethodObj.put("steps", stepsArr)
                root.put("mainMethod", mainMethodObj)

                val altArr = JSONArray()
                item.alternativeMethodRoutes.forEach { altRoute ->
                    val aObj = JSONObject()
                    aObj.put("name", altRoute.route.name)
                    aObj.put("description", RichTextSerializer.toPlainText(altRoute.route.description))
                    aObj.put("richDescription", altRoute.route.description)
                    val aSteps = JSONArray()
                    altRoute.steps.sortedBy { it.stepOrder }.forEachIndexed { idx, step ->
                        val sObj = JSONObject()
                        sObj.put("stepNumber", idx + 1)
                        sObj.put("title", step.title)
                        sObj.put("description", RichTextSerializer.toPlainText(step.description))
                        sObj.put("details", RichTextSerializer.toPlainText(step.description))
                        sObj.put("richDetails", step.description)
                        sObj.put("notes", RichTextSerializer.toPlainText(step.notes))
                        sObj.put("richNotes", step.notes)
                        val linksArr = JSONArray()
                        VaultJsonSerializers.decodeStringList(step.linksJson).forEach { linksArr.put(it) }
                        sObj.put("links", linksArr)
                        aSteps.put(sObj)
                    }
                    aObj.put("steps", aSteps)
                    altArr.put(aObj)
                }
                root.put("alternativeMethods", altArr)
            }
            "PROMPT" -> {
                // Exact prompt content, description, and notes preserved distinctly without altering whitespace
                root.put("description", RichTextSerializer.toPlainText(e.description))
                root.put("richDescription", e.description)
                root.put("prompt", e.content)
                root.put("promptText", e.content)
                root.put("content", e.content)
                root.put("systemInstructions", "")
                root.put("notes", RichTextSerializer.toPlainText(e.notes))
                root.put("richNotes", e.notes)
            }
            "NOTE" -> {
                root.put("description", RichTextSerializer.toPlainText(e.description))
                root.put("richDescription", e.description)
                root.put("content", RichTextSerializer.toPlainText(e.content))
                root.put("richContent", e.content)
                root.put("notes", RichTextSerializer.toPlainText(e.notes))
                root.put("richNotes", e.notes)
                val blocksArr = JSONArray()
                item.richBlocks.forEach { block ->
                    val bObj = JSONObject()
                    bObj.put(
                        "type",
                        when (block.type) {
                            RichBlockType.HEADING -> "heading"
                            RichBlockType.PARAGRAPH -> "paragraph"
                            RichBlockType.BULLET_LIST -> "bullet_list"
                            RichBlockType.NUMBERED_LIST -> "numbered_list"
                            RichBlockType.CHECKLIST -> "checklist"
                            RichBlockType.CODE_BLOCK -> "code"
                            RichBlockType.QUOTE -> "quote"
                            RichBlockType.DIVIDER -> "divider"
                        }
                    )
                    bObj.put("title", block.title)
                    bObj.put("content", block.content)
                    if (block.type == RichBlockType.CODE_BLOCK) {
                        bObj.put("code", block.content)
                    }
                    if (block.type == RichBlockType.CHECKLIST) {
                        bObj.put("checked", block.checked)
                    }
                    blocksArr.put(bObj)
                }
                root.put("blocks", blocksArr)
            }
            "CUSTOM" -> {
                val ct = customType!!
                root.put("customTypeName", ct.name)
                root.put("description", RichTextSerializer.toPlainText(e.description))
                root.put("richDescription", e.description)
                root.put("content", RichTextSerializer.toPlainText(e.content))
                root.put("richContent", e.content)
                root.put("notes", RichTextSerializer.toPlainText(e.notes))
                root.put("richNotes", e.notes)

                val defs = VaultJsonSerializers.decodeCustomFieldDefinitions(ct.fieldsJson)
                    .sortedBy { it.orderIndex }

                val defObj = JSONObject()
                defObj.put("name", ct.name)
                defObj.put("icon", ct.iconName)
                defObj.put("color", ct.colorHex)
                defObj.put("description", ct.description)
                val defFieldsArr = JSONArray()
                defs.forEach { def ->
                    val fObj = JSONObject()
                    fObj.put("name", def.name)
                    fObj.put("type", def.type.jsonKey)
                    fObj.put("required", def.required)
                    val optArr = JSONArray()
                    def.options.forEach { optArr.put(it) }
                    fObj.put("options", optArr)
                    fObj.put("placeholder", def.placeholder)
                    defFieldsArr.put(fObj)
                }
                defObj.put("fields", defFieldsArr)
                root.put("customTypeDefinition", defObj)

                val fieldsObj = JSONObject()
                val valuesMap = item.customFieldsMap
                defs.forEach { def ->
                    val rawVal = valuesMap[def.id].orEmpty()
                    when (def.type) {
                        CustomFieldType.CHECKBOX -> fieldsObj.put(def.name, rawVal.equals("true", ignoreCase = true))
                        CustomFieldType.MULTI_SELECT -> {
                            val arr = JSONArray()
                            VaultJsonSerializers.decodeStringList(rawVal).forEach { arr.put(it) }
                            fieldsObj.put(def.name, arr)
                        }
                        CustomFieldType.CHECKLIST -> {
                            val arr = JSONArray()
                            VaultJsonSerializers.decodeCustomChecklistItems(rawVal).forEach { chk ->
                                val cObj = JSONObject()
                                cObj.put("text", chk.text)
                                cObj.put("checked", chk.checked)
                                arr.put(cObj)
                            }
                            fieldsObj.put(def.name, arr)
                        }
                        else -> fieldsObj.put(def.name, rawVal)
                    }
                }
                root.put("fields", fieldsObj)
            }
        }

        return root.toString(2)
    }

    private fun safeMarkdownCodeFence(content: String): String {
        val maxBackticks = Regex("`+").findAll(content).maxOfOrNull { it.value.length } ?: 0
        val fenceLen = maxOf(3, maxBackticks + 1)
        return "`".repeat(fenceLen)
    }

    private fun stripInternalRichTagsKeepMarkdown(raw: String): String {
        if (raw.isEmpty()) return ""
        return raw
            .replace(Regex("""\[\[IV_FMT:[^\]]*\]\]"""), "")
            .replace(Regex("""<align=(center|right|left)>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</align>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<size=\d+>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</size>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<color=[A-Z_]+>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""</color>""", RegexOption.IGNORE_CASE), "")
    }

    fun formatCopyAsMarkdown(
        item: VaultItemWithDetails,
        customType: CustomTypeEntity? = item.customType
    ): String {
        val e = item.entity
        return buildString {
            appendLine("# ${e.title}")
            appendLine()
            val typeLabel = customType?.name ?: item.categoryEnum.label
            appendLine("**Type:** $typeLabel")
            if (item.tags.isNotEmpty()) {
                appendLine("**Tags:** ${item.tags.joinToString(", ") { "`$it`" }}")
            }
            appendLine()

            if (customType != null) {
                val defs = VaultJsonSerializers.decodeCustomFieldDefinitions(customType.fieldsJson)
                    .sortedBy { it.orderIndex }
                val values = item.customFieldsMap
                if (defs.isNotEmpty()) {
                    appendLine("## ${customType.name} Fields")
                    defs.forEach { def ->
                        val rawVal = values[def.id].orEmpty()
                        if (rawVal.isNotBlank()) {
                            when (def.type) {
                                CustomFieldType.CODE -> {
                                    val fence = safeMarkdownCodeFence(rawVal)
                                    appendLine("### ${def.name}")
                                    appendLine(fence)
                                    appendLine(rawVal)
                                    appendLine(fence)
                                }
                                CustomFieldType.CHECKLIST -> {
                                    appendLine("### ${def.name}")
                                    VaultJsonSerializers.decodeCustomChecklistItems(rawVal).forEach { chk ->
                                        appendLine("- [${if (chk.checked) "x" else " "}] ${chk.text}")
                                    }
                                }
                                else -> {
                                    appendLine("- **${def.name}:** ${formatCustomFieldValueReadable(def, rawVal)}")
                                }
                            }
                        }
                    }
                    appendLine()
                }
            }

            val cleanDesc = stripInternalRichTagsKeepMarkdown(e.description)
            if (cleanDesc.isNotBlank()) {
                appendLine(cleanDesc)
                appendLine()
            }

            if (item.categoryEnum == VaultCategory.METHOD) {
                val routes = item.effectiveMethodRoutes
                if (routes.isNotEmpty()) {
                    routes.forEach { rws ->
                        val isPrimary = rws.route.isMain || rws.route.id == MAIN_METHOD_ROUTE_ID
                        val headingPrefix = if (isPrimary) "## Main Method" else "### Alternative Method: ${rws.route.name}"
                        appendLine(headingPrefix)
                        val rDesc = stripInternalRichTagsKeepMarkdown(rws.route.description)
                        if (rDesc.isNotBlank()) {
                            appendLine(rDesc)
                        }
                        appendLine()
                        rws.steps.sortedBy { it.stepOrder }.forEachIndexed { idx, step ->
                            val sDesc = stripInternalRichTagsKeepMarkdown(step.description)
                            if (sDesc.isNotBlank()) {
                                appendLine("${idx + 1}. **${step.title}**: $sDesc")
                            } else {
                                appendLine("${idx + 1}. **${step.title}**")
                            }
                            val sNotes = stripInternalRichTagsKeepMarkdown(step.notes)
                            if (sNotes.isNotBlank()) {
                                appendLine("   > Note: $sNotes")
                            }
                        }
                        appendLine()
                    }
                } else {
                    appendLine("## Steps")
                    item.steps.sortedBy { it.stepOrder }.forEachIndexed { idx, step ->
                        val sDesc = stripInternalRichTagsKeepMarkdown(step.description)
                        if (sDesc.isNotBlank()) {
                            appendLine("${idx + 1}. **${step.title}**: $sDesc")
                        } else {
                            appendLine("${idx + 1}. **${step.title}**")
                        }
                    }
                    appendLine()
                }
            }

            if (item.categoryEnum == VaultCategory.PROMPT) {
                if (e.content.isNotEmpty()) {
                    val fence = safeMarkdownCodeFence(e.content)
                    appendLine(fence)
                    appendLine(e.content)
                    appendLine(fence)
                    appendLine()
                }
            } else {
                val mdContent = stripInternalRichTagsKeepMarkdown(e.content)
                if (mdContent.isNotBlank()) {
                    appendLine(mdContent)
                    appendLine()
                }
            }

            if (item.richBlocks.isNotEmpty()) {
                item.richBlocks.forEach { block ->
                    when (block.type) {
                        RichBlockType.HEADING -> appendLine("## ${block.title.ifBlank { block.content }}")
                        RichBlockType.PARAGRAPH -> appendLine(block.content)
                        RichBlockType.BULLET_LIST -> block.content.lines().filter { it.isNotBlank() }.forEach {
                            appendLine("- $it")
                        }
                        RichBlockType.NUMBERED_LIST -> block.content.lines().filter { it.isNotBlank() }.forEachIndexed { idx, line ->
                            appendLine("${idx + 1}. $line")
                        }
                        RichBlockType.CHECKLIST -> {
                            appendLine("- [${if (block.checked) "x" else " "}] ${block.title.ifBlank { block.content }}")
                        }
                        RichBlockType.CODE_BLOCK -> {
                            val fence = safeMarkdownCodeFence(block.content)
                            appendLine("$fence${block.title}")
                            appendLine(block.content)
                            appendLine(fence)
                        }
                        RichBlockType.QUOTE -> appendLine("> ${block.content}")
                        RichBlockType.DIVIDER -> appendLine("---")
                    }
                    appendLine()
                }
            }
        }.trimEnd()
    }

    fun formatCopyForAi(
        item: VaultItemWithDetails,
        customType: CustomTypeEntity? = item.customType,
        userRequest: String = ""
    ): String {
        val typeLabel = customType?.name ?: item.categoryEnum.label
        val body = formatCopyAll(item, customType)
        val effectiveRequest = userRequest.trim().ifBlank {
            "Review this ${typeLabel.lowercase()} and provide structured improvements, actionable feedback, and next steps."
        }
        return buildString {
            appendLine("Analyze or improve the following IDEAVAULT content.")
            appendLine()
            appendLine("Type: $typeLabel")
            appendLine("Title: ${item.entity.title}")
            appendLine()
            appendLine("Content:")
            appendLine(body)
            appendLine()
            appendLine("User Request:")
            append(effectiveRequest)
        }
    }

    fun formatCustomFieldValueReadable(def: CustomFieldDefinition, rawVal: String): String {
        if (rawVal.isBlank()) return ""
        return when (def.type) {
            CustomFieldType.CHECKBOX -> if (rawVal.equals("true", ignoreCase = true)) "Yes" else "No"
            CustomFieldType.MULTI_SELECT -> VaultJsonSerializers.decodeStringList(rawVal).joinToString(", ")
            CustomFieldType.CHECKLIST -> {
                VaultJsonSerializers.decodeCustomChecklistItems(rawVal).joinToString("; ") {
                    "${if (it.checked) "[x]" else "[ ]"} ${it.text}"
                }
            }
            else -> rawVal
        }
    }
}
