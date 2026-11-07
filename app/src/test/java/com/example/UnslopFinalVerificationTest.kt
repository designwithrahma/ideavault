package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AttachmentStorageManager
import com.example.data.BackupRestoreManager
import com.example.data.IdeaStatus
import com.example.data.MAIN_METHOD_ROUTE_ID
import com.example.data.MethodStepEntity
import com.example.data.UserPreferencesRepository
import com.example.data.VaultCategory
import com.example.data.VaultDatabase
import com.example.data.VaultItemEntity
import com.example.data.VaultJsonSerializers
import com.example.data.VaultRepository
import com.example.ui.IdeaVaultApp
import com.example.ui.VaultViewModel
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.IdeaVaultTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class UnslopFinalVerificationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: VaultDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var attachmentStorage: AttachmentStorageManager
    private lateinit var backupManager: BackupRestoreManager
    private lateinit var repository: VaultRepository
    private lateinit var tempPrefsFile: File
    private var currentWidthDp: Dp = 411.dp
    private var currentHeightDp: Dp = 891.dp

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempPrefsFile = File(context.cacheDir, "unslop_verify_${System.nanoTime()}.preferences_pb")
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
        db.close()
        tempPrefsFile.delete()
    }

    @Suppress("UNCHECKED_CAST")
    private fun getActiveWindowViews(): List<View> {
        return runCatching {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val instance = wmgClass.getMethod("getInstance").invoke(null)
            val mViewsField = wmgClass.getDeclaredField("mViews").apply { isAccessible = true }
            (mViewsField.get(instance) as? List<View>)?.filter {
                it.visibility == View.VISIBLE
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun saveScreenshot(fileName: String) {
        composeTestRule.waitForIdle()
        val density = context.resources.displayMetrics.density
        val widthPx = (currentWidthDp.value * density).toInt()
        val heightPx = (currentHeightDp.value * density).toInt()
        val views = getActiveWindowViews()
        check(views.isNotEmpty()) { "No active window view for $fileName" }
        val mainView = views.first()
        mainView.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        mainView.layout(0, 0, widthPx, heightPx)
        composeTestRule.waitForIdle()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        mainView.draw(canvas)

        val outDirs = listOf(
            File("/.aistudio/artifacts/brain/77113e33-7cc7-4f3f-b48b-484cd236a1df"),
            File("build/reports/unslop_screenshots")
        )
        outDirs.forEach { dir ->
            runCatching {
                dir.mkdirs()
                File(dir, fileName).outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        }
    }

    private fun SemanticsNode.widthDp(): Float = size.width / layoutInfo.density.density
    private fun SemanticsNode.heightDp(): Float = size.height / layoutInfo.density.density

    private fun assertNoOverlap(tagA: String, tagB: String) {
        val boundsA: Rect = composeTestRule.onNodeWithTag(tagA).fetchSemanticsNode().boundsInRoot
        val boundsB: Rect = composeTestRule.onNodeWithTag(tagB).fetchSemanticsNode().boundsInRoot
        val overlapsHorizontally = boundsA.left < boundsB.right - 0.5f && boundsB.left < boundsA.right - 0.5f
        val overlapsVertically = boundsA.top < boundsB.bottom - 0.5f && boundsB.top < boundsA.bottom - 0.5f
        assertTrue(
            "Expected $tagA ($boundsA) and $tagB ($boundsB) not to overlap",
            !(overlapsHorizontally && overlapsVertically)
        )
    }

    @Test
    fun verifyResponsiveWidthTouchTargetsCurvedArrowFontScaleAndCaptureScreenshots() {
        val ideaId = "audit_idea_1"
        val methodId = "audit_method_1"
        val promptId = "audit_prompt_1"

        runBlocking<Unit> {
            db.clearAllTables()
            prefsRepo.completeOnboarding("Alex")
            prefsRepo.updateThemeMode(AppThemeMode.LIGHT)
            prefsRepo.updateAppLockPin("")

            repository.createFolder("Product Architecture", VaultCategory.IDEA, "#EF7B59")
            val folderProductId = repository.foldersFlow.first().first().id

            repository.saveVaultItemWithResult(
                item = VaultItemEntity(
                    id = promptId,
                    title = "System Prompt: Android Architecture Auditor",
                    description = "Structured prompt template with literal placeholders, Markdown tokens, and JSON output schema.",
                    content = "Act as a **Senior Android Architect** auditing `{PROJECT_NAME}`.",
                    category = VaultCategory.PROMPT.id,
                    folderId = null,
                    isPinned = false,
                    isFavorite = false,
                    tagsJson = VaultJsonSerializers.encodeStringList(listOf("prompt", "audit")),
                    updatedAt = 1729800000000L
                ),
                steps = emptyList(),
                expectedPreviousUpdatedAt = null,
                isEditingExisting = false,
                forceOverwrite = true
            )
            kotlinx.coroutines.delay(5)

            repository.saveVaultItemWithResult(
                item = VaultItemEntity(
                    id = methodId,
                    title = "Release Readiness & QA Audit Protocol",
                    description = "Step-by-step release verification for responsive width constraints, 48dp touch targets, and font scaling.",
                    content = "Follow this **standard operating procedure** before shipping any release build.",
                    category = VaultCategory.METHOD.id,
                    folderId = folderProductId,
                    isPinned = true,
                    isFavorite = true,
                    tagsJson = VaultJsonSerializers.encodeStringList(listOf("qa", "release")),
                    updatedAt = 1729900000000L
                ),
                steps = listOf(
                    MethodStepEntity(
                        id = "m_step_1",
                        itemId = methodId,
                        routeId = MAIN_METHOD_ROUTE_ID,
                        stepOrder = 1,
                        title = "Verify widthIn(max = 680.dp) ordering",
                        description = "Ensure widthIn precedes fillMaxSize/fillMaxWidth so wide screens constrain content width."
                    ),
                    MethodStepEntity(
                        id = "m_step_2",
                        itemId = methodId,
                        routeId = MAIN_METHOD_ROUTE_ID,
                        stepOrder = 2,
                        title = "Verify 48dp touch regions",
                        description = "Keep 34dp compact visual pills while providing 48dp minimum interactive targets."
                    )
                ),
                expectedPreviousUpdatedAt = null,
                isEditingExisting = false,
                forceOverwrite = true
            )
            kotlinx.coroutines.delay(5)

            repository.saveVaultItemWithResult(
                item = VaultItemEntity(
                    id = ideaId,
                    title = "Offline-First Personal Knowledge Vault",
                    description = "Curved-cutout cards with rich formatting, transactional ZIP backups, and fail-closed App Lock.",
                    content = "# Vision Overview\nBuild a **zero-latency offline vault** for creators.\n• Support *inline rich styling* and custom font sizes\n• Preserve exact Markdown & prompt templates",
                    category = VaultCategory.IDEA.id,
                    folderId = folderProductId,
                    ideaStatus = IdeaStatus.DEVELOPING.id,
                    isPinned = true,
                    isFavorite = true,
                    tagsJson = VaultJsonSerializers.encodeStringList(listOf("android", "architecture")),
                    updatedAt = 1730000000000L
                ),
                steps = emptyList(),
                expectedPreviousUpdatedAt = null,
                isEditingExisting = false,
                forceOverwrite = true
            )
        }

        val viewModel = VaultViewModel(context, repository)

        var generation by mutableIntStateOf(0)
        var themeMode by mutableStateOf(AppThemeMode.LIGHT)
        var fontScale by mutableStateOf(1f)
        var containerWidthDp by mutableStateOf(411.dp)
        var containerHeightDp by mutableStateOf(891.dp)

        fun updateViewport(
            width: Dp = 411.dp,
            height: Dp = 891.dp,
            theme: AppThemeMode = AppThemeMode.LIGHT,
            scale: Float = 1f
        ) {
            runBlocking<Unit> {
                prefsRepo.updateThemeMode(theme)
            }
            currentWidthDp = width
            currentHeightDp = height
            containerWidthDp = width
            containerHeightDp = height
            themeMode = theme
            fontScale = scale
            generation++
            composeTestRule.waitForIdle()
            val density = context.resources.displayMetrics.density
            val wPx = (width.value * density).toInt()
            val hPx = (height.value * density).toInt()
            getActiveWindowViews().firstOrNull()?.let { mainView ->
                mainView.measure(
                    View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY)
                )
                mainView.layout(0, 0, wPx, hPx)
            }
            composeTestRule.waitForIdle()
        }

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = fontScale),
                LocalSaveableStateRegistry provides null
            ) {
                IdeaVaultTheme(themeMode = themeMode) {
                    key(generation, themeMode, fontScale, containerWidthDp, containerHeightDp) {
                        Box(modifier = Modifier.requiredSize(width = containerWidthDp, height = containerHeightDp)) {
                            IdeaVaultApp(viewModel = viewModel)
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 1. HOME SCREEN (Phone 411dp + Screenshot 1) & FIX 3 Curved Card Arrow 48dp
        // =========================================================================
        updateViewport(width = 411.dp, height = 891.dp, theme = AppThemeMode.LIGHT, scale = 1f)

        val phoneHomeWidth = composeTestRule.onNodeWithTag("home_screen_list").fetchSemanticsNode().widthDp()
        assertEquals(411f, phoneHomeWidth, 1.5f)

        // FIX 3: Verify CurvedActionCardContainer arrow button has 48dp x 48dp touch target
        val arrowNode = composeTestRule.onNodeWithTag("vault_item_card_${ideaId}_action_button").fetchSemanticsNode()
        assertTrue("Expected arrow touch width >= 48dp, got ${arrowNode.widthDp()}", arrowNode.widthDp() >= 47.5f)
        assertTrue("Expected arrow touch height >= 48dp, got ${arrowNode.heightDp()}", arrowNode.heightDp() >= 47.5f)

        // Home filter chips >= 48dp touch target and non-overlapping
        val filterAllNode = composeTestRule.onNodeWithTag("filter_chip_all").fetchSemanticsNode()
        assertTrue("Expected filter_chip_all height >= 48dp, got ${filterAllNode.heightDp()}", filterAllNode.heightDp() >= 47.5f)
        assertNoOverlap("filter_chip_all", "filter_chip_idea")

        saveScreenshot("01_home_screen.png")

        // =========================================================================
        // 2. ITEM DETAIL SCREEN via Curved Card Arrow Click (Screenshot 3 + FIX 2/3/4)
        // =========================================================================
        composeTestRule.onNodeWithTag("vault_item_card_${methodId}_action_button").performClick()
        composeTestRule.waitForIdle()

        val detailCopyNode = composeTestRule.onNodeWithTag("detail_copy_all_button").fetchSemanticsNode()
        assertTrue("Expected detail_copy_all_button height >= 48dp, got ${detailCopyNode.heightDp()}", detailCopyNode.heightDp() >= 47.5f)
        assertNoOverlap("detail_copy_all_button", "detail_copy_for_ai_button")

        saveScreenshot("03_item_detail_screen.png")

        // =========================================================================
        // 3. EDITOR SCREEN (FIX 2: 48dp Touch Targets, Compact Visual Pills, Screenshot 2)
        // =========================================================================
        composeTestRule.onNodeWithTag("detail_edit_button").performClick()
        composeTestRule.waitForIdle()

        // Switch to Idea category to also verify idea status pills
        composeTestRule.onNodeWithTag("editor_category_idea").performClick()
        composeTestRule.waitForIdle()

        val editorTouchTags = listOf(
            "editor_folder_selector",
            "editor_toggle_pin",
            "editor_toggle_favorite",
            "editor_category_idea",
            "editor_category_method",
            "editor_category_prompt",
            "editor_category_knowledge",
            "editor_create_custom_type_chip",
            "editor_idea_status_exploring",
            "editor_idea_status_developing"
        )
        editorTouchTags.forEach { tag ->
            val node = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode()
            assertTrue("Expected $tag height >= 48dp, got ${node.heightDp()}", node.heightDp() >= 47.5f)
            assertTrue("Expected $tag width >= 48dp, got ${node.widthDp()}", node.widthDp() >= 47.5f)
        }

        // Verify neighboring interactive targets do NOT overlap horizontally or vertically
        assertNoOverlap("editor_toggle_pin", "editor_toggle_favorite")
        assertNoOverlap("editor_folder_selector", "editor_toggle_pin")
        assertNoOverlap("editor_category_idea", "editor_category_method")
        assertNoOverlap("editor_category_method", "editor_category_prompt")
        assertNoOverlap("editor_category_idea", "editor_idea_status_exploring")
        assertNoOverlap("editor_idea_status_exploring", "editor_idea_status_developing")

        saveScreenshot("02_editor_screen.png")

        // Restore original category so editor has no unsaved changes and returns cleanly to Detail -> Home
        composeTestRule.onNodeWithTag("editor_category_method").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_bar_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_back_button").performClick()
        composeTestRule.waitForIdle()

        // =========================================================================
        // 4. DARK THEME SCREENSHOT (Screenshot 4)
        // =========================================================================
        updateViewport(width = 411.dp, height = 891.dp, theme = AppThemeMode.DARK, scale = 1f)
        saveScreenshot("04_dark_theme.png")

        // =========================================================================
        // 5. ADDITIONAL ACCESSIBILITY CHECK: 150% and 200% FONT SCALING (Screenshot 5)
        // =========================================================================
        updateViewport(width = 411.dp, height = 891.dp, theme = AppThemeMode.LIGHT, scale = 1.0f)
        val baseHomeNavHeight = composeTestRule.onNodeWithTag("bottom_nav_home").fetchSemanticsNode().heightDp()

        for (scale in listOf(1.5f, 2.0f)) {
            updateViewport(width = 411.dp, height = 891.dp, theme = AppThemeMode.LIGHT, scale = scale)
            val cardNode = composeTestRule.onNodeWithTag("vault_item_card_$ideaId").fetchSemanticsNode()
            assertTrue("Card height at ${scale}x font scale should expand cleanly: ${cardNode.heightDp()}", cardNode.heightDp() >= 180f)
            assertNoOverlap("vault_item_card_$ideaId", "vault_item_card_$methodId")

            val scaledHomeNavHeight = composeTestRule.onNodeWithTag("bottom_nav_home").fetchSemanticsNode().heightDp()
            assertTrue(
                "BottomNavTabItem height at ${scale}x ($scaledHomeNavHeight) should exceed 1.0x height ($baseHomeNavHeight)",
                scaledHomeNavHeight > baseHomeNavHeight
            )
            assertNoOverlap("bottom_nav_home", "bottom_nav_collections")
            assertNoOverlap("bottom_nav_collections", "bottom_nav_add_fab")
            assertNoOverlap("bottom_nav_add_fab", "bottom_nav_favorites")
            assertNoOverlap("bottom_nav_favorites", "bottom_nav_settings")

            if (scale == 1.5f) {
                saveScreenshot("05_large_font_layout.png")
            }
        }

        // =========================================================================
        // 6. FIX 1: RESPONSIVE WIDTH ACROSS LANDSCAPE (891dp), TABLET (840dp), FOLDABLE (768dp)
        // =========================================================================
        val wideFormFactors = listOf(
            Triple("Landscape", 891.dp, 411.dp),
            Triple("Foldable", 768.dp, 1024.dp),
            Triple("Tablet", 840.dp, 1200.dp)
        )

        for ((label, wDp, hDp) in wideFormFactors) {
            updateViewport(width = wDp, height = hDp, theme = AppThemeMode.LIGHT, scale = 1f)

            // 6a. Home
            val homeW = composeTestRule.onNodeWithTag("home_screen_list").fetchSemanticsNode().widthDp()
            assertEquals("Home list width on $label should be constrained to 680dp", 680f, homeW, 1.5f)

            // 6b. Search (opened from Home search bar entry point)
            composeTestRule.onNodeWithTag("home_search_input").performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            val searchW = composeTestRule.onNodeWithTag("search_results_list").fetchSemanticsNode().widthDp()
            assertEquals("Search list width on $label should be constrained to 680dp", 680f, searchW, 1.5f)
            composeTestRule.onNodeWithTag("search_back_button").performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()

            // 6c. Collections / Folders
            composeTestRule.onNodeWithTag("bottom_nav_collections").performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            val foldersW = composeTestRule.onNodeWithTag("folders_list_screen").fetchSemanticsNode().widthDp()
            assertEquals("Folders list width on $label should be constrained to 680dp", 680f, foldersW, 1.5f)

            // 6d. Favourites
            composeTestRule.onNodeWithTag("bottom_nav_favorites").performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            val favW = composeTestRule.onNodeWithTag("favorites_screen_list").fetchSemanticsNode().widthDp()
            assertEquals("Favorites list width on $label should be constrained to 680dp", 680f, favW, 1.5f)

            // 6e. Settings
            composeTestRule.onNodeWithTag("bottom_nav_settings").performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            val settingsW = composeTestRule.onNodeWithTag("settings_screen_list").fetchSemanticsNode().widthDp()
            assertEquals("Settings list width on $label should be constrained to 680dp", 680f, settingsW, 1.5f)

            // 6f. Return to Home -> Detail -> Editor
            composeTestRule.onNodeWithTag("bottom_nav_home").performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("vault_item_card_$ideaId").performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            val detailW = composeTestRule.onNodeWithTag("detail_content_list").fetchSemanticsNode().widthDp()
            assertEquals("Detail list width on $label should be constrained to 680dp", 680f, detailW, 1.5f)

            composeTestRule.onNodeWithTag("detail_edit_button").performSemanticsAction(SemanticsActions.OnClick)
            composeTestRule.waitForIdle()
            val editorW = composeTestRule.onNodeWithTag("editor_scroll_list").fetchSemanticsNode().widthDp()
            assertEquals("Editor list width on $label should be constrained to 680dp", 680f, editorW, 1.5f)
        }

        // Capture Tablet / Landscape Screenshot 6 (840dp x 600dp @ 2x = 1680x1200 px)
        updateViewport(width = 840.dp, height = 600.dp, theme = AppThemeMode.LIGHT, scale = 1f)
        saveScreenshot("06_tablet_landscape_layout.png")
    }
}
