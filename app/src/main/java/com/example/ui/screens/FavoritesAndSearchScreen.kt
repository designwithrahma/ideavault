package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.FolderEntity
import com.example.data.VaultCategory
import com.example.data.VaultItemWithDetails
import com.example.ui.FilterAndSortState
import com.example.ui.components.CategoryFilterPillRow
import com.example.ui.components.VaultEmptyState
import com.example.ui.components.VaultEntryCurvedCard
import com.example.ui.components.VaultFilterChip
import com.example.ui.components.VaultSearchBar
import com.example.ui.theme.IdeaVaultTheme

enum class VaultSortOrder(val label: String) {
    RECENTLY_UPDATED("Recently Updated"),
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    ALPHABETICAL("Alphabetical A–Z")
}

enum class DateRangeFilter(val maxAgeMillis: Long?, val label: String) {
    ALL_TIME(null, "All Time"),
    LAST_24_HOURS(24L * 3600_000L, "Last 24h"),
    LAST_7_DAYS(7L * 24L * 3600_000L, "Last 7 Days"),
    LAST_30_DAYS(30L * 24L * 3600_000L, "Last 30 Days")
}

/**
 * IDEAVAULT V2.1 Favourite Screen:
 * - Shows saved favourite entries directly without a duplicate large search bar at the top
 * - Provides Horizontal Category Filter Chips: All | Ideas | Methods | Prompts | Notes (#F9D55D active state)
 * - Compact action button to open the unified Search screen pre-filtered to Favourites if needed
 * - Favorited items displayed as colorful concave VaultEntryCurvedCard cards with #E994AD heart icon
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoriteItems: List<VaultItemWithDetails>,
    onOpenItem: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEditItem: (VaultItemWithDetails) -> Unit = {},
    onTogglePin: (String) -> Unit = {},
    onDuplicateItem: (VaultItemWithDetails) -> Unit = {},
    onArchiveItem: (String) -> Unit = {},
    onDeleteItem: (String) -> Unit = {},
    onOpenFavoritesSearch: (() -> Unit)? = null
) {
    val vaultColors = IdeaVaultTheme.colors
    var selectedCategory by rememberSaveable { mutableStateOf<VaultCategory?>(null) }
    var sortOrder by rememberSaveable { mutableStateOf(VaultSortOrder.RECENTLY_UPDATED) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val displayedFavorites = remember(favoriteItems, selectedCategory, sortOrder) {
        val filtered = favoriteItems.filter { item ->
            when (selectedCategory) {
                null -> true
                VaultCategory.KNOWLEDGE -> item.categoryEnum == VaultCategory.KNOWLEDGE ||
                    item.categoryEnum == VaultCategory.REFERENCE ||
                    item.categoryEnum == VaultCategory.INBOX
                else -> item.categoryEnum == selectedCategory
            }
        }

        when (sortOrder) {
            VaultSortOrder.RECENTLY_UPDATED -> filtered.sortedByDescending { it.entity.updatedAt }
            VaultSortOrder.NEWEST -> filtered.sortedByDescending { it.entity.createdAt }
            VaultSortOrder.OLDEST -> filtered.sortedBy { it.entity.createdAt }
            VaultSortOrder.ALPHABETICAL -> filtered.sortedBy { it.entity.title.lowercase() }
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

        // Clean header row without duplicate large search input bar
        Row(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Favourite",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = vaultColors.primaryText,
                modifier = Modifier.testTag("favorites_header_title")
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onOpenFavoritesSearch != null) {
                    IconButton(
                        onClick = onOpenFavoritesSearch,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(vaultColors.surface)
                            .border(1.dp, vaultColors.border, CircleShape)
                            .testTag("favorites_open_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Favourites",
                            tint = vaultColors.primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { showFilterSheet = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(vaultColors.surface)
                        .border(1.dp, vaultColors.border, CircleShape)
                        .testTag("favorites_sort_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FilterAlt,
                        contentDescription = "Sort Favourites",
                        tint = if (sortOrder != VaultSortOrder.RECENTLY_UPDATED) {
                            vaultColors.actionYellow
                        } else {
                            vaultColors.primaryText
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        CategoryFilterPillRow(
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .testTag("favorites_screen_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (displayedFavorites.isEmpty()) {
                item {
                    VaultEmptyState(
                        title = if (selectedCategory != null) {
                            "No Favourite ${selectedCategory!!.label}"
                        } else {
                            "No Favourites Yet"
                        },
                        subtitle = "Tap the heart icon or card menu on any Idea, Method, Prompt, or Note to save it to Favourite.",
                        icon = Icons.Outlined.FavoriteBorder,
                        accentColor = vaultColors.pink,
                        actionLabel = if (selectedCategory != null) "Show All Favourites" else null,
                        onActionClick = if (selectedCategory != null) {
                            { selectedCategory = null }
                        } else {
                            null
                        }
                    )
                }
            } else {
                items(displayedFavorites, key = { it.entity.id }) { item ->
                    VaultEntryCurvedCard(
                        item = item,
                        showFavoriteHeart = true,
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Sort Favourites",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
                VaultSortOrder.entries.forEach { order ->
                    VaultFilterChip(
                        label = order.label,
                        selected = sortOrder == order,
                        accentColor = vaultColors.actionYellow,
                        onClick = {
                            sortOrder = order
                            showFilterSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * IDEAVAULT V2.1 Dedicated Search Screen (Board 01 - 2. Search):
 * - Single authoritative searchable input and filtering system for the entire app
 * - Supports autoFocus when opened via Home's quick Search entry point
 * - Horizontal Category Filter Chips: All | Ideas | Methods | Prompts | Notes (#F9D55D active state)
 * - Results displayed as colorful concave VaultEntryCurvedCard cards
 * - Advanced Filter & Sort bottom sheet when Filter icon is tapped
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    filterState: FilterAndSortState,
    filteredItems: List<VaultItemWithDetails>,
    folders: List<FolderEntity>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCategoryFilterChange: (VaultCategory?) -> Unit,
    onFolderFilterChange: (String?) -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    onOnlyFavoritesChange: (Boolean) -> Unit,
    onSortOrderChange: (VaultSortOrder) -> Unit,
    onResetFilters: () -> Unit,
    onOpenItem: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEditItem: (VaultItemWithDetails) -> Unit = {},
    onTogglePin: (String) -> Unit = {},
    onDuplicateItem: (VaultItemWithDetails) -> Unit = {},
    onArchiveItem: (String) -> Unit = {},
    onDeleteItem: (String) -> Unit = {},
    autoFocusSearch: Boolean = false,
    onAutoFocusConsumed: () -> Unit = {}
) {
    BackHandler { onBack() }
    val vaultColors = IdeaVaultTheme.colors
    var showAdvancedFilterSheet by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) {
            try {
                focusRequester.requestFocus()
            } catch (_: Throwable) {
            }
            onAutoFocusConsumed()
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

        // Top Search Input Bar with Back button returning to Home/Favourites
        Row(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(vaultColors.surface)
                    .border(1.dp, vaultColors.border, CircleShape)
                    .testTag("search_back_button")
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = vaultColors.primaryText
                )
            }
            VaultSearchBar(
                query = filterState.searchQuery,
                onQueryChange = onQueryChange,
                placeholder = "Search ideas, methods, prompts...",
                onFilterClick = { showAdvancedFilterSheet = true },
                hasActiveFilter = filterState.activeFilterCount > 0,
                focusRequester = focusRequester,
                testTag = "search_query_input",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Category Filter Chips: All | Ideas | Methods | Prompts | Notes
        CategoryFilterPillRow(
            selectedCategory = filterState.categoryFilter,
            onSelectCategory = onCategoryFilterChange,
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .testTag("search_results_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (filterState.activeFilterCount > 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredItems.size} result(s)",
                            style = MaterialTheme.typography.labelMedium,
                            color = vaultColors.secondaryText
                        )
                        Row(
                            modifier = Modifier
                                .clickable { onResetFilters() }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FilterListOff,
                                contentDescription = null,
                                tint = vaultColors.coral,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Reset Filters",
                                style = MaterialTheme.typography.labelMedium,
                                color = vaultColors.coral
                            )
                        }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    VaultEmptyState(
                        title = "No Matching Entries",
                        subtitle = "Try broadening your search keywords or resetting the active category and date filters.",
                        icon = Icons.Outlined.SearchOff,
                        actionLabel = if (filterState.activeFilterCount > 0 || filterState.searchQuery.isNotBlank()) {
                            "Reset Filters"
                        } else {
                            null
                        },
                        onActionClick = onResetFilters
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

    if (showAdvancedFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAdvancedFilterSheet = false },
            sheetState = sheetState,
            containerColor = vaultColors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter & Sort",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = vaultColors.primaryText
                    )
                    TextButton(
                        onClick = {
                            onResetFilters()
                            showAdvancedFilterSheet = false
                        }
                    ) {
                        Text("Reset All", color = vaultColors.coral)
                    }
                }

                // Favorites Only Toggle
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (filterState.onlyFavorites) vaultColors.actionYellow else vaultColors.secondarySurface,
                    border = BorderStroke(
                        1.dp,
                        if (filterState.onlyFavorites) vaultColors.actionYellow else vaultColors.border
                    ),
                    modifier = Modifier.clickable {
                        onOnlyFavoritesChange(!filterState.onlyFavorites)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = if (filterState.onlyFavorites) vaultColors.darkInk else vaultColors.pink,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Favourites Only",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (filterState.onlyFavorites) vaultColors.darkInk else vaultColors.primaryText
                        )
                    }
                }

                Text(
                    text = "Date Range",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.secondaryText
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateRangeFilter.entries.forEach { range ->
                        VaultFilterChip(
                            label = range.label,
                            selected = filterState.dateRangeFilter == range,
                            accentColor = vaultColors.actionYellow,
                            onClick = { onDateRangeChange(range) }
                        )
                    }
                }

                Text(
                    text = "Sort Order",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.secondaryText
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VaultSortOrder.entries.forEach { order ->
                        VaultFilterChip(
                            label = order.label,
                            selected = filterState.sortOrder == order,
                            accentColor = vaultColors.actionYellow,
                            onClick = { onSortOrderChange(order) }
                        )
                    }
                }

                if (folders.isNotEmpty()) {
                    Text(
                        text = "Collection / Folder",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = vaultColors.secondaryText
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        folders.forEach { folder ->
                            VaultFilterChip(
                                label = folder.name,
                                selected = filterState.folderFilterId == folder.id,
                                accentColor = vaultColors.actionYellow,
                                onClick = {
                                    onFolderFilterChange(
                                        if (filterState.folderFilterId == folder.id) null else folder.id
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
