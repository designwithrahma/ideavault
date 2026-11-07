package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.rememberReducedMotion
import com.example.ui.theme.IdeaVaultTheme
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.VaultActionYellow
import com.example.ui.theme.VaultCoral
import com.example.ui.theme.VaultDarkInk
import com.example.ui.theme.VaultLavender
import com.example.ui.theme.VaultLime
import com.example.ui.theme.VaultPink
import com.example.ui.theme.VaultYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class OnboardingSceneSpec(
    val title: String,
    val subtitle: String,
    val accentColor: Color
)

/**
 * IDEAVAULT V2.1 — Three-Screen Animated Onboarding Flow:
 * 1. Capture Everything ("Ideas, methods, prompts, knowledge and more.")
 * 2. Organize Your Way ("Keep your ideas, methods and references neatly organized.")
 * 3. Everything in One Place ("Notes, images, links, files and step-by-step methods.")
 *
 * Features:
 * - No extra introductory welcome screen
 * - No forced name input or account registration
 * - Real Jetpack Compose spring entry animations + independent continuous floating motion
 * - Respects Android reduced-motion accessibility settings
 */
@Composable
fun OnboardingFlowScreen(
    initialUserName: String = "",
    onCompleteAsync: (suspend (String) -> Boolean)? = null,
    onComplete: (String) -> Unit = {}
) {
    val vaultColors = IdeaVaultTheme.colors
    val reducedMotion = rememberReducedMotion()
    val coroutineScope = rememberCoroutineScope()
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val preservedName = remember(initialUserName) {
        initialUserName.ifBlank { "Creator" }
    }

    fun attemptCompleteOnboarding() {
        if (isSaving) return
        if (onCompleteAsync != null) {
            isSaving = true
            saveError = null
            coroutineScope.launch {
                val saved = onCompleteAsync(preservedName)
                isSaving = false
                if (saved) {
                    onComplete(preservedName)
                } else {
                    saveError = "Could not save setup preferences. Please tap Retry."
                }
            }
        } else {
            onComplete(preservedName)
        }
    }

    val scenes = remember {
        listOf(
            OnboardingSceneSpec(
                title = "Capture Everything",
                subtitle = "Ideas, methods, prompts, knowledge and more.",
                accentColor = VaultCoral
            ),
            OnboardingSceneSpec(
                title = "Organize Your Way",
                subtitle = "Keep your ideas, methods and references neatly organized.",
                accentColor = VaultLime
            ),
            OnboardingSceneSpec(
                title = "Everything in One Place",
                subtitle = "Notes, images, links, files and step-by-step methods.",
                accentColor = VaultLavender
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(vaultColors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("onboarding_flow_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar: Compact Page Indicators on left + Skip at top-right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.testTag("onboarding_page_indicators"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                scenes.indices.forEach { idx ->
                    val isSelected = idx == currentStep
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (isSelected) 28.dp else 8.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "indicator_width_$idx"
                    )
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(indicatorWidth)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isSelected) VaultActionYellow else vaultColors.secondarySurface
                            )
                            .testTag("onboarding_indicator_$idx")
                    )
                }
            }

            Text(
                text = "Skip",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = vaultColors.secondaryText,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .testTag("onboarding_skip_button")
                    .clickable { attemptCompleteOnboarding() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // Center Stage: Animated Visual Illustration + Stationary Title & Subtitle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else if (targetState > initialState) {
                        (slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetX = { fullWidth -> fullWidth / 3 }
                        ) + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(180),
                                targetOffsetX = { fullWidth -> -fullWidth / 4 }
                            ) + fadeOut(tween(160)))
                    } else {
                        (slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            initialOffsetX = { fullWidth -> -fullWidth / 3 }
                        ) + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(180),
                                targetOffsetX = { fullWidth -> fullWidth / 4 }
                            ) + fadeOut(tween(160)))
                    }.using(SizeTransform(clip = false))
                },
                label = "onboarding_scene_transition"
            ) { rawStepIndex ->
                val stepIndex = rawStepIndex.coerceIn(0, scenes.lastIndex)
                val scene = scenes[stepIndex]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Animated Visual Scene Stage above the stationary title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        when (stepIndex) {
                            0 -> CaptureEverythingAnimatedStage(reducedMotion = reducedMotion)
                            1 -> OrganizeYourWayAnimatedStage(reducedMotion = reducedMotion)
                            else -> EverythingInOnePlaceAnimatedStage(reducedMotion = reducedMotion)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stationary, readable editorial typography
                    Text(
                        text = scene.title,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                            lineHeight = 36.sp
                        ),
                        color = vaultColors.primaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_title_$stepIndex")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = scene.subtitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        ),
                        color = vaultColors.secondaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .testTag("onboarding_subtitle_$stepIndex")
                    )
                }
            }
        }

        if (!saveError.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = vaultColors.surface,
                border = BorderStroke(1.dp, VaultCoral),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("onboarding_save_error_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = saveError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.primaryText,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = VaultActionYellow,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { attemptCompleteOnboarding() }
                            .testTag("onboarding_save_retry_button")
                    ) {
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = VaultDarkInk,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Bottom Navigation Row: Back + Next / Get Started
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 0) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = vaultColors.surface,
                    border = BorderStroke(1.dp, vaultColors.border),
                    modifier = Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable {
                            saveError = null
                            currentStep -= 1
                        }
                        .testTag("onboarding_back_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = vaultColors.primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Back",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = vaultColors.primaryText
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            val isLastStep = currentStep >= scenes.lastIndex
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = VaultActionYellow,
                modifier = Modifier
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .semantics { role = Role.Button }
                    .clickable {
                        if (currentStep >= scenes.lastIndex) {
                            attemptCompleteOnboarding()
                        } else {
                            saveError = null
                            currentStep += 1
                        }
                    }
                    .testTag("onboarding_next_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (isLastStep) "Get Started" else "Next",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = VaultDarkInk
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = VaultDarkInk,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * SCREEN 1 — CAPTURE EVERYTHING
 * Four distinct colorful cards in a staggered, slightly overlapping composition:
 * 1. Coral Idea / lightbulb (#EF7B59)
 * 2. Lime creative idea symbol (#A7D678)
 * 3. Yellow Method / wrench (#F9D55D)
 * 4. Lavender Note / book (#A096EF)
 *
 * Animations:
 * - Sequential spring-based scale + vertical translation entry
 * - Independent continuous floating movement (4–9dp) and subtle rotation per card
 */
@Composable
private fun CaptureEverythingAnimatedStage(
    reducedMotion: Boolean
) {
    val cardProgress = remember { List(4) { Animatable(if (reducedMotion) 1f else 0f) } }

    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            cardProgress.forEachIndexed { index, anim ->
                launch {
                    delay(index * 95L)
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.66f,
                            stiffness = 300f
                        )
                    )
                }
            }
        } else {
            cardProgress.forEach { it.snapTo(1f) }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scene1_float")
    val float1 by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_card_1"
    )
    val float2 by infiniteTransition.animateFloat(
        initialValue = 5f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_card_2"
    )
    val float3 by infiniteTransition.animateFloat(
        initialValue = -5.5f,
        targetValue = 5.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_card_3"
    )
    val float4 by infiniteTransition.animateFloat(
        initialValue = 6.5f,
        targetValue = -6.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_card_4"
    )

    val tilt1 by infiniteTransition.animateFloat(
        initialValue = -1.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt_card_1"
    )
    val tilt2 by infiniteTransition.animateFloat(
        initialValue = 1.5f,
        targetValue = -1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt_card_2"
    )
    val tilt3 by infiniteTransition.animateFloat(
        initialValue = 1.6f,
        targetValue = -1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt_card_3"
    )
    val tilt4 by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt_card_4"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_stage_capture"),
        contentAlignment = Alignment.Center
    ) {
        // 1. Top-Left Coral Idea / Lightbulb Card
        FloatingCaptureCard(
            color = VaultCoral,
            icon = Icons.Outlined.Lightbulb,
            label = "Idea",
            caption = "Mobile app concept",
            width = 152.dp,
            height = 122.dp,
            baseOffsetX = (-58).dp,
            baseOffsetY = (-52).dp,
            baseRotation = -7f,
            entryProgress = cardProgress[0].value,
            floatOffsetY = if (reducedMotion) 0f else float1,
            rotationDelta = if (reducedMotion) 0f else tilt1,
            testTag = "onboarding_scene1_card_coral"
        )

        // 2. Top-Right Lime Creative Spark Card
        FloatingCaptureCard(
            color = VaultLime,
            icon = Icons.Outlined.AutoAwesome,
            label = "Prompt",
            caption = "Creative spark",
            width = 148.dp,
            height = 118.dp,
            baseOffsetX = 60.dp,
            baseOffsetY = (-44).dp,
            baseRotation = 6.5f,
            entryProgress = cardProgress[1].value,
            floatOffsetY = if (reducedMotion) 0f else float2,
            rotationDelta = if (reducedMotion) 0f else tilt2,
            testTag = "onboarding_scene1_card_lime"
        )

        // 3. Bottom-Left Yellow Method / Wrench Card
        FloatingCaptureCard(
            color = VaultYellow,
            icon = Icons.Outlined.Build,
            label = "Method",
            caption = "5-step workflow",
            width = 150.dp,
            height = 120.dp,
            baseOffsetX = (-50).dp,
            baseOffsetY = 56.dp,
            baseRotation = 5f,
            entryProgress = cardProgress[2].value,
            floatOffsetY = if (reducedMotion) 0f else float3,
            rotationDelta = if (reducedMotion) 0f else tilt3,
            testTag = "onboarding_scene1_card_yellow"
        )

        // 4. Bottom-Right Lavender Note / Book Card
        FloatingCaptureCard(
            color = VaultLavender,
            icon = Icons.Outlined.MenuBook,
            label = "Note",
            caption = "Design system notes",
            width = 154.dp,
            height = 122.dp,
            baseOffsetX = 54.dp,
            baseOffsetY = 52.dp,
            baseRotation = -5.5f,
            entryProgress = cardProgress[3].value,
            floatOffsetY = if (reducedMotion) 0f else float4,
            rotationDelta = if (reducedMotion) 0f else tilt4,
            testTag = "onboarding_scene1_card_lavender"
        )
    }
}

@Composable
private fun FloatingCaptureCard(
    color: Color,
    icon: ImageVector,
    label: String,
    caption: String,
    width: Dp,
    height: Dp,
    baseOffsetX: Dp,
    baseOffsetY: Dp,
    baseRotation: Float,
    entryProgress: Float,
    floatOffsetY: Float,
    rotationDelta: Float,
    testTag: String
) {
    val scale = 0.72f + (0.28f * entryProgress)
    val entrySlideDp = (1f - entryProgress) * 34f

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = color,
        border = BorderStroke(1.5.dp, VaultDarkInk.copy(alpha = 0.14f)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .offset(
                x = baseOffsetX,
                y = baseOffsetY + (floatOffsetY + entrySlideDp).dp
            )
            .size(width = width, height = height)
            .graphicsLayer {
                alpha = (0.35f + 0.65f * entryProgress).coerceIn(0.35f, 1f)
                scaleX = scale
                scaleY = scale
                rotationZ = baseRotation + rotationDelta
            }
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(VaultDarkInk.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = VaultDarkInk,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.42f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = VaultDarkInk
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = VaultDarkInk,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(VaultDarkInk.copy(alpha = 0.25f))
                )
            }
        }
    }
}

