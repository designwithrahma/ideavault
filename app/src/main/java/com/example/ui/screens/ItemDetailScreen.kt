package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.AttachmentItem
import com.example.data.AttachmentStorageManager
import com.example.data.AttachmentType
import com.example.data.CustomTypeEntity
import com.example.data.FolderEntity
import com.example.data.IdeaStatus
import com.example.data.MAIN_METHOD_ROUTE_ID
import com.example.data.MethodRouteWithSteps
import com.example.data.MethodStepEntity
import com.example.data.RichBlockType
import com.example.data.RichContentBlock
import com.example.data.SmartImportExportEngine
import com.example.data.VaultCategory
import com.example.data.VaultItemWithDetails
import com.example.ui.components.CategoryPillBadge
import com.example.ui.components.CopyForAiDialog
import com.example.ui.components.CustomFieldsDetailSection
import com.example.ui.components.RichTextContentRenderer
import com.example.ui.components.RichTextSerializer
import com.example.ui.components.VaultAttachmentCard
import com.example.ui.components.VaultTagChip
import com.example.ui.components.accessibleTextColorForBackground
import com.example.ui.components.copyTextToSystemClipboard
import com.example.ui.components.formatVaultDateTime
import com.example.ui.components.parseHexColorSafe
import com.example.ui.theme.IdeaVaultTheme
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemDetailScreen(
    item: VaultItemWithDetails,
    folders: List<FolderEntity>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveToFolder: (String?) -> Unit,
    onToggleArchive: () -> Unit,
    onMoveToTrash: () -> Unit,
    onUpdateIdeaStatus: (IdeaStatus) -> Unit,
    onToggleFollowStep: (String) -> Unit,
    onResetFollowSteps: () -> Unit,
    onReorderSteps: (List<String>) -> Unit,
    onToggleChecklistBlock: (String) -> Unit,
    onAddQuickRichBlock: (RichContentBlock) -> Unit,
    onAddUriAttachment: (Uri, AttachmentType?) -> Unit,
    onAddLinkAttachment: (String, String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onShowMessage: (String) -> Unit,
    onToggleFollowStepForRoute: (String, String) -> Unit = { _, stepId -> onToggleFollowStep(stepId) },
    onResetFollowStepsForRoute: (String) -> Unit = { _ -> onResetFollowSteps() },
    onReorderStepsForRoute: (String, List<String>) -> Unit = { _, ids -> onReorderSteps(ids) },
    customTypes: List<CustomTypeEntity> = emptyList(),
    onUpdateCustomFields: (Map<String, String>) -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val vaultColors = IdeaVaultTheme.colors
    val category = item.categoryEnum
    val methodRoutes = item.effectiveMethodRoutes

    var selectedRouteId by rememberSaveable(item.entity.id) {
        mutableStateOf(MAIN_METHOD_ROUTE_ID)
    }
    val activeMethodRoute = methodRoutes.firstOrNull { it.id == selectedRouteId } ?: methodRoutes.first()

    var followModeActive by rememberSaveable(item.entity.id) {
        mutableStateOf(
            category == VaultCategory.METHOD &&
                (item.completedFollowStepIds.isNotEmpty() || methodRoutes.any { it.completedStepIds.isNotEmpty() })
        )
    }
    var showMoveFolderDialog by remember { mutableStateOf(false) }
    var showTrashConfirmDialog by remember { mutableStateOf(false) }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var showQuickAddBlockDialog by remember { mutableStateOf<RichBlockType?>(null) }
    var previewAttachment by remember { mutableStateOf<AttachmentItem?>(null) }
    var showActionsSheet by rememberSaveable { mutableStateOf(false) }
    var showLegacySteps by rememberSaveable(item.entity.id) { mutableStateOf(false) }
    var showCopyForAiDialog by rememberSaveable { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAddUriAttachment(uri, AttachmentType.IMAGE)
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onAddUriAttachment(uri, null)
        }
    }

    Scaffold(
        containerColor = vaultColors.background,
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(vaultColors.background),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.surface)
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = vaultColors.primaryText
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(vaultColors.surface)
                                .testTag("detail_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (item.entity.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Toggle Favorite",
                                tint = if (item.entity.isFavorite) vaultColors.pink else vaultColors.primaryText
                            )
                        }

                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(vaultColors.surface)
                                .testTag("detail_edit_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit Entry",
                                tint = vaultColors.primaryText
                            )
                        }

                        IconButton(
                            onClick = { showActionsSheet = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(vaultColors.surface)
                                .testTag("detail_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More Actions",
                                tint = vaultColors.primaryText
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .testTag("detail_content_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 56.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Category Badge, Pinned Indicator, Title & Metadata
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Audit P2-10 & P2-16: Use FlowRow with single-line ellipsis and accessible badge contrast
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CategoryPillBadge(category = category)
                            item.customType?.let { ct ->
                                val ctAccent = parseHexColorSafe(ct.colorHex, vaultColors.lavender)
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = ctAccent,
                                    modifier = Modifier.testTag("detail_custom_type_badge")
                                ) {
                                    Text(
                                        text = ct.name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = accessibleTextColorForBackground(ctAccent),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            if (!item.folderName.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = vaultColors.elevatedSurface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Folder,
                                            contentDescription = null,
                                            tint = vaultColors.secondaryText,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = item.folderName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = vaultColors.primaryText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (item.entity.isPinned) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = vaultColors.elevatedSurface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PushPin,
                                            contentDescription = "Pinned",
                                            tint = vaultColors.yellow,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Pinned",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = vaultColors.yellow,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = item.entity.title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                lineHeight = 32.sp
                            ),
                            color = vaultColors.primaryText
                        )

                    if (item.entity.description.isNotBlank()) {
                        RichTextContentRenderer(
                            rawContent = item.entity.description,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                            textColor = vaultColors.secondaryText,
                            accentColor = category.cardColor
                        )
                    }

                    Text(
                        text = "Updated ${formatVaultDateTime(item.entity.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = vaultColors.secondaryText
                    )

                    // Quick Copy & Reuse Bar (Workflow C)
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_copy_reuse_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (category == VaultCategory.PROMPT) {
                            item {
                                DetailCopyActionChip(
                                    label = "Copy Prompt",
                                    testTag = "detail_copy_prompt_chip",
                                    isPrimary = true,
                                    onClick = {
                                        if (copyToClipboard(context, "Prompt", item.entity.content)) {
                                            onShowMessage("Prompt copied to clipboard")
                                        } else {
                                            onShowMessage("Unable to access system clipboard")
                                        }
                                    }
                                )
                            }
                        }
                        item {
                            DetailCopyActionChip(
                                label = "Copy All",
                                testTag = "detail_copy_all_button",
                                isPrimary = category != VaultCategory.PROMPT,
                                onClick = {
                                    val copied = SmartImportExportEngine.formatCopyAll(item)
                                    if (copyToClipboard(context, item.entity.title, copied)) {
                                        onShowMessage("Entry copied to clipboard")
                                    } else {
                                        onShowMessage("Unable to access system clipboard")
                                    }
                                }
                            )
                        }
                        item {
                            DetailCopyActionChip(
                                label = "Copy for AI",
                                testTag = "detail_copy_for_ai_button",
                                onClick = { showCopyForAiDialog = true }
                            )
                        }
                        item {
                            DetailCopyActionChip(
                                label = "Copy JSON",
                                testTag = "detail_copy_json_button",
                                onClick = {
                                    val json = SmartImportExportEngine.formatCopyAsJson(item)
                                    if (copyToClipboard(context, "${item.entity.title} JSON", json)) {
                                        onShowMessage("JSON copied to clipboard")
                                    } else {
                                        onShowMessage("Unable to access system clipboard")
                                    }
                                }
                            )
                        }
                        item {
                            DetailCopyActionChip(
                                label = "Copy Markdown",
                                testTag = "detail_copy_markdown_button",
                                onClick = {
                                    val md = SmartImportExportEngine.formatCopyAsMarkdown(item)
                                    if (copyToClipboard(context, "${item.entity.title} Markdown", md)) {
                                        onShowMessage("Markdown copied to clipboard")
                                    } else {
                                        onShowMessage("Unable to access system clipboard")
                                    }
                                }
                            )
                        }
                        item {
                            DetailCopyActionChip(
                                label = "Copy Title",
                                testTag = "detail_copy_title_button",
                                onClick = {
                                    if (copyToClipboard(context, "Title", item.entity.title)) {
                                        onShowMessage("Title copied to clipboard")
                                    } else {
                                        onShowMessage("Unable to access system clipboard")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 2. Idea Status Selector (only for IDEA category)
            if (category == VaultCategory.IDEA) {
                item {
                    IdeaStatusCard(
                        currentStatus = item.statusEnum,
                        onStatusSelected = onUpdateIdeaStatus
                    )
                }
            }

            // 3. Quick Info Card (only when meaningful metadata exists; NEVER show hardcoded "Native / Offline Vault")
            if (item.entity.platform.isNotBlank() ||
                item.entity.estimatedTime.isNotBlank() ||
                (category == VaultCategory.METHOD && item.steps.isNotEmpty())
            ) {
                item {
                    QuickInfoCard(item = item)
                }
            }

            // 4. Tags
            if (item.tags.isNotEmpty()) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item.tags.forEach { tag ->
                            VaultTagChip(tag = tag)
                        }
                    }
                }
            }

            // 4b. Custom Type Dynamic Fields Section
            if (item.customType != null) {
                item(key = "detail_custom_fields_${item.customType.id}") {
                    CustomFieldsDetailSection(
                        customType = item.customType,
                        customFieldsMap = item.customFieldsMap,
                        onUpdateCustomFields = { updatedMap ->
                            onUpdateCustomFields(updatedMap)
                        },
                        onCopyRawCode = { fieldLabel, rawCode ->
                            if (copyToClipboard(context, fieldLabel, rawCode)) {
                                onShowMessage("$fieldLabel copied to clipboard")
                            } else {
                                onShowMessage("Unable to access system clipboard")
                            }
                        },
                        onOpenUrl = { url ->
                            val att = AttachmentItem(
                                id = "url",
                                type = AttachmentType.LINK,
                                name = url,
                                uriOrUrl = url
                            )
                            val opened = AttachmentStorageManager(context).openAttachmentExternally(att)
                            if (!opened) {
                                if (copyToClipboard(context, "URL", url)) {
                                    onShowMessage("URL copied to clipboard")
                                } else {
                                    onShowMessage("Unable to access system clipboard")
                                }
                            }
                        }
                    )
                }
            }

            // 5. Category-Specific Main Rich Text Content & Actions (Issues 02, 03, 08)
            if (item.entity.content.isNotBlank()) {
                item {
                    CategoryMainContentSection(
                        item = item,
                        onCopyPlainText = { label, rawText ->
                            val textToCopy = if (category == VaultCategory.PROMPT) {
                                rawText
                            } else {
                                RichTextSerializer.toPlainText(rawText)
                            }
                            if (copyToClipboard(context, label, textToCopy)) {
                                onShowMessage("$label copied to clipboard")
                            } else {
                                onShowMessage("Unable to access system clipboard")
                            }
                        }
                    )
                }
            }

            // 6. Structured Rich Content Blocks (Checklists, Code Blocks, Quotes, etc.)
            if (item.richBlocks.isNotEmpty()) {
                item {
                    StructuredRichBlocksCard(
                        blocks = item.richBlocks,
                        accentColor = category.cardColor,
                        onToggleChecklist = onToggleChecklistBlock,
                        onCopyCode = { code ->
                            if (copyToClipboard(context, "Code Snippet", code)) {
                                onShowMessage("Code snippet copied")
                            } else {
                                onShowMessage("Unable to access system clipboard")
                            }
                        }
                    )
                }
            }

            // 7. Method Steps (Full interactive steps for METHOD; Collapsible legacy steps for non-METHOD)
            if (category == VaultCategory.METHOD && methodRoutes.any { it.steps.isNotEmpty() }) {
                item(key = "detail_method_steps_header") {
                    MethodStepsDetailHeader(
                        routes = methodRoutes,
                        activeRoute = activeMethodRoute,
                        hasAlternativeMethods = item.hasAlternativeMethods,
                        onSelectRoute = { selectedRouteId = it },
                        followModeActive = followModeActive,
                        onToggleFollowMode = { followModeActive = !followModeActive },
                        onResetFollowProgress = {
                            onResetFollowStepsForRoute(activeMethodRoute.id)
                        }
                    )
                }
                itemsIndexed(
                    items = activeMethodRoute.steps,
                    key = { idx, step -> "detail_step_${activeMethodRoute.id}_${step.id}_$idx" }
                ) { idx, step ->
                    MethodStepDetailCard(
                        idx = idx,
                        step = step,
                        isCompleted = activeMethodRoute.completedStepIds.contains(step.id),
                        followModeActive = followModeActive,
                        onToggleStepCompleted = { stepId ->
                            onToggleFollowStepForRoute(activeMethodRoute.id, stepId)
                        }
                    )
                }
            } else if (category != VaultCategory.METHOD && item.steps.isNotEmpty()) {
                item {
                    LegacyStepsCollapsibleSection(
                        steps = item.steps,
                        expanded = showLegacySteps,
                        onToggleExpanded = { showLegacySteps = !showLegacySteps }
                    )
                }
            }

            // 8. Additional Notes Card
            if (item.entity.notes.isNotBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = vaultColors.surface,
                        border = BorderStroke(1.dp, vaultColors.subtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Notes & Takeaways",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (vaultColors.isDark) vaultColors.yellow else vaultColors.primaryText
                            )
                            RichTextContentRenderer(
                                rawContent = item.entity.notes,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                textColor = vaultColors.primaryText,
                                accentColor = vaultColors.yellow
                            )
                        }
                    }
                }
            }

            // 9. Attachments & Links Section (Issue 09)
            item {
                AttachmentsDetailSection(
                    attachments = item.attachments,
                    onAddPhoto = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onAddDocument = {
                        documentPickerLauncher.launch(arrayOf("*/*"))
                    },
                    onAddLink = { showAddLinkDialog = true },
                    onPreviewAttachment = { att -> previewAttachment = att },
                    onOpenExternal = { att ->
                        val opened = AttachmentStorageManager(context).openAttachmentExternally(att)
                        if (!opened) {
                            onShowMessage("No installed app could open ${att.name}")
                        }
                    },
                    onRemoveAttachment = onRemoveAttachment
                )
            }
            }
        }
    }

    // Issue 04: Contextual Overflow Bottom Sheet replacing the obstructive floating bottom bar
    if (showActionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showActionsSheet = false },
            sheetState = sheetState,
            containerColor = vaultColors.surface,
            contentColor = vaultColors.primaryText,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier.testTag("detail_actions_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = item.entity.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${category.singularLabel} Actions",
                            style = MaterialTheme.typography.labelSmall,
                            color = vaultColors.secondaryText
                        )
                    }
                    CategoryPillBadge(category = category)
                }

                HorizontalDivider(color = vaultColors.subtleBorder)

                // Quick Content Actions (moved from the old floating bottom bar)
                DetailSheetActionRow(
                    icon = Icons.AutoMirrored.Filled.Notes,
                    iconTint = vaultColors.yellow,
                    label = "Add Quick Note Block",
                    subtitle = "Append a formatted note section to this entry",
                    testTag = "sheet_action_add_note",
                    onClick = {
                        showActionsSheet = false
                        showQuickAddBlockDialog = RichBlockType.PARAGRAPH
                    }
                )

                DetailSheetActionRow(
                    icon = Icons.Filled.Checklist,
                    iconTint = vaultColors.lime,
                    label = "Add Checklist Item",
                    subtitle = "Add an interactive checkbox item to this entry",
                    testTag = "sheet_action_add_checklist",
                    onClick = {
                        showActionsSheet = false
                        showQuickAddBlockDialog = RichBlockType.CHECKLIST
                    }
                )

                // Audit P2-01 & P3-D: For PROMPT entries, "Copy Prompt" copies ONLY raw prompt content (item.entity.content)
                if (category == VaultCategory.PROMPT) {
                    DetailSheetActionRow(
                        icon = Icons.Filled.ContentCopy,
                        iconTint = vaultColors.coral,
                        label = "Copy Prompt",
                        subtitle = "Copy exact raw prompt text without headers or notes",
                        testTag = "sheet_action_copy_entry",
                        onClick = {
                            showActionsSheet = false
                            if (copyToClipboard(context, "Prompt", item.entity.content)) {
                                onShowMessage("Prompt copied to clipboard")
                            } else {
                                onShowMessage("Unable to access system clipboard")
                            }
                        }
                    )
                    DetailSheetActionRow(
                        icon = Icons.Filled.ContentCopy,
                        iconTint = vaultColors.yellow,
                        label = "Copy All (with Title & Notes)",
                        subtitle = "Copy complete entry including title, description, prompt, and notes",
                        testTag = "sheet_action_copy_all_full",
                        onClick = {
                            showActionsSheet = false
                            val fullCopy = SmartImportExportEngine.formatCopyAll(item)
                            if (copyToClipboard(context, item.entity.title, fullCopy)) {
                                onShowMessage("Copied to clipboard")
                            } else {
                                onShowMessage("Unable to access system clipboard")
                            }
                        }
                    )
                } else {
                    DetailSheetActionRow(
                        icon = Icons.Filled.ContentCopy,
                        iconTint = vaultColors.coral,
                        label = "Copy All",
                        subtitle = "Copy complete formatted entry text",
                        testTag = "sheet_action_copy_entry",
                        onClick = {
                            showActionsSheet = false
                            val fullCopy = SmartImportExportEngine.formatCopyAll(item)
                            if (copyToClipboard(context, item.entity.title, fullCopy)) {
                                onShowMessage("Copied to clipboard")
                            } else {
                                onShowMessage("Unable to access system clipboard")
                            }
                        }
                    )
                }

                DetailSheetActionRow(
                    icon = Icons.Filled.ContentCopy,
                    iconTint = vaultColors.actionYellow,
                    label = "Copy for AI",
                    subtitle = "Generate a ready-to-paste AI prompt with this entry",
                    testTag = "sheet_action_copy_for_ai",
                    onClick = {
                        showActionsSheet = false
                        showCopyForAiDialog = true
                    }
                )

                DetailSheetActionRow(
                    icon = Icons.Filled.ContentCopy,
                    iconTint = vaultColors.lime,
                    label = "Copy as JSON",
                    subtitle = "Portable IDEAVAULT JSON for round-trip import",
                    testTag = "sheet_action_copy_json",
                    onClick = {
                        showActionsSheet = false
                        val json = SmartImportExportEngine.formatCopyAsJson(item)
                        if (copyToClipboard(context, "${item.entity.title} JSON", json)) {
                            onShowMessage("JSON copied to clipboard")
                        } else {
                            onShowMessage("Unable to access system clipboard")
                        }
                    }
                )

                DetailSheetActionRow(
                    icon = Icons.Filled.ContentCopy,
                    iconTint = vaultColors.lavender,
                    label = "Copy as Markdown",
                    subtitle = "Structured Markdown with headings, steps, and tags",
                    testTag = "sheet_action_copy_markdown",
                    onClick = {
                        showActionsSheet = false
                        val md = SmartImportExportEngine.formatCopyAsMarkdown(item)
                        if (copyToClipboard(context, "${item.entity.title} Markdown", md)) {
                            onShowMessage("Markdown copied to clipboard")
                        } else {
                            onShowMessage("Unable to access system clipboard")
                        }
                    }
                )

                DetailSheetActionRow(
                    icon = Icons.Filled.ContentCopy,
                    iconTint = vaultColors.secondaryText,
                    label = "Copy Title",
                    subtitle = "Copy only the entry title",
                    testTag = "sheet_action_copy_title",
                    onClick = {
                        showActionsSheet = false
                        if (copyToClipboard(context, "Title", item.entity.title)) {
                            onShowMessage("Title copied to clipboard")
                        } else {
                            onShowMessage("Unable to access system clipboard")
                        }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = vaultColors.subtleBorder
                )

                DetailSheetActionRow(
                    icon = if (item.entity.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    iconTint = vaultColors.yellow,
                    label = if (item.entity.isPinned) "Unpin from Home" else "Pin to Home",
                    testTag = "sheet_action_toggle_pin",
                    onClick = {
                        showActionsSheet = false
                        onTogglePin()
                    }
                )

                DetailSheetActionRow(
                    icon = Icons.Filled.Edit,
                    iconTint = vaultColors.primaryText,
                    label = "Edit Entry",
                    testTag = "sheet_action_edit",
                    onClick = {
                        showActionsSheet = false
                        onEdit()
                    }
                )

                DetailSheetActionRow(
                    icon = Icons.Filled.ContentCopy,
                    iconTint = vaultColors.lavender,
                    label = "Duplicate Entry",
                    testTag = "sheet_action_duplicate",
                    onClick = {
                        showActionsSheet = false
                        onDuplicate()
                    }
                )

                DetailSheetActionRow(
                    icon = Icons.Outlined.DriveFileMove,
                    iconTint = vaultColors.pink,
                    label = "Move to Folder",
                    testTag = "sheet_action_move_folder",
                    onClick = {
                        showActionsSheet = false
                        showMoveFolderDialog = true
                    }
                )

                DetailSheetActionRow(
                    icon = Icons.Outlined.Share,
                    iconTint = vaultColors.primaryText,
                    label = "Share Entry",
                    testTag = "sheet_action_share",
                    onClick = {
                        showActionsSheet = false
                        shareVaultItem(context, item)
                    }
                )

                DetailSheetActionRow(
                    icon = Icons.Filled.Archive,
                    iconTint = vaultColors.secondaryText,
                    label = if (item.entity.isArchived) "Unarchive Entry" else "Archive Entry",
                    testTag = "sheet_action_archive",
                    onClick = {
                        showActionsSheet = false
                        onToggleArchive()
                    }
                )

                DetailSheetActionRow(
                    icon = Icons.Filled.DeleteOutline,
                    iconTint = vaultColors.coral,
                    label = "Move to Trash",
                    testTag = "sheet_action_trash",
                    onClick = {
                        showActionsSheet = false
                        showTrashConfirmDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showMoveFolderDialog) {
        MoveToFolderDialog(
            folders = folders,
            currentFolderId = item.entity.folderId,
            onDismiss = { showMoveFolderDialog = false },
            onSelectFolder = { folderId ->
                onMoveToFolder(folderId)
                showMoveFolderDialog = false
            }
        )
    }

    if (showTrashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showTrashConfirmDialog = false },
            containerColor = vaultColors.surface,
            titleContentColor = vaultColors.primaryText,
            textContentColor = vaultColors.secondaryText,
            title = { Text("Move to Trash?") },
            text = {
                Text(
                    "\"${item.entity.title}\" will be moved to Trash. You can restore it anytime from Settings > Trash."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTrashConfirmDialog = false
                        onMoveToTrash()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.coral,
                        contentColor = Color(0xFF141414)
                    )
                ) {
                    Text("Move to Trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTrashConfirmDialog = false }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    if (showAddLinkDialog) {
        AddLinkAttachmentDialog(
            onDismiss = { showAddLinkDialog = false },
            onConfirm = { title, url ->
                onAddLinkAttachment(title, url)
                showAddLinkDialog = false
            }
        )
    }

    showQuickAddBlockDialog?.let { blockType ->
        QuickAddRichBlockDialog(
            blockType = blockType,
            onDismiss = { showQuickAddBlockDialog = null },
            onConfirm = { title, body ->
                onAddQuickRichBlock(
                    RichContentBlock(
                        id = UUID.randomUUID().toString(),
                        type = blockType,
                        title = title,
                        content = body,
                        checked = false
                    )
                )
                showQuickAddBlockDialog = null
            }
        )
    }

    previewAttachment?.let { att ->
        AttachmentPreviewDialog(
            attachment = att,
            onDismiss = { previewAttachment = null },
            onOpenExternal = {
                val opened = AttachmentStorageManager(context).openAttachmentExternally(att)
                if (!opened) {
                    onShowMessage("No installed app could open ${att.name}")
                }
            }
        )
    }

    if (showCopyForAiDialog) {
        CopyForAiDialog(
            item = item,
            onDismiss = { showCopyForAiDialog = false },
            onCopied = {
                showCopyForAiDialog = false
                onShowMessage("Copied for AI to clipboard")
            }
        )
    }
}

@Composable
private fun DetailCopyActionChip(
    label: String,
    testTag: String,
    isPrimary: Boolean = false,
    onClick: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (isPrimary) vaultColors.actionYellow else vaultColors.secondarySurface,
            border = if (isPrimary) null else BorderStroke(1.dp, vaultColors.border),
            modifier = Modifier.height(36.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = label,
                    tint = if (isPrimary) vaultColors.darkInk else vaultColors.secondaryText,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isPrimary) vaultColors.darkInk else vaultColors.primaryText
                )
            }
        }
    }
}

