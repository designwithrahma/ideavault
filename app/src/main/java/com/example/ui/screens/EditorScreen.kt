package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AttachmentItem
import com.example.data.AttachmentStorageManager
import com.example.data.AttachmentType
import com.example.data.CustomFieldDefinition
import com.example.data.CustomTypeEntity
import com.example.data.DEFAULT_MAIN_METHOD_NAME
import com.example.data.EditorDraftPayload
import com.example.data.FolderEntity
import com.example.data.IdeaStatus
import com.example.data.MAIN_METHOD_ROUTE_ID
import com.example.data.MethodImportMode
import com.example.data.MethodRouteWithSteps
import com.example.data.MethodStepEntity
import com.example.data.MethodTargetRouteChoice
import com.example.data.RichBlockType
import com.example.data.RichContentBlock
import com.example.data.SaveItemResult
import com.example.data.SmartImportExportEngine
import com.example.data.VaultCategory
import com.example.data.VaultItemEntity
import com.example.data.VaultItemWithDetails
import com.example.data.VaultJsonSerializers
import com.example.ui.components.CustomFieldsEditorSection
import com.example.ui.components.CustomTypeBuilderDialog
import com.example.ui.components.DEFAULT_EDITOR_FONT_SIZE
import com.example.ui.components.EditorBottomToolPanel
import com.example.ui.components.InlineRichStyle
import com.example.ui.components.RichTextColorToken
import com.example.ui.components.RichTextEditorController
import com.example.ui.components.RichTextEditorState
import com.example.ui.components.RichTextVisualTransformation
import com.example.ui.components.SmartImportBottomSheet
import com.example.ui.components.V2ContextualEditorBottomBar
import com.example.ui.components.VaultAttachmentCard
import com.example.ui.components.accessibleTextColorForBackground
import com.example.ui.components.copyTextToSystemClipboard
import com.example.ui.components.formatVaultDateTime
import com.example.ui.components.parseHexColorSafe
import com.example.ui.components.themeCardColor
import com.example.ui.theme.IdeaVaultTheme
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Hybrid in-memory + temporary disk-backed session buffer for large EditorScreen states
 * (up to 500 detailed Method steps, Alternative Method routes, Rich Content Blocks, and large
 * Prompt/RichText bodies — FIX 01 & FIX 02).
 *
 * Instead of serializing megabytes of step JSON into the Android SavedInstanceState Bundle
 * (which causes TransactionTooLargeException), the Compose Savers store only a compact
 * reference token (`__iv_ref__:<prefix>_<uuid>`) in the Bundle while keeping the full payload in
 * memory for configuration changes (screen rotation) and in a checksummed JSON envelope inside
 * the app's private cache directory (`editor_state_bundles`) for best-effort process recreation.
 *
 * Note: Android's `cacheDir` is best-effort temporary storage and is NOT guaranteed durable if the
 * OS evicts cache files under storage pressure. Durable recovery relies on `EditorDraftPayload`
 * in DataStore and saved entries in Room. When an internal reference token's recovery file is
 * missing or corrupted, restoration returns `null` (never silently substituting empty strings or
 * empty step lists) and records the failure so `EditorScreen` can fall back to `recoverableDraft`
 * or `existingItem` and surface a visible warning banner.
 */
object EditorLargeStateSessionBuffer {
    private const val BUNDLE_CACHE_DIR = "editor_state_bundles"
    const val REF_PREFIX = "__iv_ref__:"
    const val LIT_PREFIX = "__iv_lit__:"
    private val INTERNAL_TOKEN_REGEX = Regex(
        "^[a-zA-Z0-9]+_[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    private val stepsCache = ConcurrentHashMap<String, List<MethodStepEntity>>()
    private val routesCache = ConcurrentHashMap<String, List<MethodRouteWithSteps>>()
    private val richBlocksCache = ConcurrentHashMap<String, List<RichContentBlock>>()
    private val stringsCache = ConcurrentHashMap<String, String>()
    private val stringListsCache = ConcurrentHashMap<String, List<String>>()
    private val recoveryFailures = ConcurrentHashMap<String, String>()
    private val tokensBySessionKey = ConcurrentHashMap<String, MutableSet<String>>()

    @Volatile
    private var configuredCacheRoot: File? = null

    @Volatile
    private var activeSessionKey: String? = null

    fun configureCacheDir(cacheDir: File?) {
        configuredCacheRoot = cacheDir
    }

    fun setActiveSessionKey(sessionKey: String?) {
        activeSessionKey = sessionKey
    }

    private fun trackTokenForActiveSession(token: String) {
        val key = activeSessionKey ?: return
        tokensBySessionKey.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(token)
    }

    /**
     * Returns true ONLY if [value] starts with [REF_PREFIX] followed by a valid `<prefix>_<uuid>`
     * token generated by this buffer. User literal text such as `"__iv_ref__:example"` returns false.
     */
    fun isInternalReferenceToken(value: String): Boolean {
        if (!value.startsWith(REF_PREFIX)) return false
        val token = value.removePrefix(REF_PREFIX)
        return INTERNAL_TOKEN_REGEX.matches(token)
    }

    private fun recordRecoveryFailure(token: String, reason: String) {
        recoveryFailures[token] = reason
    }

    fun hasRecoveryFailures(): Boolean = recoveryFailures.isNotEmpty()

    fun getLastRecoveryFailures(): Map<String, String> = recoveryFailures.toMap()

    fun consumeRecoveryFailures(): List<String> {
        if (recoveryFailures.isEmpty()) return emptyList()
        val list = recoveryFailures.values.toList()
        recoveryFailures.clear()
        return list
    }

    fun clearRecoveryFailures() {
        recoveryFailures.clear()
    }

    fun resolveDiskFileForToken(refOrToken: String): File? {
        val token = refOrToken.removePrefix(REF_PREFIX)
        return resolveDiskFile(token)
    }

    private fun resolveDiskFile(token: String): File? {
        val root = configuredCacheRoot ?: return null
        val safeName = token.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "default" }
        val dir = File(root, BUNDLE_CACHE_DIR)
        return File(dir, "$safeName.json")
    }

    private fun sha256Hex(input: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun writeDiskEnvelope(token: String, type: String, payload: String) {
        val file = resolveDiskFile(token) ?: return
        try {
            val parent = file.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            val envelope = JSONObject().apply {
                put("v", 1)
                put("type", type)
                put("sha256", sha256Hex(payload))
                put("payload", payload)
            }.toString()
            val tmp = File(parent, "${file.name}.writing")
            tmp.writeText(envelope, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(envelope, Charsets.UTF_8)
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun readDiskEnvelope(token: String, expectedType: String): String? {
        val file = resolveDiskFile(token)
        if (file == null || !file.exists() || !file.isFile) {
            recordRecoveryFailure(token, "Recovery file missing for token '$token'")
            return null
        }
        return try {
            val rawText = file.readText(Charsets.UTF_8)
            val obj = JSONObject(rawText)
            val version = obj.optInt("v", -1)
            val actualType = obj.optString("type", "")
            if (version != 1 || actualType != expectedType || !obj.has("payload")) {
                recordRecoveryFailure(token, "Recovery file corrupted (invalid envelope) for token '$token'")
                return null
            }
            val payload = obj.getString("payload")
            val expectedHash = obj.optString("sha256", "")
            if (expectedHash.isBlank() || sha256Hex(payload) != expectedHash) {
                recordRecoveryFailure(token, "Recovery file checksum mismatch for token '$token'")
                return null
            }
            payload
        } catch (_: Exception) {
            recordRecoveryFailure(token, "Recovery file corrupted (unparseable JSON) for token '$token'")
            null
        }
    }

    fun putSteps(list: List<MethodStepEntity>): String {
        val token = "steps_${UUID.randomUUID()}"
        val snapshot = list.toList()
        stepsCache[token] = snapshot
        trackTokenForActiveSession(token)
        writeDiskEnvelope(token, "steps", VaultJsonSerializers.encodeMethodSteps(snapshot))
        return "$REF_PREFIX$token"
    }

    fun getStepsOrNull(refOrJson: String): List<MethodStepEntity>? {
        if (!isInternalReferenceToken(refOrJson)) {
            if (refOrJson.startsWith(REF_PREFIX)) {
                recordRecoveryFailure(refOrJson, "Invalid step reference token '$refOrJson'")
                return null
            }
            val validJson = runCatching { JSONArray(refOrJson); true }.getOrDefault(false)
            if (!validJson) {
                recordRecoveryFailure("raw_steps", "Corrupted step list JSON payload")
                return null
            }
            return VaultJsonSerializers.decodeMethodSteps(refOrJson)
        }
        val token = refOrJson.removePrefix(REF_PREFIX)
        stepsCache[token]?.let { return it }
        val diskJson = readDiskEnvelope(token, "steps") ?: return null
        val validJson = runCatching { JSONArray(diskJson); true }.getOrDefault(false)
        if (!validJson) {
            recordRecoveryFailure(token, "Corrupted step list JSON inside envelope for '$token'")
            return null
        }
        val decoded = VaultJsonSerializers.decodeMethodSteps(diskJson)
        stepsCache[token] = decoded
        return decoded
    }

    fun getSteps(refOrJson: String): List<MethodStepEntity> {
        return getStepsOrNull(refOrJson) ?: emptyList()
    }

    fun putRoutes(list: List<MethodRouteWithSteps>): String {
        val token = "routes_${UUID.randomUUID()}"
        val snapshot = list.toList()
        routesCache[token] = snapshot
        trackTokenForActiveSession(token)
        writeDiskEnvelope(token, "routes", VaultJsonSerializers.encodeMethodRoutesWithSteps(snapshot))
        return "$REF_PREFIX$token"
    }

    fun getRoutesOrNull(refOrJson: String): List<MethodRouteWithSteps>? {
        if (!isInternalReferenceToken(refOrJson)) {
            if (refOrJson.startsWith(REF_PREFIX)) {
                recordRecoveryFailure(refOrJson, "Invalid route reference token '$refOrJson'")
                return null
            }
            val validJson = runCatching { JSONArray(refOrJson); true }.getOrDefault(false)
            if (!validJson) {
                recordRecoveryFailure("raw_routes", "Corrupted route list JSON payload")
                return null
            }
            return VaultJsonSerializers.decodeMethodRoutesWithSteps(refOrJson)
        }
        val token = refOrJson.removePrefix(REF_PREFIX)
        routesCache[token]?.let { return it }
        val diskJson = readDiskEnvelope(token, "routes") ?: return null
        val validJson = runCatching { JSONArray(diskJson); true }.getOrDefault(false)
        if (!validJson) {
            recordRecoveryFailure(token, "Corrupted route list JSON inside envelope for '$token'")
            return null
        }
        val decoded = VaultJsonSerializers.decodeMethodRoutesWithSteps(diskJson)
        routesCache[token] = decoded
        return decoded
    }

    fun getRoutes(refOrJson: String): List<MethodRouteWithSteps> {
        return getRoutesOrNull(refOrJson) ?: emptyList()
    }

    fun putRichBlocks(list: List<RichContentBlock>): String {
        val token = "blocks_${UUID.randomUUID()}"
        val snapshot = list.toList()
        richBlocksCache[token] = snapshot
        trackTokenForActiveSession(token)
        writeDiskEnvelope(token, "blocks", VaultJsonSerializers.encodeRichBlocks(snapshot))
        return "$REF_PREFIX$token"
    }

    fun getRichBlocksOrNull(refOrJson: String): List<RichContentBlock>? {
        if (!isInternalReferenceToken(refOrJson)) {
            if (refOrJson.startsWith(REF_PREFIX)) {
                recordRecoveryFailure(refOrJson, "Invalid rich block reference token '$refOrJson'")
                return null
            }
            val validJson = runCatching { JSONArray(refOrJson); true }.getOrDefault(false)
            if (!validJson) {
                recordRecoveryFailure("raw_blocks", "Corrupted rich blocks JSON payload")
                return null
            }
            return VaultJsonSerializers.decodeRichBlocks(refOrJson)
        }
        val token = refOrJson.removePrefix(REF_PREFIX)
        richBlocksCache[token]?.let { return it }
        val diskJson = readDiskEnvelope(token, "blocks") ?: return null
        val validJson = runCatching { JSONArray(diskJson); true }.getOrDefault(false)
        if (!validJson) {
            recordRecoveryFailure(token, "Corrupted rich blocks JSON inside envelope for '$token'")
            return null
        }
        val decoded = VaultJsonSerializers.decodeRichBlocks(diskJson)
        richBlocksCache[token] = decoded
        return decoded
    }

    fun getRichBlocks(refOrJson: String): List<RichContentBlock> {
        return getRichBlocksOrNull(refOrJson) ?: emptyList()
    }

    /**
     * Unambiguously encodes [value] for Android Bundle storage:
     * - Short strings (`<= 512` chars) that start with [REF_PREFIX] or [LIT_PREFIX] are escaped with
     *   [LIT_PREFIX] (`__iv_lit__:<value>`) so literal user text like `"__iv_ref__:example"` can
     *   never be mistaken for an internal reference token.
     * - Other short strings (`<= 512` chars) are stored verbatim.
     * - Large strings (`> 512` chars) are stored in memory + checksummed disk envelope and referenced
     *   by `"__iv_ref__:<prefix>_<uuid>"`.
     */
    fun putLargeString(prefix: String, value: String): String {
        if (value.length <= 512) {
            return if (value.startsWith(REF_PREFIX) || value.startsWith(LIT_PREFIX)) {
                "$LIT_PREFIX$value"
            } else {
                value
            }
        }
        val token = "${prefix}_${UUID.randomUUID()}"
        stringsCache[token] = value
        trackTokenForActiveSession(token)
        writeDiskEnvelope(token, "string", value)
        return "$REF_PREFIX$token"
    }

    fun putStringList(prefix: String, list: List<String>): String {
        if (list.isEmpty()) return ""
        val token = "${prefix}_${UUID.randomUUID()}"
        val snapshot = list.toList()
        stringListsCache[token] = snapshot
        trackTokenForActiveSession(token)
        val arr = JSONArray()
        snapshot.forEach { arr.put(it) }
        writeDiskEnvelope(token, "string_list", arr.toString())
        return "$REF_PREFIX$token"
    }

    fun getStringListOrNull(refOrJson: String): List<String>? {
        if (refOrJson.isBlank()) return emptyList()
        if (!isInternalReferenceToken(refOrJson)) {
            if (refOrJson.startsWith(REF_PREFIX)) {
                recordRecoveryFailure(refOrJson, "Invalid string list reference token '$refOrJson'")
                return null
            }
            return runCatching {
                val arr = JSONArray(refOrJson)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        add(arr.optString(i, ""))
                    }
                }
            }.onFailure {
                recordRecoveryFailure("raw_string_list", "Corrupted string list JSON payload")
            }.getOrNull()
        }
        val token = refOrJson.removePrefix(REF_PREFIX)
        stringListsCache[token]?.let { return it }
        val diskJson = readDiskEnvelope(token, "string_list") ?: return null
        val decoded = runCatching {
            val arr = JSONArray(diskJson)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    add(arr.optString(i, ""))
                }
            }
        }.getOrElse {
            recordRecoveryFailure(token, "Corrupted string list JSON inside envelope for '$token'")
            return null
        }
        stringListsCache[token] = decoded
        return decoded
    }

    /**
     * Restores a string previously encoded by [putLargeString]:
     * - If [refOrValue] starts with [LIT_PREFIX], unescapes and returns the exact literal string.
     * - If [refOrValue] is not a valid `<prefix>_<uuid>` internal reference token (e.g., raw literal
     *   `"__iv_ref__:example"` or normal inline text), returns [refOrValue] verbatim.
     * - If [refOrValue] is a valid internal reference token whose memory and disk recovery payload
     *   is missing or corrupted, records a recovery failure and returns `null` (never silently
     *   substituting `""`).
     */
    fun getLargeStringOrNull(refOrValue: String): String? {
        if (refOrValue.startsWith(LIT_PREFIX)) {
            return refOrValue.removePrefix(LIT_PREFIX)
        }
        if (!isInternalReferenceToken(refOrValue)) {
            return refOrValue
        }
        val token = refOrValue.removePrefix(REF_PREFIX)
        stringsCache[token]?.let { return it }
        val diskVal = readDiskEnvelope(token, "string") ?: return null
        stringsCache[token] = diskVal
        return diskVal
    }

    /**
     * Restores a string encoded by [putLargeString], returning [fallbackIfUnavailable] (or the
     * unstripped [refOrValue] token if no fallback is supplied) when an internal reference token
     * is unavailable or corrupted — never silently replacing user content with empty text `""`.
     */
    fun getLargeString(refOrValue: String, fallbackIfUnavailable: String? = null): String {
        getLargeStringOrNull(refOrValue)?.let { return it }
        return fallbackIfUnavailable ?: refOrValue
    }

    fun clearMemoryOnly() {
        stepsCache.clear()
        routesCache.clear()
        richBlocksCache.clear()
        stringsCache.clear()
        stringListsCache.clear()
    }

    /**
     * Removes only the cached state tokens and disk envelope files associated with [sessionKey]
     * so saving or discarding one editor session never wipes active state tokens from another
     * session or background draft.
     */
    fun clearSession(sessionKey: String, context: android.content.Context? = null) {
        if (context != null) configureCacheDir(context.cacheDir)
        val tokens = tokensBySessionKey.remove(sessionKey).orEmpty()
        tokens.forEach { rawToken ->
            val token = rawToken.removePrefix(REF_PREFIX)
            stepsCache.remove(token)
            routesCache.remove(token)
            richBlocksCache.remove(token)
            stringsCache.remove(token)
            stringListsCache.remove(token)
            recoveryFailures.remove(token)
            runCatching {
                resolveDiskFile(token)?.takeIf { it.exists() }?.delete()
            }
        }
        if (activeSessionKey == sessionKey) {
            activeSessionKey = null
        }
    }

