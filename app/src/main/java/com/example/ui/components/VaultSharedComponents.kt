package com.example.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AttachmentItem
import com.example.data.AttachmentStorageManager
import com.example.data.AttachmentType
import com.example.data.VaultCategory
import com.example.data.VaultItemWithDetails
import com.example.ui.theme.IdeaVaultTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Throwable) {
            false
        }
    }
}

fun VaultCategory.icon(): ImageVector = when (this) {
    VaultCategory.IDEA -> Icons.Outlined.Lightbulb
    VaultCategory.METHOD -> Icons.Outlined.Psychology
    VaultCategory.PROMPT -> Icons.Outlined.AutoAwesome
    VaultCategory.KNOWLEDGE -> Icons.Outlined.Description
    VaultCategory.REFERENCE -> Icons.Outlined.BookmarkBorder
    VaultCategory.INBOX -> Icons.Outlined.Inbox
}

fun formatVaultTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
    return sdf.format(Date(timestamp))
}

fun formatVaultDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US)
    return sdf.format(Date(timestamp))
}

@Composable
fun VaultSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search ideas, methods, prompts...",
    modifier: Modifier = Modifier,
    onFilterClick: (() -> Unit)? = null,
    hasActiveFilter: Boolean = false,
    readOnlyEntryPoint: Boolean = false,
    onEntryPointClick: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    testTag: String = "vault_search_input"
) {
    val vaultColors = IdeaVaultTheme.colors
    val barBg = if (vaultColors.isDark) vaultColors.surface else vaultColors.secondarySurface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(barBg)
            .border(1.dp, vaultColors.border, RoundedCornerShape(26.dp))
            .then(
                if (readOnlyEntryPoint && onEntryPointClick != null) {
                    Modifier
                        .clickable(onClick = onEntryPointClick)
                        .testTag(testTag)
                } else {
                    Modifier
                }
            )
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = vaultColors.secondaryText,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (readOnlyEntryPoint) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = vaultColors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = vaultColors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = vaultColors.primaryText),
                    cursorBrush = SolidColor(vaultColors.actionYellow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .testTag(testTag)
                )
            }
        }
        if (!readOnlyEntryPoint && query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${testTag}_clear")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = vaultColors.secondaryText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (onFilterClick != null) {
            IconButton(
                onClick = onFilterClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${testTag}_filter")
            ) {
                Icon(
                    imageVector = Icons.Outlined.FilterAlt,
                    contentDescription = "Filter entries",
                    tint = if (hasActiveFilter) vaultColors.actionYellow else vaultColors.primaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(10.dp))
        }
    }
}

/**
 * IDEAVAULT V2 Horizontal Category Filter Chips:
 * All | Ideas | Methods | Prompts | Notes
 * Active chip is filled with #F9D55D (actionYellow) and #191919 text.
 */
@Composable
fun CategoryFilterPillRow(
    selectedCategory: VaultCategory?,
    onSelectCategory: (VaultCategory?) -> Unit,
    modifier: Modifier = Modifier,
    includeInbox: Boolean = false
) {
    val vaultColors = IdeaVaultTheme.colors
    val categories = remember(includeInbox) {
        if (includeInbox) {
            VaultCategory.v2PrimaryCategories + VaultCategory.INBOX
        } else {
            VaultCategory.v2PrimaryCategories
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterPillItem(
                label = "All",
                selected = selectedCategory == null,
                activeColor = vaultColors.actionYellow,
                onClick = { onSelectCategory(null) },
                testTag = "filter_chip_all"
            )
        }
        items(categories) { category ->
            FilterPillItem(
                label = category.label,
                selected = selectedCategory == category,
                activeColor = vaultColors.actionYellow,
                onClick = {
                    onSelectCategory(if (selectedCategory == category) null else category)
                },
                testTag = "filter_chip_${category.name.lowercase()}"
            )
        }
    }
}