@Composable
private fun CategoryMainContentSection(
    item: VaultItemWithDetails,
    onCopyPlainText: (label: String, rawText: String) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    val category = item.categoryEnum

    val sectionLabel = when (category) {
        VaultCategory.PROMPT -> "PROMPT"
        VaultCategory.IDEA -> "IDEA DETAILS"
        VaultCategory.METHOD -> "METHOD OVERVIEW & NOTES"
        VaultCategory.KNOWLEDGE -> "KNOWLEDGE NOTES"
        VaultCategory.REFERENCE -> "REFERENCE DETAILS"
        VaultCategory.INBOX -> "NOTE DETAILS"
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = vaultColors.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (category == VaultCategory.PROMPT) {
                vaultColors.yellow.copy(alpha = 0.45f)
            } else {
                vaultColors.subtleBorder
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detail_main_content_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sectionLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = category.cardColor
                )

                // Issue 03: Show "Copy Prompt" ONLY for Prompt entries!
                if (category == VaultCategory.PROMPT) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 48.dp)
                            .clickable { onCopyPlainText("Prompt", item.entity.content) }
                            .testTag("copy_prompt_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = vaultColors.yellow
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy Prompt",
                                    tint = Color(0xFF141414),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Copy Prompt",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF141414)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(min = 48.dp)
                            .clickable { onCopyPlainText("Text", item.entity.content) }
                            .testTag("copy_text_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = vaultColors.elevatedSurface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy Text",
                                    tint = vaultColors.primaryText,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Copy Text",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = vaultColors.primaryText
                                )
                            }
                        }
                    }
                }
            }

            // Render rich text cleanly for Idea/Method/Note, or preserve literal prompt syntax for Prompt
            SelectionContainer {
                if (category == VaultCategory.PROMPT) {
                    Text(
                        text = item.entity.content,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 24.sp
                        ),
                        color = vaultColors.primaryText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_rendered_rich_text")
                    )
                } else {
                    RichTextContentRenderer(
                        rawContent = item.entity.content,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_rendered_rich_text"),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                        textColor = vaultColors.primaryText,
                        accentColor = category.cardColor
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSheetActionRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    subtitle: String? = null,
    testTag: String,
    onClick: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(vaultColors.elevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = vaultColors.primaryText
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.secondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun IdeaStatusCard(
    currentStatus: IdeaStatus,
    onStatusSelected: (IdeaStatus) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = vaultColors.surface,
        border = BorderStroke(1.dp, vaultColors.subtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Idea Status",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = vaultColors.secondaryText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IdeaStatus.entries.forEach { status ->
                    val selected = status == currentStatus
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable { onStatusSelected(status) }
                            .testTag("detail_idea_status_${status.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (selected) status.badgeColor else vaultColors.elevatedSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = status.label,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (selected) Color(0xFF141414) else vaultColors.secondaryText,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickInfoCard(item: VaultItemWithDetails) {
    val vaultColors = IdeaVaultTheme.colors
    val category = item.categoryEnum
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = vaultColors.surface,
        border = BorderStroke(1.dp, vaultColors.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detail_quick_info_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quick Info",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = vaultColors.secondaryText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (item.entity.platform.isNotBlank()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (category == VaultCategory.PROMPT) "AI Model / Platform" else "Tools / Platform",
                            style = MaterialTheme.typography.labelSmall,
                            color = vaultColors.secondaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.entity.platform,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText
                        )
                    }
                }
                if (item.entity.estimatedTime.isNotBlank()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Estimated Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = vaultColors.secondaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.entity.estimatedTime,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.lime
                        )
                    }
                }
                if (category == VaultCategory.METHOD && item.steps.isNotEmpty()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Steps",
                            style = MaterialTheme.typography.labelSmall,
                            color = vaultColors.secondaryText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${item.steps.size} Steps",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StructuredRichBlocksCard(
    blocks: List<RichContentBlock>,
    accentColor: Color,
    onToggleChecklist: (String) -> Unit,
    onCopyCode: (String) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = vaultColors.surface,
        border = BorderStroke(1.dp, vaultColors.subtleBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Structured Blocks",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = vaultColors.secondaryText
            )

            blocks.forEachIndexed { index, block ->
                when (block.type) {
                    RichBlockType.HEADING -> {
                        Text(
                            text = block.title.ifBlank { block.content },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText
                        )
                    }

                    RichBlockType.PARAGRAPH -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (block.title.isNotBlank()) {
                                Text(
                                    text = block.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = vaultColors.primaryText
                                )
                            }
                            RichTextContentRenderer(
                                rawContent = block.content,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                textColor = vaultColors.primaryText,
                                accentColor = accentColor
                            )
                        }
                    }

                    RichBlockType.BULLET_LIST -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.titleMedium,
                                color = accentColor
                            )
                            Text(
                                text = listOf(block.title, block.content).filter { it.isNotBlank() }.joinToString(" — "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = vaultColors.primaryText
                            )
                        }
                    }

                    RichBlockType.NUMBERED_LIST -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelLarge,
                                color = accentColor
                            )
                            Text(
                                text = listOf(block.title, block.content).filter { it.isNotBlank() }.joinToString(" — "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = vaultColors.primaryText
                            )
                        }
                    }

                    RichBlockType.CHECKLIST -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = vaultColors.elevatedSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleChecklist(block.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = block.checked,
                                    onCheckedChange = { onToggleChecklist(block.id) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = vaultColors.lime,
                                        checkmarkColor = Color(0xFF141414),
                                        uncheckedColor = vaultColors.secondaryText
                                    )
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = block.title.ifBlank { block.content },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        color = if (block.checked) vaultColors.secondaryText else vaultColors.primaryText
                                    )
                                    if (block.title.isNotBlank() && block.content.isNotBlank()) {
                                        Text(
                                            text = block.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = vaultColors.secondaryText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    RichBlockType.CODE_BLOCK -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = vaultColors.elevatedSurface,
                            border = BorderStroke(1.dp, vaultColors.subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = block.title.ifBlank { "CODE" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = vaultColors.lavender
                                    )
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .widthIn(min = 48.dp)
                                            .clickable { onCopyCode(block.content) }
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(
                                            text = "Copy",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = vaultColors.coral
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                SelectionContainer {
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
                        }
                    }

                    RichBlockType.QUOTE -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(vaultColors.elevatedSurface)
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(vaultColors.pink)
                            )
                            Column {
                                Text(
                                    text = "\"${block.content.ifBlank { block.title }}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = vaultColors.primaryText
                                )
                                if (block.title.isNotBlank() && block.content.isNotBlank()) {
                                    Text(
                                        text = "— ${block.title}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = vaultColors.secondaryText
                                    )
                                }
                            }
                        }
                    }

                    RichBlockType.DIVIDER -> {
                        HorizontalDivider(color = vaultColors.subtleBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodStepsDetailHeader(
    routes: List<MethodRouteWithSteps>,
    activeRoute: MethodRouteWithSteps,
    hasAlternativeMethods: Boolean,
    onSelectRoute: (String) -> Unit,
    followModeActive: Boolean,
    onToggleFollowMode: () -> Unit,
    onResetFollowProgress: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    val steps = activeRoute.steps
    val completedStepIds = activeRoute.completedStepIds

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasAlternativeMethods) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = vaultColors.surface,
                border = BorderStroke(1.dp, vaultColors.subtleBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_choose_method_selector")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CHOOSE METHOD",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.secondaryText
                        )
                        Text(
                            text = "${routes.size} routes available",
                            style = MaterialTheme.typography.labelSmall,
                            color = vaultColors.lime
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        routes.forEach { route ->
                            val isSelected = route.id == activeRoute.id
                            val routeCompletedCount = route.completedStepIds.count { id ->
                                route.steps.any { it.id == id }
                            }
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .widthIn(min = 48.dp)
                                    .clickable { onSelectRoute(route.id) }
                                    .testTag("detail_route_chip_${route.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = if (isSelected) vaultColors.lime else vaultColors.elevatedSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) vaultColors.lime else vaultColors.subtleBorder
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = route.name,
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) Color(0xFF141414) else vaultColors.primaryText
                                        )
                                        val badgeLabel = if (followModeActive || routeCompletedCount > 0) {
                                            "$routeCompletedCount/${route.steps.size}"
                                        } else {
                                            "${route.steps.size}"
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(100.dp),
                                            color = if (isSelected) {
                                                Color(0xFF141414).copy(alpha = 0.14f)
                                            } else {
                                                vaultColors.surface
                                            }
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) Color(0xFF141414) else vaultColors.secondaryText,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (activeRoute.description.isNotBlank()) {
                        Text(
                            text = activeRoute.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasAlternativeMethods) activeRoute.name.uppercase() else "METHOD STEPS",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
                val validCompletedCount = completedStepIds.count { id -> steps.any { it.id == id } }
                Text(
                    text = if (followModeActive) {
                        "${activeRoute.name}: $validCompletedCount/${steps.size} steps completed"
                    } else {
                        "${steps.size} sequential steps"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = vaultColors.secondaryText,
                    modifier = Modifier.testTag("detail_follow_progress_text")
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (followModeActive && completedStepIds.isNotEmpty()) {
                    IconButton(
                        onClick = onResetFollowProgress,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.elevatedSurface)
                            .testTag("detail_reset_follow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset Route Progress",
                            tint = vaultColors.primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .clickable { onToggleFollowMode() }
                        .testTag("follow_method_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (followModeActive) vaultColors.lime else vaultColors.elevatedSurface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (followModeActive) Color(0xFF141414) else vaultColors.lime,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (followModeActive) "Following" else "Follow Method",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (followModeActive) Color(0xFF141414) else vaultColors.primaryText
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = followModeActive) {
            val validCompletedCount = completedStepIds.count { id -> steps.any { it.id == id } }
            val progress = if (steps.isEmpty()) 0f else validCompletedCount.toFloat() / steps.size.toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(100.dp)),
                color = vaultColors.lime,
                trackColor = vaultColors.elevatedSurface
            )
        }
    }
}

@Composable
private fun MethodStepDetailCard(
    idx: Int,
    step: MethodStepEntity,
    isCompleted: Boolean,
    followModeActive: Boolean,
    onToggleStepCompleted: (String) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Column {
        run {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (followModeActive && isCompleted) {
                    vaultColors.lime.copy(alpha = 0.14f)
                } else {
                    vaultColors.surface
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (followModeActive && isCompleted) {
                        vaultColors.lime.copy(alpha = 0.45f)
                    } else {
                        vaultColors.subtleBorder
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = followModeActive) {
                        onToggleStepCompleted(step.id)
                    }
                    .testTag("detail_follow_step_$idx")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (followModeActive) {
                        Checkbox(
                            checked = isCompleted,
                            onCheckedChange = { onToggleStepCompleted(step.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = vaultColors.lime,
                                checkmarkColor = Color(0xFF141414),
                                uncheckedColor = vaultColors.secondaryText
                            )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(vaultColors.lime),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF141414)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (followModeActive && isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            color = vaultColors.primaryText
                        )
                        if (step.description.isNotBlank()) {
                            RichTextContentRenderer(
                                rawContent = step.description,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                textColor = vaultColors.secondaryText,
                                accentColor = vaultColors.lime
                            )
                        }
                        if (step.notes.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = vaultColors.elevatedSurface
                            ) {
                                Text(
                                    text = "Tip: ${step.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vaultColors.yellow,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyStepsCollapsibleSection(
    steps: List<MethodStepEntity>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = vaultColors.surface,
        border = BorderStroke(1.dp, vaultColors.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detail_legacy_steps_section")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ATTACHED STEPS (${steps.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = vaultColors.secondaryText
                    )
                    Text(
                        text = "Preserved step items attached to this entry",
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.secondaryText
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse Steps" else "Expand Steps",
                    tint = vaultColors.primaryText
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    steps.forEachIndexed { index, step ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = vaultColors.elevatedSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${index + 1}. ${step.title}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = vaultColors.primaryText
                                )
                                if (step.description.isNotBlank()) {
                                    Text(
                                        text = step.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = vaultColors.secondaryText
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

@Composable
private fun AttachmentsDetailSection(
    attachments: List<AttachmentItem>,
    onAddPhoto: () -> Unit,
    onAddDocument: () -> Unit,
    onAddLink: () -> Unit,
    onPreviewAttachment: (AttachmentItem) -> Unit,
    onOpenExternal: (AttachmentItem) -> Unit,
    onRemoveAttachment: (String) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = vaultColors.surface,
        border = BorderStroke(1.dp, vaultColors.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("detail_attachments_section")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ATTACHMENTS & LINKS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = vaultColors.secondaryText
                    )
                    Text(
                        text = if (attachments.isEmpty()) {
                            "Attach images, PDFs, files, or web links"
                        } else {
                            "${attachments.size} attached item(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.secondaryText
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onAddPhoto,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.elevatedSurface)
                            .testTag("detail_add_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = "Add Photo",
                            tint = vaultColors.coral,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onAddDocument,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.elevatedSurface)
                            .testTag("detail_add_document_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = "Add Document or PDF",
                            tint = vaultColors.lavender,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onAddLink,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.elevatedSurface)
                            .testTag("detail_add_link_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddLink,
                            contentDescription = "Add Link",
                            tint = vaultColors.pink,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    attachments.forEach { att ->
                        VaultAttachmentCard(
                            attachment = att,
                            onPreview = { onPreviewAttachment(att) },
                            onOpenExternal = { onOpenExternal(att) },
                            onRemove = { onRemoveAttachment(att.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onSelectFolder: (String?) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    var selectedId by remember { mutableStateOf(currentFolderId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = vaultColors.surface,
        titleContentColor = vaultColors.primaryText,
        textContentColor = vaultColors.secondaryText,
        title = { Text("Move to Folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = null }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedId == null,
                        onClick = { selectedId = null },
                        colors = RadioButtonDefaults.colors(selectedColor = vaultColors.coral)
                    )
                    Text("No Folder (Root Vault)", color = vaultColors.primaryText)
                }
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = folder.id }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == folder.id,
                            onClick = { selectedId = folder.id },
                            colors = RadioButtonDefaults.colors(selectedColor = vaultColors.coral)
                        )
                        Text(folder.name, color = vaultColors.primaryText)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelectFolder(selectedId) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = vaultColors.coral,
                    contentColor = Color(0xFF141414)
                )
            ) {
                Text("Move")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = vaultColors.secondaryText)
            }
        }
    )
}

@Composable
fun AddLinkAttachmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = vaultColors.surface,
        titleContentColor = vaultColors.primaryText,
        title = { Text("Attach Web Link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Link Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = vaultColors.primaryText,
                        unfocusedTextColor = vaultColors.primaryText,
                        focusedBorderColor = vaultColors.pink,
                        unfocusedBorderColor = vaultColors.subtleBorder
                    )
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = vaultColors.primaryText,
                        unfocusedTextColor = vaultColors.primaryText,
                        focusedBorderColor = vaultColors.pink,
                        unfocusedBorderColor = vaultColors.subtleBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        onConfirm(title.trim().ifBlank { url.trim() }, url.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = vaultColors.pink,
                    contentColor = Color(0xFF141414)
                )
            ) {
                Text("Attach")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = vaultColors.secondaryText)
            }
        }
    )
}

@Composable
private fun QuickAddRichBlockDialog(
    blockType: RichBlockType,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val dialogTitle = when (blockType) {
        RichBlockType.CHECKLIST -> "Add Checklist Item"
        else -> "Add Note Block"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = vaultColors.surface,
        titleContentColor = vaultColors.primaryText,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (blockType == RichBlockType.CHECKLIST) "To-do item" else "Heading (optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = vaultColors.primaryText,
                        unfocusedTextColor = vaultColors.primaryText,
                        focusedBorderColor = vaultColors.lime,
                        unfocusedBorderColor = vaultColors.subtleBorder
                    )
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Details") },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = vaultColors.primaryText,
                        unfocusedTextColor = vaultColors.primaryText,
                        focusedBorderColor = vaultColors.lime,
                        unfocusedBorderColor = vaultColors.subtleBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() || content.isNotBlank()) {
                        onConfirm(title.trim(), content.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = vaultColors.lime,
                    contentColor = Color(0xFF141414)
                )
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = vaultColors.secondaryText)
            }
        }
    )
}

@Composable
fun AttachmentPreviewDialog(
    attachment: AttachmentItem,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = vaultColors.surface,
        titleContentColor = vaultColors.primaryText,
        textContentColor = vaultColors.secondaryText,
        title = {
            Text(
                text = attachment.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val file = File(attachment.uriOrUrl)
                if (attachment.type == AttachmentType.IMAGE && file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = attachment.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(vaultColors.elevatedSurface),
                        contentScale = ContentScale.Crop
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = vaultColors.elevatedSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Type: ${attachment.type.label}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText
                        )
                        if (attachment.mimeType.isNotBlank()) {
                            Text(
                                text = "Format: ${attachment.mimeType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = vaultColors.secondaryText
                            )
                        }
                        if (attachment.sizeBytes > 0L) {
                            Text(
                                text = "Size: ${AttachmentStorageManager.formatFileSize(attachment.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = vaultColors.secondaryText
                            )
                        }
                        if (attachment.type == AttachmentType.LINK) {
                            Text(
                                text = attachment.uriOrUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = vaultColors.pink,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onOpenExternal,
                border = BorderStroke(1.dp, vaultColors.coral)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = null,
                    tint = vaultColors.coral,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (attachment.type == AttachmentType.LINK) "Open Link" else "Open Externally",
                    color = vaultColors.primaryText
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = vaultColors.secondaryText)
            }
        }
    )
}

private fun buildCleanShareText(item: VaultItemWithDetails): String {
    return SmartImportExportEngine.formatCopyAll(item)
}

private fun copyToClipboard(context: Context, label: String, text: String): Boolean {
    return copyTextToSystemClipboard(context, label, text)
}

private fun shareVaultItem(context: Context, item: VaultItemWithDetails) {
    val shareText = buildCleanShareText(item)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, item.entity.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(sendIntent, "Share ${item.entity.title}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
