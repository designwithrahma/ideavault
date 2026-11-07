package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CustomTypeEntity
import com.example.data.VaultCategory
import com.example.data.VaultItemWithDetails
import com.example.ui.components.CategoryFilterPillRow
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultEntryCurvedCard
import com.example.ui.components.VaultSearchBar
import com.example.ui.components.parseHexColorSafe
import com.example.ui.theme.IdeaVaultTheme

/**
 * IDEAVAULT V2.1 Home Screen (Board 01 - 1. Home):
 * - No greeting header, no IdeaVault title header, no top-right action buttons
 * - Starts ~12dp below status bar inset with the visual Search entry point bar + Filter icon
 * - Tapping the Search bar navigates directly to the dedicated Search screen and focuses its input
 * - Horizontal Category Filter Chips: All | Ideas | Methods | Prompts | Notes (#F9D55D active state)
 * - Unified vertical list of colorful concave VaultEntryCurvedCard entries
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    userName: String,
    items: List<VaultItemWithDetails>,
    onOpenItem: (String) -> Unit,
    onEditItem: (VaultItemWithDetails) -> Unit = {},
    onToggleFavorite: (String) -> Unit,
    onTogglePin: (String) -> Unit = {},
    onDuplicateItem: (VaultItemWithDetails) -> Unit = {},
    onArchiveItem: (String) -> Unit = {},
    onDeleteItem: (String) -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenRecent: () -> Unit,
    onCreateEntry: (VaultCategory?) -> Unit,
    onCreateQuickNote: (String, String) -> Unit,
    onSeedSampleTemplates: () -> Unit = {},
    customTypes: List<CustomTypeEntity> = emptyList()
) {
    val vaultColors = IdeaVaultTheme.colors
    var selectedCategory by rememberSaveable { mutableStateOf<VaultCategory?>(null) }
    var selectedCustomTypeId by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOrder by rememberSaveable { mutableStateOf(VaultSortOrder.RECENTLY_UPDATED) }
    var onlyPinned by rememberSaveable { mutableStateOf(false) }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val filteredItems = remember(items, selectedCategory, selectedCustomTypeId, sortOrder, onlyPinned, onlyFavorites) {
        val matching = items.filter { item ->
            val matchesCategory = when (selectedCategory) {
                null -> true
                VaultCategory.KNOWLEDGE -> item.categoryEnum == VaultCategory.KNOWLEDGE ||
                    item.categoryEnum == VaultCategory.REFERENCE ||
                    item.categoryEnum == VaultCategory.INBOX
                else -> item.categoryEnum == selectedCategory
            }
            val matchesCustomType = selectedCustomTypeId == null || item.entity.customTypeId == selectedCustomTypeId
            val matchesPinned = !onlyPinned || item.entity.isPinned
            val matchesFavorites = !onlyFavorites || item.entity.isFavorite
            matchesCategory && matchesCustomType && matchesPinned && matchesFavorites
        }

        when (sortOrder) {
            VaultSortOrder.RECENTLY_UPDATED -> matching.sortedWith(
                compareByDescending<VaultItemWithDetails> { it.entity.isPinned }
                    .thenByDescending { it.entity.updatedAt }
            )
            VaultSortOrder.NEWEST -> matching.sortedByDescending { it.entity.createdAt }
            VaultSortOrder.OLDEST -> matching.sortedBy { it.entity.createdAt }
            VaultSortOrder.ALPHABETICAL -> matching.sortedBy { it.entity.title.lowercase() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // 1. V2.3 Search Entry Point (~12dp below status bar inset)
        // Visually matches Board 01 Search Bar; tapping opens the dedicated Search screen and focuses input
        VaultSearchBar(
            query = "",
            onQueryChange = {},
            placeholder = "Search ideas, methods, prompts...",
            onFilterClick = { showFilterSheet = true },
            hasActiveFilter = onlyPinned || onlyFavorites || selectedCustomTypeId != null || sortOrder != VaultSortOrder.RECENTLY_UPDATED,
            readOnlyEntryPoint = true,
            onEntryPointClick = onOpenSearch,
            testTag = "home_search_input",
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. V2 Horizontal Category Filter Chips: All | Ideas | Methods | Prompts | Notes
        CategoryFilterPillRow(
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Saved Content Cards (unified colorful concave cards)
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (filteredItems.isEmpty()) {
                item {
                    VaultEmptyState(
                        title = if (selectedCategory == null && selectedCustomTypeId == null && !onlyPinned && !onlyFavorites) {
                            "No entries yet"
                        } else if (selectedCategory != null) {
                            "No ${selectedCategory!!.label} Yet"
                        } else {
                            "No Matching Entries"
                        },
                        subtitle = "Capture ideas, step-by-step methods, prompts, and notes in your personal offline vault.",
                        icon = Icons.Outlined.AutoAwesome,
                        accentColor = vaultColors.actionYellow,
                        actionLabel = "Create ${selectedCategory?.singularLabel ?: "Entry"}",
                        onActionClick = { onCreateEntry(selectedCategory) }
                    )
                }
            } else {
                items(filteredItems, key = { it.entity.id }) { item ->
                    VaultEntryCurvedCard(
                        item = item,
                        onClick = { onOpenItem(item.entity.id) },
                        onToggleFavorite = { onToggleFavorite(item.entity.id) },
                        onTogglePin = { onTogglePin(item.entity.id) },
                        onEdit = { onEditItem(item) },
                        onDuplicate = { onDuplicateItem(item) },
                        onArchive = { onArchiveItem(item.entity.id) },
                        onDelete = { onDeleteItem(item.entity.id) }
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = vaultColors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter & Sort",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = vaultColors.primaryText,
                        modifier = Modifier.testTag("home_filter_sheet_title")
                    )
                    TextButton(
                        onClick = {
                            sortOrder = VaultSortOrder.RECENTLY_UPDATED
                            onlyPinned = false
                            onlyFavorites = false
                            selectedCategory = null
                            selectedCustomTypeId = null
                            showFilterSheet = false
                        },
                        modifier = Modifier.testTag("home_filter_reset_button")
                    ) {
                        Text("Reset", color = vaultColors.coral)
                    }
                }

                Text(
                    text = "Quick Filters",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.secondaryText
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.example.ui.components.VaultFilterChip(
                        label = "Pinned Only",
                        selected = onlyPinned,
                        accentColor = vaultColors.actionYellow,
                        onClick = { onlyPinned = !onlyPinned },
                        modifier = Modifier.testTag("home_filter_pinned_chip")
                    )
                    com.example.ui.components.VaultFilterChip(
                        label = "Favourites Only",
                        selected = onlyFavorites,
                        accentColor = vaultColors.pink,
                        onClick = { onlyFavorites = !onlyFavorites },
                        modifier = Modifier.testTag("home_filter_favorites_chip")
                    )
                }

                // Audit P3-C: Custom Type filter chips inside Filter & Sort sheet (preserving primary 5-pill bar)
                if (customTypes.isNotEmpty()) {
                    Text(
                        text = "Custom Types",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = vaultColors.secondaryText
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        customTypes.forEach { ct ->
                            val isSelected = selectedCustomTypeId == ct.id
                            com.example.ui.components.VaultFilterChip(
                                label = ct.name,
                                selected = isSelected,
                                accentColor = parseHexColorSafe(ct.colorHex, vaultColors.lavender),
                                onClick = {
                                    selectedCustomTypeId = if (isSelected) null else ct.id
                                },
                                modifier = Modifier.testTag("home_filter_custom_type_${ct.id}")
                            )
                        }
                    }
                }

                Text(
                    text = "Sort Order",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.secondaryText
                )

                VaultSortOrder.entries.forEach { order ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortOrder = order
                                showFilterSheet = false
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = sortOrder == order,
                            onClick = {
                                sortOrder = order
                                showFilterSheet = false
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = vaultColors.actionYellow)
                        )
                        Text(
                            text = order.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = vaultColors.primaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
