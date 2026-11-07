package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AttachmentItem
import com.example.data.AttachmentStorageManager
import com.example.data.AttachmentType
import com.example.data.BackupRestoreManager
import com.example.data.BackupRestoreResult
import com.example.data.FolderEntity
import com.example.data.IdeaStatus
import com.example.data.MAIN_METHOD_ROUTE_ID
import com.example.data.MethodRouteEntity
import com.example.data.MethodRouteWithSteps
import com.example.data.MethodStepEntity
import com.example.data.RichBlockType
import com.example.data.RichContentBlock
import com.example.data.SaveItemResult
import com.example.data.UserPreferencesRepository
import com.example.data.VaultCategory
import com.example.data.VaultDatabase
import com.example.data.VaultItemEntity
import com.example.data.VaultJsonSerializers
import com.example.data.VaultRepository
import com.example.ui.components.InlineRichStyle
import com.example.ui.components.ParagraphAlignment
import com.example.ui.components.RichTextColorToken
import com.example.ui.components.RichTextEditorController
import com.example.ui.components.RichTextEditorState
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage02DatabaseAndBackupIntegrityTest {

    private lateinit var context: Context
    private lateinit var db: VaultDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var attachmentStorage: AttachmentStorageManager
    private lateinit var backupManager: BackupRestoreManager
    private lateinit var repository: VaultRepository
    private lateinit var tempPrefsFile: File
    private val disposableDbNames = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempPrefsFile = File(context.cacheDir, "stage02_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { tempPrefsFile }
        )
        prefsRepo = UserPreferencesRepository(dataStore)
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
        repository.saveFaultInjector = null
        repository.savePostStepsFaultInjector = null
        repository.deleteRouteMidTransactionFaultInjector = null
        backupManager.extractionFaultInjector = null
        backupManager.stageFaultInjector = null
        backupManager.exportMidSnapshotHook = null
        db.close()
        tempPrefsFile.delete()
        disposableDbNames.forEach { context.deleteDatabase(it) }
        disposableDbNames.clear()
        attachmentStorage.attachmentsDir.deleteRecursively()
        attachmentStorage.generationsRootDir.deleteRecursively()
        attachmentStorage.activeGenerationFile.delete()
        attachmentStorage.stagingRootDir.deleteRecursively()
        attachmentStorage.rollbackRootDir.deleteRecursively()
    }

    private fun resolveSchemaFile(version: Int): File {
        val candidates = listOf(
            File("schemas/com.example.data.VaultDatabase/$version.json"),
            File("app/schemas/com.example.data.VaultDatabase/$version.json"),
            File("../app/schemas/com.example.data.VaultDatabase/$version.json")
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Could not locate Room schema file for version $version")
    }

    private fun execSchemaCreateSql(sqliteDb: SupportSQLiteDatabase, schemaVersion: Int) {
        val schemaFile = resolveSchemaFile(schemaVersion)
        val root = JSONObject(schemaFile.readText(Charsets.UTF_8))
        val databaseObj = root.getJSONObject("database")
        val identityHash = databaseObj.getString("identityHash")
        val entities = databaseObj.getJSONArray("entities")
        val tableNames = mutableListOf<String>()
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            tableNames.add(tableName)
            val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
            sqliteDb.execSQL(createSql)
            val indices = entity.optJSONArray("indices") ?: JSONArray()
            for (j in 0 until indices.length()) {
                val indexObj = indices.getJSONObject(j)
                val indexSql = indexObj.getString("createSql").replace("\${TABLE_NAME}", tableName)
                sqliteDb.execSQL(indexSql)
            }
        }
        val setupQueries = databaseObj.getJSONArray("setupQueries")
        for (i in 0 until setupQueries.length()) {
            sqliteDb.execSQL(setupQueries.getString(i))
        }
        // Verify identity hash was recorded in room_master_table
        sqliteDb.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(identityHash, cursor.getString(0))
        }
    }

    private fun openRoomDb(dbName: String): VaultDatabase =
        Room.databaseBuilder(context, VaultDatabase::class.java, dbName)
            .addMigrations(
                VaultDatabase.MIGRATION_1_2,
                VaultDatabase.MIGRATION_2_3,
                VaultDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()

    @Test
    fun `verify Room schema JSON artifacts 1 2 and 3 are non-empty valid and match live VaultDatabase`() {
        val schema1 = resolveSchemaFile(1)
        val schema2 = resolveSchemaFile(2)
        val schema3 = resolveSchemaFile(3)
        val schema4 = resolveSchemaFile(4)

        assertEquals("1.json byte size must match", 11_510L, schema1.length())
        assertEquals("2.json byte size must match", 12_137L, schema2.length())
        assertEquals("3.json byte size must match", 15_864L, schema3.length())
        assertTrue("4.json must be non-empty", schema4.length() > 18_000L)

        assertEquals(
            "c5f940a89c71654559899f385592aa77f665489e2f45691c39ee0c698ea676be",
            attachmentStorage.computeSha256(schema1)
        )
        assertEquals(
            "5a36922aa68663d78e58d4288d5c5f39fcb2ad1d3cfbe1e3314acb58dcc888c2",
            attachmentStorage.computeSha256(schema2)
        )
        assertEquals(
            "0e163c362b13139d9675e7776963deab2ae14ca51cfddb3b9c8c2db4ba63615e",
            attachmentStorage.computeSha256(schema3)
        )

        val json1 = JSONObject(schema1.readText(Charsets.UTF_8)).getJSONObject("database")
        val json2 = JSONObject(schema2.readText(Charsets.UTF_8)).getJSONObject("database")
        val json3 = JSONObject(schema3.readText(Charsets.UTF_8)).getJSONObject("database")
        val json4 = JSONObject(schema4.readText(Charsets.UTF_8)).getJSONObject("database")

        assertEquals(1, json1.getInt("version"))
        assertEquals("3f9a1c5e748b2d0a19c34f6e8b52a10d", json1.getString("identityHash"))

        assertEquals(2, json2.getInt("version"))
        assertEquals("6bad888f206cf4ed8d6a348a06379dab", json2.getString("identityHash"))

        assertEquals(3, json3.getInt("version"))
        assertEquals("c78a21f1b481fe51a036fd5d1cc3115d", json3.getString("identityHash"))

        assertEquals(4, json4.getInt("version"))
        assertEquals("dafd529f2a1f02817d55e9276d1887a6", json4.getString("identityHash"))

        // Verify v3 entities match exact expected 6 tables
        val entities3 = json3.getJSONArray("entities")
        val tableNames3 = (0 until entities3.length()).map {
            entities3.getJSONObject(it).getString("tableName")
        }.toSet()
        assertEquals(
            setOf(
                "folders",
                "vault_items",
                "method_routes",
                "method_steps",
                "method_follow_runs",
                "method_route_follow_runs"
            ),
            tableNames3
        )

        // Verify v4 entities match exact expected 7 tables (including custom_types)
        val entities4 = json4.getJSONArray("entities")
        val tableNames4 = (0 until entities4.length()).map {
            entities4.getJSONObject(it).getString("tableName")
        }.toSet()
        assertEquals(
            setOf(
                "folders",
                "custom_types",
                "vault_items",
                "method_routes",
                "method_steps",
                "method_follow_runs",
                "method_route_follow_runs"
            ),
            tableNames4
        )

        // Open a real file-backed Room v4 database and verify room_master_table identityHash matches 4.json
        val dbName = "schema_v4_verify_${System.nanoTime()}.db"
        disposableDbNames.add(dbName)
        val liveV4Db = openRoomDb(dbName)
        liveV4Db.openHelper.writableDatabase.query(
            "SELECT identity_hash FROM room_master_table WHERE id = 42"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("dafd529f2a1f02817d55e9276d1887a6", cursor.getString(0))
        }
        liveV4Db.close()
    }

    @Test
    fun `verify destructive migration fallback is NOT enabled on VaultDatabase`() {
        val dbName = "no_destructive_fallback_${System.nanoTime()}.db"
        disposableDbNames.add(dbName)

        // Create a SQLite database at unknown version 99 with a sentinel table
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(99) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE `user_precious_sentinel` (`id` TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("INSERT INTO `user_precious_sentinel` (`id`) VALUES ('keep_me')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()

        val roomDb = openRoomDb(dbName)
        var threwMissingMigration = false
        try {
            roomDb.openHelper.writableDatabase
        } catch (e: IllegalStateException) {
            threwMissingMigration = true
        } finally {
            roomDb.close()
        }
        assertTrue("VaultDatabase must throw IllegalStateException instead of destructively wiping data", threwMissingMigration)
    }

    @Test
    fun `verify migrations 1 to 2 and 2 to 3 and 1 to 3 on empty databases succeed and validate schema`() = runBlocking {
        // 1. Empty 1 -> 2
        val empty12Name = "empty_1_to_2_${System.nanoTime()}.db"
        disposableDbNames.add(empty12Name)
        val config12 = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(empty12Name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    execSchemaCreateSql(db, 1)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper12 = FrameworkSQLiteOpenHelperFactory().create(config12)
        helper12.writableDatabase.use { sqliteDb ->
            VaultDatabase.MIGRATION_1_2.migrate(sqliteDb)
            // Verify v2 indexes exist on empty migrated DB
            sqliteDb.query("PRAGMA index_list(`folders`)").use { cursor ->
                val indexNames = mutableSetOf<String>()
                while (cursor.moveToNext()) indexNames.add(cursor.getString(1))
                assertTrue(indexNames.contains("index_folders_category"))
                assertTrue(indexNames.contains("index_folders_updatedAt"))
            }
            sqliteDb.query("PRAGMA index_list(`vault_items`)").use { cursor ->
                val indexNames = mutableSetOf<String>()
                while (cursor.moveToNext()) indexNames.add(cursor.getString(1))
                assertTrue(indexNames.contains("index_vault_items_createdAt"))
            }
        }
        helper12.close()

        // 2. Empty 2 -> 3 (validated by Room's strict v3 onValidateSchema)
        val empty23Name = "empty_2_to_3_${System.nanoTime()}.db"
        disposableDbNames.add(empty23Name)
        val config23 = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(empty23Name)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    execSchemaCreateSql(db, 2)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config23).writableDatabase.close()

        val upgraded23 = openRoomDb(empty23Name)
        assertTrue(upgraded23.vaultDao().getAllItemsSnapshot().isEmpty())
        assertTrue(upgraded23.vaultDao().getAllRoutesSnapshot().isEmpty())
        upgraded23.close()

        // 3. Empty 1 -> 3 (validated by Room's strict v3 onValidateSchema)
        val empty13Name = "empty_1_to_3_${System.nanoTime()}.db"
        disposableDbNames.add(empty13Name)
        val config13 = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(empty13Name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    execSchemaCreateSql(db, 1)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config13).writableDatabase.close()

        val upgraded13 = openRoomDb(empty13Name)
        assertTrue(upgraded13.vaultDao().getAllItemsSnapshot().isEmpty())
        assertTrue(upgraded13.vaultDao().getAllFoldersSnapshot().isEmpty())
        upgraded13.close()
    }

    @Test
    fun `verify populated migrations 1 to 2 and 2 to 3 and 1 to 3 preserve all 15 record types losslessly`() = runBlocking {
        // Prepare rich-text serialized content with inline styles, font size, font color, and alignment
        var richState = RichTextEditorState.fromStoredContent("Rich Note Body with Tamil தமிழ் and 2 * 3 = 6")
        richState = richState.copy(textFieldValue = richState.textFieldValue.copy(selection = TextRange(0, 14)))
        richState = RichTextEditorController.toggleInlineStyle(richState, InlineRichStyle.BOLD)
        richState = RichTextEditorController.toggleInlineStyle(richState, InlineRichStyle.HIGHLIGHT)
        richState = RichTextEditorController.applyFontSize(richState, 20)
        richState = RichTextEditorController.applyFontColor(richState, RichTextColorToken.CORAL)
        richState = RichTextEditorController.applyParagraphAlignment(richState, ParagraphAlignment.CENTER)
        val richMarkdown = richState.toSerializedMarkdown()

        val richBlocksJson = VaultJsonSerializers.encodeRichBlocks(
            listOf(
                RichContentBlock(id = "rb_check", type = RichBlockType.CHECKLIST, title = "Audit DB", content = "Check v3", checked = true),
                RichContentBlock(id = "rb_code", type = RichBlockType.CODE_BLOCK, title = "SQL", content = "SELECT * FROM vault_items;")
            )
        )
        val tagsJson = VaultJsonSerializers.encodeStringList(listOf("migration", "integrity", "v3"))
        val attachmentsJson = VaultJsonSerializers.encodeAttachments(
            listOf(
                AttachmentItem(
                    id = "att_img_1",
                    type = AttachmentType.IMAGE,
                    name = "arch_diagram.png",
                    uriOrUrl = "/data/user/0/com.example/files/vault_attachments/arch_diagram.png",
                    mimeType = "image/png",
                    sizeBytes = 2048L
                ),
                AttachmentItem(
                    id = "att_link_1",
                    type = AttachmentType.LINK,
                    name = "Room Docs",
                    uriOrUrl = "https://developer.android.com/training/data-storage/room"
                )
            )
        )
        val exactPromptText = "SYSTEM: Keep trailing spaces and newlines\n{\n  \"key\": \"{VALUE}\"\n}   \n\n"

        fun seedAllRecordTypes(sqliteDb: SupportSQLiteDatabase) {
            // Folders
            sqliteDb.execSQL(
                "INSERT INTO `folders` (`id`, `name`, `category`, `colorHex`, `createdAt`, `updatedAt`) VALUES " +
                    "('folder_ideas', 'Product Ideas', 'IDEA', '#EF7B59', 1000, 1500), " +
                    "('folder_methods', 'Engineering SOPs', 'METHOD', '#A7D678', 1100, 1600)"
            )
            // 1. IDEA (pinned, favorite, in folder, with tags & attachments)
            sqliteDb.execSQL(
                "INSERT INTO `vault_items` (`id`, `title`, `description`, `content`, `category`, `folderId`, `tagsJson`, `richBlocksJson`, `attachmentsJson`, `ideaStatus`, `platform`, `estimatedTime`, `notes`, `isFavorite`, `isPinned`, `isArchived`, `isTrashed`, `createdAt`, `updatedAt`) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "item_idea_1", "Offline Sync Engine", "CRDT + SQLite", richMarkdown,
                    VaultCategory.IDEA.id, "folder_ideas", tagsJson, richBlocksJson, attachmentsJson,
                    IdeaStatus.DEVELOPING.id, "Android", "2 weeks", "High priority", 1, 1, 0, 0, 2000L, 2500L
                )
            )
            // 2. METHOD (in folder, with steps and follow progress)
            sqliteDb.execSQL(
                "INSERT INTO `vault_items` (`id`, `title`, `description`, `content`, `category`, `folderId`, `tagsJson`, `richBlocksJson`, `attachmentsJson`, `ideaStatus`, `platform`, `estimatedTime`, `notes`, `isFavorite`, `isPinned`, `isArchived`, `isTrashed`, `createdAt`, `updatedAt`) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "item_method_1", "Database Migration Checklist", "Zero data loss", "Step-by-step protocol",
                    VaultCategory.METHOD.id, "folder_methods", tagsJson, "[]", "[]",
                    IdeaStatus.COMPLETED.id, "Room SQLite", "30 mins", "Verified", 1, 0, 0, 0, 3000L, 3500L
                )
            )
            // 3. PROMPT (exact formatting, favorite)
            sqliteDb.execSQL(
                "INSERT INTO `vault_items` (`id`, `title`, `description`, `content`, `category`, `folderId`, `tagsJson`, `richBlocksJson`, `attachmentsJson`, `ideaStatus`, `platform`, `estimatedTime`, `notes`, `isFavorite`, `isPinned`, `isArchived`, `isTrashed`, `createdAt`, `updatedAt`) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "item_prompt_1", "Strict JSON Audit Prompt", "Preserves whitespace", exactPromptText,
                    VaultCategory.PROMPT.id, null, tagsJson, "[]", "[]",
                    IdeaStatus.NEW.id, "Google AI Studio", "", "Do not trim", 1, 0, 0, 0, 4000L, 4500L
                )
            )
            // 4. NOTES (KNOWLEDGE, REFERENCE, INBOX)
            sqliteDb.execSQL(
                "INSERT INTO `vault_items` (`id`, `title`, `description`, `content`, `category`, `folderId`, `tagsJson`, `richBlocksJson`, `attachmentsJson`, `ideaStatus`, `platform`, `estimatedTime`, `notes`, `isFavorite`, `isPinned`, `isArchived`, `isTrashed`, `createdAt`, `updatedAt`) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "item_note_kb", "Kotlin Coroutines Internals", "Structured concurrency", richMarkdown,
                    VaultCategory.KNOWLEDGE.id, null, tagsJson, richBlocksJson, "[]",
                    IdeaStatus.NEW.id, "", "", "", 0, 0, 0, 0, 5000L, 5500L
                )
            )
            sqliteDb.execSQL(
                "INSERT INTO `vault_items` (`id`, `title`, `description`, `content`, `category`, `folderId`, `tagsJson`, `richBlocksJson`, `attachmentsJson`, `ideaStatus`, `platform`, `estimatedTime`, `notes`, `isFavorite`, `isPinned`, `isArchived`, `isTrashed`, `createdAt`, `updatedAt`) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "item_note_ref", "Material 3 Color Tokens", "Design spec", "Surface & accent values",
                    VaultCategory.REFERENCE.id, null, "[]", "[]", attachmentsJson,
                    IdeaStatus.NEW.id, "", "", "", 0, 0, 0, 0, 5100L, 5600L
                )
            )
            sqliteDb.execSQL(
                "INSERT INTO `vault_items` (`id`, `title`, `description`, `content`, `category`, `folderId`, `tagsJson`, `richBlocksJson`, `attachmentsJson`, `ideaStatus`, `platform`, `estimatedTime`, `notes`, `isFavorite`, `isPinned`, `isArchived`, `isTrashed`, `createdAt`, `updatedAt`) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "item_note_inbox", "Quick Inbox Capture", "Untriaged thought", "Check WAL checkpointing",
                    VaultCategory.INBOX.id, null, "[]", "[]", "[]",
                    IdeaStatus.NEW.id, "", "", "", 0, 0, 0, 0, 5200L, 5700L
                )
            )
            // 5. ARCHIVED record
            sqliteDb.execSQL(
                "INSERT INTO `vault_items` (`id`, `title`, `description`, `content`, `category`, `folderId`, `tagsJson`, `richBlocksJson`, `attachmentsJson`, `ideaStatus`, `platform`, `estimatedTime`, `notes`, `isFavorite`, `isPinned`, `isArchived`, `isTrashed`, `createdAt`, `updatedAt`) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "item_archived_1", "Archived 2025 Roadmap", "Completed Q4", "All milestones shipped",
                    VaultCategory.IDEA.id, "folder_ideas", "[]", "[]", "[]",
                    IdeaStatus.COMPLETED.id, "", "", "", 0, 0, 1, 0, 6000L, 6500L
                )
            )
            // 6. TRASHED record
            sqliteDb.execSQL(
                "INSERT INTO `vault_items` (`id`, `title`, `description`, `content`, `category`, `folderId`, `tagsJson`, `richBlocksJson`, `attachmentsJson`, `ideaStatus`, `platform`, `estimatedTime`, `notes`, `isFavorite`, `isPinned`, `isArchived`, `isTrashed`, `createdAt`, `updatedAt`) VALUES " +
                    "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "item_trashed_1", "Trashed Scratchpad", "Pending deletion", "Can be restored from trash",
                    VaultCategory.KNOWLEDGE.id, null, "[]", "[]", "[]",
                    IdeaStatus.NEW.id, "", "", "", 0, 0, 0, 1, 7000L, 7500L
                )
            )
            // 7. Method steps
            sqliteDb.execSQL(
                "INSERT INTO `method_steps` (`id`, `itemId`, `stepOrder`, `title`, `description`, `notes`, `linksJson`, `imagesJson`) VALUES " +
                    "('step_m1_1', 'item_method_1', 1, 'Export Room Schema', 'Verify 3.json is non-empty', 'Critical', '[\"https://developer.android.com\"]', '[\"/data/step1.png\"]'), " +
                    "('step_m1_2', 'item_method_1', 2, 'Run Migration Tests', 'Test 1->2, 2->3, and 1->3', 'Must pass', '[]', '[]'), " +
                    "('step_m1_3', 'item_method_1', 3, 'Verify Follow Progress', 'Ensure completed steps survive', '', '[]', '[]')"
            )
            // 8. Method follow run
            sqliteDb.execSQL(
                "INSERT INTO `method_follow_runs` (`itemId`, `completedStepIdsJson`, `startedAt`, `updatedAt`) VALUES " +
                    "('item_method_1', '[\"step_m1_1\",\"step_m1_2\"]', 8000, 8500)"
            )
        }

        suspend fun verifyAllMigratedRecords(upgradedDb: VaultDatabase) {
            val dao = upgradedDb.vaultDao()
            val folders = dao.getAllFoldersSnapshot().associateBy { it.id }
            assertEquals(2, folders.size)
            assertEquals("Product Ideas", folders["folder_ideas"]?.name)
            assertEquals("Engineering SOPs", folders["folder_methods"]?.name)

            val allItems = dao.getAllItemsSnapshot().associateBy { it.id }
            assertEquals(8, allItems.size)

            // Verify Idea + Rich Text + Tags + Attachments + Favorite + Pin + Folder
            val idea = allItems.getValue("item_idea_1")
            assertEquals("Offline Sync Engine", idea.title)
            assertEquals("folder_ideas", idea.folderId)
            assertTrue(idea.isFavorite)
            assertTrue(idea.isPinned)
            assertFalse(idea.isArchived)
            assertFalse(idea.isTrashed)
            assertEquals(listOf("migration", "integrity", "v3"), VaultJsonSerializers.decodeStringList(idea.tagsJson))
            assertEquals(2, VaultJsonSerializers.decodeRichBlocks(idea.richBlocksJson).size)
            assertEquals(2, VaultJsonSerializers.decodeAttachments(idea.attachmentsJson).size)

            val decodedRich = RichTextEditorState.fromStoredContent(idea.content)
            assertEquals("Rich Note Body with Tamil தமிழ் and 2 * 3 = 6", decodedRich.textFieldValue.text)
            assertEquals(ParagraphAlignment.CENTER, decodedRich.lineAlignments[0])
            assertTrue(decodedRich.spans.any { it.style == InlineRichStyle.BOLD })
            assertTrue(decodedRich.spans.any { it.style == InlineRichStyle.HIGHLIGHT })
            assertTrue(decodedRich.fontSizeSpans.any { it.sizeSp == 20 })
            assertTrue(decodedRich.fontColorSpans.any { it.colorToken == RichTextColorToken.CORAL })

            // Verify Exact Prompt
            val prompt = allItems.getValue("item_prompt_1")
            assertEquals(exactPromptText, prompt.content)
            assertTrue(prompt.isFavorite)

            // Verify Notes (KNOWLEDGE, REFERENCE, INBOX)
            assertEquals(VaultCategory.KNOWLEDGE.id, allItems.getValue("item_note_kb").category)
            assertEquals(VaultCategory.REFERENCE.id, allItems.getValue("item_note_ref").category)
            assertEquals(VaultCategory.INBOX.id, allItems.getValue("item_note_inbox").category)

            // Verify Archived & Trashed
            val archivedList = dao.observeArchivedItems().first()
            assertEquals(1, archivedList.size)
            assertEquals("item_archived_1", archivedList.single().id)

            val trashedList = dao.observeTrashedItems().first()
            assertEquals(1, trashedList.size)
            assertEquals("item_trashed_1", trashedList.single().id)

            // Verify Method steps & Main route follow progress
            val steps = dao.getStepsForItemSnapshot("item_method_1")
            assertEquals(3, steps.size)
            assertEquals(listOf("step_m1_1", "step_m1_2", "step_m1_3"), steps.map { it.id })
            assertTrue(steps.all { it.routeId == MAIN_METHOD_ROUTE_ID })
            assertEquals(listOf("/data/step1.png"), VaultJsonSerializers.decodeStringList(steps[0].imagesJson))

            val routeRun = dao.getRouteFollowRun("item_method_1", MAIN_METHOD_ROUTE_ID)
            assertNotNull(routeRun)
            assertEquals(
                listOf("step_m1_1", "step_m1_2"),
                VaultJsonSerializers.decodeStringList(routeRun!!.completedStepIdsJson)
            )
        }

        // Path A: Populated 1 -> 2 -> 3 (step-by-step)
        val db1To2To3Name = "populated_1_to_2_to_3_${System.nanoTime()}.db"
        disposableDbNames.add(db1To2To3Name)
        val configA = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(db1To2To3Name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    execSchemaCreateSql(db, 1)
                    seedAllRecordTypes(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helperA = FrameworkSQLiteOpenHelperFactory().create(configA)
        // First migrate 1 -> 2 directly and verify v2 state
        helperA.writableDatabase.use { sqliteDb ->
            VaultDatabase.MIGRATION_1_2.migrate(sqliteDb)
            sqliteDb.version = 2
            sqliteDb.query("SELECT COUNT(*) FROM `vault_items`").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(8, c.getInt(0))
            }
        }
        helperA.close()
        // Now open v2 DB with Room to run 2 -> 3 and validate v3 schema
        val roomDbA = openRoomDb(db1To2To3Name)
        verifyAllMigratedRecords(roomDbA)
        roomDbA.close()

        // Path B: Populated 1 -> 3 directly via Room
        val db1To3Name = "populated_1_to_3_${System.nanoTime()}.db"
        disposableDbNames.add(db1To3Name)
        val configB = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(db1To3Name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    execSchemaCreateSql(db, 1)
                    seedAllRecordTypes(db)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(configB).writableDatabase.close()

        val roomDbB = openRoomDb(db1To3Name)
        verifyAllMigratedRecords(roomDbB)
        roomDbB.close()
    }

    @Test
    fun `verify Alternative Method integrity route deletion step pruning duplication primary keys and zero orphans`() = runBlocking {
        // Create step image files to verify unreferenced step image cleanup on route deletion
        val mainStepImg = File(attachmentStorage.attachmentsDir, "main_step_img.png").apply {
            parentFile?.mkdirs()
            writeBytes("MAIN_IMG".toByteArray())
        }
        val alt1StepImg = File(attachmentStorage.attachmentsDir, "alt1_step_img.png").apply {
            writeBytes("ALT1_IMG".toByteArray())
        }
        val alt2StepImg = File(attachmentStorage.attachmentsDir, "alt2_step_img.png").apply {
            writeBytes("ALT2_IMG".toByteArray())
        }

        val methodId = "method_integrity_1"
        val altRoute1Id = "route_express_cli"
        val altRoute2Id = "route_offline_bundle"

        val methodItem = VaultItemEntity(
            id = methodId,
            title = "Production Deployment SOP",
            description = "Primary and alternative release tracks",
            category = VaultCategory.METHOD.id
        )
        val mainSteps = listOf(
            MethodStepEntity(
                id = "m_step_1",
                itemId = methodId,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 1,
                title = "Main Step 1",
                description = "Compile & test",
                imagesJson = VaultJsonSerializers.encodeStringList(listOf(mainStepImg.absolutePath))
            ),
            MethodStepEntity(
                id = "m_step_2",
                itemId = methodId,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 2,
                title = "Main Step 2",
                description = "Sign APK"
            ),
            MethodStepEntity(
                id = "m_step_3",
                itemId = methodId,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 3,
                title = "Main Step 3",
                description = "Publish release"
            )
        )
        val altRoutes = listOf(
            MethodRouteWithSteps(
                route = MethodRouteEntity(
                    id = altRoute1Id,
                    itemId = methodId,
                    name = "Express CLI Track",
                    description = "Automated terminal pipeline",
                    routeOrder = 1,
                    isMain = false
                ),
                steps = listOf(
                    MethodStepEntity(
                        id = "a1_step_1",
                        itemId = methodId,
                        routeId = altRoute1Id,
                        stepOrder = 1,
                        title = "CLI Step 1",
                        imagesJson = VaultJsonSerializers.encodeStringList(listOf(alt1StepImg.absolutePath))
                    ),
                    MethodStepEntity(
                        id = "a1_step_2",
                        itemId = methodId,
                        routeId = altRoute1Id,
                        stepOrder = 2,
                        title = "CLI Step 2"
                    )
                )
            ),
            MethodRouteWithSteps(
                route = MethodRouteEntity(
                    id = altRoute2Id,
                    itemId = methodId,
                    name = "Offline Airgap Track",
                    description = "USB signing key workflow",
                    routeOrder = 2,
                    isMain = false
                ),
                steps = listOf(
                    MethodStepEntity(
                        id = "a2_step_1",
                        itemId = methodId,
                        routeId = altRoute2Id,
                        stepOrder = 1,
                        title = "Airgap Step 1",
                        imagesJson = VaultJsonSerializers.encodeStringList(listOf(alt2StepImg.absolutePath))
                    ),
                    MethodStepEntity(
                        id = "a2_step_2",
                        itemId = methodId,
                        routeId = altRoute2Id,
                        stepOrder = 2,
                        title = "Airgap Step 2"
                    )
                )
            )
        )

        val saveRes = repository.saveVaultItemWithResult(
            item = methodItem,
            steps = mainSteps,
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false,
            mainRouteName = "Main Release Method",
            mainRouteDescription = "Standard Play Console track",
            alternativeRoutes = altRoutes
        )
        assertTrue(saveRes is SaveItemResult.Success)

        // 1. Track independent Follow progress on all 3 routes
        repository.toggleFollowStep(methodId, "m_step_1", MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(methodId, "m_step_3", MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(methodId, "a1_step_1", altRoute1Id)
        repository.toggleFollowStep(methodId, "a2_step_2", altRoute2Id)

        var loaded = repository.getItemWithDetailsById(methodId)!!
        assertEquals(setOf("m_step_1", "m_step_3"), loaded.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }.completedStepIds)
        assertEquals(setOf("a1_step_1"), loaded.effectiveMethodRoutes.first { it.id == altRoute1Id }.completedStepIds)
        assertEquals(setOf("a2_step_2"), loaded.effectiveMethodRoutes.first { it.id == altRoute2Id }.completedStepIds)

        // 2. Duplicate the Method TWICE -> verify both copies preserve routes & steps with independent IDs
        // and NEVER collide on method_routes primary key!
        val copy1Id = repository.duplicateVaultItem(loaded)
        val copy2Id = repository.duplicateVaultItem(loaded)
        assertNotEquals(copy1Id, copy2Id)

        val copy1 = repository.getItemWithDetailsById(copy1Id)!!
        val copy2 = repository.getItemWithDetailsById(copy2Id)!!
        val copy1MainRoute = copy1.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }
        val copy2MainRoute = copy2.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }
        assertEquals("Main Release Method", copy1MainRoute.name)
        assertEquals("Standard Play Console track", copy1MainRoute.description)
        assertEquals("Main Release Method", copy2MainRoute.name)
        assertEquals("Standard Play Console track", copy2MainRoute.description)
        assertEquals(3, copy1.effectiveMethodRoutes.size)
        assertEquals(3, copy2.effectiveMethodRoutes.size)

        // Ensure all route IDs and step IDs in SQLite across original, copy1, and copy2 are globally unique
        val allDbRoutes = db.vaultDao().getAllRoutesSnapshot()
        assertEquals(9, allDbRoutes.size) // 3 routes * 3 items
        assertEquals(9, allDbRoutes.map { it.id }.toSet().size)

        val allDbSteps = db.vaultDao().getAllStepsSnapshot()
        assertEquals(21, allDbSteps.size) // 7 steps * 3 items
        assertEquals(21, allDbSteps.map { it.id }.toSet().size)

        // Remove the two copies cleanly so we can test route deletion & image cleanup on the original
        repository.permanentlyDelete(copy1Id)
        repository.permanentlyDelete(copy2Id)

        // 3. Delete alternative route 1 via deleteAlternativeRoute -> parent Method & Main Method remain intact,
        // altRoute1 steps & follow run are removed, and alt1StepImg is deleted from disk!
        assertFalse("Must refuse deleting Main route", repository.deleteAlternativeRoute(methodId, MAIN_METHOD_ROUTE_ID))
        assertTrue(repository.deleteAlternativeRoute(methodId, altRoute1Id))

        loaded = repository.getItemWithDetailsById(methodId)!!
        assertEquals("Production Deployment SOP", loaded.entity.title)
        assertEquals(2, loaded.effectiveMethodRoutes.size)
        assertEquals(listOf(MAIN_METHOD_ROUTE_ID, altRoute2Id), loaded.effectiveMethodRoutes.map { it.id })
        assertEquals(setOf("m_step_1", "m_step_3"), loaded.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }.completedStepIds)
        assertEquals(setOf("a2_step_2"), loaded.effectiveMethodRoutes.first { it.id == altRoute2Id }.completedStepIds)

        // Verify zero orphaned steps or progress rows for deleted altRoute1
        assertTrue(db.vaultDao().getStepsForRouteSnapshot(methodId, altRoute1Id).isEmpty())
        assertNull(db.vaultDao().getRouteFollowRun(methodId, altRoute1Id))
        assertFalse("Unreferenced step image for deleted alternative route must be cleaned up", alt1StepImg.exists())
        assertTrue("Main step image must still exist", mainStepImg.exists())
        assertTrue("Remaining alt route step image must still exist", alt2StepImg.exists())

        // 4. Edit Method via saveVaultItemWithResult: remove m_step_3 from Main Method and remove altRoute2
        // -> m_step_3 must be pruned from Main Method's completedStepIdsJson, and altRoute2's follow run must be deleted!
        val updatedMainSteps = mainSteps.take(2) // m_step_1 and m_step_2 only (m_step_3 removed)
        val editRes = repository.saveVaultItemWithResult(
            item = loaded.entity,
            steps = updatedMainSteps,
            expectedPreviousUpdatedAt = loaded.entity.updatedAt,
            isEditingExisting = true,
            mainRouteName = "Main Release Method",
            mainRouteDescription = "Standard Play Console track",
            alternativeRoutes = emptyList() // altRoute2 removed
        )
        assertTrue(editRes is SaveItemResult.Success)

        loaded = repository.getItemWithDetailsById(methodId)!!
        assertFalse(loaded.hasAlternativeMethods)
        assertEquals(listOf("m_step_1", "m_step_2"), loaded.steps.map { it.id })
        // m_step_3 was pruned from completedStepIds!
        assertEquals(setOf("m_step_1"), loaded.completedFollowStepIds)
        assertEquals(
            setOf("m_step_1"),
            loaded.effectiveMethodRoutes.single().completedStepIds
        )
        // Verify zero orphaned route follow runs or steps in SQLite
        assertNull(db.vaultDao().getRouteFollowRun(methodId, altRoute2Id))
        assertEquals(1, db.vaultDao().getRouteFollowRunsForItemSnapshot(methodId).size)
        assertEquals(2, db.vaultDao().getAllStepsSnapshot().size)
        assertFalse("Removed alt2 step image must be cleaned up", alt2StepImg.exists())
    }

    @Test
    fun `verify v1 and v2 backup archives restore cleanly onto v3 database and preserve follow progress`() = runBlocking {
        // 1. Build a legacy v1 backup archive (no attachmentsManifest, no routes, no routeFollowRuns)
        val v1AttBytes = "LEGACY_V1_ATTACHMENT_BYTES".toByteArray()
        val v1AttJson = VaultJsonSerializers.encodeAttachments(
            listOf(
                AttachmentItem(
                    id = "v1_att_1",
                    type = AttachmentType.IMAGE,
                    name = "v1_sketch.png",
                    uriOrUrl = "/old_device/files/vault_attachments/v1_sketch.png",
                    mimeType = "image/png",
                    sizeBytes = v1AttBytes.size.toLong()
                )
            )
        )
        val v1Manifest = JSONObject().apply {
            put("magic", BackupRestoreManager.BACKUP_MAGIC)
            put("schemaVersion", 1)
            put("exportedAt", 1_700_000_000_000L)
            put("preferences", JSONObject().apply {
                put("userName", "LegacyV1User")
                put("themeMode", "LIGHT")
            })
            put(
                "folders",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "v1_folder")
                        put("name", "V1 Archive Folder")
                        put("category", "METHOD")
                        put("colorHex", "#A7D678")
                        put("createdAt", 100L)
                        put("updatedAt", 200L)
                    }
                )
            )
            put(
                "items",
                JSONArray().put(
                    JSONObject().apply {
                        put("id", "v1_method")
                        put("title", "Legacy V1 Method")
                        put("description", "From schema v1 backup")
                        put("content", "Body content")
                        put("category", "METHOD")
                        put("folderId", "v1_folder")
                        put("tagsJson", "[\"v1\",\"legacy\"]")
                        put("richBlocksJson", "[]")
                        put("attachmentsJson", v1AttJson)
                        put("isFavorite", true)
                        put("isPinned", true)
                        put("isArchived", false)
                        put("isTrashed", false)
                        put("createdAt", 300L)
                        put("updatedAt", 400L)
                    }
                )
            )
            put(
                "steps",
                JSONArray()
                    .put(
                        JSONObject().apply {
                            put("id", "v1_step_1")
                            put("itemId", "v1_method")
                            put("stepOrder", 1)
                            put("title", "V1 Step One")
                            put("description", "First step")
                        }
                    )
                    .put(
                        JSONObject().apply {
                            put("id", "v1_step_2")
                            put("itemId", "v1_method")
                            put("stepOrder", 2)
                            put("title", "V1 Step Two")
                            put("description", "Second step")
                        }
                    )
            )
            put(
                "followRuns",
                JSONArray().put(
                    JSONObject().apply {
                        put("itemId", "v1_method")
                        put("completedStepIdsJson", "[\"v1_step_1\"]")
                        put("startedAt", 500L)
                        put("updatedAt", 600L)
                    }
                )
            )
        }

        val v1ZipBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry(BackupRestoreManager.MANIFEST_ENTRY_NAME))
                zos.write(v1Manifest.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("attachments/v1_sketch.png"))
                zos.write(v1AttBytes)
                zos.closeEntry()
            }
        }.toByteArray()

        val v1Payload = repository.validateBackup(ByteArrayInputStream(v1ZipBytes)).getOrThrow()
        assertEquals(1, v1Payload.schemaVersion)
        val v1Apply = repository.applyBackup(v1Payload)
        assertTrue(v1Apply is BackupRestoreResult.Success)

        val restoredV1 = repository.getItemWithDetailsById("v1_method")!!
        assertEquals("Legacy V1 Method", restoredV1.entity.title)
        assertEquals("V1 Archive Folder", restoredV1.folderName)
        assertEquals(2, restoredV1.steps.size)
        assertTrue(restoredV1.steps.all { it.routeId == MAIN_METHOD_ROUTE_ID })
        assertEquals(setOf("v1_step_1"), restoredV1.completedFollowStepIds)
        assertEquals(
            setOf("v1_step_1"),
            restoredV1.effectiveMethodRoutes.single().completedStepIds
        )
        // Verify method_route_follow_runs was populated in SQLite for MAIN_METHOD_ROUTE_ID
        val v1RouteRun = db.vaultDao().getRouteFollowRun("v1_method", MAIN_METHOD_ROUTE_ID)
        assertNotNull(v1RouteRun)
        assertEquals("[\"v1_step_1\"]", v1RouteRun!!.completedStepIdsJson)
        assertTrue(File(attachmentStorage.attachmentsDir, "v1_sketch.png").exists())

        // Verify staging and rollback directories are completely clean after restore
        assertTrue(attachmentStorage.stagingRootDir.listFiles()?.toList().orEmpty().isEmpty())
        assertTrue(attachmentStorage.rollbackRootDir.listFiles()?.toList().orEmpty().isEmpty())
    }

    @Test
    fun `verify full v3 backup export manifest structure sha256 checksums and zero bytes written on missing attachment`() = runBlocking {
        // 1. Seed a rich v3 vault with item attachment + step image + alternative routes + archived + trashed items
        val itemAttFile = File(attachmentStorage.attachmentsDir, "spec_v3.pdf").apply {
            parentFile?.mkdirs()
            writeBytes("PDF_V3_CONTENT_999".toByteArray())
        }
        val stepImgFile = File(attachmentStorage.attachmentsDir, "step_v3.png").apply {
            writeBytes("PNG_STEP_V3_888".toByteArray())
        }

        val folder = repository.createFolder("V3 Architecture", VaultCategory.METHOD, "#EF7B59")
        val methodEntity = VaultItemEntity(
            id = "v3_full_method",
            title = "Complete V3 Method",
            description = "With main and alt routes",
            content = "Rich **markdown** body",
            category = VaultCategory.METHOD.id,
            folderId = folder.id,
            tagsJson = VaultJsonSerializers.encodeStringList(listOf("v3", "backup")),
            attachmentsJson = VaultJsonSerializers.encodeAttachments(
                listOf(
                    AttachmentItem(
                        id = "att_pdf_v3",
                        type = AttachmentType.PDF,
                        name = "spec_v3.pdf",
                        uriOrUrl = itemAttFile.absolutePath,
                        mimeType = "application/pdf",
                        sizeBytes = itemAttFile.length()
                    )
                )
            ),
            isFavorite = true,
            isPinned = true
        )
        val mainSteps = listOf(
            MethodStepEntity(
                id = "v3_ms1",
                itemId = methodEntity.id,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 1,
                title = "Main V3 Step",
                imagesJson = VaultJsonSerializers.encodeStringList(listOf(stepImgFile.absolutePath))
            )
        )
        val altRoute = MethodRouteWithSteps(
            route = MethodRouteEntity(
                id = "v3_alt_route",
                itemId = methodEntity.id,
                name = "V3 Alt Route",
                description = "Secondary track",
                routeOrder = 1,
                isMain = false
            ),
            steps = listOf(
                MethodStepEntity(
                    id = "v3_as1",
                    itemId = methodEntity.id,
                    routeId = "v3_alt_route",
                    stepOrder = 1,
                    title = "Alt V3 Step"
                )
            )
        )
        assertTrue(
            repository.saveVaultItem(
                item = methodEntity,
                steps = mainSteps,
                mainRouteName = "Primary Track",
                mainRouteDescription = "Main track desc",
                alternativeRoutes = listOf(altRoute)
            )
        )
        repository.toggleFollowStep(methodEntity.id, "v3_ms1", MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(methodEntity.id, "v3_as1", "v3_alt_route")

        // Also create an Archived item and a Trashed item
        val archivedItem = VaultItemEntity(
            id = "v3_archived",
            title = "Archived V3 Entry",
            category = VaultCategory.IDEA.id,
            isArchived = true
        )
        val trashedItem = VaultItemEntity(
            id = "v3_trashed",
            title = "Trashed V3 Entry",
            category = VaultCategory.KNOWLEDGE.id,
            isTrashed = true
        )
        repository.saveVaultItem(archivedItem, emptyList())
        repository.saveVaultItem(trashedItem, emptyList())

        // 2. Delete stepImgFile temporarily -> exportBackup MUST fail and write 0 bytes to OutputStream!
        stepImgFile.delete()
        val abortedOut = ByteArrayOutputStream()
        val abortedExport = repository.exportBackup(abortedOut)
        assertTrue(abortedExport is BackupRestoreResult.Failure)
        assertEquals(0, abortedOut.size())

        // 3. Restore stepImgFile -> export succeeds and ZIP contains manifest + both attachment files with exact SHA-256
        stepImgFile.writeBytes("PNG_STEP_V3_888".toByteArray())
        val validOut = ByteArrayOutputStream()
        val validExport = repository.exportBackup(validOut)
        assertTrue(validExport is BackupRestoreResult.Success)
        val zipBytes = validOut.toByteArray()
        assertTrue(zipBytes.isNotEmpty())

        // Inspect ZIP entries directly
        val zipEntryNames = mutableSetOf<String>()
        var manifestJsonText: String? = null
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntryNames.add(entry.name)
                if (entry.name == BackupRestoreManager.MANIFEST_ENTRY_NAME) {
                    manifestJsonText = zis.readBytes().toString(Charsets.UTF_8)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        assertEquals(
            setOf("vault_backup.json", "attachments/spec_v3.pdf", "attachments/step_v3.png"),
            zipEntryNames
        )
        assertNotNull(manifestJsonText)
        val manifestObj = JSONObject(manifestJsonText!!)
        assertEquals(BackupRestoreManager.SUPPORTED_SCHEMA_VERSION, manifestObj.getInt("schemaVersion"))
        assertEquals(2, manifestObj.getJSONArray("attachmentsManifest").length())
        assertEquals(2, manifestObj.getJSONArray("routes").length())
        assertEquals(2, manifestObj.getJSONArray("routeFollowRuns").length())

        // 4. Wipe DB & attachments, restore from ZIP, and verify all active, archived, trashed, routes, steps, and files
        db.clearAllTables()
        attachmentStorage.attachmentsDir.deleteRecursively()
        val payload = repository.validateBackup(ByteArrayInputStream(zipBytes)).getOrThrow()
        assertTrue(repository.applyBackup(payload) is BackupRestoreResult.Success)

        val restoredMethod = repository.getItemWithDetailsById("v3_full_method")!!
        val restoredMainRoute = restoredMethod.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }
        assertEquals("Primary Track", restoredMainRoute.name)
        assertEquals("Main track desc", restoredMainRoute.description)
        assertEquals(2, restoredMethod.effectiveMethodRoutes.size)
        assertEquals(setOf("v3_ms1"), restoredMainRoute.completedStepIds)
        assertEquals(setOf("v3_as1"), restoredMethod.effectiveMethodRoutes.first { it.id == "v3_alt_route" }.completedStepIds)
        assertEquals(1, repository.archivedItemsWithDetailsFlow.first().size)
        assertEquals(1, repository.trashedItemsWithDetailsFlow.first().size)
        assertTrue(File(attachmentStorage.attachmentsDir, "spec_v3.pdf").exists())
        assertTrue(File(attachmentStorage.attachmentsDir, "step_v3.png").exists())
        assertTrue(attachmentStorage.stagingRootDir.listFiles()?.toList().orEmpty().isEmpty())
    }

    @Test
    fun `verify concurrent backup export serializes against mutations and restore via writeMutex and fails closed on non-healthy state`(): Unit = runBlocking {
        val attFile = File(attachmentStorage.attachmentsDir, "concurrent_spec.png").apply {
            parentFile?.mkdirs()
            writeBytes("INITIAL_ATTACHMENT_V1".toByteArray())
        }
        val folder = repository.createFolder("Concurrency Folder", VaultCategory.METHOD, "#A7D678")
        val methodId = "concurrent_method_1"
        val altRouteId = "concurrent_alt_1"

        val initialItem = VaultItemEntity(
            id = methodId,
            title = "Coherent Snapshot Method",
            description = "Before concurrent mutation",
            category = VaultCategory.METHOD.id,
            folderId = folder.id,
            attachmentsJson = VaultJsonSerializers.encodeAttachments(
                listOf(
                    AttachmentItem(
                        id = "att_conc_1",
                        type = AttachmentType.IMAGE,
                        name = "concurrent_spec.png",
                        uriOrUrl = attFile.absolutePath,
                        mimeType = "image/png",
                        sizeBytes = attFile.length()
                    )
                )
            )
        )
        val initialMainSteps = listOf(
            MethodStepEntity(
                id = "conc_ms_1",
                itemId = methodId,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 1,
                title = "Initial Main Step 1"
            ),
            MethodStepEntity(
                id = "conc_ms_2",
                itemId = methodId,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 2,
                title = "Initial Main Step 2"
            )
        )
        val initialAltRoute = MethodRouteWithSteps(
            route = MethodRouteEntity(
                id = altRouteId,
                itemId = methodId,
                name = "Initial Alt Route",
                description = "Track B",
                routeOrder = 1,
                isMain = false
            ),
            steps = listOf(
                MethodStepEntity(
                    id = "conc_as_1",
                    itemId = methodId,
                    routeId = altRouteId,
                    stepOrder = 1,
                    title = "Initial Alt Step 1"
                )
            )
        )

        assertTrue(
            repository.saveVaultItem(
                item = initialItem,
                steps = initialMainSteps,
                mainRouteName = "Initial Main Route",
                mainRouteDescription = "Track A",
                alternativeRoutes = listOf(initialAltRoute)
            )
        )
        repository.toggleFollowStep(methodId, "conc_ms_1", MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(methodId, "conc_as_1", altRouteId)

        // Track execution order to prove writeMutex serializes export vs concurrent save & route delete
        val executionEvents = java.util.Collections.synchronizedList(mutableListOf<String>())
        var concurrentJobsLaunched = false
        var concurrentSaveJob: kotlinx.coroutines.Deferred<SaveItemResult>? = null
        var concurrentDeleteRouteJob: kotlinx.coroutines.Deferred<Boolean>? = null

        backupManager.exportMidSnapshotHook = {
            executionEvents.add("export_mid_snapshot")
            // Launch a concurrent item mutation and route deletion while export holds writeMutex
            concurrentSaveJob = async(Dispatchers.Default) {
                val res = repository.saveVaultItemWithResult(
                    item = initialItem.copy(title = "Mutated During Export"),
                    steps = initialMainSteps.take(1),
                    expectedPreviousUpdatedAt = null,
                    forceOverwrite = true,
                    mainRouteName = "Mutated Main Route",
                    alternativeRoutes = listOf(initialAltRoute)
                )
                executionEvents.add("concurrent_save_completed")
                res
            }
            concurrentDeleteRouteJob = async(Dispatchers.Default) {
                val deleted = repository.deleteAlternativeRoute(methodId, altRouteId)
                executionEvents.add("concurrent_delete_route_completed")
                deleted
            }
            concurrentJobsLaunched = true
            yield()
            // Because writeMutex is held by exportBackupToStream, neither concurrent mutation can complete yet!
            assertEquals(listOf("export_mid_snapshot"), executionEvents.toList())
            executionEvents.add("export_finishing")
        }

        val exportOut = ByteArrayOutputStream()
        val exportResult = repository.exportBackup(exportOut)
        assertTrue(exportResult is BackupRestoreResult.Success)
        assertTrue(concurrentJobsLaunched)

        // Wait for queued concurrent jobs to complete after export releases writeMutex
        assertTrue(concurrentSaveJob!!.await() is SaveItemResult.Success)
        assertTrue(concurrentDeleteRouteJob!!.await())

        // Verify strict ordering: export finished before either mutation acquired writeMutex
        assertEquals("export_mid_snapshot", executionEvents[0])
        assertEquals("export_finishing", executionEvents[1])
        assertTrue(executionEvents.contains("concurrent_save_completed"))
        assertTrue(executionEvents.contains("concurrent_delete_route_completed"))

        backupManager.exportMidSnapshotHook = null

        // Verify the live DB now reflects the post-export mutations
        val postMutationDetails = repository.getItemWithDetailsById(methodId)!!
        assertEquals("Mutated During Export", postMutationDetails.entity.title)
        assertFalse(postMutationDetails.hasAlternativeMethods)

        // Restore the exported ZIP onto a clean vault and prove it captured the 100% coherent pre-mutation state
        val exportedBytes = exportOut.toByteArray()
        db.clearAllTables()
        attachmentStorage.attachmentsDir.deleteRecursively()

        val validated = repository.validateBackup(ByteArrayInputStream(exportedBytes)).getOrThrow()
        assertTrue(repository.applyBackup(validated) is BackupRestoreResult.Success)

        val restoredDetails = repository.getItemWithDetailsById(methodId)!!
        assertEquals("Coherent Snapshot Method", restoredDetails.entity.title)
        assertEquals("Concurrency Folder", restoredDetails.folderName)
        assertEquals(2, restoredDetails.effectiveMethodRoutes.size)
        assertEquals(listOf("conc_ms_1", "conc_ms_2"), restoredDetails.steps.map { it.id })
        assertEquals(setOf("conc_ms_1"), restoredDetails.completedFollowStepIds)
        assertEquals(
            setOf("conc_as_1"),
            restoredDetails.effectiveMethodRoutes.first { it.id == altRouteId }.completedStepIds
        )
        assertTrue(File(attachmentStorage.attachmentsDir, "concurrent_spec.png").exists())

        // Also verify fail-closed behavior if vault health enters RestoreInProgress or UnresolvedRecoveryError
        val corruptJournalFile = File(attachmentStorage.rollbackRootDir, BackupRestoreManager.JOURNAL_FILE_NAME).apply {
            parentFile?.mkdirs()
            writeText("{corrupt_json")
        }
        val dummyDbSnapFile = File(attachmentStorage.rollbackRootDir, BackupRestoreManager.DB_SNAPSHOT_FILE_NAME).apply {
            writeText("{}")
        }
        val recResult = backupManager.recoverInterruptedRestore()
        assertTrue(recResult is BackupRestoreResult.Failure)

        val blockedExportOut = ByteArrayOutputStream()
        val blockedExport = repository.exportBackup(blockedExportOut)
        assertTrue("Export must fail closed when vault has unresolved recovery error", blockedExport is BackupRestoreResult.Failure)
        assertEquals("Blocked export must write 0 bytes", 0, blockedExportOut.size())

        corruptJournalFile.delete()
        dummyDbSnapFile.delete()
        backupManager.recoverInterruptedRestore()
    }

    @Test
    fun `verify atomic SQL rollback on mid-transaction fault during deleteAlternativeRoute and saveVaultItemWithResult plus shared attachment preservation`(): Unit = runBlocking {
        // Create step images: one shared between Main Method and Alt Route 1, one exclusive to Alt Route 1, one exclusive to Alt Route 2
        val sharedStepImg = File(attachmentStorage.attachmentsDir, "shared_step_img.png").apply {
            parentFile?.mkdirs()
            writeBytes("SHARED_STEP_IMAGE_BYTES".toByteArray())
        }
        val alt1ExclusiveImg = File(attachmentStorage.attachmentsDir, "alt1_exclusive_img.png").apply {
            writeBytes("ALT1_EXCLUSIVE_IMAGE_BYTES".toByteArray())
        }
        val alt2ExclusiveImg = File(attachmentStorage.attachmentsDir, "alt2_exclusive_img.png").apply {
            writeBytes("ALT2_EXCLUSIVE_IMAGE_BYTES".toByteArray())
        }

        val methodId = "atomic_rollback_method"
        val altRoute1Id = "alt_route_alpha"
        val altRoute2Id = "alt_route_beta"

        val methodItem = VaultItemEntity(
            id = methodId,
            title = "Atomic Transaction Method",
            description = "Original description",
            category = VaultCategory.METHOD.id
        )
        val mainSteps = listOf(
            MethodStepEntity(
                id = "atom_ms_1",
                itemId = methodId,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 1,
                title = "Main Step 1",
                imagesJson = VaultJsonSerializers.encodeStringList(listOf(sharedStepImg.absolutePath))
            ),
            MethodStepEntity(
                id = "atom_ms_2",
                itemId = methodId,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 2,
                title = "Main Step 2"
            )
        )
        val altRoutes = listOf(
            MethodRouteWithSteps(
                route = MethodRouteEntity(
                    id = altRoute1Id,
                    itemId = methodId,
                    name = "Alpha Route",
                    description = "First alternative",
                    routeOrder = 1,
                    isMain = false
                ),
                steps = listOf(
                    MethodStepEntity(
                        id = "atom_a1_s1",
                        itemId = methodId,
                        routeId = altRoute1Id,
                        stepOrder = 1,
                        title = "Alpha Step 1",
                        imagesJson = VaultJsonSerializers.encodeStringList(
                            listOf(sharedStepImg.absolutePath, alt1ExclusiveImg.absolutePath)
                        )
                    ),
                    MethodStepEntity(
                        id = "atom_a1_s2",
                        itemId = methodId,
                        routeId = altRoute1Id,
                        stepOrder = 2,
                        title = "Alpha Step 2"
                    )
                )
            ),
            MethodRouteWithSteps(
                route = MethodRouteEntity(
                    id = altRoute2Id,
                    itemId = methodId,
                    name = "Beta Route",
                    description = "Second alternative",
                    routeOrder = 2,
                    isMain = false
                ),
                steps = listOf(
                    MethodStepEntity(
                        id = "atom_a2_s1",
                        itemId = methodId,
                        routeId = altRoute2Id,
                        stepOrder = 1,
                        title = "Beta Step 1",
                        imagesJson = VaultJsonSerializers.encodeStringList(listOf(alt2ExclusiveImg.absolutePath))
                    )
                )
            )
        )

        assertTrue(
            repository.saveVaultItemWithResult(
                item = methodItem,
                steps = mainSteps,
                mainRouteName = "Main Track",
                mainRouteDescription = "Primary",
                alternativeRoutes = altRoutes
            ) is SaveItemResult.Success
        )

        repository.toggleFollowStep(methodId, "atom_ms_1", MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(methodId, "atom_ms_2", MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(methodId, "atom_a1_s1", altRoute1Id)
        repository.toggleFollowStep(methodId, "atom_a2_s1", altRoute2Id)

        val beforeDeleteSnapshot = db.vaultDao().getConsistentVaultSnapshot()

        // PART A: Inject fault mid-way through deleteAlternativeRoute (after route/step/progress rows were deleted in SQL)
        repository.deleteRouteMidTransactionFaultInjector = {
            throw IllegalStateException("Simulated SQL transaction failure mid-route-delete")
        }

        val failedDelete = repository.deleteAlternativeRoute(methodId, altRoute1Id)
        assertFalse("deleteAlternativeRoute must return false when transaction rolls back", failedDelete)

        // Verify entire SQLite transaction rolled back cleanly across all tables and files!
        val afterFailedDeleteSnapshot = db.vaultDao().getConsistentVaultSnapshot()
        assertEquals(beforeDeleteSnapshot.items, afterFailedDeleteSnapshot.items)
        assertEquals(beforeDeleteSnapshot.routes, afterFailedDeleteSnapshot.routes)
        assertEquals(beforeDeleteSnapshot.steps, afterFailedDeleteSnapshot.steps)
        assertEquals(beforeDeleteSnapshot.followRuns, afterFailedDeleteSnapshot.followRuns)
        assertEquals(beforeDeleteSnapshot.routeFollowRuns, afterFailedDeleteSnapshot.routeFollowRuns)
        assertTrue("Shared step image must still exist after rollback", sharedStepImg.exists())
        assertTrue("Alt1 exclusive image must still exist after rollback", alt1ExclusiveImg.exists())
        assertTrue("Alt2 exclusive image must still exist after rollback", alt2ExclusiveImg.exists())

        // Clear fault injector and retry deleteAlternativeRoute -> must succeed, delete alt1ExclusiveImg, and PRESERVE sharedStepImg!
        repository.deleteRouteMidTransactionFaultInjector = null
        assertTrue("Retry after rollback must succeed", repository.deleteAlternativeRoute(methodId, altRoute1Id))

        val afterRetryDetails = repository.getItemWithDetailsById(methodId)!!
        assertEquals(listOf(MAIN_METHOD_ROUTE_ID, altRoute2Id), afterRetryDetails.effectiveMethodRoutes.map { it.id })
        assertEquals(1, afterRetryDetails.effectiveMethodRoutes.first { it.id == altRoute2Id }.route.routeOrder)
        assertEquals(setOf("atom_ms_1", "atom_ms_2"), afterRetryDetails.completedFollowStepIds)
        assertEquals(
            setOf("atom_a2_s1"),
            afterRetryDetails.effectiveMethodRoutes.first { it.id == altRoute2Id }.completedStepIds
        )
        assertFalse("Unreferenced alt1 exclusive image must be deleted after commit", alt1ExclusiveImg.exists())
        assertTrue("Shared step image referenced by Main Method must NEVER be deleted", sharedStepImg.exists())
        assertTrue("Alt2 exclusive image must still exist", alt2ExclusiveImg.exists())

        // PART B: Inject fault in saveVaultItemWithResult AFTER item/routes/steps write and BEFORE follow-run pruning
        val beforeSaveSnapshot = db.vaultDao().getConsistentVaultSnapshot()
        repository.savePostStepsFaultInjector = {
            throw IllegalStateException("Simulated fault between step write and follow-run pruning")
        }

        val failedSave = repository.saveVaultItemWithResult(
            item = afterRetryDetails.entity.copy(title = "Should Roll Back Title"),
            steps = mainSteps.take(1), // attempting to remove atom_ms_2
            expectedPreviousUpdatedAt = afterRetryDetails.entity.updatedAt,
            isEditingExisting = true,
            mainRouteName = "Main Track",
            mainRouteDescription = "Primary",
            alternativeRoutes = emptyList() // attempting to remove altRoute2
        )
        assertTrue("Save must return StorageFailure on mid-transaction fault", failedSave is SaveItemResult.StorageFailure)

        // Verify entire SQLite transaction rolled back: title, updatedAt, routes, steps, follow progress, and files are untouched!
        val afterFailedSaveSnapshot = db.vaultDao().getConsistentVaultSnapshot()
        assertEquals(beforeSaveSnapshot.items, afterFailedSaveSnapshot.items)
        assertEquals(beforeSaveSnapshot.routes, afterFailedSaveSnapshot.routes)
        assertEquals(beforeSaveSnapshot.steps, afterFailedSaveSnapshot.steps)
        assertEquals(beforeSaveSnapshot.followRuns, afterFailedSaveSnapshot.followRuns)
        assertEquals(beforeSaveSnapshot.routeFollowRuns, afterFailedSaveSnapshot.routeFollowRuns)
        assertTrue("Alt2 exclusive image must NOT be deleted when save rolls back", alt2ExclusiveImg.exists())

        // Clear fault injector and retry save -> must succeed and prune follow progress atomically
        repository.savePostStepsFaultInjector = null
        val retrySave = repository.saveVaultItemWithResult(
            item = afterRetryDetails.entity.copy(title = "Committed Atomic Save"),
            steps = mainSteps.take(1),
            expectedPreviousUpdatedAt = afterRetryDetails.entity.updatedAt,
            isEditingExisting = true,
            mainRouteName = "Main Track",
            mainRouteDescription = "Primary",
            alternativeRoutes = emptyList()
        )
        assertTrue(retrySave is SaveItemResult.Success)

        val finalDetails = repository.getItemWithDetailsById(methodId)!!
        assertEquals("Committed Atomic Save", finalDetails.entity.title)
        assertFalse(finalDetails.hasAlternativeMethods)
        assertEquals(listOf("atom_ms_1"), finalDetails.steps.map { it.id })
        assertEquals(setOf("atom_ms_1"), finalDetails.completedFollowStepIds)
        assertFalse("Alt2 image must be cleaned up after save commits", alt2ExclusiveImg.exists())
        assertTrue("Shared step image still used by atom_ms_1 must exist", sharedStepImg.exists())
    }

    @Test
    fun `verify verifyExportedZipArchive validates all six collections counts and referential integrity and rejects orphaned records without silent discard`(): Unit = runBlocking {
        val folder = repository.createFolder("Integrity Folder", VaultCategory.METHOD, "#EF7B59")
        val methodId = "manifest_verify_method"
        val altRouteId = "manifest_verify_alt"

        assertTrue(
            repository.saveVaultItem(
                item = VaultItemEntity(
                    id = methodId,
                    title = "Six Table Verification Method",
                    category = VaultCategory.METHOD.id,
                    folderId = folder.id
                ),
                steps = listOf(
                    MethodStepEntity(
                        id = "mv_s1",
                        itemId = methodId,
                        routeId = MAIN_METHOD_ROUTE_ID,
                        stepOrder = 1,
                        title = "Main Step"
                    )
                ),
                mainRouteName = "Main Route",
                mainRouteDescription = "Primary",
                alternativeRoutes = listOf(
                    MethodRouteWithSteps(
                        route = MethodRouteEntity(
                            id = altRouteId,
                            itemId = methodId,
                            name = "Alt Route",
                            description = "Secondary",
                            routeOrder = 1,
                            isMain = false
                        ),
                        steps = listOf(
                            MethodStepEntity(
                                id = "mv_as1",
                                itemId = methodId,
                                routeId = altRouteId,
                                stepOrder = 1,
                                title = "Alt Step"
                            )
                        )
                    )
                )
            )
        )
        repository.toggleFollowStep(methodId, "mv_s1", MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(methodId, "mv_as1", altRouteId)

        val validOut = ByteArrayOutputStream()
        assertTrue(repository.exportBackup(validOut) is BackupRestoreResult.Success)
        val tempArchive = File(context.cacheDir, "verified_6table_${System.nanoTime()}.zip").apply {
            writeBytes(validOut.toByteArray())
        }

        // 1. Exact 6-table counts pass verification (1 folder, 1 item, 2 routes, 2 steps, 1 followRun, 2 routeFollowRuns)
        backupManager.verifyExportedZipArchive(
            zipFile = tempArchive,
            expectedFolderCount = 1,
            expectedItemCount = 1,
            expectedRouteCount = 2,
            expectedStepCount = 2,
            expectedFollowRunCount = 1,
            expectedRouteFollowRunCount = 2,
            expectedAttachments = emptyMap()
        )

        // 2. Mismatched expectedRouteCount, expectedFollowRunCount, or expectedRouteFollowRunCount must throw IOException
        for (badCounts in listOf(
            Triple(99, 1, 2),
            Triple(2, 99, 2),
            Triple(2, 1, 99)
        )) {
            var threwCountMismatch = false
            try {
                backupManager.verifyExportedZipArchive(
                    zipFile = tempArchive,
                    expectedFolderCount = 1,
                    expectedItemCount = 1,
                    expectedRouteCount = badCounts.first,
                    expectedStepCount = 2,
                    expectedFollowRunCount = badCounts.second,
                    expectedRouteFollowRunCount = badCounts.third,
                    expectedAttachments = emptyMap()
                )
            } catch (_: IOException) {
                threwCountMismatch = true
            }
            assertTrue("Must reject mismatched 6-table counts $badCounts", threwCountMismatch)
        }
        tempArchive.delete()

        // 3. Inject an orphaned step referencing a non-existent routeId directly into SQLite ->
        // exportBackup must fail closed with ERR_EXPORT_REFERENTIAL_INTEGRITY and write 0 bytes instead of silently discarding data!
        db.vaultDao().insertSteps(
            listOf(
                MethodStepEntity(
                    id = "dangling_step",
                    itemId = methodId,
                    routeId = "non_existent_route_id",
                    stepOrder = 99,
                    title = "Dangling Step"
                )
            )
        )
        val danglingOut = ByteArrayOutputStream()
        val danglingExport = repository.exportBackup(danglingOut)
        assertTrue("Export must fail on broken referential integrity rather than silently discarding data", danglingExport is BackupRestoreResult.Failure)
        assertEquals(
            "ERR_EXPORT_REFERENTIAL_INTEGRITY",
            (danglingExport as BackupRestoreResult.Failure).diagnosticCode
        )
        assertEquals(0, danglingOut.size())
    }
}
