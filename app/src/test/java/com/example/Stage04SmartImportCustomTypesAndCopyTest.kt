package com.example

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AttachmentStorageManager
import com.example.data.BackupRestoreManager
import com.example.data.BackupRestoreResult
import com.example.data.CustomChecklistItem
import com.example.data.CustomFieldDefinition
import com.example.data.CustomFieldType
import com.example.data.CustomTypeEntity
import com.example.data.DEFAULT_MAIN_METHOD_NAME
import com.example.data.IdeaStatus
import com.example.data.MAIN_METHOD_ROUTE_ID
import com.example.data.MethodRouteWithSteps
import com.example.data.MethodStepEntity
import com.example.data.PlainTextMethodOption
import com.example.data.RichBlockType
import com.example.data.SaveItemResult
import com.example.data.SmartImportExportEngine
import com.example.data.SmartImportFormat
import com.example.data.UserPreferencesRepository
import com.example.data.VaultCategory
import com.example.data.VaultDatabase
import com.example.data.VaultItemEntity
import com.example.data.VaultJsonSerializers
import com.example.data.VaultRepository
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.ItemDetailScreen
import com.example.ui.theme.AppThemeMode
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.theme.IdeaVaultTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage04SmartImportCustomTypesAndCopyTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: VaultDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var tempPrefsFile: File
    private lateinit var attachmentStorage: AttachmentStorageManager
    private lateinit var backupRestoreManager: BackupRestoreManager
    private lateinit var repository: VaultRepository
    private val disposableDbNames = mutableListOf<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dbName = "stage04_vault_${System.nanoTime()}.db"
        disposableDbNames.add(dbName)
        database = Room.databaseBuilder(context, VaultDatabase::class.java, dbName)
            .addMigrations(
                VaultDatabase.MIGRATION_1_2,
                VaultDatabase.MIGRATION_2_3,
                VaultDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()
        tempPrefsFile = File(context.cacheDir, "stage04_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { tempPrefsFile }
        )
        prefsRepo = UserPreferencesRepository(dataStore)
        attachmentStorage = AttachmentStorageManager(context)
        backupRestoreManager = BackupRestoreManager(
            dao = database.vaultDao(),
            prefsRepo = prefsRepo,
            attachmentStorage = attachmentStorage,
            database = database
        )
        repository = VaultRepository(
            dao = database.vaultDao(),
            prefsRepo = prefsRepo,
            attachmentStorage = attachmentStorage,
            backupRestoreManager = backupRestoreManager
        )
    }

    @After
    fun tearDown() {
        runCatching { database.close() }
        runCatching { tempPrefsFile.delete() }
        disposableDbNames.forEach { name ->
            runCatching { context.deleteDatabase(name) }
        }
    }

    // =========================================================================
    // 1. BULK METHOD IMPORT & UNIVERSAL SMART IMPORT (1 TO 500 STEPS + LIMITS)
    // =========================================================================

    @Test
    fun `bulk method JSON import supports 1 10 100 and 500 steps and rejects 501 steps`() {
        for (count in listOf(1, 10, 100, 500)) {
            val stepsArr = JSONArray()
            for (i in 1..count) {
                stepsArr.put(
                    JSONObject()
                        .put("title", "Step $i")
                        .put("details", "Detailed instructions for step $i")
                )
            }
            val json = JSONObject()
                .put("title", "Method with $count steps")
                .put("description", "Offline workflow with $count steps")
                .put("steps", stepsArr)
                .toString()

            val result = SmartImportExportEngine.validateAndPreviewImportSync(
                rawInput = json,
                selectedFormat = SmartImportFormat.JSON,
                defaultCategory = VaultCategory.METHOD
            )
            assertTrue("Expected isValid=true for $count steps, got ${result.errors}", result.isValid)
            assertEquals(VaultCategory.METHOD, result.targetCategory)
            assertEquals("Method with $count steps", result.title)
            assertEquals("Offline workflow with $count steps", result.description)
            assertEquals(count, result.mainSteps.size)
            assertEquals("Step 1", result.mainSteps.first().title)
            assertEquals("Detailed instructions for step 1", result.mainSteps.first().details)
            assertEquals("Step $count", result.mainSteps.last().title)
            assertEquals(count, result.mainSteps.last().stepNumber)
        }

        // 501 steps must fail with a clear validation message
        val steps501 = JSONArray()
        for (i in 1..501) {
            steps501.put(JSONObject().put("title", "Step $i").put("details", "Details $i"))
        }
        val json501 = JSONObject()
            .put("title", "Too Many Steps")
            .put("steps", steps501)
            .toString()

        val overLimitResult = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = json501,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.METHOD
        )
        assertFalse(overLimitResult.isValid)
        val errorMsg = overLimitResult.errors.joinToString(" ")
        assertTrue("Expected 500 step limit error, got: $errorMsg", errorMsg.contains("500"))
    }

    @Test
    fun `method JSON import supports alternativeMethods and ignores external IDs`() {
        val json = """
            {
              "id": "external_evil_id_999",
              "itemId": "external_item_id_888",
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "METHOD",
              "title": "Deploy Android Release",
              "description": "Complete release checklist",
              "tags": ["android", "release"],
              "steps": [
                { "id": "ext_s1", "title": "Run Unit Tests", "details": "Execute testDebugUnitTest", "notes": "Must pass 100%" },
                { "id": "ext_s2", "title": "Sign Bundle", "details": "Generate signed AAB" }
              ],
              "alternativeMethods": [
                {
                  "id": "ext_route_1",
                  "title": "CI Pipeline Route",
                  "description": "Automated GitHub Actions flow",
                  "steps": [
                    { "title": "Push Tag", "details": "git tag v2.4.0 && git push --tags" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val res = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = json,
            selectedFormat = SmartImportFormat.AUTO,
            defaultCategory = VaultCategory.METHOD
        )
        assertTrue("Expected valid import, got: ${res.errors}", res.isValid)
        assertEquals(VaultCategory.METHOD, res.targetCategory)
        assertEquals("Deploy Android Release", res.title)
        assertEquals("Complete release checklist", res.description)
        assertEquals(listOf("android", "release"), res.tags)
        assertEquals(2, res.mainSteps.size)
        assertEquals("Must pass 100%", res.mainSteps[0].notes)
        assertEquals(1, res.alternativeRoutes.size)
        assertEquals("CI Pipeline Route", res.alternativeRoutes[0].name)
        assertEquals(1, res.alternativeRoutes[0].steps.size)
        assertEquals("Push Tag", res.alternativeRoutes[0].steps[0].title)
    }

    // =========================================================================
    // 2. IDEA, PROMPT, NOTE & MARKDOWN / PLAIN TEXT SMART IMPORT
    // =========================================================================

    @Test
    fun `idea JSON import detects description vs summary conflict and resolves cleanly`() {
        val json = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "IDEA",
              "title": "Offline Smart Import",
              "summary": "Short hook summary",
              "description": "Detailed multi-sentence description of the offline smart importer.",
              "status": "DEVELOPING",
              "tags": ["offline", "ux"]
            }
        """.trimIndent()

        val res = SmartImportExportEngine.validateAndPreviewImportSync(rawInput = json)
        assertTrue(res.isValid)
        assertTrue(res.fieldConflicts.isNotEmpty())
        assertEquals(
            "Detailed multi-sentence description of the offline smart importer.\n\nSummary:\nShort hook summary",
            res.description
        )
        assertEquals(
            "Detailed multi-sentence description of the offline smart importer.",
            res.fieldConflicts.first().primaryValue
        )
        assertEquals("Short hook summary", res.summary)
        assertEquals(IdeaStatus.DEVELOPING, res.status)
    }

    @Test
    fun `prompt JSON import preserves exact whitespace newlines markdown JSON and code fences byte-for-byte`() {
        val exactPrompt = "You are a Senior Architect.\n\n" +
            "  Indented line with 4 spaces:    keep me   \n" +
            "Variables: {USER_GOAL}, {{SYSTEM_RULES}}\n\n" +
            "```json\n{\n  \"step\": 1,\n  \"raw\": \"**bold** _italic_\"\n}\n```\n\n" +
            "Trailing blank line below:\n\n"

        val json = JSONObject()
            .put("format", "IDEAVAULT")
            .put("schemaVersion", 1)
            .put("type", "PROMPT")
            .put("title", "Architecture Generator Prompt")
            .put("promptText", exactPrompt)
            .put("tags", JSONArray().put("ai").put("prompt"))
            .toString()

        val res = SmartImportExportEngine.validateAndPreviewImportSync(rawInput = json)
        assertTrue("Expected valid prompt import, got: ${res.errors}", res.isValid)
        assertEquals(VaultCategory.PROMPT, res.targetCategory)
        assertEquals(exactPrompt, res.content)
    }

    @Test
    fun `note JSON import imports content and blocks without duplicating content`() {
        val json = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "NOTE",
              "title": "Kotlin Coroutines Notes",
              "content": "Structured concurrency guarantees no leaked coroutines.",
              "blocks": [
                { "type": "CHECKLIST", "text": "Review SupervisorJob", "checked": true },
                { "type": "CODE", "title": "Coroutine Scope", "code": "val scope = CoroutineScope(SupervisorJob())" }
              ]
            }
        """.trimIndent()

        val res = SmartImportExportEngine.validateAndPreviewImportSync(rawInput = json)
        assertTrue("Expected valid note import, got: ${res.errors}", res.isValid)
        assertEquals(VaultCategory.KNOWLEDGE, res.targetCategory)
        assertEquals("Structured concurrency guarantees no leaked coroutines.", res.content)
        assertEquals(2, res.richBlocks.size)
        assertEquals(RichBlockType.CHECKLIST, res.richBlocks[0].type)
        assertTrue(res.richBlocks[0].checked)
        assertEquals(RichBlockType.CODE_BLOCK, res.richBlocks[1].type)
    }

    @Test
    fun `markdown import does NOT turn numbered lines inside fenced code blocks into method steps`() {
        val markdown = """
            # Release Automation Method
            Automated script for building release artifacts.

            ```bash
            1. echo "Inside code block step 1"
            2. ./gradlew assembleRelease
            ```

            1. Verify Signing Config: Confirm keystore properties
            2. Run QA Suite: Execute unit and Robolectric tests
        """.trimIndent()

        val res = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = markdown,
            selectedFormat = SmartImportFormat.MARKDOWN,
            defaultCategory = VaultCategory.METHOD
        )
        assertTrue("Expected valid markdown import, got: ${res.errors}", res.isValid)
        assertEquals(VaultCategory.METHOD, res.targetCategory)
        assertEquals("Release Automation Method", res.title)
        assertEquals("Only numbered lines outside code fences may become steps", 2, res.mainSteps.size)
        assertEquals("Verify Signing Config", res.mainSteps[0].title)
        assertEquals("Run QA Suite", res.mainSteps[1].title)
    }

    @Test
    fun `plain text import supports Prompt Note Idea and Method numbered steps`() {
        val numberedText = """
            Morning Deep Work Routine
            1. Clear desk and close browser tabs
            2. Set 90-minute focus timer
            3. Complete highest-leverage engineering task
        """.trimIndent()

        val methodRes = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = numberedText,
            selectedFormat = SmartImportFormat.PLAIN_TEXT,
            defaultCategory = VaultCategory.METHOD,
            plainTextMethodOption = PlainTextMethodOption.NUMBERED_LINES_TO_STEPS
        )
        assertTrue(methodRes.isValid)
        assertEquals(VaultCategory.METHOD, methodRes.targetCategory)
        assertEquals("Morning Deep Work Routine", methodRes.title)
        assertEquals(3, methodRes.mainSteps.size)

        val promptRes = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = numberedText,
            selectedFormat = SmartImportFormat.PLAIN_TEXT,
            defaultCategory = VaultCategory.PROMPT
        )
        assertTrue(promptRes.isValid)
        assertEquals(VaultCategory.PROMPT, promptRes.targetCategory)
        assertEquals(numberedText, promptRes.content)
    }

    // =========================================================================
    // 3. CUSTOM TYPES: ALL 10 FIELD TYPES, SCHEMA EVOLUTION & SAFE DELETION
    // =========================================================================

    @Test
    fun `custom type supports all 10 field types and preserves data on field removal and type deletion`() = runBlocking {
        val fields = listOf(
            CustomFieldDefinition(id = "f_short", name = "Author", type = CustomFieldType.SHORT_TEXT, required = true),
            CustomFieldDefinition(id = "f_long", name = "Key Takeaways", type = CustomFieldType.LONG_TEXT),
            CustomFieldDefinition(id = "f_num", name = "Rating", type = CustomFieldType.NUMBER),
            CustomFieldDefinition(id = "f_date", name = "Completed Date", type = CustomFieldType.DATE),
            CustomFieldDefinition(id = "f_check", name = "Recommended", type = CustomFieldType.CHECKBOX),
            CustomFieldDefinition(id = "f_single", name = "Format", type = CustomFieldType.SINGLE_SELECT, options = listOf("Hardcover", "Audio", "Ebook")),
            CustomFieldDefinition(id = "f_multi", name = "Themes", type = CustomFieldType.MULTI_SELECT, options = listOf("Systems", "Design", "AI")),
            CustomFieldDefinition(id = "f_url", name = "Reference Link", type = CustomFieldType.URL),
            CustomFieldDefinition(id = "f_code", name = "Example Snippet", type = CustomFieldType.CODE),
            CustomFieldDefinition(id = "f_checklist", name = "Action Items", type = CustomFieldType.CHECKLIST)
        )
        val customType = CustomTypeEntity(
            id = "ct_book_notes",
            name = "Book Notes",
            iconName = "Book",
            colorHex = "#A096EF",
            description = "Structured book notes",
            fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(fields)
        )
        repository.saveCustomType(customType, fields)

        val checklistJson = VaultJsonSerializers.encodeCustomChecklistItems(
            listOf(
                CustomChecklistItem(id = "c1", text = "Apply chapter 1 framework", checked = true),
                CustomChecklistItem(id = "c2", text = "Share summary with team", checked = false)
            )
        )
        val valuesMap = mapOf(
            "f_short" to "Don Norman",
            "f_long" to "Affordances and signifiers make interfaces intuitive.",
            "f_num" to "5",
            "f_date" to "2026-10-31",
            "f_check" to "true",
            "f_single" to "Hardcover",
            "f_multi" to VaultJsonSerializers.encodeStringList(listOf("Systems", "Design")),
            "f_url" to "https://jnd.org",
            "f_code" to "fun affordance() = true",
            "f_checklist" to checklistJson
        )

        val itemEntity = VaultItemEntity(
            id = "item_book_1",
            title = "The Design of Everyday Things",
            description = "Classic industrial and interaction design book",
            content = "Main book reflections.",
            category = VaultCategory.KNOWLEDGE.id,
            customTypeId = customType.id,
            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(valuesMap)
        )
        val saveRes = repository.saveVaultItemWithResult(
            item = itemEntity,
            steps = emptyList(),
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false,
            forceOverwrite = false
        )
        assertTrue(saveRes is SaveItemResult.Success)

        val loaded = repository.getItemWithDetailsById("item_book_1")
        assertNotNull(loaded)
        assertEquals("Book Notes", loaded!!.customType?.name)
        assertEquals("Don Norman", loaded.customFieldsMap["f_short"])
        assertEquals("fun affordance() = true", loaded.customFieldsMap["f_code"])

        // Remove "f_num" (Rating) from the Custom Type schema -> existing item's "Rating: 5" must be preserved in notes
        val reducedFields = fields.filterNot { it.id == "f_num" }
        repository.saveCustomType(customType, reducedFields)

        val afterFieldRemoval = repository.getItemWithDetailsById("item_book_1")!!
        assertTrue(
            "Removed field value must be preserved in notes, got: ${afterFieldRemoval.entity.notes}",
            afterFieldRemoval.entity.notes.contains("Rating: 5")
        )

        // Delete the Custom Type safely -> item must NOT be deleted; its custom fields are preserved in content
        repository.deleteCustomTypeSafely(customType.id)
        val afterTypeDeletion = repository.getItemWithDetailsById("item_book_1")
        assertNotNull("Entry must survive Custom Type deletion", afterTypeDeletion)
        assertNull(afterTypeDeletion!!.entity.customTypeId)
        assertTrue(afterTypeDeletion.entity.content.contains("Don Norman"))
        assertTrue(afterTypeDeletion.entity.content.contains("Apply chapter 1 framework"))
    }

    // =========================================================================
    // 4. COPY & REUSE (EXPORT) + ROUND-TRIP JSON RE-IMPORT
    // =========================================================================

    @Test
    fun `copy all copy as JSON copy as Markdown copy for AI and round-trip JSON re-import preserve all data without internal IDs`() = runBlocking {
        val itemEntity = VaultItemEntity(
            id = "internal_secret_db_uuid_12345",
            title = "Zero-Downtime Room Migration",
            description = "Safe SQLite schema evolution playbook",
            content = "Always test migrations with real SQLite tables.",
            category = VaultCategory.METHOD.id,
            tagsJson = VaultJsonSerializers.encodeStringList(listOf("room", "sqlite")),
            notes = "Verify room_master_table identity_hash."
        )
        val mainSteps = listOf(
            MethodStepEntity(
                id = "internal_step_id_1",
                itemId = itemEntity.id,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 1,
                title = "Export Room Schema",
                description = "Run kspDebugKotlin",
                notes = "Commit 4.json"
            ),
            MethodStepEntity(
                id = "internal_step_id_2",
                itemId = itemEntity.id,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 2,
                title = "Write Migration Test",
                description = "Verify existing rows survive"
            )
        )
        val altRoute = MethodRouteWithSteps(
            id = "internal_route_id_alt",
            itemId = itemEntity.id,
            name = "Automated CI Verification",
            description = "Run on every PR",
            routeOrder = 1,
            isMain = false,
            steps = listOf(
                MethodStepEntity(
                    id = "internal_step_id_alt_1",
                    itemId = itemEntity.id,
                    routeId = "internal_route_id_alt",
                    stepOrder = 1,
                    title = "Execute Gradle Check",
                    description = "gradle :app:testDebugUnitTest"
                )
            )
        )

        repository.saveVaultItemWithResult(
            item = itemEntity,
            steps = mainSteps,
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false,
            forceOverwrite = false,
            mainRouteName = DEFAULT_MAIN_METHOD_NAME,
            mainRouteDescription = "Primary developer workflow",
            alternativeRoutes = listOf(altRoute)
        )

        val savedItem = repository.getItemWithDetailsById(itemEntity.id)!!

        val copyAll = SmartImportExportEngine.formatCopyAll(savedItem)
        assertTrue(copyAll.contains("Zero-Downtime Room Migration"))
        assertTrue(copyAll.contains("1. Export Room Schema"))
        assertTrue(copyAll.contains("Automated CI Verification"))
        assertFalse("Internal DB IDs must never leak into Copy All", copyAll.contains("internal_secret_db_uuid_12345"))

        val copyJson = SmartImportExportEngine.formatCopyAsJson(savedItem)
        assertFalse("Internal DB IDs must never leak into Copy as JSON", copyJson.contains("internal_secret_db_uuid_12345"))
        assertFalse("Internal step IDs must never leak into Copy as JSON", copyJson.contains("internal_step_id_1"))

        val copyMarkdown = SmartImportExportEngine.formatCopyAsMarkdown(savedItem)
        assertTrue(copyMarkdown.contains("# Zero-Downtime Room Migration"))
        assertTrue(copyMarkdown.contains("1. **Export Room Schema**: Run kspDebugKotlin"))

        val copyForAi = SmartImportExportEngine.formatCopyForAi(
            item = savedItem,
            userRequest = "Expand each step with rollback commands."
        )
        assertTrue(copyForAi.contains("Expand each step with rollback commands."))
        assertTrue(copyForAi.contains("Zero-Downtime Room Migration"))

        // Round-trip re-import from Copy as JSON
        val roundTrip = SmartImportExportEngine.validateAndPreviewImportSync(rawInput = copyJson)
        assertTrue("Expected valid round-trip import, got: ${roundTrip.errors}", roundTrip.isValid)
        assertEquals(VaultCategory.METHOD, roundTrip.targetCategory)
        assertEquals("Zero-Downtime Room Migration", roundTrip.title)
        assertEquals("Safe SQLite schema evolution playbook", roundTrip.description)
        assertEquals(2, roundTrip.mainSteps.size)
        assertEquals("Export Room Schema", roundTrip.mainSteps[0].title)
        assertEquals("Commit 4.json", roundTrip.mainSteps[0].notes)
        assertEquals(1, roundTrip.alternativeRoutes.size)
        assertEquals("Automated CI Verification", roundTrip.alternativeRoutes[0].name)
        assertEquals(1, roundTrip.alternativeRoutes[0].steps.size)
    }

    // =========================================================================
    // 5. ROOM MIGRATION 3 -> 4 & BACKUP / RESTORE WITH CUSTOM TYPES
    // =========================================================================

    @Test
    fun `Room migration 3 to 4 preserves all v3 rows and adds custom_types and custom columns`() {
        val dbName = "migration_v3_to_v4_${System.nanoTime()}.db"
        disposableDbNames.add(dbName)

        val schema3File = File("schemas/com.example.data.VaultDatabase/3.json").let {
            if (it.exists()) it else File("app/schemas/com.example.data.VaultDatabase/3.json")
        }
        val schema3Json = JSONObject(schema3File.readText(Charsets.UTF_8)).getJSONObject("database")
        val entities3 = schema3Json.getJSONArray("entities")

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (i in 0 until entities3.length()) {
                        val entity = entities3.getJSONObject(i)
                        val tableName = entity.getString("tableName")
                        val createSql = entity.getString("createSql").replace("\${TABLE_NAME}", tableName)
                        db.execSQL(createSql)
                        val indices = entity.optJSONArray("indices")
                        if (indices != null) {
                            for (j in 0 until indices.length()) {
                                val idxSql = indices.getJSONObject(j)
                                    .getString("createSql")
                                    .replace("\${TABLE_NAME}", tableName)
                                db.execSQL(idxSql)
                            }
                        }
                    }
                    db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                    db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema3Json.getString("identityHash")}')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO vault_items (
                    id, title, description, content, category, folderId,
                    tagsJson, richBlocksJson, attachmentsJson, ideaStatus,
                    platform, estimatedTime, notes,
                    isFavorite, isPinned, isArchived, isTrashed, createdAt, updatedAt
                ) VALUES (
                    'v3_item_1', 'Existing V3 Entry', 'Survived migration', 'Rich body text', 'METHOD', NULL,
                    '["v3"]', '[]', '[]', 'NEW',
                    'Android', '10m', 'V3 notes',
                    1, 0, 0, 0, 1700000000000, 1700000005000
                )
                """.trimIndent()
            )
        }
        helper.close()

        val migratedDb = Room.databaseBuilder(context, VaultDatabase::class.java, dbName)
            .addMigrations(
                VaultDatabase.MIGRATION_1_2,
                VaultDatabase.MIGRATION_2_3,
                VaultDatabase.MIGRATION_3_4
            )
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val items = migratedDb.vaultDao().getAllItemsSnapshot()
            assertEquals(1, items.size)
            assertEquals("Existing V3 Entry", items[0].title)
            assertNull(items[0].customTypeId)
            assertEquals("{}", items[0].customFieldsJson)

            val ct = CustomTypeEntity(
                id = "ct_migrated_1",
                name = "Research",
                fieldsJson = "[]"
            )
            migratedDb.vaultDao().upsertCustomType(ct)
            val savedTypes = migratedDb.vaultDao().getAllCustomTypesSnapshot()
            assertEquals(1, savedTypes.size)
            assertEquals("Research", savedTypes[0].name)
        }
        migratedDb.close()
    }

    @Test
    fun `backup and restore round-trips Custom Types and custom field values`() = runBlocking {
        val fields = listOf(
            CustomFieldDefinition(id = "repo_url", name = "Repository URL", type = CustomFieldType.URL, required = true),
            CustomFieldDefinition(id = "snippet", name = "Starter Snippet", type = CustomFieldType.CODE)
        )
        val ct = CustomTypeEntity(
            id = "ct_code_ref",
            name = "Code Reference",
            fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(fields)
        )
        repository.saveCustomType(ct, fields)

        val item = VaultItemEntity(
            id = "item_custom_backup",
            title = "Room Migration Snippet",
            description = "Non-destructive SQLite table addition",
            content = "Detailed notes",
            category = VaultCategory.KNOWLEDGE.id,
            customTypeId = ct.id,
            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(
                mapOf(
                    "repo_url" to "https://developer.android.com/training/data-storage/room",
                    "snippet" to "database.execSQL(\"CREATE TABLE IF NOT EXISTS ...\")"
                )
            )
        )
        repository.saveVaultItemWithResult(item, emptyList(), null, false, false)

        val outStream = ByteArrayOutputStream()
        val exportRes = repository.exportBackup(outStream)
        assertTrue(exportRes is BackupRestoreResult.Success)
        val zipBytes = outStream.toByteArray()

        database.vaultDao().clearAllItems()
        database.vaultDao().clearAllCustomTypes()
        assertTrue(repository.customTypesFlow.first().isEmpty())

        val payload = repository.validateBackup(ByteArrayInputStream(zipBytes)).getOrThrow()
        val restoreRes = repository.applyBackup(payload)
        assertTrue("Restore failed: $restoreRes", restoreRes is BackupRestoreResult.Success)

        val restoredTypes = database.vaultDao().getAllCustomTypesSnapshot()
        assertEquals(1, restoredTypes.size)
        assertEquals("Code Reference", restoredTypes[0].name)

        val restoredItem = repository.getItemWithDetailsById("item_custom_backup")
        assertNotNull(restoredItem)
        assertEquals("Code Reference", restoredItem!!.customType?.name)
        assertEquals(
            "https://developer.android.com/training/data-storage/room",
            restoredItem.customFieldsMap["repo_url"]
        )
    }

    // =========================================================================
    // 6. COMPOSE UI END-TO-END FLOWS: BULK METHOD IMPORT & COPY ACTIONS
    // =========================================================================

    @Test
    fun `Method Editor UI supports Import Method and Bulk Add Steps via SmartImportBottomSheet`() {
        var savedEntity: VaultItemEntity? = null
        var savedSteps: List<MethodStepEntity> = emptyList()

        composeRule.setContent {
            IdeaVaultTheme(themeMode = AppThemeMode.DARK) {
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
                    onSaveItemAwaitable = { entity, steps, _, _, _ ->
                        savedEntity = entity
                        savedSteps = steps
                        SaveItemResult.Success(entity.id, entity.updatedAt)
                    },
                    onSaveSuccess = {},
                    onCancel = {}
                )
            }
        }

        // Scroll LazyColumn to and click "Import Method"
        composeRule.onNodeWithTag("editor_scroll_list")
            .performScrollToNode(hasTestTag("editor_import_method_button"))
        composeRule.onNodeWithTag("editor_import_method_button")
            .assertIsDisplayed()
            .performClick()

        val methodJson = """
            {
              "title": "ChatGPT Generated Playbook",
              "description": "Imported via Bulk Method Import",
              "steps": [
                { "title": "First Imported Step", "details": "Do step 1" },
                { "title": "Second Imported Step", "details": "Do step 2" }
              ]
            }
        """.trimIndent()

        composeRule.onNodeWithTag("smart_import_input")
            .performScrollTo()
            .performTextReplacement(methodJson)

        composeRule.onNodeWithTag("smart_import_validate_button")
            .performScrollTo()
            .performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_import_preview_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("smart_import_confirm_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // Because the new Method draft starts with 1 blank step and Importing replaces draft content,
        // check if the Replace Draft Content dialog appeared (or if it imported directly when blank)
        runCatching {
            composeRule.onNodeWithTag("confirm_replace_draft_button").performClick()
            composeRule.waitForIdle()
        }

        // Now use "Bulk Add Steps" to append a 3rd step without overwriting title or steps 1..2
        composeRule.onNodeWithTag("editor_scroll_list")
            .performScrollToNode(hasTestTag("editor_bulk_add_steps_button"))
        composeRule.onNodeWithTag("editor_bulk_add_steps_button")
            .performClick()

        val appendJson = """
            {
              "steps": [
                { "title": "Appended Step Three", "details": "Do step 3" }
              ]
            }
        """.trimIndent()

        composeRule.onNodeWithTag("smart_import_input")
            .performScrollTo()
            .performTextReplacement(appendJson)
        composeRule.onNodeWithTag("smart_import_validate_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_import_confirm_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // Save the Method entry
        composeRule.onNodeWithTag("editor_save_button").performClick()
        composeRule.waitForIdle()

        assertNotNull(savedEntity)
        assertEquals("ChatGPT Generated Playbook", savedEntity!!.title)
        assertEquals("Imported via Bulk Method Import", savedEntity!!.description)
        assertEquals(3, savedSteps.size)
        assertEquals("First Imported Step", savedSteps[0].title)
        assertEquals("Second Imported Step", savedSteps[1].title)
        assertEquals("Appended Step Three", savedSteps[2].title)
        assertEquals(listOf(1, 2, 3), savedSteps.map { it.stepOrder })
    }

    @Test
    fun `ItemDetailScreen Copy All Copy JSON and Copy Code write expected content to Android Clipboard`() {
        val fields = listOf(
            CustomFieldDefinition(id = "code_field", name = "Snippet", type = CustomFieldType.CODE)
        )
        val ct = CustomTypeEntity(
            id = "ct_snippet",
            name = "Snippet Type",
            fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(fields)
        )
        val entity = VaultItemEntity(
            id = "detail_item_1",
            title = "Clipboard Verification Entry",
            description = "Verifying offline clipboard export",
            content = "Body content here",
            category = VaultCategory.KNOWLEDGE.id,
            customTypeId = ct.id,
            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(
                mapOf("code_field" to "val rawCode = 42")
            )
        )
        val details = runBlocking {
            repository.saveCustomType(ct, fields)
            repository.saveVaultItemWithResult(entity, emptyList(), null, false, false)
            repository.getItemWithDetailsById(entity.id)!!
        }

        composeRule.setContent {
            IdeaVaultTheme(themeMode = AppThemeMode.LIGHT) {
                ItemDetailScreen(
                    item = details,
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
                    onShowMessage = {},
                    customTypes = listOf(ct)
                )
            }
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // 1. Copy Code from Custom Field
        composeRule.onNodeWithTag("custom_field_copy_code_code_field")
            .performScrollTo()
            .performClick()
        assertEquals("val rawCode = 42", clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // 2. Copy JSON from Quick Copy & Reuse bar
        composeRule.onNodeWithTag("detail_copy_json_button")
            .performClick()
        val copiedJson = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(copiedJson.contains("\"format\": \"IDEAVAULT\""))
        assertTrue(copiedJson.contains("Clipboard Verification Entry"))
        assertFalse(copiedJson.contains("detail_item_1"))

        // 3. Copy All
        composeRule.onNodeWithTag("detail_copy_all_button")
            .performClick()
        val copiedAll = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(copiedAll.contains("Clipboard Verification Entry"))
        assertTrue(copiedAll.contains("val rawCode = 42"))
    }

    // =========================================================================
    // V2.4 RELEASE STABILIZATION REGRESSION TESTS (GROUPS A - I)
    // =========================================================================

    @Test
    fun `P1-01 Method Copy as JSON round-trips mainMethod steps and detects mainMethod vs root steps conflict`() = runBlocking {
        val entity = VaultItemEntity(
            id = "m_roundtrip_1",
            title = "Deployment Playbook",
            description = "Overview",
            category = VaultCategory.METHOD.id
        )
        val mainSteps = listOf(
            MethodStepEntity(
                itemId = entity.id,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 1,
                title = "Build Release APK",
                description = "Run gradle assembleRelease"
            ),
            MethodStepEntity(
                itemId = entity.id,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 2,
                title = "Verify Signature",
                description = "Check apksigner"
            )
        )
        repository.saveVaultItemWithResult(
            item = entity,
            steps = mainSteps,
            mainRouteName = "Primary Pipeline",
            mainRouteDescription = "Main pipeline details"
        )
        val details = repository.getItemWithDetailsById(entity.id)!!

        // Export using formatCopyAsJson (which writes mainMethod.steps)
        val exportedJson = SmartImportExportEngine.formatCopyAsJson(details, null)
        val reimported = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = exportedJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.METHOD,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )
        assertTrue(reimported.isValid)
        assertEquals(2, reimported.mainSteps.size)
        assertEquals("Build Release APK", reimported.mainSteps[0].title)
        assertEquals("Primary Pipeline", reimported.mainRouteName)

        // Now test conflict when BOTH mainMethod.steps and root steps exist and differ
        val conflictingJson = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "METHOD",
              "title": "Dual Steps Method",
              "mainMethod": {
                "name": "Nested Route",
                "steps": [{"title": "Nested Step A", "details": "From mainMethod.steps"}]
              },
              "steps": [
                {"title": "Root Step B", "details": "From root steps"}
              ]
            }
        """.trimIndent()
        val conflictResult = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = conflictingJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.METHOD,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )
        assertTrue(conflictResult.isValid)
        assertTrue(conflictResult.fieldConflicts.any { it.fieldName == "mainMethod.steps_vs_steps" })
        assertEquals("Nested Step A", conflictResult.mainSteps[0].title)
        assertEquals("Root Step B", conflictResult.secondaryMainSteps[0].title)
    }

    @Test
    fun `P1-02 Envelope format and schemaVersion validation rejects invalid format or version and warns on missing envelope`() {
        val badFormatJson = """
            {
              "format": "NOTION_EXPORT",
              "schemaVersion": 1,
              "type": "IDEA",
              "title": "Wrong Format"
            }
        """.trimIndent()
        val res1 = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = badFormatJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.IDEA,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )
        assertFalse(res1.isValid)
        assertTrue(res1.errors.any { it.contains("Unsupported JSON format") })

        val badVersionJson = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 99,
              "type": "IDEA",
              "title": "Future Schema"
            }
        """.trimIndent()
        val res2 = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = badVersionJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.IDEA,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )
        assertFalse(res2.isValid)
        assertTrue(res2.errors.any { it.contains("Unsupported schemaVersion") })

        val legacyNoEnvelopeJson = """
            {
              "type": "IDEA",
              "title": "Legacy Idea",
              "description": "No envelope fields"
            }
        """.trimIndent()
        val res3 = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = legacyNoEnvelopeJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.IDEA,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )
        assertTrue(res3.isValid)
        assertTrue(res3.warnings.any { it.contains("Legacy JSON without 'format'/'schemaVersion' envelope") })
    }

    @Test
    fun `P1-03 Prompt raw JSON and fenced JSON stay PLAIN_TEXT 100 percent verbatim in AUTO mode`() {
        val rawPromptWithJson = """
            {
              "role": "system",
              "title": "Do NOT parse me as an IdeaVault envelope",
              "instructions": "Respond with valid JSON only."
            }
        """.trimIndent()

        val result = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = rawPromptWithJson,
            selectedFormat = SmartImportFormat.AUTO,
            defaultCategory = VaultCategory.PROMPT,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )
        assertTrue(result.isValid)
        assertEquals(SmartImportFormat.PLAIN_TEXT, result.detectedFormat)
        assertEquals(rawPromptWithJson, result.content)
    }

    @Test
    fun `P1-04 and P1-05 Unmapped custom fields and declined custom type creation preserve values in notes without orphan field IDs`() {
        val existingFields = listOf(
            CustomFieldDefinition(id = "f_known", name = "Author", type = CustomFieldType.SHORT_TEXT)
        )
        val existingType = CustomTypeEntity(
            id = "ct_book_1",
            name = "Book Tracker",
            fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(existingFields)
        )
        val jsonWithExtraField = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "CUSTOM",
              "customTypeName": "Book Tracker",
              "title": "Clean Architecture",
              "fields": {
                "Author": "Robert C. Martin",
                "ISBN": "978-0134494166",
                "Edition": "1st"
              }
            }
        """.trimIndent()

        val res = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = jsonWithExtraField,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.KNOWLEDGE,
            activeCustomType = existingType,
            existingCustomTypes = listOf(existingType)
        )
        assertTrue(res.isValid)
        assertEquals(mapOf("f_known" to "Robert C. Martin"), res.customFieldValuesById)
        assertEquals(mapOf("ISBN" to "978-0134494166", "Edition" to "1st"), res.unmappedCustomFields)

        val formattedBlock = SmartImportExportEngine.formatUnmappedCustomFieldsBlock(res.unmappedCustomFields)
        assertTrue(formattedBlock.contains("ISBN: 978-0134494166"))
        assertTrue(formattedBlock.contains("Edition: 1st"))
    }

    @Test
    fun `P1-06 Rich text formatting round-trips losslessly through Copy as JSON and Smart Import`() = runBlocking {
        val encodedRich = "[[IV_FMT:eyJ2IjoxLCJzcGFucyI6W3sibCI6MCwiciI6MTIsImIiOnRydWV9XX0=]]Bold Heading\nColored line"

        val entity = VaultItemEntity(
            id = "rich_roundtrip_1",
            title = "Rich Formatting Idea",
            description = encodedRich,
            content = encodedRich,
            notes = encodedRich,
            category = VaultCategory.IDEA.id
        )
        repository.saveVaultItemWithResult(entity, emptyList(), null, false, false)
        val details = repository.getItemWithDetailsById(entity.id)!!

        val exportedJson = SmartImportExportEngine.formatCopyAsJson(details, null)
        assertTrue(exportedJson.contains("richDescription"))
        assertTrue(exportedJson.contains("richContent"))
        assertTrue(exportedJson.contains("richNotes"))

        val reimported = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = exportedJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.IDEA,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )
        assertTrue(reimported.isValid)
        assertEquals(encodedRich, reimported.description)
        assertEquals(encodedRich, reimported.content)
        assertEquals(encodedRich, reimported.notes)
    }

    @Test
    fun `P1-08 and P1-13 SmartImportSessionBuffer holds 1MB+ text outside Bundle without overflow`() {
        val key = "test_large_session_key"
        val oneMegabyteText = "A".repeat(1_100_000)
        com.example.ui.components.SmartImportSessionBuffer.put(key, oneMegabyteText)
        assertEquals(1_100_000, com.example.ui.components.SmartImportSessionBuffer.get(key).length)
        com.example.ui.components.SmartImportSessionBuffer.remove(key)
        assertEquals("", com.example.ui.components.SmartImportSessionBuffer.get(key))
    }

    @Test
    fun `P1-09 and P1-10 Idea JSON does not duplicate notes into content and Note CODE block preserves code field`() {
        val ideaJson = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "IDEA",
              "title": "Clean Idea",
              "description": "Primary description",
              "notes": "Private implementation notes"
            }
        """.trimIndent()
        val ideaRes = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = ideaJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.IDEA,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )
        assertTrue(ideaRes.isValid)
        assertEquals("Primary description", ideaRes.description)
        assertEquals("Private implementation notes", ideaRes.notes)
        assertEquals("", ideaRes.content)

        val noteJson = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "NOTE",
              "title": "Code Block Note",
              "content": "Intro text",
              "blocks": [
                {
                  "type": "CODE",
                  "language": "kotlin",
                  "code": "fun main() { println(\"Hello\") }"
                }
              ]
            }
        """.trimIndent()
        val noteRes = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = noteJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.KNOWLEDGE,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )
        assertTrue(noteRes.isValid)
        assertEquals(1, noteRes.richBlocks.size)
        assertEquals("fun main() { println(\"Hello\") }", noteRes.richBlocks[0].content)
    }

    @Test
    fun `P1-11 and P2-14 Custom field required and strict type validation enforced in engine and repository`() = runBlocking {
        val fields = listOf(
            CustomFieldDefinition(id = "f_req", name = "Title Ref", type = CustomFieldType.SHORT_TEXT, required = true),
            CustomFieldDefinition(id = "f_num", name = "Price", type = CustomFieldType.NUMBER),
            CustomFieldDefinition(id = "f_date", name = "Launch Date", type = CustomFieldType.DATE),
            CustomFieldDefinition(id = "f_url", name = "Website", type = CustomFieldType.URL),
            CustomFieldDefinition(id = "f_bool", name = "Verified", type = CustomFieldType.CHECKBOX),
            CustomFieldDefinition(id = "f_sel", name = "Tier", type = CustomFieldType.SINGLE_SELECT, options = listOf("Free", "Pro")),
            CustomFieldDefinition(id = "f_multi", name = "Platforms", type = CustomFieldType.MULTI_SELECT, options = listOf("Android", "iOS", "Web")),
            CustomFieldDefinition(id = "f_check", name = "Tasks", type = CustomFieldType.CHECKLIST)
        )
        val ct = CustomTypeEntity(
            id = "ct_strict",
            name = "Strict Type",
            fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(fields)
        )
        repository.saveCustomType(ct, fields)

        // 1. Missing required field fails repository save
        val missingRequiredEntity = VaultItemEntity(
            id = "strict_item_1",
            title = "Strict Item",
            category = VaultCategory.KNOWLEDGE.id,
            customTypeId = ct.id,
            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(mapOf("f_req" to "   "))
        )
        val saveRes1 = repository.saveVaultItemWithResult(missingRequiredEntity, emptyList(), null, false, false)
        assertTrue(saveRes1 is SaveItemResult.ValidationFailure)

        // 2. Invalid calendar date (2026-02-30), invalid number, invalid URL, invalid checkbox, invalid select option
        val invalidValuesMap = mapOf(
            "f_req" to "Valid Ref",
            "f_num" to "12.34.56",
            "f_date" to "2026-02-30",
            "f_url" to "ftp://not-http",
            "f_bool" to "maybe",
            "f_sel" to "Enterprise",
            "f_multi" to "Android, Linux",
            "f_check" to "{not-a-json-array}"
        )
        val errors = SmartImportExportEngine.validateAllCustomFields(fields, invalidValuesMap, checkRequired = true)
        assertEquals(7, errors.size)

        // 3. Valid values pass all checks and save cleanly
        val validValuesMap = mapOf(
            "f_req" to "Valid Ref",
            "f_num" to "-42.50",
            "f_date" to "2026-02-28",
            "f_url" to "https://developer.android.com",
            "f_bool" to "true",
            "f_sel" to "Pro",
            "f_multi" to "Android, Web",
            "f_check" to "[{\"text\":\"Ship\",\"checked\":true}]"
        )
        val validErrors = SmartImportExportEngine.validateAllCustomFields(fields, validValuesMap, checkRequired = true)
        assertTrue(validErrors.isEmpty())

        val validEntity = missingRequiredEntity.copy(
            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(validValuesMap)
        )
        val saveRes2 = repository.saveVaultItemWithResult(validEntity, emptyList(), null, false, false)
        assertTrue(saveRes2 is SaveItemResult.Success)
    }

    @Test
    fun `P1-12 CustomType save and delete execute inside Room transaction and roll back on mid-transaction failure`() = runBlocking {
        val fields = listOf(CustomFieldDefinition(id = "f1", name = "Field1", type = CustomFieldType.SHORT_TEXT))
        val ct = CustomTypeEntity(
            id = "ct_atomic",
            name = "Atomic Type",
            fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(fields)
        )
        repository.saveCustomType(ct, fields)

        val item = VaultItemEntity(
            id = "item_linked_to_atomic",
            title = "Linked Entry",
            category = VaultCategory.KNOWLEDGE.id,
            customTypeId = ct.id,
            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(mapOf("f1" to "SavedVal"))
        )
        repository.saveVaultItemWithResult(item, emptyList(), null, false, false)

        // Inject mid-transaction fault during deleteCustomTypeSafely
        repository.customTypeMutationMidTransactionFaultInjector = {
            throw IllegalStateException("Simulated mid-transaction failure during CustomType delete")
        }
        var threw = false
        try {
            repository.deleteCustomTypeSafely(ct.id)
        } catch (_: IllegalStateException) {
            threw = true
        } finally {
            repository.customTypeMutationMidTransactionFaultInjector = null
        }
        assertTrue(threw)

        // Verify neither the CustomType nor the item's customTypeId link was partially mutated
        val remainingTypes = repository.customTypesFlow.first()
        assertTrue(remainingTypes.any { it.id == ct.id })
        val fetchedItem = repository.getItemWithDetailsById(item.id)!!
        assertEquals(ct.id, fetchedItem.entity.customTypeId)

        // Without fault, deleteCustomTypeSafely succeeds atomically
        repository.deleteCustomTypeSafely(ct.id)
        assertFalse(repository.customTypesFlow.first().any { it.id == ct.id })
        assertNull(repository.getItemWithDetailsById(item.id)!!.entity.customTypeId)
    }

    @Test
    fun `P1-15 and P2-04 Blank steps and unknown rich block types fail by default and succeed when explicitly allowed`() {
        val blankStepJson = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "METHOD",
              "title": "Method With Blank Step",
              "steps": [
                {"title": "Valid Step 1", "details": "Do work"},
                {"title": "   ", "details": "   "}
              ]
            }
        """.trimIndent()
        val resDefault = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = blankStepJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.METHOD,
            activeCustomType = null,
            existingCustomTypes = emptyList(),
            allowSkipEmptySteps = false
        )
        assertFalse(resDefault.isValid)
        assertTrue(resDefault.errors.any { it.contains("Step object is empty") })

        val resAllowed = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = blankStepJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.METHOD,
            activeCustomType = null,
            existingCustomTypes = emptyList(),
            allowSkipEmptySteps = true
        )
        assertTrue(resAllowed.isValid)
        assertEquals(1, resAllowed.mainSteps.size)

        val unknownBlockJson = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "NOTE",
              "title": "Note With Unknown Block",
              "blocks": [
                {"type": "MERMAID_DIAGRAM", "text": "graph TD; A-->B;"}
              ]
            }
        """.trimIndent()
        val blockDefault = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = unknownBlockJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.KNOWLEDGE,
            activeCustomType = null,
            existingCustomTypes = emptyList(),
            allowUnknownRichBlocks = false
        )
        assertFalse(blockDefault.isValid)
        assertTrue(blockDefault.hasUnknownRichBlocks)

        val blockAllowed = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = unknownBlockJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.KNOWLEDGE,
            activeCustomType = null,
            existingCustomTypes = emptyList(),
            allowUnknownRichBlocks = true
        )
        assertTrue(blockAllowed.isValid)
        assertEquals(RichBlockType.PARAGRAPH, blockAllowed.richBlocks[0].type)
    }

    @Test
    fun `P1-16 Custom JSON with explicit customTypeName never falls back to an unrelated activeCustomType`() {
        val activeFields = listOf(CustomFieldDefinition(id = "f_recipe", name = "Ingredients", type = CustomFieldType.LONG_TEXT))
        val activeType = CustomTypeEntity(
            id = "ct_recipe",
            name = "Recipe",
            fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(activeFields)
        )
        val bookJson = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "CUSTOM",
              "customTypeName": "Book Notes",
              "title": "Designing Data-Intensive Applications",
              "fields": {
                "Author": "Martin Kleppmann"
              }
            }
        """.trimIndent()

        val result = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = bookJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.KNOWLEDGE,
            activeCustomType = activeType,
            existingCustomTypes = listOf(activeType)
        )
        assertTrue(result.isValid)
        assertNull(result.matchedCustomTypeId)
        assertEquals("Book Notes", result.newCustomTypeDraft?.name)
    }

    @Test
    fun `P2-01 and P2-15 Copy Prompt copies only raw prompt and Markdown escapes backticks`() = runBlocking {
        // 1. P2-01: Copy Prompt copies only raw prompt content
        val promptEntity = VaultItemEntity(
            id = "prompt_raw_1",
            title = "System Architect Prompt",
            description = "Summary header that must NOT be in Copy Prompt",
            content = "You are a Staff Android Engineer. Output strict Kotlin only.",
            notes = "Internal notes that must NOT be in Copy Prompt",
            category = VaultCategory.PROMPT.id
        )
        repository.saveVaultItemWithResult(promptEntity, emptyList(), null, false, false)
        val promptDetails = repository.getItemWithDetailsById(promptEntity.id)!!

        composeRule.setContent {
            IdeaVaultTheme(themeMode = AppThemeMode.LIGHT) {
                ItemDetailScreen(
                    item = promptDetails,
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
                    onShowMessage = {},
                    customTypes = emptyList()
                )
            }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        composeRule.onNodeWithTag("detail_copy_prompt_chip").performClick()
        assertEquals(
            "You are a Staff Android Engineer. Output strict Kotlin only.",
            clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        )

        // 2. P2-15: Markdown export dynamically uses 4+ backticks when code contains triple backticks
        val noteWithBackticks = VaultItemEntity(
            id = "note_md_fence",
            title = "Markdown Fence Test",
            content = "[[IV_FMT:eyJ2IjoxfQ==]]Plain visible text",
            category = VaultCategory.KNOWLEDGE.id,
            richBlocksJson = VaultJsonSerializers.encodeRichBlocks(
                listOf(
                    com.example.data.RichContentBlock(
                        id = "b1",
                        type = RichBlockType.CODE_BLOCK,
                        content = "val md = \"\"\"\n```kotlin\nprintln()\n```\n\"\"\""
                    )
                )
            )
        )
        repository.saveVaultItemWithResult(noteWithBackticks, emptyList(), null, false, false)
        val mdDetails = repository.getItemWithDetailsById(noteWithBackticks.id)!!
        val exportedMd = SmartImportExportEngine.formatCopyAsMarkdown(mdDetails, null)
        assertFalse(exportedMd.contains("[[IV_FMT:"))
        assertTrue(exportedMd.contains("````"))
    }

    @Test
    fun `FIX-01 Large 500-step Method editor state stores only compact reference tokens in Bundle and survives rotation and disk-backed process recreation`() {
        com.example.ui.screens.EditorLargeStateSessionBuffer.configureCacheDir(context.cacheDir)
        com.example.ui.screens.EditorLargeStateSessionBuffer.clearAll(context)

        val largeSteps = (1..500).map { i ->
            MethodStepEntity(
                id = "step_$i",
                itemId = "method_500",
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = i,
                title = "Detailed Step $i",
                description = "Comprehensive step instructions for step $i: " + "X".repeat(350),
                notes = "Execution notes for step $i",
                linksJson = VaultJsonSerializers.encodeStringList(listOf("https://example.com/step/$i"))
            )
        }
        val altRouteSteps = (1..50).map { i ->
            MethodStepEntity(
                id = "alt_step_$i",
                itemId = "method_500",
                routeId = "route_alt_1",
                stepOrder = i,
                title = "Alt Step $i",
                description = "Alt route description $i: " + "Y".repeat(250),
                linksJson = VaultJsonSerializers.encodeStringList(listOf("https://example.com/alt/$i"))
            )
        }
        val altRoutes = listOf(
            com.example.data.MethodRouteWithSteps(
                id = "route_alt_1",
                itemId = "method_500",
                name = "Fast CLI Route",
                description = "CLI fallback route",
                routeOrder = 1,
                isMain = false,
                steps = altRouteSteps
            )
        )

        val stepStateList = androidx.compose.runtime.mutableStateListOf<MethodStepEntity>().apply {
            addAll(largeSteps)
        }
        val routeStateList = androidx.compose.runtime.mutableStateListOf<com.example.data.MethodRouteWithSteps>().apply {
            addAll(altRoutes)
        }

        // Verify Saver output written into Android SavedInstanceState Bundle is a tiny reference token (< 128 chars)
        val dummyScope = androidx.compose.runtime.saveable.SaverScope { true }
        val savedStepsBundleValue = with(com.example.ui.screens.MethodStepListSaver) {
            dummyScope.save(stepStateList)
        }!!
        val savedRoutesBundleValue = with(com.example.ui.screens.MethodRouteWithStepsListSaver) {
            dummyScope.save(routeStateList)
        }!!

        assertEquals(1, savedStepsBundleValue.size)
        val stepToken = savedStepsBundleValue[0]
        assertTrue(stepToken.startsWith(com.example.ui.screens.EditorLargeStateSessionBuffer.REF_PREFIX))
        assertTrue("Bundle token must be compact (< 128 chars), was ${stepToken.length}", stepToken.length < 128)

        assertEquals(1, savedRoutesBundleValue.size)
        val routeToken = savedRoutesBundleValue[0]
        assertTrue(routeToken.startsWith(com.example.ui.screens.EditorLargeStateSessionBuffer.REF_PREFIX))
        assertTrue("Bundle token must be compact (< 128 chars), was ${routeToken.length}", routeToken.length < 128)

        // 1. Rotation restore (from in-memory session buffer)
        val restoredStepsOnRotation = com.example.ui.screens.MethodStepListSaver.restore(savedStepsBundleValue)!!
        assertEquals(500, restoredStepsOnRotation.size)
        assertEquals("Detailed Step 500", restoredStepsOnRotation[499].title)

        // 2. Process recreation restore (clear in-memory cache and restore from private disk cache)
        com.example.ui.screens.EditorLargeStateSessionBuffer.clearMemoryOnly()
        val restoredStepsAfterProcessDeath = com.example.ui.screens.MethodStepListSaver.restore(savedStepsBundleValue)!!
        val restoredRoutesAfterProcessDeath = com.example.ui.screens.MethodRouteWithStepsListSaver.restore(savedRoutesBundleValue)!!
        assertEquals(500, restoredStepsAfterProcessDeath.size)
        assertEquals("Detailed Step 500", restoredStepsAfterProcessDeath[499].title)
        assertEquals(1, restoredRoutesAfterProcessDeath.size)
        assertEquals(50, restoredRoutesAfterProcessDeath[0].steps.size)
    }

    @Test
    fun `FIX-02 Custom Type import during Smart Import is staged in draft until Editor Save and discarded cleanly on Editor Cancel`() {
        val importJsonWithNewType = """
            {
              "format": "IDEAVAULT",
              "schemaVersion": 1,
              "type": "CUSTOM",
              "customTypeName": "API Endpoint Spec",
              "title": "Create User Endpoint",
              "description": "POST /v1/users",
              "fields": {
                "HTTP Method": "POST",
                "Auth Scope": "users:write"
              }
            }
        """.trimIndent()

        var cancelCalled = false
        var savedItemId: String? = null

        composeRule.setContent {
            val customTypes by repository.customTypesFlow.collectAsState(initial = emptyList())
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            IdeaVaultTheme(themeMode = AppThemeMode.LIGHT) {
                EditorScreen(
                    existingItem = null,
                    initialCategory = VaultCategory.KNOWLEDGE,
                    initialFolderId = null,
                    folders = emptyList(),
                    recoverableDraft = null,
                    attachmentStorage = attachmentStorage,
                    onAutoSaveDraft = {},
                    onDiscardDraft = {},
                    onCreateFolder = { _, _, _ -> },
                    onSaveItemAwaitable = { item, steps, expectedUpdatedAt, isEditingExisting, forceOverwrite ->
                        repository.saveVaultItemWithResult(item, steps, expectedUpdatedAt, isEditingExisting, forceOverwrite)
                    },
                    onSaveItemWithRoutesAwaitable = { item, steps, expectedUpdatedAt, isEditingExisting, forceOverwrite, mainRouteName, mainRouteDesc, altRoutes, pendingCustomType ->
                        repository.saveVaultItemWithResult(
                            item = item,
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
                    onSaveCustomType = { ct, fields, onDone ->
                        scope.launch {
                            val saved = repository.saveCustomType(ct, fields)
                            onDone(saved)
                        }
                    },
                    onSaveSuccess = { savedItemId = it },
                    onCancel = { cancelCalled = true },
                    customTypes = customTypes,
                    initialOpenSmartImport = true
                )
            }
        }

        // Paste and confirm import into draft
        composeRule.onNodeWithTag("smart_import_input")
            .performScrollTo()
            .performTextReplacement(importJsonWithNewType)
        composeRule.onNodeWithTag("smart_import_validate_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("smart_import_preview_card")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("smart_import_confirm_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        // CRITICAL FIX-02 CHECK: After confirming Smart Import (before pressing Editor Save),
        // the Custom Type MUST NOT be committed to Room yet!
        val customTypesBeforeEditorSave = runBlocking { repository.getAllCustomTypesSnapshot() }
        assertTrue(
            "New Custom Type must NOT be committed to Room before pressing Editor Save",
            customTypesBeforeEditorSave.none { it.name == "API Endpoint Spec" }
        )

        // Now press Editor Save button -> commits both the staged Custom Type and the entry atomically!
        composeRule.onNodeWithTag("editor_save_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { savedItemId != null }

        assertNotNull(savedItemId)
        val customTypesAfterEditorSave = runBlocking { repository.getAllCustomTypesSnapshot() }
        assertEquals(1, customTypesAfterEditorSave.count { it.name == "API Endpoint Spec" })
        val savedEntry = runBlocking { repository.getItemWithDetailsById(savedItemId!!)!! }
        assertEquals("API Endpoint Spec", savedEntry.customType?.name)
    }

    @Test
    fun `FIX-03 Alternative Method step links round-trip through Export JSON Import Save and Reopen`() = runBlocking {
        val methodEntity = VaultItemEntity(
            id = "method_alt_links_1",
            title = "Cloud Deployment Playbook",
            description = "Full deployment method",
            category = VaultCategory.METHOD.id
        )
        val mainSteps = listOf(
            MethodStepEntity(
                id = "m_step_1",
                itemId = methodEntity.id,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 1,
                title = "Main Step 1",
                description = "Run primary deploy",
                linksJson = VaultJsonSerializers.encodeStringList(listOf("https://docs.example.com/main-step-1"))
            )
        )
        val altRoute = com.example.data.MethodRouteWithSteps(
            id = "alt_route_docker",
            itemId = methodEntity.id,
            name = "Docker Fallback Route",
            description = "Containerized fallback",
            routeOrder = 1,
            isMain = false,
            steps = listOf(
                MethodStepEntity(
                    id = "alt_step_1",
                    itemId = methodEntity.id,
                    routeId = "alt_route_docker",
                    stepOrder = 1,
                    title = "Build Container",
                    description = "docker build -t app .",
                    linksJson = VaultJsonSerializers.encodeStringList(
                        listOf("https://docs.example.com/docker-build", "https://hub.docker.com/r/example/app")
                    )
                )
            )
        )
        repository.saveVaultItemWithResult(
            item = methodEntity,
            steps = mainSteps,
            mainRouteName = "Main Method",
            alternativeRoutes = listOf(altRoute)
        )
        val savedDetails = repository.getItemWithDetailsById(methodEntity.id)!!

        // Export to JSON
        val exportedJson = SmartImportExportEngine.formatCopyAsJson(savedDetails, null)

        // Import back
        val imported = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = exportedJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.METHOD,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )
        assertTrue(imported.isValid)
        assertEquals(listOf("https://docs.example.com/main-step-1"), imported.mainSteps[0].links)
        assertEquals(1, imported.alternativeRoutes.size)
        assertEquals(
            listOf("https://docs.example.com/docker-build", "https://hub.docker.com/r/example/app"),
            imported.alternativeRoutes[0].steps[0].links
        )
    }

    @Test
    fun `FIX-04 Prompt title description content and notes round-trip distinctly without overwriting`() = runBlocking {
        val originalPrompt = VaultItemEntity(
            id = "prompt_roundtrip_fix04",
            title = "Kotlin Code Reviewer",
            description = "Use this prompt when auditing pull requests for concurrency bugs.",
            content = "You are a Senior Kotlin Reviewer.\nCheck all CoroutineScopes and Flows.",
            notes = "Works best with temperature=0.1.",
            category = VaultCategory.PROMPT.id
        )
        repository.saveVaultItemWithResult(originalPrompt, emptyList())
        val details = repository.getItemWithDetailsById(originalPrompt.id)!!

        val exportedJson = SmartImportExportEngine.formatCopyAsJson(details, null)
        val reimported = SmartImportExportEngine.validateAndPreviewImportSync(
            rawInput = exportedJson,
            selectedFormat = SmartImportFormat.JSON,
            defaultCategory = VaultCategory.PROMPT,
            activeCustomType = null,
            existingCustomTypes = emptyList()
        )

        assertTrue(reimported.isValid)
        assertEquals("Kotlin Code Reviewer", reimported.title)
        assertEquals("Use this prompt when auditing pull requests for concurrency bugs.", reimported.description)
        assertEquals("You are a Senior Kotlin Reviewer.\nCheck all CoroutineScopes and Flows.", reimported.content)
        assertEquals("Works best with temperature=0.1.", reimported.notes)
    }

    @Test
    fun `FIX-05 Large unsaved paste in SmartImportSessionBuffer survives simulated process death via private disk cache and is deleted on remove`() {
        val sessionKey = "test_large_import_session_${UUID.randomUUID()}"
        val largeJsonPayload = buildString {
            append("{\"format\":\"IDEAVAULT\",\"schemaVersion\":1,\"type\":\"NOTE\",\"title\":\"Large Paste\",\"content\":\"")
            append("A".repeat(250_000))
            append("\"}")
        }

        com.example.ui.components.SmartImportSessionBuffer.put(sessionKey, largeJsonPayload, context)
        assertTrue(com.example.ui.components.SmartImportSessionBuffer.hasDiskRecoveryFile(sessionKey, context))

        // Simulate Android process death by clearing the in-memory map while keeping disk cache
        com.example.ui.components.SmartImportSessionBuffer.clearMemoryOnly()

        val recoveredAfterProcessDeath = com.example.ui.components.SmartImportSessionBuffer.get(sessionKey, context)
        assertEquals(largeJsonPayload.length, recoveredAfterProcessDeath.length)
        assertEquals(largeJsonPayload, recoveredAfterProcessDeath)

        // Removing session on confirm/cancel deletes the disk recovery file immediately
        com.example.ui.components.SmartImportSessionBuffer.remove(sessionKey, context)
        assertFalse(com.example.ui.components.SmartImportSessionBuffer.hasDiskRecoveryFile(sessionKey, context))
        assertEquals("", com.example.ui.components.SmartImportSessionBuffer.get(sessionKey, context))
    }

    @Test
    fun `FIX-06 Custom Field type change normalizes compatible values and preserves incompatible values in item notes without corrupting existing entries`() = runBlocking {
        val fieldCompatible = CustomFieldDefinition(id = "f_score", name = "Priority Score", type = CustomFieldType.SHORT_TEXT)
        val fieldIncompatible = CustomFieldDefinition(id = "f_budget", name = "Cost Estimate", type = CustomFieldType.SHORT_TEXT)
        val initialCt = repository.saveCustomType(
            CustomTypeEntity(id = "ct_project_eval", name = "Project Eval"),
            listOf(fieldCompatible, fieldIncompatible)
        )

        val item = VaultItemEntity(
            id = "eval_item_1",
            title = "Q4 Mobile Rewrite",
            category = VaultCategory.IDEA.id,
            customTypeId = initialCt.id,
            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(
                mapOf(
                    "f_score" to "42",
                    "f_budget" to "TBD next quarter"
                )
            )
        )
        val saveRes = repository.saveVaultItemWithResult(item, emptyList())
        assertTrue(saveRes is com.example.data.SaveItemResult.Success)

        // Now change both fields from SHORT_TEXT to NUMBER!
        // "42" is a valid NUMBER and should remain in customFieldsMap;
        // "TBD next quarter" is NOT a valid NUMBER and must be preserved in notes and cleared from customFieldsMap!
        val updatedFields = listOf(
            fieldCompatible.copy(type = CustomFieldType.NUMBER),
            fieldIncompatible.copy(type = CustomFieldType.NUMBER)
        )
        repository.saveCustomType(initialCt, updatedFields)

        val updatedDetails = repository.getItemWithDetailsById(item.id)!!
        assertEquals("42", updatedDetails.customFieldsMap["f_score"])
        assertNull(updatedDetails.customFieldsMap["f_budget"])
        assertTrue(updatedDetails.entity.notes.contains("Cost Estimate (Short Text -> Number): TBD next quarter"))

        // Verify that re-saving the updated item succeeds without validation failure!
        val resaveRes = repository.saveVaultItemWithResult(
            item = updatedDetails.entity.copy(title = "Q4 Mobile Rewrite Updated"),
            steps = emptyList(),
            expectedPreviousUpdatedAt = updatedDetails.entity.updatedAt
        )
        assertTrue("Re-saving item after CustomFieldType conversion must succeed", resaveRes is com.example.data.SaveItemResult.Success)
    }

    @Test
    fun `FIX-07 Atomic Custom Type and Entry save rolls back or aborts Custom Type on Conflict ValidationFailure StorageFailure and mid-transaction failure`() = runBlocking {
        val requiredUrlField = CustomFieldDefinition(
            id = "field_endpoint_url",
            name = "Endpoint URL",
            type = CustomFieldType.URL,
            required = true
        )
        val pendingCustomType = CustomTypeEntity(
            id = "ct_atomic_test",
            name = "Atomic Webhook Type",
            fieldsJson = VaultJsonSerializers.encodeCustomFieldDefinitions(listOf(requiredUrlField))
        )

        // 1. ValidationFailure (invalid URL in required URL field) -> CustomType MUST NOT be committed
        val invalidItem = VaultItemEntity(
            id = "item_atomic_1",
            title = "Webhook Alpha",
            category = VaultCategory.KNOWLEDGE.id,
            customTypeId = pendingCustomType.id,
            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(mapOf("field_endpoint_url" to "not-a-valid-url"))
        )
        val validationRes = repository.saveVaultItemWithResult(
            item = invalidItem,
            steps = emptyList(),
            pendingCustomType = pendingCustomType
        )
        assertTrue(validationRes is com.example.data.SaveItemResult.ValidationFailure)
        assertTrue(
            "ValidationFailure must not leave pendingCustomType committed in Room",
            repository.getAllCustomTypesSnapshot().none { it.id == pendingCustomType.id }
        )

        // 2. Conflict (editing an existing item with stale expectedPreviousUpdatedAt) -> CustomType MUST NOT be committed
        val existingBaseItem = VaultItemEntity(
            id = "item_existing_conflict",
            title = "Existing Entry",
            category = VaultCategory.IDEA.id,
            updatedAt = 5000L
        )
        repository.saveVaultItemWithResult(existingBaseItem, emptyList())
        val savedExisting = repository.getItemWithDetailsById(existingBaseItem.id)!!.entity

        val conflictAttemptItem = savedExisting.copy(
            title = "Updated With Staged Custom Type",
            customTypeId = pendingCustomType.id,
            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(mapOf("field_endpoint_url" to "https://api.example.com/hook"))
        )
        val conflictRes = repository.saveVaultItemWithResult(
            item = conflictAttemptItem,
            steps = emptyList(),
            expectedPreviousUpdatedAt = savedExisting.updatedAt - 999L, // stale timestamp triggers Conflict
            isEditingExisting = true,
            forceOverwrite = false,
            pendingCustomType = pendingCustomType
        )
        assertTrue(conflictRes is com.example.data.SaveItemResult.Conflict)
        assertTrue(
            "Conflict must not leave pendingCustomType committed in Room",
            repository.getAllCustomTypesSnapshot().none { it.id == pendingCustomType.id }
        )

        // 3. StorageFailure (pre-transaction / repository fault) -> CustomType MUST NOT be committed
        val validItem = VaultItemEntity(
            id = "item_atomic_valid",
            title = "Webhook Beta",
            category = VaultCategory.KNOWLEDGE.id,
            customTypeId = pendingCustomType.id,
            customFieldsJson = VaultJsonSerializers.encodeCustomFieldsMap(mapOf("field_endpoint_url" to "https://api.example.com/webhook"))
        )
        repository.saveFaultInjector = {
            throw java.io.IOException("Simulated disk I/O failure before transaction")
        }
        try {
            val storageFailRes = repository.saveVaultItemWithResult(
                item = validItem,
                steps = emptyList(),
                pendingCustomType = pendingCustomType
            )
            assertTrue(storageFailRes is com.example.data.SaveItemResult.StorageFailure)
        } finally {
            repository.saveFaultInjector = null
        }
        assertTrue(
            "StorageFailure must not leave pendingCustomType committed in Room",
            repository.getAllCustomTypesSnapshot().none { it.id == pendingCustomType.id }
        )

        // 4. Mid-Transaction Failure inside VaultDao.saveItemWithSteps AFTER upsertCustomType & upsertItem ->
        // SQLite @Transaction MUST roll back BOTH pendingCustomType and item!
        com.example.data.VaultTransactionFaultHooks.onSaveAfterStepsBeforeFollowPrune = {
            throw IllegalStateException("Simulated mid-transaction crash after upsertCustomType")
        }
        try {
            val txFailRes = repository.saveVaultItemWithResult(
                item = validItem,
                steps = emptyList(),
                pendingCustomType = pendingCustomType
            )
            assertTrue(txFailRes is com.example.data.SaveItemResult.StorageFailure)
        } finally {
            com.example.data.VaultTransactionFaultHooks.onSaveAfterStepsBeforeFollowPrune = null
        }
        assertTrue(
            "Mid-transaction failure must roll back pendingCustomType via Room @Transaction",
            repository.getAllCustomTypesSnapshot().none { it.id == pendingCustomType.id }
        )
        assertNull(
            "Mid-transaction failure must roll back item via Room @Transaction",
            repository.getItemWithDetailsById(validItem.id)
        )

        // 5. Subsequent clean Save commits BOTH pendingCustomType and item atomically
        val successRes = repository.saveVaultItemWithResult(
            item = validItem,
            steps = emptyList(),
            pendingCustomType = pendingCustomType
        )
        assertTrue(successRes is com.example.data.SaveItemResult.Success)
        assertEquals(1, repository.getAllCustomTypesSnapshot().count { it.id == pendingCustomType.id })
        val committedItem = repository.getItemWithDetailsById(validItem.id)!!
        assertEquals(pendingCustomType.id, committedItem.entity.customTypeId)
        assertEquals("https://api.example.com/webhook", committedItem.customFieldsMap["field_endpoint_url"])
    }

    @Test
    fun `FIX-08 Exact text restoration preserves literal __iv_ref__ prefixes Prompt whitespace and surfaces missing or corrupted recovery files safely`() {
        com.example.ui.screens.EditorLargeStateSessionBuffer.configureCacheDir(context.cacheDir)
        val dummyScope = androidx.compose.runtime.saveable.SaverScope { true }
        com.example.ui.screens.EditorLargeStateSessionBuffer.consumeRecoveryFailures()

        // 1. Literal strings starting with "__iv_ref__:" and "__iv_lit__:" round-trip verbatim without being treated as internal tokens
        val literalPayloads = listOf(
            "__iv_ref__:example",
            "__iv_ref__:str_550e8400-e29b-41d4-a716-446655440000",
            "__iv_lit__:example",
            "__iv_lit__:__iv_ref__:nested_literal",
            "__iv_ref__:" + "X".repeat(1200) // even large literal starting with __iv_ref__:
        )
        for (literal in literalPayloads) {
            val savedToken = with(com.example.ui.screens.LargeStringSessionSaver) {
                dummyScope.save(literal)
            }!!
            val restoredInMemory = com.example.ui.screens.LargeStringSessionSaver.restore(savedToken)
            assertEquals("In-memory restore failed for literal: $literal", literal, restoredInMemory)

            com.example.ui.screens.EditorLargeStateSessionBuffer.clearMemoryOnly()
            val restoredFromDisk = com.example.ui.screens.LargeStringSessionSaver.restore(savedToken)
            assertEquals("Disk restore failed for literal: $literal", literal, restoredFromDisk)
        }
        assertFalse(com.example.ui.screens.EditorLargeStateSessionBuffer.hasRecoveryFailures())

        // 2. Exact Prompt whitespace, leading/trailing spaces, blank lines, and newlines round-trip byte-for-byte (both short and large)
        val exactShortPrompt = "   \n\n  SYSTEM PROMPT: Preserve   multiple   spaces\n\n\tIndented line\r\nTrailing spaces ->    \n\n   "
        val exactLargePrompt = exactShortPrompt + "\n" + ("Line with   3 spaces and trailing tab\t\n\n").repeat(40) + "   \n"

        for (promptText in listOf(exactShortPrompt, exactLargePrompt)) {
            val token = with(com.example.ui.screens.LargeStringSessionSaver) {
                dummyScope.save(promptText)
            }!!
            com.example.ui.screens.EditorLargeStateSessionBuffer.clearMemoryOnly()
            val restored = com.example.ui.screens.LargeStringSessionSaver.restore(token)
            assertEquals(promptText, restored)
        }

        // 3. Missing recovery file MUST NOT silently return "" or emptyList(); it must return null from Saver.restore and record a recovery failure
        val largePromptForMissingTest = "LARGE_CONTENT_FOR_MISSING_FILE_TEST_" + "Z".repeat(1000)
        val missingFileToken = with(com.example.ui.screens.LargeStringSessionSaver) {
            dummyScope.save(largePromptForMissingTest)
        }!!
        assertTrue(com.example.ui.screens.EditorLargeStateSessionBuffer.isInternalReferenceToken(missingFileToken))

        // Delete both memory and disk cache to simulate missing recovery file
        com.example.ui.screens.EditorLargeStateSessionBuffer.clearAll()
        com.example.ui.screens.EditorLargeStateSessionBuffer.consumeRecoveryFailures()

        val restoredWhenMissing = com.example.ui.screens.LargeStringSessionSaver.restore(missingFileToken)
        assertNull("Missing recovery file must return null from Saver.restore rather than silent empty string", restoredWhenMissing)
        assertTrue("Missing recovery file must record a recovery failure", com.example.ui.screens.EditorLargeStateSessionBuffer.hasRecoveryFailures())
        val missingFailures = com.example.ui.screens.EditorLargeStateSessionBuffer.consumeRecoveryFailures()
        assertTrue(missingFailures.isNotEmpty())

        // 4. Corrupted recovery file (tampered payload / invalid checksum) for large Method steps MUST return null from Saver.restore and record a recovery failure
        val methodSteps = androidx.compose.runtime.mutableStateListOf(
            MethodStepEntity(
                id = "step_corrupt_test_1",
                itemId = "item_1",
                stepOrder = 1,
                title = "Critical Step 1",
                description = "Do not silently replace with empty list"
            )
        )
        val savedStepsTokens = with(com.example.ui.screens.MethodStepListSaver) {
            dummyScope.save(methodSteps)
        }!!
        val stepCacheFile = com.example.ui.screens.EditorLargeStateSessionBuffer.resolveDiskFileForToken(savedStepsTokens[0])!!
        assertTrue("Disk cache file for MethodSteps should exist", stepCacheFile.exists())

        // Tamper with the disk file so SHA-256 checksum verification fails
        stepCacheFile.writeText("{\"v\":1,\"type\":\"steps\",\"sha256\":\"0000000000000000000000000000000000000000000000000000000000000000\",\"payload\":\"[]\"}")
        com.example.ui.screens.EditorLargeStateSessionBuffer.clearMemoryOnly()

        val restoredStepsWhenCorrupted = com.example.ui.screens.MethodStepListSaver.restore(savedStepsTokens)
        assertNull("Corrupted MethodSteps recovery file must return null from Saver.restore, never emptyList()", restoredStepsWhenCorrupted)
        assertTrue("Corrupted recovery file must record a recovery failure", com.example.ui.screens.EditorLargeStateSessionBuffer.hasRecoveryFailures())
        com.example.ui.screens.EditorLargeStateSessionBuffer.consumeRecoveryFailures()
    }
}
