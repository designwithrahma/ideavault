package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatAlignRight
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.IdeaVaultTheme
import kotlin.math.max
import kotlin.math.min

enum class InlineRichStyle {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKETHROUGH,
    HIGHLIGHT
}

enum class LineBlockFormat {
    PARAGRAPH,
    HEADING,
    BULLET_LIST,
    NUMBERED_LIST,
    LETTERED_LIST,
    QUOTE
}

enum class ParagraphAlignment {
    LEFT,
    CENTER,
    RIGHT
}

enum class RichTextColorToken(val id: String, val label: String, val hexColor: Color?) {
    DEFAULT("DEFAULT", "Default", null),
    CORAL("CORAL", "Coral", Color(0xFFEF7B59)),
    GREEN("GREEN", "Green", Color(0xFFA7D678)),
    YELLOW("YELLOW", "Yellow", Color(0xFFF9D55D)),
    LAVENDER("LAVENDER", "Lavender", Color(0xFFA096EF));

    companion object {
        fun fromId(id: String?): RichTextColorToken {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

val SUPPORTED_EDITOR_FONT_SIZES = listOf(12, 14, 16, 18, 20, 24)
const val DEFAULT_EDITOR_FONT_SIZE = 16

enum class EditorBottomToolPanel {
    NONE,
    TEXT_STYLE,
    PARAGRAPH,
    IMAGE,
    ATTACHMENT,
    MORE
}

@Immutable
data class InlineSpanRange(
    val start: Int,
    val end: Int,
    val style: InlineRichStyle
)

@Immutable
data class FontSizeSpanRange(
    val start: Int,
    val end: Int,
    val sizeSp: Int
)

@Immutable
data class FontColorSpanRange(
    val start: Int,
    val end: Int,
    val colorToken: RichTextColorToken
)

@Immutable
data class LinkSpanRange(
    val start: Int,
    val end: Int,
    val url: String
)

@Immutable
data class ParsedRichParagraph(
    val format: LineBlockFormat,
    val alignment: ParagraphAlignment = ParagraphAlignment.LEFT,
    val headingLevel: Int = 0,
    val listNumber: Int = 1,
    val listLetter: Char = 'a',
    val plainText: String,
    val inlineSpans: List<InlineSpanRange>,
    val fontSizeSpans: List<FontSizeSpanRange> = emptyList(),
    val fontColorSpans: List<FontColorSpanRange> = emptyList(),
    val links: List<LinkSpanRange>
)

@Immutable
data class RichTextHistorySnapshot(
    val textFieldValue: TextFieldValue,
    val spans: List<InlineSpanRange>,
    val fontSizeSpans: List<FontSizeSpanRange>,
    val fontColorSpans: List<FontColorSpanRange>,
    val links: List<LinkSpanRange>,
    val lineAlignments: Map<Int, ParagraphAlignment>
)

/**
 * RichTextEditorState manages true span-based rich text state in the editor while serializing
 * cleanly to and from Markdown-compatible storage so existing saved Vault items, drafts,
 * and backups work seamlessly without showing raw syntax.
 */
@Immutable
data class RichTextEditorState(
    val textFieldValue: TextFieldValue = TextFieldValue(""),
    val spans: List<InlineSpanRange> = emptyList(),
    val fontSizeSpans: List<FontSizeSpanRange> = emptyList(),
    val fontColorSpans: List<FontColorSpanRange> = emptyList(),
    val links: List<LinkSpanRange> = emptyList(),
    val lineAlignments: Map<Int, ParagraphAlignment> = emptyMap(),
    val activeTypingStyles: Set<InlineRichStyle> = emptySet(),
    val activeTypingFontSize: Int = DEFAULT_EDITOR_FONT_SIZE,
    val activeTypingColor: RichTextColorToken = RichTextColorToken.DEFAULT,
    val typingStyleExplicitlyOverridden: Boolean = false,
    val undoStack: List<RichTextHistorySnapshot> = emptyList(),
    val redoStack: List<RichTextHistorySnapshot> = emptyList()
) {
    val plainText: String
        get() = textFieldValue.text

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    fun toHistorySnapshot(): RichTextHistorySnapshot = RichTextHistorySnapshot(
        textFieldValue = textFieldValue,
        spans = spans,
        fontSizeSpans = fontSizeSpans,
        fontColorSpans = fontColorSpans,
        links = links,
        lineAlignments = lineAlignments
    )

    fun effectiveActiveStyles(): Set<InlineRichStyle> {
        val sel = textFieldValue.selection
        if (!sel.collapsed) {
            val s = min(sel.start, sel.end)
            val e = max(sel.start, sel.end)
            if (s == e) return activeTypingStyles
            return InlineRichStyle.entries.filterTo(mutableSetOf()) { style ->
                isRangeFullyCoveredByStyle(s, e, style)
            }
        }
        if (typingStyleExplicitlyOverridden) {
            return activeTypingStyles
        }
        val cursor = sel.start
        if (cursor <= 0) return activeTypingStyles
        return spans.filter { cursor > it.start && cursor <= it.end }
            .mapTo(mutableSetOf()) { it.style }
    }

    fun effectiveFontSize(): Int {
        val sel = textFieldValue.selection
        if (!sel.collapsed) {
            val s = min(sel.start, sel.end)
            val e = max(sel.start, sel.end)
            val match = fontSizeSpans.firstOrNull { it.start <= s && it.end >= e }
            return match?.sizeSp ?: activeTypingFontSize
        }
        if (typingStyleExplicitlyOverridden) return activeTypingFontSize
        val cursor = sel.start
        if (cursor <= 0) return activeTypingFontSize
        return fontSizeSpans.lastOrNull { cursor > it.start && cursor <= it.end }?.sizeSp
            ?: DEFAULT_EDITOR_FONT_SIZE
    }

    fun effectiveFontColor(): RichTextColorToken {
        val sel = textFieldValue.selection
        if (!sel.collapsed) {
            val s = min(sel.start, sel.end)
            val e = max(sel.start, sel.end)
            val match = fontColorSpans.firstOrNull { it.start <= s && it.end >= e }
            return match?.colorToken ?: activeTypingColor
        }
        if (typingStyleExplicitlyOverridden) return activeTypingColor
        val cursor = sel.start
        if (cursor <= 0) return activeTypingColor
        return fontColorSpans.lastOrNull { cursor > it.start && cursor <= it.end }?.colorToken
            ?: RichTextColorToken.DEFAULT
    }

    fun currentLineIndex(): Int {
        val text = textFieldValue.text
        val cursor = textFieldValue.selection.start.coerceIn(0, text.length)
        var lineIdx = 0
        for (i in 0 until cursor) {
            if (text[i] == '\n') lineIdx++
        }
        return lineIdx
    }

    fun currentParagraphAlignment(): ParagraphAlignment {
        return lineAlignments[currentLineIndex()] ?: ParagraphAlignment.LEFT
    }

    fun currentLineBlockFormat(): LineBlockFormat {
        val text = textFieldValue.text
        val cursor = textFieldValue.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { if (it == -1) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', cursor).let { if (it == -1) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        return detectLineFormat(line).first
    }

    fun isRangeFullyCoveredByStyle(start: Int, end: Int, style: InlineRichStyle): Boolean {
        if (start >= end) return false
        val matching = spans.filter { it.style == style && it.end > start && it.start < end }
            .sortedBy { it.start }
        if (matching.isEmpty()) return false
        var coveredUntil = start
        for (span in matching) {
            if (span.start > coveredUntil) return false
            coveredUntil = max(coveredUntil, span.end)
            if (coveredUntil >= end) return true
        }
        return coveredUntil >= end
    }

    fun toAnnotatedString(
        linkColor: Color = Color(0xFF9386FF),
        isDarkTheme: Boolean = true
    ): AnnotatedString {
        return buildAnnotatedString {
            append(textFieldValue.text)
            val len = textFieldValue.text.length
            var lineStart = 0
            val lines = textFieldValue.text.split('\n')
            lines.forEachIndexed { lineIdx, line ->
                val lineEnd = (lineStart + line.length).coerceAtMost(len)
                val alignment = lineAlignments[lineIdx] ?: ParagraphAlignment.LEFT
                if (alignment != ParagraphAlignment.LEFT && lineStart <= lineEnd) {
                    val textAlign = when (alignment) {
                        ParagraphAlignment.CENTER -> TextAlign.Center
                        ParagraphAlignment.RIGHT -> TextAlign.End
                        ParagraphAlignment.LEFT -> TextAlign.Start
                    }
                    addStyle(ParagraphStyle(textAlign = textAlign), lineStart, lineEnd)
                }
                if (lineEnd > lineStart) {
                    val (lineFormat, _) = detectLineFormat(line)
                    when (lineFormat) {
                        LineBlockFormat.HEADING -> {
                            addStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        LineBlockFormat.QUOTE -> {
                            addStyle(
                                SpanStyle(
                                    fontStyle = FontStyle.Italic
                                ),
                                lineStart,
                                lineEnd
                            )
                        }
                        else -> Unit
                    }
                }
                lineStart = lineEnd + 1
            }

            // Apply inline styles
            for (span in spans) {
                val s = span.start.coerceIn(0, len)
                val e = span.end.coerceIn(0, len)
                if (s < e) {
                    val style = when (span.style) {
                        InlineRichStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                        InlineRichStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                        InlineRichStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                        InlineRichStyle.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                        InlineRichStyle.HIGHLIGHT -> SpanStyle(
                            background = Color(0xFFF9D55D).copy(alpha = if (isDarkTheme) 0.35f else 0.55f)
                        )
                    }
                    addStyle(style, s, e)
                }
            }

            // Combine UNDERLINE + STRIKETHROUGH when both apply on overlapping segments
            val underlineSpans = spans.filter { it.style == InlineRichStyle.UNDERLINE }
            val strikeSpans = spans.filter { it.style == InlineRichStyle.STRIKETHROUGH }
            if (underlineSpans.isNotEmpty() && strikeSpans.isNotEmpty()) {
                for (u in underlineSpans) {
                    for (st in strikeSpans) {
                        val overlapStart = max(u.start, st.start).coerceIn(0, len)
                        val overlapEnd = min(u.end, st.end).coerceIn(0, len)
                        if (overlapStart < overlapEnd) {
                            addStyle(
                                SpanStyle(
                                    textDecoration = TextDecoration.combine(
                                        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                                    )
                                ),
                                overlapStart,
                                overlapEnd
                            )
                        }
                    }
                }
            }

            // Apply custom font sizes
            for (sizeSpan in fontSizeSpans) {
                val s = sizeSpan.start.coerceIn(0, len)
                val e = sizeSpan.end.coerceIn(0, len)
                if (s < e && sizeSpan.sizeSp != DEFAULT_EDITOR_FONT_SIZE) {
                    addStyle(SpanStyle(fontSize = sizeSpan.sizeSp.sp), s, e)
                }
            }

            // Apply custom font colors
            for (colorSpan in fontColorSpans) {
                val s = colorSpan.start.coerceIn(0, len)
                val e = colorSpan.end.coerceIn(0, len)
                val targetColor = colorSpan.colorToken.hexColor
                if (s < e && targetColor != null) {
                    addStyle(SpanStyle(color = targetColor), s, e)
                }
            }

            // Apply links
            for (link in links) {
                val s = link.start.coerceIn(0, len)
                val e = link.end.coerceIn(0, len)
                if (s < e) {
                    addStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold
                        ),
                        s,
                        e
                    )
                    addStringAnnotation("URL", link.url, s, e)
                }
            }
        }
    }

    fun toSerializedMarkdown(): String {
        return RichTextSerializer.serialize(
            text = textFieldValue.text,
            spans = spans,
            fontSizeSpans = fontSizeSpans,
            fontColorSpans = fontColorSpans,
            links = links,
            lineAlignments = lineAlignments
        )
    }

    companion object {
        fun fromStoredContent(rawContent: String): RichTextEditorState {
            val decoded = RichTextSerializer.deserialize(rawContent)
            val activeStyles = decoded.spans
                .filter { decoded.plainText.isNotEmpty() && it.end == decoded.plainText.length }
                .mapTo(mutableSetOf()) { it.style }
            val activeSize = decoded.fontSizeSpans
                .lastOrNull { decoded.plainText.isNotEmpty() && it.end == decoded.plainText.length }
                ?.sizeSp ?: DEFAULT_EDITOR_FONT_SIZE
            val activeColor = decoded.fontColorSpans
                .lastOrNull { decoded.plainText.isNotEmpty() && it.end == decoded.plainText.length }
                ?.colorToken ?: RichTextColorToken.DEFAULT
            return RichTextEditorState(
                textFieldValue = TextFieldValue(
                    text = decoded.plainText,
                    selection = TextRange(decoded.plainText.length)
                ),
                spans = decoded.spans,
                fontSizeSpans = decoded.fontSizeSpans,
                fontColorSpans = decoded.fontColorSpans,
                links = decoded.links,
                lineAlignments = decoded.lineAlignments,
                activeTypingStyles = activeStyles,
                activeTypingFontSize = activeSize,
                activeTypingColor = activeColor,
                typingStyleExplicitlyOverridden = false
            )
        }
    }
}

object RichTextEditorController {

    private const val MAX_HISTORY = 40

    private fun pushHistory(state: RichTextEditorState): List<RichTextHistorySnapshot> {
        val snap = state.toHistorySnapshot()
        if (state.undoStack.lastOrNull() == snap) return state.undoStack
        return (state.undoStack + snap).takeLast(MAX_HISTORY)
    }

    fun undo(state: RichTextEditorState): RichTextEditorState {
        if (state.undoStack.isEmpty()) return state
        val previousSnap = state.undoStack.last()
        val remainingUndo = state.undoStack.dropLast(1)
        val updatedRedo = (state.redoStack + state.toHistorySnapshot()).takeLast(MAX_HISTORY)
        return state.copy(
            textFieldValue = previousSnap.textFieldValue,
            spans = previousSnap.spans,
            fontSizeSpans = previousSnap.fontSizeSpans,
            fontColorSpans = previousSnap.fontColorSpans,
            links = previousSnap.links,
            lineAlignments = previousSnap.lineAlignments,
            undoStack = remainingUndo,
            redoStack = updatedRedo,
            typingStyleExplicitlyOverridden = false
        )
    }

    fun redo(state: RichTextEditorState): RichTextEditorState {
        if (state.redoStack.isEmpty()) return state
        val nextSnap = state.redoStack.last()
        val remainingRedo = state.redoStack.dropLast(1)
        val updatedUndo = (state.undoStack + state.toHistorySnapshot()).takeLast(MAX_HISTORY)
        return state.copy(
            textFieldValue = nextSnap.textFieldValue,
            spans = nextSnap.spans,
            fontSizeSpans = nextSnap.fontSizeSpans,
            fontColorSpans = nextSnap.fontColorSpans,
            links = nextSnap.links,
            lineAlignments = nextSnap.lineAlignments,
            undoStack = updatedUndo,
            redoStack = remainingRedo,
            typingStyleExplicitlyOverridden = false
        )
    }

    /**
     * Handles user text edits from BasicTextField while shifting existing spans,
     * applying active typing styles to newly typed characters, and continuing or exiting
     * bullet/numbered/lettered lists automatically on Enter.
     */
    fun onValueChange(
        previous: RichTextEditorState,
        incoming: TextFieldValue
    ): RichTextEditorState {
        val oldText = previous.textFieldValue.text
        val newText = incoming.text

        // Only selection moved
        if (oldText == newText) {
            val selectionChanged = previous.textFieldValue.selection != incoming.selection
            if (!selectionChanged) return previous.copy(textFieldValue = incoming)
            val sel = incoming.selection
            val inferredStyles = if (sel.collapsed) {
                val cursor = sel.start
                previous.spans.filter { cursor > it.start && cursor <= it.end }
                    .mapTo(mutableSetOf()) { it.style }
            } else {
                val s = min(sel.start, sel.end)
                val e = max(sel.start, sel.end)
                InlineRichStyle.entries.filterTo(mutableSetOf()) { style ->
                    previous.isRangeFullyCoveredByStyle(s, e, style)
                }
            }
            val inferredSize = if (sel.collapsed) {
                val cursor = sel.start
                previous.fontSizeSpans.lastOrNull { cursor > it.start && cursor <= it.end }?.sizeSp
                    ?: DEFAULT_EDITOR_FONT_SIZE
            } else {
                val s = min(sel.start, sel.end)
                val e = max(sel.start, sel.end)
                previous.fontSizeSpans.firstOrNull { it.start <= s && it.end >= e }?.sizeSp
                    ?: DEFAULT_EDITOR_FONT_SIZE
            }
            val inferredColor = if (sel.collapsed) {
                val cursor = sel.start
                previous.fontColorSpans.lastOrNull { cursor > it.start && cursor <= it.end }?.colorToken
                    ?: RichTextColorToken.DEFAULT
            } else {
                val s = min(sel.start, sel.end)
                val e = max(sel.start, sel.end)
                previous.fontColorSpans.firstOrNull { it.start <= s && it.end >= e }?.colorToken
                    ?: RichTextColorToken.DEFAULT
            }
            return previous.copy(
                textFieldValue = incoming,
                activeTypingStyles = inferredStyles,
                activeTypingFontSize = inferredSize,
                activeTypingColor = inferredColor,
                typingStyleExplicitlyOverridden = false
            )
        }

        val undoHistory = pushHistory(previous)

        // Determine diff prefix & suffix
        var prefixLen = 0
        val minLen = min(oldText.length, newText.length)
        while (prefixLen < minLen && oldText[prefixLen] == newText[prefixLen]) {
            prefixLen++
        }

        var oldSuffixIdx = oldText.length
        var newSuffixIdx = newText.length
        while (
            oldSuffixIdx > prefixLen &&
            newSuffixIdx > prefixLen &&
            oldText[oldSuffixIdx - 1] == newText[newSuffixIdx - 1]
        ) {
            oldSuffixIdx--
            newSuffixIdx--
        }

        val removedLen = oldSuffixIdx - prefixLen
        val insertedLen = newSuffixIdx - prefixLen

        val effectiveStyles = previous.effectiveActiveStyles()
        val effectiveSize = previous.effectiveFontSize()
        val effectiveColor = previous.effectiveFontColor()

        var updatedSpans = adjustSpansOnEdit(
            spans = previous.spans,
            changeStart = prefixLen,
            removedLength = removedLen,
            insertedLength = insertedLen,
            activeStyles = effectiveStyles
        )
        var updatedFontSizeSpans = adjustFontSizeSpansOnEdit(
            spans = previous.fontSizeSpans,
            changeStart = prefixLen,
            removedLength = removedLen,
            insertedLength = insertedLen,
            activeSizeSp = effectiveSize
        )
        var updatedFontColorSpans = adjustFontColorSpansOnEdit(
            spans = previous.fontColorSpans,
            changeStart = prefixLen,
            removedLength = removedLen,
            insertedLength = insertedLen,
            activeColor = effectiveColor
        )
        var updatedLinks = adjustLinksOnEdit(
            links = previous.links,
            changeStart = prefixLen,
            removedLength = removedLen,
            insertedLength = insertedLen
        )

        var finalValue = incoming

        // Check if user just pressed Enter at the end of a bullet, numbered, or lettered list line
        if (insertedLen == 1 && removedLen == 0 && newText[prefixLen] == '\n') {
            val prevLineStart = if (prefixLen == 0) {
                0
            } else {
                newText.lastIndexOf('\n', prefixLen - 1).let { if (it == -1) 0 else it + 1 }
            }
            val prevLine = newText.substring(prevLineStart, prefixLen)
            val bulletPrefix = when {
                prevLine.startsWith("• ") -> "• "
                prevLine.startsWith("- ") -> "• "
                else -> null
            }
            val numberedMatch = NUMBERED_LINE_REGEX.find(prevLine)
            val letteredMatch = LETTERED_LINE_REGEX.find(prevLine)

            if (bulletPrefix != null) {
                val prevLineContent = prevLine.removePrefix("• ").removePrefix("- ").trim()
                if (prevLineContent.isNotEmpty()) {
                    val insertion = bulletPrefix
                    val modifiedText = newText.substring(0, prefixLen + 1) +
                        insertion +
                        newText.substring(prefixLen + 1)
                    val newCursor = (incoming.selection.start + insertion.length)
                        .coerceIn(0, modifiedText.length)
                    finalValue = incoming.copy(
                        text = modifiedText,
                        selection = TextRange(newCursor)
                    )
                    updatedSpans = adjustSpansOnEdit(updatedSpans, prefixLen + 1, 0, insertion.length, emptySet())
                    updatedFontSizeSpans = adjustFontSizeSpansOnEdit(updatedFontSizeSpans, prefixLen + 1, 0, insertion.length, DEFAULT_EDITOR_FONT_SIZE)
                    updatedFontColorSpans = adjustFontColorSpansOnEdit(updatedFontColorSpans, prefixLen + 1, 0, insertion.length, RichTextColorToken.DEFAULT)
                    updatedLinks = adjustLinksOnEdit(updatedLinks, prefixLen + 1, 0, insertion.length)
                } else {
                    // Empty bullet item + Enter -> exit list by removing empty prefix and newline
                    val removeCount = prevLine.length + 1
                    val modifiedText = newText.substring(0, prevLineStart) + newText.substring(prefixLen + 1)
                    finalValue = incoming.copy(
                        text = modifiedText,
                        selection = TextRange(prevLineStart.coerceIn(0, modifiedText.length))
                    )
                    updatedSpans = adjustSpansOnEdit(updatedSpans, prevLineStart, removeCount, 0, emptySet())
                    updatedFontSizeSpans = adjustFontSizeSpansOnEdit(updatedFontSizeSpans, prevLineStart, removeCount, 0, DEFAULT_EDITOR_FONT_SIZE)
                    updatedFontColorSpans = adjustFontColorSpansOnEdit(updatedFontColorSpans, prevLineStart, removeCount, 0, RichTextColorToken.DEFAULT)
                    updatedLinks = adjustLinksOnEdit(updatedLinks, prevLineStart, removeCount, 0)
                }
            } else if (numberedMatch != null) {
                val numberVal = numberedMatch.groupValues[1].toIntOrNull() ?: 1
                val prevLineContent = numberedMatch.groupValues[2].trim()
                if (prevLineContent.isNotEmpty()) {
                    val insertion = "${numberVal + 1}. "
                    val modifiedText = newText.substring(0, prefixLen + 1) +
                        insertion +
                        newText.substring(prefixLen + 1)
                    val newCursor = (incoming.selection.start + insertion.length)
                        .coerceIn(0, modifiedText.length)
                    finalValue = incoming.copy(
                        text = modifiedText,
                        selection = TextRange(newCursor)
                    )
                    updatedSpans = adjustSpansOnEdit(updatedSpans, prefixLen + 1, 0, insertion.length, emptySet())
                    updatedFontSizeSpans = adjustFontSizeSpansOnEdit(updatedFontSizeSpans, prefixLen + 1, 0, insertion.length, DEFAULT_EDITOR_FONT_SIZE)
                    updatedFontColorSpans = adjustFontColorSpansOnEdit(updatedFontColorSpans, prefixLen + 1, 0, insertion.length, RichTextColorToken.DEFAULT)
                    updatedLinks = adjustLinksOnEdit(updatedLinks, prefixLen + 1, 0, insertion.length)
                } else {
                    val removeCount = prevLine.length + 1
                    val modifiedText = newText.substring(0, prevLineStart) + newText.substring(prefixLen + 1)
                    finalValue = incoming.copy(
                        text = modifiedText,
                        selection = TextRange(prevLineStart.coerceIn(0, modifiedText.length))
                    )
                    updatedSpans = adjustSpansOnEdit(updatedSpans, prevLineStart, removeCount, 0, emptySet())
                    updatedFontSizeSpans = adjustFontSizeSpansOnEdit(updatedFontSizeSpans, prevLineStart, removeCount, 0, DEFAULT_EDITOR_FONT_SIZE)
                    updatedFontColorSpans = adjustFontColorSpansOnEdit(updatedFontColorSpans, prevLineStart, removeCount, 0, RichTextColorToken.DEFAULT)
                    updatedLinks = adjustLinksOnEdit(updatedLinks, prevLineStart, removeCount, 0)
                }
            } else if (letteredMatch != null) {
                val letterChar = letteredMatch.groupValues[1].firstOrNull()?.lowercaseChar() ?: 'a'
                val prevLineContent = letteredMatch.groupValues[2].trim()
                if (prevLineContent.isNotEmpty()) {
                    val nextChar = if (letterChar < 'z') (letterChar + 1) else 'a'
                    val insertion = "$nextChar. "
                    val modifiedText = newText.substring(0, prefixLen + 1) +
                        insertion +
                        newText.substring(prefixLen + 1)
                    val newCursor = (incoming.selection.start + insertion.length)
                        .coerceIn(0, modifiedText.length)
                    finalValue = incoming.copy(
                        text = modifiedText,
                        selection = TextRange(newCursor)
                    )
                    updatedSpans = adjustSpansOnEdit(updatedSpans, prefixLen + 1, 0, insertion.length, emptySet())
                    updatedFontSizeSpans = adjustFontSizeSpansOnEdit(updatedFontSizeSpans, prefixLen + 1, 0, insertion.length, DEFAULT_EDITOR_FONT_SIZE)
                    updatedFontColorSpans = adjustFontColorSpansOnEdit(updatedFontColorSpans, prefixLen + 1, 0, insertion.length, RichTextColorToken.DEFAULT)
                    updatedLinks = adjustLinksOnEdit(updatedLinks, prefixLen + 1, 0, insertion.length)
                } else {
                    val removeCount = prevLine.length + 1
                    val modifiedText = newText.substring(0, prevLineStart) + newText.substring(prefixLen + 1)
                    finalValue = incoming.copy(
                        text = modifiedText,
                        selection = TextRange(prevLineStart.coerceIn(0, modifiedText.length))
                    )
                    updatedSpans = adjustSpansOnEdit(updatedSpans, prevLineStart, removeCount, 0, emptySet())
                    updatedFontSizeSpans = adjustFontSizeSpansOnEdit(updatedFontSizeSpans, prevLineStart, removeCount, 0, DEFAULT_EDITOR_FONT_SIZE)
                    updatedFontColorSpans = adjustFontColorSpansOnEdit(updatedFontColorSpans, prevLineStart, removeCount, 0, RichTextColorToken.DEFAULT)
                    updatedLinks = adjustLinksOnEdit(updatedLinks, prevLineStart, removeCount, 0)
                }
            }
        }

        val finalLen = finalValue.text.length
        val updatedLineAlignments = adjustLineAlignmentsForTextChange(
            previousAlignments = previous.lineAlignments,
            oldText = oldText,
            newText = finalValue.text
        )
        return previous.copy(
            textFieldValue = finalValue,
            spans = normalizeSpans(updatedSpans, finalLen),
            fontSizeSpans = normalizeFontSizeSpans(updatedFontSizeSpans, finalLen),
            fontColorSpans = normalizeFontColorSpans(updatedFontColorSpans, finalLen),
            links = normalizeLinks(updatedLinks, finalLen),
            lineAlignments = updatedLineAlignments,
            activeTypingStyles = effectiveStyles,
            activeTypingFontSize = effectiveSize,
            activeTypingColor = effectiveColor,
            typingStyleExplicitlyOverridden = true,
            undoStack = undoHistory,
            redoStack = emptyList()
        )
    }

    /**
     * Toggles Bold, Italic, Underline, Strikethrough, or Highlight on the selected range
     * (preserving selection!) or toggles active typing mode when no text is selected.
     */
    fun toggleInlineStyle(
        state: RichTextEditorState,
        style: InlineRichStyle
    ): RichTextEditorState {
        val sel = state.textFieldValue.selection
        if (sel.collapsed) {
            val current = state.effectiveActiveStyles().toMutableSet()
            if (!current.add(style)) {
                current.remove(style)
            }
            return state.copy(
                activeTypingStyles = current,
                typingStyleExplicitlyOverridden = true
            )
        }

        val start = min(sel.start, sel.end).coerceIn(0, state.plainText.length)
        val end = max(sel.start, sel.end).coerceIn(0, state.plainText.length)
        if (start >= end) return state

        val undoHistory = pushHistory(state)
        val isAlreadyFormatted = state.isRangeFullyCoveredByStyle(start, end, style)
        val updatedSpans = if (isAlreadyFormatted) {
            removeStyleFromRange(state.spans, start, end, style)
        } else {
            addStyleToRange(state.spans, start, end, style)
        }

        val normalized = normalizeSpans(updatedSpans, state.plainText.length)
        val updatedActive = state.effectiveActiveStyles().toMutableSet().apply {
            if (isAlreadyFormatted) remove(style) else add(style)
        }

        return state.copy(
            textFieldValue = state.textFieldValue,
            spans = normalized,
            activeTypingStyles = updatedActive,
            typingStyleExplicitlyOverridden = true,
            undoStack = undoHistory,
            redoStack = emptyList()
        )
    }

    /**
     * Applies a font size (12, 14, 16, 18, 20, 24) to the selected range or active typing state.
     */
    fun applyFontSize(
        state: RichTextEditorState,
        sizeSp: Int
    ): RichTextEditorState {
        val coercedSize = if (sizeSp in SUPPORTED_EDITOR_FONT_SIZES) sizeSp else DEFAULT_EDITOR_FONT_SIZE
        val sel = state.textFieldValue.selection
        if (sel.collapsed) {
            return state.copy(
                activeTypingFontSize = coercedSize,
                typingStyleExplicitlyOverridden = true
            )
        }

        val start = min(sel.start, sel.end).coerceIn(0, state.plainText.length)
        val end = max(sel.start, sel.end).coerceIn(0, state.plainText.length)
        if (start >= end) return state

        val undoHistory = pushHistory(state)
        val cleared = removeFontSizeFromRange(state.fontSizeSpans, start, end)
        val updated = if (coercedSize == DEFAULT_EDITOR_FONT_SIZE) {
            cleared
        } else {
            cleared + FontSizeSpanRange(start = start, end = end, sizeSp = coercedSize)
        }

        return state.copy(
            fontSizeSpans = normalizeFontSizeSpans(updated, state.plainText.length),
            activeTypingFontSize = coercedSize,
            typingStyleExplicitlyOverridden = true,
            undoStack = undoHistory,
            redoStack = emptyList()
        )
    }

    /**
     * Applies a theme-aware font color token to the selected range or active typing state.
     */
    fun applyFontColor(
        state: RichTextEditorState,
        colorToken: RichTextColorToken
    ): RichTextEditorState {
        val sel = state.textFieldValue.selection
        if (sel.collapsed) {
            return state.copy(
                activeTypingColor = colorToken,
                typingStyleExplicitlyOverridden = true
            )
        }

        val start = min(sel.start, sel.end).coerceIn(0, state.plainText.length)
        val end = max(sel.start, sel.end).coerceIn(0, state.plainText.length)
        if (start >= end) return state

        val undoHistory = pushHistory(state)
        val cleared = removeFontColorFromRange(state.fontColorSpans, start, end)
        val updated = if (colorToken == RichTextColorToken.DEFAULT) {
            cleared
        } else {
            cleared + FontColorSpanRange(start = start, end = end, colorToken = colorToken)
        }

        return state.copy(
            fontColorSpans = normalizeFontColorSpans(updated, state.plainText.length),
            activeTypingColor = colorToken,
            typingStyleExplicitlyOverridden = true,
            undoStack = undoHistory,
            redoStack = emptyList()
        )
    }

    /**
     * Sets paragraph alignment (LEFT, CENTER, RIGHT) on the current line or selected lines.
     */
    fun applyParagraphAlignment(
        state: RichTextEditorState,
        alignment: ParagraphAlignment
    ): RichTextEditorState {
        val undoHistory = pushHistory(state)
        val text = state.textFieldValue.text
        val sel = state.textFieldValue.selection
        val selStart = min(sel.start, sel.end).coerceIn(0, text.length)
        val selEnd = max(sel.start, sel.end).coerceIn(0, text.length)

        var startLine = 0
        for (i in 0 until selStart) {
            if (text[i] == '\n') startLine++
        }
        var endLine = startLine
        for (i in selStart until selEnd) {
            if (text[i] == '\n') endLine++
        }

        val updatedMap = state.lineAlignments.toMutableMap()
        for (lineIdx in startLine..endLine) {
            if (alignment == ParagraphAlignment.LEFT) {
                updatedMap.remove(lineIdx)
            } else {
                updatedMap[lineIdx] = alignment
            }
        }
        return state.copy(
            lineAlignments = updatedMap,
            undoStack = undoHistory,
            redoStack = emptyList()
        )
    }

    /**
     * Toggles Heading, Bullet list, Numbered list, Lettered list, or Quote on the current line or
     * selected lines while preserving cursor/selection offsets and existing inline spans.
     */
    fun toggleLineFormat(
        state: RichTextEditorState,
        targetFormat: LineBlockFormat
    ): RichTextEditorState {
        val undoHistory = pushHistory(state)
        val text = state.textFieldValue.text
        val sel = state.textFieldValue.selection
        val selStart = min(sel.start, sel.end).coerceIn(0, text.length)
        val selEnd = max(sel.start, sel.end).coerceIn(0, text.length)

        val firstLineStart = text.lastIndexOf('\n', (selStart - 1).coerceAtLeast(0))
            .let { if (it == -1) 0 else it + 1 }
        val lastLineEnd = text.indexOf('\n', selEnd)
            .let { if (it == -1) text.length else it }

        val blockSubstring = text.substring(firstLineStart, lastLineEnd)
        val lines = blockSubstring.split('\n')

        val allAlreadyMatch = lines.all { line ->
            detectLineFormat(line).first == targetFormat
        }

        var currentOffsetInText = firstLineStart
        var updatedText = text
        var updatedSpans = state.spans
        var updatedFontSizeSpans = state.fontSizeSpans
        var updatedFontColorSpans = state.fontColorSpans
        var updatedLinks = state.links
        var newSelStart = selStart
        var newSelEnd = selEnd

        lines.forEachIndexed { index, rawLine ->
            val (_, existingPrefixLen) = detectLineFormat(rawLine)
            val newPrefix = if (allAlreadyMatch) {
                ""
            } else {
                when (targetFormat) {
                    LineBlockFormat.HEADING -> "## "
                    LineBlockFormat.BULLET_LIST -> "• "
                    LineBlockFormat.NUMBERED_LIST -> "${index + 1}. "
                    LineBlockFormat.LETTERED_LIST -> {
                        val letter = ('a' + (index % 26))
                        "$letter. "
                    }
                    LineBlockFormat.QUOTE -> "❝ "
                    LineBlockFormat.PARAGRAPH -> ""
                }
            }

            updatedText = updatedText.substring(0, currentOffsetInText) +
                newPrefix +
                updatedText.substring(currentOffsetInText + existingPrefixLen)

            updatedSpans = adjustSpansOnEdit(
                spans = updatedSpans,
                changeStart = currentOffsetInText,
                removedLength = existingPrefixLen,
                insertedLength = newPrefix.length,
                activeStyles = emptySet()
            )
            updatedFontSizeSpans = adjustFontSizeSpansOnEdit(
                spans = updatedFontSizeSpans,
                changeStart = currentOffsetInText,
                removedLength = existingPrefixLen,
                insertedLength = newPrefix.length,
                activeSizeSp = DEFAULT_EDITOR_FONT_SIZE
            )
            updatedFontColorSpans = adjustFontColorSpansOnEdit(
                spans = updatedFontColorSpans,
                changeStart = currentOffsetInText,
                removedLength = existingPrefixLen,
                insertedLength = newPrefix.length,
                activeColor = RichTextColorToken.DEFAULT
            )
            updatedLinks = adjustLinksOnEdit(
                links = updatedLinks,
                changeStart = currentOffsetInText,
                removedLength = existingPrefixLen,
                insertedLength = newPrefix.length
            )

            val delta = newPrefix.length - existingPrefixLen
            if (selStart >= currentOffsetInText + existingPrefixLen) {
                newSelStart = (newSelStart + delta).coerceAtLeast(currentOffsetInText + newPrefix.length)
            } else if (selStart >= currentOffsetInText) {
                newSelStart = (currentOffsetInText + newPrefix.length)
            }

            if (selEnd >= currentOffsetInText + existingPrefixLen) {
                newSelEnd = (newSelEnd + delta).coerceAtLeast(currentOffsetInText + newPrefix.length)
            } else if (selEnd >= currentOffsetInText) {
                newSelEnd = (currentOffsetInText + newPrefix.length)
            }

            val newLineLen = rawLine.length - existingPrefixLen + newPrefix.length
            currentOffsetInText += newLineLen + 1
        }

        val finalSelection = if (sel.collapsed) {
            TextRange(newSelEnd.coerceIn(0, updatedText.length))
        } else {
            TextRange(
                newSelStart.coerceIn(0, updatedText.length),
                newSelEnd.coerceIn(0, updatedText.length)
            )
        }

        val finalLen = updatedText.length
        return state.copy(
            textFieldValue = state.textFieldValue.copy(
                text = updatedText,
                selection = finalSelection
            ),
            spans = normalizeSpans(updatedSpans, finalLen),
            fontSizeSpans = normalizeFontSizeSpans(updatedFontSizeSpans, finalLen),
            fontColorSpans = normalizeFontColorSpans(updatedFontColorSpans, finalLen),
            links = normalizeLinks(updatedLinks, finalLen),
            undoStack = undoHistory,
            redoStack = emptyList()
        )
    }

    /**
     * Inserts or applies a hyperlink at the current selection or cursor.
     */
    fun applyOrInsertLink(
        state: RichTextEditorState,
        labelInput: String,
        urlInput: String
    ): RichTextEditorState {
        val cleanUrl = urlInput.trim().let {
            if (it.isNotEmpty() && !it.startsWith("http://") && !it.startsWith("https://") && !it.startsWith("mailto:")) {
                "https://$it"
            } else {
                it
            }
        }
        if (cleanUrl.isBlank()) return state

        val undoHistory = pushHistory(state)
        val text = state.textFieldValue.text
        val sel = state.textFieldValue.selection
        val selStart = min(sel.start, sel.end).coerceIn(0, text.length)
        val selEnd = max(sel.start, sel.end).coerceIn(0, text.length)

        val selectedText = if (selStart < selEnd) text.substring(selStart, selEnd) else ""
        val effectiveLabel = labelInput.trim().ifBlank {
            selectedText.ifBlank { cleanUrl.removePrefix("https://").removePrefix("http://") }
        }

        val newText = text.substring(0, selStart) + effectiveLabel + text.substring(selEnd)
        val removedLen = selEnd - selStart
        val insertedLen = effectiveLabel.length

        val updatedSpans = adjustSpansOnEdit(
            spans = state.spans,
            changeStart = selStart,
            removedLength = removedLen,
            insertedLength = insertedLen,
            activeStyles = state.effectiveActiveStyles()
        )
        val updatedFontSizeSpans = adjustFontSizeSpansOnEdit(
            spans = state.fontSizeSpans,
            changeStart = selStart,
            removedLength = removedLen,
            insertedLength = insertedLen,
            activeSizeSp = state.effectiveFontSize()
        )
        val updatedFontColorSpans = adjustFontColorSpansOnEdit(
            spans = state.fontColorSpans,
            changeStart = selStart,
            removedLength = removedLen,
            insertedLength = insertedLen,
            activeColor = state.effectiveFontColor()
        )
        val shiftedLinks = adjustLinksOnEdit(
            links = state.links.filterNot { it.end > selStart && it.start < selEnd },
            changeStart = selStart,
            removedLength = removedLen,
            insertedLength = insertedLen
        ) + LinkSpanRange(
            start = selStart,
            end = selStart + insertedLen,
            url = cleanUrl
        )

        val newCursor = (selStart + insertedLen).coerceIn(0, newText.length)
        val updatedLineAlignments = adjustLineAlignmentsForTextChange(
            previousAlignments = state.lineAlignments,
            oldText = text,
            newText = newText
        )
        return state.copy(
            textFieldValue = TextFieldValue(
                text = newText,
                selection = TextRange(newCursor)
            ),
            spans = normalizeSpans(updatedSpans, newText.length),
            fontSizeSpans = normalizeFontSizeSpans(updatedFontSizeSpans, newText.length),
            fontColorSpans = normalizeFontColorSpans(updatedFontColorSpans, newText.length),
            links = normalizeLinks(shiftedLinks, newText.length),
            lineAlignments = updatedLineAlignments,
            undoStack = undoHistory,
            redoStack = emptyList()
        )
    }

    private fun addStyleToRange(
        spans: List<InlineSpanRange>,
        start: Int,
        end: Int,
        style: InlineRichStyle
    ): List<InlineSpanRange> {
        return spans + InlineSpanRange(start = start, end = end, style = style)
    }

    private fun removeStyleFromRange(
        spans: List<InlineSpanRange>,
        start: Int,
        end: Int,
        style: InlineRichStyle
    ): List<InlineSpanRange> {
        val result = mutableListOf<InlineSpanRange>()
        for (span in spans) {
            if (span.style != style || span.end <= start || span.start >= end) {
                result.add(span)
            } else {
                if (span.start < start) {
                    result.add(span.copy(end = start))
                }
                if (span.end > end) {
                    result.add(span.copy(start = end))
                }
            }
        }
        return result
    }

    private fun removeFontSizeFromRange(
        spans: List<FontSizeSpanRange>,
        start: Int,
        end: Int
    ): List<FontSizeSpanRange> {
        val result = mutableListOf<FontSizeSpanRange>()
        for (span in spans) {
            if (span.end <= start || span.start >= end) {
                result.add(span)
            } else {
                if (span.start < start) result.add(span.copy(end = start))
                if (span.end > end) result.add(span.copy(start = end))
            }
        }
        return result
    }

    private fun removeFontColorFromRange(
        spans: List<FontColorSpanRange>,
        start: Int,
        end: Int
    ): List<FontColorSpanRange> {
        val result = mutableListOf<FontColorSpanRange>()
        for (span in spans) {
            if (span.end <= start || span.start >= end) {
                result.add(span)
            } else {
                if (span.start < start) result.add(span.copy(end = start))
                if (span.end > end) result.add(span.copy(start = end))
            }
        }
        return result
    }

    private fun adjustSpansOnEdit(
        spans: List<InlineSpanRange>,
        changeStart: Int,
        removedLength: Int,
        insertedLength: Int,
        activeStyles: Set<InlineRichStyle>
    ): List<InlineSpanRange> {
        val changeEndOld = changeStart + removedLength
        val delta = insertedLength - removedLength
        val result = mutableListOf<InlineSpanRange>()

        for (span in spans) {
            when {
                span.end <= changeStart -> result.add(span)
                span.start >= changeEndOld -> result.add(
                    span.copy(start = span.start + delta, end = span.end + delta)
                )
                else -> {
                    val newStart = if (span.start < changeStart) span.start else (changeStart + insertedLength)
                    val newEnd = if (span.end > changeEndOld) (span.end + delta) else changeStart
                    if (newStart < newEnd) {
                        result.add(span.copy(start = newStart, end = newEnd))
                    }
                }
            }
        }

        if (insertedLength > 0) {
            for (style in activeStyles) {
                result.add(
                    InlineSpanRange(
                        start = changeStart,
                        end = changeStart + insertedLength,
                        style = style
                    )
                )
            }
        }

        return result
    }

    private fun adjustFontSizeSpansOnEdit(
        spans: List<FontSizeSpanRange>,
        changeStart: Int,
        removedLength: Int,
        insertedLength: Int,
        activeSizeSp: Int
    ): List<FontSizeSpanRange> {
        val changeEndOld = changeStart + removedLength
        val delta = insertedLength - removedLength
        val result = mutableListOf<FontSizeSpanRange>()

        for (span in spans) {
            when {
                span.end <= changeStart -> result.add(span)
                span.start >= changeEndOld -> result.add(
                    span.copy(start = span.start + delta, end = span.end + delta)
                )
                else -> {
                    val newStart = if (span.start < changeStart) span.start else (changeStart + insertedLength)
                    val newEnd = if (span.end > changeEndOld) (span.end + delta) else changeStart
                    if (newStart < newEnd) {
                        result.add(span.copy(start = newStart, end = newEnd))
                    }
                }
            }
        }

        if (insertedLength > 0 && activeSizeSp != DEFAULT_EDITOR_FONT_SIZE) {
            result.add(
                FontSizeSpanRange(
                    start = changeStart,
                    end = changeStart + insertedLength,
                    sizeSp = activeSizeSp
                )
            )
        }
        return result
    }

    private fun adjustFontColorSpansOnEdit(
        spans: List<FontColorSpanRange>,
        changeStart: Int,
        removedLength: Int,
        insertedLength: Int,
        activeColor: RichTextColorToken
    ): List<FontColorSpanRange> {
        val changeEndOld = changeStart + removedLength
        val delta = insertedLength - removedLength
        val result = mutableListOf<FontColorSpanRange>()

        for (span in spans) {
            when {
                span.end <= changeStart -> result.add(span)
                span.start >= changeEndOld -> result.add(
                    span.copy(start = span.start + delta, end = span.end + delta)
                )
                else -> {
                    val newStart = if (span.start < changeStart) span.start else (changeStart + insertedLength)
                    val newEnd = if (span.end > changeEndOld) (span.end + delta) else changeStart
                    if (newStart < newEnd) {
                        result.add(span.copy(start = newStart, end = newEnd))
                    }
                }
            }
        }

        if (insertedLength > 0 && activeColor != RichTextColorToken.DEFAULT) {
            result.add(
                FontColorSpanRange(
                    start = changeStart,
                    end = changeStart + insertedLength,
                    colorToken = activeColor
                )
            )
        }
        return result
    }

    private fun adjustLinksOnEdit(
        links: List<LinkSpanRange>,
        changeStart: Int,
        removedLength: Int,
        insertedLength: Int
    ): List<LinkSpanRange> {
        val changeEndOld = changeStart + removedLength
        val delta = insertedLength - removedLength
        val result = mutableListOf<LinkSpanRange>()

        for (link in links) {
            when {
                link.end <= changeStart -> result.add(link)
                link.start >= changeEndOld -> result.add(
                    link.copy(start = link.start + delta, end = link.end + delta)
                )
                else -> {
                    val newStart = min(link.start, changeStart)
                    val newEnd = max(changeStart + insertedLength, link.end + delta)
                    if (newStart < newEnd) {
                        result.add(link.copy(start = newStart, end = newEnd))
                    }
                }
            }
        }
        return result
    }

    fun normalizeSpans(spans: List<InlineSpanRange>, textLength: Int): List<InlineSpanRange> {
        if (spans.isEmpty() || textLength <= 0) return emptyList()
        val result = mutableListOf<InlineSpanRange>()
        for (style in InlineRichStyle.entries) {
            val styleSpans = spans
                .filter { it.style == style }
                .mapNotNull {
                    val s = it.start.coerceIn(0, textLength)
                    val e = it.end.coerceIn(0, textLength)
                    if (s < e) InlineSpanRange(s, e, style) else null
                }
                .sortedWith(compareBy<InlineSpanRange> { it.start }.thenBy { it.end })

            for (span in styleSpans) {
                val last = result.lastOrNull { it.style == style }
                if (last != null && span.start <= last.end) {
                    val lastIdx = result.lastIndexOf(last)
                    result[lastIdx] = last.copy(end = max(last.end, span.end))
                } else {
                    result.add(span)
                }
            }
        }
        return result
    }

    fun normalizeFontSizeSpans(spans: List<FontSizeSpanRange>, textLength: Int): List<FontSizeSpanRange> {
        if (spans.isEmpty() || textLength <= 0) return emptyList()
        val cleaned = spans.mapNotNull {
            val s = it.start.coerceIn(0, textLength)
            val e = it.end.coerceIn(0, textLength)
            if (s < e && it.sizeSp != DEFAULT_EDITOR_FONT_SIZE) FontSizeSpanRange(s, e, it.sizeSp) else null
        }.sortedWith(compareBy<FontSizeSpanRange> { it.start }.thenBy { it.end })

        val result = mutableListOf<FontSizeSpanRange>()
        for (span in cleaned) {
            val last = result.lastOrNull()
            if (last != null && last.sizeSp == span.sizeSp && span.start <= last.end) {
                result[result.lastIndex] = last.copy(end = max(last.end, span.end))
            } else {
                result.add(span)
            }
        }
        return result
    }

    fun normalizeFontColorSpans(spans: List<FontColorSpanRange>, textLength: Int): List<FontColorSpanRange> {
        if (spans.isEmpty() || textLength <= 0) return emptyList()
        val cleaned = spans.mapNotNull {
            val s = it.start.coerceIn(0, textLength)
            val e = it.end.coerceIn(0, textLength)
            if (s < e && it.colorToken != RichTextColorToken.DEFAULT) FontColorSpanRange(s, e, it.colorToken) else null
        }.sortedWith(compareBy<FontColorSpanRange> { it.start }.thenBy { it.end })

        val result = mutableListOf<FontColorSpanRange>()
        for (span in cleaned) {
            val last = result.lastOrNull()
            if (last != null && last.colorToken == span.colorToken && span.start <= last.end) {
                result[result.lastIndex] = last.copy(end = max(last.end, span.end))
            } else {
                result.add(span)
            }
        }
        return result
    }

    fun normalizeLinks(links: List<LinkSpanRange>, textLength: Int): List<LinkSpanRange> {
        if (links.isEmpty() || textLength <= 0) return emptyList()
        return links.mapNotNull {
            val s = it.start.coerceIn(0, textLength)
            val e = it.end.coerceIn(0, textLength)
            if (s < e && it.url.isNotBlank()) LinkSpanRange(s, e, it.url) else null
        }.sortedBy { it.start }
    }

    internal fun adjustLineAlignmentsForTextChange(
        previousAlignments: Map<Int, ParagraphAlignment>,
        oldText: String,
        newText: String
    ): Map<Int, ParagraphAlignment> {
        if (previousAlignments.isEmpty() || oldText == newText) return previousAlignments

        var prefixLen = 0
        val minLen = min(oldText.length, newText.length)
        while (prefixLen < minLen && oldText[prefixLen] == newText[prefixLen]) {
            prefixLen++
        }

        var oldSuffixIdx = oldText.length
        var newSuffixIdx = newText.length
        while (
            oldSuffixIdx > prefixLen &&
            newSuffixIdx > prefixLen &&
            oldText[oldSuffixIdx - 1] == newText[newSuffixIdx - 1]
        ) {
            oldSuffixIdx--
            newSuffixIdx--
        }

        var changeStartLine = 0
        for (i in 0 until prefixLen) {
            if (oldText[i] == '\n') changeStartLine++
        }
        var removedNewlines = 0
        for (i in prefixLen until oldSuffixIdx) {
            if (oldText[i] == '\n') removedNewlines++
        }
        var insertedNewlines = 0
        for (i in prefixLen until newSuffixIdx) {
            if (newText[i] == '\n') insertedNewlines++
        }

        val lineDelta = insertedNewlines - removedNewlines
        val totalNewLines = newText.count { it == '\n' } + 1
        val updated = mutableMapOf<Int, ParagraphAlignment>()

        for ((lineIdx, alignment) in previousAlignments) {
            if (alignment == ParagraphAlignment.LEFT) continue
            when {
                lineIdx < changeStartLine -> {
                    if (lineIdx in 0 until totalNewLines) {
                        updated[lineIdx] = alignment
                    }
                }
                lineIdx == changeStartLine -> {
                    if (changeStartLine in 0 until totalNewLines) {
                        updated[changeStartLine] = alignment
                    }
                    if (insertedNewlines > 0) {
                        for (offset in 1..insertedNewlines) {
                            val targetLine = changeStartLine + offset
                            if (targetLine in 0 until totalNewLines) {
                                updated[targetLine] = alignment
                            }
                        }
                    }
                }
                lineIdx > changeStartLine + removedNewlines -> {
                    val shifted = lineIdx + lineDelta
                    if (shifted in 0 until totalNewLines) {
                        updated[shifted] = alignment
                    }
                }
            }
        }
        return updated
    }
}

private val NUMBERED_LINE_REGEX = Regex("""^(\d+)\. (.*)$""")
private val LETTERED_LINE_REGEX = Regex("""^([a-zA-Z])\. (.*)$""")
private val HEADING_LINE_REGEX = Regex("""^(#{1,3}) (.*)$""")
private val ALIGN_LINE_PREFIX_REGEX = Regex("""^\{align:(center|right)\}""", RegexOption.IGNORE_CASE)

internal fun detectLineFormat(line: String): Pair<LineBlockFormat, Int> {
    return when {
        line.startsWith("### ") -> LineBlockFormat.HEADING to 4
        line.startsWith("## ") -> LineBlockFormat.HEADING to 3
        line.startsWith("# ") -> LineBlockFormat.HEADING to 2
        line.startsWith("• ") -> LineBlockFormat.BULLET_LIST to 2
        line.startsWith("❝ ") -> LineBlockFormat.QUOTE to 2
        else -> {
            val numMatch = NUMBERED_LINE_REGEX.find(line)
            if (numMatch != null) {
                val prefixLen = numMatch.groupValues[1].length + 2
                LineBlockFormat.NUMBERED_LIST to prefixLen
            } else {
                val letMatch = LETTERED_LINE_REGEX.find(line)
                if (letMatch != null) {
                    val prefixLen = letMatch.groupValues[1].length + 2
                    LineBlockFormat.LETTERED_LIST to prefixLen
                } else {
                    LineBlockFormat.PARAGRAPH to 0
                }
            }
        }
    }
}

/**
 * RichTextVisualTransformation applies live Bold, Italic, Underline, Strikethrough, Highlight,
 * Font Size, Font Color, Heading, Quote, Alignment, and Link styling directly inside BasicTextField
 * without changing character offsets (IdentityMapping).
 */
class RichTextVisualTransformation(
    private val state: RichTextEditorState,
    private val linkColor: Color,
    private val isDarkTheme: Boolean = true
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val styled = state.toAnnotatedString(linkColor = linkColor, isDarkTheme = isDarkTheme)
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

object RichTextSerializer {

    data class DecodedRichText(
        val plainText: String,
        val spans: List<InlineSpanRange>,
        val fontSizeSpans: List<FontSizeSpanRange> = emptyList(),
        val fontColorSpans: List<FontColorSpanRange> = emptyList(),
        val links: List<LinkSpanRange>,
        val lineAlignments: Map<Int, ParagraphAlignment> = emptyMap()
    )

    fun toPlainText(raw: String): String {
        return deserialize(raw).plainText
    }

    fun deserialize(raw: String): DecodedRichText {
        if (raw.isEmpty()) {
            return DecodedRichText("", emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())
        }

        val normalizedLines = raw.replace("\r\n", "\n").split('\n')
        val outBuilder = StringBuilder()
        val allSpans = mutableListOf<InlineSpanRange>()
        val allSizeSpans = mutableListOf<FontSizeSpanRange>()
        val allColorSpans = mutableListOf<FontColorSpanRange>()
        val allLinks = mutableListOf<LinkSpanRange>()
        val alignments = mutableMapOf<Int, ParagraphAlignment>()

        normalizedLines.forEachIndexed { lineIdx, rawLineWithAlign ->
            if (lineIdx > 0) {
                outBuilder.append('\n')
            }

            var workingLine = rawLineWithAlign
            val alignMatch = ALIGN_LINE_PREFIX_REGEX.find(workingLine)
            if (alignMatch != null) {
                val alignType = alignMatch.groupValues[1].lowercase()
                alignments[lineIdx] = if (alignType == "center") ParagraphAlignment.CENTER else ParagraphAlignment.RIGHT
                workingLine = workingLine.substring(alignMatch.range.last + 1)
            }

            val (linePrefix, lineBody) = extractEditorLinePrefix(workingLine)
            outBuilder.append(linePrefix)
            val bodyOffset = outBuilder.length

            val parsedLine = parseInlineMarkdown(lineBody)
            outBuilder.append(parsedLine.plainText)
            for (span in parsedLine.spans) {
                allSpans.add(
                    InlineSpanRange(
                        start = bodyOffset + span.start,
                        end = bodyOffset + span.end,
                        style = span.style
                    )
                )
            }
            for (sizeSpan in parsedLine.fontSizeSpans) {
                allSizeSpans.add(
                    FontSizeSpanRange(
                        start = bodyOffset + sizeSpan.start,
                        end = bodyOffset + sizeSpan.end,
                        sizeSp = sizeSpan.sizeSp
                    )
                )
            }
            for (colorSpan in parsedLine.fontColorSpans) {
                allColorSpans.add(
                    FontColorSpanRange(
                        start = bodyOffset + colorSpan.start,
                        end = bodyOffset + colorSpan.end,
                        colorToken = colorSpan.colorToken
                    )
                )
            }
            for (link in parsedLine.links) {
                allLinks.add(
                    LinkSpanRange(
                        start = bodyOffset + link.start,
                        end = bodyOffset + link.end,
                        url = link.url
                    )
                )
            }
        }

        val fullText = outBuilder.toString()
        return DecodedRichText(
            plainText = fullText,
            spans = RichTextEditorController.normalizeSpans(allSpans, fullText.length),
            fontSizeSpans = RichTextEditorController.normalizeFontSizeSpans(allSizeSpans, fullText.length),
            fontColorSpans = RichTextEditorController.normalizeFontColorSpans(allColorSpans, fullText.length),
            links = RichTextEditorController.normalizeLinks(allLinks, fullText.length),
            lineAlignments = alignments
        )
    }

    fun serialize(
        text: String,
        spans: List<InlineSpanRange>,
        fontSizeSpans: List<FontSizeSpanRange> = emptyList(),
        fontColorSpans: List<FontColorSpanRange> = emptyList(),
        links: List<LinkSpanRange>,
        lineAlignments: Map<Int, ParagraphAlignment> = emptyMap()
    ): String {
        if (text.isEmpty()) return ""
        val normalizedSpans = RichTextEditorController.normalizeSpans(spans, text.length)
        val normalizedSizeSpans = RichTextEditorController.normalizeFontSizeSpans(fontSizeSpans, text.length)
        val normalizedColorSpans = RichTextEditorController.normalizeFontColorSpans(fontColorSpans, text.length)
        val normalizedLinks = RichTextEditorController.normalizeLinks(links, text.length)

        val sb = StringBuilder()
        val lines = text.split('\n')
        var lineStartOffset = 0

        lines.forEachIndexed { lineIdx, line ->
            if (lineIdx > 0) sb.append('\n')
            val lineEndOffset = lineStartOffset + line.length

            when (lineAlignments[lineIdx]) {
                ParagraphAlignment.CENTER -> sb.append("{align:center}")
                ParagraphAlignment.RIGHT -> sb.append("{align:right}")
                else -> Unit
            }

            val (lineFormat, prefixLen) = detectLineFormat(line)
            val canonicalPrefix = when (lineFormat) {
                LineBlockFormat.HEADING -> line.substring(0, prefixLen)
                LineBlockFormat.BULLET_LIST -> "- "
                LineBlockFormat.NUMBERED_LIST -> line.substring(0, prefixLen)
                LineBlockFormat.LETTERED_LIST -> line.substring(0, prefixLen)
                LineBlockFormat.QUOTE -> "> "
                LineBlockFormat.PARAGRAPH -> ""
            }
            sb.append(canonicalPrefix)

            val contentStart = lineStartOffset + prefixLen
            val contentEnd = lineEndOffset
            if (contentStart < contentEnd) {
                val lineSpans = normalizedSpans.mapNotNull {
                    val s = max(it.start, contentStart) - contentStart
                    val e = min(it.end, contentEnd) - contentStart
                    if (s < e) InlineSpanRange(s, e, it.style) else null
                }
                val lineSizeSpans = normalizedSizeSpans.mapNotNull {
                    val s = max(it.start, contentStart) - contentStart
                    val e = min(it.end, contentEnd) - contentStart
                    if (s < e) FontSizeSpanRange(s, e, it.sizeSp) else null
                }
                val lineColorSpans = normalizedColorSpans.mapNotNull {
                    val s = max(it.start, contentStart) - contentStart
                    val e = min(it.end, contentEnd) - contentStart
                    if (s < e) FontColorSpanRange(s, e, it.colorToken) else null
                }
                val lineLinks = normalizedLinks.mapNotNull {
                    val s = max(it.start, contentStart) - contentStart
                    val e = min(it.end, contentEnd) - contentStart
                    if (s < e) LinkSpanRange(s, e, it.url) else null
                }
                val lineBody = text.substring(contentStart, contentEnd)
                val serializedBody = serializeLineInline(
                    line = lineBody,
                    spans = lineSpans,
                    fontSizeSpans = lineSizeSpans,
                    fontColorSpans = lineColorSpans,
                    links = lineLinks
                )
                if (lineFormat == LineBlockFormat.PARAGRAPH &&
                    (serializedBody.startsWith("- ") ||
                        serializedBody.regionMatches(0, "{align:", 0, 7, ignoreCase = true))
                ) {
                    sb.append('\\').append(serializedBody)
                } else {
                    sb.append(serializedBody)
                }
            }

            lineStartOffset = lineEndOffset + 1
        }

        return sb.toString()
    }

    fun parseParagraphsForDisplay(raw: String): List<ParsedRichParagraph> {
        if (raw.isEmpty()) return emptyList()
        val lines = raw.replace("\r\n", "\n").split('\n')
        return lines.map { rawLineWithAlign ->
            var rawLine = rawLineWithAlign
            var alignment = ParagraphAlignment.LEFT
            val alignMatch = ALIGN_LINE_PREFIX_REGEX.find(rawLine)
            if (alignMatch != null) {
                alignment = if (alignMatch.groupValues[1].equals("center", ignoreCase = true)) {
                    ParagraphAlignment.CENTER
                } else {
                    ParagraphAlignment.RIGHT
                }
                rawLine = rawLine.substring(alignMatch.range.last + 1)
            }

            val headingMatch = HEADING_LINE_REGEX.find(rawLine)
            val numberedMatch = NUMBERED_LINE_REGEX.find(rawLine)
            val letteredMatch = LETTERED_LINE_REGEX.find(rawLine)

            when {
                headingMatch != null -> {
                    val level = headingMatch.groupValues[1].length.coerceIn(1, 3)
                    val content = headingMatch.groupValues[2]
                    val parsed = parseInlineMarkdown(content)
                    ParsedRichParagraph(
                        format = LineBlockFormat.HEADING,
                        alignment = alignment,
                        headingLevel = level,
                        plainText = parsed.plainText,
                        inlineSpans = parsed.spans,
                        fontSizeSpans = parsed.fontSizeSpans,
                        fontColorSpans = parsed.fontColorSpans,
                        links = parsed.links
                    )
                }
                rawLine.startsWith("• ") || rawLine.startsWith("- ") -> {
                    val content = rawLine.substring(2)
                    val parsed = parseInlineMarkdown(content)
                    ParsedRichParagraph(
                        format = LineBlockFormat.BULLET_LIST,
                        alignment = alignment,
                        plainText = parsed.plainText,
                        inlineSpans = parsed.spans,
                        fontSizeSpans = parsed.fontSizeSpans,
                        fontColorSpans = parsed.fontColorSpans,
                        links = parsed.links
                    )
                }
                numberedMatch != null -> {
                    val num = numberedMatch.groupValues[1].toIntOrNull() ?: 1
                    val content = numberedMatch.groupValues[2]
                    val parsed = parseInlineMarkdown(content)
                    ParsedRichParagraph(
                        format = LineBlockFormat.NUMBERED_LIST,
                        alignment = alignment,
                        listNumber = num,
                        plainText = parsed.plainText,
                        inlineSpans = parsed.spans,
                        fontSizeSpans = parsed.fontSizeSpans,
                        fontColorSpans = parsed.fontColorSpans,
                        links = parsed.links
                    )
                }
                letteredMatch != null -> {
                    val letter = letteredMatch.groupValues[1].firstOrNull() ?: 'a'
                    val content = letteredMatch.groupValues[2]
                    val parsed = parseInlineMarkdown(content)
                    ParsedRichParagraph(
                        format = LineBlockFormat.LETTERED_LIST,
                        alignment = alignment,
                        listLetter = letter,
                        plainText = parsed.plainText,
                        inlineSpans = parsed.spans,
                        fontSizeSpans = parsed.fontSizeSpans,
                        fontColorSpans = parsed.fontColorSpans,
                        links = parsed.links
                    )
                }
                rawLine.startsWith("> ") || rawLine.startsWith("❝ ") -> {
                    val content = rawLine.substring(2)
                    val parsed = parseInlineMarkdown(content)
                    ParsedRichParagraph(
                        format = LineBlockFormat.QUOTE,
                        alignment = alignment,
                        plainText = parsed.plainText,
                        inlineSpans = parsed.spans,
                        fontSizeSpans = parsed.fontSizeSpans,
                        fontColorSpans = parsed.fontColorSpans,
                        links = parsed.links
                    )
                }
                else -> {
                    val parsed = parseInlineMarkdown(rawLine)
                    ParsedRichParagraph(
                        format = LineBlockFormat.PARAGRAPH,
                        alignment = alignment,
                        plainText = parsed.plainText,
                        inlineSpans = parsed.spans,
                        fontSizeSpans = parsed.fontSizeSpans,
                        fontColorSpans = parsed.fontColorSpans,
                        links = parsed.links
                    )
                }
            }
        }
    }

    private fun extractEditorLinePrefix(rawLine: String): Pair<String, String> {
        val headingMatch = HEADING_LINE_REGEX.find(rawLine)
        if (headingMatch != null) {
            return "${headingMatch.groupValues[1]} " to headingMatch.groupValues[2]
        }
        if (rawLine.startsWith("• ") || rawLine.startsWith("- ")) {
            return "• " to rawLine.substring(2)
        }
        val numberedMatch = NUMBERED_LINE_REGEX.find(rawLine)
        if (numberedMatch != null) {
            val numPrefix = "${numberedMatch.groupValues[1]}. "
            return numPrefix to numberedMatch.groupValues[2]
        }
        val letteredMatch = LETTERED_LINE_REGEX.find(rawLine)
        if (letteredMatch != null) {
            val letPrefix = "${letteredMatch.groupValues[1]}. "
            return letPrefix to letteredMatch.groupValues[2]
        }
        if (rawLine.startsWith("> ") || rawLine.startsWith("❝ ")) {
            return "❝ " to rawLine.substring(2)
        }
        return "" to rawLine
    }

    private fun serializeLineInline(
        line: String,
        spans: List<InlineSpanRange>,
        fontSizeSpans: List<FontSizeSpanRange>,
        fontColorSpans: List<FontColorSpanRange>,
        links: List<LinkSpanRange>
    ): String {
        if (line.isEmpty()) return ""
        if (spans.isEmpty() && fontSizeSpans.isEmpty() && fontColorSpans.isEmpty() && links.isEmpty()) {
            return escapeUnstyledSpecialTokens(line)
        }

        val boundaries = sortedSetOf(0, line.length)
        for (s in spans) {
            boundaries.add(s.start.coerceIn(0, line.length))
            boundaries.add(s.end.coerceIn(0, line.length))
        }
        for (fs in fontSizeSpans) {
            boundaries.add(fs.start.coerceIn(0, line.length))
            boundaries.add(fs.end.coerceIn(0, line.length))
        }
        for (fc in fontColorSpans) {
            boundaries.add(fc.start.coerceIn(0, line.length))
            boundaries.add(fc.end.coerceIn(0, line.length))
        }
        for (l in links) {
            boundaries.add(l.start.coerceIn(0, line.length))
            boundaries.add(l.end.coerceIn(0, line.length))
        }

        val points = boundaries.toList()
        val out = StringBuilder()

        var i = 0
        while (i < points.size - 1) {
            val segStart = points[i]
            val segEnd = points[i + 1]
            if (segStart >= segEnd) {
                i++
                continue
            }

            val activeLink = links.firstOrNull { it.start <= segStart && it.end >= segEnd }
            if (activeLink != null && activeLink.start == segStart) {
                val linkEndIdx = points.indexOf(activeLink.end).takeIf { it > i } ?: (i + 1)
                val innerText = serializeStyledRange(
                    line = line,
                    rangeStart = segStart,
                    rangeEnd = activeLink.end,
                    spans = spans,
                    fontSizeSpans = fontSizeSpans,
                    fontColorSpans = fontColorSpans
                )
                out.append("[").append(innerText).append("](").append(activeLink.url).append(")")
                i = linkEndIdx
                continue
            }

            out.append(
                serializeSegmentWithStyles(
                    fullLine = line,
                    segmentText = line.substring(segStart, segEnd),
                    spans = spans,
                    fontSizeSpans = fontSizeSpans,
                    fontColorSpans = fontColorSpans,
                    segStart = segStart,
                    segEnd = segEnd
                )
            )
            i++
        }

        return out.toString()
    }

    private fun serializeStyledRange(
        line: String,
        rangeStart: Int,
        rangeEnd: Int,
        spans: List<InlineSpanRange>,
        fontSizeSpans: List<FontSizeSpanRange>,
        fontColorSpans: List<FontColorSpanRange>
    ): String {
        val boundaries = sortedSetOf(rangeStart, rangeEnd)
        for (s in spans) {
            if (s.end > rangeStart && s.start < rangeEnd) {
                boundaries.add(s.start.coerceIn(rangeStart, rangeEnd))
                boundaries.add(s.end.coerceIn(rangeStart, rangeEnd))
            }
        }
        for (fs in fontSizeSpans) {
            if (fs.end > rangeStart && fs.start < rangeEnd) {
                boundaries.add(fs.start.coerceIn(rangeStart, rangeEnd))
                boundaries.add(fs.end.coerceIn(rangeStart, rangeEnd))
            }
        }
        for (fc in fontColorSpans) {
            if (fc.end > rangeStart && fc.start < rangeEnd) {
                boundaries.add(fc.start.coerceIn(rangeStart, rangeEnd))
                boundaries.add(fc.end.coerceIn(rangeStart, rangeEnd))
            }
        }
        val pts = boundaries.toList()
        val sb = StringBuilder()
        for (idx in 0 until pts.size - 1) {
            val s = pts[idx]
            val e = pts[idx + 1]
            if (s < e) {
                sb.append(
                    serializeSegmentWithStyles(
                        fullLine = line,
                        segmentText = line.substring(s, e),
                        spans = spans,
                        fontSizeSpans = fontSizeSpans,
                        fontColorSpans = fontColorSpans,
                        segStart = s,
                        segEnd = e
                    )
                )
            }
        }
        return sb.toString()
    }

    private fun serializeSegmentWithStyles(
        fullLine: String,
        segmentText: String,
        spans: List<InlineSpanRange>,
        fontSizeSpans: List<FontSizeSpanRange>,
        fontColorSpans: List<FontColorSpanRange>,
        segStart: Int,
        segEnd: Int
    ): String {
        if (segmentText.isEmpty()) return ""
        val activeStyles = spans.filter { it.start <= segStart && it.end >= segEnd }
            .map { it.style }
            .toSet()
        val activeSize = fontSizeSpans.lastOrNull { it.start <= segStart && it.end >= segEnd }?.sizeSp
        val activeColor = fontColorSpans.lastOrNull { it.start <= segStart && it.end >= segEnd }?.colorToken

        val escaped = escapeUnstyledSpecialTokens(segmentText)
        var wrapped = escaped

        if (InlineRichStyle.UNDERLINE in activeStyles) {
            wrapped = "<u>$wrapped</u>"
        }
        if (InlineRichStyle.STRIKETHROUGH in activeStyles) {
            wrapped = "~~$wrapped~~"
        }
        if (InlineRichStyle.HIGHLIGHT in activeStyles) {
            wrapped = "==$wrapped=="
        }
        if (InlineRichStyle.ITALIC in activeStyles) {
            val prevChar = if (segStart > 0) fullLine[segStart - 1] else ' '
            val nextChar = if (segEnd < fullLine.length) fullLine[segEnd] else ' '
            val needsTag = segmentText.first().isWhitespace() ||
                segmentText.last().isWhitespace() ||
                prevChar.isLetterOrDigit() ||
                nextChar.isLetterOrDigit()
            wrapped = if (needsTag) "<i>$wrapped</i>" else "_${wrapped}_"
        }
        if (InlineRichStyle.BOLD in activeStyles) {
            wrapped = "**$wrapped**"
        }
        if (activeSize != null && activeSize != DEFAULT_EDITOR_FONT_SIZE) {
            wrapped = "<size=$activeSize>$wrapped</size>"
        }
        if (activeColor != null && activeColor != RichTextColorToken.DEFAULT) {
            wrapped = "<color=${activeColor.id}>$wrapped</color>"
        }
        return wrapped
    }

    private fun escapeUnstyledSpecialTokens(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (ch in text) {
            if (ch in charArrayOf('\\', '*', '_', '~', '=', '+', '[', ']', '<', '>')) {
                sb.append('\\')
            }
            sb.append(ch)
        }
        return sb.toString()
    }

    /**
     * Recursive inline parser supporting nested bold, italic, underline, strikethrough,
     * highlight, font size, font color, links, escaped characters (`\*`, `\_`, `\\`, etc.),
     * and preserving literal math/lone asterisks (`2 * 3 = 6`).
     */
    fun parseInlineMarkdown(input: String): DecodedRichText {
        val plain = StringBuilder()
        val spans = mutableListOf<InlineSpanRange>()
        val sizeSpans = mutableListOf<FontSizeSpanRange>()
        val colorSpans = mutableListOf<FontColorSpanRange>()
        val links = mutableListOf<LinkSpanRange>()

        fun parseSegment(
            text: String,
            activeStyles: Set<InlineRichStyle>,
            activeSize: Int?,
            activeColor: RichTextColorToken?
        ) {
            var idx = 0
            val len = text.length
            while (idx < len) {
                // 1. Escaped character: \\ \* \_ \~ \[ \] \< \> \= \+ \- \{ \} \# \.
                if (text[idx] == '\\' && idx + 1 < len &&
                    text[idx + 1] in charArrayOf('\\', '*', '_', '~', '[', ']', '<', '>', '=', '+', '-', '{', '}', '#', '.')
                ) {
                    val startPos = plain.length
                    plain.append(text[idx + 1])
                    val endPos = plain.length
                    for (st in activeStyles) spans.add(InlineSpanRange(startPos, endPos, st))
                    if (activeSize != null) sizeSpans.add(FontSizeSpanRange(startPos, endPos, activeSize))
                    if (activeColor != null) colorSpans.add(FontColorSpanRange(startPos, endPos, activeColor))
                    idx += 2
                    continue
                }

                // 2. Link: [label](url)
                if (text[idx] == '[') {
                    val closeBracket = findClosingDelimiter(text, idx + 1, "]")
                    if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen != -1) {
                            val label = text.substring(idx + 1, closeBracket)
                            val url = text.substring(closeBracket + 2, closeParen).trim()
                            if (url.isNotEmpty()) {
                                val startPos = plain.length
                                parseSegment(label, activeStyles, activeSize, activeColor)
                                val endPos = plain.length
                                if (endPos > startPos) {
                                    links.add(LinkSpanRange(startPos, endPos, url))
                                }
                                idx = closeParen + 1
                                continue
                            }
                        }
                    }
                }

                // 3. Font Size tag: <size=NN>...</size>
                if (text.startsWith("<size=", idx, ignoreCase = true)) {
                    val closeAngle = findClosingDelimiter(text, idx + 6, ">")
                    if (closeAngle != -1) {
                        val sizeVal = text.substring(idx + 6, closeAngle).toIntOrNull()
                        val closeTag = findClosingDelimiterIgnoreCase(text, closeAngle + 1, "</size>")
                        if (sizeVal != null && closeTag != -1) {
                            val inner = text.substring(closeAngle + 1, closeTag)
                            parseSegment(inner, activeStyles, sizeVal, activeColor)
                            idx = closeTag + 7
                            continue
                        }
                    }
                }

                // 4. Font Color tag: <color=TOKEN>...</color>
                if (text.startsWith("<color=", idx, ignoreCase = true)) {
                    val closeAngle = findClosingDelimiter(text, idx + 7, ">")
                    if (closeAngle != -1) {
                        val tokenStr = text.substring(idx + 7, closeAngle)
                        val token = RichTextColorToken.fromId(tokenStr)
                        val closeTag = findClosingDelimiterIgnoreCase(text, closeAngle + 1, "</color>")
                        if (closeTag != -1) {
                            val inner = text.substring(closeAngle + 1, closeTag)
                            parseSegment(inner, activeStyles, activeSize, token)
                            idx = closeTag + 8
                            continue
                        }
                    }
                }

                // 5. Underline: <u>...</u> or ++...++
                if (text.startsWith("<u>", idx, ignoreCase = true)) {
                    val closeTag = findClosingDelimiterIgnoreCase(text, idx + 3, "</u>")
                    if (closeTag > idx + 3) {
                        val inner = text.substring(idx + 3, closeTag)
                        parseSegment(inner, activeStyles + InlineRichStyle.UNDERLINE, activeSize, activeColor)
                        idx = closeTag + 4
                        continue
                    }
                }
                if (text.startsWith("++", idx)) {
                    val closeTag = findClosingDelimiter(text, idx + 2, "++")
                    if (closeTag > idx + 2) {
                        val inner = text.substring(idx + 2, closeTag)
                        parseSegment(inner, activeStyles + InlineRichStyle.UNDERLINE, activeSize, activeColor)
                        idx = closeTag + 2
                        continue
                    }
                }

                // 5b. Italic tag (used for whitespace-padded or intra-word italic spans): <i>...</i>
                if (text.startsWith("<i>", idx, ignoreCase = true)) {
                    val closeTag = findClosingDelimiterIgnoreCase(text, idx + 3, "</i>")
                    if (closeTag > idx + 3) {
                        val inner = text.substring(idx + 3, closeTag)
                        parseSegment(inner, activeStyles + InlineRichStyle.ITALIC, activeSize, activeColor)
                        idx = closeTag + 4
                        continue
                    }
                }

                // 6. Strikethrough: ~~...~~
                if (text.startsWith("~~", idx)) {
                    val closeTag = findClosingDelimiter(text, idx + 2, "~~")
                    if (closeTag > idx + 2) {
                        val inner = text.substring(idx + 2, closeTag)
                        parseSegment(inner, activeStyles + InlineRichStyle.STRIKETHROUGH, activeSize, activeColor)
                        idx = closeTag + 2
                        continue
                    }
                }

                // 7. Highlight: ==...==
                if (text.startsWith("==", idx)) {
                    val closeTag = findClosingDelimiter(text, idx + 2, "==")
                    if (closeTag > idx + 2) {
                        val inner = text.substring(idx + 2, closeTag)
                        parseSegment(inner, activeStyles + InlineRichStyle.HIGHLIGHT, activeSize, activeColor)
                        idx = closeTag + 2
                        continue
                    }
                }

                // 8. Bold + Italic combined: ***...***
                if (text.startsWith("***", idx)) {
                    val closeIdx = findClosingDelimiter(text, idx + 3, "***")
                    if (closeIdx != -1) {
                        val inner = text.substring(idx + 3, closeIdx)
                        if (inner.isNotEmpty()) {
                            parseSegment(inner, activeStyles + InlineRichStyle.BOLD + InlineRichStyle.ITALIC, activeSize, activeColor)
                            idx = closeIdx + 3
                            continue
                        }
                    }
                }

                // 9. Bold: **...**
                if (text.startsWith("**", idx)) {
                    val closeIdx = findClosingDelimiter(text, idx + 2, "**")
                    if (closeIdx != -1) {
                        val inner = text.substring(idx + 2, closeIdx)
                        if (inner.isNotEmpty()) {
                            parseSegment(inner, activeStyles + InlineRichStyle.BOLD, activeSize, activeColor)
                            idx = closeIdx + 2
                            continue
                        }
                    }
                }

                // 10. Italic with underscore: _..._
                if (text[idx] == '_') {
                    val prevChar = if (idx > 0) text[idx - 1] else ' '
                    val prevIsWord = prevChar.isLetterOrDigit()
                    if (!prevIsWord && idx + 1 < len && !text[idx + 1].isWhitespace() && text[idx + 1] != '_') {
                        val closeIdx = findClosingSingleDelimiter(text, idx + 1, '_')
                        if (closeIdx != -1) {
                            val afterClose = if (closeIdx + 1 < len) text[closeIdx + 1] else ' '
                            if (!afterClose.isLetterOrDigit()) {
                                val inner = text.substring(idx + 1, closeIdx)
                                if (inner.isNotEmpty() && !inner.last().isWhitespace()) {
                                    parseSegment(inner, activeStyles + InlineRichStyle.ITALIC, activeSize, activeColor)
                                    idx = closeIdx + 1
                                    continue
                                }
                            }
                        }
                    }
                }

                // 11. Italic with single asterisk: *...*
                if (text[idx] == '*' && !(idx + 1 < len && text[idx + 1] == '*')) {
                    if (idx + 1 < len && !text[idx + 1].isWhitespace()) {
                        val closeIdx = findClosingSingleDelimiter(text, idx + 1, '*')
                        if (closeIdx != -1) {
                            val inner = text.substring(idx + 1, closeIdx)
                            if (inner.isNotEmpty() && !inner.last().isWhitespace()) {
                                parseSegment(inner, activeStyles + InlineRichStyle.ITALIC, activeSize, activeColor)
                                idx = closeIdx + 1
                                continue
                            }
                        }
                    }
                }

                // Default: append single character with active styles
                val charStart = plain.length
                plain.append(text[idx])
                val charEnd = plain.length
                for (st in activeStyles) {
                    spans.add(InlineSpanRange(charStart, charEnd, st))
                }
                if (activeSize != null) {
                    sizeSpans.add(FontSizeSpanRange(charStart, charEnd, activeSize))
                }
                if (activeColor != null) {
                    colorSpans.add(FontColorSpanRange(charStart, charEnd, activeColor))
                }
                idx++
            }
        }

        parseSegment(input, emptySet(), null, null)
        val finalPlain = plain.toString()
        return DecodedRichText(
            plainText = finalPlain,
            spans = RichTextEditorController.normalizeSpans(spans, finalPlain.length),
            fontSizeSpans = RichTextEditorController.normalizeFontSizeSpans(sizeSpans, finalPlain.length),
            fontColorSpans = RichTextEditorController.normalizeFontColorSpans(colorSpans, finalPlain.length),
            links = RichTextEditorController.normalizeLinks(links, finalPlain.length)
        )
    }

    private fun findClosingDelimiter(text: String, searchFrom: Int, delimiter: String): Int {
        var i = searchFrom
        while (i <= text.length - delimiter.length) {
            if (text[i] == '\\') {
                i += 2
                continue
            }
            if (text.startsWith(delimiter, i)) {
                return i
            }
            i++
        }
        return -1
    }

    private fun findClosingDelimiterIgnoreCase(text: String, searchFrom: Int, delimiter: String): Int {
        var i = searchFrom
        while (i <= text.length - delimiter.length) {
            if (text[i] == '\\') {
                i += 2
                continue
            }
            if (text.startsWith(delimiter, i, ignoreCase = true)) {
                return i
            }
            i++
        }
        return -1
    }

    private fun findClosingSingleDelimiter(text: String, searchFrom: Int, delimiter: Char): Int {
        var i = searchFrom
        while (i < text.length) {
            if (text[i] == '\\') {
                i += 2
                continue
            }
            if (text[i] == delimiter) {
                val nextSame = i + 1 < text.length && text[i + 1] == delimiter
                val prevSame = i > searchFrom && text[i - 1] == delimiter
                if (!nextSame && !prevSame && !text[i - 1].isWhitespace()) {
                    return i
                }
            }
            i++
        }
        return -1
    }
}

/**
 * V2 Contextual Bottom Toolbar docked above the keyboard/navigation bar.
 * Provides: Aa Text Style | Paragraph | Checklist | Image | Attachment | More
 * Only ONE bottom panel is open at a time; tapping the active tool icon closes it.
 */
@Composable
fun V2ContextualEditorBottomBar(
    editorState: RichTextEditorState,
    activePanel: EditorBottomToolPanel,
    onSelectPanel: (EditorBottomToolPanel) -> Unit,
    onToggleInlineStyle: (InlineRichStyle) -> Unit,
    onSelectFontSize: (Int) -> Unit,
    onSelectFontColor: (RichTextColorToken) -> Unit,
    onSelectParagraphAlignment: (ParagraphAlignment) -> Unit,
    onToggleLineFormat: (LineBlockFormat) -> Unit,
    onInsertChecklist: () -> Unit,
    onPickGalleryImage: () -> Unit,
    onCaptureCameraPhoto: () -> Unit,
    onPickDocumentFile: () -> Unit,
    onAttachWebLink: () -> Unit,
    onInsertHyperlink: () -> Unit,
    onInsertCodeBlock: () -> Unit,
    onInsertDivider: () -> Unit,
    onOpenMetadataDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vaultColors = IdeaVaultTheme.colors
    val activeInline = editorState.effectiveActiveStyles()
    val activeFontSize = editorState.effectiveFontSize()
    val activeFontColor = editorState.effectiveFontColor()
    val activeAlignment = editorState.currentParagraphAlignment()
    val activeLineFormat = editorState.currentLineBlockFormat()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(vaultColors.navigationSurface)
            .testTag("editor_v2_bottom_bar")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(vaultColors.border)
        )

        // Primary Icon Bar: Aa | Paragraph | Checklist | Image | Attachment | More
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomToolIconButton(
                label = "Text Style",
                isActive = activePanel == EditorBottomToolPanel.TEXT_STYLE,
                testTag = "editor_toolbar_aa",
                onClick = {
                    onSelectPanel(
                        if (activePanel == EditorBottomToolPanel.TEXT_STYLE) EditorBottomToolPanel.NONE
                        else EditorBottomToolPanel.TEXT_STYLE
                    )
                }
            ) { tint ->
                Text(
                    text = "Aa",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    ),
                    color = tint
                )
            }