@Composable
fun FilterPillItem(
    label: String,
    selected: Boolean,
    activeColor: Color = IdeaVaultTheme.colors.actionYellow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val vaultColors = IdeaVaultTheme.colors
    val bgColor by animateColorAsState(
        targetValue = if (selected) activeColor else vaultColors.surface,
        animationSpec = tween(160),
        label = "pill_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) vaultColors.darkInk else vaultColors.primaryText,
        animationSpec = tween(160),
        label = "pill_text"
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .semantics { role = Role.Tab }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .then(if (modifier == Modifier) Modifier else Modifier.fillMaxWidth())
                .defaultMinSize(minHeight = 40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .border(
                    width = 1.dp,
                    color = if (selected) Color.Transparent else vaultColors.border,
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = textColor
            )
        }
    }
}

@Composable
fun VaultScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val vaultColors = IdeaVaultTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(vaultColors.surface)
                        .border(1.dp, vaultColors.border, CircleShape)
                        .testTag("top_bar_back_button")
                        .semantics {
                            role = Role.Button
                            contentDescription = "Navigate back"
                        }
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = vaultColors.primaryText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.secondaryText
                    )
                }
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
fun VaultEmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Outlined.Lightbulb,
    accentColor: Color = IdeaVaultTheme.colors.actionYellow,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onActionClick: (() -> Unit)? = onAction,
    modifier: Modifier = Modifier
) {
    val vaultColors = IdeaVaultTheme.colors
    val resolvedAction = onActionClick ?: onAction
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.18f))
                .border(1.dp, accentColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (vaultColors.isDark) accentColor else vaultColors.darkInk,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = vaultColors.primaryText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = vaultColors.secondaryText,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && resolvedAction != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(vaultColors.actionYellow)
                    .clickable(onClick = resolvedAction)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.darkInk
                )
            }
        }
    }
}

/**
 * Rich, clean attachment card used in both EditorScreen and ItemDetailScreen.
 */
