package com.example.data

import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class BackupRestoreResult {
    data class Success(
        val message: String,
        val itemCount: Int,
        val folderCount: Int,
        val generationId: String = ""
    ) : BackupRestoreResult()

    data class Failure(
        val reason: String,
        val rollbackVerified: Boolean = true,
        val diagnosticCode: String? = null
    ) : BackupRestoreResult()
}

enum class RestoreJournalPhase {
    PREPARING_SNAPSHOT,
    SNAPSHOT_VERIFIED,
    REPLACING_ATTACHMENTS,
    REPLACING_DATABASE,
    COMMITTED_PENDING_CLEANUP,
    FAILED_ROLLBACK
}

enum class RestoreFaultStage {
    DURING_EXTRACTION,
    AFTER_SNAPSHOT_VERIFIED,
    DURING_ATTACHMENT_REPLACEMENT,
    DURING_DATABASE_REPLACEMENT,
    DURING_CLEANUP,
    DURING_ROLLBACK_ATTACHMENTS,
    DURING_ROLLBACK_DATABASE
}

/**
 * Thrown in tests to simulate an abrupt OS process termination (SIGKILL) at a specific
 * restore phase without running in-process rollback, so next-launch recovery can be tested.
 */
class SimulatedProcessKillError(
    val stage: RestoreFaultStage,
    message: String = "Simulated process kill at $stage"
) : Error(message)

class UnresolvedVaultRecoveryException(
    message: String,
    val diagnosticCode: String = "ERR_UNRESOLVED_RECOVERY"
) : IllegalStateException(message)

sealed class RecoveryHealthState {
    data object Healthy : RecoveryHealthState()
    data object RestoreInProgress : RecoveryHealthState()
    data class UnresolvedRecoveryError(
        val diagnosticCode: String,
        val failedPhase: String,
        val safeMessage: String,
        val timestamp: Long
    ) : RecoveryHealthState()
}

data class AttachmentFileRecord(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String
)

data class RestoreJournalRecord(
    val phase: RestoreJournalPhase,
    val targetGenerationId: String,
    val previousGenerationId: String,
    val dbSnapshotSha256: String,
    val preRestoreItemCount: Int,
    val preRestoreFolderCount: Int,
    val preRestoreStepCount: Int,
    val preRestoreRunCount: Int,
    val preRestoreAttachments: List<AttachmentFileRecord>,
    val targetItemCount: Int,
    val targetFolderCount: Int,
    val targetAttachments: List<AttachmentFileRecord>,
    val stagingDirPath: String,
    val diagnosticCode: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class ValidatedBackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val userName: String,
    val themeMode: String,
    val folders: List<FolderEntity>,
    val items: List<VaultItemEntity>,
    val steps: List<MethodStepEntity>,
    val followRuns: List<MethodFollowRunEntity>,
    val stagingDir: File,
    val stagedAttachmentFiles: Map<String, File>,
    val stagedAttachmentChecksums: Map<String, String>,
    val totalAttachmentBytes: Long,
    val routes: List<MethodRouteEntity> = emptyList(),
    val routeFollowRuns: List<MethodRouteFollowRunEntity> = emptyList(),
    val customTypes: List<CustomTypeEntity> = emptyList()
) {
    val extractedAttachmentFiles: Map<String, File>
        get() = stagedAttachmentFiles
}

