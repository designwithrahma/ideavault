package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AttachmentItem
import com.example.data.AttachmentStorageManager
import com.example.data.AttachmentType
import com.example.data.BackupRestoreManager
import com.example.data.BackupRestoreResult
import com.example.data.CustomFieldDefinition
import com.example.data.CustomTypeEntity
import com.example.data.DEFAULT_MAIN_METHOD_NAME
import com.example.data.FolderEntity
import com.example.data.IdeaStatus
import com.example.data.MAIN_METHOD_ROUTE_ID
import com.example.data.MethodRouteEntity
import com.example.data.MethodRouteWithSteps
import com.example.data.MethodStepEntity
import com.example.data.RecoveryHealthState
import com.example.data.RichContentBlock
import com.example.data.SaveItemResult
import com.example.data.UnresolvedVaultRecoveryException
import com.example.data.UserPreferencesRepository
import com.example.data.UserPrefsSnapshot
import com.example.data.ValidatedBackupPayload
import com.example.data.VaultCategory
import com.example.data.VaultDatabase
import com.example.data.VaultItemEntity
import com.example.data.VaultItemWithDetails
import com.example.data.VaultRepository
import com.example.ui.screens.DateRangeFilter
import com.example.ui.screens.VaultSortOrder
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.io.FileInputStream
import java.util.UUID

data class FilterAndSortState(
    val searchQuery: String = "",
    val categoryFilter: VaultCategory? = null,
    val folderFilterId: String? = null,
    val customTypeFilterId: String? = null,
    val dateRangeFilter: DateRangeFilter = DateRangeFilter.ALL_TIME,
    val onlyFavorites: Boolean = false,
    val sortOrder: VaultSortOrder = VaultSortOrder.RECENTLY_UPDATED
) {
    val activeFilterCount: Int
        get() {
            var count = 0
            if (categoryFilter != null) count++
            if (folderFilterId != null) count++
            if (customTypeFilterId != null) count++
            if (dateRangeFilter != DateRangeFilter.ALL_TIME) count++
            if (onlyFavorites) count++
            if (sortOrder != VaultSortOrder.RECENTLY_UPDATED) count++
            return count
        }
}

data class VaultUiState(
    val prefs: UserPrefsSnapshot = UserPrefsSnapshot(),
    val isSessionUnlocked: Boolean = false,
    val hasCompletedOnboardingSession: Boolean = false,
    val filterState: FilterAndSortState = FilterAndSortState(),
    val allActiveItems: List<VaultItemWithDetails> = emptyList(),
    val filteredItems: List<VaultItemWithDetails> = emptyList(),
    val favoriteItems: List<VaultItemWithDetails> = emptyList(),
    val archivedItems: List<VaultItemWithDetails> = emptyList(),
    val trashedItems: List<VaultItemWithDetails> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val customTypes: List<CustomTypeEntity> = emptyList(),
    val categoryCounts: Map<VaultCategory, Int> = emptyMap(),
    val folderCounts: Map<String, Int> = emptyMap(),
    val pendingBackupPayload: ValidatedBackupPayload? = null,
    val recoveryHealth: RecoveryHealthState = RecoveryHealthState.Healthy,
    val snackbarMessage: String? = null
)