            BottomToolIconButton(
                label = "Paragraph & Lists",
                isActive = activePanel == EditorBottomToolPanel.PARAGRAPH,
                testTag = "editor_toolbar_paragraph",
                onClick = {
                    onSelectPanel(
                        if (activePanel == EditorBottomToolPanel.PARAGRAPH) EditorBottomToolPanel.NONE
                        else EditorBottomToolPanel.PARAGRAPH
                    )
                }
            ) { tint ->
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.FormatAlignLeft,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            BottomToolIconButton(
                label = "Checklist",
                isActive = false,
                testTag = "editor_toolbar_checklist",
                onClick = {
                    onSelectPanel(EditorBottomToolPanel.NONE)
                    onInsertChecklist()
                }
            ) { tint ->
                Icon(
                    imageVector = Icons.Outlined.CheckBox,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            BottomToolIconButton(
                label = "Image",
                isActive = activePanel == EditorBottomToolPanel.IMAGE,
                testTag = "editor_toolbar_image",
                onClick = {
                    onSelectPanel(
                        if (activePanel == EditorBottomToolPanel.IMAGE) EditorBottomToolPanel.NONE
                        else EditorBottomToolPanel.IMAGE
                    )
                }
            ) { tint ->
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            BottomToolIconButton(
                label = "Attachment",
                isActive = activePanel == EditorBottomToolPanel.ATTACHMENT,
                testTag = "editor_toolbar_attachment",
                onClick = {
                    onSelectPanel(
                        if (activePanel == EditorBottomToolPanel.ATTACHMENT) EditorBottomToolPanel.NONE
                        else EditorBottomToolPanel.ATTACHMENT
                    )
                }
            ) { tint ->
                Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            BottomToolIconButton(
                label = "More Tools",
                isActive = activePanel == EditorBottomToolPanel.MORE,
                testTag = "editor_toolbar_more",
                onClick = {
                    onSelectPanel(
                        if (activePanel == EditorBottomToolPanel.MORE) EditorBottomToolPanel.NONE
                        else EditorBottomToolPanel.MORE
                    )
                }
            ) { tint ->
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Contextual Expandable Sub-Panel (only one open at a time)
        when (activePanel) {
            EditorBottomToolPanel.NONE -> Unit

            EditorBottomToolPanel.TEXT_STYLE -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(vaultColors.surface)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("editor_panel_text_style"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Bold | Italic | Underline | Strikethrough | Highlight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StyleTileButton(
                            icon = Icons.Outlined.FormatBold,
                            label = "Bold",
                            isActive = InlineRichStyle.BOLD in activeInline,
                            testTag = "editor_format_bold",
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleInlineStyle(InlineRichStyle.BOLD) }
                        )
                        StyleTileButton(
                            icon = Icons.Outlined.FormatItalic,
                            label = "Italic",
                            isActive = InlineRichStyle.ITALIC in activeInline,
                            testTag = "editor_format_italic",
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleInlineStyle(InlineRichStyle.ITALIC) }
                        )
                        StyleTileButton(
                            icon = Icons.Outlined.FormatUnderlined,
                            label = "Underline",
                            isActive = InlineRichStyle.UNDERLINE in activeInline,
                            testTag = "editor_format_underline",
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleInlineStyle(InlineRichStyle.UNDERLINE) }
                        )
                        StyleTileButton(
                            icon = Icons.Outlined.FormatStrikethrough,
                            label = "Strikethrough",
                            isActive = InlineRichStyle.STRIKETHROUGH in activeInline,
                            testTag = "editor_format_strikethrough",
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleInlineStyle(InlineRichStyle.STRIKETHROUGH) }
                        )
                        StyleTileButton(
                            icon = Icons.Outlined.FormatColorFill,
                            label = "Highlight",
                            isActive = InlineRichStyle.HIGHLIGHT in activeInline,
                            testTag = "editor_format_highlight",
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleInlineStyle(InlineRichStyle.HIGHLIGHT) }
                        )
                    }

                    // Row 2: Font Size (12 / 14 / 16 / 18 / 20 / 24)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(vaultColors.secondarySurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Font size",
                            style = MaterialTheme.typography.labelSmall,
                            color = vaultColors.secondaryText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SUPPORTED_EDITOR_FONT_SIZES.forEach { sizeSp ->
                                val selected = activeFontSize == sizeSp
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) vaultColors.actionYellow else Color.Transparent)
                                        .clickable { onSelectFontSize(sizeSp) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("editor_fontsize_$sizeSp"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$sizeSp",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
                                        ),
                                        color = if (selected) vaultColors.darkInk else vaultColors.primaryText
                                    )
                                }
                            }
                        }
                    }

                    // Row 3: Font Color
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(vaultColors.secondarySurface)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Font color",
                            style = MaterialTheme.typography.labelSmall,
                            color = vaultColors.secondaryText
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RichTextColorToken.entries.forEach { token ->
                                val isSelected = activeFontColor == token
                                val swatchColor = token.hexColor ?: vaultColors.primaryText
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(swatchColor)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) vaultColors.actionYellow else vaultColors.border,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable { onSelectFontColor(token) }
                                        .testTag("editor_fontcolor_${token.id.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "${token.label} selected",
                                            tint = if (token == RichTextColorToken.DEFAULT && vaultColors.isDark) {
                                                Color(0xFF191919)
                                            } else if (token == RichTextColorToken.DEFAULT) {
                                                Color.White
                                            } else {
                                                Color(0xFF191919)
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            EditorBottomToolPanel.PARAGRAPH -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .background(vaultColors.surface)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("editor_panel_paragraph"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Alignment (Left | Center | Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StyleTileButton(
                            icon = Icons.AutoMirrored.Outlined.FormatAlignLeft,
                            label = "Align Left",
                            isActive = activeAlignment == ParagraphAlignment.LEFT,
                            testTag = "editor_align_left",
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectParagraphAlignment(ParagraphAlignment.LEFT) }
                        )
                        StyleTileButton(
                            icon = Icons.Outlined.FormatAlignCenter,
                            label = "Align Center",
                            isActive = activeAlignment == ParagraphAlignment.CENTER,
                            testTag = "editor_align_center",
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectParagraphAlignment(ParagraphAlignment.CENTER) }
                        )
                        StyleTileButton(
                            icon = Icons.AutoMirrored.Outlined.FormatAlignRight,
                            label = "Align Right",
                            isActive = activeAlignment == ParagraphAlignment.RIGHT,
                            testTag = "editor_align_right",
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectParagraphAlignment(ParagraphAlignment.RIGHT) }
                        )
                    }

                    // Row 2: Bullets | Numbers | Letters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabeledFormatTileButton(
                            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                            title = "Bullets",
                            isActive = activeLineFormat == LineBlockFormat.BULLET_LIST,
                            testTag = "editor_format_bullet",
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleLineFormat(LineBlockFormat.BULLET_LIST) }
                        )
                        LabeledFormatTileButton(
                            icon = Icons.Outlined.FormatListNumbered,
                            title = "Numbers",
                            isActive = activeLineFormat == LineBlockFormat.NUMBERED_LIST,
                            testTag = "editor_format_numbered",
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleLineFormat(LineBlockFormat.NUMBERED_LIST) }
                        )
                        LabeledFormatTileButton(
                            icon = Icons.Outlined.TextFields,
                            title = "Letters",
                            isActive = activeLineFormat == LineBlockFormat.LETTERED_LIST,
                            testTag = "editor_format_lettered",
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleLineFormat(LineBlockFormat.LETTERED_LIST) }
                        )
                    }

                    // Row 3: Heading | Quote
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LabeledFormatTileButton(
                            icon = Icons.Outlined.Title,
                            title = "Heading",
                            isActive = activeLineFormat == LineBlockFormat.HEADING,
                            testTag = "editor_format_heading",
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleLineFormat(LineBlockFormat.HEADING) }
                        )
                        LabeledFormatTileButton(
                            icon = Icons.Outlined.FormatQuote,
                            title = "Quote",
                            isActive = activeLineFormat == LineBlockFormat.QUOTE,
                            testTag = "editor_format_quote",
                            modifier = Modifier.weight(1f),
                            onClick = { onToggleLineFormat(LineBlockFormat.QUOTE) }
                        )
                    }
                }
            }

            EditorBottomToolPanel.IMAGE -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(vaultColors.surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("editor_panel_image"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabeledFormatTileButton(
                        icon = Icons.Outlined.Image,
                        title = "Images",
                        isActive = false,
                        testTag = "editor_attach_image_button",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectPanel(EditorBottomToolPanel.NONE)
                            onPickGalleryImage()
                        }
                    )
                    LabeledFormatTileButton(
                        icon = Icons.Outlined.CameraAlt,
                        title = "Photo",
                        isActive = false,
                        testTag = "editor_capture_photo_button",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectPanel(EditorBottomToolPanel.NONE)
                            onCaptureCameraPhoto()
                        }
                    )
                }
            }

            EditorBottomToolPanel.ATTACHMENT -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(vaultColors.surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("editor_panel_attachment"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabeledFormatTileButton(
                        icon = Icons.Outlined.AttachFile,
                        title = "File / PDF",
                        isActive = false,
                        testTag = "editor_attach_file_button",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectPanel(EditorBottomToolPanel.NONE)
                            onPickDocumentFile()
                        }
                    )
                    LabeledFormatTileButton(
                        icon = Icons.Outlined.Link,
                        title = "Web Link",
                        isActive = false,
                        testTag = "editor_add_attachment_button",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectPanel(EditorBottomToolPanel.NONE)
                            onAttachWebLink()
                        }
                    )
                }
            }

            EditorBottomToolPanel.MORE -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(vaultColors.surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("editor_panel_more"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabeledFormatTileButton(
                        icon = Icons.Outlined.Link,
                        title = "Hyperlink",
                        isActive = false,
                        testTag = "editor_format_link",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectPanel(EditorBottomToolPanel.NONE)
                            onInsertHyperlink()
                        }
                    )
                    LabeledFormatTileButton(
                        icon = Icons.Outlined.Code,
                        title = "Code Block",
                        isActive = false,
                        testTag = "editor_insert_code_block",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectPanel(EditorBottomToolPanel.NONE)
                            onInsertCodeBlock()
                        }
                    )
                    LabeledFormatTileButton(
                        icon = Icons.Outlined.HorizontalRule,
                        title = "Divider",
                        isActive = false,
                        testTag = "editor_insert_divider",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectPanel(EditorBottomToolPanel.NONE)
                            onInsertDivider()
                        }
                    )
                    LabeledFormatTileButton(
                        icon = Icons.Outlined.Tune,
                        title = "Details",
                        isActive = false,
                        testTag = "editor_toggle_metadata_sheet",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSelectPanel(EditorBottomToolPanel.NONE)
                            onOpenMetadataDetails()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomToolIconButton(
    label: String,
    isActive: Boolean,
    testTag: String,
    onClick: () -> Unit,
    iconContent: @Composable (Color) -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    val tint = if (isActive) vaultColors.actionYellow else vaultColors.primaryText
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isActive) vaultColors.secondarySurface else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
                selected = isActive
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        iconContent(tint)
    }
}

