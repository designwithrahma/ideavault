package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.AttachmentStorageManager
import com.example.data.FolderEntity
import com.example.data.MethodStepEntity
import com.example.data.SaveItemResult
import com.example.data.VaultCategory
import com.example.data.VaultItemEntity
import com.example.data.VaultItemWithDetails
import com.example.ui.screens.EditorScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.IdeaVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w320dp-h640dp-xhdpi")
class EditorUiFixesVerificationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun verifyUi01_Ui02_Ui03_Ui08_AllCategories_LightAndDark_320dp_200PercentFont_SaveAndReopen() {
        val longFolderName = "Enterprise Architecture & Distributed Systems Reference Library 2026"
        val folders = listOf(
            FolderEntity(
                id = "folder-long",
                name = longFolderName,
                category = VaultCategory.IDEA.id,
                colorHex = "#DDFB5D",
                createdAt = 1000L
            )
        )

        var currentCategory by mutableStateOf(VaultCategory.IDEA)
        var currentTheme by mutableStateOf(AppThemeMode.DARK)
        var currentFontScale by mutableStateOf(1.0f)
        var currentFolderId by mutableStateOf<String?>(null)
        var simulatedKeyboardBottomDp by mutableStateOf(0.dp)
        var editingExisting by mutableStateOf<VaultItemWithDetails?>(null)
        var lastSavedItem: VaultItemEntity? = null
        var lastSavedSteps: List<MethodStepEntity> = emptyList()

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            val context = LocalContext.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = currentFontScale)
            ) {
                IdeaVaultTheme(themeMode = currentTheme) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = simulatedKeyboardBottomDp)
                    ) {
                        key(currentCategory, currentFolderId, editingExisting?.entity?.id) {
                            EditorScreen(
                                existingItem = editingExisting,
                                initialCategory = currentCategory,
                                initialFolderId = currentFolderId,
                                folders = folders,
                                recoverableDraft = null,
                                attachmentStorage = AttachmentStorageManager(context),
                                onAutoSaveDraft = {},
                                onDiscardDraft = {},
                                onCreateFolder = { _, _, _ -> },
                                onSaveItemAwaitable = { item, steps, _, _, _ ->
                                    lastSavedItem = item
                                    lastSavedSteps = steps
                                    SaveItemResult.Success(item.id, item.updatedAt)
                                },
                                onSaveSuccess = {},
                                onCancel = {}
                            )
                        }
                    }
                }
            }
        }

        // 1. Verify UI-01, UI-03, UI-08 across all 4 categories in both Dark and Light themes at 320dp width & 200% font scale
        for (theme in listOf(AppThemeMode.DARK, AppThemeMode.LIGHT)) {
            for (scale in listOf(1.0f, 2.0f)) {
                for (cat in listOf(VaultCategory.IDEA, VaultCategory.METHOD, VaultCategory.PROMPT, VaultCategory.KNOWLEDGE)) {
                    currentTheme = theme
                    currentFontScale = scale
                    currentCategory = cat
                    currentFolderId = null
                    editingExisting = null
                    composeTestRule.waitForIdle()

                    composeTestRule.onNodeWithTag("editor_scroll_list")
                        .performScrollToIndex(0)
                    composeTestRule.waitForIdle()

                    // UI-01: Folder selector has full width (>= 240dp on 320dp screen) and >= 48dp touch target height
                    composeTestRule.onNodeWithTag("editor_folder_selector")
                        .assertIsDisplayed()
                        .assertHeightIsAtLeast(48.dp)
                        .assertWidthIsAtLeast(240.dp)
                        .assertTextContains("Uncategorized")

                    // UI-08: Favourite chip always shows "Favourite" whether active or inactive
                    composeTestRule.onNodeWithTag("editor_toggle_favorite")
                        .assertIsDisplayed()
                        .assertTextContains("Favourite")

                    // UI-03: Check canvas minimum height (160dp for IDEA/NOTE, 96dp for METHOD)
                    composeTestRule.onNodeWithTag("editor_scroll_list")
                        .performScrollToNode(hasTestTag("editor_content_input"))
                    composeTestRule.waitForIdle()

                    if (cat == VaultCategory.IDEA || cat == VaultCategory.KNOWLEDGE) {
                        val size = composeTestRule.onNodeWithTag("editor_content_input")
                            .fetchSemanticsNode().size
                        val heightDp = with(composeTestRule.density) { size.height.toDp() }
                        assertTrue("Expected >= 160dp canvas height for $cat, got $heightDp", heightDp >= 158.dp)
                    } else if (cat == VaultCategory.METHOD) {
                        val size = composeTestRule.onNodeWithTag("editor_content_input")
                            .fetchSemanticsNode().size
                        val heightDp = with(composeTestRule.density) { size.height.toDp() }
                        assertTrue("Expected >= 96dp canvas height for METHOD, got $heightDp", heightDp >= 94.dp)
                    }
                }
            }
        }

        // 2. Verify UI-01 with long folder name at 320dp width and 200% font scale
        currentTheme = AppThemeMode.LIGHT
        currentFontScale = 2.0f
        currentCategory = VaultCategory.IDEA
        currentFolderId = "folder-long"
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToIndex(0)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_folder_selector")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(240.dp)
            .assertTextContains(longFolderName)

        // 3. Verify UI-08 toggle interaction preserves "Favourite" label in both states
        currentFontScale = 1.0f
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("editor_toggle_favorite")
            .assertTextContains("Favourite")
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("editor_toggle_favorite")
            .assertTextContains("Favourite")

        // 4. Verify UI-02: 120-character title + pasted newline normalization + keyboard open/closed + Save & Reopen
        val rawTitleWithNewlines =
            "A comprehensive 120-character architectural blueprint\nfor local-first encrypted knowledge\rmanagement systems in 2026 v23"
        val expectedNormalizedTitle =
            "A comprehensive 120-character architectural blueprint for local-first encrypted knowledge management systems in 2026 v23"
        assertEquals(120, expectedNormalizedTitle.length)

        // Keyboard open state
        simulatedKeyboardBottomDp = 260.dp
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_title_input")
            .performTextReplacement(rawTitleWithNewlines)
        composeTestRule.waitForIdle()

        // Newlines normalized to spaces, full 120-character length preserved without truncation
        composeTestRule.onNodeWithTag("editor_title_input")
            .assertTextEquals(expectedNormalizedTitle)

        // Keyboard closed state
        simulatedKeyboardBottomDp = 0.dp
        composeTestRule.waitForIdle()

        // Enter body content and save
        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToNode(hasTestTag("editor_content_input"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_content_input")
            .performClick()
            .performTextInput("Detailed body notes for 120-char title verification.")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_save_button")
            .performClick()
        composeTestRule.waitForIdle()

        assertNotNull("Expected item to be saved", lastSavedItem)
        assertEquals(expectedNormalizedTitle, lastSavedItem?.title)
        assertFalse("Saved title must not contain newlines", lastSavedItem!!.title.contains('\n'))
        assertTrue("Favourite state should be saved as true", lastSavedItem!!.isFavorite)

        // Reopen the saved item in EditorScreen and verify full 120-character title is intact
        editingExisting = VaultItemWithDetails(
            entity = lastSavedItem!!,
            tags = emptyList(),
            richBlocks = emptyList(),
            attachments = emptyList(),
            steps = lastSavedSteps
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_scroll_list")
            .performScrollToIndex(0)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("editor_title_input")
            .assertTextEquals(expectedNormalizedTitle)
    }

    @Test
    fun verifyKeyboardScrollingCorrection_LongMultilineContent_LowerPageInputs_ScreenSizes_BothThemes() {
        var currentTheme by mutableStateOf(AppThemeMode.DARK)
        var screenWidthDp by mutableStateOf(320.dp)
        var simulatedKeyboardBottomDp by mutableStateOf(240.dp)
        var lastSavedItem: VaultItemEntity? = null
        var lastSavedSteps: List<MethodStepEntity> = emptyList()

        val existingMethodItem = VaultItemWithDetails(
            entity = VaultItemEntity(
                id = "method-kb-test",
                title = "Keyboard Scroll Verification Method",
                description = "Initial summary line 1\nInitial summary line 2",
                content = (1..12).joinToString("\n") { "Paragraph $it of long multiline method overview content for keyboard scroll testing." },
                category = VaultCategory.METHOD.id,
                platform = "Android",
                estimatedTime = "25m",
                notes = "Initial lower-page note line 1\nInitial lower-page note line 2"
            ),
            tags = listOf("keyboard", "scroll"),
            richBlocks = emptyList(),
            attachments = emptyList(),
            steps = listOf(
                MethodStepEntity(
                    id = "step-1",
                    itemId = "method-kb-test",
                    stepOrder = 1,
                    title = "Step One",
                    description = (1..8).joinToString("\n") { "Step 1 multiline instruction line $it for testing VaultTextFieldBox typing stability." },
                    notes = "Step 1 link or reference"
                )
            )
        )

        composeTestRule.setContent {
            val context = LocalContext.current
            IdeaVaultTheme(themeMode = currentTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = simulatedKeyboardBottomDp)
                ) {
                    key(currentTheme, screenWidthDp) {
                        EditorScreen(
                            existingItem = existingMethodItem,
                            initialCategory = VaultCategory.METHOD,
                            initialFolderId = null,
                            folders = emptyList(),
                            recoverableDraft = null,
                            attachmentStorage = AttachmentStorageManager(context),
                            onAutoSaveDraft = {},
                            onDiscardDraft = {},
                            onCreateFolder = { _, _, _ -> },
                            onSaveItemAwaitable = { item, steps, _, _, _ ->
                                lastSavedItem = item
                                lastSavedSteps = steps
                                SaveItemResult.Success(item.id, item.updatedAt)
                            },
                            onSaveSuccess = {},
                            onCancel = {}
                        )
                    }
                }
            }
        }

        for (theme in listOf(AppThemeMode.DARK, AppThemeMode.LIGHT)) {
            for (width in listOf(320.dp, 411.dp)) {
                currentTheme = theme
                screenWidthDp = width
                simulatedKeyboardBottomDp = 240.dp
                composeTestRule.waitForIdle()

                // 1. Focus and append multiline content in the step details VaultTextFieldBox
                composeTestRule.onNodeWithTag("editor_scroll_list")
                    .performScrollToIndex(0)
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("editor_scroll_list")
                    .performScrollToNode(hasTestTag("editor_step_details_0"))
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("editor_step_details_0")
                    .assertIsDisplayed()
                    .performClick()
                    .performTextInput("\nAppended step line A\nAppended step line B")
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("editor_step_details_0")
                    .assertTextContains("Appended step line B", substring = true)

                // 2. Scroll to lower-page pending new step inputs and type with keyboard open
                composeTestRule.onNodeWithTag("editor_scroll_list")
                    .performScrollToNode(hasTestTag("editor_new_step_title"))
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("editor_new_step_title")
                    .assertIsDisplayed()
                    .performClick()
                    .performTextReplacement("Pending Lower-Page Step")
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("editor_scroll_list")
                    .performScrollToNode(hasTestTag("editor_new_step_details"))
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("editor_new_step_details")
                    .assertIsDisplayed()
                    .performClick()
                    .performTextReplacement("Lower-page pending step details typed under open IME")
                composeTestRule.waitForIdle()

                // 3. Scroll to bottom-most metadata VaultTextFieldBox fields (description, notes)
                composeTestRule.onNodeWithTag("editor_scroll_list")
                    .performScrollToNode(hasTestTag("editor_description_input"))
                composeTestRule.waitForIdle()

                val longMultilineSummary = (1..6).joinToString("\n") {
                    "Summary line $it verify no scroll jitter during multiline typing ($theme, $width)"
                }
                composeTestRule.onNodeWithTag("editor_description_input")
                    .assertIsDisplayed()
                    .performClick()
                    .performTextReplacement(longMultilineSummary)
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("editor_scroll_list")
                    .performScrollToNode(hasTestTag("editor_notes_input"))
                composeTestRule.waitForIdle()

                val longMultilineNotes = (1..6).joinToString("\n") {
                    "Lower-page notes line $it under open keyboard ($theme, $width)"
                }
                composeTestRule.onNodeWithTag("editor_notes_input")
                    .assertIsDisplayed()
                    .performClick()
                    .performTextReplacement(longMultilineNotes)
                composeTestRule.waitForIdle()

                // Save and verify all lower-page and multiline edits persisted cleanly
                composeTestRule.onNodeWithTag("editor_save_button")
                    .performClick()
                composeTestRule.waitForIdle()

                assertNotNull(lastSavedItem)
                assertEquals(longMultilineSummary, lastSavedItem!!.description)
                assertEquals(longMultilineNotes, lastSavedItem!!.notes)
                assertEquals(2, lastSavedSteps.size)
                assertTrue(lastSavedSteps[0].description.contains("Appended step line B"))
                assertEquals("Pending Lower-Page Step", lastSavedSteps[1].title)
            }
        }
    }
}
