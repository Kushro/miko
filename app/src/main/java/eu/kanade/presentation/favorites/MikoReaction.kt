package eu.kanade.presentation.favorites

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * MIKO — C16: every expression of the mascot. The art is the user's sticker set
 * (`drawable-nodpi/miko_*.webp`, cut out from their two sheets); each entry pairs the drawable
 * with its contentDescription string.
 */
enum class MikoExpression(@DrawableRes val drawableRes: Int, val descriptionRes: StringResource) {
    /** Finger to her chin, eyes up — used while she "thinks" the bookmark's note in a bubble. */
    THINKING(R.drawable.miko_thinking, MKMR.strings.page_bookmark_miko_thinking),

    /** Hand to her mouth, eyes closed — the modest "ara, ara" surprise. */
    ARA_ARA(R.drawable.miko_ara_ara, MKMR.strings.page_bookmark_miko_ara_ara),

    /** Question mark, sweat drop, finger to her teeth — shown while the page is being resolved. */
    CONFUSED(R.drawable.miko_confused, MKMR.strings.page_bookmark_miko_confused),

    /** Starry eyes, fists up, a sparkle — one of the no-note poses. */
    EXCITED(R.drawable.miko_excited, MKMR.strings.page_bookmark_miko_excited),

    /** Crossed arms, puffed cheek — the Chapters tab's empty state. */
    POUTING(R.drawable.miko_pouting, MKMR.strings.page_bookmark_miko_pouting),

    /** Starry eyes, hands clasped — one of the no-note poses. */
    AMAZED(R.drawable.miko_amazed, MKMR.strings.page_bookmark_miko_amazed),

    /** Fist up, welling eyes — shown while retrying a failed page load: she insists. */
    DETERMINED(R.drawable.miko_determined, MKMR.strings.page_bookmark_miko_determined),

    /** Half-lidded eyes, sigh cloud — the Pages tab's empty state. */
    SIGHING(R.drawable.miko_sighing, MKMR.strings.page_bookmark_miko_sighing),

    /** Bawling, waterfall tears — shown when the page could not be loaded. */
    CRYING(R.drawable.miko_crying, MKMR.strings.page_bookmark_miko_crying),

    /** Wink and a thumbs-up — flashed briefly after saving/sharing the image or saving the note. */
    WINK(R.drawable.miko_wink, MKMR.strings.page_bookmark_miko_wink),
}

/**
 * MIKO — C15/C16: the comic-mascot corner of the page-bookmark preview (`PageBookmarkPreviewDialog`).
 * Draws [expression]; with [MikoExpression.THINKING] and a non-blank [note], a comic thought
 * bubble holds the note text. Sized to just fit its own content — never `fillMaxSize` — so it
 * never steals touches from the zoomable page image behind it.
 *
 * @param note the bookmark's note as-is; blank/null both mean "no bubble".
 * @param showChibi C18: the Settings → Appearance mascot toggle. Off, the mascot (and the bubble's
 * trailing dots, which point at her head) is not drawn, but the note bubble stays — the note is
 * information, not decoration. Off and with no bubble to show, nothing is emitted at all.
 */
@Composable
fun MikoReaction(
    expression: MikoExpression,
    note: String?,
    // MIKO --> C18
    showChibi: Boolean,
    // MIKO <--
    modifier: Modifier = Modifier,
) {
    val bubbleNote = note?.takeIf { it.isNotBlank() && expression == MikoExpression.THINKING }
    // MIKO --> C18
    if (!showChibi && bubbleNote == null) return
    // MIKO <--
    Column(
        modifier = modifier.wrapContentSize(),
        horizontalAlignment = Alignment.Start,
    ) {
        if (bubbleNote != null) {
            Surface(
                modifier = Modifier
                    .padding(start = BubbleStartOffset)
                    .widthIn(max = BubbleMaxWidth),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = bubbleNote,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp),
                )
            }
            // MIKO --> C18: without her, there is no head for the dots to point at.
            if (showChibi) {
                // MIKO <--
                // Two staggered dots trailing from the bubble down towards her head.
                Surface(
                    modifier = Modifier
                        .padding(start = BubbleStartOffset - 12.dp, top = 4.dp)
                        .size(10.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp,
                    content = {},
                )
                Surface(
                    modifier = Modifier
                        .padding(start = BubbleStartOffset - 24.dp, top = 2.dp)
                        .size(6.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp,
                    content = {},
                )
                // MIKO --> C18
            }
            // MIKO <--
        }
        // MIKO --> C18
        if (showChibi) {
            // MIKO <--
            Image(
                painter = painterResource(expression.drawableRes),
                contentDescription = stringResource(expression.descriptionRes),
                modifier = Modifier.height(MikoHeight),
            )
            // MIKO --> C18
        }
        // MIKO <--
    }
}

private val MikoHeight = 110.dp
private val BubbleMaxWidth = 220.dp

/** Shifts the bubble and its dots to sit above-right of her head instead of directly overhead. */
private val BubbleStartOffset = 40.dp