@Composable
private fun StyleTileButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    val bg = if (isActive) vaultColors.actionYellow else vaultColors.secondarySurface
    val fg = if (isActive) vaultColors.darkInk else vaultColors.primaryText
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
                selected = isActive
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun LabeledFormatTileButton(
    icon: ImageVector,
    title: String,
    isActive: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    val bg = if (isActive) vaultColors.actionYellow else vaultColors.secondarySurface
    val fg = if (isActive) vaultColors.darkInk else vaultColors.primaryText
    Column(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = title
                role = Role.Button
                selected = isActive
            }
            .testTag(testTag)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1
        )
    }
}

/**
 * Compact inline formatting toolbar kept for direct horizontal toolbar usage.
 */
@Composable
fun RichTextFormattingToolbar(
    editorState: RichTextEditorState,
    accentColor: Color,
    onToggleInlineStyle: (InlineRichStyle) -> Unit,
    onToggleLineFormat: (LineBlockFormat) -> Unit,
    onRequestInsertLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vaultColors = IdeaVaultTheme.colors
    val activeInline = editorState.effectiveActiveStyles()
    val activeLineFormat = editorState.currentLineBlockFormat()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RichFormatToolbarButton(
            icon = Icons.Outlined.FormatBold,
            label = "Bold",
            isActive = InlineRichStyle.BOLD in activeInline,
            accentColor = accentColor,
            testTag = "editor_format_bold",
            onClick = { onToggleInlineStyle(InlineRichStyle.BOLD) }
        )
        RichFormatToolbarButton(
            icon = Icons.Outlined.FormatItalic,
            label = "Italic",
            isActive = InlineRichStyle.ITALIC in activeInline,
            accentColor = accentColor,
            testTag = "editor_format_italic",
            onClick = { onToggleInlineStyle(InlineRichStyle.ITALIC) }
        )
        RichFormatToolbarButton(
            icon = Icons.Outlined.FormatUnderlined,
            label = "Underline",
            isActive = InlineRichStyle.UNDERLINE in activeInline,
            accentColor = accentColor,
            testTag = "editor_format_underline",
            onClick = { onToggleInlineStyle(InlineRichStyle.UNDERLINE) }
        )
        RichFormatToolbarButton(
            icon = Icons.Outlined.FormatStrikethrough,
            label = "Strikethrough",
            isActive = InlineRichStyle.STRIKETHROUGH in activeInline,
            accentColor = accentColor,
            testTag = "editor_format_strikethrough",
            onClick = { onToggleInlineStyle(InlineRichStyle.STRIKETHROUGH) }
        )
        RichFormatToolbarButton(
            icon = Icons.Outlined.FormatColorFill,
            label = "Highlight",
            isActive = InlineRichStyle.HIGHLIGHT in activeInline,
            accentColor = accentColor,
            testTag = "editor_format_highlight",
            onClick = { onToggleInlineStyle(InlineRichStyle.HIGHLIGHT) }
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(22.dp)
                .background(vaultColors.border)
        )
        RichFormatToolbarButton(
            icon = Icons.Outlined.Title,
            label = "Heading",
            isActive = activeLineFormat == LineBlockFormat.HEADING,
            accentColor = accentColor,
            testTag = "editor_format_heading",
            onClick = { onToggleLineFormat(LineBlockFormat.HEADING) }
        )
        RichFormatToolbarButton(
            icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
            label = "Bullet List",
            isActive = activeLineFormat == LineBlockFormat.BULLET_LIST,
            accentColor = accentColor,
            testTag = "editor_format_bullet",
            onClick = { onToggleLineFormat(LineBlockFormat.BULLET_LIST) }
        )
        RichFormatToolbarButton(
            icon = Icons.Outlined.FormatListNumbered,
            label = "Numbered List",
            isActive = activeLineFormat == LineBlockFormat.NUMBERED_LIST,
            accentColor = accentColor,
            testTag = "editor_format_numbered",
            onClick = { onToggleLineFormat(LineBlockFormat.NUMBERED_LIST) }
        )
        RichFormatToolbarButton(
            icon = Icons.Outlined.FormatQuote,
            label = "Quote",
            isActive = activeLineFormat == LineBlockFormat.QUOTE,
            accentColor = accentColor,
            testTag = "editor_format_quote",
            onClick = { onToggleLineFormat(LineBlockFormat.QUOTE) }
        )
        RichFormatToolbarButton(
            icon = Icons.Outlined.Link,
            label = "Insert Link",
            isActive = false,
            accentColor = accentColor,
            testTag = "editor_format_link",
            onClick = onRequestInsertLink
        )
    }
}

