package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.data.CustomTypeEntity
import com.example.data.RecoveryHealthState
import com.example.data.VaultCategory
import com.example.data.VaultJsonSerializers
import com.example.ui.components.CustomTypeBuilderDialog
import com.example.ui.components.CustomTypeManagerSheet
import com.example.ui.components.parseHexColorSafe
import com.example.ui.screens.ActivityStatsScreen
import com.example.ui.screens.ArchivedItemsScreen
import com.example.ui.screens.CategoryItemsScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.FolderDetailScreen
import com.example.ui.screens.FoldersListScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ItemDetailScreen
import com.example.ui.screens.OnboardingFlowScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RecentlyAddedScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.theme.IdeaVaultTheme
import kotlinx.coroutines.launch

sealed interface VaultDestination {
    data object Home : VaultDestination
    data object Search : VaultDestination
    data object Favorites : VaultDestination
    data object Profile : VaultDestination
    data object Settings : VaultDestination
    data object FoldersList : VaultDestination
    data class FolderDetail(val folderId: String) : VaultDestination
    data class CategoryItems(val category: VaultCategory) : VaultDestination
    data object RecentlyAdded : VaultDestination
    data class ItemDetail(val itemId: String) : VaultDestination
    data class Editor(
        val itemId: String? = null,
        val initialCategory: VaultCategory? = null,
        val initialCustomTypeId: String? = null,
        val initialOpenSmartImport: Boolean = false
    ) : VaultDestination
    data object Archived : VaultDestination
    data object Trash : VaultDestination
    data object Stats : VaultDestination
}

