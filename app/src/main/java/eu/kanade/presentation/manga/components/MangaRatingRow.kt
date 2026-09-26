package eu.kanade.presentation.manga.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

/**
 * MIKO — the user's own 1..5 star rating for an entry, plus the rating reported by the source when
 * it is known: either from a Kotatsu parser (`KotatsuSource.getRemoteRating`) or from the
 * `RemoteRatingProvider` of a registered source enhancement (`SourceEnhancementRegistry`), which is
 * what covers installed extensions.
 *
 * Tapping a star sets that value; tapping the star that is already selected, or long-pressing any
 * star, clears the rating.
 */
@Composable
fun MangaRatingRow(
    localRating: Int?,
    remoteRating: Float?,
    onRatingChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clearLabel = stringResource(MKMR.strings.rating_clear)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(MKMR.strings.my_rating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        for (star in 1..MAX_RATING) {
            val selected = localRating != null && star <= localRating
            Icon(
                imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = stringResource(MKMR.strings.rating_stars, star),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(STAR_TOUCH_SIZE)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = { onRatingChange(if (localRating == star) null else star) },
                        onLongClick = { onRatingChange(null) },
                        onLongClickLabel = clearLabel,
                    )
                    .padding(STAR_TOUCH_PADDING),
            )
        }

        if (remoteRating != null) {
            Text(
                text = stringResource(
                    MKMR.strings.rating_remote,
                    String.format(Locale.getDefault(), "%.1f/5", remoteRating * MAX_RATING),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private const val MAX_RATING = 5

/** 24dp icon inside a 32dp touch target. */
private val STAR_TOUCH_SIZE = 32.dp
private val STAR_TOUCH_PADDING = 4.dp
