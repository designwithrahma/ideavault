package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.VaultCategory
import com.example.data.VaultItemWithDetails
import com.example.ui.theme.IdeaVaultTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CutoutCorner {
    BottomRight,
    TopRight
}

/**
 * Custom bottom-right concave cutout card shape used on Vault cards and verified by unit tests.
 */
class CurvedBottomRightCutoutShape(
    private val cornerRadius: Dp = 28.dp,
    private val cutoutRadius: Dp = 32.dp,
    private val smoothing: Dp = 12.dp
) : Shape {
    constructor(
        cornerRadiusDp: Dp,
        cutoutRadiusDp: Dp
    ) : this(cornerRadius = cornerRadiusDp, cutoutRadius = cutoutRadiusDp, smoothing = 12.dp)

    private val delegate = CurvedCutoutCardShape(
        cornerRadius = cornerRadius,
        cutoutRadius = cutoutRadius,
        smoothing = smoothing,
        cutoutCorner = CutoutCorner.BottomRight
    )

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = delegate.createOutline(size, layoutDirection, density)
}

/**
 * Custom editorial card shape with a true inward concave notch cutout at the corner
 * revealing negative space so a standalone circular yellow arrow button sits inside the notch.
 */
class CurvedCutoutCardShape(
    private val cornerRadius: Dp = 28.dp,
    private val cutoutRadius: Dp = 32.dp,
    private val smoothing: Dp = 12.dp,
    private val cutoutCorner: CutoutCorner = CutoutCorner.BottomRight
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(size.minDimension / 3f)

        // Match the standalone circular action button diameter ((cutoutRadius + 10.dp).coerceIn(38.dp, 46.dp))
        // and add a uniform negative-space channel around it so the parent White/Dark canvas is visible.
        val buttonDiameterDp = (cutoutRadius + 10.dp).coerceIn(38.dp, 46.dp)
        val buttonRadius = with(density) { (buttonDiameterDp / 2f).toPx() }
            .coerceAtMost(size.minDimension / 3.4f)
        val gapPx = with(density) { 7.dp.toPx() }
            .coerceAtMost(size.minDimension * 0.08f)
        val innerArcRadius = buttonRadius + gapPx
        val filletRadius = with(density) { smoothing.toPx() }
            .coerceIn(with(density) { 6.dp.toPx() }, buttonRadius * 0.65f)

        val hypotDist = innerArcRadius + filletRadius
        val dx = (buttonRadius - filletRadius).coerceAtLeast(0f)
        val dy = kotlin.math.sqrt((hypotDist * hypotDist - dx * dx).coerceAtLeast(0f))
        val thetaDeg = Math.toDegrees(
            kotlin.math.asin((dx / hypotDist).coerceIn(-1f, 1f)).toDouble()
        ).toFloat()

        val path = Path().apply {
            when (cutoutCorner) {
                CutoutCorner.BottomRight -> {
                    val cx = w - buttonRadius
                    val cy = h - buttonRadius
                    val topShoulderCenterY = (cy - dy).coerceAtLeast(r)
                    val bottomShoulderCenterX = (cx - dy).coerceAtLeast(r)

                    moveTo(r, 0f)
                    lineTo(w - r, 0f)
                    arcTo(
                        rect = Rect(w - 2 * r, 0f, w, 2 * r),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(w, topShoulderCenterY)
                    // Top-right convex shoulder fillet into concave cutout
                    arcTo(
                        rect = Rect(
                            w - 2 * filletRadius,
                            topShoulderCenterY - filletRadius,
                            w,
                            topShoulderCenterY + filletRadius
                        ),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f + thetaDeg,
                        forceMoveTo = false
                    )
                    // True concentric inward concave arc around the standalone yellow button
                    arcTo(
                        rect = Rect(
                            cx - innerArcRadius,
                            cy - innerArcRadius,
                            cx + innerArcRadius,
                            cy + innerArcRadius
                        ),
                        startAngleDegrees = -90f + thetaDeg,
                        sweepAngleDegrees = -(90f + 2f * thetaDeg),
                        forceMoveTo = false
                    )
                    // Bottom-left convex shoulder fillet back onto bottom edge y = h
                    arcTo(
                        rect = Rect(
                            bottomShoulderCenterX - filletRadius,
                            h - 2 * filletRadius,
                            bottomShoulderCenterX + filletRadius,
                            h
                        ),
                        startAngleDegrees = -thetaDeg,
                        sweepAngleDegrees = 90f + thetaDeg,
                        forceMoveTo = false
                    )
                    lineTo(r, h)
                    arcTo(
                        rect = Rect(0f, h - 2 * r, 2 * r, h),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(0f, r)
                    arcTo(
                        rect = Rect(0f, 0f, 2 * r, 2 * r),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    close()
                }

                CutoutCorner.TopRight -> {
                    val cx = w - buttonRadius
                    val cy = buttonRadius
                    val topShoulderCenterX = (cx - dy).coerceAtLeast(r)
                    val bottomShoulderCenterY = (cy + dy).coerceAtMost(h - r)

                    moveTo(r, 0f)
                    lineTo(topShoulderCenterX, 0f)
                    arcTo(
                        rect = Rect(
                            topShoulderCenterX - filletRadius,
                            0f,
                            topShoulderCenterX + filletRadius,
                            2 * filletRadius
                        ),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = 90f + thetaDeg,
                        forceMoveTo = false
                    )
                    arcTo(
                        rect = Rect(
                            cx - innerArcRadius,
                            cy - innerArcRadius,
                            cx + innerArcRadius,
                            cy + innerArcRadius
                        ),
                        startAngleDegrees = -180f + thetaDeg,
                        sweepAngleDegrees = -(90f + 2f * thetaDeg),
                        forceMoveTo = false
                    )
                    arcTo(
                        rect = Rect(
                            w - 2 * filletRadius,
                            bottomShoulderCenterY - filletRadius,
                            w,
                            bottomShoulderCenterY + filletRadius
                        ),
                        startAngleDegrees = -90f - thetaDeg,
                        sweepAngleDegrees = 90f + thetaDeg,
                        forceMoveTo = false
                    )
                    lineTo(w, h - r)
                    arcTo(
                        rect = Rect(w - 2 * r, h - 2 * r, w, h),
                        startAngleDegrees = 0f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(r, h)
                    arcTo(
                        rect = Rect(0f, h - 2 * r, 2 * r, h),
                        startAngleDegrees = 90f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    lineTo(0f, r)
                    arcTo(
                        rect = Rect(0f, 0f, 2 * r, 2 * r),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false
                    )
                    close()
                }
            }
        }
        return Outline.Generic(path)
    }
}

@Composable
fun VaultCategory.themeCardColor(): Color = IdeaVaultTheme.colors.categoryCardColor(this)

@Composable
fun VaultCategory.themeContentColor(): Color = IdeaVaultTheme.colors.categoryContentColor(this)

@Composable
fun VaultCategory.themeSecondaryContentColor(): Color = IdeaVaultTheme.colors.categorySecondaryContentColor(this)

private fun formatShortCardDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d", Locale.US)
    return sdf.format(Date(timestamp))
}

@Composable
fun CurvedActionCardContainer(
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cutoutCorner: CutoutCorner = CutoutCorner.BottomRight,
    cornerRadius: Dp = 26.dp,
    cutoutRadius: Dp = 32.dp,
    actionButtonColor: Color? = null,
    actionIconColor: Color? = null,
    actionIcon: ImageVector = Icons.Default.NorthEast,
    borderColor: Color = Color.Transparent,
    testTag: String? = null,
    actionContentDescription: String = "Open item",
    content: @Composable BoxScope.() -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    // V2: Standalone circular button is #F9D55D with #191919 arrow icon
    val resolvedActionButtonColor = actionButtonColor ?: vaultColors.actionYellow
    val resolvedActionIconColor = actionIconColor ?: vaultColors.darkInk

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 520f),
        label = "card_press_scale"
    )

    val shape = remember(cornerRadius, cutoutRadius, cutoutCorner) {
        CurvedCutoutCardShape(
            cornerRadius = cornerRadius,
            cutoutRadius = cutoutRadius,
            smoothing = 12.dp,
            cutoutCorner = cutoutCorner
        )
    }

    val visualButtonSize = (cutoutRadius + 10.dp).coerceIn(38.dp, 46.dp)
    val touchTargetSize = 48.dp

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Card body clipped by true concave cutout shape (negative space is 100% transparent)
        // Subtle tactile ripple is drawn strictly inside the clipped concave card outline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(backgroundColor)
                .indication(interactionSource, LocalIndication.current)
                .then(
                    if (borderColor != Color.Transparent) {
                        Modifier.border(1.dp, borderColor, shape)
                    } else {
                        Modifier
                    }
                ),
            content = content
        )

        val alignment = when (cutoutCorner) {
            CutoutCorner.BottomRight -> Alignment.BottomEnd
            CutoutCorner.TopRight -> Alignment.TopEnd
        }

        // 48dp x 48dp accessible touch region aligned to the cutout corner;
        // visual circular #F9D55D button stays sized to the concave recess so card geometry is preserved.
        Box(
            modifier = Modifier
                .align(alignment)
                .size(touchTargetSize)
                .then(
                    if (testTag != null) {
                        Modifier.testTag("${testTag}_action_button")
                    } else {
                        Modifier
                    }
                )
                .semantics(mergeDescendants = true) { role = Role.Button }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(visualButtonSize)
                    .clip(CircleShape)
                    .background(resolvedActionButtonColor)
                    .indication(interactionSource, LocalIndication.current),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionContentDescription,
                    tint = resolvedActionIconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * IDEAVAULT V2 Curved Vault Card used in Home, Search, Favourite, and Collections.
 * Features:
 * - True concave bottom-right cutout with transparent negative space and standalone #F9D55D arrow button
 * - Optional #E994AD favorite heart icon + short date ("Oct 26") + Category pill ("Idea", "Method", "Prompt", "Note")
 * - Top-right overflow menu (⋮)
 * - Bold #191919 title and clean summary preview
 * - Bottom metadata pills ("5 steps", "120 words")
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultEntryCurvedCard(
    item: VaultItemWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    useColorFill: Boolean = true,
    showFavoriteHeart: Boolean = item.entity.isFavorite,
    onToggleFavorite: (() -> Unit)? = null,
    onTogglePin: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    testTag: String = "vault_item_card_${item.entity.id}"
) {
    val vaultColors = IdeaVaultTheme.colors
    val category = item.categoryEnum
    val bgColor = if (useColorFill) category.themeCardColor() else vaultColors.surface
    val titleColor = if (useColorFill) vaultColors.darkInk else vaultColors.primaryText
    val subColor = if (useColorFill) vaultColors.darkInk.copy(alpha = 0.78f) else vaultColors.secondaryText
    val pillBg = if (useColorFill) {
        Color.White.copy(alpha = 0.34f)
    } else {
        vaultColors.secondarySurface
    }
    val metaPillBg = if (useColorFill) {
        Color.White.copy(alpha = 0.28f)
    } else {
        vaultColors.secondarySurface
    }
    val pillText = if (useColorFill) vaultColors.darkInk else vaultColors.primaryText

    val cleanPreviewSummary = remember(item.entity.description, item.entity.content, item.categoryEnum) {
        val raw = item.entity.description.ifBlank { item.entity.content }
        if (item.categoryEnum == VaultCategory.PROMPT) {
            raw.replace("\n", " ").trim()
        } else {
            RichTextSerializer.deserialize(raw).plainText
                .replace("\n", " ")
                .trim()
        }
    }

    val shortDate = remember(item.entity.updatedAt) {
        formatShortCardDate(item.entity.updatedAt)
    }

    var overflowExpanded by remember { mutableStateOf(false) }

    val minCardHeight = if (compact) 168.dp else 182.dp

    CurvedActionCardContainer(
        backgroundColor = bgColor,
        onClick = onClick,
        cutoutCorner = CutoutCorner.BottomRight,
        cornerRadius = 26.dp,
        cutoutRadius = if (compact) 30.dp else 34.dp,
        actionButtonColor = vaultColors.actionYellow,
        actionIconColor = vaultColors.darkInk,
        borderColor = if (useColorFill) Color.Transparent else vaultColors.border,
        testTag = testTag,
        actionContentDescription = "Open ${item.entity.title}",
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minCardHeight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minCardHeight)
                .padding(
                    start = 20.dp,
                    top = 16.dp,
                    end = 14.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top Row: [Heart (if favorite)] + Date + Category Pill + [Folder Pill] ... [Pin] + Overflow (⋮)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (showFavoriteHeart || item.entity.isFavorite) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Favourite",
                                tint = vaultColors.pink,
                                modifier = Modifier
                                    .size(18.dp)
                                    .then(
                                        if (onToggleFavorite != null) {
                                            Modifier.clickable(onClick = onToggleFavorite)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .testTag("${testTag}_favorite_heart")
                            )
                        }

                        Text(
                            text = shortDate,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = subColor
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillBg)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = category.singularLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = pillText
                            )
                        }

                        item.folderName?.takeIf { it.isNotBlank() }?.let { folderName ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(pillBg)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pillText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (item.entity.isPinned) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                tint = titleColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Box {
                            IconButton(
                                onClick = {
                                    if (onToggleFavorite != null || onTogglePin != null || onEdit != null || onDuplicate != null || onArchive != null || onDelete != null) {
                                        overflowExpanded = true
                                    } else {
                                        onClick()
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("${testTag}_more_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = titleColor.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false },
                                modifier = Modifier.background(vaultColors.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Open", color = vaultColors.primaryText) },
                                    leadingIcon = {
                                        Icon(Icons.Default.NorthEast, contentDescription = null, tint = vaultColors.primaryText)
                                    },
                                    onClick = {
                                        overflowExpanded = false
                                        onClick()
                                    }
                                )
                                if (onEdit != null) {
                                    DropdownMenuItem(
                                        text = { Text("Edit", color = vaultColors.primaryText) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = vaultColors.primaryText)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onEdit()
                                        }
                                    )
                                }
                                if (onToggleFavorite != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (item.entity.isFavorite) "Remove from Favourites" else "Add to Favourites",
                                                color = vaultColors.primaryText
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                if (item.entity.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                                contentDescription = null,
                                                tint = vaultColors.pink
                                            )
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onToggleFavorite()
                                        }
                                    )
                                }
                                if (onTogglePin != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (item.entity.isPinned) "Unpin" else "Pin",
                                                color = vaultColors.primaryText
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.PushPin, contentDescription = null, tint = vaultColors.primaryText)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onTogglePin()
                                        }
                                    )
                                }
                                if (onDuplicate != null) {
                                    DropdownMenuItem(
                                        text = { Text("Duplicate", color = vaultColors.primaryText) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = vaultColors.primaryText)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onDuplicate()
                                        }
                                    )
                                }
                                if (onArchive != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (item.entity.isArchived) "Unarchive" else "Archive",
                                                color = vaultColors.primaryText
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.Archive, contentDescription = null, tint = vaultColors.primaryText)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onArchive()
                                        }
                                    )
                                }
                                if (onDelete != null) {
                                    DropdownMenuItem(
                                        text = { Text("Move to Trash", color = vaultColors.coral) },
                                        leadingIcon = {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = vaultColors.coral)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onDelete()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Title
                Text(
                    text = item.entity.title,
                    style = if (compact) {
                        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    } else {
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            lineHeight = 25.sp
                        )
                    },
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 18.dp)
                )

                // Short description / preview
                if (cleanPreviewSummary.isNotBlank()) {
                    Text(
                        text = cleanPreviewSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = subColor,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Metadata Row (padded away from bottom-right concave cutout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (category == VaultCategory.METHOD || item.steps.isNotEmpty()) {
                    val stepCount = item.steps.size
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(metaPillBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatListNumbered,
                            contentDescription = null,
                            tint = pillText,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "$stepCount ${if (stepCount == 1) "step" else "steps"}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = pillText,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(metaPillBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = pillText,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${item.wordCount} words",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = pillText,
                        maxLines = 1
                    )
                }

                if (item.tags.isNotEmpty() && !compact) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        maxItemsInEachRow = 1
                    ) {
                        item.tags.take(1).forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(metaPillBg)
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = pillText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CurvedVaultCard(
    item: VaultItemWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    VaultEntryCurvedCard(
        item = item,
        onClick = onClick,
        modifier = modifier
    )
}
