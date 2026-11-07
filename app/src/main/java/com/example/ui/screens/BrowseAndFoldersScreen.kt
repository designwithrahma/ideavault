package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.FolderEntity
import com.example.data.VaultCategory
import com.example.data.VaultItemWithDetails
import com.example.ui.components.CategoryPillBadge
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultEntryCurvedCard
import com.example.ui.components.VaultFilterChip
import com.example.ui.components.icon
import com.example.ui.theme.IdeaVaultTheme

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.outlined.DriveFileMove

@Composable
fun FoldersListScreen(
    folders: List<FolderEntity>,
    folderCounts: Map<String, Int>,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onCreateFolder: (String, VaultCategory, String) -> Unit,
    onRenameFolder: (FolderEntity, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    allActiveItems: List<VaultItemWithDetails> = emptyList(),
    onOpenItem: (String) -> Unit = {},
    onMoveItemToFolder: (String, String?) -> Unit = { _, _ -> },
    isPrimaryTab: Boolean = false
) {
    BackHandler { onBack() }
    val vaultColors = IdeaVaultTheme.colors

    var showCreateDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var itemToMove by remember { mutableStateOf<VaultItemWithDetails?>(null) }
    var selectedOrgFilter by remember { mutableStateOf("ALL") }

    val uncategorizedItems = remember(allActiveItems) {
        allActiveItems.filter { it.entity.folderId.isNullOrBlank() }
    }
    val legacyItems = remember(allActiveItems) {
        allActiveItems.filter {
            it.categoryEnum == VaultCategory.KNOWLEDGE ||
                it.categoryEnum == VaultCategory.REFERENCE ||
                it.categoryEnum == VaultCategory.INBOX
        }
    }
    val filteredOrgItems = remember(allActiveItems, uncategorizedItems, legacyItems, selectedOrgFilter) {
        when (selectedOrgFilter) {
            "UNCATEGORIZED" -> uncategorizedItems
            "LEGACY" -> legacyItems
            "IDEA" -> allActiveItems.filter { it.categoryEnum == VaultCategory.IDEA }
            "METHOD" -> allActiveItems.filter { it.categoryEnum == VaultCategory.METHOD }
            "PROMPT" -> allActiveItems.filter { it.categoryEnum == VaultCategory.PROMPT }
            "NOTE" -> allActiveItems.filter {
                it.categoryEnum == VaultCategory.KNOWLEDGE ||
                    it.categoryEnum == VaultCategory.REFERENCE ||
                    it.categoryEnum == VaultCategory.INBOX
            }
            else -> allActiveItems
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("folders_list_screen"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!isPrimaryTab) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(vaultColors.surface)
                                    .border(1.dp, vaultColors.border, CircleShape)
                                    .testTag("folders_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = vaultColors.primaryText
                                )
                            }
                        }
                    Column {
                        Text(
                            text = "Collections",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText,
                            modifier = Modifier.testTag("collections_header_title")
                        )
                        Text(
                            text = "${folders.size} collection(s) • ${allActiveItems.size} entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .clickable { showCreateDialog = true }
                        .testTag("create_folder_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = vaultColors.actionYellow
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "New Collection",
                                tint = vaultColors.darkInk,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "New",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = vaultColors.darkInk
                            )
                        }
                    }
                }
            }
        }

        if (folders.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                VaultEmptyState(
                    title = "No Collections Yet",
                    subtitle = "Create folders to group related ideas, methods, prompts, and notes.",
                    icon = Icons.Outlined.CreateNewFolder,
                    accentColor = vaultColors.actionYellow,
                    actionLabel = "Create Collection",
                    onActionClick = { showCreateDialog = true }
                )
            }
        } else {
            items(folders, key = { it.id }) { folder ->
                val count = folderCounts[folder.id] ?: 0
                val cat = VaultCategory.fromId(folder.category)
                val catAccent = vaultColors.categoryCardColor(cat)

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = vaultColors.surface,
                    border = BorderStroke(1.dp, vaultColors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { onOpenFolder(folder.id) }
                        .testTag("folder_row_${folder.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(catAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = vaultColors.darkInk,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = vaultColors.primaryText
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CategoryPillBadge(category = cat)
                                Text(
                                    text = "$count item(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vaultColors.secondaryText
                                )
                            }
                        }

                        IconButton(
                            onClick = { folderToRename = folder },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("rename_folder_${folder.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Rename Collection",
                                tint = vaultColors.secondaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { folderToDelete = folder },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_folder_${folder.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete Collection",
                                tint = vaultColors.coral,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = vaultColors.secondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        if (allActiveItems.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Organized Entries",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText
                        )
                        Text(
                            text = "${filteredOrgItems.size} shown",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            VaultFilterChip(
                                label = "All (${allActiveItems.size})",
                                selected = selectedOrgFilter == "ALL",
                                accentColor = vaultColors.actionYellow,
                                onClick = { selectedOrgFilter = "ALL" }
                            )
                        }
                        item {
                            VaultFilterChip(
                                label = "Uncategorized (${uncategorizedItems.size})",
                                selected = selectedOrgFilter == "UNCATEGORIZED",
                                accentColor = vaultColors.actionYellow,
                                onClick = { selectedOrgFilter = "UNCATEGORIZED" }
                            )
                        }
                        items(VaultCategory.v2PrimaryCategories, key = { it.id }) { cat ->
                            VaultFilterChip(
                                label = cat.label,
                                selected = selectedOrgFilter == cat.id,
                                accentColor = vaultColors.categoryCardColor(cat),
                                onClick = { selectedOrgFilter = cat.id }
                            )
                        }
                        if (legacyItems.isNotEmpty()) {
                            item {
                                VaultFilterChip(
                                    label = "Legacy (${legacyItems.size})",
                                    selected = selectedOrgFilter == "LEGACY",
                                    accentColor = vaultColors.lavender,
                                    onClick = { selectedOrgFilter = "LEGACY" }
                                )
                            }
                        }
                    }
                }
            }

            items(filteredOrgItems, key = { "org_${it.entity.id}" }) { item ->
                val assignedFolder = folders.find { it.id == item.entity.folderId }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = vaultColors.surface,
                    border = BorderStroke(1.dp, vaultColors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenItem(item.entity.id) }
                        .testTag("collection_entry_row_${item.entity.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.entity.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = vaultColors.primaryText
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CategoryPillBadge(category = item.categoryEnum)
                                Text(
                                    text = assignedFolder?.name ?: "Uncategorized",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = vaultColors.secondaryText
                                )
                            }
                        }
                        IconButton(
                            onClick = { itemToMove = item },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("collection_move_item_${item.entity.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DriveFileMove,
                                contentDescription = "Move to Collection",
                                tint = vaultColors.primaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    }

    if (itemToMove != null) {
        val targetItem = itemToMove!!
        AlertDialog(
            onDismissRequest = { itemToMove = null },
            containerColor = vaultColors.surface,
            title = {
                Text(
                    text = "Move to Collection",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Choose a collection for \"${targetItem.entity.title}\":",
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.secondaryText
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (targetItem.entity.folderId == null) vaultColors.actionYellow.copy(alpha = 0.2f) else vaultColors.secondarySurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onMoveItemToFolder(targetItem.entity.id, null)
                                itemToMove = null
                            }
                            .testTag("move_to_uncategorized_option")
                    ) {
                        Text(
                            text = "Uncategorized (No Collection)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = vaultColors.primaryText,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    folders.forEach { folder ->
                        val isCurrent = targetItem.entity.folderId == folder.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isCurrent) vaultColors.actionYellow.copy(alpha = 0.2f) else vaultColors.secondarySurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onMoveItemToFolder(targetItem.entity.id, folder.id)
                                    itemToMove = null
                                }
                                .testTag("move_to_folder_option_${folder.id}")
                        ) {
                            Text(
                                text = folder.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = vaultColors.primaryText,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { itemToMove = null }) {
                    Text("Close", color = vaultColors.secondaryText)
                }
            }
        )
    }

    if (showCreateDialog) {
        var nameInput by remember { mutableStateOf("") }
        var selectedCat by remember { mutableStateOf(VaultCategory.IDEA) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = vaultColors.surface,
            title = {
                Text(
                    text = "New Collection",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Collection Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("folder_name_input")
                    )
                    Text(
                        text = "Primary Category",
                        style = MaterialTheme.typography.labelMedium,
                        color = vaultColors.secondaryText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VaultCategory.v2PrimaryCategories.forEach { cat ->
                            VaultFilterChip(
                                label = cat.singularLabel,
                                selected = selectedCat == cat,
                                accentColor = vaultColors.categoryCardColor(cat),
                                onClick = { selectedCat = cat }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = nameInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onCreateFolder(trimmed, selectedCat, "#EF7B59")
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.actionYellow,
                        contentColor = vaultColors.darkInk
                    )
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    if (folderToRename != null) {
        val target = folderToRename!!
        var renameInput by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            containerColor = vaultColors.surface,
            title = {
                Text(
                    text = "Rename Collection",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_folder_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInput.trim()
                        if (trimmed.isNotEmpty()) {
                            onRenameFolder(target, trimmed)
                            folderToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.actionYellow,
                        contentColor = vaultColors.darkInk
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }

    if (folderToDelete != null) {
        val target = folderToDelete!!
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            containerColor = vaultColors.surface,
            title = {
                Text(
                    text = "Delete Collection?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
            },
            text = {
                Text(
                    text = "Deleting \"${target.name}\" removes the collection label while safely keeping all entries inside your vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = vaultColors.secondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFolder(target.id)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.coral,
                        contentColor = vaultColors.darkInk
                    )
                ) {
                    Text("Delete Collection", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text("Cancel", color = vaultColors.secondaryText)
                }
            }
        )
    }
}

@Composable
fun FolderDetailScreen(
    folder: FolderEntity,
    items: List<VaultItemWithDetails>,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    folders: List<FolderEntity> = emptyList(),
    onMoveItemToFolder: (String, String?) -> Unit = { _, _ -> }
) {
    BackHandler { onBack() }
    val vaultColors = IdeaVaultTheme.colors
    val cat = VaultCategory.fromId(folder.category)
    var itemToMove by remember { mutableStateOf<VaultItemWithDetails?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("folder_detail_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.surface)
                            .border(1.dp, vaultColors.border, CircleShape)
                            .testTag("folder_detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = vaultColors.primaryText
                        )
                    }
                    Column {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText
                        )
                        Text(
                            text = "${items.size} saved item(s) • ${cat.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    VaultEmptyState(
                        title = "Collection Is Empty",
                        subtitle = "Assign any Idea, Method, Prompt, or Note to \"${folder.name}\" from Collections, the editor, or the detail menu.",
                        icon = Icons.Outlined.FolderOpen
                    )
                }
            } else {
                items(items, key = { it.entity.id }) { item ->
                    VaultEntryCurvedCard(
                        item = item,
                        onClick = { onOpenItem(item.entity.id) },
                        onToggleFavorite = { onToggleFavorite(item.entity.id) }
                    )
                }
            }
        }
    }

    if (itemToMove != null) {
        val targetItem = itemToMove!!
        AlertDialog(
            onDismissRequest = { itemToMove = null },
            containerColor = vaultColors.surface,
            title = {
                Text(
                    text = "Move Entry",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = vaultColors.secondarySurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onMoveItemToFolder(targetItem.entity.id, null)
                                itemToMove = null
                            }
                    ) {
                        Text(
                            text = "Remove from \"${folder.name}\"",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = vaultColors.primaryText,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    folders.filter { it.id != folder.id }.forEach { destFolder ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = vaultColors.secondarySurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onMoveItemToFolder(targetItem.entity.id, destFolder.id)
                                    itemToMove = null
                                }
                        ) {
                            Text(
                                text = "Move to ${destFolder.name}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = vaultColors.primaryText,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { itemToMove = null }) {
                    Text("Close", color = vaultColors.secondaryText)
                }
            }
        )
    }
}

@Composable
fun CategoryItemsScreen(
    category: VaultCategory,
    items: List<VaultItemWithDetails>,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onCreateInCategory: (VaultCategory) -> Unit
) {
    BackHandler { onBack() }
    val vaultColors = IdeaVaultTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("category_items_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(vaultColors.surface)
                                .border(1.dp, vaultColors.border, CircleShape)
                                .testTag("category_items_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = vaultColors.primaryText
                            )
                        }
                        Column {
                            Text(
                                text = category.label,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = vaultColors.primaryText
                            )
                            Text(
                                text = "${items.size} saved item(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = vaultColors.secondaryText
                            )
                        }
                    }

                    IconButton(
                        onClick = { onCreateInCategory(category) },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.actionYellow)
                            .testTag("category_items_create_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Create ${category.singularLabel}",
                            tint = vaultColors.darkInk
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    VaultEmptyState(
                        title = "No ${category.label} Yet",
                        subtitle = category.creationSubtitle,
                        icon = category.icon(),
                        accentColor = vaultColors.categoryCardColor(category),
                        actionLabel = "Create ${category.singularLabel}",
                        onActionClick = { onCreateInCategory(category) }
                    )
                }
            } else {
                items(items, key = { it.entity.id }) { item ->
                    VaultEntryCurvedCard(
                        item = item,
                        onClick = { onOpenItem(item.entity.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun RecentlyAddedScreen(
    items: List<VaultItemWithDetails>,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    BackHandler { onBack() }
    val vaultColors = IdeaVaultTheme.colors
    val sortedRecent = remember(items) {
        items.sortedByDescending { it.entity.createdAt }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .statusBarsPadding()
                .testTag("recently_added_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.surface)
                            .border(1.dp, vaultColors.border, CircleShape)
                            .testTag("recently_added_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = vaultColors.primaryText
                        )
                    }
                    Column {
                        Text(
                            text = "Recently Added",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText
                        )
                        Text(
                            text = "${sortedRecent.size} item(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = vaultColors.secondaryText
                        )
                    }
                }
            }

            if (sortedRecent.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    VaultEmptyState(
                        title = "No Recent Entries",
                        subtitle = "Newly captured ideas, methods, prompts, and notes will appear here.",
                        icon = Icons.Outlined.History
                    )
                }
            } else {
                items(sortedRecent, key = { it.entity.id }) { item ->
                    VaultEntryCurvedCard(
                        item = item,
                        onClick = { onOpenItem(item.entity.id) },
                        onToggleFavorite = { onToggleFavorite(item.entity.id) }
                    )
                }
            }
        }
    }
}
