package eu.kanade.presentation.browse.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.domain.source.model.SourceKind
import tachiyomi.domain.source.model.Source
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * MIKO — grid cell of the Sources tab: icon with the [SourceKind] badge pinned to its corner, the
 * source name on at most two lines, the language flag and the detected capabilities.
 *
 * `null` [kind]/[features] render nothing, which is what the corresponding display options pass
 * when they are switched off.
 */
@Composable
fun SourceGridItem(
    source: Source,
    kind: SourceKind?,
    features: Set<SourceFeature>?,
    onClickItem: () -> Unit,
    onLongClickItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClickItem, onLongClick = onLongClickItem)
            .padding(
                horizontal = MaterialTheme.padding.extraSmall,
                vertical = MaterialTheme.padding.small,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Box {
            SourceIcon(source = source, modifier = Modifier.size(48.dp))
            if (kind != null) {
                SourceKindBadge(
                    kind = kind,
                    compact = true,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
        Text(
            text = source.name.ifBlank { source.id.toString() },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (source.lang.isNotEmpty()) {
            Text(
                text = FlagEmoji.getEmojiLangFlag(source.lang) + " " + source.lang.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.secondaryItemAlpha(),
            )
        }
        SourceFeatureIcons(features = features, max = 4)
    }
}
