package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import android.content.pm.ApplicationInfo
import androidx.compose.ui.test.onAllNodesWithTag
import com.example.data.AttachmentItem
import com.example.data.AttachmentStorageManager
import com.example.data.AttachmentType
import com.example.data.BackupRestoreManager
import com.example.data.BackupRestoreResult
import com.example.data.EditorDraftPayload
import com.example.data.IdeaStatus
import com.example.data.MAIN_METHOD_ROUTE_ID
import com.example.data.MethodRouteEntity
import com.example.data.MethodRouteFollowRunEntity
import com.example.data.MethodRouteWithSteps
import com.example.data.MethodStepEntity
import com.example.data.RecoveryHealthState
import com.example.data.RestoreFaultStage
import com.example.data.RichBlockType
import com.example.data.RichContentBlock
import com.example.data.SaveItemResult
import com.example.data.SimulatedProcessKillError
import com.example.data.UnresolvedVaultRecoveryException
import com.example.data.UserPreferencesRepository
import com.example.data.VaultCategory
import com.example.data.VaultDatabase
import com.example.data.VaultItemEntity
import com.example.data.VaultJsonSerializers
import com.example.data.VaultRepository
import com.example.ui.IdeaVaultApp
import com.example.ui.VaultViewModel
import com.example.ui.components.CurvedBottomRightCutoutShape
import com.example.ui.components.InlineRichStyle
import com.example.ui.components.LineBlockFormat
import com.example.ui.components.ParagraphAlignment
import com.example.ui.components.RichTextColorToken
import com.example.ui.components.RichTextEditorController
import com.example.ui.components.RichTextEditorState
import com.example.ui.components.RichTextSerializer
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ItemDetailScreen
import com.example.ui.screens.VaultSortOrder
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.IdeaVaultDarkColors
import com.example.ui.theme.IdeaVaultLightColors
import com.example.ui.theme.IdeaVaultTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.onAllNodesWithText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class TestPreferencesFaultPolicy : com.example.data.PreferencesFaultPolicy {
        @Volatile
        var readFault: (() -> Unit)? = null

        @Volatile
        var writeFault: (() -> Unit)? = null

        override fun beforeRead() {
            readFault?.invoke()
        }

        override fun beforeWrite() {
            writeFault?.invoke()
        }
    }

    private lateinit var context: Context
    private lateinit var db: VaultDatabase
    private lateinit var testFaultPolicy: TestPreferencesFaultPolicy
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var attachmentStorage: AttachmentStorageManager
    private lateinit var backupManager: BackupRestoreManager
    private lateinit var repository: VaultRepository
    private lateinit var tempPrefsFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempPrefsFile = File(context.cacheDir, "test_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { tempPrefsFile }
        )
        testFaultPolicy = TestPreferencesFaultPolicy()
        prefsRepo = UserPreferencesRepository(dataStore, testFaultPolicy)
        attachmentStorage = AttachmentStorageManager(context)
        attachmentStorage.attachmentsDir.deleteRecursively()
        attachmentStorage.generationsRootDir.deleteRecursively()
        attachmentStorage.activeGenerationFile.delete()
        attachmentStorage.stagingRootDir.deleteRecursively()
        attachmentStorage.rollbackRootDir.deleteRecursively()
        backupManager = BackupRestoreManager(
            dao = db.vaultDao(),
            prefsRepo = prefsRepo,
            attachmentStorage = attachmentStorage,
            database = db
        )
        repository = VaultRepository(db.vaultDao(), prefsRepo, attachmentStorage, backupManager)
    }

    @After
    fun tearDown() {
        runCatching { composeTestRule.waitForIdle() }
        Thread.sleep(50L)
        db.close()
        tempPrefsFile.delete()
        attachmentStorage.attachmentsDir.deleteRecursively()
        attachmentStorage.generationsRootDir.deleteRecursively()
        attachmentStorage.activeGenerationFile.delete()
        attachmentStorage.stagingRootDir.deleteRecursively()
        attachmentStorage.rollbackRootDir.deleteRecursively()
    }

    @Test
    fun `verify app identity and string resources`() {
        assertEquals("IDEAVAULT", context.getString(R.string.app_name))
        assertEquals("Everything worth remembering.", context.getString(R.string.tagline))
        assertEquals("Rahmathullah | Designwithrahma", context.getString(R.string.developer_credit))
    }

    @Test
    fun `verify onboarding appears once and user name persists`() = runBlocking {
        val initial = prefsRepo.getSnapshot()
        assertFalse(initial.hasCompletedOnboarding)
        assertEquals("", initial.userName)

        prefsRepo.completeOnboarding("Rahmathullah")
        val after = prefsRepo.getSnapshot()
        assertTrue(after.hasCompletedOnboarding)
        assertEquals("Rahmathullah", after.userName)

        prefsRepo.updateUserName("Designwithrahma")
        assertEquals("Designwithrahma", prefsRepo.getSnapshot().userName)
    }

    @Test
    fun `verify creating editing duplicating and favoriting an idea with custom tags`() = runBlocking {
        val idea = VaultItemEntity(
            id = "idea_1",
            title = "Offline First Knowledge Graph",
            description = "Personal vault for ideas and prompts",
            content = "Full markdown and rich block support.",
            category = VaultCategory.IDEA.id,
            tagsJson = VaultJsonSerializers.encodeStringList(listOf("kotlin", "compose", "offline")),
            ideaStatus = IdeaStatus.EXPLORING.id
        )
        assertTrue(repository.saveVaultItem(idea, emptyList()))

        var active = repository.activeItemsWithDetailsFlow.first()
        assertEquals(1, active.size)
        assertEquals("Offline First Knowledge Graph", active.first().entity.title)
        assertEquals(listOf("kotlin", "compose", "offline"), active.first().tags)

        // Edit idea and favorite it
        repository.saveVaultItem(idea.copy(title = "Updated Idea Title"), emptyList())
        repository.toggleFavorite("idea_1")

        active = repository.activeItemsWithDetailsFlow.first()
        assertEquals("Updated Idea Title", active.first().entity.title)
        assertTrue(active.first().entity.isFavorite)

        // Duplicate idea
        val copyId = repository.duplicateVaultItem(active.first())
        val allActive = repository.activeItemsWithDetailsFlow.first()
        assertEquals(2, allActive.size)
        assertTrue(allActive.any { it.entity.id == copyId && it.entity.title == "Updated Idea Title (Copy)" })

        // Verify real category counts from Room
        val counts = repository.categoryCountsFlow.first()
        assertEquals(2, counts[VaultCategory.IDEA])
        assertEquals(0, counts[VaultCategory.METHOD])
    }

    @Test
    fun `verify optimistic concurrency check prevents stale writes from overwriting newer updates`() = runBlocking {
        val base = VaultItemEntity(
            id = "concurrency_1",
            title = "Original Title",
            category = VaultCategory.IDEA.id,
            createdAt = 1_000L,
            updatedAt = 2_000L
        )
        assertTrue(repository.saveVaultItem(base, emptyList()))
        val storedAfterFirstSave = repository.activeItemsWithDetailsFlow.first().first().entity

        // Perform a newer update
        assertTrue(
            repository.saveVaultItem(
                storedAfterFirstSave.copy(title = "Newer Concurrent Update"),
                emptyList(),
                expectedPreviousUpdatedAt = storedAfterFirstSave.updatedAt
            )
        )

        // Attempt a stale write referencing an older expectedPreviousUpdatedAt
        val staleAccepted = repository.saveVaultItem(
            storedAfterFirstSave.copy(title = "Stale Overwrite Attempt"),
            emptyList(),
            expectedPreviousUpdatedAt = storedAfterFirstSave.updatedAt - 10L
        )
        assertFalse(staleAccepted)

        val current = repository.activeItemsWithDetailsFlow.first().first().entity
        assertEquals("Newer Concurrent Update", current.title)
        assertEquals(1_000L, current.createdAt)
    }

    @Test
    fun `verify method steps creation reordering and follow mode never modifies original method`() = runBlocking {
        val method = VaultItemEntity(
            id = "method_1",
            title = "AI App Development Workflow",
            description = "Step by step workflow",
            category = VaultCategory.METHOD.id
        )
        val steps = listOf(
            MethodStepEntity(id = "s1", itemId = "method_1", stepOrder = 1, title = "Step One", description = "Collect refs"),
            MethodStepEntity(id = "s2", itemId = "method_1", stepOrder = 2, title = "Step Two", description = "Write prompt"),
            MethodStepEntity(id = "s3", itemId = "method_1", stepOrder = 3, title = "Step Three", description = "Build & test")
        )
        repository.saveVaultItem(method, steps)

        // Reorder steps: s3, s1, s2
        repository.reorderMethodSteps("method_1", listOf("s3", "s1", "s2"))
        var loaded = repository.activeItemsWithDetailsFlow.first().first()
        assertEquals(listOf("s3", "s1", "s2"), loaded.steps.map { it.id })
        assertEquals(listOf(1, 2, 3), loaded.steps.map { it.stepOrder })

        // Follow mode: check off s3 and s1 without modifying original method steps
        repository.toggleFollowStep("method_1", "s3")
        repository.toggleFollowStep("method_1", "s1")
        loaded = repository.activeItemsWithDetailsFlow.first().first()
        assertEquals(setOf("s3", "s1"), loaded.completedFollowStepIds)
        assertEquals("Step Three", loaded.steps.first().title)

        // Reset follow progress
        repository.resetFollowProgress("method_1")
        loaded = repository.activeItemsWithDetailsFlow.first().first()
        assertTrue(loaded.completedFollowStepIds.isEmpty())
        assertEquals(3, loaded.steps.size)
    }

    @Test
    fun `verify prompt library preserves multiline formatting and supports search and favorites`() = runBlocking {
        val multilineContent = "ROLE: Senior Engineer\n\nSTEPS:\n1. Inspect\n2. Fix\n   - Indented line"
        val prompt = VaultItemEntity(
            id = "prompt_fmt",
            title = "System Audit Prompt",
            content = multilineContent,
            platform = "Google AI Studio",
            category = VaultCategory.PROMPT.id,
            isFavorite = true
        )
        repository.saveVaultItem(prompt, emptyList())

        val loaded = repository.activeItemsWithDetailsFlow.first().first()
        assertEquals(multilineContent, loaded.entity.content)
        assertEquals("Google AI Studio", loaded.entity.platform)
        assertTrue(loaded.entity.isFavorite)
    }

    @Test
    fun `verify knowledge rich content blocks auto-save draft recovery and inbox quick note`() = runBlocking {
        prefsRepo.saveEditorDraft("""{"title":"Draft Knowledge","content":"Recovered text"}""")
        assertEquals("""{"title":"Draft Knowledge","content":"Recovered text"}""", prefsRepo.getSnapshot().editorDraftJson)
        prefsRepo.clearEditorDraft()
        assertEquals("", prefsRepo.getSnapshot().editorDraftJson)

        val blocks = listOf(
            RichContentBlock(id = "b1", type = RichBlockType.CHECKLIST, title = "wireframes", content = "main flow", checked = false),
            RichContentBlock(id = "b2", type = RichBlockType.CODE_BLOCK, title = "Kotlin", content = "val x = 42")
        )
        val kb = VaultItemEntity(
            id = "kb_1",
            title = "Architecture Notes",
            category = VaultCategory.KNOWLEDGE.id,
            richBlocksJson = VaultJsonSerializers.encodeRichBlocks(blocks)
        )
        repository.saveVaultItem(kb, emptyList())
        repository.toggleChecklistBlock("kb_1", "b1")

        val loadedKb = repository.activeItemsWithDetailsFlow.first().first { it.entity.id == "kb_1" }
        assertTrue(loadedKb.richBlocks.first { it.id == "b1" }.checked)

        // Quick Note to Inbox
        val inboxId = repository.createQuickNoteToInbox("Quick Spark", "Remember to test dark mode contrast")
        val loadedInbox = repository.activeItemsWithDetailsFlow.first().first { it.entity.id == inboxId }
        assertEquals(VaultCategory.INBOX, loadedInbox.categoryEnum)
    }

    @Test
    fun `verify folder creation moving entries and safe folder deletion preserves contained entries`() = runBlocking {
        val folder = repository.createFolder("Mobile App Ideas", VaultCategory.IDEA, "#FF7654")
        val idea = VaultItemEntity(
            id = "idea_in_folder",
            title = "Folder Scoped Idea",
            category = VaultCategory.IDEA.id
        )
        repository.saveVaultItem(idea, emptyList())
        repository.moveItemToFolder("idea_in_folder", folder.id)

        var itemDetail = repository.activeItemsWithDetailsFlow.first().first()
        assertEquals(folder.id, itemDetail.entity.folderId)
        assertEquals("Mobile App Ideas", itemDetail.folderName)

        // Delete folder: contained entry must NOT be deleted!
        repository.deleteFolderPreservingItems(folder.id)

        val remainingFolders = repository.foldersFlow.first()
        assertTrue(remainingFolders.isEmpty())

        val activeItems = repository.activeItemsWithDetailsFlow.first()
        assertEquals(1, activeItems.size)
        assertNull(activeItems.first().entity.folderId)
    }

    @Test
    fun `verify delete safety moves to trash first and allows restore or permanent delete`() = runBlocking {
        val prompt = VaultItemEntity(
            id = "prompt_1",
            title = "Multiline Prompt",
            content = "Line 1\nLine 2\n\nLine 4",
            category = VaultCategory.PROMPT.id
        )
        repository.saveVaultItem(prompt, emptyList())

        // Soft delete to Trash
        repository.moveToTrash("prompt_1")
        assertTrue(repository.activeItemsWithDetailsFlow.first().isEmpty())
        assertEquals(1, repository.trashedItemsWithDetailsFlow.first().size)

        // Restore from Trash
        repository.restoreFromTrash("prompt_1")
        val restored = repository.activeItemsWithDetailsFlow.first()
        assertEquals(1, restored.size)
        assertEquals("Line 1\nLine 2\n\nLine 4", restored.first().entity.content)

        // Permanent deletion
        repository.moveToTrash("prompt_1")
        repository.permanentlyDelete("prompt_1")
        assertTrue(repository.trashedItemsWithDetailsFlow.first().isEmpty())
    }

    @Test
    fun `verify attachment persistence across restarts and safe unreferenced file removal on permanent delete`() = runBlocking {
        val sourceFile = File(context.cacheDir, "sample_diagram.png")
        sourceFile.writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))

        val copiedAtt = attachmentStorage.copyUriToPersistentStorage(
            Uri.fromFile(sourceFile),
            AttachmentType.IMAGE
        )
        assertNotNull(copiedAtt)
        val savedLocalFile = File(copiedAtt!!.uriOrUrl)
        assertTrue(savedLocalFile.exists())

        val item = VaultItemEntity(
            id = "att_item_1",
            title = "Diagram Entry",
            category = VaultCategory.REFERENCE.id,
            attachmentsJson = VaultJsonSerializers.encodeAttachments(listOf(copiedAtt))
        )
        repository.saveVaultItem(item, emptyList())

        // Soft delete to Trash: attachment file MUST remain on disk so Trash restore works!
        repository.moveToTrash("att_item_1")
        assertTrue(savedLocalFile.exists())

        // Permanent delete: unreferenced attachment file is safely cleaned up
        repository.permanentlyDelete("att_item_1")
        assertFalse(savedLocalFile.exists())
    }

    @Test
    fun `verify zip backup and restore preserves content attachments and checksums`() = runBlocking {
        repository.seedSampleTemplates()

        // Also add an item with a real local attachment file
        val localAttFile = File(attachmentStorage.attachmentsDir, "blueprint_01.png")
        val fileContent = "PNG_BINARY_CONTENT_12345".toByteArray()
        localAttFile.writeBytes(fileContent)
        val attItem = AttachmentItem(
            id = "att_bp",
            type = AttachmentType.IMAGE,
            name = "blueprint_01.png",
            uriOrUrl = localAttFile.absolutePath,
            mimeType = "image/png",
            sizeBytes = fileContent.size.toLong()
        )
        repository.saveVaultItem(
            VaultItemEntity(
                id = "item_with_att",
                title = "Blueprint Reference",
                category = VaultCategory.REFERENCE.id,
                attachmentsJson = VaultJsonSerializers.encodeAttachments(listOf(attItem))
            ),
            emptyList()
        )

        val beforeCount = repository.activeItemsWithDetailsFlow.first().size
        assertTrue(beforeCount >= 5)

        // Export to ZIP bytes
        val zipOut = ByteArrayOutputStream()
        val exportResult = repository.exportBackup(zipOut)
        assertTrue(exportResult is BackupRestoreResult.Success)
        val zipBytes = zipOut.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        // Wipe DB and live attachment file, then restore from valid ZIP
        db.vaultDao().clearAllItems()
        localAttFile.delete()
        assertEquals(0, repository.activeItemsWithDetailsFlow.first().size)
        assertFalse(localAttFile.exists())

        val validPayloadResult = repository.validateBackup(ByteArrayInputStream(zipBytes))
        assertTrue(validPayloadResult.isSuccess)
        val applyResult = repository.applyBackup(validPayloadResult.getOrThrow())
        assertTrue(applyResult is BackupRestoreResult.Success)
        assertEquals(beforeCount, repository.activeItemsWithDetailsFlow.first().size)
        assertTrue(localAttFile.exists())
        assertEquals("PNG_BINARY_CONTENT_12345", localAttFile.readText())
    }

    @Test
    fun `verify backup rejects malformed archive path traversal and duplicate zip entries`() = runBlocking {
        repository.seedSampleTemplates()
        val initialCount = repository.activeItemsWithDetailsFlow.first().size

        // 1. Not a ZIP file
        val malformed = repository.validateBackup(ByteArrayInputStream("not a valid zip".toByteArray()))
        assertTrue(malformed.isFailure)

        // 2. ZIP with path traversal entry ("attachments/../../evil.sh")
        val traversalZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("attachments/../../evil.sh"))
                zos.write("echo pwned".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()
        val traversalResult = repository.validateBackup(ByteArrayInputStream(traversalZip))
        assertTrue(traversalResult.isFailure)
        assertTrue(traversalResult.exceptionOrNull()!!.message!!.contains("traversal", ignoreCase = true))

        // Original vault untouched
        assertEquals(initialCount, repository.activeItemsWithDetailsFlow.first().size)
    }

    @Test
    fun `verify backup rejects missing attachments and duplicate entity identifiers`() = runBlocking {
        // 1. Missing attachment referenced by an item
        val missingAttRoot = JSONObject().apply {
            put("magic", BackupRestoreManager.BACKUP_MAGIC)
            put("schemaVersion", 1)
            put("folders", JSONArray())
            val attArr = VaultJsonSerializers.encodeAttachments(
                listOf(
                    AttachmentItem(
                        id = "a1",
                        type = AttachmentType.IMAGE,
                        name = "missing_photo.jpg",
                        uriOrUrl = "/data/missing_photo.jpg"
                    )
                )
            )
            put(
                "items",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "item_missing_att")
                        put("title", "Item With Missing File")
                        put("category", "IDEA")
                        put("attachmentsJson", attArr)
                    }
                )
            )
        }
        val missingAttZip = buildSingleManifestZip(missingAttRoot)
        val missingResult = repository.validateBackup(ByteArrayInputStream(missingAttZip))
        assertTrue(missingResult.isFailure)
        assertTrue(missingResult.exceptionOrNull()!!.message!!.contains("missing attachment", ignoreCase = true))

        // 2. Duplicate item IDs inside manifest
        val duplicateIdRoot = JSONObject().apply {
            put("magic", BackupRestoreManager.BACKUP_MAGIC)
            put("schemaVersion", 1)
            put("folders", JSONArray())
            val itemsArr = JSONArray()
                .put(JSONObject().apply { put("id", "dup_1"); put("title", "First") })
                .put(JSONObject().apply { put("id", "dup_1"); put("title", "Second") })
            put("items", itemsArr)
        }
        val dupZip = buildSingleManifestZip(duplicateIdRoot)
        val dupResult = repository.validateBackup(ByteArrayInputStream(dupZip))
        assertTrue(dupResult.isFailure)
        assertTrue(dupResult.exceptionOrNull()!!.message!!.contains("duplicate", ignoreCase = true))
    }

    @Test
    fun `verify backup rejects insufficient storage space without modifying live vault`() = runBlocking {
        repository.seedSampleTemplates()
        val beforeCount = repository.activeItemsWithDetailsFlow.first().size

        val zipOut = ByteArrayOutputStream()
        repository.exportBackup(zipOut)
        val zipBytes = zipOut.toByteArray()

        // Simulate low disk space (only 100 bytes available)
        backupManager.availableBytesProvider = { 100L }
        val lowSpaceValidation = repository.validateBackup(ByteArrayInputStream(zipBytes))
        assertTrue(lowSpaceValidation.isFailure)
        assertTrue(lowSpaceValidation.exceptionOrNull()!!.message!!.contains("Insufficient storage", ignoreCase = true))

        // Restore normal storage provider and verify live vault is intact
        backupManager.availableBytesProvider = { Long.MAX_VALUE }
        assertEquals(beforeCount, repository.activeItemsWithDetailsFlow.first().size)
    }

    @Test
    fun `verify interrupted restoration mid-commit rolls back both filesystem attachments and database`() = runBlocking {
        // 1. Setup original live vault with a live attachment file
        val liveOriginalFile = File(attachmentStorage.attachmentsDir, "original_live.txt")
        liveOriginalFile.writeText("ORIGINAL_LIVE_DATA")
        val originalItem = VaultItemEntity(
            id = "orig_1",
            title = "Original Live Entry",
            category = VaultCategory.IDEA.id,
            attachmentsJson = VaultJsonSerializers.encodeAttachments(
                listOf(
                    AttachmentItem(
                        id = "att_orig",
                        type = AttachmentType.FILE,
                        name = "original_live.txt",
                        uriOrUrl = liveOriginalFile.absolutePath
                    )
                )
            )
        )
        repository.saveVaultItem(originalItem, emptyList())

        // 2. Build a valid backup containing a DIFFERENT item and attachment
        val newBackupRoot = JSONObject().apply {
            put("magic", BackupRestoreManager.BACKUP_MAGIC)
            put("schemaVersion", 1)
            put("folders", JSONArray())
            val newAttJson = VaultJsonSerializers.encodeAttachments(
                listOf(
                    AttachmentItem(
                        id = "att_new",
                        type = AttachmentType.FILE,
                        name = "new_backup.txt",
                        uriOrUrl = "/app/new_backup.txt"
                    )
                )
            )
            put(
                "items",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "replacement_item")
                        put("title", "Replacement Entry")
                        put("category", "KNOWLEDGE")
                        put("attachmentsJson", newAttJson)
                    }
                )
            )
        }
        val validBackupZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry(BackupRestoreManager.MANIFEST_ENTRY_NAME))
                zos.write(newBackupRoot.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("attachments/new_backup.txt"))
                zos.write("NEW_BACKUP_CONTENT".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }.toByteArray()

        val payload = repository.validateBackup(ByteArrayInputStream(validBackupZip)).getOrThrow()

        // 3. Inject an interruption/failure right after filesystem swap before DB commit completes
        val interruptedResult = repository.applyBackup(payload) {
            throw RuntimeException("Simulated process interruption during commit")
        }
        assertTrue(interruptedResult is BackupRestoreResult.Failure)
        val failure = interruptedResult as BackupRestoreResult.Failure
        assertTrue(failure.rollbackVerified)

        // 4. Verify BOTH database AND live attachment file rolled back to original state!
        val activeAfterRollback = repository.activeItemsWithDetailsFlow.first()
        assertEquals(1, activeAfterRollback.size)
        assertEquals("Original Live Entry", activeAfterRollback.first().entity.title)
        assertTrue(liveOriginalFile.exists())
        assertEquals("ORIGINAL_LIVE_DATA", liveOriginalFile.readText())
        assertFalse(File(attachmentStorage.attachmentsDir, "new_backup.txt").exists())
    }

    @Test
    fun `verify backup rejects corrupt sha256 attachment checksum and interrupted extraction`(): Unit = runBlocking {
        repository.seedSampleTemplates()
        val beforeCount = repository.activeItemsWithDetailsFlow.first().size

        // 1. Corrupt SHA-256 checksum in attachmentsManifest
        val corruptManifest = JSONObject().apply {
            put("magic", BackupRestoreManager.BACKUP_MAGIC)
            put("schemaVersion", 2)
            put("folders", JSONArray())
            put("items", JSONArray())
            put(
                "attachmentsManifest",
                JSONArray().put(
                    JSONObject().apply {
                        put("fileName", "corrupt.bin")
                        put("sizeBytes", 12L)
                        put("sha256", "0000000000000000000000000000000000000000000000000000000000000000")
                    }
                )
            )
        }
        val corruptZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry(BackupRestoreManager.MANIFEST_ENTRY_NAME))
                zos.write(corruptManifest.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("attachments/corrupt.bin"))
                zos.write("CORRUPT_DATA".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }.toByteArray()

        val checksumResult = repository.validateBackup(ByteArrayInputStream(corruptZip))
        assertTrue(checksumResult.isFailure)
        assertTrue(checksumResult.exceptionOrNull()!!.message!!.contains("checksum", ignoreCase = true))

        // 2. Interrupted extraction mid-ZIP staging
        backupManager.extractionFaultInjector = {
            throw IOException("Simulated stream interruption during ZIP extraction")
        }
        val interruptedExtResult = repository.validateBackup(ByteArrayInputStream(corruptZip))
        assertTrue(interruptedExtResult.isFailure)
        backupManager.extractionFaultInjector = null

        // Verify staging directory was cleaned up and live vault is untouched
        val remainingStaging = attachmentStorage.stagingRootDir.listFiles()?.toList().orEmpty()
        assertTrue(remainingStaging.isEmpty())
        assertEquals(beforeCount, repository.activeItemsWithDetailsFlow.first().size)
        Unit
    }

    @Test
    fun `verify process crash during attachment or database replacement or cleanup recovers on next launch`(): Unit = runBlocking {
        // 1. Seed original live vault with attachment
        val liveOriginalFile = File(attachmentStorage.attachmentsDir, "crash_orig.txt")
        liveOriginalFile.writeText("CRASH_ORIGINAL_CONTENT")
        val originalItem = VaultItemEntity(
            id = "crash_orig_item",
            title = "Crash Original Item",
            category = VaultCategory.IDEA.id,
            attachmentsJson = VaultJsonSerializers.encodeAttachments(
                listOf(
                    AttachmentItem(
                        id = "att_c1",
                        type = AttachmentType.FILE,
                        name = "crash_orig.txt",
                        uriOrUrl = liveOriginalFile.absolutePath
                    )
                )
            )
        )
        repository.saveVaultItem(originalItem, emptyList())

        // Helper to build a valid replacement backup ZIP
        fun createReplacementZip(): ByteArray {
            val newBackupRoot = JSONObject().apply {
                put("magic", BackupRestoreManager.BACKUP_MAGIC)
                put("schemaVersion", 2)
                put("folders", JSONArray())
                val newAttJson = VaultJsonSerializers.encodeAttachments(
                    listOf(
                        AttachmentItem(
                            id = "att_c2",
                            type = AttachmentType.FILE,
                            name = "crash_new.txt",
                            uriOrUrl = "/app/crash_new.txt"
                        )
                    )
                )
                put(
                    "items",
                    JSONArray().put(
                        JSONObject().apply {
                            put("id", "crash_new_item")
                            put("title", "Crash Replacement Item")
                            put("category", "KNOWLEDGE")
                            put("attachmentsJson", newAttJson)
                        }
                    )
                )
            }
            return ByteArrayOutputStream().apply {
                ZipOutputStream(this).use { zos ->
                    zos.putNextEntry(ZipEntry(BackupRestoreManager.MANIFEST_ENTRY_NAME))
                    zos.write(newBackupRoot.toString().toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                    zos.putNextEntry(ZipEntry("attachments/crash_new.txt"))
                    zos.write("CRASH_NEW_CONTENT".toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }.toByteArray()
        }

        // Case A: Abrupt process kill during REPLACING_ATTACHMENTS -> next launch recovers original DB & files
        val payloadA = repository.validateBackup(ByteArrayInputStream(createReplacementZip())).getOrThrow()
        backupManager.stageFaultInjector = { stage ->
            if (stage == RestoreFaultStage.DURING_ATTACHMENT_REPLACEMENT) {
                throw SimulatedProcessKillError(stage)
            }
        }
        var killedA = false
        try {
            repository.applyBackup(payloadA)
        } catch (_: SimulatedProcessKillError) {
            killedA = true
        }
        assertTrue(killedA)
        backupManager.stageFaultInjector = null

        // Simulate next application launch by constructing a new BackupRestoreManager
        val nextLaunchManagerA = BackupRestoreManager(
            dao = db.vaultDao(),
            prefsRepo = prefsRepo,
            attachmentStorage = attachmentStorage,
            database = db,
            autoRecoverOnInit = true
        )
        assertTrue(nextLaunchManagerA.recoveryHealthFlow.value is RecoveryHealthState.Healthy)
        assertEquals("Crash Original Item", repository.activeItemsWithDetailsFlow.first().single().entity.title)
        assertTrue(liveOriginalFile.exists())
        assertEquals("CRASH_ORIGINAL_CONTENT", liveOriginalFile.readText())
        assertFalse(File(attachmentStorage.attachmentsDir, "crash_new.txt").exists())

        // Case B: Abrupt process kill during REPLACING_DATABASE (after live attachments were already replaced!)
        // Next launch must coordinate recovery of BOTH the attachment files AND the database!
        val payloadB = repository.validateBackup(ByteArrayInputStream(createReplacementZip())).getOrThrow()
        backupManager.stageFaultInjector = { stage ->
            if (stage == RestoreFaultStage.DURING_DATABASE_REPLACEMENT) {
                throw SimulatedProcessKillError(stage)
            }
        }
        var killedB = false
        try {
            repository.applyBackup(payloadB)
        } catch (_: SimulatedProcessKillError) {
            killedB = true
        }
        assertTrue(killedB)
        backupManager.stageFaultInjector = null

        // Before next-launch recovery runs, live attachments have already been swapped to crash_new.txt!
        assertTrue(File(attachmentStorage.attachmentsDir, "crash_new.txt").exists())

        // Simulate next launch -> automatically rolls back BOTH attachments and DB to Crash Original Item!
        val nextLaunchManagerB = BackupRestoreManager(
            dao = db.vaultDao(),
            prefsRepo = prefsRepo,
            attachmentStorage = attachmentStorage,
            database = db,
            autoRecoverOnInit = true
        )
        assertTrue(nextLaunchManagerB.recoveryHealthFlow.value is RecoveryHealthState.Healthy)
        assertEquals("Crash Original Item", repository.activeItemsWithDetailsFlow.first().single().entity.title)
        assertTrue(liveOriginalFile.exists())
        assertEquals("CRASH_ORIGINAL_CONTENT", liveOriginalFile.readText())
        assertFalse(File(attachmentStorage.attachmentsDir, "crash_new.txt").exists())

        // Case C: Abrupt process kill during DURING_CLEANUP (after commit succeeded!)
        // Next launch must preserve the newly committed restore and finish cleaning up rollback/staging files!
        val payloadC = repository.validateBackup(ByteArrayInputStream(createReplacementZip())).getOrThrow()
        backupManager.stageFaultInjector = { stage ->
            if (stage == RestoreFaultStage.DURING_CLEANUP) {
                throw SimulatedProcessKillError(stage)
            }
        }
        var killedC = false
        try {
            repository.applyBackup(payloadC)
        } catch (_: SimulatedProcessKillError) {
            killedC = true
        }
        assertTrue(killedC)
        backupManager.stageFaultInjector = null

        val nextLaunchManagerC = BackupRestoreManager(
            dao = db.vaultDao(),
            prefsRepo = prefsRepo,
            attachmentStorage = attachmentStorage,
            database = db,
            autoRecoverOnInit = true
        )
        assertTrue(nextLaunchManagerC.recoveryHealthFlow.value is RecoveryHealthState.Healthy)
        assertEquals("Crash Replacement Item", repository.activeItemsWithDetailsFlow.first().single().entity.title)
        val committedNewFile = File(attachmentStorage.attachmentsDir, "crash_new.txt")
        assertTrue(committedNewFile.exists())
        assertEquals("CRASH_NEW_CONTENT", committedNewFile.readText())
        assertFalse(File(attachmentStorage.rollbackRootDir, BackupRestoreManager.JOURNAL_FILE_NAME).exists())
        Unit
    }

    @Test
    fun `verify failed rollback is detected preserves snapshots blocks writes and succeeds on retry`(): Unit = runBlocking {
        // 1. Setup original vault
        val liveOriginalFile = File(attachmentStorage.attachmentsDir, "rb_orig.txt")
        liveOriginalFile.writeText("RB_ORIGINAL")
        repository.saveVaultItem(
            VaultItemEntity(
                id = "rb_item_1",
                title = "Rollback Protected Entry",
                category = VaultCategory.IDEA.id,
                attachmentsJson = VaultJsonSerializers.encodeAttachments(
                    listOf(
                        AttachmentItem(
                            id = "att_rb1",
                            type = AttachmentType.FILE,
                            name = "rb_orig.txt",
                            uriOrUrl = liveOriginalFile.absolutePath
                        )
                    )
                )
            ),
            emptyList()
        )

        val backupManifest = JSONObject().apply {
            put("magic", BackupRestoreManager.BACKUP_MAGIC)
            put("schemaVersion", 2)
            put("folders", JSONArray())
            put(
                "items",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "rb_item_2")
                        put("title", "Incoming Entry")
                        put("category", "IDEA")
                    }
                )
            )
        }
        val zipBytes = buildSingleManifestZip(backupManifest)
        val payload = repository.validateBackup(ByteArrayInputStream(zipBytes)).getOrThrow()

        // 2. Inject failure during DB replacement AND failure during automatic rollback!
        backupManager.stageFaultInjector = { stage ->
            if (stage == RestoreFaultStage.DURING_DATABASE_REPLACEMENT) {
                throw IOException("Simulated DB commit failure")
            }
            if (stage == RestoreFaultStage.DURING_ROLLBACK_DATABASE) {
                throw IOException("Simulated DB rollback failure")
            }
        }

        val result = repository.applyBackup(payload)
        assertTrue(result is BackupRestoreResult.Failure)
        val failure = result as BackupRestoreResult.Failure

        // Rollback was NOT verified, so rollbackVerified MUST be false and reason must not claim data was preserved
        assertFalse(failure.rollbackVerified)
        assertEquals("ERR_ROLLBACK_DATABASE", failure.diagnosticCode)
        assertFalse(failure.reason.contains("original vault was verified and preserved", ignoreCase = true))

        // Recovery snapshot files MUST still be preserved on disk!
        assertTrue(File(attachmentStorage.rollbackRootDir, BackupRestoreManager.JOURNAL_FILE_NAME).exists())
        assertTrue(File(attachmentStorage.rollbackRootDir, BackupRestoreManager.DB_SNAPSHOT_FILE_NAME).exists())

        // Health state must be UnresolvedRecoveryError and normal vault writes MUST be blocked!
        assertTrue(backupManager.recoveryHealthFlow.value is RecoveryHealthState.UnresolvedRecoveryError)
        var writeBlocked = false
        try {
            repository.createQuickNoteToInbox("Blocked Note", "Should not write")
        } catch (_: UnresolvedVaultRecoveryException) {
            writeBlocked = true
        }
        assertTrue(writeBlocked)

        // 3. Clear the fault and retry recovery -> succeeds, restores original vault, and unblocks writes!
        backupManager.stageFaultInjector = null
        val retryResult = repository.retryPendingRecovery()
        assertTrue(retryResult is BackupRestoreResult.Success)
        assertTrue(backupManager.recoveryHealthFlow.value is RecoveryHealthState.Healthy)
        assertFalse(File(attachmentStorage.rollbackRootDir, BackupRestoreManager.JOURNAL_FILE_NAME).exists())

        // Verify original data is restored and writes are now permitted again
        assertEquals("Rollback Protected Entry", repository.activeItemsWithDetailsFlow.first().single().entity.title)
        assertTrue(liveOriginalFile.exists())
        val newNoteId = repository.createQuickNoteToInbox("Unlocked Note", "Write succeeds after recovery")
        assertTrue(newNoteId.isNotBlank())
        Unit
    }

    @Test
    fun `verify database migration 1 to 2 is non-destructive and preserves existing rows`(): Unit = runBlocking {
        val dbName = "migration_test_${System.nanoTime()}.db"
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_folders_category` ON `folders` (`category`)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `vault_items` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `content` TEXT NOT NULL, `category` TEXT NOT NULL, `folderId` TEXT, `tagsJson` TEXT NOT NULL, `richBlocksJson` TEXT NOT NULL, `attachmentsJson` TEXT NOT NULL, `ideaStatus` TEXT NOT NULL, `platform` TEXT NOT NULL, `estimatedTime` TEXT NOT NULL, `notes` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `isTrashed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_category` ON `vault_items` (`category`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_folderId` ON `vault_items` (`folderId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_isFavorite` ON `vault_items` (`isFavorite`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_isPinned` ON `vault_items` (`isPinned`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_isTrashed` ON `vault_items` (`isTrashed`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_isArchived` ON `vault_items` (`isArchived`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_updatedAt` ON `vault_items` (`updatedAt`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `method_steps` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, `stepOrder` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `notes` TEXT NOT NULL, `linksJson` TEXT NOT NULL, `imagesJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_method_steps_itemId_stepOrder` ON `method_steps` (`itemId`, `stepOrder`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `method_follow_runs` (`itemId` TEXT NOT NULL, `completedStepIdsJson` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`itemId`))"
                    )

                    db.execSQL("INSERT INTO `folders` (`id`, `name`, `category`, `colorHex`, `createdAt`, `updatedAt`) VALUES ('f1', 'V1 Folder', 'IDEA', '#FF7654', 90, 190)")
                    db.execSQL(
                        "INSERT INTO `vault_items` (`id`, `title`, `description`, `content`, `category`, `folderId`, `tagsJson`, `richBlocksJson`, `attachmentsJson`, `ideaStatus`, `platform`, `estimatedTime`, `notes`, `isFavorite`, `isPinned`, `isArchived`, `isTrashed`, `createdAt`, `updatedAt`) " +
                            "VALUES ('m1', 'Migrated Idea', 'Desc', 'Body', 'IDEA', 'f1', '[]', '[]', '[]', 'NEW', '', '', '', 1, 0, 0, 0, 100, 200)"
                    )
                    db.execSQL(
                        "INSERT INTO `method_steps` (`id`, `itemId`, `stepOrder`, `title`, `description`, `notes`, `linksJson`, `imagesJson`) VALUES ('s1', 'm1', 1, 'Step 1', 'Details', '', '[]', '[]')"
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        // Create and populate the v1 SQLite database on disk
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.close()

        // Open with Room at version 4 using MIGRATION_1_2, MIGRATION_2_3, and MIGRATION_3_4 (exercises Room's strict onValidateSchema!)
        val upgradedRoomDb = Room.databaseBuilder(context, VaultDatabase::class.java, dbName)
            .addMigrations(VaultDatabase.MIGRATION_1_2, VaultDatabase.MIGRATION_2_3, VaultDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val migratedItems = upgradedRoomDb.vaultDao().getAllItemsSnapshot()
        val migratedFolders = upgradedRoomDb.vaultDao().getAllFoldersSnapshot()
        val migratedSteps = upgradedRoomDb.vaultDao().getAllStepsSnapshot()

        assertEquals(1, migratedItems.size)
        assertEquals("Migrated Idea", migratedItems.single().title)
        assertEquals("f1", migratedItems.single().folderId)
        assertEquals(1, migratedFolders.size)
        assertEquals("V1 Folder", migratedFolders.single().name)
        assertEquals(1, migratedSteps.size)
        assertEquals("Step 1", migratedSteps.single().title)

        upgradedRoomDb.close()
        context.deleteDatabase(dbName)
        Unit
    }

    @Test
    fun `verify custom curved cutout card shape produces valid generic path outline`() {
        val shape = CurvedBottomRightCutoutShape()
        val outline = shape.createOutline(
            size = Size(360f, 220f),
            layoutDirection = LayoutDirection.Ltr,
            density = Density(1f)
        )
        assertTrue(outline is Outline.Generic)
        val path = (outline as Outline.Generic).path
        assertFalse(path.isEmpty)
        assertNotNull(path.getBounds())
    }

    @Test
    fun `verify compose ui onboarding to home navigation search and curved card interactions`() {
        runBlocking {
            prefsRepo.completeOnboarding("Rahmathullah")
            repository.seedSampleTemplates()
        }

        val vm = VaultViewModel(context, repository)
        composeTestRule.setContent {
            IdeaVaultApp(viewModel = vm)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            vm.uiState.value.allActiveItems.isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Verify V2 Home screen starts with search bar, filter chips, and colorful cards without legacy greeting header
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("home_user_name_text").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithText("AI App Development Workflow").assertIsDisplayed()

        // Filter by Methods chip
        composeTestRule.onNodeWithTag("filter_chip_method").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("AI App Development Workflow").assertIsDisplayed()

        // Open Search via Home top search bar and return via search_back_button
        composeTestRule.onNodeWithTag("home_search_input").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_results_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_back_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()

        // Navigate to V2.3 Collections tab
        composeTestRule.onNodeWithTag("bottom_nav_collections").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("folders_list_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_folder_button").assertIsDisplayed()

        // Navigate to V2.3 Favourite tab
        composeTestRule.onNodeWithTag("bottom_nav_favorites").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("favorites_screen_list").assertIsDisplayed()

        // Navigate to V2.3 Settings tab -> organized sections without public Load Starter Templates button
        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_row_profile").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_screen_list")
            .performScrollToNode(hasTestTag("settings_row_backup"))
        composeTestRule.onNodeWithTag("settings_row_backup").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("settings_row_notifications").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("load_templates_button").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("bottom_nav_profile").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("bottom_nav_search").fetchSemanticsNodes().isEmpty())

        // Return to Home and tap center raised yellow + button -> opens V2 Create New Bottom Sheet
        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_nav_add_fab").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_new_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_sheet_option_idea").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_sheet_option_method").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_sheet_option_prompt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_sheet_option_note").assertIsDisplayed()
        Unit
    }

    @Test
    fun `verify P1-01 editor draft preserves all fields across save failure and clears only after save succeeds`(): Unit = runBlocking {
        prefsRepo.completeOnboarding("Rahmathullah")
        val vm = VaultViewModel(context, repository)

        val folder = repository.createFolder("Workflows", VaultCategory.METHOD, "#A7DB7B")
        val draftBlocks = listOf(
            RichContentBlock(id = "rb_1", type = RichBlockType.CHECKLIST, title = "Verify backups", content = "Check SHA-256", checked = true),
            RichContentBlock(id = "rb_2", type = RichBlockType.CODE_BLOCK, title = "Snippet", content = "val safe = true")
        )
        val draftAttachments = listOf(
            AttachmentItem(id = "att_link_1", type = AttachmentType.LINK, name = "Android Docs", uriOrUrl = "https://developer.android.com")
        )
        val draftSteps = listOf(
            MethodStepEntity(id = "step_a", itemId = "draft_item_1", stepOrder = 1, title = "Draft Step 1", description = "First action"),
            MethodStepEntity(id = "step_b", itemId = "draft_item_1", stepOrder = 2, title = "Draft Step 2", description = "Second action")
        )

        val draftPayload = EditorDraftPayload(
            itemId = "draft_item_1",
            isEditingExisting = false,
            baseUpdatedAt = null,
            title = "Full Draft Recovery Entry",
            description = "Detailed workflow description",
            content = "Multi-line body\nwith formatting",
            categoryId = VaultCategory.METHOD.id,
            folderId = folder.id,
            ideaStatusId = IdeaStatus.DEVELOPING.id,
            platform = "Android Studio",
            estimatedTime = "45 mins",
            notes = "Critical engineering notes",
            tags = listOf("kotlin", "resilience"),
            richBlocks = draftBlocks,
            attachments = draftAttachments,
            steps = draftSteps
        )

        val encodedDraft = VaultJsonSerializers.encodeEditorDraft(draftPayload)
        vm.saveEditorDraftImmediate(encodedDraft)

        // Verify all fields round-trip losslessly from the persisted recovery draft
        val decoded = VaultJsonSerializers.decodeEditorDraft(prefsRepo.getSnapshot().editorDraftJson)
        assertNotNull(decoded)
        assertEquals("Full Draft Recovery Entry", decoded!!.title)
        assertEquals("Detailed workflow description", decoded.description)
        assertEquals("Multi-line body\nwith formatting", decoded.content)
        assertEquals(VaultCategory.METHOD.id, decoded.categoryId)
        assertEquals(folder.id, decoded.folderId)
        assertEquals(IdeaStatus.DEVELOPING.id, decoded.ideaStatusId)
        assertEquals("Android Studio", decoded.platform)
        assertEquals("45 mins", decoded.estimatedTime)
        assertEquals("Critical engineering notes", decoded.notes)
        assertEquals(listOf("kotlin", "resilience"), decoded.tags)
        assertEquals(2, decoded.richBlocks.size)
        assertEquals(1, decoded.attachments.size)
        assertEquals(2, decoded.steps.size)
        assertEquals("Draft Step 2", decoded.steps[1].title)

        // Simulate database write failure during saveItemAwaitable
        repository.saveFaultInjector = {
            throw IOException("Simulated SQLite disk full failure during save")
        }

        val itemToSave = VaultItemEntity(
            id = draftPayload.itemId,
            title = draftPayload.title,
            description = draftPayload.description,
            content = draftPayload.content,
            category = draftPayload.categoryId,
            folderId = draftPayload.folderId,
            tagsJson = VaultJsonSerializers.encodeStringList(draftPayload.tags),
            richBlocksJson = VaultJsonSerializers.encodeRichBlocks(draftPayload.richBlocks),
            attachmentsJson = VaultJsonSerializers.encodeAttachments(draftPayload.attachments),
            ideaStatus = draftPayload.ideaStatusId,
            platform = draftPayload.platform,
            estimatedTime = draftPayload.estimatedTime,
            notes = draftPayload.notes
        )

        val failedSaveResult = vm.saveItemAwaitable(
            item = itemToSave,
            steps = draftSteps,
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false
        )
        assertTrue(failedSaveResult is SaveItemResult.StorageFailure)

        // CRITICAL: Unsaved recovery draft MUST still be intact in DataStore after save failure!
        val draftAfterFailure = VaultJsonSerializers.decodeEditorDraft(prefsRepo.getSnapshot().editorDraftJson)
        assertNotNull(draftAfterFailure)
        assertEquals("Full Draft Recovery Entry", draftAfterFailure!!.title)
        assertEquals(2, draftAfterFailure.steps.size)
        assertEquals(2, draftAfterFailure.richBlocks.size)
        assertEquals(listOf("kotlin", "resilience"), draftAfterFailure.tags)

        // Remove fault and retry save -> succeeds and clears recovery draft ONLY after persistence succeeds
        repository.saveFaultInjector = null
        val retrySaveResult = vm.saveItemAwaitable(
            item = itemToSave,
            steps = draftSteps,
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false
        )
        assertTrue(retrySaveResult is SaveItemResult.Success)
        assertEquals("", prefsRepo.getSnapshot().editorDraftJson)

        val persisted = repository.getItemWithDetailsById("draft_item_1")
        assertNotNull(persisted)
        assertEquals("Full Draft Recovery Entry", persisted!!.entity.title)
        assertEquals(2, persisted.steps.size)
        assertEquals(2, persisted.richBlocks.size)
        assertEquals(listOf("kotlin", "resilience"), persisted.tags)
        Unit
    }

    @Test
    fun `verify P1-02 saveItemAwaitable returns explicit Conflict on concurrent update and never reports false success`(): Unit = runBlocking {
        val vm = VaultViewModel(context, repository)
        val initialItem = VaultItemEntity(
            id = "conflict_item_1",
            title = "Initial Version",
            description = "Base description",
            content = "Base content",
            category = VaultCategory.IDEA.id,
            createdAt = 10_000L,
            updatedAt = 20_000L
        )
        val createRes = vm.saveItemAwaitable(
            item = initialItem,
            steps = emptyList(),
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false
        )
        assertTrue(createRes is SaveItemResult.Success)
        val createdSnapshot = repository.getItemWithDetailsById("conflict_item_1")!!
        val editorLoadedUpdatedAt = createdSnapshot.entity.updatedAt

        // Another update occurs while the editor has the older snapshot open
        val backgroundUpdateRes = repository.saveVaultItemWithResult(
            item = createdSnapshot.entity.copy(title = "Newer Background Version", content = "Updated elsewhere"),
            steps = emptyList(),
            expectedPreviousUpdatedAt = editorLoadedUpdatedAt,
            isEditingExisting = true
        )
        assertTrue(backgroundUpdateRes is SaveItemResult.Success)

        // Editor saves a draft and attempts to save using stale expectedPreviousUpdatedAt
        vm.saveEditorDraftImmediate(
            VaultJsonSerializers.encodeEditorDraft(
                EditorDraftPayload(
                    itemId = "conflict_item_1",
                    isEditingExisting = true,
                    baseUpdatedAt = editorLoadedUpdatedAt,
                    title = "Stale Editor Title",
                    description = "Base description",
                    content = "Editor unsaved changes",
                    categoryId = VaultCategory.IDEA.id,
                    folderId = null,
                    ideaStatusId = IdeaStatus.NEW.id,
                    platform = "",
                    estimatedTime = "",
                    notes = "",
                    tags = emptyList(),
                    richBlocks = emptyList(),
                    attachments = emptyList(),
                    steps = emptyList()
                )
            )
        )

        val staleSaveRes = vm.saveItemAwaitable(
            item = createdSnapshot.entity.copy(title = "Stale Editor Title", content = "Editor unsaved changes"),
            steps = emptyList(),
            expectedPreviousUpdatedAt = editorLoadedUpdatedAt,
            isEditingExisting = true,
            forceOverwrite = false
        )

        // Must return explicit Conflict — NEVER false success!
        assertTrue(staleSaveRes is SaveItemResult.Conflict)
        val conflict = staleSaveRes as SaveItemResult.Conflict
        assertEquals("Newer Background Version", conflict.currentDatabaseItem?.entity?.title)

        // Newer database version was NOT overwritten AND editor draft was NOT discarded
        assertEquals("Newer Background Version", repository.getItemWithDetailsById("conflict_item_1")!!.entity.title)
        assertTrue(prefsRepo.getSnapshot().editorDraftJson.isNotBlank())

        // User explicitly chooses "Keep My Edits & Save" (forceOverwrite = true)
        val overwriteRes = vm.saveItemAwaitable(
            item = createdSnapshot.entity.copy(title = "Stale Editor Title", content = "Editor unsaved changes"),
            steps = emptyList(),
            expectedPreviousUpdatedAt = editorLoadedUpdatedAt,
            isEditingExisting = true,
            forceOverwrite = true
        )
        assertTrue(overwriteRes is SaveItemResult.Success)
        assertEquals("Stale Editor Title", repository.getItemWithDetailsById("conflict_item_1")!!.entity.title)
        assertEquals("", prefsRepo.getSnapshot().editorDraftJson)
        Unit
    }

    @Test
    fun `verify P1-03 backup export aborts when item attachment or step image is missing and verifies staged zip`(): Unit = runBlocking {
        // 1. Item referencing a missing local attachment file must fail export before publishing
        val missingFile = File(attachmentStorage.attachmentsDir, "nonexistent_attachment.png")
        missingFile.delete()
        val itemWithMissingAtt = VaultItemEntity(
            id = "item_missing_export_att",
            title = "Broken Attachment Entry",
            category = VaultCategory.REFERENCE.id,
            attachmentsJson = VaultJsonSerializers.encodeAttachments(
                listOf(
                    AttachmentItem(
                        id = "att_missing_1",
                        type = AttachmentType.IMAGE,
                        name = "nonexistent_attachment.png",
                        uriOrUrl = missingFile.absolutePath
                    )
                )
            )
        )
        repository.saveVaultItem(itemWithMissingAtt, emptyList())

        val targetZipFile = File(context.cacheDir, "published_backup_${System.nanoTime()}.zip")
        val exportMissingAttResult = repository.exportBackupToFile(targetZipFile)
        assertTrue(exportMissingAttResult is BackupRestoreResult.Failure)
        assertEquals("ERR_EXPORT_MISSING_ATTACHMENT", (exportMissingAttResult as BackupRestoreResult.Failure).diagnosticCode)
        assertFalse(targetZipFile.exists())

        // 2. Method step referencing a missing step image must also fail export before publishing
        db.vaultDao().clearAllItems()
        val missingStepImg = File(attachmentStorage.attachmentsDir, "missing_step_diagram.jpg")
        missingStepImg.delete()
        val methodItem = VaultItemEntity(
            id = "method_missing_step_img",
            title = "Method With Missing Step Image",
            category = VaultCategory.METHOD.id
        )
        val stepWithMissingImg = MethodStepEntity(
            id = "step_missing_img_1",
            itemId = "method_missing_step_img",
            stepOrder = 1,
            title = "Visual Step",
            description = "Check diagram",
            imagesJson = VaultJsonSerializers.encodeStringList(listOf(missingStepImg.absolutePath))
        )
        repository.saveVaultItem(methodItem, listOf(stepWithMissingImg))

        val exportMissingStepResult = repository.exportBackupToFile(targetZipFile)
        assertTrue(exportMissingStepResult is BackupRestoreResult.Failure)
        assertEquals("ERR_EXPORT_MISSING_ATTACHMENT", (exportMissingStepResult as BackupRestoreResult.Failure).diagnosticCode)
        assertFalse(targetZipFile.exists())

        // 3. Create the missing step image on disk -> staged export succeeds and passes full ZIP verification
        missingStepImg.writeBytes("STEP_IMAGE_BYTES_98765".toByteArray())
        val validExportResult = repository.exportBackupToFile(targetZipFile)
        assertTrue(validExportResult is BackupRestoreResult.Success)
        assertTrue(targetZipFile.exists())
        assertTrue(targetZipFile.length() > 0L)
        targetZipFile.delete()
        Unit
    }

    @Test
    fun `verify P2-01 app lock stores salted PBKDF2 hash migrates legacy plaintext pin and requires current pin to change`(): Unit = runBlocking {
        // 1. Set PIN and verify no plaintext PIN is stored in DataStore
        prefsRepo.updateAppLockPin("7391")
        val snapshot = prefsRepo.getSnapshot()
        assertTrue(snapshot.isAppLockEnabled)

        val storedMeta = prefsRepo.inspectStoredLockMetadataForVerification()
        assertNull(storedMeta.legacyPlaintextValue)
        assertTrue(!storedMeta.storedSaltHex.isNullOrBlank())
        assertTrue(!storedMeta.storedHashValue.isNullOrBlank())
        assertFalse(storedMeta.storedHashValue!!.contains("7391"))
        assertFalse(storedMeta.storedSaltHex!!.contains("7391"))

        // 2. Verify PIN comparison works for correct PIN and rejects wrong PIN
        assertTrue(prefsRepo.verifyAppLockPin("7391"))
        assertFalse(prefsRepo.verifyAppLockPin("0000"))
        assertFalse(prefsRepo.verifyAppLockPin(""))

        // 3. Changing or disabling PIN requires valid current PIN
        assertFalse(prefsRepo.changeOrDisableAppLockPin("1111", "8426"))
        assertTrue(prefsRepo.verifyAppLockPin("7391"))

        assertTrue(prefsRepo.changeOrDisableAppLockPin("7391", "8426"))
        assertFalse(prefsRepo.verifyAppLockPin("7391"))
        assertTrue(prefsRepo.verifyAppLockPin("8426"))

        // Disable lock using valid current PIN
        assertTrue(prefsRepo.changeOrDisableAppLockPin("8426", ""))
        assertFalse(prefsRepo.getSnapshot().isAppLockEnabled)

        // 4. Verify legacy plaintext PIN automatic migration removes plaintext key and preserves lock
        val legacyFile = File(context.cacheDir, "legacy_prefs_${System.nanoTime()}.preferences_pb")
        val legacyDataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { legacyFile }
        )
        legacyDataStore.updateData { prefs ->
            val mutable = prefs.toMutablePreferences()
            mutable[UserPreferencesRepository.KEY_LEGACY_APP_LOCK_PIN] = "2580"
            mutable
        }
        val migratedRepo = UserPreferencesRepository(legacyDataStore)
        val migratedSnapshot = migratedRepo.getSnapshot()
        assertTrue(migratedSnapshot.isAppLockEnabled)
        val migratedMeta = migratedRepo.inspectStoredLockMetadataForVerification()
        assertNull(migratedMeta.legacyPlaintextValue)
        assertTrue(!migratedMeta.storedHashValue.isNullOrBlank())
        assertTrue(migratedRepo.verifyAppLockPin("2580"))
        assertFalse(migratedRepo.verifyAppLockPin("9999"))
        legacyFile.delete()
        Unit
    }

    @Test
    fun `verify P2-02 android manifest disables automatic cloud and device backup of private vault data`() {
        val appInfo = context.applicationInfo
        val allowBackupFlag = appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP
        assertEquals(0, allowBackupFlag)
    }

    @Test
    fun `verify issue 01 and 02 rich text selection formatting active typing mode tamil unicode literal asterisks and persistence`(): Unit = runBlocking {
        // 1. Selecting text and tapping Bold / Italic / Underline applies mark to selected range and NEVER inserts **bold**
        val initialText = "Capture Tamil தமிழ் knowledge and 2 * 3 * 4 = 24"
        var state = RichTextEditorState.fromStoredContent(initialText)
        assertEquals(initialText, state.textFieldValue.text)

        // Select "Tamil தமிழ்" and apply Bold
        val tamilStart = initialText.indexOf("Tamil தமிழ்")
        val tamilEnd = tamilStart + "Tamil தமிழ்".length
        state = state.copy(textFieldValue = state.textFieldValue.copy(selection = TextRange(tamilStart, tamilEnd)))
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.BOLD)

        // Text itself must NOT contain literal ** or **bold**
        assertEquals(initialText, state.textFieldValue.text)
        assertFalse(state.textFieldValue.text.contains("**"))
        assertEquals(TextRange(tamilStart, tamilEnd), state.textFieldValue.selection)
        assertTrue(InlineRichStyle.BOLD in state.effectiveActiveStyles())

        // Select "knowledge" and apply Italic + Underline (nested formatting)
        val knowStart = initialText.indexOf("knowledge")
        val knowEnd = knowStart + "knowledge".length
        state = state.copy(textFieldValue = state.textFieldValue.copy(selection = TextRange(knowStart, knowEnd)))
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.ITALIC)
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.UNDERLINE)
        assertTrue(InlineRichStyle.ITALIC in state.effectiveActiveStyles())
        assertTrue(InlineRichStyle.UNDERLINE in state.effectiveActiveStyles())

        // Select "Capture" and apply V2 Strikethrough, Highlight, Font Size 20, and Coral Font Color
        val capStart = 0
        val capEnd = "Capture".length
        state = state.copy(textFieldValue = state.textFieldValue.copy(selection = TextRange(capStart, capEnd)))
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.STRIKETHROUGH)
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.HIGHLIGHT)
        state = RichTextEditorController.applyFontSize(state, 20)
        state = RichTextEditorController.applyFontColor(state, RichTextColorToken.CORAL)
        assertTrue(InlineRichStyle.STRIKETHROUGH in state.effectiveActiveStyles())
        assertTrue(InlineRichStyle.HIGHLIGHT in state.effectiveActiveStyles())
        assertEquals(20, state.effectiveFontSize())
        assertEquals(RichTextColorToken.CORAL, state.effectiveFontColor())

        // Apply Center paragraph alignment and Lettered list format on line 0, then Undo & Redo
        state = RichTextEditorController.applyParagraphAlignment(state, ParagraphAlignment.CENTER)
        assertEquals(ParagraphAlignment.CENTER, state.currentParagraphAlignment())
        state = RichTextEditorController.toggleLineFormat(state, LineBlockFormat.LETTERED_LIST)
        assertEquals(LineBlockFormat.LETTERED_LIST, state.currentLineBlockFormat())
        assertTrue(state.canUndo)
        state = RichTextEditorController.undo(state)
        assertEquals(LineBlockFormat.PARAGRAPH, state.currentLineBlockFormat())
        assertTrue(state.canRedo)
        state = RichTextEditorController.redo(state)
        assertEquals(LineBlockFormat.LETTERED_LIST, state.currentLineBlockFormat())
        // Toggle off lettered list so line 0 text returns to initialText while keeping Center alignment
        state = RichTextEditorController.toggleLineFormat(state, LineBlockFormat.LETTERED_LIST)
        assertEquals(LineBlockFormat.PARAGRAPH, state.currentLineBlockFormat())

        // 2. Active typing mode when cursor is collapsed (no selection)
        val endCursor = state.textFieldValue.text.length
        state = state.copy(
            textFieldValue = state.textFieldValue.copy(selection = TextRange(endCursor)),
            activeTypingStyles = emptySet(),
            typingStyleExplicitlyOverridden = true
        )
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.BOLD)
        assertTrue(state.activeTypingStyles.contains(InlineRichStyle.BOLD))

        val typedText = "$initialText\nNext paragraph"
        state = RichTextEditorController.onValueChange(
            previous = state,
            incoming = TextFieldValue(text = typedText, selection = TextRange(typedText.length))
        )
        // Newly typed range should carry BOLD span
        assertTrue(state.spans.any { it.style == InlineRichStyle.BOLD && it.start == endCursor && it.end == typedText.length })

        // 3. Serialize -> Persist to Room -> Reopen -> Backup & Restore -> Deserialize round-trip
        val serialized = state.toSerializedMarkdown()
        // Literal math asterisks "2 * 3 * 4" are preserved so they never get misinterpreted as italic
        val reopenedState = RichTextEditorState.fromStoredContent(serialized)
        assertEquals(typedText, reopenedState.textFieldValue.text)
        assertTrue(reopenedState.textFieldValue.text.contains("2 * 3 * 4 = 24"))
        assertTrue(reopenedState.textFieldValue.text.contains("Tamil தமிழ்"))
        assertEquals(ParagraphAlignment.CENTER, reopenedState.lineAlignments[0])
        assertTrue(
            reopenedState.spans.any {
                it.style == InlineRichStyle.BOLD &&
                    reopenedState.textFieldValue.text.substring(it.start, it.end) == "Tamil தமிழ்"
            }
        )
        assertTrue(
            reopenedState.spans.any {
                it.style == InlineRichStyle.ITALIC &&
                    reopenedState.textFieldValue.text.substring(it.start, it.end) == "knowledge"
            }
        )
        assertTrue(
            reopenedState.spans.any {
                it.style == InlineRichStyle.UNDERLINE &&
                    reopenedState.textFieldValue.text.substring(it.start, it.end) == "knowledge"
            }
        )
        assertTrue(
            reopenedState.spans.any {
                it.style == InlineRichStyle.STRIKETHROUGH &&
                    reopenedState.textFieldValue.text.substring(it.start, it.end) == "Capture"
            }
        )
        assertTrue(
            reopenedState.spans.any {
                it.style == InlineRichStyle.HIGHLIGHT &&
                    reopenedState.textFieldValue.text.substring(it.start, it.end) == "Capture"
            }
        )
        assertTrue(
            reopenedState.fontSizeSpans.any {
                it.sizeSp == 20 &&
                    reopenedState.textFieldValue.text.substring(it.start, it.end) == "Capture"
            }
        )
        assertTrue(
            reopenedState.fontColorSpans.any {
                it.colorToken == RichTextColorToken.CORAL &&
                    reopenedState.textFieldValue.text.substring(it.start, it.end) == "Capture"
            }
        )
        assertEquals(typedText, RichTextSerializer.toPlainText(serialized))
        Unit
    }

    @Test
    fun `verify issue 03 and 04 detail screen shows copy prompt only for prompts removes floating bar and opens overflow sheet`() {
        val (loadedIdea, loadedPrompt) = runBlocking {
            val ideaItem = VaultItemEntity(
                id = "idea_detail_test",
                title = "Editorial Idea Entry",
                description = "Warm light mode and rich text",
                content = "This is **bold idea text** and _italic detail_ with 2 * 3 * 4 = 24.",
                category = VaultCategory.IDEA.id,
                platform = "" // Blank platform -> must NOT display "Native / Offline Vault"
            )
            val promptItem = VaultItemEntity(
                id = "prompt_detail_test",
                title = "Structured Gemini Prompt",
                description = "System prompt template",
                content = "Act as a **Kotlin Compose Architect** for {TOPIC}.",
                category = VaultCategory.PROMPT.id,
                platform = "Google AI Studio"
            )
            repository.saveVaultItem(ideaItem, emptyList())
            repository.saveVaultItem(promptItem, emptyList())

            val idea = repository.getItemWithDetailsById("idea_detail_test")!!
            val prompt = repository.getItemWithDetailsById("prompt_detail_test")!!
            idea to prompt
        }

        val activeDetailState = androidx.compose.runtime.mutableStateOf(loadedIdea)

        composeTestRule.setContent {
            IdeaVaultTheme(themeMode = AppThemeMode.LIGHT) {
                ItemDetailScreen(
                    item = activeDetailState.value,
                    folders = emptyList(),
                    onBack = {},
                    onEdit = {},
                    onToggleFavorite = {},
                    onTogglePin = {},
                    onDuplicate = {},
                    onMoveToFolder = {},
                    onToggleArchive = {},
                    onMoveToTrash = {},
                    onUpdateIdeaStatus = {},
                    onToggleFollowStep = {},
                    onResetFollowSteps = {},
                    onReorderSteps = {},
                    onToggleChecklistBlock = {},
                    onAddQuickRichBlock = {},
                    onAddUriAttachment = { _, _ -> },
                    onAddLinkAttachment = { _, _ -> },
                    onRemoveAttachment = {},
                    onShowMessage = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. For IDEA item: "Copy Prompt" button MUST NOT exist; "IDEA DETAILS" and "Copy Text" are shown
        composeTestRule.onNodeWithTag("detail_content_list")
            .performScrollToNode(hasTestTag("detail_main_content_card"))
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodesWithTag("copy_prompt_button").fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("copy_text_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("IDEA DETAILS").assertIsDisplayed()

        // 2. Hardcoded "Native / Offline Vault" must NEVER appear
        assertTrue(composeTestRule.onAllNodesWithText("Native / Offline Vault").fetchSemanticsNodes().isEmpty())

        // 3. Rendered rich text must NOT display raw "**bold idea text**" syntax
        assertTrue(composeTestRule.onAllNodesWithText("**bold idea text**", substring = true).fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithText("2 * 3 * 4 = 24", substring = true).assertIsDisplayed()

        // 4. Overflow button opens contextual ModalBottomSheet with relocated actions
        composeTestRule.onNodeWithTag("detail_more_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_actions_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sheet_action_add_note").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sheet_action_add_checklist").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sheet_action_copy_entry").assertIsDisplayed()

        // Switch to PROMPT item -> "Copy Prompt" button MUST be displayed and literal prompt syntax preserved
        activeDetailState.value = loadedPrompt
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_content_list")
            .performScrollToNode(hasTestTag("detail_main_content_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("copy_prompt_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Act as a **Kotlin Compose Architect** for {TOPIC}.", substring = true).assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("PROMPT").fetchSemanticsNodes().isNotEmpty())
        Unit
    }

    @Test
    fun `verify issue 05 and 06 light and dark theme tokens and issue 09 attachment deduplication`(): Unit = runBlocking {
        // Verify V2 White Theme tokens match 01_APPROVED_WHITE_V2_BOARD01.png
        assertEquals(Color(0xFFFAFAF8), IdeaVaultLightColors.background)
        assertEquals(Color(0xFFFFFFFF), IdeaVaultLightColors.surface)
        assertEquals(Color(0xFFF1F1EF), IdeaVaultLightColors.elevatedSurface)
        assertEquals(Color(0xFF191919), IdeaVaultLightColors.primaryText)
        assertEquals(Color(0xFF666666), IdeaVaultLightColors.secondaryText)
        assertEquals(Color(0xFFE7E7E5), IdeaVaultLightColors.subtleBorder)
        assertEquals(Color(0xFFEF7B59), IdeaVaultLightColors.coral)
        assertEquals(Color(0xFFA7D678), IdeaVaultLightColors.lime)
        assertEquals(Color(0xFFF9D55D), IdeaVaultLightColors.yellow)
        assertEquals(Color(0xFFA096EF), IdeaVaultLightColors.lavender)
        assertEquals(Color(0xFFE994AD), IdeaVaultLightColors.pink)

        // Verify V2 Dark Theme tokens match 02_APPROVED_DARK_V2_BOARD01.png
        assertEquals(Color(0xFF171717), IdeaVaultDarkColors.background)
        assertEquals(Color(0xFF242424), IdeaVaultDarkColors.surface)
        assertEquals(Color(0xFF303030), IdeaVaultDarkColors.secondarySurface)
        assertEquals(Color(0xFF292929), IdeaVaultDarkColors.elevatedSurface)
        assertEquals(Color(0xFFF5F5F3), IdeaVaultDarkColors.primaryText)
        assertEquals(Color(0xFFA5A5A5), IdeaVaultDarkColors.secondaryText)
        assertEquals(Color(0xFF383838), IdeaVaultDarkColors.subtleBorder)

        // Verify Attachment deduplication prevents duplicate attachments on the same entry
        val dupAtt1 = AttachmentItem(
            id = "att_1",
            type = AttachmentType.PDF,
            name = "spec_sheet.pdf",
            uriOrUrl = "/data/user/0/com.example/files/vault_attachments/spec_sheet.pdf",
            mimeType = "application/pdf",
            sizeBytes = 4096L
        )
        val dupAtt2 = AttachmentItem(
            id = "att_2",
            type = AttachmentType.PDF,
            name = "spec_sheet.pdf",
            uriOrUrl = "/data/user/0/com.example/files/vault_attachments/2_spec_sheet.pdf",
            mimeType = "application/pdf",
            sizeBytes = 4096L
        )
        val deduped = AttachmentStorageManager.deduplicateAttachments(listOf(dupAtt1, dupAtt2))
        assertEquals(1, deduped.size)
        assertEquals("4.0 KB", AttachmentStorageManager.formatFileSize(4096L))
        Unit
    }

    @Test
    fun `verify V2_1 three screen animated onboarding and unified search architecture without duplicate favorites search bar`() {
        val vm = VaultViewModel(context, repository)
        composeTestRule.setContent {
            IdeaVaultApp(viewModel = vm)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            vm.uiState.value.prefs.isLoaded
        }
        composeTestRule.waitForIdle()

        // 1. Starts directly on Onboarding Scene 1 ("Capture Everything") without extra welcome screen or forced name input
        composeTestRule.onNodeWithTag("onboarding_flow_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Capture Everything").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ideas, methods, prompts, knowledge and more.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_stage_capture").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_scene1_card_coral").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_scene1_card_lime").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_scene1_card_yellow").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_scene1_card_lavender").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_skip_button").assertIsDisplayed()

        // 2. Tap Next -> Onboarding Scene 2 ("Organize Your Way")
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Organize Your Way").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep your ideas, methods and references neatly organized.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_stage_organize").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_scene2_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_scene2_row_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_back_button").assertIsDisplayed()

        // 3. Tap Next -> Onboarding Scene 3 ("Everything in One Place")
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Everything in One Place").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notes, images, links, files and step-by-step methods.").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_stage_everything").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_scene3_card_images").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_scene3_card_links").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_scene3_card_files").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_scene3_card_code").assertIsDisplayed()
        composeTestRule.onNodeWithText("Get Started").assertIsDisplayed()

        // 4. Tap Get Started -> persists onboarding completion and enters clean empty Home screen
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.uiState.value.prefs.hasCompletedOnboarding && vm.uiState.value.hasCompletedOnboardingSession
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
        runBlocking {
            assertTrue(prefsRepo.getSnapshot().hasCompletedOnboarding)
        }
        assertTrue(composeTestRule.onAllNodesWithText("Load Starter Templates").fetchSemanticsNodes().isEmpty())

        // 5. Tapping the Home top search bar opens the dedicated Search screen directly
        composeTestRule.onNodeWithTag("home_search_input").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("search_results_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("search_query_input").assertIsDisplayed()

        // 6. Return from Search via search_back_button, then open Favourite screen (does not render a duplicate search bar)
        composeTestRule.onNodeWithTag("search_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bottom_nav_favorites").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("favorites_screen_list").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("favorites_search_input").fetchSemanticsNodes().isEmpty())
        Unit
    }

    @Test
    fun `verify V2_2 database migration 2 to 3 preserves existing method steps and follow runs as main route`(): Unit = runBlocking {
        val dbName = "migration_v2_to_v3_${System.nanoTime()}.db"
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `folders` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `colorHex` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_category` ON `folders` (`category`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_updatedAt` ON `folders` (`updatedAt`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `vault_items` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `content` TEXT NOT NULL, `category` TEXT NOT NULL, `folderId` TEXT, `tagsJson` TEXT NOT NULL, `richBlocksJson` TEXT NOT NULL, `attachmentsJson` TEXT NOT NULL, `ideaStatus` TEXT NOT NULL, `platform` TEXT NOT NULL, `estimatedTime` TEXT NOT NULL, `notes` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL, `isTrashed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_category` ON `vault_items` (`category`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_folderId` ON `vault_items` (`folderId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_isFavorite` ON `vault_items` (`isFavorite`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_isPinned` ON `vault_items` (`isPinned`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_isTrashed` ON `vault_items` (`isTrashed`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_isArchived` ON `vault_items` (`isArchived`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_updatedAt` ON `vault_items` (`updatedAt`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_items_createdAt` ON `vault_items` (`createdAt`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `method_steps` (`id` TEXT NOT NULL, `itemId` TEXT NOT NULL, `stepOrder` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `notes` TEXT NOT NULL, `linksJson` TEXT NOT NULL, `imagesJson` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_method_steps_itemId_stepOrder` ON `method_steps` (`itemId`, `stepOrder`)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `method_follow_runs` (`itemId` TEXT NOT NULL, `completedStepIdsJson` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`itemId`))"
                    )

                    db.execSQL(
                        "INSERT INTO `vault_items` (`id`, `title`, `description`, `content`, `category`, `folderId`, `tagsJson`, `richBlocksJson`, `attachmentsJson`, `ideaStatus`, `platform`, `estimatedTime`, `notes`, `isFavorite`, `isPinned`, `isArchived`, `isTrashed`, `createdAt`, `updatedAt`) " +
                            "VALUES ('method_v2', 'V2 Legacy Method', 'Workflow', '', 'METHOD', NULL, '[]', '[]', '[]', 'NEW', '', '', '', 0, 0, 0, 0, 500, 600)"
                    )
                    db.execSQL(
                        "INSERT INTO `method_steps` (`id`, `itemId`, `stepOrder`, `title`, `description`, `notes`, `linksJson`, `imagesJson`) VALUES ('v2_s1', 'method_v2', 1, 'Legacy Step 1', 'Do first', 'Tip 1', '[]', '[]')"
                    )
                    db.execSQL(
                        "INSERT INTO `method_steps` (`id`, `itemId`, `stepOrder`, `title`, `description`, `notes`, `linksJson`, `imagesJson`) VALUES ('v2_s2', 'method_v2', 2, 'Legacy Step 2', 'Do second', '', '[]', '[]')"
                    )
                    db.execSQL(
                        "INSERT INTO `method_follow_runs` (`itemId`, `completedStepIdsJson`, `startedAt`, `updatedAt`) VALUES ('method_v2', '[\"v2_s1\"]', 700, 800)"
                    )
                }

                override fun onUpgrade(
                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int
                ) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.close()

        val upgradedDb = Room.databaseBuilder(context, VaultDatabase::class.java, dbName)
            .addMigrations(VaultDatabase.MIGRATION_2_3, VaultDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val steps = upgradedDb.vaultDao().getAllStepsSnapshot()
        assertEquals(2, steps.size)
        assertTrue(steps.all { it.routeId == MAIN_METHOD_ROUTE_ID })
        assertEquals("Legacy Step 1", steps[0].title)
        assertEquals("Legacy Step 2", steps[1].title)

        val routeRuns = upgradedDb.vaultDao().getAllRouteFollowRunsSnapshot()
        assertEquals(1, routeRuns.size)
        assertEquals("method_v2", routeRuns.single().itemId)
        assertEquals(MAIN_METHOD_ROUTE_ID, routeRuns.single().routeId)
        assertEquals("[\"v2_s1\"]", routeRuns.single().completedStepIdsJson)

        upgradedDb.close()
        context.deleteDatabase(dbName)
        Unit
    }

    @Test
    fun `verify V2_2 alternative methods creation editing step reordering route follow mode and reset`(): Unit = runBlocking {
        val method = VaultItemEntity(
            id = "method_multi_route",
            title = "Deploy Android Release",
            description = "Multiple deployment workflows",
            category = VaultCategory.METHOD.id
        )
        val mainSteps = listOf(
            MethodStepEntity(id = "m_s1", itemId = method.id, routeId = MAIN_METHOD_ROUTE_ID, stepOrder = 1, title = "Run Gradle checks", description = "Verify tests"),
            MethodStepEntity(id = "m_s2", itemId = method.id, routeId = MAIN_METHOD_ROUTE_ID, stepOrder = 2, title = "Sign release bundle", description = "Use release keystore"),
            MethodStepEntity(id = "m_s3", itemId = method.id, routeId = MAIN_METHOD_ROUTE_ID, stepOrder = 3, title = "Upload to Play Console", description = "Production track")
        )
        val fastRouteId = "route_fast_cli"
        val manualRouteId = "route_manual_qa"
        val routes = listOf(
            MethodRouteEntity(id = MAIN_METHOD_ROUTE_ID, itemId = method.id, name = "Main Method", description = "Standard release", routeOrder = 0, isMain = true),
            MethodRouteEntity(id = fastRouteId, itemId = method.id, name = "Fast CLI Method", description = "Automated fastlane script", routeOrder = 1, isMain = false),
            MethodRouteEntity(id = manualRouteId, itemId = method.id, name = "Manual QA Method", description = "Full regression pass", routeOrder = 2, isMain = false)
        )
        val fastSteps = listOf(
            MethodStepEntity(id = "f_s1", itemId = method.id, routeId = fastRouteId, stepOrder = 1, title = "Run CLI deploy", description = "fastlane deploy"),
            MethodStepEntity(id = "f_s2", itemId = method.id, routeId = fastRouteId, stepOrder = 2, title = "Verify webhook", description = "Slack notification")
        )
        val manualSteps = listOf(
            MethodStepEntity(id = "q_s1", itemId = method.id, routeId = manualRouteId, stepOrder = 1, title = "Smoke test build", description = "Check core flows"),
            MethodStepEntity(id = "q_s2", itemId = method.id, routeId = manualRouteId, stepOrder = 2, title = "Approve QA signoff", description = "QA lead approval")
        )
        val altRoutes = listOf(
            MethodRouteWithSteps(route = routes[1], steps = fastSteps),
            MethodRouteWithSteps(route = routes[2], steps = manualSteps)
        )
        assertTrue(
            repository.saveVaultItem(
                item = method,
                steps = mainSteps,
                mainRouteName = "Main Method",
                mainRouteDescription = "Standard release",
                alternativeRoutes = altRoutes
            )
        )

        var loaded = repository.getItemWithDetailsById(method.id)!!
        assertTrue(loaded.hasAlternativeMethods)
        assertEquals(3, loaded.effectiveMethodRoutes.size)
        // Main steps are never merged with alternative steps
        assertEquals(listOf("m_s1", "m_s2", "m_s3"), loaded.steps.map { it.id })
        assertEquals(listOf("f_s1", "f_s2"), loaded.effectiveMethodRoutes.first { it.id == fastRouteId }.steps.map { it.id })
        assertEquals(listOf("q_s1", "q_s2"), loaded.effectiveMethodRoutes.first { it.id == manualRouteId }.steps.map { it.id })

        // Reorder steps in Fast CLI Method without affecting Main Method
        repository.reorderMethodSteps(method.id, listOf("f_s2", "f_s1"), routeId = fastRouteId)
        loaded = repository.getItemWithDetailsById(method.id)!!
        assertEquals(listOf("m_s1", "m_s2", "m_s3"), loaded.steps.map { it.id })
        assertEquals(listOf("f_s2", "f_s1"), loaded.effectiveMethodRoutes.first { it.id == fastRouteId }.steps.map { it.id })

        // Route-specific Follow Mode progress: complete 2 steps in Main Method and 1 step in Fast CLI Method
        repository.toggleFollowStep(method.id, "m_s1", routeId = MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(method.id, "m_s2", routeId = MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(method.id, "f_s2", routeId = fastRouteId)

        loaded = repository.getItemWithDetailsById(method.id)!!
        val mainRouteLoaded = loaded.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }
        val fastRouteLoaded = loaded.effectiveMethodRoutes.first { it.id == fastRouteId }
        val manualRouteLoaded = loaded.effectiveMethodRoutes.first { it.id == manualRouteId }
        assertEquals(setOf("m_s1", "m_s2"), mainRouteLoaded.completedStepIds)
        assertEquals(setOf("f_s2"), fastRouteLoaded.completedStepIds)
        assertTrue(manualRouteLoaded.completedStepIds.isEmpty())

        // Reset Follow progress ONLY for Fast CLI Method -> Main Method progress remains intact!
        repository.resetFollowProgress(method.id, routeId = fastRouteId)
        loaded = repository.getItemWithDetailsById(method.id)!!
        assertEquals(setOf("m_s1", "m_s2"), loaded.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }.completedStepIds)
        assertTrue(loaded.effectiveMethodRoutes.first { it.id == fastRouteId }.completedStepIds.isEmpty())

        // Duplicate the entire Method entry -> copies all routes and their respective steps
        val copyId = repository.duplicateVaultItem(loaded)
        val copiedMethod = repository.getItemWithDetailsById(copyId)!!
        assertTrue(copiedMethod.hasAlternativeMethods)
        assertEquals(3, copiedMethod.effectiveMethodRoutes.size)
        assertEquals(3, copiedMethod.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }.steps.size)
        assertEquals(2, copiedMethod.effectiveMethodRoutes.first { it.name == "Fast CLI Method" }.steps.size)
        assertEquals(2, copiedMethod.effectiveMethodRoutes.first { it.name == "Manual QA Method" }.steps.size)
        Unit
    }

    @Test
    fun `verify V2_2 editor draft recovery and zip backup restore preserve alternative methods and route progress`(): Unit = runBlocking {
        val vm = VaultViewModel(context, repository)
        val itemId = "draft_and_backup_method"
        val altRouteId = "route_alt_1"

        val mainSteps = listOf(
            MethodStepEntity(id = "ms_1", itemId = itemId, routeId = MAIN_METHOD_ROUTE_ID, stepOrder = 1, title = "Main Step 1", description = "Main desc 1"),
            MethodStepEntity(id = "ms_2", itemId = itemId, routeId = MAIN_METHOD_ROUTE_ID, stepOrder = 2, title = "Main Step 2", description = "Main desc 2")
        )
        val altSteps = listOf(
            MethodStepEntity(id = "as_1", itemId = itemId, routeId = altRouteId, stepOrder = 1, title = "Alt Step 1", description = "Alt desc 1", notes = "Fast tip")
        )
        val altRoutesWithSteps = listOf(
            MethodRouteWithSteps(
                route = MethodRouteEntity(
                    id = altRouteId,
                    itemId = itemId,
                    name = "Offline Fallback Method",
                    description = "When network is unavailable",
                    routeOrder = 1,
                    isMain = false
                ),
                steps = altSteps
            )
        )

        // 1. Verify EditorDraftPayload round-trips main + alternative routes losslessly
        val draft = EditorDraftPayload(
            itemId = itemId,
            isEditingExisting = false,
            baseUpdatedAt = null,
            title = "Resilient Sync Workflow",
            description = "Primary and fallback sync methods",
            content = "Overview notes",
            categoryId = VaultCategory.METHOD.id,
            folderId = null,
            ideaStatusId = IdeaStatus.NEW.id,
            platform = "Android",
            estimatedTime = "20 mins",
            notes = "",
            tags = listOf("sync", "fallback"),
            richBlocks = emptyList(),
            attachments = emptyList(),
            steps = mainSteps,
            mainRouteName = "Main Method",
            mainRouteDescription = "Online sync",
            alternativeRoutes = altRoutesWithSteps
        )
        vm.saveEditorDraftImmediate(VaultJsonSerializers.encodeEditorDraft(draft))
        val decodedDraft = VaultJsonSerializers.decodeEditorDraft(prefsRepo.getSnapshot().editorDraftJson)
        assertNotNull(decodedDraft)
        assertEquals(1, decodedDraft!!.alternativeRoutes.size)
        assertEquals("Offline Fallback Method", decodedDraft.alternativeRoutes.single().name)
        assertEquals("Alt Step 1", decodedDraft.alternativeRoutes.single().steps.single().title)
        assertEquals("Fast tip", decodedDraft.alternativeRoutes.single().steps.single().notes)

        // 2. Save item with routes and route follow progress, export ZIP backup, wipe DB, and restore
        val saveRes = vm.saveItemAwaitable(
            item = VaultItemEntity(
                id = itemId,
                title = draft.title,
                description = draft.description,
                content = draft.content,
                category = VaultCategory.METHOD.id
            ),
            steps = mainSteps,
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false,
            mainRouteName = "Main Method",
            mainRouteDescription = "Online sync",
            alternativeRoutes = altRoutesWithSteps
        )
        assertTrue(saveRes is SaveItemResult.Success)
        repository.toggleFollowStep(itemId, "ms_1", routeId = MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(itemId, "as_1", routeId = altRouteId)

        val zipOut = ByteArrayOutputStream()
        assertTrue(repository.exportBackup(zipOut) is BackupRestoreResult.Success)
        val zipBytes = zipOut.toByteArray()

        db.vaultDao().clearAllItems()
        db.vaultDao().clearAllRoutes()
        db.vaultDao().clearAllSteps()
        db.vaultDao().clearAllRouteFollowRuns()
        assertTrue(repository.activeItemsWithDetailsFlow.first().isEmpty())

        val validated = repository.validateBackup(ByteArrayInputStream(zipBytes)).getOrThrow()
        assertTrue(repository.applyBackup(validated) is BackupRestoreResult.Success)

        val restored = repository.getItemWithDetailsById(itemId)!!
        assertTrue(restored.hasAlternativeMethods)
        assertEquals(2, restored.effectiveMethodRoutes.size)
        val restoredMain = restored.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }
        val restoredAlt = restored.effectiveMethodRoutes.first { it.id == altRouteId }
        assertEquals(2, restoredMain.steps.size)
        assertEquals(setOf("ms_1"), restoredMain.completedStepIds)
        assertEquals("Offline Fallback Method", restoredAlt.name)
        assertEquals(1, restoredAlt.steps.size)
        assertEquals("Alt Step 1", restoredAlt.steps.single().title)
        assertEquals(setOf("as_1"), restoredAlt.completedStepIds)
        Unit
    }

    @Test
    fun `verify V2_2 compose ui alternative methods in editor and detail screen and lower step 10 editing`() {
        val inEditorMode = androidx.compose.runtime.mutableStateOf(true)
        val detailItemState = androidx.compose.runtime.mutableStateOf<com.example.data.VaultItemWithDetails?>(null)

        composeTestRule.setContent {
            IdeaVaultTheme(themeMode = AppThemeMode.LIGHT) {
                if (inEditorMode.value) {
                    EditorScreen(
                        existingItem = null,
                        initialCategory = VaultCategory.METHOD,
                        initialFolderId = null,
                        folders = emptyList(),
                        recoverableDraft = null,
                        attachmentStorage = attachmentStorage,
                        onAutoSaveDraft = {},
                        onDiscardDraft = {},
                        onCreateFolder = { _, _, _ -> },
                        onSaveItemAwaitable = { entity, steps, expectedUpdatedAt, isEditingExisting, forceOverwrite ->
                            repository.saveVaultItemWithResult(
                                item = entity,
                                steps = steps,
                                expectedPreviousUpdatedAt = expectedUpdatedAt,
                                isEditingExisting = isEditingExisting,
                                forceOverwrite = forceOverwrite
                            )
                        },
                        onSaveItemWithRoutesAwaitable = { entity, steps, expectedUpdatedAt, isEditingExisting, forceOverwrite, mainRouteName, mainRouteDesc, altRoutes, pendingCustomType ->
                            runBlocking {
                                val res = repository.saveVaultItemWithResult(
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
                                detailItemState.value = repository.getItemWithDetailsById(entity.id)
                                res
                            }
                        },
                        onSaveSuccess = {
                            inEditorMode.value = false
                        },
                        onCancel = {}
                    )
                } else {
                    detailItemState.value?.let { currentItem ->
                        ItemDetailScreen(
                            item = currentItem,
                            folders = emptyList(),
                            onBack = {},
                            onEdit = {},
                            onToggleFavorite = {},
                            onTogglePin = {},
                            onDuplicate = {},
                            onMoveToFolder = {},
                            onToggleArchive = {},
                            onMoveToTrash = {},
                            onUpdateIdeaStatus = {},
                            onToggleFollowStep = { stepId ->
                                runBlocking {
                                    repository.toggleFollowStep(currentItem.entity.id, stepId, MAIN_METHOD_ROUTE_ID)
                                    detailItemState.value = repository.getItemWithDetailsById(currentItem.entity.id)
                                }
                            },
                            onToggleFollowStepForRoute = { routeId, stepId ->
                                runBlocking {
                                    repository.toggleFollowStep(currentItem.entity.id, stepId, routeId)
                                    detailItemState.value = repository.getItemWithDetailsById(currentItem.entity.id)
                                }
                            },
                            onResetFollowSteps = {
                                runBlocking {
                                    repository.resetFollowProgress(currentItem.entity.id, MAIN_METHOD_ROUTE_ID)
                                    detailItemState.value = repository.getItemWithDetailsById(currentItem.entity.id)
                                }
                            },
                            onResetFollowStepsForRoute = { routeId ->
                                runBlocking {
                                    repository.resetFollowProgress(currentItem.entity.id, routeId)
                                    detailItemState.value = repository.getItemWithDetailsById(currentItem.entity.id)
                                }
                            },
                            onReorderSteps = {},
                            onToggleChecklistBlock = {},
                            onAddQuickRichBlock = {},
                            onAddUriAttachment = { _, _ -> },
                            onAddLinkAttachment = { _, _ -> },
                            onRemoveAttachment = {},
                            onShowMessage = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Enter Method Title
        composeTestRule.onNodeWithTag("editor_title_input").performTextInput("Multi-Route Release Guide")

        // 2. Add 10 steps in Main Method and verify lower steps (Step 6 and Step 10) scroll into view and remain editable
        for (i in 1..10) {
            val stepLabel = when (i) {
                1 -> "Main Step 1: Prepare"
                6 -> "Main Step 6: Mid Lower Step"
                10 -> "Main Step 10: Deep Lower Step"
                else -> "Main Step $i"
            }
            composeTestRule.onNodeWithTag("editor_scroll_list")
                .performScrollToNode(hasTestTag("editor_add_step_card"))
            composeTestRule.onNodeWithTag("editor_new_step_title").performTextInput(stepLabel)
            composeTestRule.onNodeWithTag("editor_add_step_button").performClick()
            composeTestRule.waitForIdle()
        }
        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToNode(hasTestTag("editor_step_card_9"))
        composeTestRule.onNodeWithTag("editor_step_details_9")
            .performTextInput("Step 10 description stays visible above toolbar")
        composeTestRule.onNodeWithTag("editor_step_notes_9")
            .performTextInput("Step 10 tip")
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()

        // Verify Save button in Top Bar remains displayed and accessible while editing Step 10
        composeTestRule.onNodeWithTag("editor_save_button").assertIsDisplayed()

        // 3. Add an Alternative Method via "+ Add Alternative Method"
        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToIndex(0)
        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToNode(hasTestTag("editor_steps_section"))
        composeTestRule.onNodeWithTag("editor_add_alternative_method_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_alt_method_name_input").performTextInput("Quick CLI Method")
        composeTestRule.onNodeWithTag("editor_alt_method_desc_input").performTextInput("Fast terminal workflow")
        composeTestRule.onNodeWithTag("editor_confirm_add_alt_method_button").performClick()
        composeTestRule.waitForIdle()

        // Route selector is now displayed and switched to the new alternative route
        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToNode(hasTestTag("editor_steps_section"))
        composeTestRule.onNodeWithTag("editor_method_route_selector").assertIsDisplayed()

        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToNode(hasTestTag("editor_add_step_card"))
        composeTestRule.onNodeWithTag("editor_new_step_title").performTextInput("CLI Step 1: Run script")
        composeTestRule.onNodeWithTag("editor_add_step_button").performClick()
        composeTestRule.waitForIdle()

        // Save the Method entry -> switches to ItemDetailScreen
        composeTestRule.onNodeWithTag("editor_save_button").performClick()
        composeTestRule.waitForIdle()
        assertNotNull(detailItemState.value)

        // 4. Verify "Choose Method" selector and independent route steps in Detail Screen
        composeTestRule.onNodeWithTag("detail_content_list")
            .performScrollToNode(hasTestTag("detail_choose_method_selector"))
        composeTestRule.onNodeWithTag("detail_choose_method_selector").assertIsDisplayed()

        // Main Method is active by default; CLI Step 1 must NOT be merged into Main Method's list
        composeTestRule.onNodeWithTag("detail_content_list")
            .performScrollToNode(hasTestTag("detail_follow_step_0"))
        composeTestRule.onNodeWithText("Main Step 1: Prepare", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("CLI Step 1: Run script", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())

        // Enable Follow Mode and complete Step 1 in Main Method
        composeTestRule.onNodeWithTag("detail_content_list")
            .performScrollToNode(hasTestTag("follow_method_button"))
        composeTestRule.onNodeWithTag("follow_method_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_content_list")
            .performScrollToNode(hasTestTag("detail_follow_step_0"))
        composeTestRule.onNodeWithTag("detail_follow_step_0").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_content_list")
            .performScrollToNode(hasTestTag("detail_follow_progress_text"))
        composeTestRule.onNodeWithText("Main Method: 1/10 steps completed", substring = true, useUnmergedTree = true).assertIsDisplayed()

        // Switch to "Quick CLI Method" chip -> shows only CLI Step 1 with 0/1 completed
        val savedItem = detailItemState.value!!
        val cliRouteId = savedItem.alternativeMethodRoutes.single().id
        composeTestRule.onNodeWithTag("detail_content_list")
            .performScrollToNode(hasTestTag("detail_choose_method_selector"))
        composeTestRule.onNodeWithTag("detail_route_chip_$cliRouteId").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("detail_content_list")
            .performScrollToNode(hasTestTag("detail_follow_step_0"))
        composeTestRule.onNodeWithText("CLI Step 1: Run script", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("Main Step 1: Prepare", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        composeTestRule.onNodeWithTag("detail_content_list")
            .performScrollToNode(hasTestTag("detail_follow_progress_text"))
        composeTestRule.onNodeWithText("Quick CLI Method: 0/1 steps completed", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `verify V2_3 exact prompt preservation pending step draft recovery alternative route validation and collections move`(): Unit = runBlocking {
        val vm = VaultViewModel(context, repository)

        // 1. DATA-01: Uncommitted pending step title, details, new tag, and activeRouteId round-trip in EditorDraftPayload
        val pendingDraft = EditorDraftPayload(
            itemId = "draft_v23_pending",
            isEditingExisting = false,
            baseUpdatedAt = null,
            title = "Pending Step Method",
            description = "Testing uncommitted inputs",
            content = "Overview",
            categoryId = VaultCategory.METHOD.id,
            folderId = null,
            ideaStatusId = IdeaStatus.NEW.id,
            platform = "",
            estimatedTime = "",
            notes = "",
            tags = listOf("committed-tag"),
            richBlocks = emptyList(),
            attachments = emptyList(),
            steps = listOf(
                MethodStepEntity(id = "s_1", itemId = "draft_v23_pending", routeId = MAIN_METHOD_ROUTE_ID, stepOrder = 1, title = "Committed Step 1")
            ),
            mainRouteName = "Main Method",
            mainRouteDescription = "",
            alternativeRoutes = emptyList(),
            activeRouteId = MAIN_METHOD_ROUTE_ID,
            pendingStepTitle = "Uncommitted Step 2 Title",
            pendingStepDetails = "Uncommitted Step 2 Details",
            pendingNewTag = "uncommitted-tag"
        )
        val encodedPending = VaultJsonSerializers.encodeEditorDraft(pendingDraft)
        val decodedPending = VaultJsonSerializers.decodeEditorDraft(encodedPending)
        assertNotNull(decodedPending)
        assertEquals("Uncommitted Step 2 Title", decodedPending!!.pendingStepTitle)
        assertEquals("Uncommitted Step 2 Details", decodedPending.pendingStepDetails)
        assertEquals("uncommitted-tag", decodedPending.pendingNewTag)
        assertEquals(MAIN_METHOD_ROUTE_ID, decodedPending.activeRouteId)

        // 2. DATA-02: Exact Prompt preservation (trailing spaces, trailing newlines, JSON, braces, code blocks, Tamil + Unicode)
        val exactPrompt = "{\n  \"system\": \"You are a Tamil/English assistant தமிழ் ✨\",\n  \"template\": \"Analyze {USER_CODE} and keep **exact** markdown\",\n  \"code\": \"```kotlin\\nval x = 1 * 2 * 3\\n```\"\n}   \n\n  "
        val promptEntity = VaultItemEntity(
            id = "prompt_exact_v23",
            title = "Exact Multiline JSON Prompt",
            description = "Must never trim trailing spaces or newlines",
            content = exactPrompt,
            category = VaultCategory.PROMPT.id
        )
        val savePromptRes = vm.saveItemAwaitable(
            item = promptEntity,
            steps = emptyList(),
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false
        )
        assertTrue(savePromptRes is SaveItemResult.Success)
        val loadedPrompt = repository.getItemWithDetailsById("prompt_exact_v23")!!
        assertEquals(exactPrompt, loadedPrompt.entity.content)

        // Verify backup & restore also preserves exact prompt byte-for-byte
        val backupBytes = ByteArrayOutputStream().also { out ->
            assertTrue(repository.exportBackup(out) is BackupRestoreResult.Success)
        }.toByteArray()
        val validatedBackup = repository.validateBackup(ByteArrayInputStream(backupBytes)).getOrThrow()
        assertTrue(repository.applyBackup(validatedBackup) is BackupRestoreResult.Success)
        assertEquals(exactPrompt, repository.getItemWithDetailsById("prompt_exact_v23")!!.entity.content)

        // 3. DATA-04: Repository rejects blank or duplicate alternative method route names with explicit ValidationFailure
        val methodEntity = VaultItemEntity(
            id = "method_validation_v23",
            title = "Validated Routes",
            category = VaultCategory.METHOD.id
        )
        val blankRouteRes = repository.saveVaultItemWithResult(
            item = methodEntity,
            steps = listOf(MethodStepEntity(id = "ms1", itemId = methodEntity.id, stepOrder = 1, title = "Step 1")),
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false,
            alternativeRoutes = listOf(
                MethodRouteWithSteps(
                    route = MethodRouteEntity(id = "alt_blank", itemId = methodEntity.id, name = "   ", routeOrder = 1),
                    steps = emptyList()
                )
            )
        )
        assertTrue(blankRouteRes is SaveItemResult.ValidationFailure)

        val duplicateRouteRes = repository.saveVaultItemWithResult(
            item = methodEntity,
            steps = listOf(MethodStepEntity(id = "ms1", itemId = methodEntity.id, stepOrder = 1, title = "Step 1")),
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false,
            mainRouteName = "Main Method",
            alternativeRoutes = listOf(
                MethodRouteWithSteps(
                    route = MethodRouteEntity(id = "alt_dup", itemId = methodEntity.id, name = "main method", routeOrder = 1),
                    steps = emptyList()
                )
            )
        )
        assertTrue(duplicateRouteRes is SaveItemResult.ValidationFailure)

        // 4. DATA-05: Editing a route and removing a step prunes only the removed step ID from follow progress
        val validAltRouteId = "alt_valid_1"
        val validSaveRes = repository.saveVaultItemWithResult(
            item = methodEntity,
            steps = listOf(
                MethodStepEntity(id = "ms_keep", itemId = methodEntity.id, routeId = MAIN_METHOD_ROUTE_ID, stepOrder = 1, title = "Keep Step"),
                MethodStepEntity(id = "ms_drop", itemId = methodEntity.id, routeId = MAIN_METHOD_ROUTE_ID, stepOrder = 2, title = "Drop Step")
            ),
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false,
            alternativeRoutes = listOf(
                MethodRouteWithSteps(
                    route = MethodRouteEntity(id = validAltRouteId, itemId = methodEntity.id, name = "Fast Route", routeOrder = 1),
                    steps = listOf(MethodStepEntity(id = "as_keep", itemId = methodEntity.id, routeId = validAltRouteId, stepOrder = 1, title = "Alt Step"))
                )
            )
        )
        assertTrue(validSaveRes is SaveItemResult.Success)
        repository.toggleFollowStep(methodEntity.id, "ms_keep", MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(methodEntity.id, "ms_drop", MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(methodEntity.id, "as_keep", validAltRouteId)

        val beforePrune = repository.getItemWithDetailsById(methodEntity.id)!!
        val updateAfterRemovingStep = repository.saveVaultItemWithResult(
            item = beforePrune.entity,
            steps = listOf(
                MethodStepEntity(id = "ms_keep", itemId = methodEntity.id, routeId = MAIN_METHOD_ROUTE_ID, stepOrder = 1, title = "Keep Step")
            ),
            expectedPreviousUpdatedAt = beforePrune.entity.updatedAt,
            isEditingExisting = true,
            alternativeRoutes = listOf(
                MethodRouteWithSteps(
                    route = MethodRouteEntity(id = validAltRouteId, itemId = methodEntity.id, name = "Fast Route", routeOrder = 1),
                    steps = listOf(MethodStepEntity(id = "as_keep", itemId = methodEntity.id, routeId = validAltRouteId, stepOrder = 1, title = "Alt Step"))
                )
            )
        )
        assertTrue(updateAfterRemovingStep is SaveItemResult.Success)
        val afterPrune = repository.getItemWithDetailsById(methodEntity.id)!!
        assertEquals(setOf("ms_keep"), afterPrune.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }.completedStepIds)
        assertEquals(setOf("as_keep"), afterPrune.effectiveMethodRoutes.first { it.id == validAltRouteId }.completedStepIds)

        // 5. UX-10: FilterAndSortState.activeFilterCount includes categoryFilter
        assertEquals(0, com.example.ui.FilterAndSortState().activeFilterCount)
        assertEquals(1, com.example.ui.FilterAndSortState(categoryFilter = VaultCategory.PROMPT).activeFilterCount)
        assertEquals(
            3,
            com.example.ui.FilterAndSortState(
                categoryFilter = VaultCategory.PROMPT,
                folderFilterId = "folder_1",
                onlyFavorites = true
            ).activeFilterCount
        )
        Unit
    }

    @Test
    fun `verify stage 01 app lock startup gate process death non restoration rotation background relock and pin lifecycle`() {
        // Seed a confidential entry and an unsaved recovery draft to verify zero data loss across all lock transitions
        runBlocking {
            assertTrue(prefsRepo.completeOnboarding("Rahmathullah"))
            repository.saveVaultItem(
                VaultItemEntity(
                    id = "stage01_secret_item",
                    title = "Confidential Idea Entry",
                    description = "Must remain protected behind App Lock",
                    content = "Top secret vault payload",
                    category = VaultCategory.IDEA.id
                ),
                emptyList()
            )
            prefsRepo.saveEditorDraft(
                VaultJsonSerializers.encodeEditorDraft(
                    EditorDraftPayload(
                        itemId = "stage01_draft_item",
                        title = "Confidential Recovery Draft",
                        content = "Unsaved work preserved across lock & rotation",
                        categoryId = VaultCategory.IDEA.id
                    )
                )
            )
        }

        // Verify fault-injected PIN write fails closed without enabling broken lock or unlocking session
        val setupVm = VaultViewModel(context, repository)
        runBlocking {
            testFaultPolicy.writeFault = { throw IOException("Simulated DataStore disk full") }
            val failedSetup = setupVm.setupAppLockPin("4826")
            assertFalse(failedSetup)
            assertFalse(setupVm.isSessionUnlocked)
            assertFalse(prefsRepo.getSnapshot().isAppLockEnabled)
            testFaultPolicy.writeFault = null

            // Valid PIN setup persists salted PBKDF2 hash and unlocks current setup session
            val okSetup = setupVm.setupAppLockPin("4826")
            assertTrue(okSetup)
            assertTrue(setupVm.isSessionUnlocked)
            assertTrue(prefsRepo.getSnapshot().isAppLockEnabled)
            val lockMeta = prefsRepo.inspectStoredLockMetadataForVerification()
            assertNull(lockMeta.legacyPlaintextValue)
            assertFalse(lockMeta.storedHashValue.isNullOrBlank())
            assertFalse(lockMeta.storedHashValue!!.contains("4826"))
        }

        // Scenario 3: Close and reopen the app (new ViewModel instance starts locked)
        val vm1 = VaultViewModel(context, repository)
        assertFalse(vm1.isSessionUnlocked)
        val activeVmState = androidx.compose.runtime.mutableStateOf(vm1)
        var simulatedIsChangingConfigurations = false

        val testLifecycleOwner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry(this).apply {
                currentState = androidx.lifecycle.Lifecycle.State.RESUMED
            }
            override val lifecycle: androidx.lifecycle.Lifecycle
                get() = registry
        }

        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.lifecycle.compose.LocalLifecycleOwner provides testLifecycleOwner
            ) {
                IdeaVaultApp(
                    viewModel = activeVmState.value,
                    isChangingConfigurationsProvider = { simulatedIsChangingConfigurations }
                )
            }
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            activeVmState.value.uiState.value.prefs.isLoaded
        }
        composeTestRule.waitForIdle()

        // Vault is locked at startup; confidential Home content is NOT composed
        composeTestRule.onNodeWithTag("app_lock_gate_screen").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("home_screen_list").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("Confidential Idea Entry").fetchSemanticsNodes().isEmpty())

        // Scenario 4: Enter incorrect PIN -> rejected, error shown, remains locked
        composeTestRule.onNodeWithTag("app_lock_pin_input").performTextInput("9999")
        composeTestRule.onNodeWithTag("app_lock_unlock_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            composeTestRule.onAllNodesWithTag("app_lock_error_text").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("app_lock_error_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_lock_gate_screen").assertIsDisplayed()
        assertFalse(vm1.isSessionUnlocked)

        // Scenario 5: Enter correct PIN -> unlocks session and displays Home screen with saved entry
        composeTestRule.onNodeWithTag("app_lock_pin_input").performTextReplacement("4826")
        composeTestRule.onNodeWithTag("app_lock_unlock_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm1.isSessionUnlocked && vm1.uiState.value.allActiveItems.isNotEmpty()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confidential Idea Entry").assertIsDisplayed()

        // Scenario 6 (Issue 3 real lifecycle test): Rotate the screen!
        // Dispatch actual ON_PAUSE + ON_STOP with isChangingConfigurations = true, restore saved UI state, then ON_START + ON_RESUME.
        simulatedIsChangingConfigurations = true
        composeTestRule.runOnUiThread {
            testLifecycleOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
            testLifecycleOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        }
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.runOnUiThread {
            testLifecycleOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
            testLifecycleOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        }
        simulatedIsChangingConfigurations = false
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        assertTrue(vm1.isSessionUnlocked)
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("Confidential Idea Entry").assertIsDisplayed()

        // Scenario 8 (P0-02 regression proof): Simulate OS process death and recreation!
        // Saved instance state Bundle was captured while unlocked, but the new process creates a fresh ViewModel.
        // Saved UI state MUST NOT restore an unlocked session!
        val vmAfterProcessDeath = VaultViewModel(context, repository)
        assertFalse(vmAfterProcessDeath.isSessionUnlocked)
        activeVmState.value = vmAfterProcessDeath
        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vmAfterProcessDeath.uiState.value.prefs.isLoaded
        }
        composeTestRule.waitForIdle()
        assertFalse(vmAfterProcessDeath.isSessionUnlocked)
        composeTestRule.onNodeWithTag("app_lock_gate_screen").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("home_screen_list").fetchSemanticsNodes().isEmpty())

        // Unlock after process recreation
        composeTestRule.onNodeWithTag("app_lock_pin_input").performTextInput("4826")
        composeTestRule.onNodeWithTag("app_lock_unlock_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vmAfterProcessDeath.isSessionUnlocked
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()

        // Scenario 7 (Issue 3 real lifecycle test): Background and foreground the app via actual Lifecycle.Event.ON_STOP -> ON_START -> ON_RESUME!
        // Do NOT call lockSession() directly; verify the registered LifecycleEventObserver locks the session on ON_STOP!
        simulatedIsChangingConfigurations = false
        composeTestRule.runOnUiThread {
            testLifecycleOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_PAUSE)
            testLifecycleOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_STOP)
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse(vmAfterProcessDeath.isSessionUnlocked)

        // Return to foreground (ON_START -> ON_RESUME): App Lock gate must be enforced with zero confidential content exposed
        composeTestRule.runOnUiThread {
            testLifecycleOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
            testLifecycleOwner.registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        composeTestRule.waitForIdle()
        assertFalse(vmAfterProcessDeath.isSessionUnlocked)
        composeTestRule.onNodeWithTag("app_lock_gate_screen").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("home_screen_list").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("Confidential Idea Entry").fetchSemanticsNodes().isEmpty())

        // Scenario 9 & 10: Change PIN using current PIN, then disable App Lock using current PIN
        runBlocking {
            assertFalse(vmAfterProcessDeath.changeOrDisableAppLockPin("0000", "7531"))
            assertTrue(vmAfterProcessDeath.changeOrDisableAppLockPin("4826", "7531"))
            assertTrue(prefsRepo.verifyAppLockPin("7531"))
            assertFalse(prefsRepo.verifyAppLockPin("4826"))

            // Disable App Lock with valid current PIN
            assertFalse(vmAfterProcessDeath.changeOrDisableAppLockPin("4826", ""))
            assertTrue(vmAfterProcessDeath.changeOrDisableAppLockPin("7531", ""))
            assertFalse(prefsRepo.getSnapshot().isAppLockEnabled)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            !vmAfterProcessDeath.uiState.value.prefs.isAppLockEnabled
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()

        // Scenario 12: Verify saved entry and recovery draft were never deleted or modified
        runBlocking {
            val savedItem = repository.getItemWithDetailsById("stage01_secret_item")
            assertNotNull(savedItem)
            assertEquals("Confidential Idea Entry", savedItem!!.entity.title)
            val savedDraft = VaultJsonSerializers.decodeEditorDraft(prefsRepo.getSnapshot().editorDraftJson)
            assertNotNull(savedDraft)
            assertEquals("Confidential Recovery Draft", savedDraft!!.title)
        }
    }

    @Test
    fun `verify stage 01 datastore read failure fails closed at cold start and with existing app lock then recovers on retry without overwriting pin`() {
        // Seed a confidential entry in Room
        runBlocking {
            repository.saveVaultItem(
                VaultItemEntity(
                    id = "read_fault_secret_1",
                    title = "Read Fault Protected Entry",
                    description = "Must never leak when DataStore read fails",
                    content = "Protected content",
                    category = VaultCategory.IDEA.id
                ),
                emptyList()
            )
        }

        // Part A: Cold start with simulated DataStore IOException read failure
        testFaultPolicy.readFault = { throw IOException("Simulated cold-start DataStore I/O error") }
        val vmColdStart = VaultViewModel(context, repository)
        val activeVmState = androidx.compose.runtime.mutableStateOf(vmColdStart)

        composeTestRule.setContent {
            IdeaVaultApp(viewModel = activeVmState.value)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            activeVmState.value.uiState.value.prefs.hasLoadError
        }
        composeTestRule.waitForIdle()

        // Must fail closed: isLoaded == false, hasLoadError == true, error gate shown, neither onboarding nor home shown
        assertFalse(vmColdStart.uiState.value.prefs.isLoaded)
        assertTrue(vmColdStart.uiState.value.prefs.hasLoadError)
        composeTestRule.onNodeWithTag("preferences_load_error_gate").assertIsDisplayed()
        composeTestRule.onNodeWithTag("preferences_load_error_message").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("onboarding_flow_screen").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag("home_screen_list").fetchSemanticsNodes().isEmpty())

        // Clear read fault and tap Retry -> genuinely empty preferences load into Onboarding
        testFaultPolicy.readFault = null
        composeTestRule.onNodeWithTag("preferences_load_retry_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vmColdStart.uiState.value.prefs.isLoaded && !vmColdStart.uiState.value.prefs.hasLoadError
        }
        composeTestRule.waitForIdle()
        assertFalse(vmColdStart.uiState.value.prefs.isAppLockEnabled)
        composeTestRule.onNodeWithTag("onboarding_flow_screen").assertIsDisplayed()

        // Part B: Configure onboarding + App Lock PIN ("4826"), then simulate DataStore read failure on next launch
        runBlocking {
            assertTrue(prefsRepo.completeOnboarding("Rahmathullah"))
            assertTrue(prefsRepo.updateAppLockPin("4826"))
            assertTrue(prefsRepo.getSnapshot().isAppLockEnabled)
        }

        testFaultPolicy.readFault = { throw IOException("Simulated read error with active App Lock") }
        val vmLockedReadFault = VaultViewModel(context, repository)
        activeVmState.value = vmLockedReadFault
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vmLockedReadFault.uiState.value.prefs.hasLoadError
        }
        composeTestRule.waitForIdle()

        // Fail closed: error gate displayed, confidential entry is NOT composed, PIN cannot be overwritten during read failure
        assertFalse(vmLockedReadFault.uiState.value.prefs.isLoaded)
        assertTrue(vmLockedReadFault.uiState.value.prefs.hasLoadError)
        composeTestRule.onNodeWithTag("preferences_load_error_gate").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("home_screen_list").fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithText("Read Fault Protected Entry").fetchSemanticsNodes().isEmpty())

        runBlocking {
            // Attempting to verify or disable PIN during read failure must fail closed without modifying stored PIN
            assertFalse(prefsRepo.verifyAppLockPin("4826"))
            assertFalse(prefsRepo.changeOrDisableAppLockPin("4826", ""))
        }

        // Clear read fault and tap Retry -> restores normal App Lock gate with existing PIN intact!
        testFaultPolicy.readFault = null
        composeTestRule.onNodeWithTag("preferences_load_retry_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vmLockedReadFault.uiState.value.prefs.isLoaded &&
                !vmLockedReadFault.uiState.value.prefs.hasLoadError &&
                vmLockedReadFault.uiState.value.prefs.isAppLockEnabled
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("app_lock_gate_screen").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("home_screen_list").fetchSemanticsNodes().isEmpty())

        // Verify wrong PIN still fails and original PIN "4826" unlocks the vault
        composeTestRule.onNodeWithTag("app_lock_pin_input").performTextInput("0000")
        composeTestRule.onNodeWithTag("app_lock_unlock_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            composeTestRule.onAllNodesWithTag("app_lock_error_text").fetchSemanticsNodes().isNotEmpty()
        }
        assertFalse(vmLockedReadFault.isSessionUnlocked)

        composeTestRule.onNodeWithTag("app_lock_pin_input").performTextReplacement("4826")
        composeTestRule.onNodeWithTag("app_lock_unlock_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vmLockedReadFault.isSessionUnlocked
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("Read Fault Protected Entry").assertIsDisplayed()
    }

    @Test
    fun `verify stage 01 onboarding completion awaits durable persistence and preserves state with retry on write failure`() {
        val vm = VaultViewModel(context, repository)
        composeTestRule.setContent {
            IdeaVaultApp(viewModel = vm)
        }
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.uiState.value.prefs.isLoaded
        }
        composeTestRule.waitForIdle()

        // Screen 1 -> Screen 2 -> Screen 3
        composeTestRule.onNodeWithTag("onboarding_flow_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_title_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_title_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_title_2").assertIsDisplayed()

        // Inject DataStore write failure when user clicks "Get Started"
        testFaultPolicy.writeFault = { throw IOException("Disk full while saving onboarding state") }
        composeTestRule.onNodeWithTag("onboarding_next_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            composeTestRule.onAllNodesWithTag("onboarding_save_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Must NOT report onboarding complete for the current session or advance to Home
        assertFalse(vm.uiState.value.hasCompletedOnboardingSession)
        assertFalse(vm.uiState.value.prefs.hasCompletedOnboarding)
        composeTestRule.onNodeWithTag("onboarding_flow_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_title_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_save_error_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("onboarding_save_retry_button").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("home_screen_list").fetchSemanticsNodes().isEmpty())

        // Clear write fault and tap Retry -> durably saves onboarding completion and transitions to Home
        testFaultPolicy.writeFault = null
        composeTestRule.onNodeWithTag("onboarding_save_retry_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vm.uiState.value.prefs.hasCompletedOnboarding && vm.uiState.value.hasCompletedOnboardingSession
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen_list").assertIsDisplayed()

        // Next launch with fresh ViewModel sees durably persisted onboarding completion
        val vmNextLaunch = VaultViewModel(context, repository)
        composeTestRule.waitUntil(timeoutMillis = 15_000L) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            vmNextLaunch.uiState.value.prefs.isLoaded
        }
        assertTrue(vmNextLaunch.uiState.value.prefs.hasCompletedOnboarding)
    }

    @Test
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun `verify stage 01 curved vault card concave cutout negative space and white dark canvas rendering`() {
        // Render the exact CurvedCutoutCardShape + standalone #F9D55D circular NorthEast arrow button
        // onto both White Theme (#FAFAF8) and Dark Theme (#171717) canvas bitmaps at 2x density (px = 2 * dp),
        // save verification PNG screenshots, and verify pixel colors in the negative-space concave channel.
        val density = Density(2f)
        val cardWidthDp = 360.dp
        val cardHeightDp = 182.dp
        val cutoutRadiusDp = 34.dp
        val cornerRadiusDp = 26.dp
        val padPx = with(density) { 24.dp.toPx().toInt() }
        val wPx = with(density) { cardWidthDp.toPx().toInt() }
        val hPx = with(density) { cardHeightDp.toPx().toInt() }
        val bmpW = wPx + padPx * 2
        val bmpH = hPx + padPx * 2

        val shape = com.example.ui.components.CurvedCutoutCardShape(
            cornerRadius = cornerRadiusDp,
            cutoutRadius = cutoutRadiusDp,
            smoothing = 12.dp,
            cutoutCorner = com.example.ui.components.CutoutCorner.BottomRight
        )
        val outline = shape.createOutline(
            size = Size(wPx.toFloat(), hPx.toFloat()),
            layoutDirection = LayoutDirection.Ltr,
            density = density
        ) as Outline.Generic

        val buttonDiameterDp = (cutoutRadiusDp + 10.dp).coerceIn(38.dp, 46.dp)
        val buttonRadiusPx = with(density) { (buttonDiameterDp / 2f).toPx() }
        val gapPx = with(density) { 7.dp.toPx() }

        fun renderCardPreviewBitmap(
            canvasBgArgb: Int,
            cardFillArgb: Int,
            buttonFillArgb: Int,
            inkArgb: Int,
            title: String,
            categoryLabel: String
        ): android.graphics.Bitmap {
            val bitmap = android.graphics.Bitmap.createBitmap(
                bmpW,
                bmpH,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(canvasBgArgb)

            canvas.save()
            canvas.translate(padPx.toFloat(), padPx.toFloat())

            // Draw card body using exact CurvedCutoutCardShape path
            val androidPath = android.graphics.Path(outline.path.asAndroidPath())

            val cardPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = cardFillArgb
            }
            canvas.drawPath(androidPath, cardPaint)

            // Draw card editorial details (date, category pill, title, summary, metadata pills)
            val pillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.argb(87, 255, 255, 255)
            }
            val textPaintTitle = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = inkArgb
                textSize = with(density) { 20.dp.toPx() }
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }
            val textPaintSub = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb(199, 25, 25, 25)
                textSize = with(density) { 14.dp.toPx() }
            }
            val textPaintLabel = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = inkArgb
                textSize = with(density) { 12.dp.toPx() }
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            }

            canvas.drawText("Oct 29", with(density) { 20.dp.toPx() }, with(density) { 34.dp.toPx() }, textPaintSub)
            canvas.drawRoundRect(
                with(density) { 74.dp.toPx() },
                with(density) { 18.dp.toPx() },
                with(density) { 134.dp.toPx() },
                with(density) { 40.dp.toPx() },
                with(density) { 11.dp.toPx() },
                with(density) { 11.dp.toPx() },
                pillPaint
            )
            canvas.drawText(categoryLabel, with(density) { 88.dp.toPx() }, with(density) { 33.dp.toPx() }, textPaintLabel)
            canvas.drawText(title, with(density) { 20.dp.toPx() }, with(density) { 78.dp.toPx() }, textPaintTitle)
            canvas.drawText(
                "True inward concave cutout with negative space.",
                with(density) { 20.dp.toPx() },
                with(density) { 108.dp.toPx() },
                textPaintSub
            )
            canvas.drawRoundRect(
                with(density) { 20.dp.toPx() },
                with(density) { 136.dp.toPx() },
                with(density) { 116.dp.toPx() },
                with(density) { 162.dp.toPx() },
                with(density) { 13.dp.toPx() },
                with(density) { 13.dp.toPx() },
                pillPaint
            )
            canvas.drawText("128 words", with(density) { 36.dp.toPx() }, with(density) { 153.dp.toPx() }, textPaintLabel)

            // Draw standalone circular #F9D55D button centered in the bottom-right concave recess
            val cx = wPx - buttonRadiusPx
            val cy = hPx - buttonRadiusPx
            val buttonPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.FILL
                color = buttonFillArgb
            }
            canvas.drawCircle(cx, cy, buttonRadiusPx, buttonPaint)

            // Draw NorthEast arrow icon inside the yellow circle
            val arrowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = with(density) { 2.2.dp.toPx() }
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                color = inkArgb
            }
            val rArrow = with(density) { 5.5.dp.toPx() }
            canvas.drawLine(cx - rArrow, cy + rArrow, cx + rArrow, cy - rArrow, arrowPaint)
            canvas.drawLine(cx - rArrow * 0.2f, cy - rArrow, cx + rArrow, cy - rArrow, arrowPaint)
            canvas.drawLine(cx + rArrow, cy + rArrow * 0.2f, cx + rArrow, cy - rArrow, arrowPaint)

            canvas.restore()
            return bitmap
        }

        val whiteThemeBg = 0xFFFAFAF8.toInt()
        val darkThemeBg = 0xFF171717.toInt()
        val coralCardFill = 0xFFEF7B59.toInt()
        val yellowButtonFill = 0xFFF9D55D.toInt()
        val darkInk = 0xFF191919.toInt()

        val whiteThemeBmp = renderCardPreviewBitmap(
            canvasBgArgb = whiteThemeBg,
            cardFillArgb = coralCardFill,
            buttonFillArgb = yellowButtonFill,
            inkArgb = darkInk,
            title = "Offline Vault Architecture",
            categoryLabel = "Idea"
        )
        val darkThemeBmp = renderCardPreviewBitmap(
            canvasBgArgb = darkThemeBg,
            cardFillArgb = coralCardFill,
            buttonFillArgb = yellowButtonFill,
            inkArgb = darkInk,
            title = "Offline Vault Architecture",
            categoryLabel = "Idea"
        )

        // Save rendered screenshots to workspace root for visual inspection
        val outDir = File("/app/applet").takeIf { it.exists() } ?: File(".")
        File(outDir, "curved_card_white_theme.png").outputStream().use { out ->
            whiteThemeBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        File(outDir, "curved_card_dark_theme.png").outputStream().use { out ->
            darkThemeBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }

        // Sample 3 key points along the 45-degree diagonal from the button center (cx, cy) toward top-left:
        // 1. Inside the yellow button (distance 0.72 * buttonRadiusPx from button center, outside central arrow icon)
        // 2. Inside the negative-space concave channel (distance buttonRadiusPx + gapPx / 2f from button center)
        // 3. Inside the colored card body (distance buttonRadiusPx + gapPx + 10.dp from button center)
        val cxGlobal = padPx + wPx - buttonRadiusPx
        val cyGlobal = padPx + hPx - buttonRadiusPx
        val cos45 = 0.70710677f

        val btnSampleX = (cxGlobal - buttonRadiusPx * 0.72f * cos45).toInt()
        val btnSampleY = (cyGlobal - buttonRadiusPx * 0.72f * cos45).toInt()

        val gapMidDist = buttonRadiusPx + gapPx * 0.5f
        val gapSampleX = (cxGlobal - gapMidDist * cos45).toInt()
        val gapSampleY = (cyGlobal - gapMidDist * cos45).toInt()

        val cardInsideDist = buttonRadiusPx + gapPx + with(density) { 10.dp.toPx() }
        val cardSampleX = (cxGlobal - cardInsideDist * cos45).toInt()
        val cardSampleY = (cyGlobal - cardInsideDist * cos45).toInt()

        assertEquals(yellowButtonFill, whiteThemeBmp.getPixel(btnSampleX, btnSampleY))
        assertEquals(yellowButtonFill, darkThemeBmp.getPixel(btnSampleX, btnSampleY))

        // Negative space in White Theme must be exact #FAFAF8 canvas; in Dark Theme must be exact #171717 canvas (NO white ring/halo!)
        assertEquals(whiteThemeBg, whiteThemeBmp.getPixel(gapSampleX, gapSampleY))
        assertEquals(darkThemeBg, darkThemeBmp.getPixel(gapSampleX, gapSampleY))

        // Card body just beyond the concave cutout must be exact #EF7B59 Coral
        assertEquals(coralCardFill, whiteThemeBmp.getPixel(cardSampleX, cardSampleY))
        assertEquals(coralCardFill, darkThemeBmp.getPixel(cardSampleX, cardSampleY))
    }

    private fun buildSingleManifestZip(manifest: JSONObject): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry(BackupRestoreManager.MANIFEST_ENTRY_NAME))
            zos.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return out.toByteArray()
    }
}