@Composable
fun VaultAttachmentCard(
    attachment: AttachmentItem,
    accentColor: Color = IdeaVaultTheme.colors.coral,
    onPreview: (() -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
    onOpenExternal: (() -> Unit)? = onOpen,
    onRemove: (() -> Unit)? = null,
    testTagPrefix: String = "attachment_card"
) {
    val resolvedOpen = onOpenExternal ?: onOpen
    val vaultColors = IdeaVaultTheme.colors
    val isImage = attachment.type == AttachmentType.IMAGE ||
        attachment.mimeType.startsWith("image/", ignoreCase = true)

    val imageBitmap = remember(attachment.uriOrUrl, isImage) {
        if (!isImage || attachment.uriOrUrl.isBlank()) {
            null
        } else {
            try {
                val file = File(attachment.uriOrUrl)
                if (file.exists() && file.isFile) {
                    val opts = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, opts)
                    val maxDim = 240
                    var sampleSize = 1
                    while (opts.outWidth / sampleSize > maxDim || opts.outHeight / sampleSize > maxDim) {
                        sampleSize *= 2
                    }
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    BitmapFactory.decodeFile(file.absolutePath, decodeOpts)?.asImageBitmap()
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
        }
    }

    val cleanSubtitle = remember(attachment.type, attachment.uriOrUrl, attachment.mimeType, attachment.sizeBytes) {
        buildCleanAttachmentSubtitle(
            type = attachment.type,
            uriOrUrl = attachment.uriOrUrl,
            mimeType = attachment.mimeType,
            fileSizeBytes = attachment.sizeBytes
        )
    }

    val displayName = remember(attachment.name, attachment.uriOrUrl, attachment.type) {
        when {
            attachment.name.isNotBlank() && !attachment.name.startsWith("/data/") -> attachment.name
            attachment.uriOrUrl.startsWith("/") -> File(attachment.uriOrUrl).name.substringAfter("_", File(attachment.uriOrUrl).name)
            else -> attachment.name.ifBlank { attachment.type.label }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(vaultColors.secondarySurface)
            .border(1.dp, vaultColors.border, RoundedCornerShape(16.dp))
            .clickable {
                when {
                    onPreview != null -> onPreview()
                    onOpen != null -> onOpen()
                }
            }
            .padding(12.dp)
            .testTag(testTagPrefix),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, vaultColors.border, RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (attachment.type) {
                        AttachmentType.LINK -> Icons.Outlined.Link
                        AttachmentType.IMAGE -> Icons.Outlined.Image
                        AttachmentType.PDF -> Icons.Outlined.PictureAsPdf
                        AttachmentType.FILE -> Icons.Outlined.Description
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = attachment.type.label,
                        tint = if (vaultColors.isDark) accentColor else vaultColors.darkInk,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = vaultColors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cleanSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = vaultColors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (onPreview != null) {
                IconButton(
                    onClick = onPreview,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_preview")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Visibility,
                        contentDescription = "Preview attachment",
                        tint = vaultColors.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (resolvedOpen != null) {
                IconButton(
                    onClick = resolvedOpen,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_open")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = "Open attachment",
                        tint = vaultColors.primaryText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("${testTagPrefix}_remove")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove attachment",
                        tint = vaultColors.coral,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

fun buildCleanAttachmentSubtitle(
    type: AttachmentType,
    uriOrUrl: String,
    mimeType: String,
    fileSizeBytes: Long
): String {
    val parts = mutableListOf<String>()
    val cleanTypeLabel = when {
        type == AttachmentType.LINK -> "Website Link"
        type == AttachmentType.PDF || mimeType.equals("application/pdf", ignoreCase = true) -> "PDF Document"
        type == AttachmentType.IMAGE || mimeType.startsWith("image/", ignoreCase = true) -> {
            val subtype = mimeType.substringAfter("image/", "").uppercase(Locale.US)
            if (subtype.isNotBlank() && subtype.length <= 5) "$subtype Image" else "Image"
        }
        mimeType.startsWith("text/", ignoreCase = true) -> "Text Document"
        else -> type.label
    }
    parts.add(cleanTypeLabel)

    val resolvedSize = if (fileSizeBytes > 0L) {
        fileSizeBytes
    } else if (type != AttachmentType.LINK && uriOrUrl.startsWith("/")) {
        try {
            val f = File(uriOrUrl)
            if (f.exists() && f.isFile) f.length() else 0L
        } catch (_: Throwable) {
            0L
        }
    } else {
        0L
    }

    if (resolvedSize > 0L) {
        parts.add(AttachmentStorageManager.formatFileSize(resolvedSize))
    } else if (type == AttachmentType.LINK && uriOrUrl.isNotBlank()) {
        val host = try {
            Uri.parse(uriOrUrl).host?.removePrefix("www.")
        } catch (_: Throwable) {
            null
        }
        if (!host.isNullOrBlank()) {
            parts.add(host)
        }
    }

    return parts.joinToString(" • ")
}

@Composable
fun CategoryPillBadge(
    category: VaultCategory,
    modifier: Modifier = Modifier
) {
    val vaultColors = IdeaVaultTheme.colors
    val bgColor = vaultColors.categoryCardColor(category)
    val contentColor = vaultColors.categoryContentColor(category)
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Text(
            text = category.singularLabel,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun VaultTagChip(
    tag: String,
    modifier: Modifier = Modifier
) {
    val vaultColors = IdeaVaultTheme.colors
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = vaultColors.secondarySurface,
        modifier = modifier
    ) {
        Text(
            text = if (tag.startsWith("#")) tag else "#$tag",
            style = MaterialTheme.typography.labelMedium,
            color = vaultColors.primaryText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun VaultFilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color = IdeaVaultTheme.colors.actionYellow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterPillItem(
        label = label,
        selected = selected,
        activeColor = accentColor,
        onClick = onClick,
        modifier = modifier
    )
}

@Composable
fun VaultSectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val vaultColors = IdeaVaultTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = vaultColors.primaryText
        )
        if (actionText != null && onActionClick != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .widthIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = vaultColors.coral
                )
            }
        }
    }
}

@Composable
fun CompactVaultRowCard(
    item: VaultItemWithDetails,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vaultColors = IdeaVaultTheme.colors
    val summary = remember(item.entity.description, item.entity.content) {
        val raw = item.entity.description.ifBlank { item.entity.content }
        RichTextSerializer.deserialize(raw).plainText.replace("\n", " ").trim()
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = vaultColors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, vaultColors.border),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryPillBadge(category = item.categoryEnum)
                    if (!item.folderName.isNullOrBlank()) {
                        Text(
                            text = item.folderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = vaultColors.secondaryText
                        )
                    }
                }
                Text(
                    text = item.entity.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = vaultColors.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = vaultColors.secondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (item.entity.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (item.entity.isFavorite) vaultColors.pink else vaultColors.secondaryText
                )
            }
        }
    }
}