@Composable
private fun RichFormatToolbarButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val vaultColors = IdeaVaultTheme.colors
    val containerColor = if (isActive) accentColor else vaultColors.surface
    val contentColor = if (isActive) vaultColors.darkInk else vaultColors.primaryText
    val borderColor = if (isActive) accentColor else vaultColors.border

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
                selected = isActive
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * RichTextContentRenderer renders stored rich text on ItemDetailScreen with visual
 * Bold, Italic, Underline, Strikethrough, Highlight, Font Size, Font Color, Alignment,
 * Headings, Bullet lists, Numbered lists, Lettered lists, Quotes, and Links — without
 * displaying raw Markdown syntax.
 */
@Composable
fun RichTextContentRenderer(
    rawContent: String,
    accentColor: Color = IdeaVaultTheme.colors.coral,
    onOpenUrl: (String) -> Unit = {},
    textStyle: androidx.compose.ui.text.TextStyle? = null,
    textColor: Color? = null,
    modifier: Modifier = Modifier,
    testTag: String = "rich_text_rendered_content"
) {
    val vaultColors = IdeaVaultTheme.colors
    val resolvedPrimaryTextColor = textColor ?: vaultColors.primaryText
    val baseBodyStyle = (textStyle ?: MaterialTheme.typography.bodyLarge).copy(
        color = resolvedPrimaryTextColor,
        lineHeight = textStyle?.lineHeight ?: 24.sp
    )
    val paragraphs = RichTextSerializer.parseParagraphsForDisplay(rawContent)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        paragraphs.forEachIndexed { index, paragraph ->
            if (paragraph.plainText.isEmpty() && paragraph.format == LineBlockFormat.PARAGRAPH) {
                if (paragraphs.size > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                return@forEachIndexed
            }

            val annotated = buildParagraphAnnotatedString(
                paragraph = paragraph,
                primaryTextColor = resolvedPrimaryTextColor,
                secondaryTextColor = vaultColors.secondaryText,
                linkColor = accentColor,
                isDarkTheme = vaultColors.isDark
            )

            val textAlign = when (paragraph.alignment) {
                ParagraphAlignment.CENTER -> TextAlign.Center
                ParagraphAlignment.RIGHT -> TextAlign.End
                ParagraphAlignment.LEFT -> TextAlign.Start
            }

            when (paragraph.format) {
                LineBlockFormat.HEADING -> {
                    val headingStyle = when (paragraph.headingLevel) {
                        1 -> MaterialTheme.typography.headlineSmall.copy(
                            color = resolvedPrimaryTextColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = textAlign
                        )
                        2 -> MaterialTheme.typography.titleLarge.copy(
                            color = resolvedPrimaryTextColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = textAlign
                        )
                        else -> MaterialTheme.typography.titleMedium.copy(
                            color = resolvedPrimaryTextColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = textAlign
                        )
                    }
                    ParagraphClickableText(
                        annotated = annotated,
                        style = headingStyle,
                        onOpenUrl = onOpenUrl,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                LineBlockFormat.BULLET_LIST -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = baseBodyStyle.copy(fontWeight = FontWeight.Bold),
                            color = accentColor,
                            modifier = Modifier.padding(end = 10.dp, top = 1.dp)
                        )
                        ParagraphClickableText(
                            annotated = annotated,
                            style = baseBodyStyle.copy(textAlign = textAlign),
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                LineBlockFormat.NUMBERED_LIST -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${paragraph.listNumber}.",
                            style = baseBodyStyle.copy(fontWeight = FontWeight.Bold),
                            color = accentColor,
                            modifier = Modifier.padding(end = 10.dp, top = 1.dp)
                        )
                        ParagraphClickableText(
                            annotated = annotated,
                            style = baseBodyStyle.copy(textAlign = textAlign),
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                LineBlockFormat.LETTERED_LIST -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${paragraph.listLetter}.",
                            style = baseBodyStyle.copy(fontWeight = FontWeight.Bold),
                            color = accentColor,
                            modifier = Modifier.padding(end = 10.dp, top = 1.dp)
                        )
                        ParagraphClickableText(
                            annotated = annotated,
                            style = baseBodyStyle.copy(textAlign = textAlign),
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                LineBlockFormat.QUOTE -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(10.dp))
                            .background(vaultColors.elevatedSurface.copy(alpha = 0.65f))
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp))
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        ParagraphClickableText(
                            annotated = annotated,
                            style = baseBodyStyle.copy(
                                fontStyle = FontStyle.Italic,
                                textAlign = textAlign
                            ),
                            onOpenUrl = onOpenUrl,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                LineBlockFormat.PARAGRAPH -> {
                    ParagraphClickableText(
                        annotated = annotated,
                        style = baseBodyStyle.copy(textAlign = textAlign),
                        onOpenUrl = onOpenUrl,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ParagraphClickableText(
    annotated: AnnotatedString,
    style: androidx.compose.ui.text.TextStyle,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasLinks = annotated.getStringAnnotations("URL", 0, annotated.length).isNotEmpty()
    if (hasLinks) {
        @Suppress("DEPRECATION")
        ClickableText(
            text = annotated,
            style = style,
            modifier = modifier,
            onClick = { offset ->
                annotated.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()
                    ?.let { onOpenUrl(it.item) }
            }
        )
    } else {
        Text(
            text = annotated,
            style = style,
            modifier = modifier
        )
    }
}

internal fun buildParagraphAnnotatedString(
    paragraph: ParsedRichParagraph,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    linkColor: Color,
    isDarkTheme: Boolean = true
): AnnotatedString {
    return buildAnnotatedString {
        append(paragraph.plainText)
        val len = paragraph.plainText.length
        addStyle(SpanStyle(color = primaryTextColor), 0, len)

        for (span in paragraph.inlineSpans) {
            val s = span.start.coerceIn(0, len)
            val e = span.end.coerceIn(0, len)
            if (s < e) {
                val spanStyle = when (span.style) {
                    InlineRichStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    InlineRichStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    InlineRichStyle.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                    InlineRichStyle.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    InlineRichStyle.HIGHLIGHT -> SpanStyle(
                        background = Color(0xFFF9D55D).copy(alpha = if (isDarkTheme) 0.35f else 0.55f)
                    )
                }
                addStyle(spanStyle, s, e)
            }
        }

        val underlineSpans = paragraph.inlineSpans.filter { it.style == InlineRichStyle.UNDERLINE }
        val strikeSpans = paragraph.inlineSpans.filter { it.style == InlineRichStyle.STRIKETHROUGH }
        if (underlineSpans.isNotEmpty() && strikeSpans.isNotEmpty()) {
            for (u in underlineSpans) {
                for (st in strikeSpans) {
                    val overlapStart = max(u.start, st.start).coerceIn(0, len)
                    val overlapEnd = min(u.end, st.end).coerceIn(0, len)
                    if (overlapStart < overlapEnd) {
                        addStyle(
                            SpanStyle(
                                textDecoration = TextDecoration.combine(
                                    listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                                )
                            ),
                            overlapStart,
                            overlapEnd
                        )
                    }
                }
            }
        }

        for (sizeSpan in paragraph.fontSizeSpans) {
            val s = sizeSpan.start.coerceIn(0, len)
            val e = sizeSpan.end.coerceIn(0, len)
            if (s < e && sizeSpan.sizeSp != DEFAULT_EDITOR_FONT_SIZE) {
                addStyle(SpanStyle(fontSize = sizeSpan.sizeSp.sp), s, e)
            }
        }

        for (colorSpan in paragraph.fontColorSpans) {
            val s = colorSpan.start.coerceIn(0, len)
            val e = colorSpan.end.coerceIn(0, len)
            val targetColor = colorSpan.colorToken.hexColor
            if (s < e && targetColor != null) {
                addStyle(SpanStyle(color = targetColor), s, e)
            }
        }

        for (link in paragraph.links) {
            val s = link.start.coerceIn(0, len)
            val e = link.end.coerceIn(0, len)
            if (s < e) {
                addStyle(
                    SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline
                    ),
                    s,
                    e
                )
                addStringAnnotation("URL", link.url, s, e)
            }
        }
    }
}