    fun clearAll(context: android.content.Context? = null) {
        if (context != null) configureCacheDir(context.cacheDir)
        clearMemoryOnly()
        clearRecoveryFailures()
        tokensBySessionKey.clear()
        activeSessionKey = null
        try {
            val root = context?.cacheDir ?: configuredCacheRoot ?: return
            val dir = File(root, BUNDLE_CACHE_DIR)
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        } catch (_: Exception) {
        }
    }
}

internal val MethodStepListSaver: Saver<MutableList<MethodStepEntity>, List<String>> = Saver(
    save = { list ->
        if (list.isEmpty()) emptyList() else listOf(EditorLargeStateSessionBuffer.putSteps(list))
    },
    restore = { saved ->
        if (saved.isEmpty()) {
            mutableStateListOf()
        } else {
            val restored = mutableStateListOf<MethodStepEntity>()
            for (tokenOrJson in saved) {
                val decoded = EditorLargeStateSessionBuffer.getStepsOrNull(tokenOrJson)
                    ?: return@Saver null
                restored.addAll(decoded)
            }
            restored
        }
    }
)

internal val MethodRouteWithStepsListSaver: Saver<MutableList<MethodRouteWithSteps>, List<String>> = Saver(
    save = { list ->
        if (list.isEmpty()) emptyList() else listOf(EditorLargeStateSessionBuffer.putRoutes(list))
    },
    restore = { saved ->
        if (saved.isEmpty()) {
            mutableStateListOf()
        } else {
            val restored = mutableStateListOf<MethodRouteWithSteps>()
            for (tokenOrJson in saved) {
                val decoded = EditorLargeStateSessionBuffer.getRoutesOrNull(tokenOrJson)
                    ?: return@Saver null
                restored.addAll(decoded)
            }
            restored
        }
    }
)

private val AttachmentItemListSaver = listSaver<MutableList<AttachmentItem>, String>(
    save = { list -> list.map { VaultJsonSerializers.encodeAttachments(listOf(it)) } },
    restore = { saved ->
        mutableStateListOf<AttachmentItem>().apply {
            saved.forEach { json ->
                addAll(VaultJsonSerializers.decodeAttachments(json))
            }
        }
    }
)

internal val RichBlockListSaver: Saver<MutableList<RichContentBlock>, List<String>> = Saver(
    save = { list ->
        if (list.isEmpty()) emptyList() else listOf(EditorLargeStateSessionBuffer.putRichBlocks(list))
    },
    restore = { saved ->
        if (saved.isEmpty()) {
            mutableStateListOf()
        } else {
            val restored = mutableStateListOf<RichContentBlock>()
            for (tokenOrJson in saved) {
                val decoded = EditorLargeStateSessionBuffer.getRichBlocksOrNull(tokenOrJson)
                    ?: return@Saver null
                restored.addAll(decoded)
            }
            restored
        }
    }
)

private val StringListSaver = listSaver<MutableList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf<String>().apply { addAll(it) } }
)

internal val LargeStringSessionSaver: Saver<String, String> = Saver(
    save = { value -> EditorLargeStateSessionBuffer.putLargeString("str", value) },
    restore = { saved -> EditorLargeStateSessionBuffer.getLargeStringOrNull(saved) }
)

internal val StringStackSessionSaver: Saver<List<String>, String> = Saver(
    save = { list -> EditorLargeStateSessionBuffer.putStringList("stack", list) },
    restore = { saved -> EditorLargeStateSessionBuffer.getStringListOrNull(saved) }
)

/**
 * Coalesces rapid single-character Prompt typing into sensible undo checkpoints while always
 * recording:
 * - the initial baseline before the first edit (`undoStackEmpty == true`),
 * - multi-character edits (paste, cut, selection replacement),
 * - typing pauses (`>= 600ms`),
 * - newline insertions or word-boundary transitions (non-whitespace -> whitespace).
 */
internal fun shouldPushPromptUndoCheckpoint(
    previousText: String,
    nextText: String,
    undoStackEmpty: Boolean,
    elapsedMsSinceLastPush: Long
): Boolean {
    if (previousText == nextText) return false
    if (undoStackEmpty) return true
    val lengthDelta = kotlin.math.abs(nextText.length - previousText.length)
    if (lengthDelta > 1) return true
    if (elapsedMsSinceLastPush >= 600L) return true
    if (nextText.length > previousText.length) {
        val nextLast = nextText.lastOrNull()
        val prevLast = previousText.lastOrNull()
        if (nextLast == '\n') return true
        if (prevLast != null && !prevLast.isWhitespace() && nextLast != null && nextLast.isWhitespace()) {
            return true
        }
    } else if (nextText.length < previousText.length) {
        if (previousText.lastOrNull() == '\n') return true
    }
    return false
}

private val NullableCustomTypeEntitySaver: Saver<CustomTypeEntity?, String> = Saver(
    save = { entity ->
        if (entity == null) "" else EditorLargeStateSessionBuffer.putLargeString("ct", VaultJsonSerializers.encodeCustomTypeEntity(entity))
    },
    restore = { saved ->
        if (saved.isBlank()) {
            null
        } else {
            val json = EditorLargeStateSessionBuffer.getLargeStringOrNull(saved) ?: return@Saver null
            VaultJsonSerializers.decodeCustomTypeEntity(json)
        }
    }
)

internal val RichTextEditorStateSaver: Saver<RichTextEditorState, String> = Saver(
    save = { state ->
        val obj = JSONObject()
        obj.put("serialized", state.toSerializedMarkdown())
        obj.put("selStart", state.textFieldValue.selection.start)
        obj.put("selEnd", state.textFieldValue.selection.end)
        obj.put("override", state.typingStyleExplicitlyOverridden)
        obj.put("activeSize", state.activeTypingFontSize)
        obj.put("activeColor", state.activeTypingColor.id)
        val stylesArr = JSONArray()
        state.activeTypingStyles.forEach { stylesArr.put(it.name) }
        obj.put("activeStyles", stylesArr)
        EditorLargeStateSessionBuffer.putLargeString("rich", obj.toString())
    },
    restore = { rawRef ->
        val raw = EditorLargeStateSessionBuffer.getLargeStringOrNull(rawRef) ?: return@Saver null
        runCatching {
            val obj = JSONObject(raw)
            val serialized = obj.optString("serialized", "")
            val restored = RichTextEditorState.fromStoredContent(serialized)
            val len = restored.plainText.length
            val selStart = obj.optInt("selStart", len).coerceIn(0, len)
            val selEnd = obj.optInt("selEnd", len).coerceIn(0, len)
            val override = obj.optBoolean("override", false)
            val activeSize = obj.optInt("activeSize", DEFAULT_EDITOR_FONT_SIZE)
            val activeColor = RichTextColorToken.fromId(obj.optString("activeColor", "DEFAULT"))
            val styles = mutableSetOf<InlineRichStyle>()
            val arr = obj.optJSONArray("activeStyles")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    runCatching {
                        styles.add(InlineRichStyle.valueOf(arr.getString(i)))
                    }
                }
            }
            restored.copy(
                textFieldValue = restored.textFieldValue.copy(
                    selection = TextRange(selStart, selEnd)
                ),
                activeTypingStyles = if (override) styles else restored.activeTypingStyles,
                activeTypingFontSize = if (override) activeSize else restored.activeTypingFontSize,
                activeTypingColor = if (override) activeColor else restored.activeTypingColor,
                typingStyleExplicitlyOverridden = override
            )
        }.getOrElse {
            RichTextEditorState.fromStoredContent(raw)
        }
    }
)

