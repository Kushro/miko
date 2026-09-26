package eu.kanade.presentation.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * MIKO — presentation mapping for [ChapterBookmarkType]: the short label and the small "exponent"
 * icon drawn on the top-end corner of the bookmark glyph. `GENERIC` has no exponent.
 */
val ChapterBookmarkType.labelRes: StringResource
    get() = when (this) {
        ChapterBookmarkType.GENERIC -> MKMR.strings.chapter_bookmark_type_generic
        ChapterBookmarkType.PLOT_TWIST -> MKMR.strings.chapter_bookmark_type_plot_twist
        ChapterBookmarkType.CHARACTER_GROWTH -> MKMR.strings.chapter_bookmark_type_character_growth
        ChapterBookmarkType.ART -> MKMR.strings.chapter_bookmark_type_art
    }

val ChapterBookmarkType.exponentIcon: ImageVector?
    get() = when (this) {
        ChapterBookmarkType.GENERIC -> null
        ChapterBookmarkType.PLOT_TWIST -> Icons.Outlined.Bolt
        ChapterBookmarkType.CHARACTER_GROWTH -> Icons.AutoMirrored.Outlined.TrendingUp
        ChapterBookmarkType.ART -> Icons.Outlined.Palette
    }

/**
 * The chapter-bookmark glyph: `Icons.Filled.Bookmark` (always the primary "this is a favorite"
 * indicator) plus, for every kind but `GENERIC`, a small exponent-style sub-icon on a contrasting
 * disc at the top-end corner. Long-pressing shows the kind's name in a tooltip.
 *
 * The composable is [size] × [size]; the exponent overflows the corner by a hair so it reads as a
 * superscript rather than as part of the bookmark. Callers that need the glyph to be tappable
 * wrap it in their own clickable container (a `Box` with `combinedClickable`, an `IconButton`…),
 * so the tooltip long-press and the caller's tap coexist.
 *
 * @param showTooltip false when the caller already provides its own tooltip (e.g. `AppBar.Action`).
 */
@Composable
fun ChapterBookmarkTypeIcon(
    type: ChapterBookmarkType,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    showTooltip: Boolean = true,
) {
    val label = stringResource(type.labelRes)
    val glyph: @Composable () -> Unit = {
        Box(modifier = modifier.size(size)) {
            Icon(
                imageVector = Icons.Filled.Bookmark,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.fillMaxSize(),
            )
            val exponent = type.exponentIcon
            if (exponent != null) {
                val badgeSize = size * EXPONENT_RATIO
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = badgeSize / 4, y = -(badgeSize / 4))
                        .size(badgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(size / 20)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = exponent,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(badgeSize * 0.7f),
                    )
                }
            }
        }
    }
    if (!showTooltip) {
        glyph()
        return
    }
    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text = label) } },
        state = rememberTooltipState(),
        focusable = false,
        content = glyph,
    )
}

/** Exponent disc size relative to the bookmark glyph. */
private const val EXPONENT_RATIO = 0.6f