private val VaultBackStackSaver: Saver<SnapshotStateList<VaultDestination>, ArrayList<String>> = Saver(
    save = { list ->
        ArrayList(
            list.map { dest ->
                when (dest) {
                    VaultDestination.Home -> "Home"
                    VaultDestination.Search -> "Search"
                    VaultDestination.Favorites -> "Favorites"
                    VaultDestination.Profile -> "Profile"
                    VaultDestination.Settings -> "Settings"
                    VaultDestination.FoldersList -> "FoldersList"
                    is VaultDestination.FolderDetail -> "FolderDetail:${dest.folderId}"
                    is VaultDestination.CategoryItems -> "CategoryItems:${dest.category.id}"
                    VaultDestination.RecentlyAdded -> "RecentlyAdded"
                    is VaultDestination.ItemDetail -> "ItemDetail:${dest.itemId}"
                    is VaultDestination.Editor -> "Editor:${dest.itemId ?: ""}:${dest.initialCategory?.id ?: ""}:${dest.initialCustomTypeId ?: ""}:${dest.initialOpenSmartImport}"
                    VaultDestination.Archived -> "Archived"
                    VaultDestination.Trash -> "Trash"
                    VaultDestination.Stats -> "Stats"
                }
            }
        )
    },
    restore = { saved ->
        val restored = saved.mapNotNull { token ->
            when {
                token == "Home" -> VaultDestination.Home
                token == "Search" -> VaultDestination.Search
                token == "Favorites" -> VaultDestination.Favorites
                token == "Profile" -> VaultDestination.Profile
                token == "Settings" -> VaultDestination.Settings
                token == "FoldersList" -> VaultDestination.FoldersList
                token.startsWith("FolderDetail:") -> VaultDestination.FolderDetail(token.removePrefix("FolderDetail:"))
                token.startsWith("CategoryItems:") -> VaultDestination.CategoryItems(
                    VaultCategory.fromId(token.removePrefix("CategoryItems:"))
                )
                token == "RecentlyAdded" -> VaultDestination.RecentlyAdded
                token.startsWith("ItemDetail:") -> VaultDestination.ItemDetail(token.removePrefix("ItemDetail:"))
                token.startsWith("Editor:") -> {
                    val parts = token.removePrefix("Editor:").split(":", limit = 4)
                    val itemId = parts.getOrNull(0)?.takeIf { it.isNotEmpty() }
                    val cat = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { VaultCategory.fromId(it) }
                    val customTypeId = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
                    val openImport = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
                    VaultDestination.Editor(
                        itemId = itemId,
                        initialCategory = cat,
                        initialCustomTypeId = customTypeId,
                        initialOpenSmartImport = openImport
                    )
                }
                token == "Archived" -> VaultDestination.Archived
                token == "Trash" -> VaultDestination.Trash
                token == "Stats" -> VaultDestination.Stats
                else -> null
            }
        }
        mutableStateListOf<VaultDestination>().apply {
            if (restored.isEmpty()) add(VaultDestination.Home) else addAll(restored)
        }
    }
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun IdeaVaultApp(
    viewModel: VaultViewModel,
    isChangingConfigurationsProvider: (() -> Boolean)? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val backStack = rememberSaveable(saver = VaultBackStackSaver) {
        mutableStateListOf<VaultDestination>(VaultDestination.Home)
    }
    val currentDestination = backStack.lastOrNull() ?: VaultDestination.Home

    var showCreateNewSheet by rememberSaveable { mutableStateOf(false) }
    var autoFocusSearchInput by rememberSaveable { mutableStateOf(false) }
    var showCustomTypeBuilderDialog by rememberSaveable { mutableStateOf(false) }
    var editingCustomType by remember { mutableStateOf<CustomTypeEntity?>(null) }
    var showCustomTypeManagerSheet by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state.prefs.isAppLockEnabled, context, isChangingConfigurationsProvider) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && state.prefs.isAppLockEnabled) {
                val isConfigChange = isChangingConfigurationsProvider?.invoke()
                    ?: (context.findActivity()?.isChangingConfigurations == true)
                if (!isConfigChange) {
                    viewModel.lockSession()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun navigateTo(destination: VaultDestination) {
        if (backStack.lastOrNull() != destination) {
            backStack.add(destination)
        }
    }

    fun selectBottomTab(tabDestination: VaultDestination) {
        backStack.clear()
        backStack.add(VaultDestination.Home)
        if (tabDestination != VaultDestination.Home) {
            backStack.add(tabDestination)
        }
    }

    fun popBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    val isPrimaryBottomBarDestination = when (currentDestination) {
        VaultDestination.Home,
        VaultDestination.FoldersList,
        VaultDestination.Favorites,
        VaultDestination.Settings -> true
        else -> false
    }

    IdeaVaultTheme(themeMode = state.prefs.themeMode) {
        val vaultColors = IdeaVaultTheme.colors

        // Gate 1: Fail-closed startup loading gate until DataStore preferences are loaded.
        // If DataStore read fails, fail closed into an explicit retryable error state without
        // exposing onboarding or vault content or overwriting any saved PIN/preferences.
        if (!state.prefs.isLoaded) {
            if (state.prefs.hasLoadError) {
                PreferencesLoadErrorGateScreen(
                    errorMessage = state.prefs.loadErrorMessage
                        ?: "Unable to read security preferences.",
                    onRetry = { viewModel.retryLoadPreferences() }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(vaultColors.background)
                        .testTag("startup_loading_gate")
                )
            }
            return@IdeaVaultTheme
        }

        // Gate 2: Enforce App Lock before any onboarding or vault content can be composed.
        if (state.prefs.isAppLockEnabled && !state.isSessionUnlocked) {
            AppLockGateScreen(
                onVerifyPin = { candidate -> viewModel.verifyAppLockPin(candidate) },
                onUnlocked = { viewModel.unlockSession() }
            )
            return@IdeaVaultTheme
        }

        // Gate 3: 3-screen animated onboarding flow for first-time installations.
        if (!state.prefs.hasCompletedOnboarding && !state.hasCompletedOnboardingSession) {
            OnboardingFlowScreen(
                initialUserName = state.prefs.userName,
                onCompleteAsync = { chosenName ->
                    viewModel.completeOnboardingAwait(
                        chosenName.ifBlank { state.prefs.userName.ifBlank { "Creator" } }
                    )
                }
            )
            return@IdeaVaultTheme
        }

        LaunchedEffect(state.snackbarMessage) {
            val msg = state.snackbarMessage
            if (!msg.isNullOrBlank()) {
                snackbarHostState.showSnackbar(msg)
                viewModel.clearMessage()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(vaultColors.background)
        ) {
            Scaffold(
                containerColor = vaultColors.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (isPrimaryBottomBarDestination) {
                        VaultBottomNavigationBar(
                            currentDestination = currentDestination,
                            onSelectTab = { tab -> selectBottomTab(tab) },
                            onCreateNewClick = { showCreateNewSheet = true }
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding().coerceAtLeast(0.dp))
                ) {
                    if (state.recoveryHealth is RecoveryHealthState.UnresolvedRecoveryError) {
                        val failure = state.recoveryHealth as RecoveryHealthState.UnresolvedRecoveryError
                        RecoveryWarningBanner(
                            reason = failure.safeMessage,
                            onRetry = { viewModel.retryVaultRecovery() }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = currentDestination,
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                            },
                            label = "vault_destination_transition"
                        ) { dest ->
                            when (dest) {
                                VaultDestination.Home -> {
                                    HomeScreen(
                                        userName = state.prefs.userName,
                                        items = state.allActiveItems,
                                        onOpenItem = { id -> navigateTo(VaultDestination.ItemDetail(id)) },
                                        onEditItem = { item ->
                                            navigateTo(VaultDestination.Editor(item.entity.id, item.categoryEnum))
                                        },
                                        onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                                        onTogglePin = { id -> viewModel.togglePinned(id) },
                                        onDuplicateItem = { item -> viewModel.duplicateItem(item) },
                                        onArchiveItem = { id -> viewModel.toggleArchived(id) },
                                        onDeleteItem = { id -> viewModel.moveToTrash(id) },
                                        onOpenSearch = {
                                            viewModel.setOnlyFavorites(false)
                                            autoFocusSearchInput = true
                                            navigateTo(VaultDestination.Search)
                                        },
                                        onOpenFolders = { selectBottomTab(VaultDestination.FoldersList) },
                                        onOpenRecent = { navigateTo(VaultDestination.RecentlyAdded) },
                                        onCreateEntry = { cat ->
                                            if (cat != null) {
                                                navigateTo(VaultDestination.Editor(null, cat))
                                            } else {
                                                showCreateNewSheet = true
                                            }
                                        },
                                        onCreateQuickNote = { title, content ->
                                            viewModel.createQuickNote(title, content)
                                        },
                                        onSeedSampleTemplates = { viewModel.seedSampleTemplates() },
                                        customTypes = state.customTypes
                                    )
                                }

                                VaultDestination.Search -> {
                                    SearchScreen(
                                        filterState = state.filterState,
                                        filteredItems = state.filteredItems,
                                        folders = state.folders,
                                        onBack = {
                                            viewModel.setOnlyFavorites(false)
                                            if (backStack.size > 1) {
                                                popBack()
                                            } else {
                                                selectBottomTab(VaultDestination.Home)
                                            }
                                        },
                                        onQueryChange = { q -> viewModel.setSearchQuery(q) },
                                        onCategoryFilterChange = { cat -> viewModel.setCategoryFilter(cat) },
                                        onFolderFilterChange = { fId -> viewModel.setFolderFilter(fId) },
                                        onDateRangeChange = { range -> viewModel.setDateRangeFilter(range) },
                                        onOnlyFavoritesChange = { fav -> viewModel.setOnlyFavorites(fav) },
                                        onSortOrderChange = { order -> viewModel.setSortOrder(order) },
                                        onResetFilters = { viewModel.resetFilters() },
                                        onOpenItem = { id -> navigateTo(VaultDestination.ItemDetail(id)) },
                                        onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                                        onEditItem = { item ->
                                            navigateTo(VaultDestination.Editor(item.entity.id, item.categoryEnum))
                                        },
                                        onTogglePin = { id -> viewModel.togglePinned(id) },
                                        onDuplicateItem = { item -> viewModel.duplicateItem(item) },
                                        onArchiveItem = { id -> viewModel.toggleArchived(id) },
                                        onDeleteItem = { id -> viewModel.moveToTrash(id) },
                                        autoFocusSearch = autoFocusSearchInput,
                                        onAutoFocusConsumed = { autoFocusSearchInput = false }
                                    )
                                }

                                VaultDestination.Favorites -> {
                                    FavoritesScreen(
                                        favoriteItems = state.favoriteItems,
                                        onOpenItem = { id -> navigateTo(VaultDestination.ItemDetail(id)) },
                                        onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                                        onEditItem = { item ->
                                            navigateTo(VaultDestination.Editor(item.entity.id, item.categoryEnum))
                                        },
                                        onTogglePin = { id -> viewModel.togglePinned(id) },
                                        onDuplicateItem = { item -> viewModel.duplicateItem(item) },
                                        onArchiveItem = { id -> viewModel.toggleArchived(id) },
                                        onDeleteItem = { id -> viewModel.moveToTrash(id) },
                                        onOpenFavoritesSearch = {
                                            viewModel.setOnlyFavorites(true)
                                            autoFocusSearchInput = true
                                            navigateTo(VaultDestination.Search)
                                        }
                                    )
                                }

                                VaultDestination.Profile -> {
                                    ProfileScreen(
                                        onOpenCollections = { selectBottomTab(VaultDestination.FoldersList) },
                                        onOpenSettings = { selectBottomTab(VaultDestination.Settings) }
                                    )
                                }

                                VaultDestination.Settings -> {
                                    SettingsScreen(
                                        prefs = state.prefs,
                                        activeCount = state.allActiveItems.size,
                                        archivedCount = state.archivedItems.size,
                                        trashedCount = state.trashedItems.size,
                                        pendingBackupPayload = state.pendingBackupPayload,
                                        onBack = { popBack() },
                                        onUpdateUserName = { viewModel.updateUserName(it) },
                                        onUpdateThemeMode = { viewModel.updateThemeMode(it) },
                                        onSetupLockPin = { pin ->
                                            viewModel.setupAppLockPin(pin)
                                        },
                                        onChangeOrDisableLockPin = { cur, next ->
                                            viewModel.changeOrDisableAppLockPin(cur, next)
                                        },
                                        onOpenArchived = { navigateTo(VaultDestination.Archived) },
                                        onOpenTrash = { navigateTo(VaultDestination.Trash) },
                                        onOpenStats = { navigateTo(VaultDestination.Stats) },
                                        onSeedSampleData = { viewModel.seedSampleTemplates() },
                                        onExportBackupUri = { viewModel.exportBackupToUri(it) },
                                        onExportQuickLocalBackup = { viewModel.exportLocalQuickBackup() },
                                        onImportBackupUri = { viewModel.validateImportZipUri(it) },
                                        onConfirmRestoreBackup = { viewModel.confirmRestoreBackup(it) },
                                        onDismissPendingBackup = { viewModel.dismissPendingBackup() },
                                        isPrimaryTab = true
                                    )
                                }

                                VaultDestination.FoldersList -> {
                                    FoldersListScreen(
                                        folders = state.folders,
                                        folderCounts = state.folderCounts,
                                        onBack = { popBack() },
                                        onOpenFolder = { id -> navigateTo(VaultDestination.FolderDetail(id)) },
                                        onCreateFolder = { name, cat, hex -> viewModel.createFolder(name, cat, hex) },
                                        onRenameFolder = { folder, newName -> viewModel.renameFolder(folder, newName) },
                                        onDeleteFolder = { id -> viewModel.deleteFolderPreserveItems(id) },
                                        allActiveItems = state.allActiveItems,
                                        onOpenItem = { id -> navigateTo(VaultDestination.ItemDetail(id)) },
                                        onMoveItemToFolder = { itemId, fId -> viewModel.moveItemToFolder(itemId, fId) },
                                        isPrimaryTab = true
                                    )
                                }

                                is VaultDestination.FolderDetail -> {
                                    val folder = state.folders.find { it.id == dest.folderId }
                                    if (folder == null) {
                                        LaunchedEffect(dest.folderId) { popBack() }
                                    } else {
                                        val folderItems = state.allActiveItems.filter { it.entity.folderId == folder.id }
                                        FolderDetailScreen(
                                            folder = folder,
                                            items = folderItems,
                                            onBack = { popBack() },
                                            onOpenItem = { id -> navigateTo(VaultDestination.ItemDetail(id)) },
                                            onToggleFavorite = { id -> viewModel.toggleFavorite(id) },
                                            folders = state.folders,
                                            onMoveItemToFolder = { itemId, fId -> viewModel.moveItemToFolder(itemId, fId) }
                                        )
                                    }
                                }

                                is VaultDestination.CategoryItems -> {
                                    val catItems = state.allActiveItems.filter { it.categoryEnum == dest.category }
                                    CategoryItemsScreen(
                                        category = dest.category,
                                        items = catItems,
                                        onBack = { popBack() },
                                        onOpenItem = { id -> navigateTo(VaultDestination.ItemDetail(id)) },
                                        onCreateInCategory = { cat -> navigateTo(VaultDestination.Editor(null, cat)) }
                                    )
                                }

                                VaultDestination.RecentlyAdded -> {
                                    RecentlyAddedScreen(
                                        items = state.allActiveItems,
                                        onBack = { popBack() },
                                        onOpenItem = { id -> navigateTo(VaultDestination.ItemDetail(id)) },
                                        onToggleFavorite = { id -> viewModel.toggleFavorite(id) }
                                    )
                                }

                                is VaultDestination.ItemDetail -> {
                                    val item = (state.allActiveItems + state.archivedItems).find { it.entity.id == dest.itemId }
                                    if (item == null) {
                                        LaunchedEffect(dest.itemId) { popBack() }
                                    } else {
                                        ItemDetailScreen(
                                            item = item,
                                            folders = state.folders,
                                            onBack = { popBack() },
                                            onEdit = { navigateTo(VaultDestination.Editor(item.entity.id, item.categoryEnum)) },
                                            onToggleFavorite = { viewModel.toggleFavorite(item.entity.id) },
                                            onTogglePin = { viewModel.togglePinned(item.entity.id) },
                                            onDuplicate = { viewModel.duplicateItem(item) },
                                            onMoveToFolder = { fId -> viewModel.moveItemToFolder(item.entity.id, fId) },
                                            onToggleArchive = {
                                                viewModel.toggleArchived(item.entity.id)
                                                popBack()
                                            },
                                            onMoveToTrash = {
                                                viewModel.moveToTrash(item.entity.id)
                                                popBack()
                                            },
                                            onUpdateIdeaStatus = { status -> viewModel.updateIdeaStatus(item.entity.id, status) },
                                            onToggleFollowStep = { stepId -> viewModel.toggleFollowStep(item.entity.id, stepId) },
                                            onToggleFollowStepForRoute = { routeId, stepId ->
                                                viewModel.toggleFollowStep(item.entity.id, stepId, routeId)
                                            },
                                            onResetFollowSteps = { viewModel.resetFollowProgress(item.entity.id) },
                                            onResetFollowStepsForRoute = { routeId ->
                                                viewModel.resetFollowProgress(item.entity.id, routeId)
                                            },
                                            onReorderSteps = { ids -> viewModel.reorderMethodSteps(item.entity.id, ids) },
                                            onReorderStepsForRoute = { routeId, ids ->
                                                viewModel.reorderMethodSteps(item.entity.id, ids, routeId)
                                            },
                                            onToggleChecklistBlock = { bId -> viewModel.toggleChecklistBlock(item.entity.id, bId) },
                                            onAddQuickRichBlock = { block -> viewModel.addQuickRichBlock(item.entity.id, block) },
                                            onAddUriAttachment = { uri, type ->
                                                viewModel.addUriAttachmentToItem(item.entity.id, uri, type)
                                            },
                                            onAddLinkAttachment = { title, url ->
                                                viewModel.addLinkAttachmentToItem(item.entity.id, title, url)
                                            },
                                            onRemoveAttachment = { attId ->
                                                viewModel.removeAttachmentFromItem(item.entity.id, attId)
                                            },
                                            onShowMessage = { msg -> viewModel.showMessage(msg) },
                                            customTypes = state.customTypes,
                                            onUpdateCustomFields = { fields ->
                                                viewModel.updateItemCustomFields(item.entity.id, fields)
                                            }
                                        )
                                    }
                                }

                                is VaultDestination.Editor -> {
                                    val existingItem = dest.itemId?.let { id ->
                                        (state.allActiveItems + state.archivedItems).find { it.entity.id == id }
                                    }
                                    val recoverableDraft = remember(state.prefs.editorDraftJson) {
                                        VaultJsonSerializers.decodeEditorDraft(state.prefs.editorDraftJson)
                                    }
                                    EditorScreen(
                                        existingItem = existingItem,
                                        initialCategory = dest.initialCategory ?: existingItem?.categoryEnum ?: VaultCategory.IDEA,
                                        initialFolderId = existingItem?.entity?.folderId,
                                        folders = state.folders,
                                        recoverableDraft = recoverableDraft,
                                        attachmentStorage = viewModel.repository.attachmentStorage,
                                        onAutoSaveDraft = { json -> viewModel.saveEditorDraft(json) },
                                        onDiscardDraft = { viewModel.clearEditorDraft() },
                                        onCreateFolder = { name, cat, hex -> viewModel.createFolder(name, cat, hex) },
                                        onSaveItemAwaitable = { entity, steps, expectedUpdatedAt, isEditingExisting, forceOverwrite ->
                                            viewModel.saveItemAwaitable(
                                                item = entity,
                                                steps = steps,
                                                expectedPreviousUpdatedAt = expectedUpdatedAt,
                                                isEditingExisting = isEditingExisting,
                                                forceOverwrite = forceOverwrite
                                            )
                                        },
                                        onSaveItemWithRoutesAwaitable = { entity, steps, expectedUpdatedAt, isEditingExisting, forceOverwrite, mainRouteName, mainRouteDesc, altRoutes, pendingCustomType ->
                                            viewModel.saveItemAwaitable(
                                                item = entity,
                                                steps = steps,
                                                expectedPreviousUpdatedAt = expectedUpdatedAt,
                                                isEditingExisting = isEditingExisting,
                                                forceOverwrite = forceOverwrite,
                                                mainRouteName = mainRouteName,
                                                mainRouteDescription = mainRouteDesc,
                                                alternativeRoutes = altRoutes,
                                                pendingCustomType = pendingCustomType
                                            )
                                        },
                                        onSaveSuccess = { popBack() },
                                        onCancel = { popBack() },
                                        initialCustomTypeId = dest.initialCustomTypeId,
                                        customTypes = state.customTypes,
                                        initialOpenSmartImport = dest.initialOpenSmartImport,
                                        onSaveCustomType = { entity, fields, onDone ->
                                            coroutineScope.launch {
                                                val saved = viewModel.saveCustomTypeAwaitable(entity, fields)
                                                onDone(saved)
                                            }
                                        },
                                        onDeleteCustomTypeSafely = { id -> viewModel.deleteCustomTypeSafely(id) }
                                    )
                                }

                                VaultDestination.Archived -> {
                                    ArchivedItemsScreen(
                                        items = state.archivedItems,
                                        onBack = { popBack() },
                                        onOpenItem = { id -> navigateTo(VaultDestination.ItemDetail(id)) },
                                        onUnarchive = { id -> viewModel.toggleArchived(id) }
                                    )
                                }

                                VaultDestination.Trash -> {
                                    TrashScreen(
                                        items = state.trashedItems,
                                        onBack = { popBack() },
                                        onRestore = { id -> viewModel.restoreFromTrash(id) },
                                        onDeletePermanently = { id -> viewModel.permanentlyDelete(id) },
                                        onEmptyTrash = { viewModel.emptyTrash() }
                                    )
                                }

                                VaultDestination.Stats -> {
                                    ActivityStatsScreen(
                                        items = state.allActiveItems,
                                        categoryCounts = state.categoryCounts,
                                        folderCount = state.folders.size,
                                        onBack = { popBack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showCreateNewSheet) {
                CreateNewBottomSheet(
                    customTypes = state.customTypes,
                    onDismiss = { showCreateNewSheet = false },
                    onSelectCategory = { selectedCat ->
                        showCreateNewSheet = false
                        navigateTo(VaultDestination.Editor(itemId = null, initialCategory = selectedCat))
                    },
                    onSelectCustomType = { ct ->
                        showCreateNewSheet = false
                        navigateTo(
                            VaultDestination.Editor(
                                itemId = null,
                                initialCategory = VaultCategory.KNOWLEDGE,
                                initialCustomTypeId = ct.id
                            )
                        )
                    },
                    onOpenSmartImport = {
                        showCreateNewSheet = false
                        navigateTo(
                            VaultDestination.Editor(
                                itemId = null,
                                initialCategory = VaultCategory.IDEA,
                                initialOpenSmartImport = true
                            )
                        )
                    },
                    onCreateCustomType = {
                        showCreateNewSheet = false
                        editingCustomType = null
                        showCustomTypeBuilderDialog = true
                    },
                    onManageCustomTypes = {
                        showCreateNewSheet = false
                        showCustomTypeManagerSheet = true
                    }
                )
            }

            if (showCustomTypeBuilderDialog) {
                CustomTypeBuilderDialog(
                    initialCustomType = editingCustomType,
                    existingCustomTypes = state.customTypes,
                    onDismiss = {
                        showCustomTypeBuilderDialog = false
                        editingCustomType = null
                    },
                    onSave = { entity, fields ->
                        coroutineScope.launch {
                            val saved = viewModel.saveCustomTypeAwaitable(entity, fields)
                            showCustomTypeBuilderDialog = false
                            val wasEditing = editingCustomType != null
                            editingCustomType = null
                            if (!wasEditing) {
                                navigateTo(
                                    VaultDestination.Editor(
                                        itemId = null,
                                        initialCategory = VaultCategory.KNOWLEDGE,
                                        initialCustomTypeId = saved.id
                                    )
                                )
                            }
                        }
                    }
                )
            }

            if (showCustomTypeManagerSheet) {
                CustomTypeManagerSheet(
                    customTypes = state.customTypes,
                    onDismiss = { showCustomTypeManagerSheet = false },
                    onCreateNewType = {
                        showCustomTypeManagerSheet = false
                        editingCustomType = null
                        showCustomTypeBuilderDialog = true
                    },
                    onEditType = { ct ->
                        showCustomTypeManagerSheet = false
                        editingCustomType = ct
                        showCustomTypeBuilderDialog = true
                    },
                    onDeleteTypeSafely = { ct ->
                        viewModel.deleteCustomTypeSafely(ct.id)
                    },
                    onCreateEntryWithType = { ct ->
                        showCustomTypeManagerSheet = false
                        navigateTo(
                            VaultDestination.Editor(
                                itemId = null,
                                initialCategory = VaultCategory.KNOWLEDGE,
                                initialCustomTypeId = ct.id
                            )
                        )
                    }
                )
            }
        }
    }
}

/**
 * IDEAVAULT V2.3 Bottom Navigation Bar:
 * - Home | Collections | [Raised Circular #F9D55D +] | Favourite | Settings
 * - Rounded rectangular bar inset from screen edges, ~74dp high
 * - Active tab highlighted with #F9D55D accent
 * - Center circular #F9D55D plus button (~64dp diameter) raised above the top edge of the bar
 */
@Composable
private fun VaultBottomNavigationBar(
    currentDestination: VaultDestination,
    onSelectTab: (VaultDestination) -> Unit,
    onCreateNewClick: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    val plusInteractionSource = remember { MutableInteractionSource() }
    val isPlusPressed by plusInteractionSource.collectIsPressedAsState()
    val plusScale by animateFloatAsState(
        targetValue = if (isPlusPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "plus_button_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            color = vaultColors.navigationSurface,
            border = BorderStroke(1.dp, vaultColors.border),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = if (vaultColors.isDark) 0.dp else 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 74.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 74.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTabItem(
                    label = "Home",
                    selected = currentDestination == VaultDestination.Home,
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    testTag = "bottom_nav_home",
                    onClick = { onSelectTab(VaultDestination.Home) },
                    modifier = Modifier.weight(1f)
                )

                BottomNavTabItem(
                    label = "Collections",
                    selected = currentDestination == VaultDestination.FoldersList,
                    selectedIcon = Icons.Filled.Folder,
                    unselectedIcon = Icons.Outlined.Folder,
                    testTag = "bottom_nav_collections",
                    onClick = { onSelectTab(VaultDestination.FoldersList) },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(68.dp))

                BottomNavTabItem(
                    label = "Favourite",
                    selected = currentDestination == VaultDestination.Favorites,
                    selectedIcon = Icons.Filled.Favorite,
                    unselectedIcon = Icons.Outlined.FavoriteBorder,
                    testTag = "bottom_nav_favorites",
                    onClick = { onSelectTab(VaultDestination.Favorites) },
                    modifier = Modifier.weight(1f)
                )

                BottomNavTabItem(
                    label = "Settings",
                    selected = currentDestination == VaultDestination.Settings,
                    selectedIcon = Icons.Filled.Settings,
                    unselectedIcon = Icons.Outlined.Settings,
                    testTag = "bottom_nav_settings",
                    onClick = { onSelectTab(VaultDestination.Settings) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Raised Circular Yellow Plus Button (~64dp diameter, raised above the top edge of the bar)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
                .size(64.dp)
                .graphicsLayer {
                    scaleX = plusScale
                    scaleY = plusScale
                }
                .shadow(
                    elevation = 6.dp,
                    shape = CircleShape,
                    clip = false
                )
                .clip(CircleShape)
                .background(vaultColors.actionYellow)
                .border(1.5.dp, vaultColors.darkInk.copy(alpha = 0.25f), CircleShape)
                .clickable(
                    interactionSource = plusInteractionSource,
                    indication = LocalIndication.current,
                    onClick = onCreateNewClick
                )
                .testTag("bottom_nav_add_fab"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Create New",
                tint = vaultColors.darkInk,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@Composable
private fun BottomNavTabItem(
    label: String,
    selected: Boolean,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vaultColors = IdeaVaultTheme.colors
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "tab_icon_scale_$label"
    )
    val iconColor = when {
        selected && vaultColors.isDark -> vaultColors.actionYellow
        selected && !vaultColors.isDark -> vaultColors.darkInk
        else -> vaultColors.secondaryText
    }
    val labelColor = when {
        selected && vaultColors.isDark -> vaultColors.actionYellow
        selected && !vaultColors.isDark -> vaultColors.darkInk
        else -> vaultColors.secondaryText
    }
    val indicatorColor = when {
        !selected -> Color.Transparent
        vaultColors.isDark -> vaultColors.actionYellow.copy(alpha = 0.16f)
        else -> vaultColors.actionYellow.copy(alpha = 0.85f)
    }

    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(48.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(indicatorColor)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        ) {
            Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * IDEAVAULT V2 Create New Modal Bottom Sheet (Board 01 - 3. Create New):
 * - Header: "Create New"
 * - 4 selectable creation options:
 *   1. Idea ("Save something you're thinking about", #EF7B59)
 *   2. Method ("Save how to do something step by step", #A7D678)
 *   3. Prompt ("Save an AI prompt for reuse", #F9D55D)
 *   4. Note ("Save useful information", #A096EF)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNewBottomSheet(
    customTypes: List<CustomTypeEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSelectCategory: (VaultCategory) -> Unit,
    onSelectCustomType: (CustomTypeEntity) -> Unit = {},
    onOpenSmartImport: () -> Unit = {},
    onCreateCustomType: () -> Unit = {},
    onManageCustomTypes: () -> Unit = {}
) {
    val vaultColors = IdeaVaultTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = vaultColors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("create_new_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Create New",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = vaultColors.primaryText,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            CreateNewOptionRow(
                category = VaultCategory.IDEA,
                icon = Icons.Filled.Lightbulb,
                badgeColor = vaultColors.coral,
                title = "Idea",
                subtitle = "Save something you're thinking about",
                testTag = "create_sheet_option_idea",
                onClick = { onSelectCategory(VaultCategory.IDEA) }
            )

            CreateNewOptionRow(
                category = VaultCategory.METHOD,
                icon = Icons.Filled.Settings,
                badgeColor = vaultColors.lime,
                title = "Method",
                subtitle = "Save how to do something step by step",
                testTag = "create_sheet_option_method",
                onClick = { onSelectCategory(VaultCategory.METHOD) }
            )

            CreateNewOptionRow(
                category = VaultCategory.PROMPT,
                icon = Icons.Filled.ChatBubbleOutline,
                badgeColor = vaultColors.yellow,
                title = "Prompt",
                subtitle = "Save an AI prompt for reuse",
                testTag = "create_sheet_option_prompt",
                onClick = { onSelectCategory(VaultCategory.PROMPT) }
            )

            CreateNewOptionRow(
                category = VaultCategory.KNOWLEDGE,
                icon = Icons.Filled.Description,
                badgeColor = vaultColors.lavender,
                title = "Note",
                subtitle = "Save useful information",
                testTag = "create_sheet_option_note",
                onClick = { onSelectCategory(VaultCategory.KNOWLEDGE) }
            )

            // Custom Types (if any exist)
            customTypes.forEach { ct ->
                val fieldCount = VaultJsonSerializers.decodeCustomFieldDefinitions(ct.fieldsJson).size
                val ctAccent = parseHexColorSafe(ct.colorHex, vaultColors.lavender)
                CreateNewOptionRow(
                    category = VaultCategory.KNOWLEDGE,
                    icon = Icons.Filled.Description,
                    badgeColor = ctAccent,
                    title = ct.name,
                    subtitle = ct.description.ifBlank { "Custom Type ($fieldCount field${if (fieldCount == 1) "" else "s"})" },
                    testTag = "create_sheet_custom_type_${ct.id}",
                    onClick = { onSelectCustomType(ct) }
                )
            }

            // Smart Import & Custom Type Actions
            CreateNewOptionRow(
                category = VaultCategory.IDEA,
                icon = Icons.Filled.Add,
                badgeColor = vaultColors.actionYellow,
                title = "Paste & Convert (Smart Import)",
                subtitle = "Import JSON, Markdown, or text into IDEAVAULT",
                testTag = "create_sheet_smart_import",
                onClick = onOpenSmartImport
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = vaultColors.secondarySurface,
                    border = BorderStroke(1.dp, vaultColors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onCreateCustomType)
                        .testTag("create_sheet_add_custom_type")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp)
                    ) {
                        Text(
                            text = "+ Create Custom Type",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                if (customTypes.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = vaultColors.secondarySurface,
                        border = BorderStroke(1.dp, vaultColors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onManageCustomTypes)
                            .testTag("create_sheet_manage_custom_types")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = "Manage Custom Types (${customTypes.size})",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = vaultColors.secondaryText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateNewOptionRow(
    category: VaultCategory,
    icon: ImageVector,
    badgeColor: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    val rowBg = if (vaultColors.isDark) vaultColors.secondarySurface else vaultColors.surface
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = rowBg,
        border = BorderStroke(1.dp, vaultColors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category.singularLabel,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = vaultColors.secondaryText
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

@Composable
private fun AppLockGateScreen(
    onVerifyPin: suspend (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    val coroutineScope = rememberCoroutineScope()
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background)
            .padding(24.dp)
            .testTag("app_lock_gate_screen"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = vaultColors.surface,
            border = BorderStroke(1.dp, vaultColors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(vaultColors.actionYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Vault Locked",
                        tint = vaultColors.darkInk,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "IdeaVault Is Locked",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )

                Text(
                    text = "Enter your PIN to unlock your offline vault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = vaultColors.secondaryText,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        errorMessage = null
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_lock_pin_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.coral,
                        modifier = Modifier.testTag("app_lock_error_text")
                    )
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            val ok = onVerifyPin(pinInput)
                            if (ok) {
                                onUnlocked()
                            } else {
                                errorMessage = "Incorrect PIN. Please try again."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.actionYellow,
                        contentColor = vaultColors.darkInk
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("app_lock_unlock_button")
                ) {
                    Text("Unlock Vault", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RecoveryWarningBanner(
    reason: String,
    onRetry: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Surface(
        color = vaultColors.coral.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, vaultColors.coral),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Recovery Alert",
                tint = vaultColors.coral
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = vaultColors.primaryText,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = vaultColors.coral,
                    contentColor = vaultColors.darkInk
                )
            ) {
                Text("Retry", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun PreferencesLoadErrorGateScreen(
    errorMessage: String,
    onRetry: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background)
            .padding(24.dp)
            .testTag("preferences_load_error_gate"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = vaultColors.surface,
            border = BorderStroke(1.dp, vaultColors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(vaultColors.coral),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Security state unavailable",
                        tint = vaultColors.darkInk,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "Security Check Paused",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "IdeaVault could not verify your security preferences and has remained locked to protect your entries.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = vaultColors.secondaryText,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = vaultColors.coral,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("preferences_load_error_message")
                )

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = vaultColors.actionYellow,
                        contentColor = vaultColors.darkInk
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("preferences_load_retry_button")
                ) {
                    Text("Retry", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

