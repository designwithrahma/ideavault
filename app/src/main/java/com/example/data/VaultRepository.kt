package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class VaultRepository(
    private val dao: VaultDao,
    val prefsRepo: UserPreferencesRepository,
    val attachmentStorage: AttachmentStorageManager,
    val backupRestoreManager: BackupRestoreManager
) {
    val foldersFlow: Flow<List<FolderEntity>> = dao.observeFolders()
    val customTypesFlow: Flow<List<CustomTypeEntity>> = dao.observeAllCustomTypes()

    val categoryCountsFlow: Flow<Map<VaultCategory, Int>> = dao.observeCategoryCounts()
        .combine(dao.observeFolders()) { counts, _ ->
            val map = mutableMapOf<VaultCategory, Int>()
            VaultCategory.entries.forEach { map[it] = 0 }
            counts.forEach { row ->
                val cat = VaultCategory.fromId(row.category)
                map[cat] = row.count
            }
            map
        }

    val folderCountsFlow: Flow<Map<String, Int>> = dao.observeFolderCounts()
        .combine(dao.observeFolders()) { counts, _ ->
            counts.mapNotNull { row ->
                val fId = row.folderId ?: return@mapNotNull null
                fId to row.count
            }.toMap()
        }

    private val routesAndFollowsFlow: Flow<Triple<List<MethodRouteEntity>, List<MethodFollowRunEntity>, List<MethodRouteFollowRunEntity>>> =
        combine(
            dao.observeAllMethodRoutes(),
            dao.observeAllFollowRuns(),
            dao.observeAllRouteFollowRuns()
        ) { routes, legacyRuns, routeRuns ->
            Triple(routes, legacyRuns, routeRuns)
        }

    private val foldersAndCustomTypesFlow: Flow<Pair<List<FolderEntity>, List<CustomTypeEntity>>> =
        combine(
            dao.observeFolders(),
            dao.observeAllCustomTypes()
        ) { folders, customTypes ->
            folders to customTypes
        }

    val activeItemsWithDetailsFlow: Flow<List<VaultItemWithDetails>> = combine(
        dao.observeActiveItems(),
        dao.observeAllMethodSteps(),
        routesAndFollowsFlow,
        foldersAndCustomTypesFlow
    ) { items, allSteps, (allRoutes, allRuns, allRouteRuns), (folders, customTypes) ->
        val stepsByItem = allSteps.groupBy { it.itemId }
        val routesByItem = allRoutes.groupBy { it.itemId }
        val runsByItem = allRuns.associateBy { it.itemId }
        val routeRunsByItem = allRouteRuns.groupBy { it.itemId }
        val folderNames = folders.associate { it.id to it.name }
        val customTypesById = customTypes.associateBy { it.id }
        items.map { entity ->
            mapToDetails(
                entity = entity,
                steps = stepsByItem[entity.id].orEmpty(),
                routes = routesByItem[entity.id].orEmpty(),
                followRun = runsByItem[entity.id],
                routeFollowRuns = routeRunsByItem[entity.id].orEmpty(),
                folderName = folderNames[entity.folderId],
                customType = entity.customTypeId?.let { customTypesById[it] }
            )
        }
    }

    val archivedItemsWithDetailsFlow: Flow<List<VaultItemWithDetails>> = combine(
        dao.observeArchivedItems(),
        dao.observeAllMethodSteps(),
        routesAndFollowsFlow,
        foldersAndCustomTypesFlow
    ) { items, allSteps, (allRoutes, allRuns, allRouteRuns), (folders, customTypes) ->
        val stepsByItem = allSteps.groupBy { it.itemId }
        val routesByItem = allRoutes.groupBy { it.itemId }
        val runsByItem = allRuns.associateBy { it.itemId }
        val routeRunsByItem = allRouteRuns.groupBy { it.itemId }
        val folderNames = folders.associate { it.id to it.name }
        val customTypesById = customTypes.associateBy { it.id }
        items.map { entity ->
            mapToDetails(
                entity = entity,
                steps = stepsByItem[entity.id].orEmpty(),
                routes = routesByItem[entity.id].orEmpty(),
                followRun = runsByItem[entity.id],
                routeFollowRuns = routeRunsByItem[entity.id].orEmpty(),
                folderName = folderNames[entity.folderId],
                customType = entity.customTypeId?.let { customTypesById[it] }
            )
        }
    }

    val trashedItemsWithDetailsFlow: Flow<List<VaultItemWithDetails>> = combine(
        dao.observeTrashedItems(),
        dao.observeAllMethodSteps(),
        routesAndFollowsFlow,
        foldersAndCustomTypesFlow
    ) { items, allSteps, (allRoutes, allRuns, allRouteRuns), (folders, customTypes) ->
        val stepsByItem = allSteps.groupBy { it.itemId }
        val routesByItem = allRoutes.groupBy { it.itemId }
        val runsByItem = allRuns.associateBy { it.itemId }
        val routeRunsByItem = allRouteRuns.groupBy { it.itemId }
        val folderNames = folders.associate { it.id to it.name }
        val customTypesById = customTypes.associateBy { it.id }
        items.map { entity ->
            mapToDetails(
                entity = entity,
                steps = stepsByItem[entity.id].orEmpty(),
                routes = routesByItem[entity.id].orEmpty(),
                followRun = runsByItem[entity.id],
                routeFollowRuns = routeRunsByItem[entity.id].orEmpty(),
                folderName = folderNames[entity.folderId],
                customType = entity.customTypeId?.let { customTypesById[it] }
            )
        }
    }

    private fun mapToDetails(
        entity: VaultItemEntity,
        steps: List<MethodStepEntity>,
        routes: List<MethodRouteEntity>,
        followRun: MethodFollowRunEntity?,
        routeFollowRuns: List<MethodRouteFollowRunEntity>,
        folderName: String?,
        customType: CustomTypeEntity? = null
    ): VaultItemWithDetails {
        val mainSteps = steps
            .filter { it.routeId.isBlank() || it.routeId == MAIN_METHOD_ROUTE_ID }
            .sortedBy { it.stepOrder }
        val mainRouteRun = routeFollowRuns.firstOrNull { it.routeId == MAIN_METHOD_ROUTE_ID }
        val mainCompletedStepIds = mainRouteRun?.let {
            VaultJsonSerializers.decodeStringList(it.completedStepIdsJson).toSet()
        } ?: followRun?.let {
            VaultJsonSerializers.decodeStringList(it.completedStepIdsJson).toSet()
        } ?: emptySet()

        val storedMainRoute = routes.firstOrNull {
            it.isMain || it.id == MAIN_METHOD_ROUTE_ID || it.id == "${entity.id}_$MAIN_METHOD_ROUTE_ID"
        }
        val mainRouteEntity = MethodRouteEntity(
            id = MAIN_METHOD_ROUTE_ID,
            itemId = entity.id,
            name = storedMainRoute?.name?.takeIf { it.isNotBlank() } ?: DEFAULT_MAIN_METHOD_NAME,
            description = storedMainRoute?.description ?: "",
            routeOrder = 0,
            isMain = true
        )

        val altRoutes = routes
            .filter { !it.isMain && it.id != MAIN_METHOD_ROUTE_ID }
            .sortedBy { it.routeOrder }
            .map { altRoute ->
                val altSteps = steps
                    .filter { it.routeId == altRoute.id }
                    .sortedBy { it.stepOrder }
                val altCompletedIds = routeFollowRuns
                    .firstOrNull { it.routeId == altRoute.id }
                    ?.let { VaultJsonSerializers.decodeStringList(it.completedStepIdsJson).toSet() }
                    ?: emptySet()
                MethodRouteWithSteps(
                    route = altRoute,
                    steps = altSteps,
                    completedStepIds = altCompletedIds
                )
            }

        val methodRoutes = listOf(
            MethodRouteWithSteps(
                route = mainRouteEntity,
                steps = mainSteps,
                completedStepIds = mainCompletedStepIds
            )
        ) + altRoutes

        return VaultItemWithDetails(
            entity = entity,
            tags = VaultJsonSerializers.decodeStringList(entity.tagsJson),
            richBlocks = VaultJsonSerializers.decodeRichBlocks(entity.richBlocksJson),
            attachments = VaultJsonSerializers.decodeAttachments(entity.attachmentsJson),
            steps = mainSteps,
            completedFollowStepIds = mainCompletedStepIds,
            folderName = folderName,
            methodRoutes = methodRoutes,
            customType = customType,
            customFieldsMap = VaultJsonSerializers.decodeCustomFieldsMap(entity.customFieldsJson)
        )
    }

    val recoveryHealthFlow: Flow<RecoveryHealthState> = backupRestoreManager.recoveryHealthFlow

    // Optional fault hooks for automated tests verifying save/route-delete failure & rollback
    var saveFaultInjector: (() -> Unit)? = null
    var savePostStepsFaultInjector: (() -> Unit)?
        get() = VaultTransactionFaultHooks.onSaveAfterStepsBeforeFollowPrune
        set(value) {
            VaultTransactionFaultHooks.onSaveAfterStepsBeforeFollowPrune = value
        }
    var deleteRouteMidTransactionFaultInjector: (() -> Unit)?
        get() = VaultTransactionFaultHooks.onDeleteRouteMidTransaction
        set(value) {
            VaultTransactionFaultHooks.onDeleteRouteMidTransaction = value
        }
    var customTypeMutationMidTransactionFaultInjector: (() -> Unit)?
        get() = VaultTransactionFaultHooks.onCustomTypeMutationMidTransaction
        set(value) {
            VaultTransactionFaultHooks.onCustomTypeMutationMidTransaction = value
        }

    suspend fun getItemWithDetailsById(itemId: String): VaultItemWithDetails? {
        val entity = dao.getItemById(itemId) ?: return null
        val steps = dao.getStepsForItemSnapshot(itemId)
        val routes = dao.getRoutesForItemSnapshot(itemId)
        val followRun = dao.getFollowRun(itemId)
        val routeFollowRuns = dao.getRouteFollowRunsForItemSnapshot(itemId)
        val folderName = dao.getAllFoldersSnapshot().firstOrNull { it.id == entity.folderId }?.name
        val customType = entity.customTypeId?.let { dao.getCustomTypeById(it) }
        return mapToDetails(entity, steps, routes, followRun, routeFollowRuns, folderName, customType)
    }

    suspend fun getAllCustomTypesSnapshot(): List<CustomTypeEntity> = dao.getAllCustomTypesSnapshot()

    suspend fun getCustomTypeById(customTypeId: String): CustomTypeEntity? = dao.getCustomTypeById(customTypeId)

    suspend fun countItemsUsingCustomType(customTypeId: String): Int =
        dao.getItemsByCustomTypeSnapshot(customTypeId).size

    suspend fun countItemsUsingCustomField(customTypeId: String, fieldId: String): Int {
        val items = dao.getItemsByCustomTypeSnapshot(customTypeId)
        return items.count { item ->
            val map = VaultJsonSerializers.decodeCustomFieldsMap(item.customFieldsJson)
            !map[fieldId].isNullOrBlank()
        }
    }

    /**
     * Creates or updates a CustomTypeEntity inside a single atomic Room @Transaction (P1-12 & FIX 06).
     * - If any previously existing fields were removed from the definition while saved items had
     *   non-empty values for those fields, preserves those values in the item's notes.
     * - If any existing field's type or select options changed (FIX 06), validates and normalizes
     *   existing items' saved values for that field; any value that is incompatible with the new
     *   field type/options is automatically preserved in the item's notes and cleared from the
     *   typed map so existing entries never become corrupted or fail validation.
     */
    suspend fun saveCustomType(
        customType: CustomTypeEntity,
        fields: List<CustomFieldDefinition>
    ): CustomTypeEntity = backupRestoreManager.withWriteLock {
        val now = System.currentTimeMillis()
        val existing = dao.getCustomTypeById(customType.id)
        val normalizedFields = fields.mapIndexed { idx, f -> f.copy(orderIndex = idx) }
        val encodedFields = VaultJsonSerializers.encodeCustomFieldDefinitions(normalizedFields)

        val itemsToUpdate = mutableListOf<VaultItemEntity>()
        if (existing != null) {
            val oldDefs = VaultJsonSerializers.decodeCustomFieldDefinitions(existing.fieldsJson)
            val oldDefsById = oldDefs.associateBy { it.id }
            val newIds = normalizedFields.map { it.id }.toSet()
            val removedDefs = oldDefs.filter { it.id !in newIds }
            val changedDefs = normalizedFields.mapNotNull { newDef ->
                val oldDef = oldDefsById[newDef.id]
                if (oldDef != null && (oldDef.type != newDef.type || oldDef.options != newDef.options)) {
                    oldDef to newDef
                } else {
                    null
                }
            }
            if (removedDefs.isNotEmpty() || changedDefs.isNotEmpty()) {
                val items = dao.getItemsByCustomTypeSnapshot(existing.id)
                for (item in items) {
                    val valuesMap = VaultJsonSerializers.decodeCustomFieldsMap(item.customFieldsJson).toMutableMap()
                    val preservedLines = mutableListOf<String>()
                    var mapModified = false
                    for (removed in removedDefs) {
                        val v = valuesMap.remove(removed.id).orEmpty()
                        if (v.isNotBlank()) {
                            mapModified = true
                            preservedLines.add("${removed.name}: ${SmartImportExportEngine.formatCustomFieldValueReadable(removed, v)}")
                        }
                    }
                    for ((oldDef, newDef) in changedDefs) {
                        val rawVal = valuesMap[newDef.id].orEmpty()
                        if (rawVal.isNotBlank()) {
                            val normalizedVal = SmartImportExportEngine.normalizeValueForFieldType(rawVal, newDef)
                            val validationErr = SmartImportExportEngine.validateCustomFieldValue(
                                fieldDef = newDef,
                                rawValue = normalizedVal,
                                checkRequired = false
                            )
                            if (validationErr == null) {
                                if (normalizedVal != rawVal) {
                                    valuesMap[newDef.id] = normalizedVal
                                    mapModified = true
                                }
                            } else {
                                valuesMap.remove(newDef.id)
                                mapModified = true
                                val readableOld = SmartImportExportEngine.formatCustomFieldValueReadable(oldDef, rawVal)
                                preservedLines.add(
                                    "${oldDef.name} (${oldDef.type.label} -> ${newDef.type.label}): $readableOld"
                                )
                            }
                        }
                    }
                    if (preservedLines.isNotEmpty() || mapModified) {
                        val updatedNotes = if (preservedLines.isNotEmpty()) {
                            val preservedBlock = "Preserved (${existing.name} fields):\n" + preservedLines.joinToString("\n")
                            if (item.notes.isBlank()) preservedBlock else "${item.notes}\n\n$preservedBlock"
                        } else {
                            item.notes
                        }
                        itemsToUpdate.add(
                            item.copy(
                                notes = updatedNotes,
                                customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(valuesMap),
                                updatedAt = maxOf(now, item.updatedAt + 1L)
                            )
                        )
                    }
                }
            }
        }

        val toSave = customType.copy(
            name = customType.name.trim(),
            fieldsJson = encodedFields,
            createdAt = existing?.createdAt ?: customType.createdAt,
            updatedAt = maxOf(now, (existing?.updatedAt ?: 0L) + 1L)
        )
        dao.saveCustomTypeAtomically(toSave, itemsToUpdate)
        toSave
    }

    /**
     * Deletes a CustomTypeEntity safely inside a single atomic Room @Transaction (P1-12)
     * without deleting any saved entries. Any entry using this custom type has its custom
     * field values formatted and appended into its content/notes before clearing `customTypeId`.
     */
    suspend fun deleteCustomTypeSafely(customTypeId: String): Int = backupRestoreManager.withWriteLock {
        val existing = dao.getCustomTypeById(customTypeId) ?: return@withWriteLock 0
        val defs = VaultJsonSerializers.decodeCustomFieldDefinitions(existing.fieldsJson).sortedBy { it.orderIndex }
        val affectedItems = dao.getItemsByCustomTypeSnapshot(customTypeId)
        val now = System.currentTimeMillis()
        val itemsToUpdate = affectedItems.map { item ->
            val valuesMap = VaultJsonSerializers.decodeCustomFieldsMap(item.customFieldsJson)
            val formattedLines = defs.mapNotNull { def ->
                val raw = valuesMap[def.id].orEmpty()
                if (raw.isNotBlank()) "${def.name}: ${SmartImportExportEngine.formatCustomFieldValueReadable(def, raw)}" else null
            }
            val updatedContent = if (formattedLines.isNotEmpty()) {
                val summaryBlock = "[${existing.name}]\n" + formattedLines.joinToString("\n")
                if (item.content.isBlank()) summaryBlock else "${item.content}\n\n$summaryBlock"
            } else {
                item.content
            }
            item.copy(
                content = updatedContent,
                customTypeId = null,
                customFieldsJson = "{}",
                updatedAt = maxOf(now, item.updatedAt + 1L)
            )
        }
        dao.deleteCustomTypeAtomically(customTypeId, itemsToUpdate)
        affectedItems.size
    }

    suspend fun updateItemCustomFields(itemId: String, updatedFieldsMap: Map<String, String>) = backupRestoreManager.withWriteLock {
        val existing = dao.getItemById(itemId) ?: return@withWriteLock
        val now = System.currentTimeMillis()
        dao.updateItem(
            existing.copy(
                customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(updatedFieldsMap),
                updatedAt = maxOf(now, existing.updatedAt + 1L)
            )
        )
    }

    /**
     * Authoritative save operation returning an explicit [SaveItemResult] distinguishing:
     * - [SaveItemResult.Success]: Persisted and verified in Room.
     * - [SaveItemResult.Conflict]: Stale edit detected (`existing.updatedAt != expectedPreviousUpdatedAt`
     *   or concurrent creation/deletion conflict); preserves both database state and user's unsaved draft.
     * - [SaveItemResult.ValidationFailure]: Invalid input fields.
     * - [SaveItemResult.StorageFailure]: Database/IO error or unresolved recovery lock.
     */
    suspend fun saveVaultItemWithResult(
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
        if (item.id.isBlank()) {
            return SaveItemResult.ValidationFailure("Entry identifier cannot be blank.")
        }
        if (item.title.trim().isEmpty()) {
            return SaveItemResult.ValidationFailure("Entry title cannot be blank.")
        }
        val cleanedMainNameForCheck = mainRouteName.trim().ifEmpty { DEFAULT_MAIN_METHOD_NAME }
        val seenRouteNames = mutableSetOf(cleanedMainNameForCheck.lowercase())
        for (altRoute in alternativeRoutes) {
            val trimmedAltName = altRoute.route.name.trim()
            if (trimmedAltName.isEmpty()) {
                return SaveItemResult.ValidationFailure("Alternative method name cannot be empty.")
            }
            if (!seenRouteNames.add(trimmedAltName.lowercase())) {
                return SaveItemResult.ValidationFailure("Alternative method names must be unique (\"$trimmedAltName\" is duplicated).")
            }
        }
        return try {
            backupRestoreManager.withWriteLock {
                saveFaultInjector?.invoke()

                var customTypeToPersistOnSave: CustomTypeEntity? = null
                // P1-11, P2-14 & FIX 02: Authoritative Custom Type required & typed field validation
                if (!item.customTypeId.isNullOrBlank()) {
                    val existingCustomType = dao.getCustomTypeById(item.customTypeId)
                    val stagedCustomType = pendingCustomType?.takeIf { it.id == item.customTypeId }
                    val customType = existingCustomType ?: stagedCustomType
                        ?: return@withWriteLock SaveItemResult.ValidationFailure(
                            "Selected Custom Type '${item.customTypeId}' does not exist."
                        )
                    if (existingCustomType == null && stagedCustomType != null) {
                        val nowCt = System.currentTimeMillis()
                        val normalizedDefs = VaultJsonSerializers
                            .decodeCustomFieldDefinitions(stagedCustomType.fieldsJson)
                            .mapIndexed { idx, f -> f.copy(orderIndex = idx) }
                        customTypeToPersistOnSave = stagedCustomType.copy(
                            name = stagedCustomType.name.trim(),
                            fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(normalizedDefs),
                            createdAt = stagedCustomType.createdAt.takeIf { it > 0L } ?: nowCt,
                            updatedAt = nowCt
                        )
                    }
                    val defs = VaultJsonSerializers.decodeCustomFieldDefinitions(customType.fieldsJson)
                    val valuesMap = VaultJsonSerializers.decodeCustomFieldsMap(item.customFieldsJson)
                    val fieldErrors = SmartImportExportEngine.validateAllCustomFields(
                        definitions = defs,
                        valuesMap = valuesMap,
                        checkRequired = true
                    )
                    if (fieldErrors.isNotEmpty()) {
                        return@withWriteLock SaveItemResult.ValidationFailure(fieldErrors.values.first())
                    }
                }

                val existing = dao.getItemById(item.id)
                if (!forceOverwrite && expectedPreviousUpdatedAt != null) {
                    if (existing != null && existing.updatedAt != expectedPreviousUpdatedAt) {
                        val currentDetails = getItemWithDetailsById(existing.id)
                        return@withWriteLock SaveItemResult.Conflict(
                            itemId = item.id,
                            currentDatabaseItem = currentDetails,
                            expectedUpdatedAt = expectedPreviousUpdatedAt,
                            actualUpdatedAt = existing.updatedAt,
                            message = "Save conflict: \"${existing.title}\" was modified since you opened it. Your unsaved edits are preserved."
                        )
                    }
                    if (existing == null && isEditingExisting) {
                        return@withWriteLock SaveItemResult.Conflict(
                            itemId = item.id,
                            currentDatabaseItem = null,
                            expectedUpdatedAt = expectedPreviousUpdatedAt,
                            actualUpdatedAt = -1L,
                            message = "Save conflict: this entry was deleted while you were editing it. Choose Overwrite & Save to recreate it with your edits."
                        )
                    }
                }

                val previousSteps = dao.getStepsForItemSnapshot(item.id)
                val previousStepImages = previousSteps.flatMap {
                    VaultJsonSerializers.decodeStringList(it.imagesJson)
                }.toSet()

                val now = System.currentTimeMillis()
                val preservedCreatedAt = existing?.createdAt ?: item.createdAt.takeIf { it > 0L } ?: now
                val monotonicUpdatedAt = if (existing != null) {
                    maxOf(now, existing.updatedAt + 1L)
                } else {
                    maxOf(now, preservedCreatedAt)
                }

                val entityToPersist = item.copy(
                    title = item.title.trim(),
                    createdAt = preservedCreatedAt,
                    updatedAt = monotonicUpdatedAt
                )

                val mainSteps = steps.mapIndexed { idx, step ->
                    step.copy(
                        id = step.id.ifBlank { UUID.randomUUID().toString() },
                        itemId = item.id,
                        routeId = MAIN_METHOD_ROUTE_ID,
                        stepOrder = idx + 1
                    )
                }

                val sanitizedAltRoutes = alternativeRoutes.mapIndexed { idx, routeWithSteps ->
                    val cleanedName = routeWithSteps.route.name.trim()
                    val routeId = routeWithSteps.route.id
                        .takeIf { it.isNotBlank() && it != MAIN_METHOD_ROUTE_ID && it != "${item.id}_$MAIN_METHOD_ROUTE_ID" }
                        ?: UUID.randomUUID().toString()
                    val routeEntity = MethodRouteEntity(
                        id = routeId,
                        itemId = item.id,
                        name = cleanedName,
                        description = routeWithSteps.route.description.trim(),
                        routeOrder = idx + 1,
                        isMain = false
                    )
                    val routeSteps = routeWithSteps.steps.mapIndexed { stepIdx, step ->
                        step.copy(
                            id = step.id.ifBlank { UUID.randomUUID().toString() },
                            itemId = item.id,
                            routeId = routeId,
                            stepOrder = stepIdx + 1
                        )
                    }
                    routeEntity to routeSteps
                }

                val routesToPersist = buildList {
                    val cleanedMainName = mainRouteName.trim().ifEmpty { DEFAULT_MAIN_METHOD_NAME }
                    val cleanedMainDesc = mainRouteDescription.trim()
                    if (sanitizedAltRoutes.isNotEmpty() ||
                        cleanedMainName != DEFAULT_MAIN_METHOD_NAME ||
                        cleanedMainDesc.isNotEmpty()
                    ) {
                        add(
                            MethodRouteEntity(
                                id = "${item.id}_$MAIN_METHOD_ROUTE_ID",
                                itemId = item.id,
                                name = cleanedMainName,
                                description = cleanedMainDesc,
                                routeOrder = 0,
                                isMain = true
                            )
                        )
                    }
                    sanitizedAltRoutes.forEach { (routeEntity, _) ->
                        add(routeEntity)
                    }
                }

                val allStepsToPersist = mainSteps + sanitizedAltRoutes.flatMap { it.second }
                // Atomic SQL transaction: saves pendingCustomType (if any), item, routes, steps, and prunes stale follow runs together
                dao.saveItemWithSteps(
                    item = entityToPersist,
                    steps = allStepsToPersist,
                    routes = routesToPersist,
                    pendingCustomType = customTypeToPersistOnSave
                )

                val verified = dao.getItemById(item.id)
                if (verified == null || verified.updatedAt != monotonicUpdatedAt) {
                    return@withWriteLock SaveItemResult.StorageFailure(
                        reason = "Save failed: database write could not be verified.",
                        diagnosticCode = "ERR_SAVE_VERIFY_FAILED"
                    )
                }

                // Clean up any unreferenced step images ONLY after the SQL transaction has committed and been verified
                val newStepImages = allStepsToPersist.flatMap {
                    VaultJsonSerializers.decodeStringList(it.imagesJson)
                }.toSet()
                val removedStepImages = previousStepImages - newStepImages
                if (removedStepImages.isNotEmpty()) {
                    val remainingItems = dao.getAllItemsSnapshot()
                    val remainingSteps = dao.getAllStepsSnapshot()
                    removedStepImages.forEach { imgPath ->
                        attachmentStorage.deleteLocalFileIfUnreferenced(imgPath, remainingItems, remainingSteps)
                    }
                }

                SaveItemResult.Success(
                    itemId = item.id,
                    updatedAt = monotonicUpdatedAt
                )
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (e: UnresolvedVaultRecoveryException) {
            SaveItemResult.StorageFailure(
                reason = e.message ?: "Vault writes are locked until recovery completes.",
                diagnosticCode = e.diagnosticCode
            )
        } catch (e: Exception) {
            SaveItemResult.StorageFailure(
                reason = "Failed to save entry: ${e.localizedMessage ?: e.javaClass.simpleName}",
                diagnosticCode = "ERR_SAVE_STORAGE_EXCEPTION"
            )
        }
    }

    /**
     * Saves a vault item and its ordered steps while preserving original createdAt
     * and ensuring monotonic updatedAt timestamps.
     * If `expectedPreviousUpdatedAt` is provided and does not match the stored `updatedAt`,
     * the stale write is rejected and `false` is returned so newer concurrent edits are never silently overwritten.
     */
    suspend fun saveVaultItem(
        item: VaultItemEntity,
        steps: List<MethodStepEntity>,
        expectedPreviousUpdatedAt: Long? = null,
        mainRouteName: String = DEFAULT_MAIN_METHOD_NAME,
        mainRouteDescription: String = "",
        alternativeRoutes: List<MethodRouteWithSteps> = emptyList(),
        pendingCustomType: CustomTypeEntity? = null
    ): Boolean {
        return when (
            val result = saveVaultItemWithResult(
                item = item,
                steps = steps,
                expectedPreviousUpdatedAt = expectedPreviousUpdatedAt,
                isEditingExisting = expectedPreviousUpdatedAt != null,
                forceOverwrite = false,
                mainRouteName = mainRouteName,
                mainRouteDescription = mainRouteDescription,
                alternativeRoutes = alternativeRoutes,
                pendingCustomType = pendingCustomType
            )
        ) {
            is SaveItemResult.Success -> true
            is SaveItemResult.Conflict -> false
            is SaveItemResult.ValidationFailure -> false
            is SaveItemResult.StorageFailure -> {
                if (result.diagnosticCode == "ERR_UNRESOLVED_RECOVERY") {
                    throw UnresolvedVaultRecoveryException(result.reason, result.diagnosticCode)
                }
                false
            }
        }
    }

    suspend fun createQuickNoteToInbox(title: String, content: String): String = backupRestoreManager.withWriteLock {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val cleanTitle = title.trim().ifEmpty {
            content.trim().lineSequence().firstOrNull()?.take(48)?.ifBlank { "Quick Note" } ?: "Quick Note"
        }
        val entity = VaultItemEntity(
            id = id,
            title = cleanTitle,
            description = "Quick capture to Inbox",
            content = content,
            category = VaultCategory.INBOX.id,
            createdAt = now,
            updatedAt = now
        )
        dao.saveItemWithSteps(entity, emptyList())
        id
    }

    suspend fun duplicateVaultItem(source: VaultItemWithDetails): String = backupRestoreManager.withWriteLock {
        val now = System.currentTimeMillis()
        val newItemId = UUID.randomUUID().toString()
        val copyEntity = source.entity.copy(
            id = newItemId,
            title = "${source.entity.title} (Copy)",
            isPinned = false,
            createdAt = now,
            updatedAt = now
        )
        val copiedRoutes = mutableListOf<MethodRouteEntity>()
        val copiedSteps = mutableListOf<MethodStepEntity>()

        source.effectiveMethodRoutes.forEachIndexed { routeIdx, routeWithSteps ->
            val isMain = routeWithSteps.route.isMain || routeWithSteps.route.id == MAIN_METHOD_ROUTE_ID
            val routeEntityId = if (isMain) "${newItemId}_$MAIN_METHOD_ROUTE_ID" else UUID.randomUUID().toString()
            val stepRouteId = if (isMain) MAIN_METHOD_ROUTE_ID else routeEntityId
            if (!isMain ||
                routeWithSteps.route.name != DEFAULT_MAIN_METHOD_NAME ||
                routeWithSteps.route.description.isNotBlank() ||
                source.hasAlternativeMethods
            ) {
                copiedRoutes.add(
                    routeWithSteps.route.copy(
                        id = routeEntityId,
                        itemId = newItemId,
                        routeOrder = if (isMain) 0 else routeIdx,
                        isMain = isMain
                    )
                )
            }
            routeWithSteps.steps.forEachIndexed { stepIdx, step ->
                copiedSteps.add(
                    step.copy(
                        id = UUID.randomUUID().toString(),
                        itemId = newItemId,
                        routeId = stepRouteId,
                        stepOrder = stepIdx + 1
                    )
                )
            }
        }
        dao.saveItemWithSteps(copyEntity, copiedSteps, copiedRoutes)
        newItemId
    }

    /**
     * Deletes a single alternative method route from [itemId] without deleting the parent Method
     * or the Main Method route/steps/progress, and cleans up all steps, follow runs, and unreferenced
     * step images belonging to the deleted alternative route.
     */
    suspend fun deleteAlternativeRoute(itemId: String, routeId: String): Boolean = backupRestoreManager.withWriteLock {
        val normalizedId = routeId.trim()
        if (normalizedId.isEmpty() ||
            normalizedId == MAIN_METHOD_ROUTE_ID ||
            normalizedId == "${itemId}_$MAIN_METHOD_ROUTE_ID"
        ) {
            return@withWriteLock false
        }
        val routeStepsToDelete = dao.getStepsForRouteSnapshot(itemId, normalizedId)
        val deletedStepImages = routeStepsToDelete.flatMap {
            VaultJsonSerializers.decodeStringList(it.imagesJson)
        }.toSet()

        val committed = try {
            dao.deleteAlternativeRouteAtomic(
                itemId = itemId,
                normalizedRouteId = normalizedId,
                updatedAt = System.currentTimeMillis()
            )
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return@withWriteLock false
        }
        if (!committed) {
            return@withWriteLock false
        }

        // Physical attachment cleanup executes ONLY after the SQL transaction commits
        if (deletedStepImages.isNotEmpty()) {
            val remainingItems = dao.getAllItemsSnapshot()
            val remainingSteps = dao.getAllStepsSnapshot()
            deletedStepImages.forEach { imgPath ->
                attachmentStorage.deleteLocalFileIfUnreferenced(imgPath, remainingItems, remainingSteps)
            }
        }
        true
    }

    suspend fun toggleFavorite(itemId: String) = backupRestoreManager.withWriteLock {
        val current = dao.getItemById(itemId) ?: return@withWriteLock
        val nextTs = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
        dao.updateItem(current.copy(isFavorite = !current.isFavorite, updatedAt = nextTs))
    }

    suspend fun togglePinned(itemId: String) = backupRestoreManager.withWriteLock {
        val current = dao.getItemById(itemId) ?: return@withWriteLock
        val nextTs = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
        dao.updateItem(current.copy(isPinned = !current.isPinned, updatedAt = nextTs))
    }

    suspend fun toggleArchived(itemId: String) = backupRestoreManager.withWriteLock {
        val current = dao.getItemById(itemId) ?: return@withWriteLock
        val nextArchived = !current.isArchived
        val nextTs = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
        dao.updateItem(
            current.copy(
                isArchived = nextArchived,
                isPinned = if (nextArchived) false else current.isPinned,
                updatedAt = nextTs
            )
        )
    }

    suspend fun moveItemToFolder(itemId: String, folderId: String?) = backupRestoreManager.withWriteLock {
        val current = dao.getItemById(itemId) ?: return@withWriteLock
        val nextTs = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
        dao.updateItem(current.copy(folderId = folderId, updatedAt = nextTs))
    }

    suspend fun updateIdeaStatus(itemId: String, status: IdeaStatus) = backupRestoreManager.withWriteLock {
        val current = dao.getItemById(itemId) ?: return@withWriteLock
        val nextTs = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
        dao.updateItem(current.copy(ideaStatus = status.id, updatedAt = nextTs))
    }

    suspend fun toggleChecklistBlock(itemId: String, blockId: String) = backupRestoreManager.withWriteLock {
        val current = dao.getItemById(itemId) ?: return@withWriteLock
        val blocks = VaultJsonSerializers.decodeRichBlocks(current.richBlocksJson).map { block ->
            if (block.id == blockId) block.copy(checked = !block.checked) else block
        }
        val nextTs = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
        dao.updateItem(
            current.copy(
                richBlocksJson = VaultJsonSerializers.encodeRichBlocks(blocks),
                updatedAt = nextTs
            )
        )
    }

    suspend fun addRichBlockToItem(itemId: String, newBlock: RichContentBlock) = backupRestoreManager.withWriteLock {
        val current = dao.getItemById(itemId) ?: return@withWriteLock
        val blocks = VaultJsonSerializers.decodeRichBlocks(current.richBlocksJson) + newBlock
        val nextTs = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
        dao.updateItem(
            current.copy(
                richBlocksJson = VaultJsonSerializers.encodeRichBlocks(blocks),
                updatedAt = nextTs
            )
        )
    }

    suspend fun removeAttachmentFromItem(itemId: String, attachmentId: String) = backupRestoreManager.withWriteLock {
        val current = dao.getItemById(itemId) ?: return@withWriteLock
        val decoded = VaultJsonSerializers.decodeAttachments(current.attachmentsJson)
        val removed = decoded.firstOrNull { it.id == attachmentId }
        val updated = decoded.filterNot { it.id == attachmentId }
        val nextTs = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
        dao.updateItem(
            current.copy(
                attachmentsJson = VaultJsonSerializers.encodeAttachments(updated),
                updatedAt = nextTs
            )
        )
        if (removed != null && removed.type != AttachmentType.LINK) {
            val allRemaining = dao.getAllItemsSnapshot()
            val allSteps = dao.getAllStepsSnapshot()
            attachmentStorage.deleteLocalFileIfUnreferenced(removed.uriOrUrl, allRemaining, allSteps)
        }
    }

    suspend fun addAttachmentToItem(itemId: String, attachment: AttachmentItem): Boolean = backupRestoreManager.withWriteLock {
        val current = dao.getItemById(itemId) ?: return@withWriteLock false
        val existingList = VaultJsonSerializers.decodeAttachments(current.attachmentsJson)
        if (AttachmentStorageManager.isDuplicateAttachment(existingList, attachment)) {
            return@withWriteLock false
        }
        val updated = existingList + attachment
        val nextTs = maxOf(System.currentTimeMillis(), current.updatedAt + 1L)
        dao.updateItem(
            current.copy(
                attachmentsJson = VaultJsonSerializers.encodeAttachments(updated),
                updatedAt = nextTs
            )
        )
        true
    }

    // Method step reordering (persists new stepOrder for the specified route without losing data)
    suspend fun reorderMethodSteps(
        itemId: String,
        orderedStepIds: List<String>,
        routeId: String = MAIN_METHOD_ROUTE_ID
    ) = backupRestoreManager.withWriteLock {
        val normalizedRouteId = routeId.ifBlank { MAIN_METHOD_ROUTE_ID }
        val routeSteps = dao.getStepsForRouteSnapshot(itemId, normalizedRouteId)
        val byId = routeSteps.associateBy { it.id }
        val reordered = orderedStepIds.mapIndexedNotNull { index, id ->
            byId[id]?.copy(stepOrder = index + 1, routeId = normalizedRouteId)
        }
        if (reordered.size == routeSteps.size && reordered.isNotEmpty()) {
            dao.deleteStepsForRoute(itemId, normalizedRouteId)
            dao.insertSteps(reordered)
            dao.getItemById(itemId)?.let {
                val nextTs = maxOf(System.currentTimeMillis(), it.updatedAt + 1L)
                dao.updateItem(it.copy(updatedAt = nextTs))
            }
        }
    }

    // Follow Mode progress (tracked separately per route and synced with legacy table for Main Method!)
    suspend fun toggleFollowStep(
        itemId: String,
        stepId: String,
        routeId: String = MAIN_METHOD_ROUTE_ID
    ) = backupRestoreManager.withWriteLock {
        val normalizedRouteId = routeId.ifBlank { MAIN_METHOD_ROUTE_ID }
        val existingRouteRun = dao.getRouteFollowRun(itemId, normalizedRouteId)
        val legacyMainRun = if (normalizedRouteId == MAIN_METHOD_ROUTE_ID) dao.getFollowRun(itemId) else null

        val currentSet = existingRouteRun?.let {
            VaultJsonSerializers.decodeStringList(it.completedStepIdsJson).toMutableSet()
        } ?: legacyMainRun?.let {
            VaultJsonSerializers.decodeStringList(it.completedStepIdsJson).toMutableSet()
        } ?: mutableSetOf()

        if (currentSet.contains(stepId)) {
            currentSet.remove(stepId)
        } else {
            currentSet.add(stepId)
        }

        val now = System.currentTimeMillis()
        val encodedIds = VaultJsonSerializers.encodeStringList(currentSet.toList())
        val startedAt = existingRouteRun?.startedAt ?: legacyMainRun?.startedAt ?: now

        dao.upsertRouteFollowRun(
            MethodRouteFollowRunEntity(
                itemId = itemId,
                routeId = normalizedRouteId,
                completedStepIdsJson = encodedIds,
                startedAt = startedAt,
                updatedAt = now
            )
        )
        if (normalizedRouteId == MAIN_METHOD_ROUTE_ID) {
            dao.upsertFollowRun(
                MethodFollowRunEntity(
                    itemId = itemId,
                    completedStepIdsJson = encodedIds,
                    startedAt = startedAt,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun resetFollowProgress(
        itemId: String,
        routeId: String = MAIN_METHOD_ROUTE_ID
    ) = backupRestoreManager.withWriteLock {
        val normalizedRouteId = routeId.ifBlank { MAIN_METHOD_ROUTE_ID }
        dao.deleteRouteFollowRun(itemId, normalizedRouteId)
        if (normalizedRouteId == MAIN_METHOD_ROUTE_ID) {
            dao.deleteFollowRun(itemId)
        }
    }


    // Folders
    suspend fun createFolder(name: String, category: VaultCategory, colorHex: String): FolderEntity =
        backupRestoreManager.withWriteLock {
            val now = System.currentTimeMillis()
            val folder = FolderEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                category = category.id,
                colorHex = colorHex,
                createdAt = now,
                updatedAt = now
            )
            dao.upsertFolder(folder)
            folder
        }

    suspend fun renameFolder(folder: FolderEntity, newName: String) = backupRestoreManager.withWriteLock {
        val cleaned = newName.trim()
        if (cleaned.isNotEmpty()) {
            val nextTs = maxOf(System.currentTimeMillis(), folder.updatedAt + 1L)
            dao.upsertFolder(folder.copy(name = cleaned, updatedAt = nextTs))
        }
    }

    suspend fun deleteFolderPreservingItems(folderId: String) = backupRestoreManager.withWriteLock {
        dao.safeDeleteFolderPreserveItems(folderId)
    }

    // Trash & Permanent Delete Safety
    suspend fun moveToTrash(itemId: String) = backupRestoreManager.withWriteLock {
        dao.moveItemToTrash(itemId)
    }

    suspend fun restoreFromTrash(itemId: String) = backupRestoreManager.withWriteLock {
        dao.restoreItemFromTrash(itemId)
    }

    suspend fun permanentlyDelete(itemId: String) = backupRestoreManager.withWriteLock {
        val target = dao.getItemById(itemId)
        val targetSteps = dao.getStepsForItemSnapshot(itemId)
        val targetAttachments = target?.let { VaultJsonSerializers.decodeAttachments(it.attachmentsJson) }.orEmpty()
        val stepImagePaths = targetSteps.flatMap { VaultJsonSerializers.decodeStringList(it.imagesJson) }
        dao.permanentlyDeleteItem(itemId)
        if (targetAttachments.isNotEmpty() || stepImagePaths.isNotEmpty()) {
            val remaining = dao.getAllItemsSnapshot()
            val remainingSteps = dao.getAllStepsSnapshot()
            targetAttachments.forEach { att ->
                if (att.type != AttachmentType.LINK) {
                    attachmentStorage.deleteLocalFileIfUnreferenced(att.uriOrUrl, remaining, remainingSteps)
                }
            }
            stepImagePaths.forEach { imgPath ->
                attachmentStorage.deleteLocalFileIfUnreferenced(imgPath, remaining, remainingSteps)
            }
        }
    }

    suspend fun emptyTrash() = backupRestoreManager.withWriteLock {
        val trashed = dao.getAllItemsSnapshot().filter { it.isTrashed }
        val trashedIds = trashed.map { it.id }.toSet()
        val trashedSteps = dao.getAllStepsSnapshot().filter { it.itemId in trashedIds }
        val candidateFiles = (
            trashed.flatMap { VaultJsonSerializers.decodeAttachments(it.attachmentsJson) }
                .filter { it.type != AttachmentType.LINK }
                .map { it.uriOrUrl } +
                trashedSteps.flatMap { VaultJsonSerializers.decodeStringList(it.imagesJson) }
            ).distinct()
        dao.emptyTrash()
        if (candidateFiles.isNotEmpty()) {
            val remaining = dao.getAllItemsSnapshot()
            val remainingSteps = dao.getAllStepsSnapshot()
            candidateFiles.forEach { path ->
                attachmentStorage.deleteLocalFileIfUnreferenced(path, remaining, remainingSteps)
            }
        }
    }

    // Backup & Restore
    suspend fun exportBackup(outputStream: OutputStream): BackupRestoreResult =
        backupRestoreManager.exportBackupToStream(outputStream)

    suspend fun exportBackupToFile(targetFile: File): BackupRestoreResult =
        backupRestoreManager.exportBackupToFile(targetFile)

    suspend fun validateBackup(inputStream: InputStream): Result<ValidatedBackupPayload> =
        backupRestoreManager.validateBackupStream(inputStream)

    suspend fun applyBackup(
        payload: ValidatedBackupPayload,
        faultInjector: (() -> Unit)? = null
    ): BackupRestoreResult =
        backupRestoreManager.applyValidatedBackup(payload, faultInjector)

    suspend fun retryPendingRecovery(): BackupRestoreResult =
        backupRestoreManager.retryPendingRecovery()

    fun dismissPendingBackup(payload: ValidatedBackupPayload?) {
        backupRestoreManager.cleanupStagingPayload(payload)
    }

    /**
     * Optional sample templates action for users who click "Load Starter Example Templates"
     * in the empty state to experience the reference cards immediately.
     */
    suspend fun seedSampleTemplates() = backupRestoreManager.withWriteLock {
        val now = System.currentTimeMillis()
        val folderMobile = FolderEntity(
            id = "sample_folder_mobile",
            name = "Mobile App Ideas",
            category = VaultCategory.IDEA.id,
            colorHex = "#FF7654",
            createdAt = now - 3600_000L,
            updatedAt = now - 3600_000L
        )
        val folderDesign = FolderEntity(
            id = "sample_folder_design",
            name = "Design Systems",
            category = VaultCategory.KNOWLEDGE.id,
            colorHex = "#A7DB7B",
            createdAt = now - 3500_000L,
            updatedAt = now - 3500_000L
        )
        dao.upsertFolder(folderMobile)
        dao.upsertFolder(folderDesign)

        val knowledgeBlocks = listOf(
            RichContentBlock(
                id = "kb_block_1",
                type = RichBlockType.PARAGRAPH,
                title = "01/ identification of cast iron",
                content = "The distinctive feature of grey cast iron is that it has a higher content of the chemical element silicon and carbon in graphite flake form."
            ),
            RichContentBlock(
                id = "kb_block_2",
                type = RichBlockType.PARAGRAPH,
                title = "02/ orthographic projection rules",
                content = "Maintain consistent line weights between visible edges, hidden dashed contours, and center axes across front, top, and side views."
            )
        )
        val itemKnowledge = VaultItemEntity(
            id = "sample_item_drawing",
            title = "technical drawing fundamentals",
            description = "Class notes",
            content = "Core projection principles, metallurgy notes, and sectioning tolerances for mechanical blueprints.",
            category = VaultCategory.IDEA.id,
            folderId = folderMobile.id,
            tagsJson = VaultJsonSerializers.encodeStringList(listOf("engineering", "class-notes", "cad")),
            richBlocksJson = VaultJsonSerializers.encodeRichBlocks(knowledgeBlocks),
            ideaStatus = IdeaStatus.EXPLORING.id,
            isFavorite = true,
            isPinned = true,
            createdAt = now - 7200_000L,
            updatedAt = now - 1200_000L
        )
        dao.saveItemWithSteps(itemKnowledge, emptyList())

        val methodId = "sample_item_ai_workflow"
        val itemMethod = VaultItemEntity(
            id = methodId,
            title = "AI App Development Workflow",
            description = "Step by step process I use to build Android or Web apps using AI tools like Google AI Studio, Arena AI etc.",
            content = "Repeatable end-to-end product design and engineering workflow.",
            category = VaultCategory.METHOD.id,
            folderId = folderMobile.id,
            tagsJson = VaultJsonSerializers.encodeStringList(listOf("android", "workflow", "ai-studio")),
            ideaStatus = IdeaStatus.COMPLETED.id,
            platform = "Google AI Studio, Arena AI",
            estimatedTime = "~ 30 minutes",
            isFavorite = true,
            isPinned = true,
            createdAt = now - 5400_000L,
            updatedAt = now - 900_000L
        )
        val methodSteps = listOf(
            MethodStepEntity(
                id = "step_ai_1",
                itemId = methodId,
                stepOrder = 1,
                title = "Collect reference images",
                description = "Find UI inspiration from Pinterest, Dribbble or Figma."
            ),
            MethodStepEntity(
                id = "step_ai_2",
                itemId = methodId,
                stepOrder = 2,
                title = "Write detailed prompt",
                description = "Give clear and specific architectural & visual prompt to AI Studio."
            ),
            MethodStepEntity(
                id = "step_ai_3",
                itemId = methodId,
                stepOrder = 3,
                title = "Build the project",
                description = "Generate code and review interactive flows in Google AI Studio."
            ),
            MethodStepEntity(
                id = "step_ai_4",
                itemId = methodId,
                stepOrder = 4,
                title = "Test and improve the application",
                description = "Verify edge cases, offline persistence, and smooth animations."
            )
        )
        dao.saveItemWithSteps(itemMethod, methodSteps)

        val todoBlocks = listOf(
            RichContentBlock(
                id = "todo_1",
                type = RichBlockType.CHECKLIST,
                title = "mobile app",
                content = "research",
                checked = true
            ),
            RichContentBlock(
                id = "todo_2",
                type = RichBlockType.CHECKLIST,
                title = "wireframes",
                content = "prepare wireframes for main flow",
                checked = true
            ),
            RichContentBlock(
                id = "todo_3",
                type = RichBlockType.CHECKLIST,
                title = "redesign",
                content = "make a customer journey map",
                checked = false
            ),
            RichContentBlock(
                id = "todo_4",
                type = RichBlockType.CHECKLIST,
                title = "target audience",
                content = "audience analysis",
                checked = false
            ),
            RichContentBlock(
                id = "todo_5",
                type = RichBlockType.CHECKLIST,
                title = "website",
                content = "research",
                checked = false
            )
        )
        val itemTodo = VaultItemEntity(
            id = "sample_item_todo",
            title = "to-do list",
            description = "Product launch milestones & UX sprint checklist",
            content = "",
            category = VaultCategory.METHOD.id,
            tagsJson = VaultJsonSerializers.encodeStringList(listOf("sprint", "ux", "checklist")),
            richBlocksJson = VaultJsonSerializers.encodeRichBlocks(todoBlocks),
            ideaStatus = IdeaStatus.DEVELOPING.id,
            estimatedTime = "~ 2 hours",
            createdAt = now - 3600_000L,
            updatedAt = now - 600_000L
        )
        dao.saveItemWithSteps(itemTodo, emptyList())

        val itemPrompt = VaultItemEntity(
            id = "sample_item_prompt",
            title = "Senior Android Architecture Prompt",
            description = "Reusable system prompt for offline-first Jetpack Compose + Room applications.",
            content = "ROLE: Senior Android Engineer & Motion Designer\nSTACK: Kotlin, Jetpack Compose, Material 3, Room, DataStore\n\nREQUIREMENTS:\n1. Use custom Path shapes for editorial cards.\n2. Persist all state in Room with reactive Flows.\n3. Ensure every interactive element has accessible touch targets.",
            category = VaultCategory.PROMPT.id,
            platform = "Google AI Studio / Gemini",
            tagsJson = VaultJsonSerializers.encodeStringList(listOf("prompt", "compose", "kotlin")),
            notes = "Works great with attached UI reference mockups.",
            isFavorite = true,
            createdAt = now - 2400_000L,
            updatedAt = now - 300_000L
        )
        dao.saveItemWithSteps(itemPrompt, emptyList())
    }
}