class BackupRestoreManager(
    private val dao: VaultDao,
    private val prefsRepo: UserPreferencesRepository,
    private val attachmentStorage: AttachmentStorageManager,
    var availableBytesProvider: () -> Long = {
        val usable = attachmentStorage.attachmentsDir.usableSpace
        if (usable > 0L) usable else Long.MAX_VALUE
    },
    private val database: VaultDatabase? = null,
    autoRecoverOnInit: Boolean = true
) {
    companion object {
        const val BACKUP_MAGIC = "IDEAVAULT_BACKUP_ARCHIVE"
        const val SUPPORTED_SCHEMA_VERSION = 3
        const val MANIFEST_ENTRY_NAME = "vault_backup.json"
        const val JOURNAL_FILE_NAME = "restore_journal.json"
        const val DB_SNAPSHOT_FILE_NAME = "rollback_db_snapshot.json"
        const val LIVE_ATTACHMENT_SNAPSHOT_DIR = "live_snapshot"

        // Security & ZIP bomb defense thresholds
        const val MAX_ZIP_ENTRIES = 2_000
        const val MAX_SINGLE_ENTRY_BYTES = 50L * 1024L * 1024L // 50 MB per entry
        const val MAX_TOTAL_UNCOMPRESSED_BYTES = 250L * 1024L * 1024L // 250 MB total archive
        const val MIN_REQUIRED_FREE_SPACE_BYTES = 2L * 1024L * 1024L // 2 MB minimum headroom
    }

    private val writeMutex = Mutex()
    private val _recoveryHealthFlow = MutableStateFlow<RecoveryHealthState>(RecoveryHealthState.Healthy)
    val recoveryHealthFlow: StateFlow<RecoveryHealthState> = _recoveryHealthFlow.asStateFlow()

    // Optional fault injection hooks for automated tests
    var extractionFaultInjector: (() -> Unit)? = null
    var stageFaultInjector: ((RestoreFaultStage) -> Unit)? = null
    var exportMidSnapshotHook: (suspend () -> Unit)? = null

    init {
        if (autoRecoverOnInit) {
            recoverInterruptedRestoreIfAny()
        }
    }

    /**
     * Serializes vault mutations with backup/restore operations so active Room connections
     * cannot interleave writes during a restore commit, and blocks normal writes if the
     * vault is in an unresolved recovery error state.
     */
    suspend fun <T> withWriteLock(block: suspend () -> T): T = writeMutex.withLock {
        ensureWriteAllowedUnlocked()
        block()
    }

    fun ensureWriteAllowedUnlocked() {
        when (val state = _recoveryHealthFlow.value) {
            is RecoveryHealthState.UnresolvedRecoveryError -> {
                throw UnresolvedVaultRecoveryException(
                    message = state.safeMessage,
                    diagnosticCode = state.diagnosticCode
                )
            }
            is RecoveryHealthState.RestoreInProgress -> {
                throw IllegalStateException("Vault write blocked while backup restoration is in progress.")
            }
            RecoveryHealthState.Healthy -> Unit
        }
    }

    /**
     * Synchronous startup entry point that coordinates recovery of BOTH the Room database
     * and attachment files before normal application access.
     */
    fun recoverInterruptedRestoreIfAny(): BackupRestoreResult = runBlocking(Dispatchers.IO) {
        recoverInterruptedRestore()
    }

    /**
     * Allows user or system to safely retry recovery when a previous rollback failed.
     */
    suspend fun retryPendingRecovery(): BackupRestoreResult = withContext(Dispatchers.IO) {
        recoverInterruptedRestore()
    }

    /**
     * Inspects the durable restore journal (`restore_journal.json`) and recovers both
     * the Room database and attachment files based on the recorded phase:
     * - `PREPARING_SNAPSHOT`: No live data was modified yet; cleans up partial snapshot/staging.
     * - `SNAPSHOT_VERIFIED`, `REPLACING_ATTACHMENTS`, `REPLACING_DATABASE`, `FAILED_ROLLBACK`:
     *   Executes verified rollback of both live attachments and Room database from the preserved snapshot.
     * - `COMMITTED_PENDING_CLEANUP`: Verifies that the committed restore is intact and completes cleanup
     *   without rolling back the committed restore.
     */
    suspend fun recoverInterruptedRestore(): BackupRestoreResult = writeMutex.withLock {
        val rollbackDir = attachmentStorage.rollbackRootDir
        val journalFile = File(rollbackDir, JOURNAL_FILE_NAME)

        if (!journalFile.exists()) {
            // Clean up any orphaned staging directories when no journal is active
            deleteDirectoryContents(attachmentStorage.stagingRootDir)
            _recoveryHealthFlow.value = RecoveryHealthState.Healthy
            return@withLock BackupRestoreResult.Success(
                message = "No interrupted restore pending.",
                itemCount = 0,
                folderCount = 0
            )
        }

        val journal = try {
            readJournal(journalFile)
        } catch (_: Exception) {
            // If journal file itself is unreadable, check if verified snapshots exist before deciding
            val dbSnapFile = File(rollbackDir, DB_SNAPSHOT_FILE_NAME)
            if (!dbSnapFile.exists()) {
                // Crashed before snapshot was written; live vault was never touched
                deleteDirectoryContents(rollbackDir)
                deleteDirectoryContents(attachmentStorage.stagingRootDir)
                _recoveryHealthFlow.value = RecoveryHealthState.Healthy
                return@withLock BackupRestoreResult.Success(
                    message = "Cleaned up incomplete pre-snapshot journal.",
                    itemCount = 0,
                    folderCount = 0
                )
            } else {
                val errState = RecoveryHealthState.UnresolvedRecoveryError(
                    diagnosticCode = "ERR_CORRUPT_JOURNAL",
                    failedPhase = "JOURNAL_READ",
                    safeMessage = "Vault recovery journal could not be parsed (code: ERR_CORRUPT_JOURNAL). Recovery files are preserved and writes are locked.",
                    timestamp = System.currentTimeMillis()
                )
                _recoveryHealthFlow.value = errState
                return@withLock BackupRestoreResult.Failure(
                    reason = errState.safeMessage,
                    rollbackVerified = false,
                    diagnosticCode = errState.diagnosticCode
                )
            }
        }

        return@withLock when (journal.phase) {
            RestoreJournalPhase.PREPARING_SNAPSHOT -> {
                // Interrupted before SNAPSHOT_VERIFIED: live attachments and live DB were never modified
                deleteDirectoryContents(rollbackDir)
                if (journal.stagingDirPath.isNotBlank()) {
                    cleanupStagingDir(File(journal.stagingDirPath))
                }
                deleteDirectoryContents(attachmentStorage.stagingRootDir)
                _recoveryHealthFlow.value = RecoveryHealthState.Healthy
                BackupRestoreResult.Success(
                    message = "Aborted pre-commit snapshot cleaned up; original vault untouched.",
                    itemCount = journal.preRestoreItemCount,
                    folderCount = journal.preRestoreFolderCount
                )
            }

            RestoreJournalPhase.COMMITTED_PENDING_CLEANUP -> {
                // Both attachments and database were committed before interruption during cleanup.
                // Verify committed state is intact, then finish cleanup.
                val verifyCommitted = verifyCommittedTargetState(journal)
                if (!verifyCommitted) {
                    // If committed state failed verification, fall back to rolling back from snapshot
                    performVerifiedRollback(journal, "Interrupted post-commit verification failed")
                } else {
                    completePostCommitCleanup(journal)
                    _recoveryHealthFlow.value = RecoveryHealthState.Healthy
                    BackupRestoreResult.Success(
                        message = "Completed pending cleanup for committed restore.",
                        itemCount = journal.targetItemCount,
                        folderCount = journal.targetFolderCount,
                        generationId = journal.targetGenerationId
                    )
                }
            }

            RestoreJournalPhase.SNAPSHOT_VERIFIED,
            RestoreJournalPhase.REPLACING_ATTACHMENTS,
            RestoreJournalPhase.REPLACING_DATABASE,
            RestoreJournalPhase.FAILED_ROLLBACK -> {
                val rollbackResult = performVerifiedRollback(journal, "Interrupted restore in phase ${journal.phase.name}")
                if (rollbackResult is BackupRestoreResult.Failure && rollbackResult.rollbackVerified) {
                    BackupRestoreResult.Success(
                        message = "Recovered original vault after interrupted restore (${journal.phase.name}).",
                        itemCount = journal.preRestoreItemCount,
                        folderCount = journal.preRestoreFolderCount,
                        generationId = journal.previousGenerationId
                    )
                } else {
                    rollbackResult
                }
            }
        }
    }

    /**
     * Exports a complete ZIP backup to [targetFile] by writing and verifying a temporary file first,
     * and only atomically replacing [targetFile] after the backup passes full integrity validation.
     */
    suspend fun exportBackupToFile(targetFile: File): BackupRestoreResult = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val parent = targetFile.parentFile ?: attachmentStorage.stagingRootDir
            if (!parent.exists() && !parent.mkdirs()) {
                return@withLock BackupRestoreResult.Failure(
                    reason = "Backup aborted: unable to create destination directory.",
                    rollbackVerified = true
                )
            }
            val tempDest = File(parent, "${targetFile.name}.tmp_${UUID.randomUUID()}")
            try {
                currentCoroutineContext().ensureActive()
                val result = FileOutputStream(tempDest).use { fos ->
                    exportBackupToStreamLocked(fos)
                }
                if (result is BackupRestoreResult.Success) {
                    currentCoroutineContext().ensureActive()
                    if (!tempDest.exists() || tempDest.length() <= 0L) {
                        tempDest.delete()
                        return@withLock BackupRestoreResult.Failure(
                            reason = "Backup aborted: temporary archive was not created.",
                            rollbackVerified = true
                        )
                    }
                    if (!tempDest.renameTo(targetFile)) {
                        copyFileWithSync(tempDest, targetFile)
                        tempDest.delete()
                    }
                } else {
                    tempDest.delete()
                }
                result
            } catch (cancel: CancellationException) {
                tempDest.delete()
                throw cancel
            } catch (e: Exception) {
                tempDest.delete()
                BackupRestoreResult.Failure(
                    reason = "Failed to export backup: ${e.localizedMessage ?: "Unknown error"}",
                    rollbackVerified = true
                )
            } finally {
                if (tempDest.exists()) {
                    tempDest.delete()
                }
            }
        }
    }

    /**
     * Exports a complete ZIP backup to the given OutputStream containing structured data
     * (items, folders, method routes, method steps, follow runs, route follow runs, tags, preferences),
     * SHA-256 attachment checksums, and local attachment files.
     *
     * Integrity & concurrency guarantees:
     * 1. Acquires [writeMutex] so concurrent database/attachment writes and restores cannot interleave.
     * 2. Fails closed before writing any bytes if recovery health is [RecoveryHealthState.RestoreInProgress]
     *    or [RecoveryHealthState.UnresolvedRecoveryError].
     * 3. Reads all 6 database tables inside a single Room `@Transaction` snapshot ([VaultDao.getConsistentVaultSnapshot]).
     * 4. Validates referential integrity across all 6 tables without silently discarding user data.
     * 5. Validates every expected item attachment and step image exists and is readable before export.
     * 6. Stages the ZIP archive in an isolated temporary file, flushes & fsyncs it, and verifies
     *    all 6 manifest collections, referential integrity, attachment counts, sizes, and SHA-256 checksums.
     * 7. Publishes bytes to [outputStream] only after the temporary archive passes verification.
     */
    suspend fun exportBackupToStream(outputStream: OutputStream): BackupRestoreResult = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            exportBackupToStreamLocked(outputStream)
        }
    }

    private suspend fun exportBackupToStreamLocked(outputStream: OutputStream): BackupRestoreResult {
        val stagingRoot = attachmentStorage.stagingRootDir.apply { mkdirs() }
        val tempZipFile = File(stagingRoot, "export_stage_${UUID.randomUUID()}.zip")
        try {
            currentCoroutineContext().ensureActive()

            when (val currentHealth = _recoveryHealthFlow.value) {
                is RecoveryHealthState.UnresolvedRecoveryError -> {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: vault has an unresolved recovery error (${currentHealth.diagnosticCode}).",
                        rollbackVerified = false,
                        diagnosticCode = currentHealth.diagnosticCode
                    )
                }
                is RecoveryHealthState.RestoreInProgress -> {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: a backup restore operation is currently in progress.",
                        rollbackVerified = false,
                        diagnosticCode = "ERR_EXPORT_RESTORE_IN_PROGRESS"
                    )
                }
                RecoveryHealthState.Healthy -> Unit
            }

            database?.checkpointWal()
            val snapshot = dao.getConsistentVaultSnapshot()
            val folders = snapshot.folders
            val items = snapshot.items
            val routes = snapshot.routes
            val steps = snapshot.steps
            val followRuns = snapshot.followRuns
            val routeFollowRuns = snapshot.routeFollowRuns
            val customTypes = snapshot.customTypes
            val prefs = prefsRepo.getSnapshot()

            exportMidSnapshotHook?.invoke()

            when (val postHookHealth = _recoveryHealthFlow.value) {
                is RecoveryHealthState.UnresolvedRecoveryError -> {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: vault entered an unresolved recovery error (${postHookHealth.diagnosticCode}).",
                        rollbackVerified = false,
                        diagnosticCode = postHookHealth.diagnosticCode
                    )
                }
                is RecoveryHealthState.RestoreInProgress -> {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: a backup restore operation started during export.",
                        rollbackVerified = false,
                        diagnosticCode = "ERR_EXPORT_RESTORE_IN_PROGRESS"
                    )
                }
                RecoveryHealthState.Healthy -> Unit
            }

            // Validate referential integrity across all 6 collections without silently discarding records
            val validFolderIds = folders.map { it.id }.toSet()
            val validItemIds = items.map { it.id }.toSet()
            for (item in items) {
                if (item.folderId != null && item.folderId !in validFolderIds) {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: item '${item.id}' references non-existent folder '${item.folderId}'.",
                        rollbackVerified = true,
                        diagnosticCode = "ERR_EXPORT_REFERENTIAL_INTEGRITY"
                    )
                }
            }
            val validItemRouteKeys = mutableSetOf<String>()
            for (route in routes) {
                if (route.itemId !in validItemIds) {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: route '${route.id}' references non-existent item '${route.itemId}'.",
                        rollbackVerified = true,
                        diagnosticCode = "ERR_EXPORT_REFERENTIAL_INTEGRITY"
                    )
                }
                validItemRouteKeys.add("${route.itemId}:${route.id}")
                if (route.isMain) {
                    validItemRouteKeys.add("${route.itemId}:$MAIN_METHOD_ROUTE_ID")
                }
            }
            for (step in steps) {
                if (step.itemId !in validItemIds) {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: step '${step.id}' references non-existent item '${step.itemId}'.",
                        rollbackVerified = true,
                        diagnosticCode = "ERR_EXPORT_REFERENTIAL_INTEGRITY"
                    )
                }
                val normalizedStepRouteId = step.routeId.ifBlank { MAIN_METHOD_ROUTE_ID }
                if (normalizedStepRouteId != MAIN_METHOD_ROUTE_ID &&
                    "${step.itemId}:$normalizedStepRouteId" !in validItemRouteKeys
                ) {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: step '${step.id}' references non-existent route '$normalizedStepRouteId' for item '${step.itemId}'.",
                        rollbackVerified = true,
                        diagnosticCode = "ERR_EXPORT_REFERENTIAL_INTEGRITY"
                    )
                }
            }
            for (run in followRuns) {
                if (run.itemId !in validItemIds) {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: followRun references non-existent item '${run.itemId}'.",
                        rollbackVerified = true,
                        diagnosticCode = "ERR_EXPORT_REFERENTIAL_INTEGRITY"
                    )
                }
            }
            for (routeRun in routeFollowRuns) {
                if (routeRun.itemId !in validItemIds) {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: routeFollowRun references non-existent item '${routeRun.itemId}'.",
                        rollbackVerified = true,
                        diagnosticCode = "ERR_EXPORT_REFERENTIAL_INTEGRITY"
                    )
                }
                val normalizedRunRouteId = routeRun.routeId.ifBlank { MAIN_METHOD_ROUTE_ID }
                if (normalizedRunRouteId != MAIN_METHOD_ROUTE_ID &&
                    "${routeRun.itemId}:$normalizedRunRouteId" !in validItemRouteKeys
                ) {
                    return BackupRestoreResult.Failure(
                        reason = "Backup aborted: routeFollowRun references non-existent route '$normalizedRunRouteId' for item '${routeRun.itemId}'.",
                        rollbackVerified = true,
                        diagnosticCode = "ERR_EXPORT_REFERENTIAL_INTEGRITY"
                    )
                }
            }

            val attachmentFilesToPackage = linkedMapOf<String, File>()
            val expectedAttachmentRecords = linkedMapOf<String, AttachmentFileRecord>()
            val missingAttachments = mutableListOf<String>()

            items.forEach { item ->
                currentCoroutineContext().ensureActive()
                val attachments = VaultJsonSerializers.decodeAttachments(item.attachmentsJson)
                attachments.forEach { att ->
                    if (att.type != AttachmentType.LINK) {
                        val rawPath = att.uriOrUrl.trim()
                        if (rawPath.isEmpty()) {
                            val label = att.name.ifBlank { att.id }
                            missingAttachments.add("${item.title} ($label: empty path)")
                        } else {
                            val localFile = File(rawPath)
                            if (!localFile.exists() || !localFile.isFile || !localFile.canRead()) {
                                val label = att.name.ifBlank { localFile.name }
                                missingAttachments.add("${item.title} ($label)")
                            } else if (!attachmentFilesToPackage.containsKey(localFile.name)) {
                                attachmentFilesToPackage[localFile.name] = localFile
                            } else {
                                val existingFile = attachmentFilesToPackage[localFile.name]
                                if (existingFile != null &&
                                    existingFile.canonicalPath != localFile.canonicalPath
                                ) {
                                    missingAttachments.add("${item.title} (duplicate attachment filename '${localFile.name}')")
                                }
                            }
                        }
                    }
                }
            }

            steps.forEach { step ->
                currentCoroutineContext().ensureActive()
                val stepImages = VaultJsonSerializers.decodeStringList(step.imagesJson)
                stepImages.forEach { rawPath ->
                    val trimmed = rawPath.trim()
                    if (trimmed.isEmpty()) {
                        missingAttachments.add("Step '${step.title}' (empty image path)")
                    } else {
                        val localFile = File(trimmed)
                        if (!localFile.exists() || !localFile.isFile || !localFile.canRead()) {
                            missingAttachments.add("Step '${step.title}' (${localFile.name})")
                        } else if (!attachmentFilesToPackage.containsKey(localFile.name)) {
                            attachmentFilesToPackage[localFile.name] = localFile
                        } else {
                            val existingFile = attachmentFilesToPackage[localFile.name]
                            if (existingFile != null &&
                                existingFile.canonicalPath != localFile.canonicalPath
                            ) {
                                missingAttachments.add("Step '${step.title}' (duplicate image filename '${localFile.name}')")
                            }
                        }
                    }
                }
            }

            // Fail safely BEFORE writing anything to outputStream if any expected attachment is missing!
            if (missingAttachments.isNotEmpty()) {
                return BackupRestoreResult.Failure(
                    reason = "Backup aborted: ${missingAttachments.size} required attachment file(s) are missing: ${missingAttachments.joinToString(", ")}",
                    rollbackVerified = true,
                    diagnosticCode = "ERR_EXPORT_MISSING_ATTACHMENT"
                )
            }

            var totalExpectedAttachmentBytes = 0L
            val attachmentMetaArr = JSONArray()
            for ((fileName, localFile) in attachmentFilesToPackage) {
                currentCoroutineContext().ensureActive()
                val sizeBytes = localFile.length()
                totalExpectedAttachmentBytes += sizeBytes
                val sha256 = attachmentStorage.computeSha256(localFile)
                expectedAttachmentRecords[fileName] = AttachmentFileRecord(
                    fileName = fileName,
                    sizeBytes = sizeBytes,
                    sha256 = sha256
                )
                attachmentMetaArr.put(
                    JSONObject().apply {
                        put("fileName", fileName)
                        put("sizeBytes", sizeBytes)
                        put("sha256", sha256)
                    }
                )
            }

            val availableSpace = availableBytesProvider()
            if (availableSpace < totalExpectedAttachmentBytes + MIN_REQUIRED_FREE_SPACE_BYTES) {
                return BackupRestoreResult.Failure(
                    reason = "Backup aborted: insufficient storage space available to create backup archive.",
                    rollbackVerified = true,
                    diagnosticCode = "ERR_EXPORT_LOW_STORAGE"
                )
            }

            val root = JSONObject()
            root.put("magic", BACKUP_MAGIC)
            root.put("schemaVersion", SUPPORTED_SCHEMA_VERSION)
            root.put("exportedAt", System.currentTimeMillis())
            root.put("attachmentsManifest", attachmentMetaArr)

            val prefsObj = JSONObject().apply {
                put("userName", prefs.userName)
                put("themeMode", prefs.themeMode.name)
            }
            root.put("preferences", prefsObj)
            root.put("folders", encodeFoldersJson(folders))
            root.put("customTypes", encodeCustomTypesJson(customTypes))
            root.put("items", encodeItemsJson(items))
            root.put("routes", encodeRoutesJson(routes))
            root.put("steps", encodeStepsJson(steps))
            root.put("followRuns", encodeFollowRunsJson(followRuns))
            root.put("routeFollowRuns", encodeRouteFollowRunsJson(routeFollowRuns))

            // Stage backup into isolated temporary ZIP file first
            FileOutputStream(tempZipFile).use { fos ->
                ZipOutputStream(fos).use { zipOut ->
                    val manifestBytes = root.toString(2).toByteArray(Charsets.UTF_8)
                    zipOut.putNextEntry(ZipEntry(MANIFEST_ENTRY_NAME))
                    zipOut.write(manifestBytes)
                    zipOut.closeEntry()

                    attachmentFilesToPackage.forEach { (fileName, localFile) ->
                        currentCoroutineContext().ensureActive()
                        if (!localFile.exists() || !localFile.isFile) {
                            throw IOException("Attachment '$fileName' disappeared during export.")
                        }
                        zipOut.putNextEntry(ZipEntry("attachments/$fileName"))
                        FileInputStream(localFile).use { fis ->
                            fis.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                    zipOut.finish()
                    zipOut.flush()
                    fos.flush()
                    runCatching { fos.fd.sync() }
                }
            }

            currentCoroutineContext().ensureActive()

            // Verify generated temporary ZIP structure, all 6 manifest collections, referential integrity, and SHA-256 checksums
            verifyExportedZipArchive(
                zipFile = tempZipFile,
                expectedFolderCount = folders.size,
                expectedItemCount = items.size,
                expectedRouteCount = routes.size,
                expectedStepCount = steps.size,
                expectedFollowRunCount = followRuns.size,
                expectedRouteFollowRunCount = routeFollowRuns.size,
                expectedAttachments = expectedAttachmentRecords
            )

            currentCoroutineContext().ensureActive()

            // Publish verified archive bytes to outputStream
            FileInputStream(tempZipFile).use { fis ->
                fis.copyTo(outputStream)
            }
            outputStream.flush()
            if (outputStream is FileOutputStream) {
                runCatching { outputStream.fd.sync() }
            }

            return BackupRestoreResult.Success(
                message = "Exported ${items.size} entries, ${folders.size} folders, and ${expectedAttachmentRecords.size} attachments to ZIP backup.",
                itemCount = items.size,
                folderCount = folders.size,
                generationId = attachmentStorage.readActiveGenerationId()
            )
        } catch (cancel: CancellationException) {
            tempZipFile.delete()
            throw cancel
        } catch (e: Exception) {
            tempZipFile.delete()
            return BackupRestoreResult.Failure(
                reason = "Failed to export backup: ${e.localizedMessage ?: "Unknown error"}",
                rollbackVerified = true
            )
        } finally {
            if (tempZipFile.exists()) {
                tempZipFile.delete()
            }
        }
    }

    internal fun verifyExportedZipArchive(
        zipFile: File,
        expectedFolderCount: Int,
        expectedItemCount: Int,
        expectedRouteCount: Int,
        expectedStepCount: Int,
        expectedFollowRunCount: Int,
        expectedRouteFollowRunCount: Int,
        expectedAttachments: Map<String, AttachmentFileRecord>
    ) {
        if (!zipFile.exists() || zipFile.length() <= 0L) {
            throw IOException("Generated backup archive is empty or missing.")
        }
        var manifestJson: String? = null
        val verifiedAttachments = mutableMapOf<String, AttachmentFileRecord>()
        val seenEntries = mutableSetOf<String>()

        FileInputStream(zipFile).use { fis ->
            ZipInputStream(fis).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name ?: throw IOException("Archive contains unnamed entry.")
                    if (!seenEntries.add(name)) {
                        throw IOException("Archive contains duplicate entry '$name'.")
                    }
                    if (!entry.isDirectory) {
                        if (name == MANIFEST_ENTRY_NAME) {
                            val out = ByteArrayOutputStream()
                            copyBounded(zipIn, out, MAX_SINGLE_ENTRY_BYTES)
                            manifestJson = out.toByteArray().toString(Charsets.UTF_8)
                        } else if (name.startsWith("attachments/")) {
                            val fileName = name.removePrefix("attachments/")
                            val digest = MessageDigest.getInstance("SHA-256")
                            var totalBytes = 0L
                            val buf = ByteArray(8192)
                            var read = zipIn.read(buf)
                            while (read != -1) {
                                totalBytes += read
                                digest.update(buf, 0, read)
                                read = zipIn.read(buf)
                            }
                            val sha = digest.digest().joinToString("") { "%02x".format(it) }
                            verifiedAttachments[fileName] = AttachmentFileRecord(fileName, totalBytes, sha)
                        } else {
                            throw IOException("Unexpected entry '$name' in exported archive.")
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }

        val rawManifest = manifestJson ?: throw IOException("Exported archive is missing $MANIFEST_ENTRY_NAME.")
        val root = JSONObject(rawManifest)
        if (root.optString("magic", "") != BACKUP_MAGIC) {
            throw IOException("Exported archive has invalid magic header.")
        }
        if (root.optInt("schemaVersion", -1) != SUPPORTED_SCHEMA_VERSION) {
            throw IOException("Exported archive has unexpected schema version.")
        }
        val foldersArr = root.optJSONArray("folders") ?: throw IOException("Exported manifest missing folders.")
        val itemsArr = root.optJSONArray("items") ?: throw IOException("Exported manifest missing items.")
        val routesArr = root.optJSONArray("routes") ?: throw IOException("Exported manifest missing routes.")
        val stepsArr = root.optJSONArray("steps") ?: throw IOException("Exported manifest missing steps.")
        val followRunsArr = root.optJSONArray("followRuns") ?: throw IOException("Exported manifest missing followRuns.")
        val routeFollowRunsArr = root.optJSONArray("routeFollowRuns")
            ?: throw IOException("Exported manifest missing routeFollowRuns.")
        val attManifestArr = root.optJSONArray("attachmentsManifest")
            ?: throw IOException("Exported manifest missing attachmentsManifest.")

        if (foldersArr.length() != expectedFolderCount ||
            itemsArr.length() != expectedItemCount ||
            routesArr.length() != expectedRouteCount ||
            stepsArr.length() != expectedStepCount ||
            followRunsArr.length() != expectedFollowRunCount ||
            routeFollowRunsArr.length() != expectedRouteFollowRunCount
        ) {
            throw IOException(
                "Exported manifest record counts do not match source vault " +
                    "(folders=${foldersArr.length()}/$expectedFolderCount, " +
                    "items=${itemsArr.length()}/$expectedItemCount, " +
                    "routes=${routesArr.length()}/$expectedRouteCount, " +
                    "steps=${stepsArr.length()}/$expectedStepCount, " +
                    "followRuns=${followRunsArr.length()}/$expectedFollowRunCount, " +
                    "routeFollowRuns=${routeFollowRunsArr.length()}/$expectedRouteFollowRunCount)."
            )
        }

        // Validate referential integrity of all 6 archived collections
        val archivedFolderIds = mutableSetOf<String>()
        for (i in 0 until foldersArr.length()) {
            val folderObj = foldersArr.getJSONObject(i)
            val id = folderObj.optString("id", "").trim()
            if (id.isEmpty() || !archivedFolderIds.add(id)) {
                throw IOException("Exported manifest contains invalid or duplicate folder id '$id'.")
            }
        }

        val archivedItemIds = mutableSetOf<String>()
        for (i in 0 until itemsArr.length()) {
            val itemObj = itemsArr.getJSONObject(i)
            val id = itemObj.optString("id", "").trim()
            if (id.isEmpty() || !archivedItemIds.add(id)) {
                throw IOException("Exported manifest contains invalid or duplicate item id '$id'.")
            }
            val folderId = if (itemObj.isNull("folderId")) null else itemObj.optString("folderId", "").trim().ifEmpty { null }
            if (folderId != null && folderId !in archivedFolderIds) {
                throw IOException("Exported manifest item '$id' references non-existent folder '$folderId'.")
            }
        }

        val archivedItemRouteKeys = mutableSetOf<String>()
        for (i in 0 until routesArr.length()) {
            val routeObj = routesArr.getJSONObject(i)
            val routeId = routeObj.optString("id", "").trim()
            val itemId = routeObj.optString("itemId", "").trim()
            if (routeId.isEmpty() || itemId.isEmpty() || itemId !in archivedItemIds) {
                throw IOException("Exported manifest route '$routeId' references invalid item '$itemId'.")
            }
            if (!archivedItemRouteKeys.add("$itemId:$routeId")) {
                throw IOException("Exported manifest contains duplicate route '$routeId' for item '$itemId'.")
            }
            if (routeObj.optBoolean("isMain", false)) {
                archivedItemRouteKeys.add("$itemId:$MAIN_METHOD_ROUTE_ID")
            }
        }

        val archivedStepIds = mutableSetOf<String>()
        for (i in 0 until stepsArr.length()) {
            val stepObj = stepsArr.getJSONObject(i)
            val stepId = stepObj.optString("id", "").trim()
            val itemId = stepObj.optString("itemId", "").trim()
            val routeId = stepObj.optString("routeId", MAIN_METHOD_ROUTE_ID).trim().ifEmpty { MAIN_METHOD_ROUTE_ID }
            if (stepId.isEmpty() || !archivedStepIds.add(stepId)) {
                throw IOException("Exported manifest contains invalid or duplicate step id '$stepId'.")
            }
            if (itemId !in archivedItemIds) {
                throw IOException("Exported manifest step '$stepId' references non-existent item '$itemId'.")
            }
            if (routeId != MAIN_METHOD_ROUTE_ID && "$itemId:$routeId" !in archivedItemRouteKeys) {
                throw IOException("Exported manifest step '$stepId' references non-existent route '$routeId' for item '$itemId'.")
            }
        }

        val archivedFollowRunItems = mutableSetOf<String>()
        for (i in 0 until followRunsArr.length()) {
            val runObj = followRunsArr.getJSONObject(i)
            val itemId = runObj.optString("itemId", "").trim()
            if (itemId.isEmpty() || itemId !in archivedItemIds) {
                throw IOException("Exported manifest followRun references non-existent item '$itemId'.")
            }
            if (!archivedFollowRunItems.add(itemId)) {
                throw IOException("Exported manifest contains duplicate followRun for item '$itemId'.")
            }
        }

        val archivedRouteFollowRunKeys = mutableSetOf<String>()
        for (i in 0 until routeFollowRunsArr.length()) {
            val routeRunObj = routeFollowRunsArr.getJSONObject(i)
            val itemId = routeRunObj.optString("itemId", "").trim()
            val routeId = routeRunObj.optString("routeId", MAIN_METHOD_ROUTE_ID).trim().ifEmpty { MAIN_METHOD_ROUTE_ID }
            if (itemId.isEmpty() || itemId !in archivedItemIds) {
                throw IOException("Exported manifest routeFollowRun references non-existent item '$itemId'.")
            }
            if (routeId != MAIN_METHOD_ROUTE_ID && "$itemId:$routeId" !in archivedItemRouteKeys) {
                throw IOException("Exported manifest routeFollowRun references non-existent route '$routeId' for item '$itemId'.")
            }
            if (!archivedRouteFollowRunKeys.add("$itemId:$routeId")) {
                throw IOException("Exported manifest contains duplicate routeFollowRun for '$itemId:$routeId'.")
            }
        }

        if (attManifestArr.length() != expectedAttachments.size ||
            verifiedAttachments.size != expectedAttachments.size
        ) {
            throw IOException(
                "Exported attachment count mismatch (manifest=${attManifestArr.length()}, zip=${verifiedAttachments.size}, expected=${expectedAttachments.size})."
            )
        }
        for ((fileName, expected) in expectedAttachments) {
            val actual = verifiedAttachments[fileName]
                ?: throw IOException("Exported archive is missing attachment '$fileName'.")
            if (actual.sizeBytes != expected.sizeBytes) {
                throw IOException("Exported attachment '$fileName' size mismatch (${actual.sizeBytes} vs ${expected.sizeBytes}).")
            }
            if (!actual.sha256.equals(expected.sha256, ignoreCase = true)) {
                throw IOException("Exported attachment '$fileName' SHA-256 checksum mismatch.")
            }
        }
    }

    /**
     * Validates a ZIP backup input stream and extracts attachments into an isolated temporary staging
     * directory without touching live database records or live attachment files.
     */
    suspend fun validateBackupStream(inputStream: InputStream): Result<ValidatedBackupPayload> =
        withContext(Dispatchers.IO) {
            val stagingDir = File(attachmentStorage.stagingRootDir, "stage_${UUID.randomUUID()}")
            val stagingAttachmentsDir = File(stagingDir, "attachments")
            try {
                if (!stagingAttachmentsDir.mkdirs() && !stagingAttachmentsDir.exists()) {
                    return@withContext Result.failure(IOException("Unable to create isolated staging directory."))
                }

                val availableSpace = availableBytesProvider()
                if (availableSpace < MIN_REQUIRED_FREE_SPACE_BYTES) {
                    cleanupStagingDir(stagingDir)
                    return@withContext Result.failure(IOException("Insufficient storage space available to stage backup."))
                }

                var manifestJson: String? = null
                val stagedFiles = mutableMapOf<String, File>()
                val computedChecksums = mutableMapOf<String, String>()
                val seenEntryNames = mutableSetOf<String>()
                var entryCount = 0
                var totalUncompressedBytes = 0L
                var totalAttachmentBytes = 0L

                val canonicalStagingAttRoot = stagingAttachmentsDir.canonicalPath + File.separator

                ZipInputStream(inputStream).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        entryCount++
                        if (entryCount > MAX_ZIP_ENTRIES) {
                            cleanupStagingDir(stagingDir)
                            return@withContext Result.failure(
                                IllegalArgumentException("Backup rejected: archive exceeds maximum allowed entry count ($MAX_ZIP_ENTRIES).")
                            )
                        }

                        val rawName = entry.name ?: ""
                        if (rawName.isBlank() ||
                            rawName.contains("..") ||
                            rawName.contains("\\") ||
                            rawName.startsWith("/")
                        ) {
                            cleanupStagingDir(stagingDir)
                            return@withContext Result.failure(
                                IllegalArgumentException("Backup rejected: unsafe or path-traversal entry path '$rawName'.")
                            )
                        }

                        if (!seenEntryNames.add(rawName)) {
                            cleanupStagingDir(stagingDir)
                            return@withContext Result.failure(
                                IllegalArgumentException("Backup rejected: duplicate archive entry '$rawName'.")
                            )
                        }

                        if (!entry.isDirectory) {
                            if (rawName == MANIFEST_ENTRY_NAME) {
                                val out = ByteArrayOutputStream()
                                val bytesRead = copyBounded(zipIn, out, MAX_SINGLE_ENTRY_BYTES)
                                totalUncompressedBytes += bytesRead
                                if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    cleanupStagingDir(stagingDir)
                                    return@withContext Result.failure(
                                        IllegalArgumentException("Backup rejected: total uncompressed size exceeds limit.")
                                    )
                                }
                                manifestJson = out.toByteArray().toString(Charsets.UTF_8)
                            } else if (rawName.startsWith("attachments/")) {
                                val fileName = rawName.removePrefix("attachments/")
                                if (fileName.isBlank() || fileName.contains("/") || fileName.contains("..")) {
                                    cleanupStagingDir(stagingDir)
                                    return@withContext Result.failure(
                                        IllegalArgumentException("Backup rejected: invalid attachment filename '$fileName'.")
                                    )
                                }

                                val destFile = File(stagingAttachmentsDir, fileName)
                                val canonicalDest = destFile.canonicalPath
                                if (!canonicalDest.startsWith(canonicalStagingAttRoot)) {
                                    cleanupStagingDir(stagingDir)
                                    return@withContext Result.failure(
                                        IllegalArgumentException("Backup rejected: attachment escaped staging directory.")
                                    )
                                }

                                extractionFaultInjector?.invoke()
                                stageFaultInjector?.invoke(RestoreFaultStage.DURING_EXTRACTION)

                                val digest = MessageDigest.getInstance("SHA-256")
                                var fileBytes = 0L
                                FileOutputStream(destFile).use { fos ->
                                    val buf = ByteArray(8192)
                                    var read = zipIn.read(buf)
                                    while (read != -1) {
                                        fileBytes += read
                                        totalUncompressedBytes += read
                                        totalAttachmentBytes += read

                                        if (fileBytes > MAX_SINGLE_ENTRY_BYTES) {
                                            throw IllegalArgumentException("Backup rejected: entry '$rawName' exceeds maximum file size.")
                                        }
                                        if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                            throw IllegalArgumentException("Backup rejected: archive exceeds maximum total uncompressed size.")
                                        }
                                        if (totalUncompressedBytes + MIN_REQUIRED_FREE_SPACE_BYTES > availableSpace) {
                                            throw IOException("Insufficient storage space to extract backup attachments.")
                                        }

                                        digest.update(buf, 0, read)
                                        fos.write(buf, 0, read)
                                        read = zipIn.read(buf)
                                    }
                                    fos.flush()
                                    fos.fd.sync()
                                }

                                stagedFiles[fileName] = destFile
                                computedChecksums[fileName] = digest.digest().joinToString("") { "%02x".format(it) }
                            } else {
                                cleanupStagingDir(stagingDir)
                                return@withContext Result.failure(
                                    IllegalArgumentException("Backup rejected: unexpected archive entry '$rawName'.")
                                )
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }

                val rawManifest = manifestJson ?: run {
                    cleanupStagingDir(stagingDir)
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid backup archive: missing vault_backup.json manifest.")
                    )
                }

                val root = JSONObject(rawManifest)
                val magic = root.optString("magic", "")
                if (magic != BACKUP_MAGIC) {
                    cleanupStagingDir(stagingDir)
                    return@withContext Result.failure(
                        IllegalArgumentException("Unsupported backup format: invalid magic header.")
                    )
                }

                val version = root.optInt("schemaVersion", -1)
                if (version <= 0 || version > SUPPORTED_SCHEMA_VERSION) {
                    cleanupStagingDir(stagingDir)
                    return@withContext Result.failure(
                        IllegalArgumentException("Unsupported backup schema version: $version.")
                    )
                }

                if (!root.has("items") || !root.has("folders")) {
                    cleanupStagingDir(stagingDir)
                    return@withContext Result.failure(
                        IllegalArgumentException("Malformed backup: required collections are missing.")
                    )
                }

                // Verify attachment manifest checksums if present
                val attManifestArr = root.optJSONArray("attachmentsManifest")
                if (attManifestArr != null) {
                    for (i in 0 until attManifestArr.length()) {
                        val metaObj = attManifestArr.getJSONObject(i)
                        val expectedName = metaObj.optString("fileName", "")
                        val expectedSha256 = metaObj.optString("sha256", "")
                        val expectedSize = metaObj.optLong("sizeBytes", -1L)
                        val staged = stagedFiles[expectedName]
                            ?: run {
                                cleanupStagingDir(stagingDir)
                                return@withContext Result.failure(
                                    IllegalArgumentException("Backup validation failed: missing attachment '$expectedName' listed in manifest.")
                                )
                            }
                        if (expectedSize >= 0L && staged.length() != expectedSize) {
                            cleanupStagingDir(stagingDir)
                            return@withContext Result.failure(
                                IllegalArgumentException("Backup validation failed: size mismatch for attachment '$expectedName'.")
                            )
                        }
                        if (expectedSha256.isNotBlank() &&
                            !computedChecksums[expectedName].equals(expectedSha256, ignoreCase = true)
                        ) {
                            cleanupStagingDir(stagingDir)
                            return@withContext Result.failure(
                                IllegalArgumentException("Backup validation failed: SHA-256 checksum mismatch for '$expectedName'.")
                            )
                        }
                    }
                }

                val prefsObj = root.optJSONObject("preferences")
                val userName = prefsObj?.optString("userName", "") ?: ""
                val themeMode = prefsObj?.optString("themeMode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name

                // Validate folders and reject duplicate folder IDs
                val foldersArr = root.getJSONArray("folders")
                val seenFolderIds = mutableSetOf<String>()
                val folders = parseFoldersJson(foldersArr, seenFolderIds)

                // Validate items, reject duplicate item IDs, validate folder references and attachment references
                val itemsArr = root.getJSONArray("items")
                val seenItemIds = mutableSetOf<String>()
                val items = parseItemsJson(itemsArr, seenItemIds, seenFolderIds, stagedFiles)

                // Validate routes
                val routesArr = root.optJSONArray("routes") ?: JSONArray()
                val seenRouteIds = mutableSetOf<String>()
                val routes = parseRoutesJson(routesArr, seenRouteIds, seenItemIds)

                // Validate steps, reject duplicate step IDs and broken itemId references
                val stepsArr = root.optJSONArray("steps") ?: JSONArray()
                val seenStepIds = mutableSetOf<String>()
                val steps = parseStepsJson(stepsArr, seenStepIds, seenItemIds, stagedFiles)

                // Validate follow runs
                val runsArr = root.optJSONArray("followRuns") ?: JSONArray()
                val seenRunItemIds = mutableSetOf<String>()
                val parsedFollowRuns = parseFollowRunsJson(runsArr, seenRunItemIds, seenItemIds)

                val routeRunsArr = root.optJSONArray("routeFollowRuns") ?: JSONArray()
                val parsedRouteFollowRuns = parseRouteFollowRunsJson(routeRunsArr, seenItemIds)

                val customTypesArr = root.optJSONArray("customTypes") ?: JSONArray()
                val parsedCustomTypes = parseCustomTypesJson(customTypesArr)

                // Ensure v1/v2 backup followRuns and v3 routeFollowRuns for MAIN_METHOD_ROUTE_ID are synchronized
                val mergedRouteFollowRuns = parsedRouteFollowRuns.toMutableList()
                val existingMainRouteRunItemIds = mergedRouteFollowRuns
                    .filter { it.routeId == MAIN_METHOD_ROUTE_ID }
                    .map { it.itemId }
                    .toMutableSet()
                for (legacyRun in parsedFollowRuns) {
                    if (existingMainRouteRunItemIds.add(legacyRun.itemId)) {
                        mergedRouteFollowRuns.add(
                            MethodRouteFollowRunEntity(
                                itemId = legacyRun.itemId,
                                routeId = MAIN_METHOD_ROUTE_ID,
                                completedStepIdsJson = legacyRun.completedStepIdsJson,
                                startedAt = legacyRun.startedAt,
                                updatedAt = legacyRun.updatedAt
                            )
                        )
                    }
                }
                val mergedFollowRuns = parsedFollowRuns.toMutableList()
                val existingFollowRunItemIds = mergedFollowRuns.map { it.itemId }.toMutableSet()
                for (routeRun in mergedRouteFollowRuns) {
                    if (routeRun.routeId == MAIN_METHOD_ROUTE_ID && existingFollowRunItemIds.add(routeRun.itemId)) {
                        mergedFollowRuns.add(
                            MethodFollowRunEntity(
                                itemId = routeRun.itemId,
                                completedStepIdsJson = routeRun.completedStepIdsJson,
                                startedAt = routeRun.startedAt,
                                updatedAt = routeRun.updatedAt
                            )
                        )
                    }
                }

                Result.success(
                    ValidatedBackupPayload(
                        schemaVersion = version,
                        exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
                        userName = userName,
                        themeMode = themeMode,
                        folders = folders,
                        items = items,
                        steps = steps,
                        followRuns = mergedFollowRuns,
                        stagingDir = stagingDir,
                        stagedAttachmentFiles = stagedFiles,
                        stagedAttachmentChecksums = computedChecksums,
                        totalAttachmentBytes = totalAttachmentBytes,
                        routes = routes,
                        routeFollowRuns = mergedRouteFollowRuns,
                        customTypes = parsedCustomTypes
                    )
                )
            } catch (t: SimulatedProcessKillError) {
                throw t
            } catch (e: Exception) {
                cleanupStagingDir(stagingDir)
                Result.failure(
                    IllegalArgumentException("Backup validation failed: ${e.localizedMessage ?: "Malformed ZIP or JSON"}")
                )
            }
        }

    /**
     * Applies a validated backup payload using a coordinated, generation-based, journaled commit strategy:
     * 1. Acquires the exclusive write lock (`writeMutex`) and verifies vault health state.
     * 2. Checkpoints SQLite WAL/SHM and writes a verified, checksummed recovery snapshot of the
     *    current Room database (`rollback_db_snapshot.json`) and live attachments (`live_snapshot/`)
     *    alongside a durable journal (`restore_journal.json`).
     * 3. Prepares the new attachment generation (`vault_attachment_generations/gen_<id>`) and verifies
     *    every file's SHA-256 before transitioning the journal to `SNAPSHOT_VERIFIED`.
     * 4. Replaces live attachments (`REPLACING_ATTACHMENTS`) and verifies their SHA-256 digests.
     * 5. Replaces Room database records (`REPLACING_DATABASE`) with paths pointing to the active generation,
     *    checkpoints SQLite WAL, and verifies database record counts.
     * 6. Transitions journal to `COMMITTED_PENDING_CLEANUP`, cleans up temporary files, and marks health `Healthy`.
     * 7. If any failure occurs during commit, executes a verified rollback of BOTH attachments and Room database,
     *    detecting any rollback failure and preserving recovery snapshots if rollback cannot be verified.
     */
    suspend fun applyValidatedBackup(
        payload: ValidatedBackupPayload,
        faultInjector: (() -> Unit)? = null
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val currentHealth = _recoveryHealthFlow.value
            if (currentHealth is RecoveryHealthState.UnresolvedRecoveryError) {
                return@withLock BackupRestoreResult.Failure(
                    reason = "Restore blocked: vault has an unresolved recovery error (${currentHealth.diagnosticCode}). Retry recovery first.",
                    rollbackVerified = false,
                    diagnosticCode = currentHealth.diagnosticCode
                )
            }

            if (!payload.stagingDir.exists()) {
                return@withLock BackupRestoreResult.Failure("Restore aborted: staging directory no longer exists.")
            }
            for ((name, file) in payload.stagedAttachmentFiles) {
                if (!file.exists() || !file.isFile) {
                    cleanupStagingDir(payload.stagingDir)
                    return@withLock BackupRestoreResult.Failure("Restore aborted: staged attachment '$name' is missing.")
                }
                val expectedSha = payload.stagedAttachmentChecksums[name]
                if (!expectedSha.isNullOrBlank()) {
                    val actualSha = attachmentStorage.computeSha256(file)
                    if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                        cleanupStagingDir(payload.stagingDir)
                        return@withLock BackupRestoreResult.Failure("Restore aborted: staged attachment '$name' failed checksum verification.")
                    }
                }
            }

            val availableSpace = availableBytesProvider()
            if (availableSpace < (payload.totalAttachmentBytes * 2L) + MIN_REQUIRED_FREE_SPACE_BYTES) {
                cleanupStagingDir(payload.stagingDir)
                return@withLock BackupRestoreResult.Failure("Restore aborted: insufficient storage space to commit restore safely.")
            }

            _recoveryHealthFlow.value = RecoveryHealthState.RestoreInProgress

            val rollbackDir = attachmentStorage.rollbackRootDir
            val liveSnapshotDir = File(rollbackDir, LIVE_ATTACHMENT_SNAPSHOT_DIR)
            val dbSnapshotFile = File(rollbackDir, DB_SNAPSHOT_FILE_NAME)
            val journalFile = File(rollbackDir, JOURNAL_FILE_NAME)

            val previousGenerationId = attachmentStorage.readActiveGenerationId()
            val targetGenerationId = "gen_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            val targetGenerationDir = attachmentStorage.getGenerationDir(targetGenerationId)

            // Step 1: Checkpoint SQLite WAL and read current live DB + attachment state in a single transaction
            database?.checkpointWal()
            val preRestoreSnapshot = dao.getConsistentVaultSnapshot()
            val previousFolders = preRestoreSnapshot.folders
            val previousItems = preRestoreSnapshot.items
            val previousRoutes = preRestoreSnapshot.routes
            val previousSteps = preRestoreSnapshot.steps
            val previousRuns = preRestoreSnapshot.followRuns
            val previousRouteRuns = preRestoreSnapshot.routeFollowRuns
            val previousCustomTypes = preRestoreSnapshot.customTypes
            val previousPrefs = prefsRepo.getSnapshot()

            val preRestoreAttachmentRecords = mutableListOf<AttachmentFileRecord>()
            val targetAttachmentRecords = payload.stagedAttachmentFiles.map { (name, file) ->
                val sha = payload.stagedAttachmentChecksums[name] ?: attachmentStorage.computeSha256(file)
                AttachmentFileRecord(name, file.length(), sha)
            }

            var journalRecord = RestoreJournalRecord(
                phase = RestoreJournalPhase.PREPARING_SNAPSHOT,
                targetGenerationId = targetGenerationId,
                previousGenerationId = previousGenerationId,
                dbSnapshotSha256 = "",
                preRestoreItemCount = previousItems.size,
                preRestoreFolderCount = previousFolders.size,
                preRestoreStepCount = previousSteps.size,
                preRestoreRunCount = previousRuns.size,
                preRestoreAttachments = emptyList(),
                targetItemCount = payload.items.size,
                targetFolderCount = payload.folders.size,
                targetAttachments = targetAttachmentRecords,
                stagingDirPath = payload.stagingDir.absolutePath
            )

            try {
                // Write initial PREPARING_SNAPSHOT journal before creating snapshot files
                deleteDirectoryContents(rollbackDir)
                liveSnapshotDir.mkdirs()
                writeJournal(journalFile, journalRecord)

                // Copy live attachments into rollbackDir/live_snapshot and verify each file's SHA-256
                val liveDir = attachmentStorage.attachmentsDir
                liveDir.listFiles()?.filter { it.isFile }?.forEach { liveFile ->
                    val snapDest = File(liveSnapshotDir, liveFile.name)
                    copyFileWithSync(liveFile, snapDest)
                    val origSha = attachmentStorage.computeSha256(liveFile)
                    val snapSha = attachmentStorage.computeSha256(snapDest)
                    if (snapDest.length() != liveFile.length() || !origSha.equals(snapSha, ignoreCase = true)) {
                        throw IOException("Failed to verify pre-restore attachment snapshot for '${liveFile.name}'.")
                    }
                    preRestoreAttachmentRecords.add(
                        AttachmentFileRecord(liveFile.name, liveFile.length(), origSha)
                    )
                }

                // Serialize and verify pre-restore database snapshot
                val dbSnapshotJson = buildDbSnapshotJson(
                    folders = previousFolders,
                    items = previousItems,
                    routes = previousRoutes,
                    steps = previousSteps,
                    followRuns = previousRuns,
                    routeFollowRuns = previousRouteRuns,
                    userName = previousPrefs.userName,
                    themeMode = previousPrefs.themeMode.name,
                    customTypes = previousCustomTypes
                )
                val dbSnapshotBytes = dbSnapshotJson.toByteArray(Charsets.UTF_8)
                val dbSnapshotSha = attachmentStorage.computeSha256Bytes(dbSnapshotBytes)
                attachmentStorage.atomicWriteText(dbSnapshotFile, dbSnapshotJson)

                val verifiedDiskSha = attachmentStorage.computeSha256(dbSnapshotFile)
                if (!verifiedDiskSha.equals(dbSnapshotSha, ignoreCase = true)) {
                    throw IOException("Pre-restore database snapshot checksum verification failed.")
                }

                // Stage incoming attachments into an immutable generation directory and verify checksums
                deleteDirectoryContents(targetGenerationDir)
                targetGenerationDir.mkdirs()
                for (attRecord in targetAttachmentRecords) {
                    val stagedSource = payload.stagedAttachmentFiles[attRecord.fileName]
                        ?: throw IOException("Missing staged file '${attRecord.fileName}'.")
                    val genDest = File(targetGenerationDir, attRecord.fileName)
                    copyFileWithSync(stagedSource, genDest)
                    val genSha = attachmentStorage.computeSha256(genDest)
                    if (genDest.length() != attRecord.sizeBytes || !genSha.equals(attRecord.sha256, ignoreCase = true)) {
                        throw IOException("Target generation verification failed for '${attRecord.fileName}'.")
                    }
                }

                // Transition journal to SNAPSHOT_VERIFIED
                journalRecord = journalRecord.copy(
                    phase = RestoreJournalPhase.SNAPSHOT_VERIFIED,
                    dbSnapshotSha256 = dbSnapshotSha,
                    preRestoreAttachments = preRestoreAttachmentRecords,
                    updatedAt = System.currentTimeMillis()
                )
                writeJournal(journalFile, journalRecord)
                stageFaultInjector?.invoke(RestoreFaultStage.AFTER_SNAPSHOT_VERIFIED)
            } catch (kill: SimulatedProcessKillError) {
                throw kill
            } catch (prepError: Exception) {
                // Failed before any live attachments or live DB rows were touched!
                deleteDirectoryContents(rollbackDir)
                deleteDirectoryContents(targetGenerationDir)
                targetGenerationDir.delete()
                cleanupStagingDir(payload.stagingDir)
                _recoveryHealthFlow.value = RecoveryHealthState.Healthy
                return@withLock BackupRestoreResult.Failure(
                    reason = "Restore aborted before modifying vault: ${prepError.localizedMessage ?: "Snapshot preparation failed"}",
                    rollbackVerified = true
                )
            }

            // Remap item and step attachment paths to final live attachmentsDir paths
            val liveDir = attachmentStorage.attachmentsDir
            val remappedItems = payload.items.map { item ->
                val decoded = VaultJsonSerializers.decodeAttachments(item.attachmentsJson)
                if (decoded.isEmpty()) {
                    item
                } else {
                    val updatedAtts = decoded.map { att ->
                        if (att.type != AttachmentType.LINK) {
                            val baseName = File(att.uriOrUrl).name
                            att.copy(uriOrUrl = File(liveDir, baseName).absolutePath)
                        } else {
                            att
                        }
                    }
                    item.copy(attachmentsJson = VaultJsonSerializers.encodeAttachments(updatedAtts))
                }
            }

            val remappedSteps = payload.steps.map { step ->
                val imgs = VaultJsonSerializers.decodeStringList(step.imagesJson)
                if (imgs.isEmpty()) {
                    step
                } else {
                    val updatedImgs = imgs.map { File(liveDir, File(it).name).absolutePath }
                    step.copy(imagesJson = VaultJsonSerializers.encodeStringList(updatedImgs))
                }
            }

            try {
                // Phase 2: Replace live attachments from verified generation directory
                journalRecord = journalRecord.copy(
                    phase = RestoreJournalPhase.REPLACING_ATTACHMENTS,
                    updatedAt = System.currentTimeMillis()
                )
                writeJournal(journalFile, journalRecord)

                deleteDirectoryContents(liveDir)
                for (attRecord in targetAttachmentRecords) {
                    val genSource = File(targetGenerationDir, attRecord.fileName)
                    val liveTarget = File(liveDir, attRecord.fileName)
                    copyFileWithSync(genSource, liveTarget)
                }

                // Fault hooks during attachment replacement / before DB replacement
                stageFaultInjector?.invoke(RestoreFaultStage.DURING_ATTACHMENT_REPLACEMENT)
                faultInjector?.invoke()

                // Verify live attachments match target generation manifest before touching DB
                for (attRecord in targetAttachmentRecords) {
                    val liveTarget = File(liveDir, attRecord.fileName)
                    if (!liveTarget.exists() || liveTarget.length() != attRecord.sizeBytes) {
                        throw IOException("Live attachment verification failed for '${attRecord.fileName}'.")
                    }
                    val liveSha = attachmentStorage.computeSha256(liveTarget)
                    if (!liveSha.equals(attRecord.sha256, ignoreCase = true)) {
                        throw IOException("Live attachment checksum mismatch for '${attRecord.fileName}'.")
                    }
                }

                // Update active generation pointer
                attachmentStorage.writeActiveGeneration(
                    generationId = targetGenerationId,
                    fileChecksums = targetAttachmentRecords.associate { it.fileName to it.sha256 }
                )

                // Phase 3: Replace Room database records and preferences
                journalRecord = journalRecord.copy(
                    phase = RestoreJournalPhase.REPLACING_DATABASE,
                    updatedAt = System.currentTimeMillis()
                )
                writeJournal(journalFile, journalRecord)

                stageFaultInjector?.invoke(RestoreFaultStage.DURING_DATABASE_REPLACEMENT)

                dao.replaceVaultWithValidatedBackup(
                    folders = payload.folders,
                    items = remappedItems,
                    steps = remappedSteps,
                    followRuns = payload.followRuns,
                    routes = payload.routes,
                    routeFollowRuns = payload.routeFollowRuns,
                    customTypes = payload.customTypes
                )
                database?.checkpointWal()

                if (payload.userName.isNotBlank()) {
                    prefsRepo.updateUserName(payload.userName)
                }
                try {
                    prefsRepo.updateThemeMode(AppThemeMode.valueOf(payload.themeMode))
                } catch (_: IllegalArgumentException) {
                    // Preserve existing theme mode if unknown enum name
                }

                // Verify committed database state and attachment references across all 6 tables
                val committedItems = dao.getAllItemsSnapshot()
                val committedFolders = dao.getAllFoldersSnapshot()
                val committedRoutes = dao.getAllRoutesSnapshot()
                val committedSteps = dao.getAllStepsSnapshot()
                val committedRuns = dao.getAllFollowRunsSnapshot()
                val committedRouteRuns = dao.getAllRouteFollowRunsSnapshot()
                if (committedItems.size != remappedItems.size ||
                    committedFolders.size != payload.folders.size ||
                    committedRoutes.size != payload.routes.size ||
                    committedSteps.size != remappedSteps.size ||
                    committedRuns.size != payload.followRuns.size ||
                    committedRouteRuns.size != payload.routeFollowRuns.size
                ) {
                    throw IOException("Database verification failed after restore commit.")
                }

                // Phase 4: Transition journal to COMMITTED_PENDING_CLEANUP before deleting recovery snapshots
                journalRecord = journalRecord.copy(
                    phase = RestoreJournalPhase.COMMITTED_PENDING_CLEANUP,
                    updatedAt = System.currentTimeMillis()
                )
                writeJournal(journalFile, journalRecord)

                stageFaultInjector?.invoke(RestoreFaultStage.DURING_CLEANUP)

                completePostCommitCleanup(journalRecord)
                _recoveryHealthFlow.value = RecoveryHealthState.Healthy

                BackupRestoreResult.Success(
                    message = "Restored ${remappedItems.size} entries and ${payload.folders.size} folders.",
                    itemCount = remappedItems.size,
                    folderCount = payload.folders.size,
                    generationId = targetGenerationId
                )
            } catch (kill: SimulatedProcessKillError) {
                // Leave journal & snapshots on disk to simulate abrupt process termination
                throw kill
            } catch (commitError: Exception) {
                performVerifiedRollback(
                    journal = journalRecord,
                    causeSummary = commitError.localizedMessage ?: commitError.javaClass.simpleName
                )
            }
        }
    }

    /**
     * Executes a verified rollback of BOTH live attachments and the Room database using the
     * durable snapshot files in `rollbackRootDir`.
     *
     * Never suppresses errors silently:
     * - If both attachment restoration and database restoration succeed and pass post-rollback
     *   verification, cleans up the journal and returns `Failure(..., rollbackVerified = true)`.
     * - If any rollback step or verification check fails, preserves all recovery snapshots on disk,
     *   records `FAILED_ROLLBACK` in the journal, sets `RecoveryHealthState.UnresolvedRecoveryError`,
     *   blocks normal vault writes, and returns `Failure(..., rollbackVerified = false)` without
     *   claiming that original data was preserved.
     */
    private suspend fun performVerifiedRollback(
        journal: RestoreJournalRecord,
        causeSummary: String
    ): BackupRestoreResult {
        val rollbackDir = attachmentStorage.rollbackRootDir
        val liveSnapshotDir = File(rollbackDir, LIVE_ATTACHMENT_SNAPSHOT_DIR)
        val dbSnapshotFile = File(rollbackDir, DB_SNAPSHOT_FILE_NAME)
        val journalFile = File(rollbackDir, JOURNAL_FILE_NAME)

        var diagnosticCode = "ERR_ROLLBACK_UNKNOWN"

        try {
            // 1. Verify snapshot files exist and match recorded SHA-256
            diagnosticCode = "ERR_ROLLBACK_SNAPSHOT_MISSING"
            if (!dbSnapshotFile.exists() || !liveSnapshotDir.exists()) {
                throw IOException("Recovery snapshot files are missing ($diagnosticCode).")
            }
            val actualDbSnapSha = attachmentStorage.computeSha256(dbSnapshotFile)
            if (journal.dbSnapshotSha256.isNotBlank() &&
                !actualDbSnapSha.equals(journal.dbSnapshotSha256, ignoreCase = true)
            ) {
                diagnosticCode = "ERR_ROLLBACK_SNAPSHOT_CORRUPT"
                throw IOException("Database recovery snapshot failed SHA-256 verification ($diagnosticCode).")
            }

            // 2. Restore live attachments from liveSnapshotDir and verify every file
            diagnosticCode = "ERR_ROLLBACK_ATTACHMENTS"
            stageFaultInjector?.invoke(RestoreFaultStage.DURING_ROLLBACK_ATTACHMENTS)

            val liveDir = attachmentStorage.attachmentsDir
            deleteDirectoryContents(liveDir)
            for (expectedFile in journal.preRestoreAttachments) {
                val snapSource = File(liveSnapshotDir, expectedFile.fileName)
                if (!snapSource.exists() || snapSource.length() != expectedFile.sizeBytes) {
                    throw IOException("Pre-restore attachment '${expectedFile.fileName}' missing from snapshot.")
                }
                val targetFile = File(liveDir, expectedFile.fileName)
                copyFileWithSync(snapSource, targetFile)
            }

            // Verify restored live attachments match exact pre-restore count, sizes, and SHA-256
            val restoredFiles = liveDir.listFiles()?.filter { it.isFile }.orEmpty()
            if (restoredFiles.size != journal.preRestoreAttachments.size) {
                throw IOException("Attachment count mismatch after rollback (${restoredFiles.size} vs ${journal.preRestoreAttachments.size}).")
            }
            for (expectedFile in journal.preRestoreAttachments) {
                val restored = File(liveDir, expectedFile.fileName)
                if (!restored.exists() || restored.length() != expectedFile.sizeBytes) {
                    throw IOException("Restored attachment '${expectedFile.fileName}' size mismatch.")
                }
                val restoredSha = attachmentStorage.computeSha256(restored)
                if (!restoredSha.equals(expectedFile.sha256, ignoreCase = true)) {
                    throw IOException("Restored attachment '${expectedFile.fileName}' checksum mismatch.")
                }
            }

            attachmentStorage.writeActiveGeneration(
                generationId = journal.previousGenerationId,
                fileChecksums = journal.preRestoreAttachments.associate { it.fileName to it.sha256 }
            )

            // 3. Restore Room database and preferences from verified rollback_db_snapshot.json
            diagnosticCode = "ERR_ROLLBACK_DATABASE"
            stageFaultInjector?.invoke(RestoreFaultStage.DURING_ROLLBACK_DATABASE)

            val dbSnapRoot = JSONObject(dbSnapshotFile.readText(Charsets.UTF_8))
            val prevFolders = parseFoldersJson(dbSnapRoot.getJSONArray("folders"), mutableSetOf())
            val prevItems = parseItemsJsonWithoutAttachmentCheck(dbSnapRoot.getJSONArray("items"))
            val prevRoutes = parseRoutesJsonWithoutCheck(dbSnapRoot.optJSONArray("routes") ?: JSONArray())
            val prevSteps = parseStepsJsonWithoutAttachmentCheck(dbSnapRoot.optJSONArray("steps") ?: JSONArray())
            val prevRuns = parseFollowRunsJsonWithoutCheck(dbSnapRoot.optJSONArray("followRuns") ?: JSONArray())
            val prevRouteRuns = parseRouteFollowRunsJsonWithoutCheck(dbSnapRoot.optJSONArray("routeFollowRuns") ?: JSONArray())
            val prevCustomTypes = parseCustomTypesJson(dbSnapRoot.optJSONArray("customTypes") ?: JSONArray())

            dao.replaceVaultWithValidatedBackup(
                folders = prevFolders,
                items = prevItems,
                steps = prevSteps,
                followRuns = prevRuns,
                routes = prevRoutes,
                routeFollowRuns = prevRouteRuns,
                customTypes = prevCustomTypes
            )
            database?.checkpointWal()

            val prefsObj = dbSnapRoot.optJSONObject("preferences")
            if (prefsObj != null) {
                val prevUser = prefsObj.optString("userName", "")
                if (prevUser.isNotBlank()) {
                    prefsRepo.updateUserName(prevUser)
                }
                val prevTheme = prefsObj.optString("themeMode", AppThemeMode.DARK.name)
                try {
                    prefsRepo.updateThemeMode(AppThemeMode.valueOf(prevTheme))
                } catch (_: IllegalArgumentException) {
                    // Ignore unknown theme enum
                }
            }

            // 4. Verify live Room database matches pre-restore counts and IDs
            diagnosticCode = "ERR_ROLLBACK_VERIFY_DB"
            val currentItems = dao.getAllItemsSnapshot()
            val currentFolders = dao.getAllFoldersSnapshot()
            val currentSteps = dao.getAllStepsSnapshot()
            val currentRuns = dao.getAllFollowRunsSnapshot()

            if (currentItems.size != journal.preRestoreItemCount ||
                currentFolders.size != journal.preRestoreFolderCount ||
                currentSteps.size != journal.preRestoreStepCount ||
                currentRuns.size != journal.preRestoreRunCount
            ) {
                throw IOException("Database record count mismatch after rollback verification.")
            }

            val expectedItemIds = prevItems.map { it.id }.toSet()
            val actualItemIds = currentItems.map { it.id }.toSet()
            if (expectedItemIds != actualItemIds) {
                throw IOException("Database item identifier mismatch after rollback verification.")
            }

            // 5. Both attachment and database rollbacks succeeded and passed verification!
            // Now it is safe to clean up temporary staging, aborted target generation, and rollback files.
            val abortedGenDir = attachmentStorage.getGenerationDir(journal.targetGenerationId)
            deleteDirectoryContents(abortedGenDir)
            abortedGenDir.delete()
            if (journal.stagingDirPath.isNotBlank()) {
                cleanupStagingDir(File(journal.stagingDirPath))
            }
            journalFile.delete()
            deleteDirectoryContents(rollbackDir)

            _recoveryHealthFlow.value = RecoveryHealthState.Healthy
            return BackupRestoreResult.Failure(
                reason = "Restore failed and original vault was verified and preserved: $causeSummary",
                rollbackVerified = true,
                diagnosticCode = null
            )
        } catch (kill: SimulatedProcessKillError) {
            throw kill
        } catch (rollbackError: Exception) {
            // Rollback failed! Preserve recovery snapshots, record FAILED_ROLLBACK in journal,
            // block normal vault writes, and report accurate non-destructive error.
            val failedJournal = journal.copy(
                phase = RestoreJournalPhase.FAILED_ROLLBACK,
                diagnosticCode = diagnosticCode,
                updatedAt = System.currentTimeMillis()
            )
            try {
                writeJournal(journalFile, failedJournal)
            } catch (_: Exception) {
                // Keep existing journal file on disk if writing updated phase fails
            }

            val safeMessage =
                "Restore failed and automatic rollback could not be verified (code: $diagnosticCode, phase: ${journal.phase.name}). " +
                    "Recovery snapshot is preserved and vault writes are blocked until recovery succeeds."

            _recoveryHealthFlow.value = RecoveryHealthState.UnresolvedRecoveryError(
                diagnosticCode = diagnosticCode,
                failedPhase = journal.phase.name,
                safeMessage = safeMessage,
                timestamp = System.currentTimeMillis()
            )

            return BackupRestoreResult.Failure(
                reason = safeMessage,
                rollbackVerified = false,
                diagnosticCode = diagnosticCode
            )
        }
    }

    private suspend fun verifyCommittedTargetState(journal: RestoreJournalRecord): Boolean {
        return try {
            val liveDir = attachmentStorage.attachmentsDir
            for (targetAtt in journal.targetAttachments) {
                val file = File(liveDir, targetAtt.fileName)
                if (!file.exists() || file.length() != targetAtt.sizeBytes) return false
                val sha = attachmentStorage.computeSha256(file)
                if (!sha.equals(targetAtt.sha256, ignoreCase = true)) return false
            }
            val itemsCount = dao.getAllItemsSnapshot().size
            val foldersCount = dao.getAllFoldersSnapshot().size
            itemsCount == journal.targetItemCount && foldersCount == journal.targetFolderCount
        } catch (_: Exception) {
            false
        }
    }

    private fun completePostCommitCleanup(journal: RestoreJournalRecord) {
        val rollbackDir = attachmentStorage.rollbackRootDir
        val journalFile = File(rollbackDir, JOURNAL_FILE_NAME)
        if (journal.stagingDirPath.isNotBlank()) {
            cleanupStagingDir(File(journal.stagingDirPath))
        }
        // Remove older generation directories while keeping the active target generation
        val activeGenDirName = "gen_${journal.targetGenerationId.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
        attachmentStorage.generationsRootDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && dir.name != activeGenDirName) {
                deleteDirectoryContents(dir)
                dir.delete()
            }
        }
        journalFile.delete()
        deleteDirectoryContents(rollbackDir)
    }

    private fun writeJournal(journalFile: File, record: RestoreJournalRecord) {
        val obj = JSONObject().apply {
            put("phase", record.phase.name)
            put("targetGenerationId", record.targetGenerationId)
            put("previousGenerationId", record.previousGenerationId)
            put("dbSnapshotSha256", record.dbSnapshotSha256)
            put("preRestoreItemCount", record.preRestoreItemCount)
            put("preRestoreFolderCount", record.preRestoreFolderCount)
            put("preRestoreStepCount", record.preRestoreStepCount)
            put("preRestoreRunCount", record.preRestoreRunCount)
            put("targetItemCount", record.targetItemCount)
            put("targetFolderCount", record.targetFolderCount)
            put("stagingDirPath", record.stagingDirPath)
            put("diagnosticCode", record.diagnosticCode)
            put("updatedAt", record.updatedAt)

            val preAttArr = JSONArray()
            record.preRestoreAttachments.forEach { att ->
                preAttArr.put(
                    JSONObject().apply {
                        put("fileName", att.fileName)
                        put("sizeBytes", att.sizeBytes)
                        put("sha256", att.sha256)
                    }
                )
            }
            put("preRestoreAttachments", preAttArr)

            val targetAttArr = JSONArray()
            record.targetAttachments.forEach { att ->
                targetAttArr.put(
                    JSONObject().apply {
                        put("fileName", att.fileName)
                        put("sizeBytes", att.sizeBytes)
                        put("sha256", att.sha256)
                    }
                )
            }
            put("targetAttachments", targetAttArr)
        }
        attachmentStorage.atomicWriteText(journalFile, obj.toString(2))
    }

    private fun readJournal(journalFile: File): RestoreJournalRecord {
        val obj = JSONObject(journalFile.readText(Charsets.UTF_8))
        val phase = RestoreJournalPhase.valueOf(obj.getString("phase"))
        val preAtts = mutableListOf<AttachmentFileRecord>()
        val preArr = obj.optJSONArray("preRestoreAttachments") ?: JSONArray()
        for (i in 0 until preArr.length()) {
            val item = preArr.getJSONObject(i)
            preAtts.add(
                AttachmentFileRecord(
                    fileName = item.getString("fileName"),
                    sizeBytes = item.getLong("sizeBytes"),
                    sha256 = item.getString("sha256")
                )
            )
        }
        val targetAtts = mutableListOf<AttachmentFileRecord>()
        val targetArr = obj.optJSONArray("targetAttachments") ?: JSONArray()
        for (i in 0 until targetArr.length()) {
            val item = targetArr.getJSONObject(i)
            targetAtts.add(
                AttachmentFileRecord(
                    fileName = item.getString("fileName"),
                    sizeBytes = item.getLong("sizeBytes"),
                    sha256 = item.getString("sha256")
                )
            )
        }
        return RestoreJournalRecord(
            phase = phase,
            targetGenerationId = obj.optString("targetGenerationId", ""),
            previousGenerationId = obj.optString("previousGenerationId", "gen_initial"),
            dbSnapshotSha256 = obj.optString("dbSnapshotSha256", ""),
            preRestoreItemCount = obj.optInt("preRestoreItemCount", 0),
            preRestoreFolderCount = obj.optInt("preRestoreFolderCount", 0),
            preRestoreStepCount = obj.optInt("preRestoreStepCount", 0),
            preRestoreRunCount = obj.optInt("preRestoreRunCount", 0),
            preRestoreAttachments = preAtts,
            targetItemCount = obj.optInt("targetItemCount", 0),
            targetFolderCount = obj.optInt("targetFolderCount", 0),
            targetAttachments = targetAtts,
            stagingDirPath = obj.optString("stagingDirPath", ""),
            diagnosticCode = obj.optString("diagnosticCode", ""),
            updatedAt = obj.optLong("updatedAt", 0L)
        )
    }

    private fun buildDbSnapshotJson(
        folders: List<FolderEntity>,
        items: List<VaultItemEntity>,
        routes: List<MethodRouteEntity>,
        steps: List<MethodStepEntity>,
        followRuns: List<MethodFollowRunEntity>,
        routeFollowRuns: List<MethodRouteFollowRunEntity>,
        userName: String,
        themeMode: String,
        customTypes: List<CustomTypeEntity> = emptyList()
    ): String {
        val root = JSONObject()
        root.put("preferences", JSONObject().apply {
            put("userName", userName)
            put("themeMode", themeMode)
        })
        root.put("folders", encodeFoldersJson(folders))
        root.put("customTypes", encodeCustomTypesJson(customTypes))
        root.put("items", encodeItemsJson(items))
        root.put("routes", encodeRoutesJson(routes))
        root.put("steps", encodeStepsJson(steps))
        root.put("followRuns", encodeFollowRunsJson(followRuns))
        root.put("routeFollowRuns", encodeRouteFollowRunsJson(routeFollowRuns))
        return root.toString()
    }

    private fun encodeCustomTypesJson(customTypes: List<CustomTypeEntity>): JSONArray {
        val arr = JSONArray()
        customTypes.forEach { ct ->
            arr.put(
                JSONObject().apply {
                    put("id", ct.id)
                    put("name", ct.name)
                    put("iconName", ct.iconName)
                    put("colorHex", ct.colorHex)
                    put("description", ct.description)
                    put("fieldsJson", ct.fieldsJson)
                    put("createdAt", ct.createdAt)
                    put("updatedAt", ct.updatedAt)
                }
            )
        }
        return arr
    }

    private fun parseCustomTypesJson(arr: JSONArray): List<CustomTypeEntity> {
        val list = mutableListOf<CustomTypeEntity>()
        val seenIds = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty() || !seenIds.add(id)) continue
            list.add(
                CustomTypeEntity(
                    id = id,
                    name = name,
                    iconName = obj.optString("iconName", "Extension"),
                    colorHex = obj.optString("colorHex", "#5E35B1"),
                    description = obj.optString("description", ""),
                    fieldsJson = obj.optString("fieldsJson", "[]"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return list
    }

    private fun encodeRoutesJson(routes: List<MethodRouteEntity>): JSONArray {
        val arr = JSONArray()
        routes.forEach { route ->
            arr.put(
                JSONObject().apply {
                    put("id", route.id)
                    put("itemId", route.itemId)
                    put("name", route.name)
                    put("description", route.description)
                    put("routeOrder", route.routeOrder)
                    put("isMain", route.isMain)
                }
            )
        }
        return arr
    }

    private fun encodeRouteFollowRunsJson(runs: List<MethodRouteFollowRunEntity>): JSONArray {
        val arr = JSONArray()
        runs.forEach { run ->
            arr.put(
                JSONObject().apply {
                    put("itemId", run.itemId)
                    put("routeId", run.routeId)
                    put("completedStepIdsJson", run.completedStepIdsJson)
                    put("startedAt", run.startedAt)
                    put("updatedAt", run.updatedAt)
                }
            )
        }
        return arr
    }

    private fun encodeFoldersJson(folders: List<FolderEntity>): JSONArray {
        val arr = JSONArray()
        folders.forEach { folder ->
            arr.put(
                JSONObject().apply {
                    put("id", folder.id)
                    put("name", folder.name)
                    put("category", folder.category)
                    put("colorHex", folder.colorHex)
                    put("createdAt", folder.createdAt)
                    put("updatedAt", folder.updatedAt)
                }
            )
        }
        return arr
    }

    private fun encodeItemsJson(items: List<VaultItemEntity>): JSONArray {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("description", item.description)
                    put("content", item.content)
                    put("category", item.category)
                    put("folderId", item.folderId ?: JSONObject.NULL)
                    put("tagsJson", item.tagsJson)
                    put("richBlocksJson", item.richBlocksJson)
                    put("attachmentsJson", item.attachmentsJson)
                    put("ideaStatus", item.ideaStatus)
                    put("platform", item.platform)
                    put("estimatedTime", item.estimatedTime)
                    put("notes", item.notes)
                    put("customTypeId", item.customTypeId ?: JSONObject.NULL)
                    put("customFieldsJson", item.customFieldsJson)
                    put("isFavorite", item.isFavorite)
                    put("isPinned", item.isPinned)
                    put("isArchived", item.isArchived)
                    put("isTrashed", item.isTrashed)
                    put("createdAt", item.createdAt)
                    put("updatedAt", item.updatedAt)
                }
            )
        }
        return arr
    }

    private fun encodeStepsJson(steps: List<MethodStepEntity>): JSONArray {
        val arr = JSONArray()
        steps.forEach { step ->
            arr.put(
                JSONObject().apply {
                    put("id", step.id)
                    put("itemId", step.itemId)
                    put("stepOrder", step.stepOrder)
                    put("title", step.title)
                    put("description", step.description)
                    put("notes", step.notes)
                    put("linksJson", step.linksJson)
                    put("imagesJson", step.imagesJson)
                    put("routeId", step.routeId.ifBlank { MAIN_METHOD_ROUTE_ID })
                }
            )
        }
        return arr
    }

    private fun encodeFollowRunsJson(runs: List<MethodFollowRunEntity>): JSONArray {
        val arr = JSONArray()
        runs.forEach { run ->
            arr.put(
                JSONObject().apply {
                    put("itemId", run.itemId)
                    put("completedStepIdsJson", run.completedStepIdsJson)
                    put("startedAt", run.startedAt)
                    put("updatedAt", run.updatedAt)
                }
            )
        }
        return arr
    }

    private fun parseRoutesJson(
        routesArr: JSONArray,
        seenRouteIds: MutableSet<String>,
        seenItemIds: Set<String>
    ): List<MethodRouteEntity> {
        val routes = mutableListOf<MethodRouteEntity>()
        val seenPrimaryKeys = mutableSetOf<String>()
        for (i in 0 until routesArr.length()) {
            val obj = routesArr.getJSONObject(i)
            val rawId = obj.optString("id", "").trim()
            val itemId = obj.optString("itemId", "").trim()
            val name = obj.optString("name", "").trim()
            if (rawId.isEmpty() || itemId.isEmpty() || name.isEmpty()) {
                throw IllegalArgumentException("Malformed method route record at index $i.")
            }
            if (!seenItemIds.contains(itemId)) {
                throw IllegalArgumentException("Backup rejected: route '$rawId' references non-existent item '$itemId'.")
            }
            val compositeKey = "$itemId:$rawId"
            if (!seenRouteIds.add(compositeKey)) {
                throw IllegalArgumentException("Backup rejected: duplicate route identifier '$rawId' for item '$itemId'.")
            }
            val isMain = obj.optBoolean(
                "isMain",
                rawId == MAIN_METHOD_ROUTE_ID || rawId == "${itemId}_$MAIN_METHOD_ROUTE_ID"
            )
            val normalizedEntityId = if (isMain && rawId == MAIN_METHOD_ROUTE_ID) {
                "${itemId}_$MAIN_METHOD_ROUTE_ID"
            } else {
                rawId
            }
            if (!seenPrimaryKeys.add(normalizedEntityId)) {
                throw IllegalArgumentException("Backup rejected: duplicate route primary key '$normalizedEntityId'.")
            }
            routes.add(
                MethodRouteEntity(
                    id = normalizedEntityId,
                    itemId = itemId,
                    name = name,
                    description = obj.optString("description", ""),
                    routeOrder = obj.optInt("routeOrder", i),
                    isMain = isMain
                )
            )
        }
        return routes
    }

    private fun parseRoutesJsonWithoutCheck(routesArr: JSONArray): List<MethodRouteEntity> {
        val routes = mutableListOf<MethodRouteEntity>()
        for (i in 0 until routesArr.length()) {
            val obj = routesArr.getJSONObject(i)
            val id = obj.optString("id", MAIN_METHOD_ROUTE_ID)
            routes.add(
                MethodRouteEntity(
                    id = id,
                    itemId = obj.optString("itemId", ""),
                    name = obj.optString("name", DEFAULT_MAIN_METHOD_NAME),
                    description = obj.optString("description", ""),
                    routeOrder = obj.optInt("routeOrder", i),
                    isMain = obj.optBoolean("isMain", id == MAIN_METHOD_ROUTE_ID)
                )
            )
        }
        return routes
    }

    private fun parseRouteFollowRunsJson(
        runsArr: JSONArray,
        seenItemIds: Set<String>
    ): List<MethodRouteFollowRunEntity> {
        val routeRuns = mutableListOf<MethodRouteFollowRunEntity>()
        val seenKeys = mutableSetOf<String>()
        for (i in 0 until runsArr.length()) {
            val obj = runsArr.getJSONObject(i)
            val itemId = obj.optString("itemId", "").trim()
            val routeId = obj.optString("routeId", MAIN_METHOD_ROUTE_ID).trim().ifEmpty { MAIN_METHOD_ROUTE_ID }
            if (itemId.isEmpty() || !seenItemIds.contains(itemId)) {
                throw IllegalArgumentException("Backup rejected: routeFollowRun references invalid item '$itemId'.")
            }
            val key = "$itemId:$routeId"
            if (!seenKeys.add(key)) {
                throw IllegalArgumentException("Backup rejected: duplicate routeFollowRun for '$key'.")
            }
            routeRuns.add(
                MethodRouteFollowRunEntity(
                    itemId = itemId,
                    routeId = routeId,
                    completedStepIdsJson = obj.optString("completedStepIdsJson", "[]"),
                    startedAt = obj.optLong("startedAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return routeRuns
    }

    private fun parseRouteFollowRunsJsonWithoutCheck(runsArr: JSONArray): List<MethodRouteFollowRunEntity> {
        val routeRuns = mutableListOf<MethodRouteFollowRunEntity>()
        for (i in 0 until runsArr.length()) {
            val obj = runsArr.getJSONObject(i)
            routeRuns.add(
                MethodRouteFollowRunEntity(
                    itemId = obj.optString("itemId", ""),
                    routeId = obj.optString("routeId", MAIN_METHOD_ROUTE_ID).ifBlank { MAIN_METHOD_ROUTE_ID },
                    completedStepIdsJson = obj.optString("completedStepIdsJson", "[]"),
                    startedAt = obj.optLong("startedAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return routeRuns
    }

    private fun parseFoldersJson(foldersArr: JSONArray, seenFolderIds: MutableSet<String>): List<FolderEntity> {
        val folders = mutableListOf<FolderEntity>()
        for (i in 0 until foldersArr.length()) {
            val obj = foldersArr.getJSONObject(i)
            val id = obj.optString("id", "").trim()
            val name = obj.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) {
                throw IllegalArgumentException("Malformed folder record at index $i.")
            }
            if (!seenFolderIds.add(id)) {
                throw IllegalArgumentException("Backup rejected: duplicate folder identifier '$id'.")
            }
            folders.add(
                FolderEntity(
                    id = id,
                    name = name,
                    category = VaultCategory.fromId(obj.optString("category", VaultCategory.IDEA.id)).id,
                    colorHex = obj.optString("colorHex", "#FF7654"),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return folders
    }

    private fun parseItemsJson(
        itemsArr: JSONArray,
        seenItemIds: MutableSet<String>,
        seenFolderIds: Set<String>,
        stagedFiles: Map<String, File>
    ): List<VaultItemEntity> {
        val items = mutableListOf<VaultItemEntity>()
        for (i in 0 until itemsArr.length()) {
            val obj = itemsArr.getJSONObject(i)
            val id = obj.optString("id", "").trim()
            val title = obj.optString("title", "").trim()
            if (id.isEmpty() || title.isEmpty()) {
                throw IllegalArgumentException("Malformed item record at index $i: missing id or title.")
            }
            if (!seenItemIds.add(id)) {
                throw IllegalArgumentException("Backup rejected: duplicate item identifier '$id'.")
            }

            val rawFolderId = if (obj.isNull("folderId")) null else obj.optString("folderId", "").ifBlank { null }
            if (rawFolderId != null && !seenFolderIds.contains(rawFolderId)) {
                throw IllegalArgumentException("Backup rejected: item '$id' references non-existent folder '$rawFolderId'.")
            }

            val attachmentsJson = obj.optString("attachmentsJson", "[]")
            val decodedAtts = VaultJsonSerializers.decodeAttachments(attachmentsJson)
            for (att in decodedAtts) {
                if (att.type != AttachmentType.LINK) {
                    val fileName = File(att.uriOrUrl).name
                    if (fileName.isBlank() || !stagedFiles.containsKey(fileName)) {
                        throw IllegalArgumentException("Backup rejected: item '$id' references missing attachment '$fileName'.")
                    }
                }
            }

            val rawCustomTypeId = if (obj.isNull("customTypeId")) null else obj.optString("customTypeId", "").ifBlank { null }
            items.add(
                VaultItemEntity(
                    id = id,
                    title = title,
                    description = obj.optString("description", ""),
                    content = obj.optString("content", ""),
                    category = VaultCategory.fromId(obj.optString("category", VaultCategory.IDEA.id)).id,
                    folderId = rawFolderId,
                    tagsJson = obj.optString("tagsJson", "[]"),
                    richBlocksJson = obj.optString("richBlocksJson", "[]"),
                    attachmentsJson = attachmentsJson,
                    ideaStatus = IdeaStatus.fromId(obj.optString("ideaStatus", IdeaStatus.NEW.id)).id,
                    platform = obj.optString("platform", ""),
                    estimatedTime = obj.optString("estimatedTime", ""),
                    notes = obj.optString("notes", ""),
                    customTypeId = rawCustomTypeId,
                    customFieldsJson = obj.optString("customFieldsJson", "{}"),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    isPinned = obj.optBoolean("isPinned", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    isTrashed = obj.optBoolean("isTrashed", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return items
    }

    private fun parseItemsJsonWithoutAttachmentCheck(itemsArr: JSONArray): List<VaultItemEntity> {
        val items = mutableListOf<VaultItemEntity>()
        for (i in 0 until itemsArr.length()) {
            val obj = itemsArr.getJSONObject(i)
            val rawFolderId = if (obj.isNull("folderId")) null else obj.optString("folderId", "").ifBlank { null }
            val rawCustomTypeId = if (obj.isNull("customTypeId")) null else obj.optString("customTypeId", "").ifBlank { null }
            items.add(
                VaultItemEntity(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    content = obj.optString("content", ""),
                    category = obj.optString("category", VaultCategory.IDEA.id),
                    folderId = rawFolderId,
                    tagsJson = obj.optString("tagsJson", "[]"),
                    richBlocksJson = obj.optString("richBlocksJson", "[]"),
                    attachmentsJson = obj.optString("attachmentsJson", "[]"),
                    ideaStatus = obj.optString("ideaStatus", IdeaStatus.NEW.id),
                    platform = obj.optString("platform", ""),
                    estimatedTime = obj.optString("estimatedTime", ""),
                    notes = obj.optString("notes", ""),
                    customTypeId = rawCustomTypeId,
                    customFieldsJson = obj.optString("customFieldsJson", "{}"),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    isPinned = obj.optBoolean("isPinned", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    isTrashed = obj.optBoolean("isTrashed", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return items
    }

    private fun parseStepsJson(
        stepsArr: JSONArray,
        seenStepIds: MutableSet<String>,
        seenItemIds: Set<String>,
        stagedFiles: Map<String, File>
    ): List<MethodStepEntity> {
        val steps = mutableListOf<MethodStepEntity>()
        for (i in 0 until stepsArr.length()) {
            val obj = stepsArr.getJSONObject(i)
            val id = obj.optString("id", "").trim()
            val itemId = obj.optString("itemId", "").trim()
            val stepTitle = obj.optString("title", "").trim()
            if (id.isEmpty() || itemId.isEmpty() || stepTitle.isEmpty()) {
                throw IllegalArgumentException("Malformed step record at index $i.")
            }
            if (!seenItemIds.contains(itemId)) {
                throw IllegalArgumentException("Backup rejected: step '$id' references non-existent item '$itemId'.")
            }
            if (!seenStepIds.add(id)) {
                throw IllegalArgumentException("Backup rejected: duplicate step identifier '$id'.")
            }
            val stepImagesJson = obj.optString("imagesJson", "[]")
            val stepImages = VaultJsonSerializers.decodeStringList(stepImagesJson)
            for (imgPath in stepImages) {
                val fileName = File(imgPath).name
                if (fileName.isBlank() || !stagedFiles.containsKey(fileName)) {
                    throw IllegalArgumentException("Backup rejected: step '$id' references missing image '$fileName'.")
                }
            }

            steps.add(
                MethodStepEntity(
                    id = id,
                    itemId = itemId,
                    stepOrder = obj.optInt("stepOrder", i + 1),
                    title = stepTitle,
                    description = obj.optString("description", ""),
                    notes = obj.optString("notes", ""),
                    linksJson = obj.optString("linksJson", "[]"),
                    imagesJson = stepImagesJson,
                    routeId = obj.optString("routeId", MAIN_METHOD_ROUTE_ID).ifBlank { MAIN_METHOD_ROUTE_ID }
                )
            )
        }
        return steps
    }

    private fun parseStepsJsonWithoutAttachmentCheck(stepsArr: JSONArray): List<MethodStepEntity> {
        val steps = mutableListOf<MethodStepEntity>()
        for (i in 0 until stepsArr.length()) {
            val obj = stepsArr.getJSONObject(i)
            steps.add(
                MethodStepEntity(
                    id = obj.getString("id"),
                    itemId = obj.getString("itemId"),
                    stepOrder = obj.optInt("stepOrder", i + 1),
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    notes = obj.optString("notes", ""),
                    linksJson = obj.optString("linksJson", "[]"),
                    imagesJson = obj.optString("imagesJson", "[]"),
                    routeId = obj.optString("routeId", MAIN_METHOD_ROUTE_ID).ifBlank { MAIN_METHOD_ROUTE_ID }
                )
            )
        }
        return steps
    }

    private fun parseFollowRunsJson(
        runsArr: JSONArray,
        seenRunItemIds: MutableSet<String>,
        seenItemIds: Set<String>
    ): List<MethodFollowRunEntity> {
        val followRuns = mutableListOf<MethodFollowRunEntity>()
        for (i in 0 until runsArr.length()) {
            val obj = runsArr.getJSONObject(i)
            val itemId = obj.optString("itemId", "").trim()
            if (itemId.isEmpty() || !seenItemIds.contains(itemId)) {
                throw IllegalArgumentException("Backup rejected: followRun references invalid item '$itemId'.")
            }
            if (!seenRunItemIds.add(itemId)) {
                throw IllegalArgumentException("Backup rejected: duplicate followRun for item '$itemId'.")
            }
            followRuns.add(
                MethodFollowRunEntity(
                    itemId = itemId,
                    completedStepIdsJson = obj.optString("completedStepIdsJson", "[]"),
                    startedAt = obj.optLong("startedAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return followRuns
    }

    private fun parseFollowRunsJsonWithoutCheck(runsArr: JSONArray): List<MethodFollowRunEntity> {
        val followRuns = mutableListOf<MethodFollowRunEntity>()
        for (i in 0 until runsArr.length()) {
            val obj = runsArr.getJSONObject(i)
            followRuns.add(
                MethodFollowRunEntity(
                    itemId = obj.getString("itemId"),
                    completedStepIdsJson = obj.optString("completedStepIdsJson", "[]"),
                    startedAt = obj.optLong("startedAt", System.currentTimeMillis()),
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            )
        }
        return followRuns
    }

    fun cleanupStagingPayload(payload: ValidatedBackupPayload?) {
        if (payload != null) {
            cleanupStagingDir(payload.stagingDir)
        }
    }

    private fun cleanupStagingDir(dir: File) {
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun deleteDirectoryContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                child.deleteRecursively()
            } else {
                child.delete()
            }
        }
    }

    private fun copyFileWithSync(source: File, dest: File) {
        val parent = dest.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
                output.flush()
                output.fd.sync()
            }
        }
    }

    private fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(8192)
        var total = 0L
        var read = input.read(buffer)
        while (read != -1) {
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("Archive entry exceeds maximum allowed size ($maxBytes bytes).")
            }
            output.write(buffer, 0, read)
            read = input.read(buffer)
        }
        return total
    }
}