/**
 * SCREEN 2 — ORGANIZE YOUR WAY
 * Clean floating miniature workflow list above the title where list items enter sequentially
 * with spring translation and opacity, plus gentle continuous floating motion.
 */
@Composable
private fun OrganizeYourWayAnimatedStage(
    reducedMotion: Boolean
) {
    val vaultColors = IdeaVaultTheme.colors

    val rowProgress = remember { List(4) { Animatable(if (reducedMotion) 1f else 0f) } }

    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            rowProgress.forEachIndexed { idx, anim ->
                launch {
                    delay(idx * 100L)
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = 280f
                        )
                    )
                }
            }
        } else {
            rowProgress.forEach { it.snapTo(1f) }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scene2_float")
    val panelFloatY by infiniteTransition.animateFloat(
        initialValue = -4.5f,
        targetValue = 4.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "panel_float_y"
    )
    val panelTilt by infiniteTransition.animateFloat(
        initialValue = -0.8f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "panel_tilt"
    )

    data class WorkflowRowSpec(
        val title: String,
        val tag: String,
        val accent: Color,
        val checked: Boolean
    )

    val rows = remember {
        listOf(
            WorkflowRowSpec("Mobile App Architecture", "Idea", VaultCoral, true),
            WorkflowRowSpec("Release QA Checklist", "Method", VaultLime, true),
            WorkflowRowSpec("Code Review Prompt", "Prompt", VaultYellow, false),
            WorkflowRowSpec("Typography Tokens", "Note", VaultLavender, true)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_stage_organize"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = vaultColors.surface,
            border = BorderStroke(1.dp, vaultColors.border),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .offset(y = (if (reducedMotion) 0f else panelFloatY).dp)
                .graphicsLayer {
                    rotationZ = if (reducedMotion) 0f else panelTilt
                }
                .testTag("onboarding_scene2_panel")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Miniature filter chip header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("All" to true, "Ideas" to false, "Methods" to false, "Notes" to false).forEach { (label, active) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) VaultActionYellow else vaultColors.secondarySurface)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (active) VaultDarkInk else vaultColors.secondaryText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                rows.forEachIndexed { idx, row ->
                    val progress = rowProgress[idx].value
                    val slideX = (1f - progress) * 36f

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(x = slideX.dp)
                            .graphicsLayer {
                                alpha = (0.35f + 0.65f * progress).coerceIn(0.35f, 1f)
                                scaleX = 0.92f + (0.08f * progress)
                                scaleY = 0.92f + (0.08f * progress)
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(vaultColors.secondarySurface)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("onboarding_scene2_row_$idx"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(row.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            if (row.checked) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = VaultDarkInk,
                                    modifier = Modifier.size(15.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = VaultDarkInk,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = vaultColors.primaryText,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(row.accent.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = row.tag,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (vaultColors.isDark) row.accent else VaultDarkInk
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * SCREEN 3 — EVERYTHING IN ONE PLACE
 * Layered miniature cards representing:
 * 1. Images
 * 2. Links
 * 3. Files
 * 4. Code snippets
 * Animated into a tidy overlapping stack with subtle independent continuous movement.
 */
@Composable
private fun EverythingInOnePlaceAnimatedStage(
    reducedMotion: Boolean
) {
    val stackProgress = remember { List(4) { Animatable(if (reducedMotion) 1f else 0f) } }

    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            stackProgress.forEachIndexed { index, anim ->
                launch {
                    delay(index * 90L)
                    anim.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.68f,
                            stiffness = 290f
                        )
                    )
                }
            }
        } else {
            stackProgress.forEach { it.snapTo(1f) }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "scene3_float")
    val float1 by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scene3_float_1"
    )
    val float2 by infiniteTransition.animateFloat(
        initialValue = 4.5f,
        targetValue = -4.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scene3_float_2"
    )
    val float3 by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scene3_float_3"
    )
    val float4 by infiniteTransition.animateFloat(
        initialValue = 5.5f,
        targetValue = -5.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scene3_float_4"
    )

    val tilt1 by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scene3_tilt_1"
    )
    val tilt2 by infiniteTransition.animateFloat(
        initialValue = 1.2f,
        targetValue = -1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scene3_tilt_2"
    )
    val tilt3 by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scene3_tilt_3"
    )
    val tilt4 by infiniteTransition.animateFloat(
        initialValue = 1.1f,
        targetValue = -1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scene3_tilt_4"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_stage_everything"),
        contentAlignment = Alignment.Center
    ) {
        // 1. Images Miniature Card (Top-Left layered)
        LayeredResourceMiniCard(
            title = "Images & Visuals",
            subtitle = "wireframe_v2.png • 1.4 MB",
            badge = "IMAGE",
            icon = Icons.Outlined.Image,
            cardColor = VaultCoral,
            offsetX = (-34).dp,
            offsetY = (-68).dp,
            rotation = -5f + if (reducedMotion) 0f else tilt1,
            progress = stackProgress[0].value,
            floatY = if (reducedMotion) 0f else float1,
            testTag = "onboarding_scene3_card_images"
        )

        // 2. Links Miniature Card (Top-Right layered)
        LayeredResourceMiniCard(
            title = "Saved Web Links",
            subtitle = "developer.android.com/compose",
            badge = "LINK",
            icon = Icons.Outlined.Link,
            cardColor = VaultLime,
            offsetX = 32.dp,
            offsetY = (-20).dp,
            rotation = 4.5f + if (reducedMotion) 0f else tilt2,
            progress = stackProgress[1].value,
            floatY = if (reducedMotion) 0f else float2,
            testTag = "onboarding_scene3_card_links"
        )

        // 3. Files Miniature Card (Middle-Left layered)
        LayeredResourceMiniCard(
            title = "Documents & PDFs",
            subtitle = "product_spec_v2.pdf • Offline",
            badge = "FILE",
            icon = Icons.Outlined.Description,
            cardColor = VaultYellow,
            offsetX = (-26).dp,
            offsetY = 30.dp,
            rotation = -3.5f + if (reducedMotion) 0f else tilt3,
            progress = stackProgress[2].value,
            floatY = if (reducedMotion) 0f else float3,
            testTag = "onboarding_scene3_card_files"
        )

        // 4. Code Snippets Miniature Card (Bottom-Right foreground layered)
        LayeredResourceMiniCard(
            title = "val vault = remember { ... }",
            subtitle = "Kotlin Compose snippet",
            badge = "CODE",
            icon = Icons.Outlined.Code,
            cardColor = VaultLavender,
            offsetX = 24.dp,
            offsetY = 78.dp,
            rotation = 3f + if (reducedMotion) 0f else tilt4,
            progress = stackProgress[3].value,
            floatY = if (reducedMotion) 0f else float4,
            isMonospaceTitle = true,
            testTag = "onboarding_scene3_card_code"
        )
    }
}

@Composable
private fun LayeredResourceMiniCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    cardColor: Color,
    offsetX: Dp,
    offsetY: Dp,
    rotation: Float,
    progress: Float,
    floatY: Float,
    isMonospaceTitle: Boolean = false,
    testTag: String
) {
    val scale = 0.78f + (0.22f * progress)
    val slideY = (1f - progress) * 28f

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardColor,
        border = BorderStroke(1.5.dp, VaultDarkInk.copy(alpha = 0.15f)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .width(236.dp)
            .offset(
                x = offsetX,
                y = offsetY + (floatY + slideY).dp
            )
            .graphicsLayer {
                alpha = (0.35f + 0.65f * progress).coerceIn(0.35f, 1f)
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VaultDarkInk.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = badge,
                    tint = VaultDarkInk,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = if (isMonospaceTitle) JetBrainsMonoFontFamily else MaterialTheme.typography.labelLarge.fontFamily
                    ),
                    color = VaultDarkInk,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = VaultDarkInk.copy(alpha = 0.75f),
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.45f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = VaultDarkInk
                )
            }
        }
    }
}