class VaultViewModel(
    private val appContext: Context,
    val repository: VaultRepository
) : ViewModel() {

    private val filterStateFlow = MutableStateFlow(FilterAndSortState())
    private val pendingBackupFlow = MutableStateFlow<ValidatedBackupPayload?>(null)
    private val snackbarFlow = MutableStateFlow<String?>(null)
    // In-memory only (NEVER saved in SavedStateHandle or rememberSaveable Bundle):
    // Survives configuration changes (screen rotation) via ViewModel retention,
    // but resets to false (fail-closed) on process death or full app close.
    private val sessionUnlockedFlow = MutableStateFlow(false)
    private val onboardingSessionCompletedFlow = MutableStateFlow(false)
    private val editorSaveMutex = Mutex()

    val isSessionUnlocked: Boolean
        get() = sessionUnlockedFlow.value

    private val coreDataFlow = combine(
        repository.prefsRepo.prefsFlow,
        repository.activeItemsWithDetailsFlow.onStart { emit(emptyList()) },
        repository.archivedItemsWithDetailsFlow.onStart { emit(emptyList()) },
        repository.trashedItemsWithDetailsFlow.onStart { emit(emptyList()) },
        repository.foldersFlow.onStart { emit(emptyList()) }
    ) { prefs, active, archived, trashed, folders ->
        CoreVaultBundle(prefs, active, archived, trashed, folders)
    }

    private val sessionStateFlow = combine(
        sessionUnlockedFlow,
        onboardingSessionCompletedFlow
    ) { isUnlocked, onboardingDone ->
        isUnlocked to onboardingDone
    }

    private val countsFlow = combine(
        repository.categoryCountsFlow.onStart { emit(emptyMap()) },
        repository.folderCountsFlow.onStart { emit(emptyMap()) },
        pendingBackupFlow,
        snackbarFlow,
        sessionStateFlow
    ) { catCounts, foldCounts, pendingBackup, snackbar, sessionPair ->
        AuxVaultBundle(
            categoryCounts = catCounts,
            folderCounts = foldCounts,
            pendingBackup = pendingBackup,
            snackbarMessage = snackbar,
            isSessionUnlocked = sessionPair.first,
            hasCompletedOnboardingSession = sessionPair.second
        )
    }

    val uiState: StateFlow<VaultUiState> = combine(
        coreDataFlow,
        countsFlow,
        filterStateFlow,
        repository.recoveryHealthFlow,
        repository.customTypesFlow.onStart { emit(emptyList()) }
    ) { core, aux, filter, recoveryHealth, customTypes ->
        val filtered = applySearchAndFilters(core.activeItems, filter)
        val favorites = core.activeItems.filter { it.entity.isFavorite }

        VaultUiState(
            prefs = core.prefs,
            isSessionUnlocked = aux.isSessionUnlocked,
            hasCompletedOnboardingSession = aux.hasCompletedOnboardingSession,
            filterState = filter,
            allActiveItems = core.activeItems,
            filteredItems = filtered,
            favoriteItems = favorites,
            archivedItems = core.archivedItems,
            trashedItems = core.trashedItems,
            folders = core.folders,
            customTypes = customTypes,
            categoryCounts = aux.categoryCounts,
            folderCounts = aux.folderCounts,
            pendingBackupPayload = aux.pendingBackup,
            recoveryHealth = recoveryHealth,
            snackbarMessage = aux.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = VaultUiState()
    )

    private fun applySearchAndFilters(
        items: List<VaultItemWithDetails>,
        filter: FilterAndSortState
    ): List<VaultItemWithDetails> {
        val now = System.currentTimeMillis()
        val query = filter.searchQuery.trim().lowercase()

        val matched = items.filter { item ->
            if (filter.categoryFilter != null) {
                val matchesCat = if (filter.categoryFilter == VaultCategory.KNOWLEDGE) {
                    item.categoryEnum == VaultCategory.KNOWLEDGE ||
                        item.categoryEnum == VaultCategory.REFERENCE ||
                        item.categoryEnum == VaultCategory.INBOX
                } else {
                    item.categoryEnum == filter.categoryFilter
                }
                if (!matchesCat) return@filter false
            }
            if (filter.folderFilterId != null && item.entity.folderId != filter.folderFilterId) {
                return@filter false
            }
            if (filter.customTypeFilterId != null && item.entity.customTypeId != filter.customTypeFilterId) {
                return@filter false
            }
            if (filter.onlyFavorites && !item.entity.isFavorite) {
                return@filter false
            }
            val maxAge = filter.dateRangeFilter.maxAgeMillis
            if (maxAge != null && (now - item.entity.updatedAt) > maxAge) {
                return@filter false
            }
            if (query.isNotEmpty()) {
                val matchesTitle = item.entity.title.lowercase().contains(query)
                val matchesDesc = item.entity.description.lowercase().contains(query)
                val matchesContent = item.entity.content.lowercase().contains(query)
                val matchesPlatform = item.entity.platform.lowercase().contains(query)
                val matchesNotes = item.entity.notes.lowercase().contains(query)
                val matchesFolder = item.folderName?.lowercase()?.contains(query) == true
                val matchesTag = item.tags.any { it.lowercase().contains(query) }
                val matchesStep = item.effectiveMethodRoutes.any { routeWithSteps ->
                    routeWithSteps.route.name.lowercase().contains(query) ||
                        routeWithSteps.route.description.lowercase().contains(query) ||
                        routeWithSteps.steps.any {
                            it.title.lowercase().contains(query) ||
                                it.description.lowercase().contains(query) ||
                                it.notes.lowercase().contains(query)
                        }
                }
                val matchesBlock = item.richBlocks.any {
                    it.title.lowercase().contains(query) || it.content.lowercase().contains(query)
                }
                val matchesCustomType = item.customType?.name?.lowercase()?.contains(query) == true
                val matchesCustomFields = item.customFieldsMap.values.any { it.lowercase().contains(query) }
                matchesTitle || matchesDesc || matchesContent || matchesPlatform ||
                    matchesNotes || matchesFolder || matchesTag || matchesStep || matchesBlock ||
                    matchesCustomType || matchesCustomFields
            } else {
                true
            }
        }

        return when (filter.sortOrder) {
            VaultSortOrder.RECENTLY_UPDATED -> matched.sortedWith(
                compareByDescending<VaultItemWithDetails> { it.entity.isPinned }
                    .thenByDescending { it.entity.updatedAt }
            )
            VaultSortOrder.NEWEST -> matched.sortedByDescending { it.entity.createdAt }
            VaultSortOrder.OLDEST -> matched.sortedBy { it.entity.createdAt }
            VaultSortOrder.ALPHABETICAL -> matched.sortedBy { it.entity.title.lowercase() }
        }
    }

    // Search & Filter setters
    fun setSearchQuery(query: String) {
        filterStateFlow.update { it.copy(searchQuery = query) }
    }

    fun setCategoryFilter(category: VaultCategory?) {
        filterStateFlow.update { it.copy(categoryFilter = category) }
    }

    fun setFolderFilter(folderId: String?) {
        filterStateFlow.update { it.copy(folderFilterId = folderId) }
    }

    fun setCustomTypeFilter(customTypeId: String?) {
        filterStateFlow.update { it.copy(customTypeFilterId = customTypeId) }
    }

    fun setDateRangeFilter(range: DateRangeFilter) {
        filterStateFlow.update { it.copy(dateRangeFilter = range) }
    }

    fun setOnlyFavorites(onlyFavorites: Boolean) {
        filterStateFlow.update { it.copy(onlyFavorites = onlyFavorites) }
    }

    fun setSortOrder(order: VaultSortOrder) {
        filterStateFlow.update { it.copy(sortOrder = order) }
    }

    fun resetFilters() {
        filterStateFlow.value = FilterAndSortState()
    }

    // Onboarding, App Lock & Preferences
    suspend fun completeOnboardingAwait(userName: String): Boolean {
        val saved = repository.prefsRepo.completeOnboarding(userName)
        if (saved) {
            onboardingSessionCompletedFlow.value = true
        } else {
            onboardingSessionCompletedFlow.value = false
        }
        return saved
    }

    fun completeOnboarding(userName: String) {
        viewModelScope.launch {
            completeOnboardingAwait(userName)
        }
    }

    fun retryLoadPreferences() {
        repository.prefsRepo.retryLoadPreferences()
    }

    fun unlockSession() {
        sessionUnlockedFlow.value = true
    }

    fun lockSession() {
        sessionUnlockedFlow.value = false
    }

    fun updateUserName(userName: String) {
        viewModelScope.launch {
            repository.prefsRepo.updateUserName(userName)
            showMessage("Display name updated to $userName")
        }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.prefsRepo.updateThemeMode(mode)
        }
    }

    suspend fun setupAppLockPin(pin: String): Boolean {
        val saved = repository.prefsRepo.updateAppLockPin(pin)
        if (saved) {
            sessionUnlockedFlow.value = pin.trim().isNotEmpty()
            showMessage(if (pin.isBlank()) "App Lock disabled" else "App Lock PIN saved")
        } else {
            showMessage("Failed to save App Lock PIN")
        }
        return saved
    }

    fun updateAppLockPin(pin: String) {
        viewModelScope.launch {
            setupAppLockPin(pin)
        }
    }

    suspend fun verifyAppLockPin(candidatePin: String): Boolean {
        val verified = repository.prefsRepo.verifyAppLockPin(candidatePin)
        if (verified) {
            sessionUnlockedFlow.value = true
        }
        return verified
    }

    suspend fun changeOrDisableAppLockPin(currentPin: String, newPin: String): Boolean {
        val updated = repository.prefsRepo.changeOrDisableAppLockPin(currentPin, newPin)
        if (updated) {
            sessionUnlockedFlow.value = newPin.trim().isNotEmpty()
            showMessage(if (newPin.isBlank()) "App Lock disabled" else "App Lock PIN saved")
        }
        return updated
    }

    fun saveEditorDraft(draftJson: String) {
        viewModelScope.launch {
            repository.prefsRepo.saveEditorDraft(draftJson)
        }
    }

    suspend fun saveEditorDraftImmediate(draftJson: String) {
        repository.prefsRepo.saveEditorDraft(draftJson)
    }

    fun clearEditorDraft() {
        viewModelScope.launch {
            repository.prefsRepo.clearEditorDraft()
        }
    }

    suspend fun clearEditorDraftImmediate() {
        repository.prefsRepo.clearEditorDraft()
    }

    private fun launchVaultWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: UnresolvedVaultRecoveryException) {
                showMessage(e.message ?: "Vault writes are locked until recovery completes.")
            } catch (e: IllegalStateException) {
                showMessage(e.message ?: "Operation blocked during active recovery.")
            }
        }
    }

    /**
     * Awaitable editor save operation with double-save race protection, explicit [SaveItemResult],
     * and guaranteed preservation of the recovery draft unless persistence succeeds.
     */
    suspend fun saveItemAwaitable(
        item: VaultItemEntity,
        steps: List<MethodStepEntity>,
        expectedPreviousUpdatedAt: Long? = null,
        isEditingExisting: Boolean = expectedPreviousUpdatedAt != null,
        forceOverwrite: Boolean = false,
        mainRouteName: String = DEFAULT_MAIN_METHOD_NAME,
        mainRouteDescription: String = "",
        alternativeRoutes: List<MethodRouteWithSteps> = emptyList(),
        pendingCustomType: CustomTypeEntity? = null
    ): SaveItemResult {
        if (!editorSaveMutex.tryLock()) {
            return SaveItemResult.ValidationFailure("A save operation is already in progress.")
        }
        return try {
            val result = repository.saveVaultItemWithResult(
                item = item,
                steps = steps,
                expectedPreviousUpdatedAt = expectedPreviousUpdatedAt,
                isEditingExisting = isEditingExisting,
                forceOverwrite = forceOverwrite,
                mainRouteName = mainRouteName,
                mainRouteDescription = mainRouteDescription,
                alternativeRoutes = alternativeRoutes,
                pendingCustomType = pendingCustomType
            )
            when (result) {
                is SaveItemResult.Success -> {
                    val currentDraftJson = repository.prefsRepo.getSnapshot().editorDraftJson
                    val currentDraft = com.example.data.VaultJsonSerializers.decodeEditorDraft(currentDraftJson)
                    val shouldClearDraft = currentDraft == null ||
                        currentDraft.itemId == item.id ||
                        (!isEditingExisting && !currentDraft.isEditingExisting)
                    if (shouldClearDraft) {
                        repository.prefsRepo.clearEditorDraft()
                    }
                    showMessage("Saved \"${item.title.trim()}\"")
                }
                is SaveItemResult.Conflict -> {
                    showMessage(result.message)
                }
                is SaveItemResult.ValidationFailure -> {
                    showMessage(result.reason)
                }
                is SaveItemResult.StorageFailure -> {
                    showMessage(result.reason)
                }
            }
            result
        } finally {
            editorSaveMutex.unlock()
        }
    }

    // Vault Item CRUD
    fun saveItem(
        item: VaultItemEntity,
        steps: List<MethodStepEntity>,
        expectedPreviousUpdatedAt: Long? = null,
        isEditingExisting: Boolean = expectedPreviousUpdatedAt != null,
        forceOverwrite: Boolean = false,
        mainRouteName: String = DEFAULT_MAIN_METHOD_NAME,
        mainRouteDescription: String = "",
        alternativeRoutes: List<MethodRouteWithSteps> = emptyList(),
        pendingCustomType: CustomTypeEntity? = null,
        onSaved: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = saveItemAwaitable(
                item = item,
                steps = steps,
                expectedPreviousUpdatedAt = expectedPreviousUpdatedAt,
                isEditingExisting = isEditingExisting,
                forceOverwrite = forceOverwrite,
                mainRouteName = mainRouteName,
                mainRouteDescription = mainRouteDescription,
                alternativeRoutes = alternativeRoutes,
                pendingCustomType = pendingCustomType
            )
            if (result is SaveItemResult.Success) {
                onSaved(result.itemId)
            }
        }
    }

    fun createQuickNote(title: String, content: String) {
        launchVaultWrite {
            repository.createQuickNoteToInbox(title, content)
            showMessage("Saved Quick Note to Inbox")
        }
    }

    fun duplicateItem(item: VaultItemWithDetails) {
        launchVaultWrite {
            repository.duplicateVaultItem(item)
            showMessage("Duplicated \"${item.entity.title}\"")
        }
    }

    fun toggleFavorite(itemId: String) {
        launchVaultWrite {
            repository.toggleFavorite(itemId)
        }
    }

    fun togglePinned(itemId: String) {
        launchVaultWrite {
            repository.togglePinned(itemId)
        }
    }

    fun toggleArchived(itemId: String) {
        launchVaultWrite {
            repository.toggleArchived(itemId)
            showMessage("Updated archive status")
        }
    }

    fun moveItemToFolder(itemId: String, folderId: String?) {
        launchVaultWrite {
            repository.moveItemToFolder(itemId, folderId)
            showMessage("Folder updated")
        }
    }

    fun updateIdeaStatus(itemId: String, status: IdeaStatus) {
        launchVaultWrite {
            repository.updateIdeaStatus(itemId, status)
        }
    }

    fun toggleChecklistBlock(itemId: String, blockId: String) {
        launchVaultWrite {
            repository.toggleChecklistBlock(itemId, blockId)
        }
    }

    fun addQuickRichBlock(itemId: String, block: RichContentBlock) {
        launchVaultWrite {
            repository.addRichBlockToItem(itemId, block)
        }
    }

    // Custom Types CRUD
    suspend fun saveCustomTypeAwaitable(
        customType: CustomTypeEntity,
        fields: List<CustomFieldDefinition>
    ): CustomTypeEntity {
        val saved = repository.saveCustomType(customType, fields)
        showMessage("Saved Custom Type \"${saved.name}\"")
        return saved
    }

    fun saveCustomType(
        customType: CustomTypeEntity,
        fields: List<CustomFieldDefinition>,
        onSaved: (CustomTypeEntity) -> Unit = {}
    ) {
        launchVaultWrite {
            val saved = repository.saveCustomType(customType, fields)
            showMessage("Saved Custom Type \"${saved.name}\"")
            onSaved(saved)
        }
    }

    fun deleteCustomTypeSafely(
        customTypeId: String,
        onDeleted: (Int) -> Unit = {}
    ) {
        launchVaultWrite {
            val affected = repository.deleteCustomTypeSafely(customTypeId)
            showMessage(
                if (affected > 0) {
                    "Deleted Custom Type and preserved $affected saved ${if (affected == 1) "entry" else "entries"}"
                } else {
                    "Deleted Custom Type"
                }
            )
            onDeleted(affected)
        }
    }

    fun updateItemCustomFields(itemId: String, updatedFieldsMap: Map<String, String>) {
        launchVaultWrite {
            repository.updateItemCustomFields(itemId, updatedFieldsMap)
        }
    }

    // Method Steps & Follow Mode
    fun reorderMethodSteps(
        itemId: String,
        orderedStepIds: List<String>,
        routeId: String = MAIN_METHOD_ROUTE_ID
    ) {
        launchVaultWrite {
            repository.reorderMethodSteps(itemId, orderedStepIds, routeId)
        }
    }

    fun toggleFollowStep(
        itemId: String,
        stepId: String,
        routeId: String = MAIN_METHOD_ROUTE_ID
    ) {
        launchVaultWrite {
            repository.toggleFollowStep(itemId, stepId, routeId)
        }
    }

    fun resetFollowProgress(
        itemId: String,
        routeId: String = MAIN_METHOD_ROUTE_ID
    ) {
        launchVaultWrite {
            repository.resetFollowProgress(itemId, routeId)
            showMessage("Follow progress reset")
        }
    }

    // Attachments
    fun addUriAttachmentToItem(itemId: String, uri: Uri, explicitType: AttachmentType?) {
        launchVaultWrite {
            val saved = repository.attachmentStorage.copyUriToPersistentStorage(uri, explicitType)
            if (saved != null) {
                val added = repository.addAttachmentToItem(itemId, saved)
                if (added) {
                    showMessage("Attached ${saved.name}")
                } else {
                    File(saved.uriOrUrl).takeIf { it.exists() }?.delete()
                    showMessage("\"${saved.name}\" is already attached to this entry")
                }
            } else {
                showMessage("Could not read selected file")
            }
        }
    }

    fun addLinkAttachmentToItem(itemId: String, title: String, url: String) {
        launchVaultWrite {
            val linkAtt = repository.attachmentStorage.createLinkAttachment(title, url)
            val added = repository.addAttachmentToItem(itemId, linkAtt)
            if (added) {
                showMessage("Added link ${linkAtt.name}")
            } else {
                showMessage("\"${linkAtt.name}\" is already attached to this entry")
            }
        }
    }

    fun removeAttachmentFromItem(itemId: String, attachmentId: String) {
        launchVaultWrite {
            repository.removeAttachmentFromItem(itemId, attachmentId)
            showMessage("Removed attachment")
        }
    }

    // Folders
    fun createFolder(name: String, category: VaultCategory, colorHex: String) {
        launchVaultWrite {
            repository.createFolder(name, category, colorHex)
            showMessage("Created folder \"$name\"")
        }
    }

    fun renameFolder(folder: FolderEntity, newName: String) {
        launchVaultWrite {
            repository.renameFolder(folder, newName)
            showMessage("Renamed folder to \"$newName\"")
        }
    }

    fun deleteFolderPreserveItems(folderId: String) {
        launchVaultWrite {
            repository.deleteFolderPreservingItems(folderId)
            showMessage("Folder deleted (entries preserved)")
        }
    }

    // Trash & Permanent Delete
    fun moveToTrash(itemId: String) {
        launchVaultWrite {
            repository.moveToTrash(itemId)
            showMessage("Moved to Trash")
        }
    }

    fun restoreFromTrash(itemId: String) {
        launchVaultWrite {
            repository.restoreFromTrash(itemId)
            showMessage("Restored from Trash")
        }
    }

    fun permanentlyDelete(itemId: String) {
        launchVaultWrite {
            repository.permanentlyDelete(itemId)
            showMessage("Permanently deleted")
        }
    }

    fun emptyTrash() {
        launchVaultWrite {
            repository.emptyTrash()
            showMessage("Trash emptied")
        }
    }

    // Sample Templates
    fun seedSampleTemplates() {
        launchVaultWrite {
            repository.seedSampleTemplates()
            showMessage("Sample templates added to your vault")
        }
    }

    // Backup & Restore
    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val tempFile = File(
                repository.attachmentStorage.stagingRootDir,
                "saf_export_${UUID.randomUUID()}.zip"
            )
            try {
                when (val res = repository.exportBackupToFile(tempFile)) {
                    is BackupRestoreResult.Success -> {
                        val outStream = appContext.contentResolver.openOutputStream(uri)
                        if (outStream == null) {
                            showMessage("Unable to open destination file")
                        } else {
                            outStream.use { out ->
                                FileInputStream(tempFile).use { fis ->
                                    fis.copyTo(out)
                                }
                                out.flush()
                            }
                            showMessage(res.message)
                        }
                    }
                    is BackupRestoreResult.Failure -> showMessage(res.reason)
                }
            } catch (e: Exception) {
                showMessage("Backup failed: ${e.localizedMessage ?: "Storage error"}")
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    fun exportLocalQuickBackup() {
        viewModelScope.launch {
            val backupFile = File(appContext.filesDir, "ideavault_latest_backup.zip")
            when (val res = repository.exportBackupToFile(backupFile)) {
                is BackupRestoreResult.Success -> showMessage("Saved snapshot to ${backupFile.name}")
                is BackupRestoreResult.Failure -> showMessage(res.reason)
            }
        }
    }

    fun validateImportZipUri(uri: Uri) {
        viewModelScope.launch {
            try {
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    val validation = repository.validateBackup(input)
                    validation.fold(
                        onSuccess = { payload ->
                            pendingBackupFlow.value = payload
                        },
                        onFailure = { err ->
                            showMessage(err.localizedMessage ?: "Invalid backup archive")
                        }
                    )
                } ?: showMessage("Unable to read selected backup file")
            } catch (e: Exception) {
                showMessage("Invalid backup file: ${e.localizedMessage}")
            }
        }
    }

    fun confirmRestoreBackup(payload: ValidatedBackupPayload) {
        viewModelScope.launch {
            pendingBackupFlow.value = null
            when (val res = repository.applyBackup(payload)) {
                is BackupRestoreResult.Success -> showMessage(res.message)
                is BackupRestoreResult.Failure -> showMessage(res.reason)
            }
        }
    }

    fun dismissPendingBackup() {
        val current = pendingBackupFlow.value
        pendingBackupFlow.value = null
        repository.dismissPendingBackup(current)
    }

    fun retryVaultRecovery() {
        viewModelScope.launch {
            when (val res = repository.retryPendingRecovery()) {
                is BackupRestoreResult.Success -> showMessage(res.message)
                is BackupRestoreResult.Failure -> showMessage(res.reason)
            }
        }
    }

    fun showMessage(msg: String) {
        snackbarFlow.value = msg
    }

    fun clearMessage() {
        snackbarFlow.value = null
    }

    private data class CoreVaultBundle(
        val prefs: UserPrefsSnapshot,
        val activeItems: List<VaultItemWithDetails>,
        val archivedItems: List<VaultItemWithDetails>,
        val trashedItems: List<VaultItemWithDetails>,
        val folders: List<FolderEntity>
    )

    private data class AuxVaultBundle(
        val categoryCounts: Map<VaultCategory, Int>,
        val folderCounts: Map<String, Int>,
        val pendingBackup: ValidatedBackupPayload?,
        val snackbarMessage: String?,
        val isSessionUnlocked: Boolean,
        val hasCompletedOnboardingSession: Boolean
    )

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appCtx = context.applicationContext
                    val db = VaultDatabase.getInstance(appCtx)
                    val prefsRepo = UserPreferencesRepository(appCtx)
                    val attStorage = AttachmentStorageManager(appCtx)
                    val backupManager = BackupRestoreManager(
                        dao = db.vaultDao(),
                        prefsRepo = prefsRepo,
                        attachmentStorage = attStorage,
                        database = db
                    )
                    val repo = VaultRepository(db.vaultDao(), prefsRepo, attStorage, backupManager)
                    return VaultViewModel(appCtx, repo) as T
                }
            }
    }
}
