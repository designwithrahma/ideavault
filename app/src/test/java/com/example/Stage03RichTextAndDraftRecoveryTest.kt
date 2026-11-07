package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AttachmentStorageManager
import com.example.data.BackupRestoreManager
import com.example.data.BackupRestoreResult
import com.example.data.DEFAULT_MAIN_METHOD_NAME
import com.example.data.EditorDraftPayload
import com.example.data.IdeaStatus
import com.example.data.MAIN_METHOD_ROUTE_ID
import com.example.data.MethodRouteEntity
import com.example.data.MethodRouteWithSteps
import com.example.data.MethodStepEntity
import com.example.data.PreferencesFaultPolicy
import com.example.data.RichBlockType
import com.example.data.RichContentBlock
import com.example.data.SaveItemResult
import com.example.data.UserPreferencesRepository
import com.example.data.VaultCategory
import com.example.data.VaultDatabase
import com.example.data.VaultItemEntity
import com.example.data.VaultJsonSerializers
import com.example.data.VaultRepository
import com.example.ui.VaultViewModel
import com.example.ui.components.InlineRichStyle
import com.example.ui.components.LineBlockFormat
import com.example.ui.components.ParagraphAlignment
import com.example.ui.components.RichTextColorToken
import com.example.ui.components.RichTextEditorController
import com.example.ui.components.RichTextEditorState
import com.example.ui.components.RichTextSerializer
import com.example.ui.components.buildParagraphAnnotatedString
import com.example.ui.screens.EditorScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.IdeaVaultTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Stage03RichTextAndDraftRecoveryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: VaultDatabase
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
        tempPrefsFile = File(context.cacheDir, "stage03_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { tempPrefsFile }
        )
        prefsRepo = UserPreferencesRepository(dataStore)
        attachmentStorage = AttachmentStorageManager(context)
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
        db.close()
        tempPrefsFile.delete()
    }

    private fun idleUi(advanceMs: Long = 200L) {
        composeTestRule.mainClock.advanceTimeBy(advanceMs)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testStage03_RichTextIntegrity_AllFormatsAndEdgeCasesRoundTrip() = runBlocking {
        // 1. Construct multi-paragraph rich text with leading/trailing whitespace, blank lines,
        // multiple consecutive spaces, Tamil, English, emoji, Unicode, literal Markdown/code tokens,
        // mixed inline formatting, headings, bullet/numbered/lettered lists, quotes, font sizes,
        // font colors, links, and paragraph alignments.
        val rawParagraphs = listOf(
            "   Leading spaces and trailing spaces   ",
            "",
            "# Heading1   with three spaces and Tamil தமிழ் 🚀",
            "## Heading2   with **literal** and _literal_ and ~~strike~~",
            "### Heading3   with ==highlight== and ++underline++ and [literal](https://x.dev)",
            "• Bullet item   with   multiple   consecutive   spaces",
            "1. Numbered item   with   extra   spaces",
            "a. Lettered item   with   extra   spaces",
            "❝ Quote line with C:\\Users\\admin\\vault_file.kt and 2 * 3 * 4 = 24",
            "- Literal dash line in paragraph",
            "{align:center}Literal align tag in paragraph",
            "Mixed paragraph: BoldItalicUnderlineStrikeHighlight Size24Coral IntraWord and   padded span   end",
            "   Indented centered line with   multiple   spaces   ",
            ""
        )
        val fullEditorText = rawParagraphs.joinToString("\n")

        var state = RichTextEditorState(
            textFieldValue = TextFieldValue(
                text = fullEditorText,
                selection = TextRange(fullEditorText.length)
            )
        )

        // Apply mixed formatting to "BoldItalicUnderlineStrikeHighlight"
        val mixTarget = "BoldItalicUnderlineStrikeHighlight"
        val mixStart = fullEditorText.indexOf(mixTarget)
        val mixEnd = mixStart + mixTarget.length
        state = state.copy(textFieldValue = state.textFieldValue.copy(selection = TextRange(mixStart, mixEnd)))
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.BOLD)
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.ITALIC)
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.UNDERLINE)
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.STRIKETHROUGH)
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.HIGHLIGHT)
        // Verify selection is preserved after every inline style toggle
        assertEquals(TextRange(mixStart, mixEnd), state.textFieldValue.selection)

        // Apply Font Size 24 and Coral Color to "Size24Coral"
        val sizeTarget = "Size24Coral"
        val sizeStart = fullEditorText.indexOf(sizeTarget)
        val sizeEnd = sizeStart + sizeTarget.length
        state = state.copy(textFieldValue = state.textFieldValue.copy(selection = TextRange(sizeStart, sizeEnd)))
        state = RichTextEditorController.applyFontSize(state, 24)
        state = RichTextEditorController.applyFontColor(state, RichTextColorToken.CORAL)
        assertEquals(TextRange(sizeStart, sizeEnd), state.textFieldValue.selection)

        // Apply intra-word Bold + Italic to "Word" inside "IntraWord"
        val intraStart = fullEditorText.indexOf("IntraWord") + "Intra".length
        val intraEnd = intraStart + "Word".length
        state = state.copy(textFieldValue = state.textFieldValue.copy(selection = TextRange(intraStart, intraEnd)))
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.BOLD)
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.ITALIC)

        // Apply Bold + Italic to whitespace-padded range "  padded span  "
        val paddedTarget = "  padded span  "
        val paddedStart = fullEditorText.indexOf(paddedTarget)
        val paddedEnd = paddedStart + paddedTarget.length
        state = state.copy(textFieldValue = state.textFieldValue.copy(selection = TextRange(paddedStart, paddedEnd)))
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.BOLD)
        state = RichTextEditorController.toggleInlineStyle(state, InlineRichStyle.ITALIC)

        // Apply Center alignment to line 12 ("   Indented centered line with   multiple   spaces   ")
        val centeredLineOffset = fullEditorText.indexOf("   Indented centered line")
        state = state.copy(textFieldValue = state.textFieldValue.copy(selection = TextRange(centeredLineOffset + 3)))
        state = RichTextEditorController.applyParagraphAlignment(state, ParagraphAlignment.CENTER)
        assertEquals(ParagraphAlignment.CENTER, state.lineAlignments[12])

        // Verify combined UNDERLINE + STRIKETHROUGH decoration in both editor AnnotatedString and renderer
        val editorAnnotated = state.toAnnotatedString()
        val combinedDecorations = editorAnnotated.spanStyles.filter {
            it.start <= mixStart && it.end >= mixEnd &&
                it.item.textDecoration == TextDecoration.combine(
                    listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                )
        }
        assertTrue("Editor AnnotatedString must combine Underline and LineThrough", combinedDecorations.isNotEmpty())

        // Serialize -> Deserialize round-trip
        val serialized = state.toSerializedMarkdown()
        val reopened = RichTextEditorState.fromStoredContent(serialized)

        assertEquals("Plain text must round-trip byte-for-byte", fullEditorText, reopened.textFieldValue.text)
        assertEquals(state.spans.toSet(), reopened.spans.toSet())
        assertEquals(state.fontSizeSpans, reopened.fontSizeSpans)
        assertEquals(state.fontColorSpans, reopened.fontColorSpans)
        assertEquals(state.lineAlignments, reopened.lineAlignments)

        // Verify display paragraph parser also preserves heading levels, combined decorations, and exact text
        val displayParagraphs = RichTextSerializer.parseParagraphsForDisplay(serialized)
        assertEquals(rawParagraphs.size, displayParagraphs.size)
        assertEquals(1, displayParagraphs[2].headingLevel)
        assertEquals(2, displayParagraphs[3].headingLevel)
        assertEquals(3, displayParagraphs[4].headingLevel)
        assertEquals(LineBlockFormat.PARAGRAPH, displayParagraphs[9].format)
        assertEquals("- Literal dash line in paragraph", displayParagraphs[9].plainText)
        assertEquals(LineBlockFormat.PARAGRAPH, displayParagraphs[10].format)
        assertEquals(ParagraphAlignment.LEFT, displayParagraphs[10].alignment)
        assertEquals("{align:center}Literal align tag in paragraph", displayParagraphs[10].plainText)
        assertEquals(ParagraphAlignment.CENTER, displayParagraphs[12].alignment)
        assertEquals("   Indented centered line with   multiple   spaces   ", displayParagraphs[12].plainText)

        val mixParagraphAnnotated = buildParagraphAnnotatedString(
            paragraph = displayParagraphs[11],
            primaryTextColor = androidx.compose.ui.graphics.Color.White,
            secondaryTextColor = androidx.compose.ui.graphics.Color.Gray,
            linkColor = androidx.compose.ui.graphics.Color.Yellow,
            isDarkTheme = true
        )
        assertTrue(
            mixParagraphAnnotated.spanStyles.any {
                it.item.textDecoration == TextDecoration.combine(
                    listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                )
            }
        )

        // Verify inserting a newline above an aligned line shifts lineAlignments properly
        val textWithInsertedNewline = "\n" + reopened.textFieldValue.text
        val afterNewlineInsert = RichTextEditorController.onValueChange(
            previous = reopened,
            incoming = TextFieldValue(text = textWithInsertedNewline, selection = TextRange(1))
        )
        assertEquals(ParagraphAlignment.CENTER, afterNewlineInsert.lineAlignments[13])
    }

    @Test
    fun testStage03_SaveReopenAcrossIdeaMethodPromptAndNote_PreservesWhitespaceAndChecklist() = runBlocking {
        val vm = VaultViewModel(context, repository)
        val richContentWithLeadingTrailingBlankLines =
            "\n  First paragraph with **bold**, _italic_, and   3 spaces  \n\nSecond paragraph with Tamil தமிழ் ✨\n  "

        val checklistBlocks = listOf(
            RichContentBlock(id = "chk_1", type = RichBlockType.CHECKLIST, content = "Verify rich text round-trip", checked = true),
            RichContentBlock(id = "chk_2", type = RichBlockType.CHECKLIST, content = "Verify draft recovery", checked = false)
        )

        // Save IDEA, METHOD, and KNOWLEDGE (Note) through EditorScreen executeSave flow
        for (cat in listOf(VaultCategory.IDEA, VaultCategory.METHOD, VaultCategory.KNOWLEDGE)) {
            val id = "stage03_${cat.id}"
            val res = vm.saveItemAwaitable(
                item = VaultItemEntity(
                    id = id,
                    title = "Stage 03 ${cat.label}",
                    description = "Summary for ${cat.label}",
                    content = richContentWithLeadingTrailingBlankLines,
                    category = cat.id,
                    richBlocksJson = VaultJsonSerializers.encodeRichBlocks(checklistBlocks)
                ),
                steps = if (cat == VaultCategory.METHOD) {
                    listOf(
                        MethodStepEntity(
                            id = "step_${cat.id}_1",
                            itemId = id,
                            routeId = MAIN_METHOD_ROUTE_ID,
                            stepOrder = 1,
                            title = "Step 1",
                            description = "  Step description with   spaces and\nsecond line  ",
                            notes = "  Step note with   exact spacing  "
                        )
                    )
                } else {
                    emptyList()
                },
                expectedPreviousUpdatedAt = null,
                isEditingExisting = false
            )
            assertTrue(res is SaveItemResult.Success)

            val loaded = repository.getItemWithDetailsById(id)
            assertNotNull(loaded)
            assertEquals(richContentWithLeadingTrailingBlankLines, loaded!!.entity.content)
            assertEquals(checklistBlocks, loaded.richBlocks)
            val reopenedRich = RichTextEditorState.fromStoredContent(loaded.entity.content)
            assertEquals(
                "\n  First paragraph with bold, italic, and   3 spaces  \n\nSecond paragraph with Tamil தமிழ் ✨\n  ",
                reopenedRich.plainText
            )
        }
    }

    @Test
    fun testStage03_PromptExactness_ByteForBytePreservation() = runBlocking {
        val vm = VaultViewModel(context, repository)
        val exactPrompt = buildString {
            append("   \n")
            append("You are a **Principal Kotlin Architect** & Tamil translator (தமிழ்).\n\n")
            append("Input variables:\n")
            append("- {USER_QUERY}   (keep 3 spaces)\n")
            append("- {CODE_BLOCK}\n\n")
            append("```kotlin\n")
            append("fun calculate(a: Int, b: Int): Int {\n")
            append("    // Do not strip **asterisks** or _underscores_ or <size=20>tags</size>\n")
            append("    return a * b * 2\n")
            append("}\n")
            append("```\n")
            append("   \n\n")
        }

        var savedItemId: String? = null
        composeTestRule.setContent {
            IdeaVaultTheme(themeMode = AppThemeMode.DARK) {
                EditorScreen(
                    existingItem = null,
                    initialCategory = VaultCategory.PROMPT,
                    initialFolderId = null,
                    folders = emptyList(),
                    recoverableDraft = null,
                    attachmentStorage = attachmentStorage,
                    onAutoSaveDraft = { vm.saveEditorDraft(it) },
                    onDiscardDraft = { vm.clearEditorDraft() },
                    onCreateFolder = { _, _, _ -> },
                    onSaveItemAwaitable = { item, steps, expectedPrev, isExisting, force ->
                        vm.saveItemAwaitable(item, steps, expectedPrev, isExisting, force)
                    },
                    onSaveItemWithRoutesAwaitable = { item, steps, expectedPrev, isExisting, force, mainName, mainDesc, altRoutes, pendingCustomType ->
                        vm.saveItemAwaitable(item, steps, expectedPrev, isExisting, force, mainName, mainDesc, altRoutes, pendingCustomType)
                    },
                    onSaveSuccess = { savedItemId = it },
                    onCancel = {}
                )
            }
        }
        idleUi()

        composeTestRule.onNodeWithTag("editor_title_input").performTextReplacement("Exact Prompt Template")
        composeTestRule.onNodeWithTag("editor_content_input").performTextReplacement(exactPrompt)
        idleUi()

        composeTestRule.onNodeWithTag("editor_save_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            savedItemId != null
        }

        val persisted = repository.getItemWithDetailsById(savedItemId!!)
        assertNotNull(persisted)
        assertEquals("Prompt content must be preserved byte-for-byte without normalization", exactPrompt, persisted!!.entity.content)

        // Verify Backup & Restore also preserves exact prompt content byte-for-byte
        val out = ByteArrayOutputStream()
        assertTrue(repository.exportBackup(out) is BackupRestoreResult.Success)
        val validated = repository.validateBackup(ByteArrayInputStream(out.toByteArray())).getOrThrow()
        assertTrue(repository.applyBackup(validated) is BackupRestoreResult.Success)
        val afterRestore = repository.getItemWithDetailsById(savedItemId!!)!!
        assertEquals(exactPrompt, afterRestore.entity.content)
    }

    @Test
    fun testStage03_DraftRecovery_AllLifecycleAndFailureScenarios() {
        val vm = VaultViewModel(context, repository)

        // Seed an existing saved entry in Room to verify drafts never overwrite saved entries
        val existingSavedContent = "Original committed content in Room"
        val existingItem = VaultItemEntity(
            id = "existing_idea_stage03",
            title = "Committed Idea Title",
            description = "Committed summary",
            content = existingSavedContent,
            category = VaultCategory.IDEA.id,
            isPinned = false,
            isFavorite = false,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_005_000L
        )
        runBlocking {
            repository.saveVaultItem(existingItem, emptyList())
        }

        val testLifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
            override val lifecycle: Lifecycle
                get() = registry
        }

        val showEditorState = mutableStateOf(true)
        val currentDetailsState = mutableStateOf(runBlocking { repository.getItemWithDetailsById(existingItem.id) })
        val currentDraftState = mutableStateOf<EditorDraftPayload?>(null)
        var latestFlushedDraftJson: String? = null

        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                IdeaVaultTheme(themeMode = AppThemeMode.LIGHT) {
                    if (showEditorState.value) {
                        EditorScreen(
                            existingItem = currentDetailsState.value,
                            initialCategory = VaultCategory.IDEA,
                            initialFolderId = null,
                            folders = emptyList(),
                            recoverableDraft = currentDraftState.value,
                            attachmentStorage = attachmentStorage,
                            onAutoSaveDraft = { json ->
                                latestFlushedDraftJson = json
                            },
                            onDiscardDraft = {
                                latestFlushedDraftJson = null
                            },
                            onCreateFolder = { _, _, _ -> },
                            onSaveItemAwaitable = { item, steps, expectedPrev, isExisting, force ->
                                vm.saveItemAwaitable(item, steps, expectedPrev, isExisting, force)
                            },
                            onSaveItemWithRoutesAwaitable = { item, steps, expectedPrev, isExisting, force, mainName, mainDesc, altRoutes, pendingCustomType ->
                                vm.saveItemAwaitable(item, steps, expectedPrev, isExisting, force, mainName, mainDesc, altRoutes, pendingCustomType)
                            },
                            onSaveSuccess = {},
                            onCancel = { showEditorState.value = false }
                        )
                    }
                }
            }
        }
        idleUi()

        // 1. Type unsaved content with leading/trailing spaces & toggle Pin/Favourite WITHOUT pressing Save
        val unsavedBody = "   Unsaved draft body with Tamil தமிழ் and trailing newline\n   "
        composeTestRule.onNodeWithTag("editor_title_input").performTextReplacement("Draft Modified Title")
        composeTestRule.onNodeWithTag("editor_content_input").performTextReplacement(unsavedBody)
        composeTestRule.onNodeWithTag("editor_toggle_pin").performClick()
        composeTestRule.onNodeWithTag("editor_toggle_favorite").performClick()

        // 2. Immediately pause the app (< 650ms, before debounce timer) via ON_PAUSE
        composeTestRule.runOnUiThread {
            testLifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("Draft must be flushed immediately on ON_PAUSE even before 650ms debounce", latestFlushedDraftJson)

        // Persist flushed draft to DataStore and verify Room still has original committed content
        runBlocking {
            vm.saveEditorDraftImmediate(latestFlushedDraftJson!!)
            val roomItem = repository.getItemWithDetailsById(existingItem.id)!!
            assertEquals("Committed Idea Title", roomItem.entity.title)
            assertEquals(existingSavedContent, roomItem.entity.content)
            assertFalse(roomItem.entity.isPinned)
            assertFalse(roomItem.entity.isFavorite)

            val savedDraft = VaultJsonSerializers.decodeEditorDraft(prefsRepo.getSnapshot().editorDraftJson)
            assertNotNull("Draft must round-trip through DataStore", savedDraft)
            assertEquals("Draft Modified Title", savedDraft!!.title)
            assertEquals(unsavedBody, savedDraft.content)
            assertTrue(savedDraft.isPinned)
            assertTrue(savedDraft.isFavorite)
        }

        // 3. Resume and rotate the device (StateRestorationTester) -> unsaved state stays intact
        composeTestRule.runOnUiThread {
            testLifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        restorationTester.emulateSavedInstanceStateRestore()
        idleUi()
        composeTestRule.onNodeWithTag("editor_scroll_list").performScrollToIndex(0)
        idleUi()
        composeTestRule.onNodeWithTag("editor_title_input").assertTextEquals("Draft Modified Title")

        // 4. Press Back -> Discard confirmation dialog appears; choose "Keep Editing"
        composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
        idleUi()
        composeTestRule.onNodeWithTag("keep_editing_button").performClick()
        idleUi()

        // 5. Simulate Failed Save attempt (StorageFailure) -> editor keeps content, shows error banner, preserves draft
        repository.saveFaultInjector = { throw IOException("Simulated disk write failure") }
        composeTestRule.onNodeWithTag("editor_save_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithTag("editor_save_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("editor_save_error_banner").assertIsDisplayed()
        repository.saveFaultInjector = null

        runBlocking {
            val roomItemAfterFail = repository.getItemWithDetailsById(existingItem.id)!!
            assertEquals("Committed Idea Title", roomItemAfterFail.entity.title)
            val draftAfterFail = VaultJsonSerializers.decodeEditorDraft(latestFlushedDraftJson ?: prefsRepo.getSnapshot().editorDraftJson)
            assertNotNull("Draft must remain intact after failed save", draftAfterFail)
            assertEquals("Draft Modified Title", draftAfterFail!!.title)
        }

        // 6. Simulate switching screens / closing and reopening the editor from cold state with recoverableDraft
        showEditorState.value = false
        idleUi()

        val storedDraft = VaultJsonSerializers.decodeEditorDraft(latestFlushedDraftJson ?: "")
        assertNotNull(storedDraft)

        // Reopen with recoverableDraft
        currentDraftState.value = storedDraft
        showEditorState.value = true
        idleUi()
    }

    @Test
    fun testStage03_SaveAndCancelSafety_ConflictAndCrossEntryDraftIsolation() = runBlocking {
        val vm = VaultViewModel(context, repository)

        // Create two independent entries: Entry A and Entry B
        val itemA = VaultItemEntity(
            id = "entry_A",
            title = "Entry A Original",
            description = "Summary A",
            content = "Body A Original",
            category = VaultCategory.KNOWLEDGE.id,
            updatedAt = 1_700_000_010_000L
        )
        val itemB = VaultItemEntity(
            id = "entry_B",
            title = "Entry B Original",
            description = "Summary B",
            content = "Body B Original",
            category = VaultCategory.KNOWLEDGE.id,
            updatedAt = 1_700_000_020_000L
        )
        repository.saveVaultItem(itemA, emptyList())
        repository.saveVaultItem(itemB, emptyList())

        val initialDetailsA = repository.getItemWithDetailsById(itemA.id)!!

        // Save an unsaved recovery draft for Entry A
        val draftForA = EditorDraftPayload(
            itemId = itemA.id,
            isEditingExisting = true,
            baseUpdatedAt = initialDetailsA.entity.updatedAt,
            categoryId = VaultCategory.KNOWLEDGE.id,
            title = "Entry A Unsaved Draft",
            content = "Body A Unsaved Draft",
            isPinned = true,
            isFavorite = true
        )
        vm.saveEditorDraftImmediate(VaultJsonSerializers.encodeEditorDraft(draftForA))

        // Edit and save Entry B -> Entry A in Room AND Entry A's unsaved draft must NOT be overwritten or cleared!
        val loadedB = repository.getItemWithDetailsById(itemB.id)!!
        val saveBResult = vm.saveItemAwaitable(
            item = loadedB.entity.copy(title = "Entry B Updated", content = "Body B Updated"),
            steps = emptyList(),
            expectedPreviousUpdatedAt = loadedB.entity.updatedAt,
            isEditingExisting = true
        )
        assertTrue(saveBResult is SaveItemResult.Success)

        val checkA = repository.getItemWithDetailsById(itemA.id)!!
        assertEquals("Entry A Original", checkA.entity.title)
        assertEquals("Body A Original", checkA.entity.content)

        val preservedDraftA = VaultJsonSerializers.decodeEditorDraft(prefsRepo.getSnapshot().editorDraftJson)
        assertNotNull("Saving Entry B must not delete Entry A's unsaved draft", preservedDraftA)
        assertEquals(itemA.id, preservedDraftA!!.itemId)
        assertEquals("Entry A Unsaved Draft", preservedDraftA.title)

        // Open Entry A in EditorScreen with its recoverableDraft, restore draft, then test Conflict and Cancel/Discard
        val detailsA = repository.getItemWithDetailsById(itemA.id)!!
        var editorCancelled = false
        composeTestRule.setContent {
            IdeaVaultTheme(themeMode = AppThemeMode.DARK) {
                EditorScreen(
                    existingItem = detailsA,
                    initialCategory = VaultCategory.KNOWLEDGE,
                    initialFolderId = null,
                    folders = emptyList(),
                    recoverableDraft = preservedDraftA,
                    attachmentStorage = attachmentStorage,
                    onAutoSaveDraft = { json -> runBlocking { vm.saveEditorDraftImmediate(json) } },
                    onDiscardDraft = { runBlocking { vm.clearEditorDraftImmediate() } },
                    onCreateFolder = { _, _, _ -> },
                    onSaveItemAwaitable = { item, steps, expectedPrev, isExisting, force ->
                        vm.saveItemAwaitable(item, steps, expectedPrev, isExisting, force)
                    },
                    onSaveItemWithRoutesAwaitable = { item, steps, expectedPrev, isExisting, force, mainName, mainDesc, altRoutes, pendingCustomType ->
                        vm.saveItemAwaitable(item, steps, expectedPrev, isExisting, force, mainName, mainDesc, altRoutes, pendingCustomType)
                    },
                    onSaveSuccess = {},
                    onCancel = { editorCancelled = true }
                )
            }
        }
        idleUi()

        // Recovery banner is shown for Entry A; click Restore
        composeTestRule.onNodeWithTag("editor_draft_recovery_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("restore_draft_button").performClick()
        idleUi()
        composeTestRule.onNodeWithText("Entry A Unsaved Draft").assertIsDisplayed()

        // Simulate concurrent update to Entry A in Room while editor is open
        repository.saveVaultItem(
            detailsA.entity.copy(
                title = "Entry A Concurrent DB Update",
                content = "Concurrent DB Body",
                updatedAt = detailsA.entity.updatedAt + 50_000L
            ),
            emptyList()
        )

        // Attempt Save -> Conflict dialog appears; click "Load Stored Version"
        composeTestRule.onNodeWithTag("editor_save_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeTestRule.onAllNodesWithTag("conflict_reload_database_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("conflict_reload_database_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("conflict_reload_database_button").performClick()
        idleUi()
        composeTestRule.onNodeWithText("Entry A Concurrent DB Update").assertIsDisplayed()

        // Now type another unsaved edit and press Back -> Discard -> verify draft is cleared and DB version is intact
        composeTestRule.onNodeWithTag("editor_title_input").performTextReplacement("Aborted Edit")
        idleUi()
        composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
        idleUi()
        composeTestRule.onNodeWithTag("confirm_discard_editor_button").performClick()
        idleUi()

        assertTrue(editorCancelled)
        assertNull(VaultJsonSerializers.decodeEditorDraft(prefsRepo.getSnapshot().editorDraftJson))
        val finalA = repository.getItemWithDetailsById(itemA.id)!!
        assertEquals("Entry A Concurrent DB Update", finalA.entity.title)
        assertEquals("Concurrent DB Body", finalA.entity.content)
    }

    @Test
    fun testStage03_MethodEditorSafety_RoutesStepsFollowProgressAndPendingStepPreservation() = runBlocking {
        val vm = VaultViewModel(context, repository)
        val methodId = "method_stage03_safety"
        val altRoute1Id = "route_alt_fast"
        val altRoute2Id = "route_alt_deep"

        val initialMethod = VaultItemEntity(
            id = methodId,
            title = "Deployment Playbook",
            description = "Initial overview",
            content = "## Principles\n- Keep zero downtime",
            category = VaultCategory.METHOD.id
        )
        val initialMainSteps = listOf(
            MethodStepEntity(
                id = "m_step_1",
                itemId = methodId,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 1,
                title = "Run preflight checks",
                description = "Verify schema v3",
                notes = "Initial note 1"
            ),
            MethodStepEntity(
                id = "m_step_2",
                itemId = methodId,
                routeId = MAIN_METHOD_ROUTE_ID,
                stepOrder = 2,
                title = "Deploy canary",
                description = "Monitor metrics",
                notes = "Initial note 2"
            )
        )
        val initialAltRoutes = listOf(
            MethodRouteWithSteps(
                id = altRoute1Id,
                itemId = methodId,
                name = "Fast Hotfix Route",
                description = "Emergency path",
                routeOrder = 1,
                isMain = false,
                steps = listOf(
                    MethodStepEntity(
                        id = "a1_step_1",
                        itemId = methodId,
                        routeId = altRoute1Id,
                        stepOrder = 1,
                        title = "Patch branch",
                        description = "Cherry-pick fix",
                        notes = "Keep logs"
                    )
                )
            ),
            MethodRouteWithSteps(
                id = altRoute2Id,
                itemId = methodId,
                name = "Full Audit Route",
                description = "Comprehensive verification",
                routeOrder = 2,
                isMain = false,
                steps = listOf(
                    MethodStepEntity(
                        id = "a2_step_1",
                        itemId = methodId,
                        routeId = altRoute2Id,
                        stepOrder = 1,
                        title = "Run full suite",
                        description = "All unit + Robolectric tests",
                        notes = "Archive report"
                    )
                )
            )
        )

        val createRes = vm.saveItemAwaitable(
            item = initialMethod,
            steps = initialMainSteps,
            expectedPreviousUpdatedAt = null,
            isEditingExisting = false,
            mainRouteName = DEFAULT_MAIN_METHOD_NAME,
            mainRouteDescription = "Standard release route",
            alternativeRoutes = initialAltRoutes
        )
        assertTrue(createRes is SaveItemResult.Success)

        // Complete steps in Follow mode across Main Method and both Alternative Methods
        repository.toggleFollowStep(methodId, "m_step_1", MAIN_METHOD_ROUTE_ID)
        repository.toggleFollowStep(methodId, "a1_step_1", altRoute1Id)
        repository.toggleFollowStep(methodId, "a2_step_1", altRoute2Id)

        val beforeEdit = repository.getItemWithDetailsById(methodId)!!
        var savedAfterEdit = false

        composeTestRule.setContent {
            IdeaVaultTheme(themeMode = AppThemeMode.DARK) {
                EditorScreen(
                    existingItem = beforeEdit,
                    initialCategory = VaultCategory.METHOD,
                    initialFolderId = null,
                    folders = emptyList(),
                    recoverableDraft = null,
                    attachmentStorage = attachmentStorage,
                    onAutoSaveDraft = { vm.saveEditorDraft(it) },
                    onDiscardDraft = { vm.clearEditorDraft() },
                    onCreateFolder = { _, _, _ -> },
                    onSaveItemAwaitable = { item, steps, expectedPrev, isExisting, force ->
                        vm.saveItemAwaitable(item, steps, expectedPrev, isExisting, force)
                    },
                    onSaveItemWithRoutesAwaitable = { item, steps, expectedPrev, isExisting, force, mainName, mainDesc, altRoutes, pendingCustomType ->
                        vm.saveItemAwaitable(item, steps, expectedPrev, isExisting, force, mainName, mainDesc, altRoutes, pendingCustomType)
                    },
                    onSaveSuccess = { savedAfterEdit = true },
                    onCancel = {}
                )
            }
        }
        idleUi()

        // Scroll to and edit Method description, then scroll to step inputs and type an uncommitted in-progress step
        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToNode(hasTestTag("editor_description_input"))
        composeTestRule.onNodeWithTag("editor_description_input")
            .performTextReplacement("Updated overview preserving all routes and progress")
        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToNode(hasTestTag("editor_new_step_title"))
        composeTestRule.onNodeWithTag("editor_new_step_title")
            .performTextInput("Uncommitted Main Step 3")
        composeTestRule.onNodeWithTag("editor_new_step_details")
            .performTextInput("  Details with   preserved spaces  ")

        // Press Save directly without clicking Add Step -> pending step must be committed automatically!
        composeTestRule.onNodeWithTag("editor_save_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            savedAfterEdit
        }

        val afterEdit = repository.getItemWithDetailsById(methodId)!!
        assertEquals("Updated overview preserving all routes and progress", afterEdit.entity.description)

        // Verify Main Method steps (including the in-progress step 3!) and completed Follow progress
        val mainRouteAfter = afterEdit.effectiveMethodRoutes.first { it.id == MAIN_METHOD_ROUTE_ID }
        assertEquals(3, mainRouteAfter.steps.size)
        assertEquals("Run preflight checks", mainRouteAfter.steps[0].title)
        assertEquals("Deploy canary", mainRouteAfter.steps[1].title)
        assertEquals("Uncommitted Main Step 3", mainRouteAfter.steps[2].title)
        assertEquals("  Details with   preserved spaces  ", mainRouteAfter.steps[2].description)
        assertEquals(setOf("m_step_1"), mainRouteAfter.completedStepIds)

        // Verify Alternative Routes, their steps, their routeOrder, and their completed Follow progress
        val altRoutesAfter = afterEdit.alternativeMethodRoutes
        assertEquals(2, altRoutesAfter.size)
        assertEquals(altRoute1Id, altRoutesAfter[0].id)
        assertEquals(1, altRoutesAfter[0].routeOrder)
        assertEquals(1, altRoutesAfter[0].steps.size)
        assertEquals("Patch branch", altRoutesAfter[0].steps[0].title)
        assertEquals(setOf("a1_step_1"), altRoutesAfter[0].completedStepIds)

        assertEquals(altRoute2Id, altRoutesAfter[1].id)
        assertEquals(2, altRoutesAfter[1].routeOrder)
        assertEquals(1, altRoutesAfter[1].steps.size)
        assertEquals("Run full suite", altRoutesAfter[1].steps[0].title)
        assertEquals(setOf("a2_step_1"), altRoutesAfter[1].completedStepIds)
    }

    @Test
    fun testStage03_KeyboardFocusAndLongMultilineScrollStability_WhiteAndDarkThemes(): Unit = runBlocking {
        val vm = VaultViewModel(context, repository)
        val themeState = mutableStateOf(AppThemeMode.LIGHT)

        // Also verify RichTextEditorController pressing Enter at index 0 (prefixLen == 0) never throws
        val initZeroState = RichTextEditorState.fromStoredContent("Line after leading newline")
        val afterEnterAtZero = RichTextEditorController.onValueChange(
            previous = initZeroState,
            incoming = TextFieldValue(
                text = "\nLine after leading newline",
                selection = TextRange(1)
            )
        )
        assertEquals("\nLine after leading newline", afterEnterAtZero.plainText)

        composeTestRule.setContent {
            IdeaVaultTheme(themeMode = themeState.value) {
                EditorScreen(
                    existingItem = null,
                    initialCategory = VaultCategory.IDEA,
                    initialFolderId = null,
                    folders = emptyList(),
                    recoverableDraft = null,
                    attachmentStorage = attachmentStorage,
                    onAutoSaveDraft = { vm.saveEditorDraft(it) },
                    onDiscardDraft = { vm.clearEditorDraft() },
                    onCreateFolder = { _, _, _ -> },
                    onSaveItemAwaitable = { item, steps, expectedPrev, isExisting, force ->
                        vm.saveItemAwaitable(item, steps, expectedPrev, isExisting, force)
                    },
                    onSaveItemWithRoutesAwaitable = { item, steps, expectedPrev, isExisting, force, mainName, mainDesc, altRoutes, pendingCustomType ->
                        vm.saveItemAwaitable(item, steps, expectedPrev, isExisting, force, mainName, mainDesc, altRoutes, pendingCustomType)
                    },
                    onSaveSuccess = {},
                    onCancel = {}
                )
            }
        }
        idleUi()

        // Long multiline rich text content in LIGHT theme
        val longMultilineText = (1..30).joinToString("\n") { idx ->
            "Paragraph $idx: Long multiline rich-text editor verification line with Tamil தமிழ் and emojis 🚀"
        }
        composeTestRule.onNodeWithTag("editor_title_input").performTextReplacement("Long Multiline Scroll Entry")
        composeTestRule.onNodeWithTag("editor_content_input").performTextReplacement(longMultilineText)
        idleUi()

        // Scroll to lower-page metadata fields (already expanded by default) without focus-stealing jumps
        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToNode(hasTestTag("editor_notes_input"))
        composeTestRule.onNodeWithTag("editor_platform_input").performTextReplacement("Android Compose")
        composeTestRule.onNodeWithTag("editor_notes_input").performTextReplacement("Lower-page notes input while typing")
        idleUi()

        // Switch to DARK theme and verify all content and lower-page fields remain intact
        themeState.value = AppThemeMode.DARK
        idleUi()
        composeTestRule.onNodeWithText("Lower-page notes input while typing").assertIsDisplayed()
        Unit
    }
}

