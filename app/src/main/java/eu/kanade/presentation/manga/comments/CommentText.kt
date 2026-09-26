package eu.kanade.presentation.manga.comments

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * MIKO — renders the lightweight markup comment sites use: `**bold**`, `__bold__`, `***bold
 * italic***`, `*italic*`, `~~strike~~`, `||spoiler||` and `@mentions`. Everything else is literal,
 * `\n` included (the text is never HTML).
 *
 * Spoilers start masked — same background as the text colour — and each one uncovers on its own tap;
 * the reveal is remembered per composition, so scrolling a spoiler out of a lazy list re-hides it,
 * which is the safer default.
 */
@Composable
fun CommentText(
    text: String,
    modifier: Modifier = Modifier,
) {
    var revealedSpoilers by remember(text) { mutableStateOf(emptySet<Int>()) }
    var layoutResult by remember(text) { mutableStateOf<TextLayoutResult?>(null) }

    val mentionColor = MaterialTheme.colorScheme.primary
    val spoilerBackground = MaterialTheme.colorScheme.surfaceVariant
    val spoilerHint = stringResource(MKMR.strings.comments_spoiler)

    val annotated = remember(text, revealedSpoilers, mentionColor, spoilerBackground) {
        buildCommentAnnotatedString(
            text = text,
            mentionColor = mentionColor,
            spoilerBackground = spoilerBackground,
            revealedSpoilers = revealedSpoilers,
        )
    }
    val hasHiddenSpoiler = remember(annotated, revealedSpoilers) {
        annotated.getStringAnnotations(SPOILER_TAG, 0, annotated.length)
            .any { annotation -> annotation.item.toIntOrNull()?.let { it !in revealedSpoilers } == true }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium,
        onTextLayout = { layoutResult = it },
        modifier = modifier
            .then(
                if (hasHiddenSpoiler) Modifier.semantics { contentDescription = spoilerHint } else Modifier,
            )
            .pointerInput(annotated) {
                detectTapGestures { position ->
                    val layout = layoutResult ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(position)
                    val hit = annotated.getStringAnnotations(SPOILER_TAG, offset, offset).firstOrNull()
                        ?: return@detectTapGestures
                    val index = hit.item.toIntOrNull() ?: return@detectTapGestures
                    revealedSpoilers = revealedSpoilers + index
                }
            },
    )
}

private const val SPOILER_TAG = "miko-comment-spoiler"

/** `@user`, matching what the sites accept. */
private val MENTION_REGEX = Regex("@[a-zA-Z0-9_-]{3,20}")

/**
 * Markup tokens in the order the sites resolve them: the longest fences first, so `***x***` is not
 * eaten by the `*` rule and `**x**` is not eaten by `*x*`.
 */
private val FENCES = listOf(
    Fence(open = "||", style = null, spoiler = true),
    Fence(open = "***", style = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)),
    Fence(open = "**", style = SpanStyle(fontWeight = FontWeight.Bold)),
    Fence(open = "__", style = SpanStyle(fontWeight = FontWeight.Bold)),
    Fence(open = "~~", style = SpanStyle(textDecoration = TextDecoration.LineThrough)),
    Fence(open = "*", style = SpanStyle(fontStyle = FontStyle.Italic)),
)

private class Fence(
    val open: String,
    val style: SpanStyle?,
    val spoiler: Boolean = false,
)

/**
 * Sequential scanner: at every position it takes the earliest token (fence or mention) and recurses
 * into the fence's body, so `**bold with *italic* inside**` nests. Unterminated fences stay literal.
 */
internal fun buildCommentAnnotatedString(
    text: String,
    mentionColor: Color,
    spoilerBackground: Color,
    revealedSpoilers: Set<Int>,
): AnnotatedString {
    var spoilerCounter = 0
    return buildAnnotatedString {
        fun render(input: String) {
            var index = 0
            while (index < input.length) {
                var bestStart = -1
                var bestFence: Fence? = null
                var bestBodyStart = 0
                var bestBodyEnd = 0
                var bestNext = 0

                for (fence in FENCES) {
                    val start = input.indexOf(fence.open, index)
                    if (start < 0) continue
                    if (bestStart >= 0 && bestStart < start) continue
                    val bodyStart = start + fence.open.length
                    val close = input.indexOf(fence.open, bodyStart)
                    // An empty or unterminated fence is plain text.
                    if (close < 0 || close == bodyStart) continue
                    if (bestStart < 0 || start < bestStart) {
                        bestStart = start
                        bestFence = fence
                        bestBodyStart = bodyStart
                        bestBodyEnd = close
                        bestNext = close + fence.open.length
                    }
                }

                val mention = MENTION_REGEX.find(input, index)
                if (mention != null && (bestStart < 0 || mention.range.first < bestStart)) {
                    appendPlain(input.substring(index, mention.range.first))
                    withStyleCompat(SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold)) {
                        append(mention.value)
                    }
                    index = mention.range.last + 1
                    continue
                }

                val fence = bestFence
                if (fence == null || bestStart < 0) {
                    appendPlain(input.substring(index))
                    return
                }

                appendPlain(input.substring(index, bestStart))
                val body = input.substring(bestBodyStart, bestBodyEnd)
                if (fence.spoiler) {
                    val spoilerIndex = spoilerCounter++
                    val revealed = spoilerIndex in revealedSpoilers
                    pushStringAnnotation(SPOILER_TAG, spoilerIndex.toString())
                    val style = if (revealed) {
                        SpanStyle(background = spoilerBackground)
                    } else {
                        SpanStyle(background = spoilerBackground, color = Color.Transparent)
                    }
                    withStyleCompat(style) { render(body) }
                    pop()
                } else {
                    withStyleCompat(fence.style ?: SpanStyle()) { render(body) }
                }
                index = bestNext
            }
        }

        render(text)
    }
}

/** `append` that keeps line breaks as line breaks; the sites store them as `\n`. */
private fun AnnotatedString.Builder.appendPlain(value: String) {
    if (value.isNotEmpty()) append(value)
}

/**
 * `withStyle` as a plain helper: the builder's own inline function cannot be called from a local
 * recursive function without capturing it, and pushing/popping by hand keeps the nesting explicit.
 */
private inline fun AnnotatedString.Builder.withStyleCompat(style: SpanStyle, block: () -> Unit) {
    pushStyle(style)
    try {
        block()
    } finally {
        pop()
    }
}