private data class EditorSnapshot(
    val title: String,
    val description: String,
    val content: String,
    val categoryId: String,
    val folderId: String?,
    val ideaStatusId: String,
    val platform: String,
    val estimatedTime: String,
    val notes: String,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val tags: List<String>,
    val richBlocks: List<RichContentBlock>,
    val steps: List<MethodStepEntity>,
    val mainRouteName: String = DEFAULT_MAIN_METHOD_NAME,
    val mainRouteDescription: String = "",
    val alternativeRoutes: List<MethodRouteWithSteps> = emptyList(),
    val attachments: List<AttachmentItem>,
    val customTypeId: String? = null,
    val customFieldsJson: String = "{}",
    val pendingCustomType: CustomTypeEntity? = null
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, FlowPreview::class)
@Composable
fun EditorScreen(
    existingItem: VaultItemWithDetails?,
    initialCategory: VaultCategory,
    initialFolderId: String?,
    folders: List<FolderEntity>,
    recoverableDraft: EditorDraftPayload?,
    attachmentStorage: AttachmentStorageManager,
    onAutoSaveDraft: (String) -> Unit,
    onDiscardDraft: () -> Unit,
    onCreateFolder: (String, VaultCategory, String) -> Unit,
    onSaveItemAwaitable: suspend (
        item: VaultItemEntity,
        steps: List<MethodStepEntity>,
        expectedPreviousUpdatedAt: Long?,
        isEditingExisting: Boolean,
        forceOverwrite: Boolean
    ) -> SaveItemResult,
    onSaveSuccess: (String) -> Unit,
    onCancel: () -> Unit,
    onSaveItemWithRoutesAwaitable: (suspend (
        item: VaultItemEntity,
        steps: List<MethodStepEntity>,
        expectedPreviousUpdatedAt: Long?,
        isEditingExisting: Boolean,
        forceOverwrite: Boolean,
        mainRouteName: String,
        mainRouteDescription: String,
        alternativeRoutes: List<MethodRouteWithSteps>,
        pendingCustomType: CustomTypeEntity?
    ) -> SaveItemResult)? = null,
    initialCustomTypeId: String? = null,
    customTypes: List<CustomTypeEntity> = emptyList(),
    initialOpenSmartImport: Boolean = false,
    onSaveCustomType: (CustomTypeEntity, List<CustomFieldDefinition>, (CustomTypeEntity) -> Unit) -> Unit = { _, _, _ -> },
    onDeleteCustomTypeSafely: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultColors = IdeaVaultTheme.colors
    val stateKey = existingItem?.entity?.id ?: "new_entry"
    remember(context, stateKey) {
        EditorLargeStateSessionBuffer.configureCacheDir(context.cacheDir)
        EditorLargeStateSessionBuffer.setActiveSessionKey(stateKey)
        true
    }

    var itemId by rememberSaveable(stateKey) {
        mutableStateOf(existingItem?.entity?.id ?: UUID.randomUUID().toString())
    }
    var baseUpdatedAt by rememberSaveable(stateKey) {
        mutableLongStateOf(existingItem?.entity?.updatedAt ?: 0L)
    }

    var title by rememberSaveable(stateKey) { mutableStateOf(existingItem?.entity?.title ?: "") }
    var description by rememberSaveable(stateKey, stateSaver = LargeStringSessionSaver) {
        mutableStateOf(existingItem?.entity?.description ?: "")
    }
    var category by rememberSaveable(stateKey) {
        mutableStateOf(existingItem?.categoryEnum ?: initialCategory)
    }

    // For PROMPT entries, preserve exact literal content (including **, _, {TOPIC}, JSON, code fences)
    var promptLiteralContent by rememberSaveable(stateKey, stateSaver = LargeStringSessionSaver) {
        mutableStateOf(existingItem?.entity?.content ?: "")
    }
    var promptUndoStack by rememberSaveable(stateKey, stateSaver = StringStackSessionSaver) {
        mutableStateOf<List<String>>(emptyList())
    }
    var promptRedoStack by rememberSaveable(stateKey, stateSaver = StringStackSessionSaver) {
        mutableStateOf<List<String>>(emptyList())
    }
    var lastPromptUndoPushMs by rememberSaveable(stateKey) { mutableLongStateOf(0L) }

    var richEditorState by rememberSaveable(stateKey, stateSaver = RichTextEditorStateSaver) {
        mutableStateOf(RichTextEditorState.fromStoredContent(existingItem?.entity?.content ?: ""))
    }

    val effectiveSerializedContent = remember(category, promptLiteralContent, richEditorState) {
        if (category == VaultCategory.PROMPT) {
            promptLiteralContent
        } else {
            richEditorState.toSerializedMarkdown()
        }
    }

    var selectedFolderId by rememberSaveable(stateKey) {
        mutableStateOf(existingItem?.entity?.folderId ?: initialFolderId)
    }
    var ideaStatus by rememberSaveable(stateKey) {
        mutableStateOf(existingItem?.statusEnum ?: IdeaStatus.NEW)
    }
    var platform by rememberSaveable(stateKey) {
        mutableStateOf(existingItem?.entity?.platform ?: "")
    }
    var estimatedTime by rememberSaveable(stateKey) {
        mutableStateOf(existingItem?.entity?.estimatedTime ?: "")
    }
    var notes by rememberSaveable(stateKey, stateSaver = LargeStringSessionSaver) {
        mutableStateOf(existingItem?.entity?.notes ?: "")
    }
    var isPinned by rememberSaveable(stateKey) {
        mutableStateOf(existingItem?.entity?.isPinned ?: false)
    }
    var isFavorite by rememberSaveable(stateKey) {
        mutableStateOf(existingItem?.entity?.isFavorite ?: false)
    }

    val selectedTags = rememberSaveable(stateKey, saver = StringListSaver) {
        mutableStateListOf<String>().apply {
            existingItem?.tags?.forEach { add(it) }
        }
    }
    var newTagInput by rememberSaveable(stateKey) { mutableStateOf("") }

    val richBlocks = rememberSaveable(stateKey, saver = RichBlockListSaver) {
        mutableStateListOf<RichContentBlock>().apply {
            existingItem?.richBlocks?.forEach { add(it) }
        }
    }

    val initialMainRoute = remember(existingItem) {
        existingItem?.effectiveMethodRoutes?.firstOrNull { it.isMain }
    }
    var mainRouteName by rememberSaveable(stateKey) {
        mutableStateOf(initialMainRoute?.name ?: DEFAULT_MAIN_METHOD_NAME)
    }
    var mainRouteDescription by rememberSaveable(stateKey) {
        mutableStateOf(initialMainRoute?.description ?: "")
    }

    val steps = rememberSaveable(stateKey, saver = MethodStepListSaver) {
        mutableStateListOf<MethodStepEntity>().apply {
            val mainSteps = initialMainRoute?.steps ?: existingItem?.steps ?: emptyList()
            mainSteps.sortedBy { it.stepOrder }.forEach { add(it) }
        }
    }

    val alternativeRoutes = rememberSaveable(stateKey, saver = MethodRouteWithStepsListSaver) {
        mutableStateListOf<MethodRouteWithSteps>().apply {
            existingItem?.alternativeMethodRoutes?.forEach { add(it) }
        }
    }

    var activeRouteId by rememberSaveable(stateKey) {
        mutableStateOf(MAIN_METHOD_ROUTE_ID)
    }

    var showAddOrEditRouteDialog by rememberSaveable(stateKey) { mutableStateOf(false) }
    var editingRouteId by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }
    var routeNameInput by rememberSaveable(stateKey) { mutableStateOf("") }
    var routeDescInput by rememberSaveable(stateKey) { mutableStateOf("") }
    var routeNameError by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }
    var copyStepsFromCurrentRoute by rememberSaveable(stateKey) { mutableStateOf(false) }
    var routeIdPendingDelete by rememberSaveable(stateKey) { mutableStateOf<String?>(null) }

    var stepTitleInput by rememberSaveable(stateKey) { mutableStateOf("") }
    var stepDetailsInput by rememberSaveable(stateKey) { mutableStateOf("") }
    var showLegacyStepsExpanded by rememberSaveable(stateKey) { mutableStateOf(false) }

    val attachments = rememberSaveable(stateKey, saver = AttachmentItemListSaver) {
        mutableStateListOf<AttachmentItem>().apply {
            existingItem?.attachments?.forEach { add(it) }
        }
    }

    val sessionCopiedFiles = rememberSaveable(stateKey, saver = StringListSaver) {
        mutableStateListOf<String>()
    }

    var activeBottomPanel by rememberSaveable(stateKey) {
        mutableStateOf(EditorBottomToolPanel.NONE)
    }
    var showMetadataDetailsSection by rememberSaveable(stateKey) {
        mutableStateOf(true)
    }

    var linkNameInput by rememberSaveable(stateKey) { mutableStateOf("") }
    var linkUrlInput by rememberSaveable(stateKey) { mutableStateOf("") }
    var showAddLinkDialog by rememberSaveable(stateKey) { mutableStateOf(false) }
    var showInsertRichLinkDialog by rememberSaveable(stateKey) { mutableStateOf(false) }
    var richLinkLabelInput by rememberSaveable(stateKey) { mutableStateOf("") }
    var richLinkUrlInput by rememberSaveable(stateKey) { mutableStateOf("") }
    var showInsertCodeBlockDialog by rememberSaveable(stateKey) { mutableStateOf(false) }
    var codeBlockTitleInput by rememberSaveable(stateKey) { mutableStateOf("") }
    var codeBlockContentInput by rememberSaveable(stateKey) { mutableStateOf("") }
    var previewingAttachment by remember { mutableStateOf<AttachmentItem?>(null) }

    var showNewFolderDialog by rememberSaveable(stateKey) { mutableStateOf(false) }
    var newFolderName by rememberSaveable(stateKey) { mutableStateOf("") }
    var showFolderDropdown by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    var showCustomFieldRequiredErrors by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var isCopyInProgress by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var draftBannerDismissed by rememberSaveable(stateKey) { mutableStateOf(false) }
    var conflictResult by remember { mutableStateOf<SaveItemResult.Conflict?>(null) }

    var selectedCustomTypeId by rememberSaveable(stateKey) {
        mutableStateOf(existingItem?.entity?.customTypeId ?: initialCustomTypeId)
    }
    var customFieldsJson by rememberSaveable(stateKey, stateSaver = LargeStringSessionSaver) {
        mutableStateOf(existingItem?.entity?.customFieldsJson ?: "{}")
    }
    // FIX 02: Staged CustomTypeEntity created during Smart Import; held in draft until user presses Save
    var pendingNewCustomType by rememberSaveable(stateKey, stateSaver = NullableCustomTypeEntitySaver) {
        mutableStateOf<CustomTypeEntity?>(null)
    }
    val displayCustomTypes = remember(customTypes, pendingNewCustomType) {
        val pending = pendingNewCustomType
        if (pending != null && customTypes.none { it.id == pending.id }) {
            customTypes + pending
        } else {
            customTypes
        }
    }
    val customFieldsMap = remember(customFieldsJson) {
        VaultJsonSerializers.decodeCustomFieldsMap(customFieldsJson)
    }
    val activeCustomType = remember(selectedCustomTypeId, displayCustomTypes, existingItem) {
        if (selectedCustomTypeId.isNullOrBlank()) {
            null
        } else {
            displayCustomTypes.find { it.id == selectedCustomTypeId }
                ?: existingItem?.customType?.takeIf { it.id == selectedCustomTypeId }
        }
    }

    var showSmartImportSheet by rememberSaveable(stateKey) {
        mutableStateOf(initialOpenSmartImport)
    }
    var smartImportInitialMethodMode by rememberSaveable(stateKey) {
        mutableStateOf(MethodImportMode.FULL_REPLACE)
    }
    var smartImportInitialTargetRouteId by rememberSaveable(stateKey) {
        mutableStateOf(MAIN_METHOD_ROUTE_ID)
    }
    var showCustomTypeBuilderDialog by rememberSaveable(stateKey) {
        mutableStateOf(false)
    }
    var editingCustomTypeInEditor by remember {
        mutableStateOf<CustomTypeEntity?>(null)
    }

    var baselineSnapshot by remember(stateKey) {
        mutableStateOf(
            EditorSnapshot(
                title = existingItem?.entity?.title ?: "",
                description = existingItem?.entity?.description ?: "",
                content = existingItem?.entity?.content ?: "",
                categoryId = (existingItem?.categoryEnum ?: initialCategory).id,
                folderId = existingItem?.entity?.folderId ?: initialFolderId,
                ideaStatusId = (existingItem?.statusEnum ?: IdeaStatus.NEW).id,
                platform = existingItem?.entity?.platform ?: "",
                estimatedTime = existingItem?.entity?.estimatedTime ?: "",
                notes = existingItem?.entity?.notes ?: "",
                isPinned = existingItem?.entity?.isPinned ?: false,
                isFavorite = existingItem?.entity?.isFavorite ?: false,
                tags = existingItem?.tags ?: emptyList(),
                richBlocks = existingItem?.richBlocks ?: emptyList(),
                steps = (initialMainRoute?.steps ?: existingItem?.steps ?: emptyList()).sortedBy { it.stepOrder },
                mainRouteName = initialMainRoute?.name ?: DEFAULT_MAIN_METHOD_NAME,
                mainRouteDescription = initialMainRoute?.description ?: "",
                alternativeRoutes = existingItem?.alternativeMethodRoutes ?: emptyList(),
                attachments = existingItem?.attachments ?: emptyList(),
                customTypeId = existingItem?.entity?.customTypeId ?: initialCustomTypeId,
                customFieldsJson = existingItem?.entity?.customFieldsJson ?: "{}",
                pendingCustomType = null
            )
        )
    }

    val currentSnapshot = EditorSnapshot(
        title = title,
        description = description,
        content = effectiveSerializedContent,
        categoryId = category.id,
        folderId = selectedFolderId,
        ideaStatusId = ideaStatus.id,
        platform = platform,
        estimatedTime = estimatedTime,
        notes = notes,
        isPinned = isPinned,
        isFavorite = isFavorite,
        tags = selectedTags.toList(),
        richBlocks = richBlocks.toList(),
        steps = steps.toList(),
        mainRouteName = mainRouteName,
        mainRouteDescription = mainRouteDescription,
        alternativeRoutes = alternativeRoutes.toList(),
        attachments = attachments.toList(),
        customTypeId = selectedCustomTypeId,
        customFieldsJson = customFieldsJson,
        pendingCustomType = pendingNewCustomType?.takeIf { it.id == selectedCustomTypeId }
    )

    val hasUnsavedChanges = currentSnapshot != baselineSnapshot ||
        stepTitleInput.isNotBlank() ||
        stepDetailsInput.isNotBlank() ||
        newTagInput.isNotBlank()

    // Photo Picker for gallery images
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isCopyInProgress = true
            attachmentError = null
            scope.launch {
                val copied = attachmentStorage.copyUriToPersistentStorage(
                    sourceUri = uri,
                    explicitType = AttachmentType.IMAGE
                )
                isCopyInProgress = false
                if (copied != null) {
                    if (AttachmentStorageManager.isDuplicateAttachment(attachments, copied)) {
                        File(copied.uriOrUrl).takeIf { it.exists() }?.delete()
                        attachmentError = "\"${copied.name}\" is already attached to this entry."
                    } else {
                        sessionCopiedFiles.add(copied.uriOrUrl)
                        attachments.add(copied)
                    }
                } else {
                    attachmentError = "Unable to attach image."
                }
            }
        }
    }

    // Camera capture launcher for direct photo capture
    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            isCopyInProgress = true
            attachmentError = null
            scope.launch {
                val savedAtt = withContext(Dispatchers.IO) {
                    runCatching {
                        val fileName = "camera_${System.currentTimeMillis()}.png"
                        val destFile = File(attachmentStorage.attachmentsDir, fileName)
                        FileOutputStream(destFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                        }
                        AttachmentItem(
                            id = UUID.randomUUID().toString(),
                            type = AttachmentType.IMAGE,
                            name = fileName,
                            uriOrUrl = destFile.absolutePath,
                            mimeType = "image/png",
                            sizeBytes = destFile.length()
                        )
                    }.getOrNull()
                }
                isCopyInProgress = false
                if (savedAtt != null) {
                    sessionCopiedFiles.add(savedAtt.uriOrUrl)
                    attachments.add(savedAtt)
                } else {
                    attachmentError = "Unable to save captured photo."
                }
            }
        }
    }

    // Document picker for files / PDFs
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isCopyInProgress = true
            attachmentError = null
            scope.launch {
                val copied = attachmentStorage.copyUriToPersistentStorage(
                    sourceUri = uri,
                    explicitType = null
                )
                isCopyInProgress = false
                if (copied != null) {
                    if (AttachmentStorageManager.isDuplicateAttachment(attachments, copied)) {
                        File(copied.uriOrUrl).takeIf { it.exists() }?.delete()
                        attachmentError = "\"${copied.name}\" is already attached to this entry."
                    } else {
                        sessionCopiedFiles.add(copied.uriOrUrl)
                        attachments.add(copied)
                    }
                } else {
                    attachmentError = "Unable to attach file."
                }
            }
        }
    }

    var draftExplicitlyClearedOrSaved by remember { mutableStateOf(false) }

    fun buildCurrentDraftPayload(): EditorDraftPayload {
        return EditorDraftPayload(
            itemId = itemId,
            isEditingExisting = existingItem != null,
            baseUpdatedAt = baseUpdatedAt.takeIf { it > 0L },
            categoryId = category.id,
            folderId = selectedFolderId,
            ideaStatusId = ideaStatus.id,
            title = title,
            description = description,
            content = if (category == VaultCategory.PROMPT) promptLiteralContent else richEditorState.toSerializedMarkdown(),
            platform = platform,
            estimatedTime = estimatedTime,
            notes = notes,
            isPinned = isPinned,
            isFavorite = isFavorite,
            tags = selectedTags.toList(),
            richBlocks = richBlocks.toList(),
            attachments = attachments.toList(),
            steps = steps.mapIndexed { index, step ->
                step.copy(itemId = itemId, routeId = MAIN_METHOD_ROUTE_ID, stepOrder = index + 1)
            },
            mainRouteName = mainRouteName,
            mainRouteDescription = mainRouteDescription,
            alternativeRoutes = alternativeRoutes.mapIndexed { rIdx, route ->
                route.copy(
                    routeOrder = rIdx + 1,
                    steps = route.steps.mapIndexed { sIdx, step ->
                        step.copy(itemId = itemId, routeId = route.id, stepOrder = sIdx + 1)
                    }
                )
            },
            activeRouteId = activeRouteId,
            pendingStepTitle = stepTitleInput,
            pendingStepDetails = stepDetailsInput,
            pendingNewTag = newTagInput,
            customTypeId = selectedCustomTypeId,
            customFieldsJson = customFieldsJson,
            pendingCustomType = pendingNewCustomType?.takeIf { it.id == selectedCustomTypeId },
            savedAt = System.currentTimeMillis()
        )
    }

    val currentFlushDraftIfDirty by rememberUpdatedState {
        if (hasUnsavedChanges && !isSaving && !draftExplicitlyClearedOrSaved) {
            onAutoSaveDraft(VaultJsonSerializers.encodeEditorDraft(buildCurrentDraftPayload()))
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                currentFlushDraftIfDirty()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentFlushDraftIfDirty()
        }
    }

    // Surface any missing/corrupted temporary session cache failures safely (FIX 2)
    LaunchedEffect(stateKey) {
        val failures = EditorLargeStateSessionBuffer.consumeRecoveryFailures()
        if (failures.isNotEmpty()) {
            val matchingDraft = recoverableDraft?.takeIf {
                (existingItem == null && !it.isEditingExisting) ||
                    (existingItem != null && it.itemId == existingItem.entity.id)
            }
            if (matchingDraft != null) {
                itemId = matchingDraft.itemId
                title = matchingDraft.title
                description = matchingDraft.description
                val restoredCat = VaultCategory.fromId(matchingDraft.categoryId)
                category = restoredCat
                promptLiteralContent = matchingDraft.content
                richEditorState = RichTextEditorState.fromStoredContent(matchingDraft.content)
                selectedFolderId = matchingDraft.folderId
                ideaStatus = IdeaStatus.fromId(matchingDraft.ideaStatusId)
                platform = matchingDraft.platform
                estimatedTime = matchingDraft.estimatedTime
                notes = matchingDraft.notes
                isPinned = matchingDraft.isPinned
                isFavorite = matchingDraft.isFavorite
                if (matchingDraft.baseUpdatedAt != null) {
                    baseUpdatedAt = matchingDraft.baseUpdatedAt
                }
                selectedTags.clear()
                selectedTags.addAll(matchingDraft.tags)
                richBlocks.clear()
                richBlocks.addAll(matchingDraft.richBlocks)
                steps.clear()
                steps.addAll(matchingDraft.steps)
                mainRouteName = matchingDraft.mainRouteName
                mainRouteDescription = matchingDraft.mainRouteDescription
                alternativeRoutes.clear()
                alternativeRoutes.addAll(matchingDraft.alternativeRoutes)
                activeRouteId = matchingDraft.activeRouteId.ifBlank { MAIN_METHOD_ROUTE_ID }
                stepTitleInput = matchingDraft.pendingStepTitle
                stepDetailsInput = matchingDraft.pendingStepDetails
                newTagInput = matchingDraft.pendingNewTag
                attachments.clear()
                attachments.addAll(matchingDraft.attachments)
                pendingNewCustomType = matchingDraft.pendingCustomType
                selectedCustomTypeId = matchingDraft.customTypeId
                customFieldsJson = matchingDraft.customFieldsJson
                draftBannerDismissed = true
                saveErrorMessage = "Temporary session cache was unavailable; recovered unsaved changes from your durable draft."
            } else {
                saveErrorMessage = "Temporary session cache was unavailable or corrupted (${failures.first()}). Showing last saved state — please review before saving."
            }
        }
    }

    // Debounced auto-save draft to DataStore when changes exist
    LaunchedEffect(
        itemId,
        baseUpdatedAt,
        hasUnsavedChanges
    ) {
        snapshotFlow { buildCurrentDraftPayload() }
            .debounce(650L)
            .collectLatest { payload ->
                if (hasUnsavedChanges && !isSaving && !draftExplicitlyClearedOrSaved) {
                    draftBannerDismissed = true
                    onAutoSaveDraft(VaultJsonSerializers.encodeEditorDraft(payload))
                }
            }
    }

    val handleExitRequest = {
        if (!isSaving) {
            if (hasUnsavedChanges) {
                showDiscardConfirmDialog = true
            } else {
                onCancel()
            }
        }
    }

    BackHandler {
        if (activeBottomPanel != EditorBottomToolPanel.NONE) {
            activeBottomPanel = EditorBottomToolPanel.NONE
        } else {
            handleExitRequest()
        }
    }

    fun buildEffectiveStepsForSave(): List<MethodStepEntity> {
        val currentSteps = steps.toMutableList()
        if (activeRouteId == MAIN_METHOD_ROUTE_ID && (stepTitleInput.isNotBlank() || stepDetailsInput.isNotBlank())) {
            currentSteps.add(
                MethodStepEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = itemId,
                    routeId = MAIN_METHOD_ROUTE_ID,
                    stepOrder = currentSteps.size + 1,
                    title = stepTitleInput.trim().ifBlank { "Step ${currentSteps.size + 1}" },
                    description = stepDetailsInput
                )
            )
        }
        return currentSteps
            .filter { it.title.isNotBlank() || it.description.isNotBlank() || it.notes.isNotBlank() }
            .mapIndexed { idx, step ->
                step.copy(
                    itemId = itemId,
                    routeId = MAIN_METHOD_ROUTE_ID,
                    stepOrder = idx + 1,
                    title = step.title.trim().ifBlank { "Step ${idx + 1}" },
                    description = step.description,
                    notes = step.notes
                )
            }
    }

    fun buildEffectiveAlternativeRoutesForSave(): List<MethodRouteWithSteps> {
        return alternativeRoutes.mapIndexed { rIdx, route ->
            val routeSteps = route.steps.toMutableList()
            if (activeRouteId == route.id && (stepTitleInput.isNotBlank() || stepDetailsInput.isNotBlank())) {
                routeSteps.add(
                    MethodStepEntity(
                        id = UUID.randomUUID().toString(),
                        itemId = itemId,
                        routeId = route.id,
                        stepOrder = routeSteps.size + 1,
                        title = stepTitleInput.trim().ifBlank { "Step ${routeSteps.size + 1}" },
                        description = stepDetailsInput
                    )
                )
            }
            val cleanedSteps = routeSteps
                .filter { it.title.isNotBlank() || it.description.isNotBlank() || it.notes.isNotBlank() }
                .mapIndexed { idx, step ->
                    step.copy(
                        itemId = itemId,
                        routeId = route.id,
                        stepOrder = idx + 1,
                        title = step.title.trim().ifBlank { "Step ${idx + 1}" },
                        description = step.description,
                        notes = step.notes
                    )
                }
            route.copy(
                itemId = itemId,
                routeOrder = rIdx + 1,
                steps = cleanedSteps
            )
        }
    }

    fun buildEffectiveTagsForSave(): List<String> {
        val currentTags = selectedTags.toMutableList()
        val pendingTag = newTagInput.trim().removePrefix("#").trim()
        if (pendingTag.isNotBlank() && !currentTags.contains(pendingTag)) {
            currentTags.add(pendingTag)
        }
        return currentTags
    }

    fun executeSave(forceOverwrite: Boolean) {
        if (isSaving || isCopyInProgress) return
        if (title.isBlank()) {
            titleError = true
            if (hasUnsavedChanges) {
                onAutoSaveDraft(VaultJsonSerializers.encodeEditorDraft(buildCurrentDraftPayload()))
            }
            return
        }
        // Audit P1-11 & P2-14: Validate Custom Type required fields and field value formats before saving
        if (activeCustomType != null) {
            val defs = VaultJsonSerializers.decodeCustomFieldDefinitions(activeCustomType.fieldsJson)
            val customFieldErrors = SmartImportExportEngine.validateAllCustomFields(
                definitions = defs,
                valuesMap = customFieldsMap,
                checkRequired = true
            )
            if (customFieldErrors.isNotEmpty()) {
                showCustomFieldRequiredErrors = true
                saveErrorMessage = customFieldErrors.values.first()
                if (hasUnsavedChanges) {
                    onAutoSaveDraft(VaultJsonSerializers.encodeEditorDraft(buildCurrentDraftPayload()))
                }
                return
            }
        }
        showCustomFieldRequiredErrors = false
        isSaving = true
        saveErrorMessage = null

        val effectiveSteps = buildEffectiveStepsForSave()
        val effectiveAltRoutes = buildEffectiveAlternativeRoutesForSave()
        val effectiveTags = buildEffectiveTagsForSave()
        val finalContent = if (category == VaultCategory.PROMPT) {
            promptLiteralContent
        } else {
            richEditorState.toSerializedMarkdown()
        }
        val deduplicatedAttachments = AttachmentStorageManager.deduplicateAttachments(attachments)

        val now = System.currentTimeMillis()
        val entityToSave = VaultItemEntity(
            id = itemId,
            title = title.trim(),
            description = description.trim(),
            content = finalContent,
            category = category.id,
            folderId = selectedFolderId,
            tagsJson = VaultJsonSerializers.encodeStringList(effectiveTags),
            richBlocksJson = VaultJsonSerializers.encodeRichBlocks(richBlocks),
            attachmentsJson = VaultJsonSerializers.encodeAttachments(deduplicatedAttachments),
            ideaStatus = ideaStatus.id,
            platform = platform.trim(),
            estimatedTime = estimatedTime.trim(),
            notes = notes.trim(),
            isFavorite = isFavorite,
            isPinned = isPinned,
            isArchived = existingItem?.entity?.isArchived ?: false,
            isTrashed = existingItem?.entity?.isTrashed ?: false,
            customTypeId = selectedCustomTypeId,
            customFieldsJson = customFieldsJson,
            createdAt = existingItem?.entity?.createdAt ?: now,
            updatedAt = now
        )

        val stagedCustomTypeForSave = pendingNewCustomType?.takeIf {
            it.id == selectedCustomTypeId && customTypes.none { existingCt -> existingCt.id == it.id }
        }

        scope.launch {
            val result = if (onSaveItemWithRoutesAwaitable != null) {
                onSaveItemWithRoutesAwaitable(
                    entityToSave,
                    effectiveSteps,
                    baseUpdatedAt.takeIf { it > 0L },
                    existingItem != null,
                    forceOverwrite,
                    mainRouteName,
                    mainRouteDescription,
                    effectiveAltRoutes,
                    stagedCustomTypeForSave
                )
            } else {
                onSaveItemAwaitable(
                    entityToSave,
                    effectiveSteps,
                    baseUpdatedAt.takeIf { it > 0L },
                    existingItem != null,
                    forceOverwrite
                )
            }
            isSaving = false
            when (result) {
                is SaveItemResult.Success -> {
                    draftExplicitlyClearedOrSaved = true
                    pendingNewCustomType = null
                    sessionCopiedFiles.clear()
                    EditorLargeStateSessionBuffer.clearSession(stateKey, context)
                    conflictResult = null
                    onSaveSuccess(result.itemId)
                }
                is SaveItemResult.Conflict -> {
                    conflictResult = result
                    onAutoSaveDraft(VaultJsonSerializers.encodeEditorDraft(buildCurrentDraftPayload()))
                }
                is SaveItemResult.ValidationFailure -> {
                    titleError = title.isBlank()
                    saveErrorMessage = result.reason
                    onAutoSaveDraft(VaultJsonSerializers.encodeEditorDraft(buildCurrentDraftPayload()))
                }
                is SaveItemResult.StorageFailure -> {
                    saveErrorMessage = result.reason
                    onAutoSaveDraft(VaultJsonSerializers.encodeEditorDraft(buildCurrentDraftPayload()))
                }
            }
        }
    }

    val categoryMeta = remember(category) { getCategoryEditorMetadata(category) }
    val displayTimestamp = remember(existingItem) {
        formatVaultDateTime(existingItem?.entity?.updatedAt ?: System.currentTimeMillis())
    }
    val charCount = if (category == VaultCategory.PROMPT) {
        promptLiteralContent.length
    } else {
        richEditorState.plainText.length
    }
    val canUndo = if (category == VaultCategory.PROMPT) promptUndoStack.isNotEmpty() else richEditorState.canUndo
    val canRedo = if (category == VaultCategory.PROMPT) promptRedoStack.isNotEmpty() else richEditorState.canRedo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    ) {
        // V2 Document-First Top Bar: Back (<) | Undo (↶) | Redo (↷) | Done/Save (✓)
        Row(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = handleExitRequest,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .testTag("top_bar_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = vaultColors.primaryText
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (category == VaultCategory.PROMPT) {
                            if (promptUndoStack.isNotEmpty()) {
                                val prev = promptUndoStack.last()
                                promptUndoStack = promptUndoStack.dropLast(1)
                                promptRedoStack = (promptRedoStack + promptLiteralContent).takeLast(40)
                                promptLiteralContent = prev
                                lastPromptUndoPushMs = 0L
                            }
                        } else {
                            richEditorState = RichTextEditorController.undo(richEditorState)
                        }
                    },
                    enabled = canUndo,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) vaultColors.primaryText else vaultColors.secondaryText.copy(alpha = 0.38f)
                    )
                }

                IconButton(
                    onClick = {
                        if (category == VaultCategory.PROMPT) {
                            if (promptRedoStack.isNotEmpty()) {
                                val next = promptRedoStack.last()
                                promptRedoStack = promptRedoStack.dropLast(1)
                                promptUndoStack = (promptUndoStack + promptLiteralContent).takeLast(40)
                                promptLiteralContent = next
                                lastPromptUndoPushMs = 0L
                            }
                        } else {
                            richEditorState = RichTextEditorController.redo(richEditorState)
                        }
                    },
                    enabled = canRedo,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) vaultColors.primaryText else vaultColors.secondaryText.copy(alpha = 0.38f)
                    )
                }

                val canSave = !isSaving && !isCopyInProgress
                IconButton(
                    onClick = { executeSave(forceOverwrite = false) },
                    enabled = canSave,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (canSave) vaultColors.actionYellow else vaultColors.secondarySurface)
                        .testTag("editor_save_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = vaultColors.darkInk,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save",
                            tint = vaultColors.darkInk
                        )
                    }
                }
            }
        }

        // Main Document Scroll Area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .testTag("editor_scroll_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 4.dp,
                end = 20.dp,
                bottom = 48.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (saveErrorMessage != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(vaultColors.coral.copy(alpha = 0.14f))
                            .border(1.dp, vaultColors.coral, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                            .testTag("editor_save_error_banner"),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WarningAmber,
                            contentDescription = null,
                            tint = vaultColors.coral,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = saveErrorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.primaryText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Recoverable Draft Banner (P1-01)
            if (recoverableDraft != null &&
                !draftBannerDismissed &&
                ((existingItem == null && !recoverableDraft.isEditingExisting) ||
                    (existingItem != null && recoverableDraft.itemId == existingItem.entity.id))
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(vaultColors.yellow.copy(alpha = 0.14f))
                            .border(1.dp, vaultColors.yellow.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                            .padding(14.dp)
                            .testTag("editor_draft_recovery_banner"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Unsaved draft available",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (vaultColors.isDark) vaultColors.yellow else vaultColors.primaryText
                            )
                            Text(
                                text = recoverableDraft.title.ifBlank { "Untitled draft" },
                                style = MaterialTheme.typography.bodySmall,
                                color = vaultColors.secondaryText
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    itemId = recoverableDraft.itemId
                                    title = recoverableDraft.title
                                    description = recoverableDraft.description
                                    val restoredCat = VaultCategory.fromId(recoverableDraft.categoryId)
                                    category = restoredCat
                                    promptLiteralContent = recoverableDraft.content
                                    richEditorState = RichTextEditorState.fromStoredContent(recoverableDraft.content)
                                    selectedFolderId = recoverableDraft.folderId
                                    ideaStatus = IdeaStatus.fromId(recoverableDraft.ideaStatusId)
                                    platform = recoverableDraft.platform
                                    estimatedTime = recoverableDraft.estimatedTime
                                    notes = recoverableDraft.notes
                                    isPinned = recoverableDraft.isPinned
                                    isFavorite = recoverableDraft.isFavorite
                                    if (recoverableDraft.baseUpdatedAt != null) {
                                        baseUpdatedAt = recoverableDraft.baseUpdatedAt
                                    }
                                    selectedTags.clear()
                                    selectedTags.addAll(recoverableDraft.tags)
                                    richBlocks.clear()
                                    richBlocks.addAll(recoverableDraft.richBlocks)
                                    steps.clear()
                                    steps.addAll(recoverableDraft.steps)
                                    mainRouteName = recoverableDraft.mainRouteName
                                    mainRouteDescription = recoverableDraft.mainRouteDescription
                                    alternativeRoutes.clear()
                                    alternativeRoutes.addAll(recoverableDraft.alternativeRoutes)
                                    activeRouteId = recoverableDraft.activeRouteId.ifBlank { MAIN_METHOD_ROUTE_ID }
                                    stepTitleInput = recoverableDraft.pendingStepTitle
                                    stepDetailsInput = recoverableDraft.pendingStepDetails
                                    newTagInput = recoverableDraft.pendingNewTag
                                    attachments.clear()
                                    attachments.addAll(recoverableDraft.attachments)
                                    pendingNewCustomType = recoverableDraft.pendingCustomType
                                    selectedCustomTypeId = recoverableDraft.customTypeId
                                    customFieldsJson = recoverableDraft.customFieldsJson
                                    draftBannerDismissed = true
                                    draftExplicitlyClearedOrSaved = false
                                },
                                modifier = Modifier.testTag("restore_draft_button")
                            ) {
                                Text(
                                    text = "Restore",
                                    color = if (vaultColors.isDark) vaultColors.yellow else vaultColors.primaryText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = {
                                    draftBannerDismissed = true
                                    draftExplicitlyClearedOrSaved = true
                                    onDiscardDraft()
                                },
                                modifier = Modifier.testTag("dismiss_draft_button")
                            ) {
                                Text("Dismiss", color = vaultColors.secondaryText)
                            }
                        }
                    }
                }
            }

            // 1. Borderless Document Title + Subtle Metadata Line (Date | Characters | Folder)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val titleBringIntoViewRequester = remember { BringIntoViewRequester() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(titleBringIntoViewRequester),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (title.isEmpty()) {
                            Text(
                                text = "Title",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp
                                ),
                                color = if (titleError) vaultColors.coral else vaultColors.secondaryText.copy(alpha = 0.55f)
                            )
                        }
                        BasicTextField(
                            value = title,
                            onValueChange = {
                                // Keep document titles human-readable without introducing hard newlines.
                                title = it.replace('\n', ' ').replace('\r', ' ')
                                if (title.isNotBlank()) titleError = false
                            },
                            singleLine = false,
                            maxLines = 3,
                            textStyle = MaterialTheme.typography.headlineMedium.copy(
                                color = vaultColors.primaryText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            ),
                            cursorBrush = SolidColor(vaultColors.actionYellow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusEvent { event ->
                                    if (event.isFocused) {
                                        scope.launch { titleBringIntoViewRequester.bringIntoView() }
                                    }
                                }
                                .testTag("editor_title_input")
                        )
                    }

                    if (titleError) {
                        Text(
                            text = "Please enter a title before saving.",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.coral
                        )
                    }

                    // Keep metadata and actions organized cleanly: category and folder labels
                    // never shrink to a one-character-wide vertical column on narrow screens.
                    val currentFolder = folders.find { it.id == selectedFolderId }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_metadata_line"),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Folder gets its own full-width row to avoid cramped text on 320dp screens
                        // and at 200% Android font scale while remaining visually quiet.
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(vaultColors.secondarySurface.copy(alpha = 0.65f))
                                    .border(1.dp, vaultColors.border, RoundedCornerShape(12.dp))
                                    .clickable { showFolderDropdown = true }
                                    .padding(horizontal = 12.dp)
                                    .testTag("editor_folder_selector"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = vaultColors.secondaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = currentFolder?.name ?: "Uncategorized",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = vaultColors.primaryText
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select folder",
                                    tint = vaultColors.secondaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showFolderDropdown,
                                onDismissRequest = { showFolderDropdown = false },
                                modifier = Modifier.background(vaultColors.elevatedSurface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Uncategorized", color = vaultColors.primaryText) },
                                    onClick = {
                                        selectedFolderId = null
                                        showFolderDropdown = false
                                    }
                                )
                                folders.forEach { folder ->
                                    DropdownMenuItem(
                                        text = { Text(folder.name, color = vaultColors.primaryText) },
                                        onClick = {
                                            selectedFolderId = folder.id
                                            showFolderDropdown = false
                                        }
                                    )
                                }
                                HorizontalDivider(color = vaultColors.border)
                                DropdownMenuItem(
                                    text = { Text("+ New Folder", color = vaultColors.lime, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showFolderDropdown = false
                                        showNewFolderDialog = true
                                    },
                                    modifier = Modifier.testTag("editor_new_folder_button")
                                )
                            }
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "$displayTimestamp  •  $charCount characters",
                                style = MaterialTheme.typography.labelSmall,
                                color = vaultColors.secondaryText,
                                maxLines = 2,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(vertical = 2.dp)
                            )
                            // Do not call a favourite "Saved": document saving and
                            // bookmarking are different actions.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ToggleChipButton(
                                    label = if (isPinned) "Pinned" else "Pin",
                                    icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    active = isPinned,
                                    activeColor = vaultColors.actionYellow,
                                    onClick = { isPinned = !isPinned },
                                    testTag = "editor_toggle_pin"
                                )
                                ToggleChipButton(
                                    label = "Favourite",
                                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    active = isFavorite,
                                    activeColor = vaultColors.pink,
                                    onClick = { isFavorite = !isFavorite },
                                    testTag = "editor_toggle_favorite"
                                )
                            }
                        }
                    }
                }
            }

            // 2. Compact Category Selector Row + Custom Types + Quiet Utility Actions
            // Visual pills stay compact (30dp-34dp) while outer interactive boxes provide >= 48dp touch targets
            item {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    val categoriesToShow = remember(existingItem) {
                        val base = VaultCategory.v2PrimaryCategories.toMutableList()
                        val existingCat = existingItem?.categoryEnum
                        if (existingCat != null && existingCat !in base) {
                            base.add(existingCat)
                        }
                        base
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categoriesToShow) { cat ->
                            val isSelected = category == cat && selectedCustomTypeId == null
                            val catColor = cat.themeCardColor()
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .widthIn(min = 48.dp)
                                    .testTag("editor_category_${cat.name.lowercase()}")
                                    .semantics { role = Role.RadioButton }
                                    .clickable {
                                        selectedCustomTypeId = null
                                        if (category != cat) {
                                            if (category == VaultCategory.PROMPT) {
                                                richEditorState = RichTextEditorState.fromStoredContent(promptLiteralContent)
                                            } else if (cat == VaultCategory.PROMPT) {
                                                promptLiteralContent = richEditorState.plainText
                                            }
                                            category = cat
                                            if (cat == VaultCategory.METHOD && steps.isEmpty()) {
                                                steps.add(
                                                    MethodStepEntity(
                                                        id = UUID.randomUUID().toString(),
                                                        itemId = itemId,
                                                        stepOrder = 1,
                                                        title = "",
                                                        description = ""
                                                    )
                                                )
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(if (isSelected) catColor else vaultColors.surface)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color.Transparent else vaultColors.border,
                                            shape = RoundedCornerShape(17.dp)
                                        )
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cat.singularLabel,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) vaultColors.darkInk else vaultColors.primaryText
                                    )
                                }
                            }
                        }

                        items(displayCustomTypes, key = { "ct_${it.id}" }) { ct ->
                            val isSelected = selectedCustomTypeId == ct.id
                            val ctAccent = parseHexColorSafe(ct.colorHex, vaultColors.lavender)
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .widthIn(min = 48.dp)
                                    .testTag("editor_custom_type_${ct.id}")
                                    .semantics { role = Role.RadioButton }
                                    .clickable {
                                        selectedCustomTypeId = ct.id
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(if (isSelected) ctAccent else vaultColors.surface)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) Color.Transparent else vaultColors.border,
                                            shape = RoundedCornerShape(17.dp)
                                        )
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ct.name,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) accessibleTextColorForBackground(ctAccent) else vaultColors.primaryText
                                    )
                                }
                            }
                        }

                        item(key = "create_custom_type_chip") {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .widthIn(min = 48.dp)
                                    .testTag("editor_create_custom_type_chip")
                                    .clickable {
                                        editingCustomTypeInEditor = null
                                        showCustomTypeBuilderDialog = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(vaultColors.secondarySurface)
                                        .border(1.dp, vaultColors.border, RoundedCornerShape(17.dp))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+ Custom Type",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = vaultColors.secondaryText
                                    )
                                }
                            }
                        }

                        item(key = "smart_import_chip") {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .widthIn(min = 48.dp)
                                    .testTag("editor_smart_import_button")
                                    .clickable {
                                        smartImportInitialMethodMode = MethodImportMode.FULL_REPLACE
                                        smartImportInitialTargetRouteId = activeRouteId
                                        showSmartImportSheet = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(vaultColors.secondarySurface)
                                        .border(1.dp, vaultColors.border, RoundedCornerShape(17.dp))
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Paste & Convert",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = vaultColors.primaryText
                                    )
                                }
                            }
                        }
                    }

                    // Idea Status Pills for IDEA entries
                    if (category == VaultCategory.IDEA && selectedCustomTypeId == null) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(IdeaStatus.entries) { status ->
                                val selected = ideaStatus == status
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .widthIn(min = 48.dp)
                                        .clickable { ideaStatus = status }
                                        .testTag("editor_idea_status_${status.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(30.dp)
                                            .clip(RoundedCornerShape(15.dp))
                                            .background(
                                                if (selected) status.badgeColor else vaultColors.secondarySurface
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (selected) Color.Transparent else vaultColors.border,
                                                shape = RoundedCornerShape(15.dp)
                                            )
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = status.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (selected) vaultColors.darkInk else vaultColors.secondaryText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2b. Custom Type Dynamic Fields Section (when a Custom Type is selected)
            if (activeCustomType != null) {
                item(key = "editor_custom_fields_${activeCustomType.id}") {
                    CustomFieldsEditorSection(
                        customType = activeCustomType,
                        customFieldsMap = customFieldsMap,
                        onFieldValueChange = { fieldId, value ->
                            val updated = customFieldsMap.toMutableMap()
                            updated[fieldId] = value
                            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(updated)
                        },
                        onEditCustomTypeDefinition = {
                            editingCustomTypeInEditor = activeCustomType
                            showCustomTypeBuilderDialog = true
                        },
                        onCopyCodeSnippet = { rawCode ->
                            copyTextToSystemClipboard(context, "Code", rawCode)
                        },
                        showRequiredErrors = showCustomFieldRequiredErrors
                    )
                }
            }

            // 3. Category-Specific Main Writing Canvas
            item {
                val toolbarAccent = category.themeCardColor()
                val activeInline = richEditorState.effectiveActiveStyles()

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (activeInline.isNotEmpty() && category != VaultCategory.PROMPT) {
                        Text(
                            text = activeInline.joinToString(" • ") {
                                it.name.lowercase().replaceFirstChar { c -> c.uppercase() }
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = toolbarAccent,
                            modifier = Modifier.testTag("editor_active_format_indicator")
                        )
                    }

                    if (category == VaultCategory.PROMPT) {
                        // PROMPT: Exact literal multiline prompt canvas (never strips **, _, {TOPIC}, JSON, or code fences)
                        PromptLiteralCanvas(
                            value = promptLiteralContent,
                            onValueChange = { updated ->
                                if (updated != promptLiteralContent) {
                                    val nowMs = System.currentTimeMillis()
                                    val elapsed = if (lastPromptUndoPushMs == 0L) Long.MAX_VALUE else (nowMs - lastPromptUndoPushMs).coerceAtLeast(0L)
                                    if (
                                        shouldPushPromptUndoCheckpoint(
                                            previousText = promptLiteralContent,
                                            nextText = updated,
                                            undoStackEmpty = promptUndoStack.isEmpty(),
                                            elapsedMsSinceLastPush = elapsed
                                        )
                                    ) {
                                        promptUndoStack = (promptUndoStack + promptLiteralContent).takeLast(40)
                                        lastPromptUndoPushMs = nowMs
                                    }
                                    promptRedoStack = emptyList()
                                    promptLiteralContent = updated
                                }
                            },
                            placeholder = categoryMeta.bodyPlaceholder,
                            testTag = "editor_content_input"
                        )
                    } else {
                        // IDEA, NOTE (KNOWLEDGE), METHOD: Continuous Document-First Rich-Text Canvas
                        DocumentRichTextCanvas(
                            state = richEditorState,
                            onStateChange = { updated ->
                                richEditorState = updated
                            },
                            placeholder = categoryMeta.bodyPlaceholder,
                            accentColor = toolbarAccent,
                            minHeight = if (category == VaultCategory.METHOD) 96.dp else 160.dp,
                            testTag = "editor_content_input"
                        )
                    }
                }
            }

            // 4. Interactive Checklist / Code / Divider Rich Blocks inside Document Flow
            if (richBlocks.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_rich_blocks_section"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        richBlocks.forEachIndexed { idx, block ->
                            when (block.type) {
                                RichBlockType.CHECKLIST -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(vaultColors.surface)
                                            .border(1.dp, vaultColors.border, RoundedCornerShape(12.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                            .testTag("editor_checklist_row_$idx"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = block.checked,
                                            onCheckedChange = { isChecked ->
                                                richBlocks[idx] = block.copy(checked = isChecked)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = vaultColors.actionYellow,
                                                checkmarkColor = vaultColors.darkInk,
                                                uncheckedColor = vaultColors.secondaryText
                                            ),
                                            modifier = Modifier.testTag("editor_checklist_checkbox_$idx")
                                        )
                                        BasicTextField(
                                            value = block.title,
                                            onValueChange = { newText ->
                                                richBlocks[idx] = block.copy(title = newText)
                                            },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                                color = if (block.checked) vaultColors.secondaryText else vaultColors.primaryText,
                                                textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            cursorBrush = SolidColor(vaultColors.actionYellow),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("editor_checklist_input_$idx"),
                                            decorationBox = { innerTextField ->
                                                Box(contentAlignment = Alignment.CenterStart) {
                                                    if (block.title.isEmpty()) {
                                                        Text(
                                                            text = "Checklist item...",
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = vaultColors.secondaryText.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                        IconButton(
                                            onClick = { richBlocks.removeAt(idx) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("editor_checklist_remove_$idx")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove checklist item",
                                                tint = vaultColors.secondaryText,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                RichBlockType.CODE_BLOCK -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(vaultColors.secondarySurface)
                                            .border(1.dp, vaultColors.border, RoundedCornerShape(14.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = block.title.ifBlank { "CODE" },
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = vaultColors.lavender
                                            )
                                            IconButton(
                                                onClick = { richBlocks.removeAt(idx) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("editor_code_block_remove_$idx")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove code block",
                                                    tint = vaultColors.secondaryText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = block.content,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = 19.sp
                                            ),
                                            color = vaultColors.primaryText
                                        )
                                    }
                                }

                                RichBlockType.DIVIDER -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        HorizontalDivider(
                                            modifier = Modifier.weight(1f),
                                            color = vaultColors.border
                                        )
                                        IconButton(
                                            onClick = { richBlocks.removeAt(idx) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("editor_divider_remove_$idx")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove divider",
                                                tint = vaultColors.secondaryText,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }

            // 5. Method Steps Builder (Primary for METHOD; Collapsible for non-Method items with existing steps)
            if (category == VaultCategory.METHOD) {
                val activeAltRouteIndex = alternativeRoutes.indexOfFirst { it.id == activeRouteId }
                val activeRouteSteps = if (activeRouteId == MAIN_METHOD_ROUTE_ID || activeAltRouteIndex < 0) {
                    steps.toList()
                } else {
                    alternativeRoutes[activeAltRouteIndex].steps
                }
                val activeRouteTitle = if (activeRouteId == MAIN_METHOD_ROUTE_ID || activeAltRouteIndex < 0) {
                    mainRouteName
                } else {
                    alternativeRoutes[activeAltRouteIndex].name
                }
                val activeRouteDesc = if (activeRouteId == MAIN_METHOD_ROUTE_ID || activeAltRouteIndex < 0) {
                    mainRouteDescription
                } else {
                    alternativeRoutes[activeAltRouteIndex].description
                }

                fun updateActiveRouteSteps(updated: List<MethodStepEntity>) {
                    val effectiveRouteId = if (activeRouteId == MAIN_METHOD_ROUTE_ID || activeAltRouteIndex < 0) {
                        MAIN_METHOD_ROUTE_ID
                    } else {
                        activeRouteId
                    }
                    val normalized = updated.mapIndexed { idx, s ->
                        s.copy(
                            itemId = itemId,
                            routeId = effectiveRouteId,
                            stepOrder = idx + 1
                        )
                    }
                    if (effectiveRouteId == MAIN_METHOD_ROUTE_ID) {
                        steps.clear()
                        steps.addAll(normalized)
                    } else if (activeAltRouteIndex >= 0) {
                        alternativeRoutes[activeAltRouteIndex] = alternativeRoutes[activeAltRouteIndex].copy(
                            steps = normalized
                        )
                    }
                }

                item(key = "method_steps_header") {
                    MethodStepsHeaderSection(
                        sectionTitle = "METHOD STEPS (${activeRouteSteps.size})",
                        activeRouteId = if (activeRouteId == MAIN_METHOD_ROUTE_ID || activeAltRouteIndex < 0) MAIN_METHOD_ROUTE_ID else activeRouteId,
                        mainRouteName = mainRouteName,
                        mainRouteStepCount = steps.size,
                        activeRouteName = activeRouteTitle,
                        activeRouteDescription = activeRouteDesc,
                        alternativeRoutes = alternativeRoutes,
                        onSelectRoute = { selectedId ->
                            if (selectedId != activeRouteId && (stepTitleInput.isNotBlank() || stepDetailsInput.isNotBlank())) {
                                val nextStep = MethodStepEntity(
                                    id = UUID.randomUUID().toString(),
                                    itemId = itemId,
                                    routeId = if (activeRouteId == MAIN_METHOD_ROUTE_ID || activeAltRouteIndex < 0) MAIN_METHOD_ROUTE_ID else activeRouteId,
                                    stepOrder = activeRouteSteps.size + 1,
                                    title = stepTitleInput.trim().ifBlank { "Step ${activeRouteSteps.size + 1}" },
                                    description = stepDetailsInput
                                )
                                updateActiveRouteSteps(activeRouteSteps + nextStep)
                                stepTitleInput = ""
                                stepDetailsInput = ""
                            }
                            activeRouteId = selectedId
                        },
                        onRequestAddAlternativeRoute = {
                            editingRouteId = null
                            routeNameInput = ""
                            routeDescInput = ""
                            routeNameError = null
                            copyStepsFromCurrentRoute = false
                            showAddOrEditRouteDialog = true
                        },
                        onRequestEditActiveRoute = {
                            if (activeRouteId != MAIN_METHOD_ROUTE_ID && activeAltRouteIndex >= 0) {
                                val target = alternativeRoutes[activeAltRouteIndex]
                                editingRouteId = target.id
                                routeNameInput = target.name
                                routeDescInput = target.description
                                routeNameError = null
                                copyStepsFromCurrentRoute = false
                                showAddOrEditRouteDialog = true
                            }
                        },
                        onDuplicateActiveRoute = {
                            val newRouteId = "route_${UUID.randomUUID()}"
                            val sourceName = activeRouteTitle
                            val copiedSteps = activeRouteSteps.mapIndexed { idx, s ->
                                s.copy(
                                    id = UUID.randomUUID().toString(),
                                    itemId = itemId,
                                    routeId = newRouteId,
                                    stepOrder = idx + 1
                                )
                            }
                            val newRoute = MethodRouteWithSteps(
                                id = newRouteId,
                                itemId = itemId,
                                name = "$sourceName (Copy)",
                                description = activeRouteDesc,
                                routeOrder = alternativeRoutes.size + 1,
                                isMain = false,
                                steps = copiedSteps
                            )
                            alternativeRoutes.add(newRoute)
                            activeRouteId = newRouteId
                        },
                        onRequestDeleteActiveRoute = {
                            if (activeRouteId != MAIN_METHOD_ROUTE_ID && activeAltRouteIndex >= 0) {
                                routeIdPendingDelete = activeRouteId
                            }
                        },
                        onRequestImportMethod = {
                            smartImportInitialMethodMode = MethodImportMode.FULL_REPLACE
                            smartImportInitialTargetRouteId = if (activeRouteId == MAIN_METHOD_ROUTE_ID || activeAltRouteIndex < 0) MAIN_METHOD_ROUTE_ID else activeRouteId
                            showSmartImportSheet = true
                        },
                        onRequestBulkAddSteps = {
                            smartImportInitialMethodMode = MethodImportMode.BULK_APPEND_STEPS
                            smartImportInitialTargetRouteId = if (activeRouteId == MAIN_METHOD_ROUTE_ID || activeAltRouteIndex < 0) MAIN_METHOD_ROUTE_ID else activeRouteId
                            showSmartImportSheet = true
                        },
                        showAlternativeControls = true
                    )
                }

                itemsIndexed(
                    items = activeRouteSteps,
                    key = { idx, step -> "step_${activeRouteId}_${step.id}_$idx" }
                ) { idx, step ->
                    MethodStepEditorCard(
                        idx = idx,
                        step = step,
                        activeSteps = activeRouteSteps,
                        onUpdateActiveSteps = ::updateActiveRouteSteps
                    )
                }

                item(key = "method_steps_add_card") {
                    MethodAddStepEditorCard(
                        activeRouteName = activeRouteTitle,
                        stepTitleInput = stepTitleInput,
                        onStepTitleChange = { stepTitleInput = it },
                        stepDetailsInput = stepDetailsInput,
                        onStepDetailsChange = { stepDetailsInput = it },
                        onAddStep = {
                            if (stepTitleInput.isNotBlank() || stepDetailsInput.isNotBlank()) {
                                val targetRouteId = if (activeRouteId == MAIN_METHOD_ROUTE_ID || activeAltRouteIndex < 0) {
                                    MAIN_METHOD_ROUTE_ID
                                } else {
                                    activeRouteId
                                }
                                val nextStep = MethodStepEntity(
                                    id = UUID.randomUUID().toString(),
                                    itemId = itemId,
                                    routeId = targetRouteId,
                                    stepOrder = activeRouteSteps.size + 1,
                                    title = stepTitleInput.trim().ifBlank { "Step ${activeRouteSteps.size + 1}" },
                                    description = stepDetailsInput
                                )
                                updateActiveRouteSteps(activeRouteSteps + nextStep)
                                stepTitleInput = ""
                                stepDetailsInput = ""
                            }
                        }
                    )
                }
            } else if (steps.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(vaultColors.surface)
                            .border(1.dp, vaultColors.border, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                            .testTag("editor_legacy_steps_section"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLegacyStepsExpanded = !showLegacyStepsExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ATTACHED STEPS (${steps.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = vaultColors.primaryText
                                )
                                Text(
                                    text = "Preserved checklist steps attached to this entry",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vaultColors.secondaryText
                                )
                            }
                            Icon(
                                imageVector = if (showLegacyStepsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (showLegacyStepsExpanded) "Collapse steps" else "Expand steps",
                                tint = vaultColors.secondaryText
                            )
                        }
                        AnimatedVisibility(visible = showLegacyStepsExpanded) {
                            MethodStepsEditorSection(
                                sectionTitle = null,
                                itemId = itemId,
                                activeRouteId = MAIN_METHOD_ROUTE_ID,
                                mainRouteName = mainRouteName,
                                mainRouteStepCount = steps.size,
                                activeRouteName = mainRouteName,
                                activeRouteDescription = mainRouteDescription,
                                alternativeRoutes = emptyList(),
                                activeSteps = steps.toList(),
                                onSelectRoute = {},
                                onRequestAddAlternativeRoute = {},
                                onRequestEditActiveRoute = {},
                                onDuplicateActiveRoute = {},
                                onRequestDeleteActiveRoute = {},
                                onUpdateActiveSteps = { updated ->
                                    steps.clear()
                                    steps.addAll(updated)
                                },
                                stepTitleInput = stepTitleInput,
                                onStepTitleChange = { stepTitleInput = it },
                                stepDetailsInput = stepDetailsInput,
                                onStepDetailsChange = { stepDetailsInput = it },
                                onAddStep = {
                                    if (stepTitleInput.isNotBlank() || stepDetailsInput.isNotBlank()) {
                                        steps.add(
                                            MethodStepEntity(
                                                id = UUID.randomUUID().toString(),
                                                itemId = itemId,
                                                routeId = MAIN_METHOD_ROUTE_ID,
                                                stepOrder = steps.size + 1,
                                                title = stepTitleInput.trim().ifBlank { "Step ${steps.size + 1}" },
                                                description = stepDetailsInput
                                            )
                                        )
                                        stepTitleInput = ""
                                        stepDetailsInput = ""
                                    }
                                },
                                showAlternativeControls = false
                            )
                        }
                    }
                }
            }

            // 6. Attached Images, Files & Web Links inside the Document
            if (attachments.isNotEmpty() || attachmentError != null || isCopyInProgress) {
                item {
                    val attAccent = category.themeCardColor()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(vaultColors.surface)
                            .border(1.dp, vaultColors.border, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ATTACHMENTS (${attachments.size})",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = vaultColors.secondaryText
                            )
                            if (isCopyInProgress) {
                                CircularProgressIndicator(
                                    color = vaultColors.lavender,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (attachmentError != null) {
                            Text(
                                text = attachmentError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = vaultColors.coral,
                                modifier = Modifier.testTag("editor_attachment_error_text")
                            )
                        }

                        attachments.forEachIndexed { idx, att ->
                            VaultAttachmentCard(
                                attachment = att,
                                accentColor = attAccent,
                                onPreview = { previewingAttachment = att },
                                onOpen = {
                                    if (!AttachmentStorageManager.openAttachmentExternally(context, att)) {
                                        attachmentError = "No installed app could open \"${att.name}\"."
                                    } else {
                                        attachmentError = null
                                    }
                                },
                                onRemove = {
                                    val removed = attachments.removeAt(idx)
                                    if (sessionCopiedFiles.contains(removed.uriOrUrl)) {
                                        sessionCopiedFiles.remove(removed.uriOrUrl)
                                        File(removed.uriOrUrl).takeIf { it.exists() }?.delete()
                                    }
                                },
                                testTagPrefix = "editor_attachment_card_$idx"
                            )
                        }
                    }
                }
            }

            // 7. Entry Summary, Tags & Optional Context Metadata
            if (showMetadataDetailsSection) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(vaultColors.surface)
                            .border(1.dp, vaultColors.border, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = categoryMeta.summarySectionLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = vaultColors.secondaryText
                        )
                        VaultTextFieldBox(
                            value = description,
                            onValueChange = { description = it },
                            placeholder = categoryMeta.summaryPlaceholder,
                            minLines = 2,
                            testTag = "editor_description_input"
                        )

                        // Tags
                        Text(
                            text = "TAGS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = vaultColors.secondaryText
                        )
                        if (selectedTags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                selectedTags.forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .widthIn(min = 48.dp)
                                            .clickable { selectedTags.remove(tag) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(17.dp))
                                                .background(vaultColors.secondarySurface)
                                                .border(1.dp, vaultColors.border, RoundedCornerShape(17.dp))
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "#$tag",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = vaultColors.primaryText
                                            )
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove $tag",
                                                tint = vaultColors.secondaryText,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VaultTextFieldBox(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                placeholder = "Add tag (e.g. mobile, workflow)...",
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                testTag = "editor_new_tag_input"
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(vaultColors.secondarySurface)
                                    .border(1.dp, vaultColors.border, RoundedCornerShape(14.dp))
                                    .testTag("editor_add_tag_button")
                                    .semantics { role = Role.Button }
                                    .clickable {
                                        val clean = newTagInput.trim().removePrefix("#").trim()
                                        if (clean.isNotBlank() && !selectedTags.contains(clean)) {
                                            selectedTags.add(clean)
                                            newTagInput = ""
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Tag",
                                    tint = vaultColors.primaryText
                                )
                            }
                        }

                        // Optional Context & Platform fields
                        Text(
                            text = categoryMeta.metadataCardHeader,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = vaultColors.secondaryText
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            VaultTextFieldBox(
                                value = platform,
                                onValueChange = { platform = it },
                                placeholder = categoryMeta.platformPlaceholder,
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                testTag = "editor_platform_input"
                            )
                            VaultTextFieldBox(
                                value = estimatedTime,
                                onValueChange = { estimatedTime = it },
                                placeholder = categoryMeta.estimatedTimePlaceholder,
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                testTag = "editor_estimated_time_input"
                            )
                        }
                        VaultTextFieldBox(
                            value = notes,
                            onValueChange = { notes = it },
                            placeholder = categoryMeta.notesPlaceholder,
                            minLines = 2,
                            testTag = "editor_notes_input"
                        )
                    }
                }
            }
        }

        // Docked V2 Contextual Bottom Toolbar above IME / Navigation Bar
        V2ContextualEditorBottomBar(
            editorState = richEditorState,
            activePanel = activeBottomPanel,
            onSelectPanel = { panel -> activeBottomPanel = panel },
            onToggleInlineStyle = { style ->
                richEditorState = RichTextEditorController.toggleInlineStyle(richEditorState, style)
            },
            onSelectFontSize = { sizeSp ->
                richEditorState = RichTextEditorController.applyFontSize(richEditorState, sizeSp)
            },
            onSelectFontColor = { colorToken ->
                richEditorState = RichTextEditorController.applyFontColor(richEditorState, colorToken)
            },
            onSelectParagraphAlignment = { alignment ->
                richEditorState = RichTextEditorController.applyParagraphAlignment(richEditorState, alignment)
            },
            onToggleLineFormat = { lineFormat ->
                richEditorState = RichTextEditorController.toggleLineFormat(richEditorState, lineFormat)
            },
            onInsertChecklist = {
                richBlocks.add(
                    RichContentBlock(
                        id = UUID.randomUUID().toString(),
                        type = RichBlockType.CHECKLIST,
                        title = "",
                        content = "",
                        checked = false
                    )
                )
            },
            onPickGalleryImage = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onCaptureCameraPhoto = {
                cameraCaptureLauncher.launch(null)
            },
            onPickDocumentFile = {
                documentPickerLauncher.launch(arrayOf("*/*"))
            },
            onAttachWebLink = {
                showAddLinkDialog = true
            },
            onInsertHyperlink = {
                val sel = richEditorState.textFieldValue.selection
                val selectedText = if (!sel.collapsed) {
                    val s = minOf(sel.start, sel.end).coerceIn(0, richEditorState.plainText.length)
                    val e = maxOf(sel.start, sel.end).coerceIn(0, richEditorState.plainText.length)
                    richEditorState.plainText.substring(s, e)
                } else {
                    ""
                }
                richLinkLabelInput = selectedText
                richLinkUrlInput = ""
                showInsertRichLinkDialog = true
            },
            onInsertCodeBlock = {
                codeBlockTitleInput = ""
                codeBlockContentInput = ""
                showInsertCodeBlockDialog = true
            },
            onInsertDivider = {
                richBlocks.add(
                    RichContentBlock(
                        id = UUID.randomUUID().toString(),
                        type = RichBlockType.DIVIDER,
                        title = "",
                        content = ""
                    )
                )
            },
            onOpenMetadataDetails = {
                showMetadataDetailsSection = !showMetadataDetailsSection
            },
            modifier = Modifier
        )
    }

    // Add or Edit Alternative Method Dialog
    if (showAddOrEditRouteDialog) {
        val isEditingRoute = editingRouteId != null
        AlertDialog(
            onDismissRequest = {
                routeNameError = null
                showAddOrEditRouteDialog = false
            },
            containerColor = vaultColors.elevatedSurface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = {
                Text(if (isEditingRoute) "Edit Alternative Method" else "Add Alternative Method")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = routeNameInput,
                        onValueChange = {
                            routeNameInput = it
                            if (routeNameError != null) routeNameError = null
                        },
                        label = { Text("Alternative method name") },
                        placeholder = { Text("e.g. Method 2 — Quick Version") },
                        isError = routeNameError != null,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = vaultColors.primaryText,
                            unfocusedTextColor = vaultColors.primaryText,
                            focusedBorderColor = vaultColors.lime,
                            unfocusedBorderColor = vaultColors.border,
                            errorBorderColor = vaultColors.coral
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_alt_method_name_input")
                    )
                    if (routeNameError != null) {
                        Text(
                            text = routeNameError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.coral,
                            modifier = Modifier.testTag("editor_alt_method_name_error")
                        )
                    }
                    OutlinedTextField(
                        value = routeDescInput,
                        onValueChange = { routeDescInput = it },
                        label = { Text("Optional description") },
                        placeholder = { Text("When to use this route...") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = vaultColors.primaryText,
                            unfocusedTextColor = vaultColors.primaryText,
                            focusedBorderColor = vaultColors.lime,
                            unfocusedBorderColor = vaultColors.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_alt_method_desc_input")
                    )
                    if (!isEditingRoute) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { copyStepsFromCurrentRoute = !copyStepsFromCurrentRoute }
                                .padding(vertical = 4.dp)
                                .testTag("editor_alt_method_copy_steps_checkbox"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = copyStepsFromCurrentRoute,
                                onCheckedChange = { copyStepsFromCurrentRoute = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = vaultColors.lime,
                                    checkmarkColor = vaultColors.darkInk,
                                    uncheckedColor = vaultColors.secondaryText
                                )
                            )
                            Text(
                                text = "Copy steps from current method",
                                style = MaterialTheme.typography.bodyMedium,
                                color = vaultColors.primaryText
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedName = routeNameInput.trim()
                        val isDuplicateMain = mainRouteName.trim().equals(trimmedName, ignoreCase = true)
                        val isDuplicateAlt = alternativeRoutes.any {
                            it.id != editingRouteId && it.name.trim().equals(trimmedName, ignoreCase = true)
                        }
                        when {
                            trimmedName.isEmpty() -> {
                                routeNameError = "Please enter an alternative method name."
                            }
                            isDuplicateMain || isDuplicateAlt -> {
                                routeNameError = "A method route with this name already exists."
                            }
                            else -> {
                                routeNameError = null
                                val trimmedDesc = routeDescInput.trim()
                                if (isEditingRoute) {
                                    val idx = alternativeRoutes.indexOfFirst { it.id == editingRouteId }
                                    if (idx >= 0) {
                                        alternativeRoutes[idx] = alternativeRoutes[idx].copy(
                                            name = trimmedName,
                                            description = trimmedDesc
                                        )
                                    }
                                } else {
                                    val newRouteId = "route_${UUID.randomUUID()}"
                                    val activeAltIdx = alternativeRoutes.indexOfFirst { it.id == activeRouteId }
                                    val sourceSteps = if (activeRouteId == MAIN_METHOD_ROUTE_ID || activeAltIdx < 0) {
                                        steps.toList()
                                    } else {
                                        alternativeRoutes[activeAltIdx].steps
                                    }
                                    val initialRouteSteps = if (copyStepsFromCurrentRoute) {
                                        sourceSteps.mapIndexed { sIdx, step ->
                                            step.copy(
                                                id = UUID.randomUUID().toString(),
                                                itemId = itemId,
                                                routeId = newRouteId,
                                                stepOrder = sIdx + 1
                                            )
                                        }
                                    } else {
                                        emptyList()
                                    }
                                    val created = MethodRouteWithSteps(
                                        id = newRouteId,
                                        itemId = itemId,
                                        name = trimmedName,
                                        description = trimmedDesc,
                                        routeOrder = alternativeRoutes.size + 1,
                                        isMain = false,
                                        steps = initialRouteSteps
                                    )
                                    alternativeRoutes.add(created)
                                    activeRouteId = newRouteId
                                }
                                showAddOrEditRouteDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("editor_confirm_add_alt_method_button")
                ) {
                    Text(
                        text = if (isEditingRoute) "Save" else "Add",
                        color = vaultColors.lime,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        routeNameError = null
                        showAddOrEditRouteDialog = false
                    }
                ) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    // Delete Alternative Method Confirmation Dialog
    routeIdPendingDelete?.let { routeIdToDelete ->
        val routeToDelete = alternativeRoutes.firstOrNull { it.id == routeIdToDelete }
        AlertDialog(
            onDismissRequest = { routeIdPendingDelete = null },
            containerColor = vaultColors.elevatedSurface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Delete Alternative Method?") },
            text = {
                Text(
                    "Remove \"${routeToDelete?.name ?: "this alternative method"}\" and its steps? Your Main Method and other routes will be preserved."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        alternativeRoutes.removeAll { it.id == routeIdToDelete }
                        if (activeRouteId == routeIdToDelete) {
                            activeRouteId = MAIN_METHOD_ROUTE_ID
                        }
                        routeIdPendingDelete = null
                    },
                    modifier = Modifier.testTag("editor_confirm_delete_route_button")
                ) {
                    Text("Delete Route", color = vaultColors.coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { routeIdPendingDelete = null }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    // Insert Code Block Dialog
    if (showInsertCodeBlockDialog) {
        AlertDialog(
            onDismissRequest = { showInsertCodeBlockDialog = false },
            containerColor = vaultColors.elevatedSurface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Insert Code Block") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = codeBlockTitleInput,
                        onValueChange = { codeBlockTitleInput = it },
                        label = { Text("Language / Title (optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = vaultColors.primaryText,
                            unfocusedTextColor = vaultColors.primaryText,
                            focusedBorderColor = vaultColors.actionYellow,
                            unfocusedBorderColor = vaultColors.border
                        ),
                        modifier = Modifier.testTag("code_block_title_input")
                    )
                    OutlinedTextField(
                        value = codeBlockContentInput,
                        onValueChange = { codeBlockContentInput = it },
                        label = { Text("Code snippet") },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = vaultColors.primaryText,
                            unfocusedTextColor = vaultColors.primaryText,
                            focusedBorderColor = vaultColors.actionYellow,
                            unfocusedBorderColor = vaultColors.border
                        ),
                        modifier = Modifier.testTag("code_block_content_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (codeBlockContentInput.isNotBlank()) {
                            richBlocks.add(
                                RichContentBlock(
                                    id = UUID.randomUUID().toString(),
                                    type = RichBlockType.CODE_BLOCK,
                                    title = codeBlockTitleInput.trim(),
                                    content = codeBlockContentInput
                                )
                            )
                            showInsertCodeBlockDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.actionYellow,
                        contentColor = vaultColors.darkInk
                    ),
                    modifier = Modifier.testTag("confirm_code_block_button")
                ) {
                    Text("Insert", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsertCodeBlockDialog = false }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    // Insert Rich Text Hyperlink Dialog
    if (showInsertRichLinkDialog) {
        val linkAccent = category.themeCardColor()
        AlertDialog(
            onDismissRequest = { showInsertRichLinkDialog = false },
            containerColor = vaultColors.elevatedSurface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Insert Hyperlink") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = richLinkLabelInput,
                        onValueChange = { richLinkLabelInput = it },
                        label = { Text("Display text (optional)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = vaultColors.primaryText,
                            unfocusedTextColor = vaultColors.primaryText,
                            focusedBorderColor = linkAccent,
                            unfocusedBorderColor = vaultColors.border
                        ),
                        modifier = Modifier.testTag("rich_link_label_input")
                    )
                    OutlinedTextField(
                        value = richLinkUrlInput,
                        onValueChange = { richLinkUrlInput = it },
                        label = { Text("URL (https://...)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = vaultColors.primaryText,
                            unfocusedTextColor = vaultColors.primaryText,
                            focusedBorderColor = linkAccent,
                            unfocusedBorderColor = vaultColors.border
                        ),
                        modifier = Modifier.testTag("rich_link_url_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (richLinkUrlInput.isNotBlank()) {
                            richEditorState = RichTextEditorController.applyOrInsertLink(
                                state = richEditorState,
                                labelInput = richLinkLabelInput,
                                urlInput = richLinkUrlInput
                            )
                            richLinkLabelInput = ""
                            richLinkUrlInput = ""
                            showInsertRichLinkDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_rich_link_button")
                ) {
                    Text("Insert", color = linkAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInsertRichLinkDialog = false }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    // Add Web Link Attachment Dialog
    if (showAddLinkDialog) {
        AlertDialog(
            onDismissRequest = { showAddLinkDialog = false },
            containerColor = vaultColors.elevatedSurface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Attach Web Link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = linkNameInput,
                        onValueChange = { linkNameInput = it },
                        label = { Text("Link label") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = vaultColors.primaryText,
                            unfocusedTextColor = vaultColors.primaryText,
                            focusedBorderColor = vaultColors.lavender,
                            unfocusedBorderColor = vaultColors.border
                        ),
                        modifier = Modifier.testTag("attachment_label_input")
                    )
                    OutlinedTextField(
                        value = linkUrlInput,
                        onValueChange = { linkUrlInput = it },
                        label = { Text("URL (https://...)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = vaultColors.primaryText,
                            unfocusedTextColor = vaultColors.primaryText,
                            focusedBorderColor = vaultColors.lavender,
                            unfocusedBorderColor = vaultColors.border
                        ),
                        modifier = Modifier.testTag("attachment_url_input")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val rawUrl = linkUrlInput.trim()
                        if (rawUrl.isNotBlank()) {
                            val candidate = attachmentStorage.createLinkAttachment(
                                title = linkNameInput.trim(),
                                url = rawUrl
                            )
                            if (AttachmentStorageManager.isDuplicateAttachment(attachments, candidate)) {
                                attachmentError = "\"${candidate.name}\" is already attached to this entry."
                            } else {
                                attachmentError = null
                                attachments.add(candidate)
                            }
                            linkNameInput = ""
                            linkUrlInput = ""
                            showAddLinkDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_attachment_button")
                ) {
                    Text("Attach", color = vaultColors.lavender, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLinkDialog = false }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    previewingAttachment?.let { att ->
        val previewAccent = category.themeCardColor()
        EditorAttachmentPreviewDialog(
            attachment = att,
            accentColor = previewAccent,
            onDismiss = { previewingAttachment = null },
            onOpenExternal = {
                if (!AttachmentStorageManager.openAttachmentExternally(context, att)) {
                    attachmentError = "No installed app could open \"${att.name}\"."
                } else {
                    attachmentError = null
                }
            }
        )
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = vaultColors.elevatedSurface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Create Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = vaultColors.primaryText,
                        unfocusedTextColor = vaultColors.primaryText,
                        focusedBorderColor = vaultColors.lime,
                        unfocusedBorderColor = vaultColors.border
                    ),
                    modifier = Modifier.testTag("new_folder_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName.trim(), category, "#F9D55D")
                            newFolderName = ""
                            showNewFolderDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_new_folder_button")
                ) {
                    Text("Create", color = vaultColors.lime, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    // Concurrent Edit Conflict Resolution Dialog (P1-02)
    conflictResult?.let { conflict ->
        AlertDialog(
            onDismissRequest = { conflictResult = null },
            containerColor = vaultColors.elevatedSurface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Conflicting Version Detected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = conflict.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = vaultColors.primaryText
                    )
                    conflict.currentDatabaseItem?.entity?.content?.takeIf { it.isNotBlank() }?.let { storedContent ->
                        Text(
                            text = "Stored preview: ${storedContent.take(120)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        conflictResult = null
                        executeSave(forceOverwrite = true)
                    },
                    modifier = Modifier.testTag("conflict_keep_current_edits_button")
                ) {
                    Text("Keep My Edits", color = vaultColors.coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    conflict.currentDatabaseItem?.let { dbItem ->
                        TextButton(
                            onClick = {
                                title = dbItem.entity.title
                                description = dbItem.entity.description
                                category = dbItem.categoryEnum
                                promptLiteralContent = dbItem.entity.content
                                richEditorState = RichTextEditorState.fromStoredContent(dbItem.entity.content)
                                selectedFolderId = dbItem.entity.folderId
                                ideaStatus = dbItem.statusEnum
                                platform = dbItem.entity.platform
                                estimatedTime = dbItem.entity.estimatedTime
                                notes = dbItem.entity.notes
                                isPinned = dbItem.entity.isPinned
                                isFavorite = dbItem.entity.isFavorite
                                selectedTags.clear()
                                selectedTags.addAll(dbItem.tags)
                                richBlocks.clear()
                                richBlocks.addAll(dbItem.richBlocks)
                                val loadedMainRoute = dbItem.effectiveMethodRoutes.firstOrNull { it.isMain }
                                mainRouteName = loadedMainRoute?.name ?: DEFAULT_MAIN_METHOD_NAME
                                mainRouteDescription = loadedMainRoute?.description ?: ""
                                val loadedMainSteps = (loadedMainRoute?.steps ?: dbItem.steps).sortedBy { it.stepOrder }
                                steps.clear()
                                steps.addAll(loadedMainSteps)
                                alternativeRoutes.clear()
                                alternativeRoutes.addAll(dbItem.alternativeMethodRoutes)
                                attachments.clear()
                                attachments.addAll(dbItem.attachments)
                                stepTitleInput = ""
                                stepDetailsInput = ""
                                newTagInput = ""
                                baseUpdatedAt = dbItem.entity.updatedAt
                                baselineSnapshot = EditorSnapshot(
                                    title = dbItem.entity.title,
                                    description = dbItem.entity.description,
                                    content = dbItem.entity.content,
                                    categoryId = dbItem.categoryEnum.id,
                                    folderId = dbItem.entity.folderId,
                                    ideaStatusId = dbItem.statusEnum.id,
                                    platform = dbItem.entity.platform,
                                    estimatedTime = dbItem.entity.estimatedTime,
                                    notes = dbItem.entity.notes,
                                    isPinned = dbItem.entity.isPinned,
                                    isFavorite = dbItem.entity.isFavorite,
                                    tags = dbItem.tags,
                                    richBlocks = dbItem.richBlocks,
                                    steps = loadedMainSteps,
                                    mainRouteName = mainRouteName,
                                    mainRouteDescription = mainRouteDescription,
                                    alternativeRoutes = dbItem.alternativeMethodRoutes,
                                    attachments = dbItem.attachments,
                                    customTypeId = dbItem.entity.customTypeId,
                                    customFieldsJson = dbItem.entity.customFieldsJson
                                )
                                selectedCustomTypeId = dbItem.entity.customTypeId
                                customFieldsJson = dbItem.entity.customFieldsJson
                                conflictResult = null
                            },
                            modifier = Modifier.testTag("conflict_reload_database_button")
                        ) {
                            Text("Load Stored Version", color = vaultColors.lime)
                        }
                    }
                    TextButton(
                        onClick = { conflictResult = null },
                        modifier = Modifier.testTag("conflict_cancel_button")
                    ) {
                        Text("Review", color = vaultColors.secondaryText)
                    }
                }
            }
        )
    }

    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            containerColor = vaultColors.elevatedSurface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Discard unsaved changes?") },
            text = {
                Text("You have unsaved changes in this entry. Would you like to discard them or keep editing?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmDialog = false
                        draftExplicitlyClearedOrSaved = true
                        pendingNewCustomType = null
                        val filesToClean = sessionCopiedFiles.toList()
                        sessionCopiedFiles.clear()
                        filesToClean.forEach { path ->
                            File(path).takeIf { it.exists() }?.delete()
                        }
                        EditorLargeStateSessionBuffer.clearSession(stateKey, context)
                        onDiscardDraft()
                        onCancel()
                    },
                    modifier = Modifier.testTag("confirm_discard_editor_button")
                ) {
                    Text("Discard", color = vaultColors.coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirmDialog = false },
                    modifier = Modifier.testTag("keep_editing_button")
                ) {
                    Text("Keep Editing", color = vaultColors.primaryText)
                }
            }
        )
    }

    if (showCustomTypeBuilderDialog) {
        CustomTypeBuilderDialog(
            initialCustomType = editingCustomTypeInEditor,
            existingCustomTypes = customTypes,
            onDismiss = {
                editingCustomTypeInEditor = null
                showCustomTypeBuilderDialog = false
            },
            onSave = { entity, fields ->
                onSaveCustomType(entity, fields) { saved ->
                    selectedCustomTypeId = saved.id
                    editingCustomTypeInEditor = null
                    showCustomTypeBuilderDialog = false
                }
            }
        )
    }

    if (showSmartImportSheet) {
        val draftHasExistingContent = title.isNotBlank() ||
            description.isNotBlank() ||
            effectiveSerializedContent.isNotBlank() ||
            steps.any { it.title.isNotBlank() || it.description.isNotBlank() }
        SmartImportBottomSheet(
            defaultCategory = category,
            initialMethodImportMode = smartImportInitialMethodMode,
            activeCustomType = activeCustomType,
            existingCustomTypes = customTypes,
            existingAlternativeRoutes = alternativeRoutes,
            hasExistingDraftContent = draftHasExistingContent,
            onDismiss = { showSmartImportSheet = false },
            onShowMessage = { },
            onConfirmImport = { payload ->
                val result = payload.validationResult
                val methodMode = payload.methodImportMode
                val targetCat = if (payload.keepOriginalCategory) category else result.targetCategory
                val chosenMainSteps = if (payload.useSecondaryMainSteps && result.secondaryMainSteps.isNotEmpty()) {
                    result.secondaryMainSteps
                } else {
                    result.mainSteps
                }

                // Helper to append preserved/unmapped custom fields into notes without losing data
                val extraNotesToAppend = mutableMapOf<String, String>()
                if (payload.preserveUnmappedFieldsInNotes && result.unmappedCustomFields.isNotEmpty()) {
                    extraNotesToAppend.putAll(result.unmappedCustomFields)
                }

                // 1. Handle Custom Type creation, linking, or safe fallback when declined (Audit P1-04, P1-05 & FIX 02)
                if (result.requiresCustomTypeCreationConfirmation && result.newCustomTypeDraft != null) {
                    val draftCt = result.newCustomTypeDraft
                    if (payload.confirmCreateNewCustomType) {
                        val newCtId = "custom_${UUID.randomUUID().toString().take(8)}"
                        val newCt = CustomTypeEntity(
                            id = newCtId,
                            name = draftCt.name,
                            iconName = draftCt.iconName,
                            colorHex = draftCt.colorHex,
                            description = draftCt.description,
                            fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(draftCt.fields)
                        )
                        // FIX 02: Stage the new Custom Type in editor draft state only.
                        // Do NOT commit to Room until the user presses the Editor Save button!
                        pendingNewCustomType = newCt
                        selectedCustomTypeId = newCtId
                        val merged = customFieldsMap.toMutableMap()
                        merged.putAll(result.customFieldValuesById)
                        customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(merged)
                    } else {
                        pendingNewCustomType = null
                        // User declined creating the new Custom Type — never write orphan field_* IDs!
                        val fallbackCt = payload.fallbackExistingCustomTypeId?.let { fbId ->
                            customTypes.firstOrNull { it.id == fbId }
                        }
                        if (fallbackCt != null) {
                            selectedCustomTypeId = fallbackCt.id
                            val fbDefs = VaultJsonSerializers.decodeCustomFieldDefinitions(fallbackCt.fieldsJson)
                            val remapped = customFieldsMap.toMutableMap()
                            draftCt.fields.forEach { draftField ->
                                val rawVal = result.customFieldValuesById[draftField.id].orEmpty()
                                if (rawVal.isNotBlank()) {
                                    val matchingFb = fbDefs.firstOrNull {
                                        it.name.equals(draftField.name, ignoreCase = true)
                                    }
                                    if (matchingFb != null) {
                                        remapped[matchingFb.id] = rawVal
                                    } else {
                                        extraNotesToAppend[draftField.name] = SmartImportExportEngine
                                            .formatCustomFieldValueReadable(draftField, rawVal)
                                    }
                                }
                            }
                            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(remapped)
                        } else {
                            // Standard entry fallback: convert all draft custom fields into readable notes
                            selectedCustomTypeId = null
                            customFieldsJson = "{}"
                            draftCt.fields.forEach { draftField ->
                                val rawVal = result.customFieldValuesById[draftField.id].orEmpty()
                                if (rawVal.isNotBlank()) {
                                    extraNotesToAppend[draftField.name] = SmartImportExportEngine
                                        .formatCustomFieldValueReadable(draftField, rawVal)
                                }
                            }
                        }
                    }
                } else if (!result.matchedCustomTypeId.isNullOrBlank()) {
                    selectedCustomTypeId = result.matchedCustomTypeId
                    if (result.customFieldValuesById.isNotEmpty()) {
                        val merged = customFieldsMap.toMutableMap()
                        merged.putAll(result.customFieldValuesById)
                        customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(merged)
                    }
                } else {
                    selectedCustomTypeId = null
                    customFieldsJson = "{}"
                }

                // Convert SmartImportStepDraft to MethodStepEntity (preserving richDetails resolved into s.details — Audit P1-06)
                fun toStepEntities(draftSteps: List<com.example.data.SmartImportStepDraft>, rId: String): List<MethodStepEntity> {
                    return draftSteps.mapIndexed { idx, s ->
                        MethodStepEntity(
                            id = UUID.randomUUID().toString(),
                            itemId = itemId,
                            routeId = rId,
                            stepOrder = idx + 1,
                            title = s.title.ifBlank { "Step ${idx + 1}" },
                            description = s.details,
                            notes = s.notes,
                            linksJson = VaultJsonSerializers.encodeStringList(s.links)
                        )
                    }
                }

                // 2. Handle Bulk Add Steps vs Merge Selected Fields vs Full Replace
                if (targetCat == VaultCategory.METHOD && methodMode == MethodImportMode.BULK_APPEND_STEPS) {
                    if (category != VaultCategory.METHOD) {
                        category = VaultCategory.METHOD
                    }
                    when (payload.methodTargetRouteChoice) {
                        MethodTargetRouteChoice.NEW_ALTERNATIVE -> {
                            val newRouteId = "route_${UUID.randomUUID()}"
                            val normalized = toStepEntities(chosenMainSteps, newRouteId)
                            val newRoute = MethodRouteWithSteps(
                                id = newRouteId,
                                itemId = itemId,
                                name = payload.newAlternativeRouteName.trim().ifBlank {
                                    result.title.ifBlank { "Alternative Method ${alternativeRoutes.size + 1}" }
                                },
                                description = result.description,
                                routeOrder = alternativeRoutes.size + 1,
                                isMain = false,
                                steps = normalized
                            )
                            alternativeRoutes.add(newRoute)
                            activeRouteId = newRouteId
                        }
                        MethodTargetRouteChoice.EXISTING_ALTERNATIVE -> {
                            val targetRId = payload.targetExistingRouteId
                            val rIdx = alternativeRoutes.indexOfFirst { it.id == targetRId }
                            if (rIdx < 0 || targetRId == null) {
                                // Audit P1-14: Never silently drop steps when route was deleted
                                saveErrorMessage = "Selected Alternative Method route no longer exists."
                                return@SmartImportBottomSheet
                            }
                            val existingRoute = alternativeRoutes[rIdx]
                            val baseSteps = existingRoute.steps.filter {
                                it.title.isNotBlank() || it.description.isNotBlank() || it.notes.isNotBlank()
                            }
                            val incoming = toStepEntities(chosenMainSteps, targetRId)
                            val combined = (baseSteps + incoming).mapIndexed { idx, s ->
                                s.copy(
                                    id = if (idx < baseSteps.size) s.id else UUID.randomUUID().toString(),
                                    itemId = itemId,
                                    routeId = targetRId,
                                    stepOrder = idx + 1
                                )
                            }
                            alternativeRoutes[rIdx] = existingRoute.copy(steps = combined)
                            activeRouteId = targetRId
                        }
                        MethodTargetRouteChoice.MAIN_METHOD -> {
                            val baseSteps = steps.filter {
                                it.title.isNotBlank() || it.description.isNotBlank() || it.notes.isNotBlank()
                            }
                            val incoming = toStepEntities(chosenMainSteps, MAIN_METHOD_ROUTE_ID)
                            val combined = (baseSteps + incoming).mapIndexed { idx, s ->
                                s.copy(
                                    id = if (idx < baseSteps.size) s.id else UUID.randomUUID().toString(),
                                    itemId = itemId,
                                    routeId = MAIN_METHOD_ROUTE_ID,
                                    stepOrder = idx + 1
                                )
                            }
                            steps.clear()
                            steps.addAll(combined)
                            activeRouteId = MAIN_METHOD_ROUTE_ID
                        }
                    }
                } else if (targetCat == VaultCategory.METHOD && methodMode == MethodImportMode.MERGE_SELECTED_FIELDS) {
                    // Audit P1-07: Merge Selected Fields mode
                    category = VaultCategory.METHOD
                    if (payload.mergeTitle && result.title.isNotBlank()) {
                        title = result.title.replace('\n', ' ').replace('\r', ' ')
                        titleError = false
                    }
                    if (payload.mergeDescription) {
                        val incomingDesc = result.description
                        if (incomingDesc.isNotBlank()) {
                            description = if (description.isBlank()) incomingDesc else "$description\n\n$incomingDesc"
                            val currentRich = richEditorState.toSerializedMarkdown()
                            val mergedBody = if (currentRich.isBlank()) {
                                result.content.ifBlank { incomingDesc }
                            } else {
                                "$currentRich\n\n${result.content.ifBlank { incomingDesc }}"
                            }
                            richEditorState = RichTextEditorState.fromStoredContent(mergedBody)
                        }
                    }
                    if (payload.mergeTags && result.tags.isNotEmpty()) {
                        result.tags.forEach { tag ->
                            val clean = tag.trim().removePrefix("#").trim()
                            if (clean.isNotBlank() && !selectedTags.contains(clean)) {
                                selectedTags.add(clean)
                            }
                        }
                    }
                    if (payload.mergeNotes) {
                        val incomingNotes = result.notes
                        if (incomingNotes.isNotBlank()) {
                            notes = if (notes.isBlank()) incomingNotes else "$notes\n\n$incomingNotes"
                        }
                    }
                    if (payload.mergeMainSteps && chosenMainSteps.isNotEmpty()) {
                        val baseSteps = steps.filter {
                            it.title.isNotBlank() || it.description.isNotBlank() || it.notes.isNotBlank()
                        }
                        val incoming = toStepEntities(chosenMainSteps, MAIN_METHOD_ROUTE_ID)
                        val combined = (baseSteps + incoming).mapIndexed { idx, s ->
                            s.copy(
                                id = if (idx < baseSteps.size) s.id else UUID.randomUUID().toString(),
                                itemId = itemId,
                                routeId = MAIN_METHOD_ROUTE_ID,
                                stepOrder = idx + 1
                            )
                        }
                        steps.clear()
                        steps.addAll(combined)
                    }
                    if (payload.mergeAltRoutes && result.alternativeRoutes.isNotEmpty()) {
                        result.alternativeRoutes.forEachIndexed { rIdx, altDraft ->
                            val rId = "route_${UUID.randomUUID()}"
                            val rSteps = toStepEntities(altDraft.steps, rId)
                            alternativeRoutes.add(
                                MethodRouteWithSteps(
                                    id = rId,
                                    itemId = itemId,
                                    name = altDraft.name.ifBlank { "Method ${alternativeRoutes.size + rIdx + 2}" },
                                    description = altDraft.description,
                                    routeOrder = alternativeRoutes.size + 1,
                                    isMain = false,
                                    steps = rSteps
                                )
                            )
                        }
                    }
                } else {
                    // Full Import (Import Method FULL_REPLACE or Idea / Prompt / Note / Custom Type)
                    category = targetCat
                    if (result.title.isNotBlank()) {
                        title = result.title.replace('\n', ' ').replace('\r', ' ')
                        titleError = false
                    }
                    val resolvedDesc = when (payload.resolvedIdeaDescriptionChoice) {
                        "SUMMARY" -> result.summary.ifBlank { result.description }
                        "BOTH" -> if (result.summary.isNotBlank() && result.description.isNotBlank() && result.summary != result.description) {
                            "${result.description}\n\nSummary:\n${result.summary}"
                        } else {
                            result.description.ifBlank { result.summary }
                        }
                        else -> result.description.ifBlank { result.summary }
                    }
                    description = resolvedDesc
                    if (targetCat == VaultCategory.PROMPT) {
                        promptUndoStack = (promptUndoStack + promptLiteralContent).takeLast(40)
                        promptRedoStack = emptyList()
                        promptLiteralContent = result.content
                    } else {
                        val bodyToLoad = when {
                            result.content.isNotBlank() -> result.content
                            targetCat != VaultCategory.METHOD && resolvedDesc.isNotBlank() -> resolvedDesc
                            else -> ""
                        }
                        richEditorState = RichTextEditorState.fromStoredContent(bodyToLoad)
                    }

                    // Audit P1-07: In FULL_REPLACE mode, replace tags, notes, richBlocks, and alternativeRoutes cleanly
                    selectedTags.clear()
                    result.tags.forEach { tag ->
                        val clean = tag.trim().removePrefix("#").trim()
                        if (clean.isNotBlank() && !selectedTags.contains(clean)) {
                            selectedTags.add(clean)
                        }
                    }
                    result.status?.let { ideaStatus = it }
                    platform = result.platform
                    estimatedTime = result.estimatedTime
                    notes = result.notes

                    richBlocks.clear()
                    richBlocks.addAll(result.richBlocks)

                    if (targetCat == VaultCategory.METHOD) {
                        mainRouteName = result.mainRouteName.ifBlank { DEFAULT_MAIN_METHOD_NAME }
                        mainRouteDescription = result.mainRouteDescription
                        val normalizedMain = toStepEntities(chosenMainSteps, MAIN_METHOD_ROUTE_ID)
                        steps.clear()
                        steps.addAll(normalizedMain)
                        // Audit P1-07: Always clear old alternativeRoutes on FULL_REPLACE even if incoming has 0 alt routes
                        alternativeRoutes.clear()
                        result.alternativeRoutes.forEachIndexed { rIdx, altDraft ->
                            val rId = "route_${UUID.randomUUID()}"
                            val rSteps = toStepEntities(altDraft.steps, rId)
                            alternativeRoutes.add(
                                MethodRouteWithSteps(
                                    id = rId,
                                    itemId = itemId,
                                    name = altDraft.name.ifBlank { "Method ${rIdx + 2}" },
                                    description = altDraft.description,
                                    routeOrder = rIdx + 1,
                                    isMain = false,
                                    steps = rSteps
                                )
                            )
                        }
                        activeRouteId = MAIN_METHOD_ROUTE_ID
                    }
                }

                // Append any unmapped or fallback custom fields into notes so zero data is lost (Audit P1-04 & P1-05)
                if (extraNotesToAppend.isNotEmpty()) {
                    val formattedExtra = SmartImportExportEngine.formatUnmappedCustomFieldsBlock(extraNotesToAppend)
                    notes = if (notes.isBlank()) formattedExtra else "$notes\n\n$formattedExtra"
                }

                showSmartImportSheet = false
            }
        )
    }
}

private data class CategoryEditorMetadata(
    val titlePlaceholder: String,
    val summarySectionLabel: String,
    val summaryPlaceholder: String,
    val bodySectionLabel: String,
    val bodyPlaceholder: String,
    val metadataCardHeader: String,
    val platformPlaceholder: String,
    val estimatedTimePlaceholder: String,
    val notesPlaceholder: String
)

private fun getCategoryEditorMetadata(category: VaultCategory): CategoryEditorMetadata {
    return when (category) {
        VaultCategory.IDEA -> CategoryEditorMetadata(
            titlePlaceholder = "Give your idea a clear, memorable title...",
            summarySectionLabel = "SUMMARY / KEY HOOK",
            summaryPlaceholder = "Core thesis or one-sentence pitch...",
            bodySectionLabel = "IDEA DETAILS & NOTES",
            bodyPlaceholder = "Start writing your idea, context, and next actions...",
            metadataCardHeader = "CONTEXT & ADDITIONAL NOTES (OPTIONAL)",
            platformPlaceholder = "Domain or project",
            estimatedTimePlaceholder = "Timeline / Scope",
            notesPlaceholder = "Additional thoughts or inspiration source..."
        )
        VaultCategory.METHOD -> CategoryEditorMetadata(
            titlePlaceholder = "Name this method, playbook, or workflow...",
            summarySectionLabel = "OVERVIEW & OBJECTIVE",
            summaryPlaceholder = "When to use this method and what outcome it produces...",
            bodySectionLabel = "METHOD NOTES & PRINCIPLES",
            bodyPlaceholder = "Write method principles, prerequisites, or workflow notes...",
            metadataCardHeader = "TOOLS & EXECUTION METADATA (OPTIONAL)",
            platformPlaceholder = "Tools or environment (e.g. Android Studio)",
            estimatedTimePlaceholder = "Estimated time (e.g. ~ 30 mins)",
            notesPlaceholder = "Execution tips or prerequisites..."
        )
        VaultCategory.PROMPT -> CategoryEditorMetadata(
            titlePlaceholder = "Name this prompt template...",
            summarySectionLabel = "USE CASE & INSTRUCTIONS",
            summaryPlaceholder = "What this prompt achieves and which variables to fill...",
            bodySectionLabel = "PROMPT TEMPLATE",
            bodyPlaceholder = "Write your full reusable prompt instructions, role, {VARIABLES}, JSON, or code fences...",
            metadataCardHeader = "MODEL & PROMPT METADATA (OPTIONAL)",
            platformPlaceholder = "Target model or platform (e.g. Gemini)",
            estimatedTimePlaceholder = "Token / context tier",
            notesPlaceholder = "Usage notes or example variables..."
        )
        VaultCategory.KNOWLEDGE -> CategoryEditorMetadata(
            titlePlaceholder = "Note title...",
            summarySectionLabel = "KEY TAKEAWAY",
            summaryPlaceholder = "Distill the core insight in 1–2 sentences...",
            bodySectionLabel = "NOTE CONTENT",
            bodyPlaceholder = "Start writing your note...",
            metadataCardHeader = "SOURCE & CITATION (OPTIONAL)",
            platformPlaceholder = "Book, article, or course",
            estimatedTimePlaceholder = "Topic / Field",
            notesPlaceholder = "Source URL, author, or citation notes..."
        )
        VaultCategory.REFERENCE -> CategoryEditorMetadata(
            titlePlaceholder = "Resource, documentation, or benchmark title...",
            summarySectionLabel = "REFERENCE SUMMARY",
            summaryPlaceholder = "Why this reference is worth keeping...",
            bodySectionLabel = "REFERENCE DETAILS & SNIPPETS",
            bodyPlaceholder = "Key specifications, parameters, or notes...",
            metadataCardHeader = "SOURCE & PLATFORM METADATA",
            platformPlaceholder = "Platform / Documentation site",
            estimatedTimePlaceholder = "Version / Reference code",
            notesPlaceholder = "Primary source URL or maintainer..."
        )
        VaultCategory.INBOX -> CategoryEditorMetadata(
            titlePlaceholder = "Quick capture title...",
            summarySectionLabel = "SUMMARY",
            summaryPlaceholder = "Quick context...",
            bodySectionLabel = "CAPTURE NOTES",
            bodyPlaceholder = "Write down everything you want to remember...",
            metadataCardHeader = "ADDITIONAL CONTEXT (OPTIONAL)",
            platformPlaceholder = "Platform / Tool",
            estimatedTimePlaceholder = "Estimated time",
            notesPlaceholder = "Extra notes..."
        )
    }
}

@Composable
private fun DocumentRichTextCanvas(
    state: RichTextEditorState,
    onStateChange: (RichTextEditorState) -> Unit,
    placeholder: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    minHeight: androidx.compose.ui.unit.Dp = 230.dp,
    testTag: String = "editor_content_input"
) {
    val vaultColors = IdeaVaultTheme.colors
    val visualTransformation = remember(state, accentColor, vaultColors.isDark) {
        RichTextVisualTransformation(
            state = state,
            linkColor = accentColor,
            isDarkTheme = vaultColors.isDark
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight + 10.dp)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (state.plainText.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                color = vaultColors.secondaryText.copy(alpha = 0.65f)
            )
        }
        BasicTextField(
            value = state.textFieldValue,
            onValueChange = { newValue: TextFieldValue ->
                onStateChange(
                    RichTextEditorController.onValueChange(
                        previous = state,
                        incoming = newValue
                    )
                )
            },
            visualTransformation = visualTransformation,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = vaultColors.primaryText,
                lineHeight = 25.sp
            ),
            cursorBrush = SolidColor(vaultColors.actionYellow),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .testTag(testTag)
        )
    }
}

@Composable
private fun PromptLiteralCanvas(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "editor_content_input"
) {
    val vaultColors = IdeaVaultTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(vaultColors.surface)
            .border(1.dp, vaultColors.yellow.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 24.sp
                ),
                color = vaultColors.secondaryText.copy(alpha = 0.65f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = vaultColors.primaryText,
                fontFamily = FontFamily.Monospace,
                lineHeight = 24.sp
            ),
            cursorBrush = SolidColor(vaultColors.actionYellow),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 210.dp)
                .testTag(testTag)
        )
    }
}

@Composable
private fun MethodStepsHeaderSection(
    sectionTitle: String?,
    activeRouteId: String,
    mainRouteName: String,
    mainRouteStepCount: Int,
    activeRouteName: String,
    activeRouteDescription: String,
    alternativeRoutes: List<MethodRouteWithSteps>,
    onSelectRoute: (String) -> Unit,
    onRequestAddAlternativeRoute: () -> Unit,
    onRequestEditActiveRoute: () -> Unit,
    onDuplicateActiveRoute: () -> Unit,
    onRequestDeleteActiveRoute: () -> Unit,
    onRequestImportMethod: () -> Unit = {},
    onRequestBulkAddSteps: () -> Unit = {},
    showAlternativeControls: Boolean = true
) {
    val vaultColors = IdeaVaultTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("editor_steps_section")
            .then(
                if (sectionTitle != null) {
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(vaultColors.surface)
                        .border(1.dp, vaultColors.border, RoundedCornerShape(22.dp))
                        .padding(16.dp)
                } else {
                    Modifier
                }
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (sectionTitle != null || showAlternativeControls) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (sectionTitle != null) {
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = if (vaultColors.isDark) vaultColors.lime else vaultColors.primaryText
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (showAlternativeControls) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 48.dp)
                            .testTag("editor_add_alternative_method_button")
                            .semantics { role = Role.Button }
                            .clickable(onClick = onRequestAddAlternativeRoute),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(vaultColors.secondarySurface)
                                .border(1.dp, vaultColors.lime.copy(alpha = 0.65f), RoundedCornerShape(17.dp))
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+ Add Alternative Method",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (vaultColors.isDark) vaultColors.lime else vaultColors.primaryText
                            )
                        }
                    }
                }
            }

            if (showAlternativeControls) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("editor_import_method_button")
                            .semantics { role = Role.Button }
                            .clickable(onClick = onRequestImportMethod),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(vaultColors.actionYellow.copy(alpha = 0.16f))
                                .border(1.dp, vaultColors.actionYellow.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Import Method",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (vaultColors.isDark) vaultColors.actionYellow else vaultColors.primaryText
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("editor_bulk_add_steps_button")
                            .semantics { role = Role.Button }
                            .clickable(onClick = onRequestBulkAddSteps),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(vaultColors.lime.copy(alpha = 0.16f))
                                .border(1.dp, vaultColors.lime.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Bulk Add Steps",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (vaultColors.isDark) vaultColors.lime else vaultColors.primaryText
                            )
                        }
                    }
                }
            }
        }

        if (showAlternativeControls && alternativeRoutes.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editor_method_route_selector"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = MAIN_METHOD_ROUTE_ID) {
                    val isSelected = activeRouteId == MAIN_METHOD_ROUTE_ID
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 48.dp)
                            .testTag("editor_route_tab_main")
                            .semantics { role = Role.Tab }
                            .clickable { onSelectRoute(MAIN_METHOD_ROUTE_ID) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) vaultColors.lime else vaultColors.secondarySurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else vaultColors.border,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$mainRouteName ($mainRouteStepCount)",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) vaultColors.darkInk else vaultColors.primaryText
                            )
                        }
                    }
                }

                items(alternativeRoutes, key = { it.id }) { altRoute ->
                    val isSelected = activeRouteId == altRoute.id
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 48.dp)
                            .testTag("editor_route_tab_${altRoute.id}")
                            .semantics { role = Role.Tab }
                            .clickable { onSelectRoute(altRoute.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) vaultColors.lime else vaultColors.secondarySurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.Transparent else vaultColors.border,
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${altRoute.name} (${altRoute.steps.size})",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) vaultColors.darkInk else vaultColors.primaryText
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(vaultColors.secondarySurface.copy(alpha = 0.7f))
                    .border(1.dp, vaultColors.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeRouteName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = vaultColors.primaryText
                    )
                    if (activeRouteDescription.isNotBlank()) {
                        Text(
                            text = activeRouteDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onDuplicateActiveRoute,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("editor_duplicate_route_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate route",
                            tint = vaultColors.primaryText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (activeRouteId != MAIN_METHOD_ROUTE_ID) {
                        IconButton(
                            onClick = onRequestEditActiveRoute,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("editor_edit_route_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename alternative method",
                                tint = vaultColors.primaryText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onRequestDeleteActiveRoute,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("editor_delete_route_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete alternative method",
                                tint = vaultColors.coral,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodStepEditorCard(
    idx: Int,
    step: MethodStepEntity,
    activeSteps: List<MethodStepEntity>,
    onUpdateActiveSteps: (List<MethodStepEntity>) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(vaultColors.elevatedSurface)
            .border(1.dp, vaultColors.border, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("editor_step_card_$idx"),
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
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(vaultColors.lime),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${idx + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = vaultColors.darkInk
                    )
                }
                Text(
                    text = "Step ${idx + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = vaultColors.secondaryText
                )
            }

            Row {
                IconButton(
                    onClick = {
                        if (idx > 0) {
                            val mutable = activeSteps.toMutableList()
                            val item = mutable.removeAt(idx)
                            mutable.add(idx - 1, item)
                            onUpdateActiveSteps(mutable)
                        }
                    },
                    enabled = idx > 0,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor_step_move_up_$idx")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowUpward,
                        contentDescription = "Move step up",
                        tint = if (idx > 0) vaultColors.primaryText else vaultColors.secondaryText.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        if (idx < activeSteps.lastIndex) {
                            val mutable = activeSteps.toMutableList()
                            val item = mutable.removeAt(idx)
                            mutable.add(idx + 1, item)
                            onUpdateActiveSteps(mutable)
                        }
                    },
                    enabled = idx < activeSteps.lastIndex,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor_step_move_down_$idx")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Move step down",
                        tint = if (idx < activeSteps.lastIndex) vaultColors.primaryText else vaultColors.secondaryText.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val mutable = activeSteps.toMutableList()
                        mutable.removeAt(idx)
                        onUpdateActiveSteps(mutable)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor_step_delete_$idx")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete step",
                        tint = vaultColors.coral,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        VaultTextFieldBox(
            value = step.title,
            onValueChange = { newTitle ->
                val mutable = activeSteps.toMutableList()
                mutable[idx] = step.copy(title = newTitle, stepOrder = idx + 1)
                onUpdateActiveSteps(mutable)
            },
            placeholder = "Step title...",
            singleLine = true,
            testTag = "editor_step_title_$idx"
        )

        VaultTextFieldBox(
            value = step.description,
            onValueChange = { newDetails ->
                val mutable = activeSteps.toMutableList()
                mutable[idx] = step.copy(description = newDetails, stepOrder = idx + 1)
                onUpdateActiveSteps(mutable)
            },
            placeholder = "Step instructions or tips...",
            minLines = 2,
            testTag = "editor_step_details_$idx"
        )

        VaultTextFieldBox(
            value = step.notes,
            onValueChange = { newNotes ->
                val mutable = activeSteps.toMutableList()
                mutable[idx] = step.copy(notes = newNotes, stepOrder = idx + 1)
                onUpdateActiveSteps(mutable)
            },
            placeholder = "Optional step notes or link...",
            singleLine = true,
            testTag = "editor_step_notes_$idx"
        )
    }
}

@Composable
private fun MethodAddStepEditorCard(
    activeRouteName: String,
    stepTitleInput: String,
    onStepTitleChange: (String) -> Unit,
    stepDetailsInput: String,
    onStepDetailsChange: (String) -> Unit,
    onAddStep: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(vaultColors.elevatedSurface.copy(alpha = 0.5f))
            .border(1.dp, vaultColors.border, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("editor_add_step_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Add another step ($activeRouteName)",
            style = MaterialTheme.typography.labelMedium,
            color = vaultColors.secondaryText
        )
        VaultTextFieldBox(
            value = stepTitleInput,
            onValueChange = onStepTitleChange,
            placeholder = "New step title...",
            singleLine = true,
            testTag = "editor_new_step_title"
        )
        VaultTextFieldBox(
            value = stepDetailsInput,
            onValueChange = onStepDetailsChange,
            placeholder = "Details (optional)...",
            singleLine = true,
            testTag = "editor_new_step_details"
        )
        Box(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(vaultColors.lime)
                .testTag("editor_add_step_button")
                .semantics { role = Role.Button }
                .clickable(onClick = onAddStep)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+ Add Step",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = vaultColors.darkInk
            )
        }
    }
}

@Composable
private fun MethodStepsEditorSection(
    sectionTitle: String?,
    itemId: String,
    activeRouteId: String,
    mainRouteName: String,
    mainRouteStepCount: Int,
    activeRouteName: String,
    activeRouteDescription: String,
    alternativeRoutes: List<MethodRouteWithSteps>,
    activeSteps: List<MethodStepEntity>,
    onSelectRoute: (String) -> Unit,
    onRequestAddAlternativeRoute: () -> Unit,
    onRequestEditActiveRoute: () -> Unit,
    onDuplicateActiveRoute: () -> Unit,
    onRequestDeleteActiveRoute: () -> Unit,
    onUpdateActiveSteps: (List<MethodStepEntity>) -> Unit,
    stepTitleInput: String,
    onStepTitleChange: (String) -> Unit,
    stepDetailsInput: String,
    onStepDetailsChange: (String) -> Unit,
    onAddStep: () -> Unit,
    showAlternativeControls: Boolean = true
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MethodStepsHeaderSection(
            sectionTitle = sectionTitle,
            activeRouteId = activeRouteId,
            mainRouteName = mainRouteName,
            mainRouteStepCount = mainRouteStepCount,
            activeRouteName = activeRouteName,
            activeRouteDescription = activeRouteDescription,
            alternativeRoutes = alternativeRoutes,
            onSelectRoute = onSelectRoute,
            onRequestAddAlternativeRoute = onRequestAddAlternativeRoute,
            onRequestEditActiveRoute = onRequestEditActiveRoute,
            onDuplicateActiveRoute = onDuplicateActiveRoute,
            onRequestDeleteActiveRoute = onRequestDeleteActiveRoute,
            showAlternativeControls = showAlternativeControls
        )
        activeSteps.forEachIndexed { idx, step ->
            MethodStepEditorCard(
                idx = idx,
                step = step,
                activeSteps = activeSteps,
                onUpdateActiveSteps = onUpdateActiveSteps
            )
        }
        MethodAddStepEditorCard(
            activeRouteName = activeRouteName,
            stepTitleInput = stepTitleInput,
            onStepTitleChange = onStepTitleChange,
            stepDetailsInput = stepDetailsInput,
            onStepDetailsChange = onStepDetailsChange,
            onAddStep = onAddStep
        )
    }
}

@Composable
private fun EditorAttachmentPreviewDialog(
    attachment: AttachmentItem,
    accentColor: Color,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    val isImage = attachment.type == AttachmentType.IMAGE ||
        attachment.mimeType.startsWith("image/")

    val bitmap = remember(attachment.uriOrUrl, isImage) {
        if (!isImage) null
        else {
            try {
                val file = File(attachment.uriOrUrl)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                } else null
            } catch (_: Throwable) {
                null
            }
        }
    }

    val textPreview = remember(attachment.uriOrUrl, attachment.mimeType) {
        if (attachment.mimeType.startsWith("text/") ||
            attachment.name.endsWith(".txt", true) ||
            attachment.name.endsWith(".md", true) ||
            attachment.name.endsWith(".json", true)
        ) {
            try {
                val file = File(attachment.uriOrUrl)
                if (file.exists() && file.length() < 128 * 1024) {
                    file.readText().take(2000)
                } else null
            } catch (_: Throwable) {
                null
            }
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = vaultColors.elevatedSurface,
        titleContentColor = vaultColors.primaryText,
        textContentColor = vaultColors.secondaryText,
        title = {
            Text(
                text = attachment.name.ifBlank { attachment.type.label },
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = attachment.name,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else if (textPreview != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(vaultColors.surface)
                            .border(1.dp, vaultColors.border, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = textPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.primaryText
                        )
                    }
                }
                if (attachment.sizeBytes > 0L) {
                    Text(
                        text = "Size: ${AttachmentStorageManager.formatFileSize(attachment.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = vaultColors.secondaryText
                    )
                }
                if (attachment.mimeType.isNotBlank()) {
                    Text(
                        text = "Format: ${attachment.mimeType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = vaultColors.secondaryText
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onOpenExternal()
                },
                modifier = Modifier.testTag("editor_attachment_preview_open_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open", color = accentColor, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = vaultColors.secondaryText)
            }
        }
    )
}

@Composable
private fun ToggleChipButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val vaultColors = IdeaVaultTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .testTag(testTag)
            .semantics { role = Role.Checkbox }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (active) activeColor else vaultColors.surface)
                .border(
                    width = 1.dp,
                    color = if (active) Color.Transparent else vaultColors.border,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) vaultColors.darkInk else vaultColors.secondaryText,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (active) vaultColors.darkInk else vaultColors.secondaryText
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VaultTextFieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    isError: Boolean = false,
    testTag: String? = null
) {
    val vaultColors = IdeaVaultTheme.colors
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .heightIn(min = (minLines * 26 + 24).dp)
            .clip(RoundedCornerShape(16.dp))
            .background(vaultColors.surface)
            .border(
                width = 1.dp,
                color = if (isError) vaultColors.coral else vaultColors.border,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.TopStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = vaultColors.secondaryText.copy(alpha = 0.7f)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            // Do not request scrolling for every typed character. A multiline
            // field can be taller than the visible area when the IME is open;
            // re-requesting the whole container repeatedly causes scroll jitter
            // and can move the caret away from the user's chosen position.
            // The focused field is brought into view on focus below, while
            // Compose's BasicTextField manages caret visibility during typing.
            singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = vaultColors.primaryText,
                lineHeight = 22.sp
            ),
            cursorBrush = SolidColor(vaultColors.actionYellow),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusEvent { focusState ->
                    if (focusState.isFocused) {
                        scope.launch {
                            delay(60L)
                            bringIntoViewRequester.bringIntoView()
                        }
                    }
                }
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        )
    }
}
