package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.data.AttachmentItem
import com.example.data.AttachmentStorageManager
import com.example.data.AttachmentType
import com.example.data.IdeaStatus
import com.example.data.SaveItemResult
import com.example.data.VaultCategory
import com.example.data.VaultItemEntity
import com.example.data.VaultItemWithDetails
import com.example.ui.screens.ActivityStatsScreen
import com.example.ui.screens.ArchivedItemsScreen
import com.example.ui.screens.CategoryItemsScreen
import com.example.ui.screens.EditorLargeStateSessionBuffer
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.RecentlyAddedScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.screens.shouldPushPromptUndoCheckpoint
import com.example.ui.theme.IdeaVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinalAuditCorrectionsVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleItemWithDetails(
        id: String,
        title: String,
        category: VaultCategory = VaultCategory.IDEA,
        isArchived: Boolean = false,
        isTrashed: Boolean = false
    ): VaultItemWithDetails {
        return VaultItemWithDetails(
            entity = VaultItemEntity(
                id = id,
                title = title,
                description = "Description for $title",
                content = "Content for $title",
                category = category.id,
                folderId = null,
                tagsJson = "[\"audit\"]",
                ideaStatus = IdeaStatus.EXPLORING.id,
                platform = "Android",
                estimatedTime = "15m",
                notes = "",
                isPinned = false,
                isFavorite = false,
                isArchived = isArchived,
                isTrashed = isTrashed,
                createdAt = 1700000000000L,
                updatedAt = 1700000005000L
            ),
            tags = listOf("audit"),
            richBlocks = emptyList(),
            attachments = emptyList(),
            steps = emptyList()
        )
    }

    @Test
    fun fix1_fileProvider_generatesContentUris_andLaunchesExternalViewerWithGrantReadFlag_andRejectsExternalFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activeDir = File(context.filesDir, "vault_attachments").apply { mkdirs() }
        val genDir = File(File(context.filesDir, "vault_attachment_generations"), "gen_test").apply { mkdirs() }

        val imageFile = File(activeDir, "sample_image.png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)) }
        val pdfFile = File(activeDir, "architecture_spec.pdf").apply { writeText("%PDF-1.4 sample") }
        val docFile = File(genDir, "release_notes.txt").apply { writeText("Release notes v2.4.0") }

        assertTrue(AttachmentStorageManager.isOwnedAttachmentFile(context, imageFile))
        assertTrue(AttachmentStorageManager.isOwnedAttachmentFile(context, pdfFile))
        assertTrue(AttachmentStorageManager.isOwnedAttachmentFile(context, docFile))

        val imageUri = AttachmentStorageManager.getContentUriForOwnedAttachment(context, imageFile)
        val pdfUri = AttachmentStorageManager.getContentUriForOwnedAttachment(context, pdfFile)
        val docUri = AttachmentStorageManager.getContentUriForOwnedAttachment(context, docFile)

        assertNotNull(imageUri)
        assertNotNull(pdfUri)
        assertNotNull(docUri)
        assertEquals("content", imageUri!!.scheme)
        assertEquals("com.aistudio.ideavault.vltpkx.fileprovider", imageUri.authority)
        assertEquals("content", pdfUri!!.scheme)
        assertEquals("com.aistudio.ideavault.vltpkx.fileprovider", pdfUri.authority)
        assertEquals("content", docUri!!.scheme)
        assertEquals("com.aistudio.ideavault.vltpkx.fileprovider", docUri.authority)

        // Verify files outside owned attachment directories are strictly rejected
        val outsideCacheFile = File(context.cacheDir, "untrusted.pdf").apply { writeText("outside") }
        val outsideFilesDir = File(context.filesDir, "other_private_file.txt").apply { writeText("secret") }
        assertFalse(AttachmentStorageManager.isOwnedAttachmentFile(context, outsideCacheFile))
        assertFalse(AttachmentStorageManager.isOwnedAttachmentFile(context, outsideFilesDir))
        assertNull(AttachmentStorageManager.getContentUriForOwnedAttachment(context, outsideCacheFile))
        assertNull(AttachmentStorageManager.getContentUriForOwnedAttachment(context, outsideFilesDir))

        val unownedAttachment = AttachmentItem(
            id = "att_outside",
            type = AttachmentType.FILE,
            name = "untrusted.pdf",
            uriOrUrl = outsideCacheFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = outsideCacheFile.length()
        )
        assertFalse(AttachmentStorageManager.openAttachmentExternally(context, unownedAttachment))

        val pdfAttachment = AttachmentItem(
            id = "att_pdf",
            type = AttachmentType.PDF,
            name = "architecture_spec.pdf",
            uriOrUrl = pdfFile.absolutePath,
            mimeType = "application/pdf",
            sizeBytes = pdfFile.length()
        )
        val imageAttachment = AttachmentItem(
            id = "att_img",
            type = AttachmentType.IMAGE,
            name = "sample_image.png",
            uriOrUrl = imageFile.absolutePath,
            mimeType = "image/png",
            sizeBytes = imageFile.length()
        )
        val docAttachment = AttachmentItem(
            id = "att_doc",
            type = AttachmentType.FILE,
            name = "release_notes.txt",
            uriOrUrl = docFile.absolutePath,
            mimeType = "text/plain",
            sizeBytes = docFile.length()
        )

        val shadowApplication = Shadows.shadowOf(context as android.app.Application)
        shadowApplication.clearNextStartedActivities()
        shadowApplication.checkActivities(true)

        // 1. When no viewer Activity is registered, openAttachmentExternally must return false and not throw
        assertFalse(AttachmentStorageManager.openAttachmentExternally(context, pdfAttachment))
        assertNull(shadowApplication.nextStartedActivity)

        // 2. Register a viewer Activity for content:// + MIME types and verify intent URI, type, and flags
        val shadowPm = Shadows.shadowOf(context.packageManager)
        val viewerComponent = ComponentName("com.example.viewer", "com.example.viewer.ViewerActivity")
        shadowPm.addActivityIfNotPresent(viewerComponent)
        val filter = IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("content")
            addDataType("application/pdf")
            addDataType("image/png")
            addDataType("text/plain")
        }
        shadowPm.addIntentFilterForActivity(viewerComponent, filter)

        for ((att, expectedMime) in listOf(
            pdfAttachment to "application/pdf",
            imageAttachment to "image/png",
            docAttachment to "text/plain"
        )) {
            val opened = AttachmentStorageManager.openAttachmentExternally(context, att)
            assertTrue("Expected attachment ${att.name} to open when viewer is installed", opened)
            val startedIntent = shadowApplication.nextStartedActivity
            assertNotNull("Expected ACTION_VIEW intent for ${att.name}", startedIntent)
            assertEquals(Intent.ACTION_VIEW, startedIntent.action)
            assertEquals("content", startedIntent.data?.scheme)
            assertEquals("com.aistudio.ideavault.vltpkx.fileprovider", startedIntent.data?.authority)
            assertEquals(expectedMime, startedIntent.type)
            assertTrue(
                "FLAG_GRANT_READ_URI_PERMISSION must be set",
                (startedIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
            )
        }
    }

    @Test
    fun fix2_trashScreen_requiresConfirmationForEmptyTrashAndPermanentDelete_andCancelMakesNoChanges() {
        val item1 = sampleItemWithDetails(id = "trash_1", title = "Important Archived Idea", isTrashed = true)
        val item2 = sampleItemWithDetails(id = "trash_2", title = "Draft Method Workflow", isTrashed = true)
        var emptyTrashCalls = 0
        val permanentlyDeletedIds = mutableListOf<String>()

        composeRule.setContent {
            IdeaVaultTheme {
                TrashScreen(
                    items = listOf(item1, item2),
                    onBack = {},
                    onRestore = {},
                    onDeletePermanently = { id -> permanentlyDeletedIds.add(id) },
                    onEmptyTrash = { emptyTrashCalls++ }
                )
            }
        }

        // 1. Empty Trash -> Cancel makes no changes
        composeRule.onNodeWithTag("trash_empty_button").performClick()
        composeRule.onNodeWithText("Empty Trash Permanently?").assertIsDisplayed()
        assertEquals(0, emptyTrashCalls)

        composeRule.onNodeWithTag("cancel_empty_trash_button").performClick()
        assertEquals(0, emptyTrashCalls)

        // 2. Empty Trash -> Confirm invokes callback once
        composeRule.onNodeWithTag("trash_empty_button").performClick()
        composeRule.onNodeWithText("Empty Trash Permanently?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_empty_trash_button").performClick()
        assertEquals(1, emptyTrashCalls)

        // 3. Delete Permanently -> Cancel makes no changes
        composeRule.onNodeWithTag("trash_delete_trash_1").performClick()
        composeRule.onNodeWithText("Delete Permanently?").assertIsDisplayed()
        assertTrue(permanentlyDeletedIds.isEmpty())

        composeRule.onNodeWithTag("cancel_permanent_delete_button").performClick()
        assertTrue(permanentlyDeletedIds.isEmpty())

        // 4. Delete Permanently -> Confirm invokes callback for that item
        composeRule.onNodeWithTag("trash_delete_trash_1").performClick()
        composeRule.onNodeWithText("Delete Permanently?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_permanent_delete_button").performClick()
        assertEquals(listOf("trash_1"), permanentlyDeletedIds)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1000dp-h800dp")
    fun fix3_secondaryScreens_constrainContentWidthOnTablet_andProvideAccessible48dpTouchTargets() {
        val sampleItems = listOf(
            sampleItemWithDetails(id = "sec_1", title = "Tablet Responsive Entry", isArchived = true, isTrashed = true)
        )

        var currentScreen by mutableStateOf("category")
        composeRule.setContent {
            IdeaVaultTheme {
                when (currentScreen) {
                    "category" -> CategoryItemsScreen(
                        category = VaultCategory.IDEA,
                        items = sampleItems,
                        onBack = {},
                        onOpenItem = {},
                        onCreateInCategory = {}
                    )
                    "recent" -> RecentlyAddedScreen(
                        items = sampleItems,
                        onBack = {},
                        onOpenItem = {},
                        onToggleFavorite = {}
                    )
                    "archived" -> ArchivedItemsScreen(
                        items = sampleItems,
                        onBack = {},
                        onOpenItem = {},
                        onUnarchive = {}
                    )
                    "trash" -> TrashScreen(
                        items = sampleItems,
                        onBack = {},
                        onRestore = {},
                        onDeletePermanently = {},
                        onEmptyTrash = {}
                    )
                    "stats" -> ActivityStatsScreen(
                        items = sampleItems,
                        categoryCounts = mapOf(VaultCategory.IDEA to 1),
                        folderCount = 2,
                        onBack = {}
                    )
                }
            }
        }

        val density = composeRule.density.density

        fun assertConstrainedAndCentered(tag: String) {
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val widthDp = bounds.width / density
            val leftDp = bounds.left / density
            assertTrue("Expected $tag width <= 681dp on 1000dp tablet, got ${widthDp}dp", widthDp <= 681f)
            assertTrue("Expected $tag centered (left >= 150dp) on 1000dp tablet, got ${leftDp}dp", leftDp >= 150f)
        }

        fun assertMinTouchTarget48dp(tag: String) {
            val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val widthDp = bounds.width / density
            val heightDp = bounds.height / density
            assertTrue("Expected $tag width >= 48dp, got ${widthDp}dp", widthDp >= 47.5f)
            assertTrue("Expected $tag height >= 48dp, got ${heightDp}dp", heightDp >= 47.5f)
        }

        assertConstrainedAndCentered("category_items_list")
        assertMinTouchTarget48dp("category_items_create_button")

        currentScreen = "recent"
        composeRule.waitForIdle()
        assertConstrainedAndCentered("recently_added_list")

        currentScreen = "archived"
        composeRule.waitForIdle()
        assertConstrainedAndCentered("archived_items_list")
        assertMinTouchTarget48dp("archived_unarchive_sec_1")

        currentScreen = "trash"
        composeRule.waitForIdle()
        assertConstrainedAndCentered("trash_items_list")
        assertMinTouchTarget48dp("trash_empty_button")
        assertMinTouchTarget48dp("trash_restore_sec_1")
        assertMinTouchTarget48dp("trash_delete_sec_1")

        currentScreen = "stats"
        composeRule.waitForIdle()
        assertConstrainedAndCentered("activity_stats_list")
    }

    @Test
    fun fix4_promptUndoCheckpointing_rotationPersistence_andSessionScopedBufferCleanup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        EditorLargeStateSessionBuffer.configureCacheDir(context.cacheDir)
        EditorLargeStateSessionBuffer.clearAll(context)

        // 1. Verify coalescing logic for rapid typing vs word boundaries / pauses / multi-char pastes
        assertTrue(
            "Initial edit from empty undo stack must always create a baseline checkpoint",
            shouldPushPromptUndoCheckpoint(
                previousText = "",
                nextText = "H",
                undoStackEmpty = true,
                elapsedMsSinceLastPush = 10L
            )
        )
        assertFalse(
            "Rapid single-character typing inside the same word (<600ms) must coalesce",
            shouldPushPromptUndoCheckpoint(
                previousText = "H",
                nextText = "He",
                undoStackEmpty = false,
                elapsedMsSinceLastPush = 45L
            )
        )
        assertTrue(
            "Typing a space after a word must create a word-boundary undo checkpoint",
            shouldPushPromptUndoCheckpoint(
                previousText = "Hello",
                nextText = "Hello ",
                undoStackEmpty = false,
                elapsedMsSinceLastPush = 45L
            )
        )
        assertTrue(
            "Typing a newline must create an undo checkpoint",
            shouldPushPromptUndoCheckpoint(
                previousText = "Line 1",
                nextText = "Line 1\n",
                undoStackEmpty = false,
                elapsedMsSinceLastPush = 45L
            )
        )
        assertTrue(
            "Multi-character paste must create an undo checkpoint",
            shouldPushPromptUndoCheckpoint(
                previousText = "Hello ",
                nextText = "Hello {TOPIC}",
                undoStackEmpty = false,
                elapsedMsSinceLastPush = 30L
            )
        )
        assertTrue(
            "Pause >= 600ms must create an undo checkpoint",
            shouldPushPromptUndoCheckpoint(
                previousText = "Hello",
                nextText = "Hello!",
                undoStackEmpty = false,
                elapsedMsSinceLastPush = 650L
            )
        )

        // 2. Verify session-scoped cleanup preserves active tokens from other sessions
        EditorLargeStateSessionBuffer.setActiveSessionKey("session_alpha")
        val alphaToken = EditorLargeStateSessionBuffer.putLargeString("str", "A".repeat(1200))
        val alphaStackToken = EditorLargeStateSessionBuffer.putStringList("stack", listOf("snap1", "snap2"))
        assertTrue(alphaToken.startsWith(EditorLargeStateSessionBuffer.REF_PREFIX))
        assertTrue(alphaStackToken.startsWith(EditorLargeStateSessionBuffer.REF_PREFIX))

        EditorLargeStateSessionBuffer.setActiveSessionKey("session_beta")
        val betaToken = EditorLargeStateSessionBuffer.putLargeString("str", "B".repeat(1200))
        assertNotNull(EditorLargeStateSessionBuffer.resolveDiskFileForToken(betaToken))
        assertTrue(EditorLargeStateSessionBuffer.resolveDiskFileForToken(betaToken)!!.exists())

        // Clearing session_beta removes betaToken from memory and disk, leaving session_alpha untouched
        EditorLargeStateSessionBuffer.clearSession("session_beta", context)
        assertFalse(EditorLargeStateSessionBuffer.resolveDiskFileForToken(betaToken)!!.exists())
        assertNull(EditorLargeStateSessionBuffer.getLargeStringOrNull(betaToken))
        assertEquals("A".repeat(1200), EditorLargeStateSessionBuffer.getLargeStringOrNull(alphaToken))
        assertEquals(listOf("snap1", "snap2"), EditorLargeStateSessionBuffer.getStringListOrNull(alphaStackToken))

        // 3. Verify Prompt Undo/Redo survives rotation (StateRestorationTester) and restores literal text
        val attachmentStorage = AttachmentStorageManager(context)
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            IdeaVaultTheme {
                EditorScreen(
                    existingItem = null,
                    initialCategory = VaultCategory.PROMPT,
                    initialFolderId = null,
                    folders = emptyList(),
                    recoverableDraft = null,
                    attachmentStorage = attachmentStorage,
                    onAutoSaveDraft = {},
                    onDiscardDraft = {},
                    onCreateFolder = { _, _, _ -> },
                    onSaveItemAwaitable = { _, _, _, _, _ ->
                        SaveItemResult.Success("prompt_1", 1700000010000L)
                    },
                    onSaveSuccess = {},
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithTag("editor_content_input").performTextReplacement("System: Analyze **bold** {TOPIC}")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_content_input").performTextReplacement("System: Analyze **bold** {TOPIC}\nOutput JSON.")
        composeRule.waitForIdle()

        // Emulate Activity recreation / rotation
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        // Undo after rotation must restore previous checkpoint
        composeRule.onNodeWithTag("editor_undo_button").performClick()
        composeRule.waitForIdle()
        val afterUndoText = composeRule.onNodeWithTag("editor_content_input")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("System: Analyze **bold** {TOPIC}", afterUndoText)

        // Redo after rotation must restore latest text
        composeRule.onNodeWithTag("editor_redo_button").performClick()
        composeRule.waitForIdle()
        val afterRedoText = composeRule.onNodeWithTag("editor_content_input")
            .fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("System: Analyze **bold** {TOPIC}\nOutput JSON.", afterRedoText)
    }
}
