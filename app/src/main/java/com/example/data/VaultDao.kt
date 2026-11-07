package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    // Active (non-trashed, non-archived) items
    @Query("SELECT * FROM vault_items WHERE isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeActiveItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrashed = 0 AND isArchived = 0 AND isFavorite = 1 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeFavoriteItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrashed = 0 AND isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchivedItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE isTrashed = 1 ORDER BY updatedAt DESC")
    fun observeTrashedItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :itemId LIMIT 1")
    fun observeItemById(itemId: String): Flow<VaultItemEntity?>

    @Query("SELECT * FROM vault_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items")
    suspend fun getAllItemsSnapshot(): List<VaultItemEntity>

    // Real category counts from Room database
    @Query("SELECT category, COUNT(*) as count FROM vault_items WHERE isTrashed = 0 AND isArchived = 0 GROUP BY category")
    fun observeCategoryCounts(): Flow<List<CategoryCountRow>>

    // Real folder counts from Room database
    @Query("SELECT folderId, COUNT(*) as count FROM vault_items WHERE isTrashed = 0 AND isArchived = 0 AND folderId IS NOT NULL GROUP BY folderId")
    fun observeFolderCounts(): Flow<List<FolderCountRow>>

    // Folders
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersSnapshot(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("UPDATE vault_items SET folderId = NULL, updatedAt = :timestamp WHERE folderId = :folderId")
    suspend fun detachItemsFromFolder(folderId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderRow(folderId: String)

    // Delete safety: Deleting a folder NEVER silently destroys the content inside it.
    @Transaction
    suspend fun safeDeleteFolderPreserveItems(folderId: String) {
        detachItemsFromFolder(folderId)
        deleteFolderRow(folderId)
    }

    // Method routes (Main Method + optional Alternative Methods)
    @Query("SELECT * FROM method_routes ORDER BY itemId ASC, routeOrder ASC")
    fun observeAllMethodRoutes(): Flow<List<MethodRouteEntity>>

    @Query("SELECT * FROM method_routes WHERE itemId = :itemId ORDER BY routeOrder ASC")
    suspend fun getRoutesForItemSnapshot(itemId: String): List<MethodRouteEntity>

    @Query("SELECT * FROM method_routes ORDER BY itemId ASC, routeOrder ASC")
    suspend fun getAllRoutesSnapshot(): List<MethodRouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<MethodRouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoute(route: MethodRouteEntity)

    @Query("DELETE FROM method_routes WHERE itemId = :itemId")
    suspend fun deleteRoutesForItem(itemId: String)

    @Query("DELETE FROM method_routes WHERE itemId = :itemId AND id = :routeId")
    suspend fun deleteRouteById(itemId: String, routeId: String)

    // Method steps
    @Query("SELECT * FROM method_steps ORDER BY itemId ASC, routeId ASC, stepOrder ASC")
    fun observeAllMethodSteps(): Flow<List<MethodStepEntity>>

    @Query("SELECT * FROM method_steps WHERE itemId = :itemId ORDER BY stepOrder ASC")
    fun observeStepsForItem(itemId: String): Flow<List<MethodStepEntity>>

    @Query("SELECT * FROM method_steps WHERE itemId = :itemId ORDER BY stepOrder ASC")
    suspend fun getStepsForItemSnapshot(itemId: String): List<MethodStepEntity>

    @Query("SELECT * FROM method_steps WHERE itemId = :itemId AND routeId = :routeId ORDER BY stepOrder ASC")
    suspend fun getStepsForRouteSnapshot(itemId: String, routeId: String): List<MethodStepEntity>

    @Query("SELECT * FROM method_steps ORDER BY itemId ASC, routeId ASC, stepOrder ASC")
    suspend fun getAllStepsSnapshot(): List<MethodStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSteps(steps: List<MethodStepEntity>)

    @Query("DELETE FROM method_steps WHERE itemId = :itemId")
    suspend fun deleteStepsForItem(itemId: String)

    @Query("DELETE FROM method_steps WHERE itemId = :itemId AND routeId = :routeId")
    suspend fun deleteStepsForRoute(itemId: String, routeId: String)

    // Method Follow Mode (tracked separately per route and synced with legacy table for Main Method)
    @Query("SELECT * FROM method_follow_runs")
    fun observeAllFollowRuns(): Flow<List<MethodFollowRunEntity>>

    @Query("SELECT * FROM method_follow_runs")
    suspend fun getAllFollowRunsSnapshot(): List<MethodFollowRunEntity>

    @Query("SELECT * FROM method_follow_runs WHERE itemId = :itemId LIMIT 1")
    suspend fun getFollowRun(itemId: String): MethodFollowRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFollowRun(run: MethodFollowRunEntity)

    @Query("DELETE FROM method_follow_runs WHERE itemId = :itemId")
    suspend fun deleteFollowRun(itemId: String)

    @Query("SELECT * FROM method_route_follow_runs")
    fun observeAllRouteFollowRuns(): Flow<List<MethodRouteFollowRunEntity>>

    @Query("SELECT * FROM method_route_follow_runs")
    suspend fun getAllRouteFollowRunsSnapshot(): List<MethodRouteFollowRunEntity>

    @Query("SELECT * FROM method_route_follow_runs WHERE itemId = :itemId AND routeId = :routeId LIMIT 1")
    suspend fun getRouteFollowRun(itemId: String, routeId: String): MethodRouteFollowRunEntity?

    @Query("SELECT * FROM method_route_follow_runs WHERE itemId = :itemId")
    suspend fun getRouteFollowRunsForItemSnapshot(itemId: String): List<MethodRouteFollowRunEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRouteFollowRun(run: MethodRouteFollowRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRouteFollowRunsBatch(runs: List<MethodRouteFollowRunEntity>)

    @Query("DELETE FROM method_route_follow_runs WHERE itemId = :itemId AND routeId = :routeId")
    suspend fun deleteRouteFollowRun(itemId: String, routeId: String)

    @Query("DELETE FROM method_route_follow_runs WHERE itemId = :itemId")
    suspend fun deleteRouteFollowRunsForItem(itemId: String)

    // Item CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: VaultItemEntity)

    @Update
    suspend fun updateItem(item: VaultItemEntity)

    @Transaction
    suspend fun saveItemWithSteps(
        item: VaultItemEntity,
        steps: List<MethodStepEntity>,
        routes: List<MethodRouteEntity> = emptyList(),
        pendingCustomType: CustomTypeEntity? = null
    ) {
        if (pendingCustomType != null) {
            upsertCustomType(pendingCustomType)
        }
        val previousRoutes = getRoutesForItemSnapshot(item.id)
        upsertItem(item)
        deleteStepsForItem(item.id)
        deleteRoutesForItem(item.id)
        if (routes.isNotEmpty()) {
            insertRoutes(routes.map { it.copy(itemId = item.id) })
        }
        val groupedByRoute = steps.groupBy { it.routeId.ifBlank { MAIN_METHOD_ROUTE_ID } }
        val orderedAll = mutableListOf<MethodStepEntity>()
        groupedByRoute.forEach { (routeId, routeSteps) ->
            routeSteps.forEachIndexed { index, step ->
                orderedAll.add(
                    step.copy(
                        itemId = item.id,
                        routeId = routeId,
                        stepOrder = index + 1
                    )
                )
            }
        }
        if (orderedAll.isNotEmpty()) {
            insertSteps(orderedAll)
        }

        // Fault hook after item, routes, and steps are written, before follow-run pruning
        VaultTransactionFaultHooks.onSaveAfterStepsBeforeFollowPrune?.invoke()

        // Atomically prune follow runs inside the same SQL transaction
        val mainSteps = orderedAll.filter { it.routeId == MAIN_METHOD_ROUTE_ID }
        val altRouteEntities = routes.filter {
            !it.isMain && it.id != MAIN_METHOD_ROUTE_ID && it.id != "${item.id}_$MAIN_METHOD_ROUTE_ID"
        }
        val activeRouteIds = if (mainSteps.isEmpty() && altRouteEntities.isEmpty()) {
            emptySet()
        } else {
            setOf(MAIN_METHOD_ROUTE_ID) + altRouteEntities.map { it.id }
        }

        getRouteFollowRunsForItemSnapshot(item.id).forEach { existingRun ->
            if (existingRun.routeId !in activeRouteIds) {
                deleteRouteFollowRun(item.id, existingRun.routeId)
            }
        }
        previousRoutes.forEach { oldRoute ->
            if (!oldRoute.isMain && oldRoute.id !in activeRouteIds) {
                deleteRouteFollowRun(item.id, oldRoute.id)
            }
        }

        val validMainStepIds = mainSteps.map { it.id }.toSet()
        getRouteFollowRun(item.id, MAIN_METHOD_ROUTE_ID)?.let { mainRun ->
            if (validMainStepIds.isEmpty()) {
                deleteRouteFollowRun(item.id, MAIN_METHOD_ROUTE_ID)
            } else {
                val currentIds = VaultJsonSerializers.decodeStringList(mainRun.completedStepIdsJson)
                val prunedIds = currentIds.filter { it in validMainStepIds }
                if (prunedIds.size != currentIds.size) {
                    upsertRouteFollowRun(
                        mainRun.copy(completedStepIdsJson = VaultJsonSerializers.encodeStringList(prunedIds))
                    )
                }
            }
        }
        getFollowRun(item.id)?.let { legacyRun ->
            if (validMainStepIds.isEmpty()) {
                deleteFollowRun(item.id)
            } else {
                val currentIds = VaultJsonSerializers.decodeStringList(legacyRun.completedStepIdsJson)
                val prunedIds = currentIds.filter { it in validMainStepIds }
                if (prunedIds.size != currentIds.size) {
                    upsertFollowRun(
                        legacyRun.copy(completedStepIdsJson = VaultJsonSerializers.encodeStringList(prunedIds))
                    )
                }
            }
        }
        altRouteEntities.forEach { altRoute ->
            val validStepIds = orderedAll.filter { it.routeId == altRoute.id }.map { it.id }.toSet()
            getRouteFollowRun(item.id, altRoute.id)?.let { altRun ->
                if (validStepIds.isEmpty()) {
                    deleteRouteFollowRun(item.id, altRoute.id)
                } else {
                    val currentIds = VaultJsonSerializers.decodeStringList(altRun.completedStepIdsJson)
                    val prunedIds = currentIds.filter { it in validStepIds }
                    if (prunedIds.size != currentIds.size) {
                        upsertRouteFollowRun(
                            altRun.copy(completedStepIdsJson = VaultJsonSerializers.encodeStringList(prunedIds))
                        )
                    }
                }
            }
        }
    }

    @Transaction
    suspend fun deleteAlternativeRouteAtomic(
        itemId: String,
        normalizedRouteId: String,
        updatedAt: Long
    ): Boolean {
        if (normalizedRouteId.isEmpty() ||
            normalizedRouteId == MAIN_METHOD_ROUTE_ID ||
            normalizedRouteId == "${itemId}_$MAIN_METHOD_ROUTE_ID"
        ) {
            return false
        }
        val parent = getItemById(itemId) ?: return false
        val existingRoutes = getRoutesForItemSnapshot(itemId)
        val targetRoute = existingRoutes.firstOrNull { it.id == normalizedRouteId && !it.isMain }
        val routeStepsToDelete = getStepsForRouteSnapshot(itemId, normalizedRouteId)
        if (targetRoute == null && routeStepsToDelete.isEmpty()) {
            return false
        }

        deleteRouteById(itemId, normalizedRouteId)
        deleteStepsForRoute(itemId, normalizedRouteId)
        deleteRouteFollowRun(itemId, normalizedRouteId)

        // Normalize remaining alternative route ordering
        val remainingRoutes = getRoutesForItemSnapshot(itemId)
        val remainingAltRoutes = remainingRoutes
            .filter { !it.isMain && it.id != MAIN_METHOD_ROUTE_ID && it.id != "${itemId}_$MAIN_METHOD_ROUTE_ID" }
            .sortedBy { it.routeOrder }
            .mapIndexed { idx, route -> route.copy(routeOrder = idx + 1) }
        val mainRoute = remainingRoutes.firstOrNull {
            it.isMain || it.id == MAIN_METHOD_ROUTE_ID || it.id == "${itemId}_$MAIN_METHOD_ROUTE_ID"
        }

        deleteRoutesForItem(itemId)

        // Fault injection hook mid-transaction (after route/step/progress deletes and before re-inserting routes & updating parent)
        VaultTransactionFaultHooks.onDeleteRouteMidTransaction?.invoke()

        val updatedRoutesToKeep = buildList {
            if (mainRoute != null &&
                (remainingAltRoutes.isNotEmpty() ||
                    mainRoute.name != DEFAULT_MAIN_METHOD_NAME ||
                    mainRoute.description.isNotBlank())
            ) {
                add(mainRoute.copy(id = "${itemId}_$MAIN_METHOD_ROUTE_ID", routeOrder = 0, isMain = true))
            }
            addAll(remainingAltRoutes)
        }
        if (updatedRoutesToKeep.isNotEmpty()) {
            insertRoutes(updatedRoutesToKeep)
        }

        val nextTs = maxOf(updatedAt, parent.updatedAt + 1L)
        updateItem(parent.copy(updatedAt = nextTs))
        return true
    }

    // Custom Types
    @Query("SELECT * FROM custom_types ORDER BY name ASC")
    fun observeAllCustomTypes(): Flow<List<CustomTypeEntity>>

    @Query("SELECT * FROM custom_types ORDER BY name ASC")
    suspend fun getAllCustomTypesSnapshot(): List<CustomTypeEntity>

    @Query("SELECT * FROM custom_types WHERE id = :customTypeId LIMIT 1")
    suspend fun getCustomTypeById(customTypeId: String): CustomTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomType(customType: CustomTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomTypesBatch(customTypes: List<CustomTypeEntity>)

    @Query("DELETE FROM custom_types WHERE id = :customTypeId")
    suspend fun deleteCustomTypeById(customTypeId: String)

    @Query("DELETE FROM custom_types")
    suspend fun clearAllCustomTypes()

    @Query("SELECT * FROM vault_items WHERE customTypeId = :customTypeId")
    suspend fun getItemsByCustomTypeSnapshot(customTypeId: String): List<VaultItemEntity>

    @Transaction
    suspend fun getConsistentVaultSnapshot(): VaultDatabaseSnapshot {
        return VaultDatabaseSnapshot(
            folders = getAllFoldersSnapshot(),
            items = getAllItemsSnapshot(),
            routes = getAllRoutesSnapshot(),
            steps = getAllStepsSnapshot(),
            followRuns = getAllFollowRunsSnapshot(),
            routeFollowRuns = getAllRouteFollowRunsSnapshot(),
            customTypes = getAllCustomTypesSnapshot()
        )
    }

    // Soft delete to Trash first
    @Query("UPDATE vault_items SET isTrashed = 1, isPinned = 0, updatedAt = :timestamp WHERE id = :itemId")
    suspend fun moveItemToTrash(itemId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE vault_items SET isTrashed = 0, updatedAt = :timestamp WHERE id = :itemId")
    suspend fun restoreItemFromTrash(itemId: String, timestamp: Long = System.currentTimeMillis())

    // Permanent delete
    @Query("DELETE FROM vault_items WHERE id = :itemId")
    suspend fun deleteItemRowPermanently(itemId: String)

    @Transaction
    suspend fun permanentlyDeleteItem(itemId: String) {
        deleteStepsForItem(itemId)
        deleteRoutesForItem(itemId)
        deleteFollowRun(itemId)
        deleteRouteFollowRunsForItem(itemId)
        deleteItemRowPermanently(itemId)
    }

    @Query("DELETE FROM method_routes WHERE itemId NOT IN (SELECT id FROM vault_items)")
    suspend fun deleteOrphanedRoutes()

    @Query("DELETE FROM method_steps WHERE itemId NOT IN (SELECT id FROM vault_items)")
    suspend fun deleteOrphanedSteps()

    @Query("DELETE FROM method_follow_runs WHERE itemId NOT IN (SELECT id FROM vault_items)")
    suspend fun deleteOrphanedFollowRuns()

    @Query("DELETE FROM method_route_follow_runs WHERE itemId NOT IN (SELECT id FROM vault_items)")
    suspend fun deleteOrphanedRouteFollowRuns()

    @Transaction
    suspend fun emptyTrash() {
        val trashed = getAllItemsSnapshot().filter { it.isTrashed }
        trashed.forEach { permanentlyDeleteItem(it.id) }
        deleteOrphanedRoutes()
        deleteOrphanedSteps()
        deleteOrphanedFollowRuns()
        deleteOrphanedRouteFollowRuns()
    }

    // Transactional full restore for validated Backup & Restore
    @Query("DELETE FROM method_route_follow_runs")
    suspend fun clearAllRouteFollowRuns()

    @Query("DELETE FROM method_follow_runs")
    suspend fun clearAllFollowRuns()

    @Query("DELETE FROM method_routes")
    suspend fun clearAllRoutes()

    @Query("DELETE FROM method_steps")
    suspend fun clearAllSteps()

    @Query("DELETE FROM vault_items")
    suspend fun clearAllItems()

    @Query("DELETE FROM folders")
    suspend fun clearAllFolders()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFoldersBatch(folders: List<FolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemsBatch(items: List<VaultItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowRunsBatch(runs: List<MethodFollowRunEntity>)

    @Transaction
    suspend fun replaceVaultWithValidatedBackup(
        folders: List<FolderEntity>,
        items: List<VaultItemEntity>,
        steps: List<MethodStepEntity>,
        followRuns: List<MethodFollowRunEntity>,
        routes: List<MethodRouteEntity> = emptyList(),
        routeFollowRuns: List<MethodRouteFollowRunEntity> = emptyList(),
        customTypes: List<CustomTypeEntity> = emptyList()
    ) {
        clearAllRouteFollowRuns()
        clearAllFollowRuns()
        clearAllRoutes()
        clearAllSteps()
        clearAllItems()
        clearAllFolders()
        clearAllCustomTypes()
        if (customTypes.isNotEmpty()) insertCustomTypesBatch(customTypes)
        if (folders.isNotEmpty()) insertFoldersBatch(folders)
        if (items.isNotEmpty()) insertItemsBatch(items)
        if (routes.isNotEmpty()) insertRoutes(routes)
        if (steps.isNotEmpty()) insertSteps(steps)
        if (followRuns.isNotEmpty()) insertFollowRunsBatch(followRuns)
        if (routeFollowRuns.isNotEmpty()) {
            insertRouteFollowRunsBatch(routeFollowRuns)
        } else if (followRuns.isNotEmpty()) {
            insertRouteFollowRunsBatch(
                followRuns.map {
                    MethodRouteFollowRunEntity(
                        itemId = it.itemId,
                        routeId = MAIN_METHOD_ROUTE_ID,
                        completedStepIdsJson = it.completedStepIdsJson,
                        startedAt = it.startedAt,
                        updatedAt = it.updatedAt
                    )
                }
            )
        }
    }

    @Transaction
    suspend fun saveCustomTypeAtomically(
        customTypeToSave: CustomTypeEntity,
        updatedItems: List<VaultItemEntity>
    ) {
        for (item in updatedItems) {
            updateItem(item)
        }
        VaultTransactionFaultHooks.onCustomTypeMutationMidTransaction?.invoke()
        upsertCustomType(customTypeToSave)
    }

    @Transaction
    suspend fun deleteCustomTypeAtomically(
        customTypeId: String,
        updatedItems: List<VaultItemEntity>
    ) {
        for (item in updatedItems) {
            updateItem(item)
        }
        VaultTransactionFaultHooks.onCustomTypeMutationMidTransaction?.invoke()
        deleteCustomTypeById(customTypeId)
    }
}

data class VaultDatabaseSnapshot(
    val folders: List<FolderEntity>,
    val items: List<VaultItemEntity>,
    val routes: List<MethodRouteEntity>,
    val steps: List<MethodStepEntity>,
    val followRuns: List<MethodFollowRunEntity>,
    val routeFollowRuns: List<MethodRouteFollowRunEntity>,
    val customTypes: List<CustomTypeEntity> = emptyList()
)

object VaultTransactionFaultHooks {
    @Volatile
    var onSaveAfterStepsBeforeFollowPrune: (() -> Unit)? = null

    @Volatile
    var onDeleteRouteMidTransaction: (() -> Unit)? = null

    @Volatile
    var onCustomTypeMutationMidTransaction: (() -> Unit)? = null
}


