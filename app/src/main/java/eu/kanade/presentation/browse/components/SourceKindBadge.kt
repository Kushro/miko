package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.Explicit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.domain.source.model.SourceKind
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.i18n.stringResource

/*
 * MIKO — shared visuals for source kinds and features. Used by the Sources tab, library covers,
 * global search, manga details and the migration screens, so all of them agree on icon + colour.
 */

/** Icon representing a [SourceKind]. */
val SourceKind.icon: ImageVector
    get() = when (this) {
        SourceKind.LOCAL -> Icons.Outlined.Folder
        SourceKind.BUILT_IN -> Icons.Outlined.Memory
        SourceKind.BUILT_IN_DEDICATED -> Icons.Outlined.AutoAwesome
        SourceKind.EXTENSION -> Icons.Outlined.Extension
        SourceKind.EXTENSION_ENHANCED -> Icons.Outlined.Hub
        SourceKind.NOT_INSTALLED -> Icons.Outlined.Warning
    }

/** Container colour of a [SourceKind] (badge background). */
@Composable
fun SourceKind.containerColor(): Color = when (this) {
    SourceKind.LOCAL -> MaterialTheme.colorScheme.surfaceVariant
    SourceKind.BUILT_IN -> MaterialTheme.colorScheme.tertiaryContainer
    SourceKind.BUILT_IN_DEDICATED -> MaterialTheme.colorScheme.tertiary
    SourceKind.EXTENSION -> MaterialTheme.colorScheme.secondaryContainer
    SourceKind.EXTENSION_ENHANCED -> MaterialTheme.colorScheme.primary
    SourceKind.NOT_INSTALLED -> MaterialTheme.colorScheme.errorContainer
}

/** Content colour of a [SourceKind] (badge foreground). */
@Composable
fun SourceKind.contentColor(): Color = when (this) {
    SourceKind.LOCAL -> MaterialTheme.colorScheme.onSurfaceVariant
    SourceKind.BUILT_IN -> MaterialTheme.colorScheme.onTertiaryContainer
    SourceKind.BUILT_IN_DEDICATED -> MaterialTheme.colorScheme.onTertiary
    SourceKind.EXTENSION -> MaterialTheme.colorScheme.onSecondaryContainer
    SourceKind.EXTENSION_ENHANCED -> MaterialTheme.colorScheme.onPrimary
    SourceKind.NOT_INSTALLED -> MaterialTheme.colorScheme.onErrorContainer
}

/** Icon representing a [SourceFeature]. */
val SourceFeature.icon: ImageVector
    get() = when (this) {
        SourceFeature.LATEST -> Icons.Outlined.NewReleases
        SourceFeature.SEARCH -> Icons.Outlined.Search
        SourceFeature.TAGS -> Icons.Outlined.Sell
        SourceFeature.TAG_EXCLUSION -> Icons.Outlined.Sell
        SourceFeature.YEAR -> Icons.Outlined.CalendarMonth
        SourceFeature.AUTHOR_SEARCH -> Icons.Outlined.Person
        SourceFeature.MULTI_LANGUAGE -> Icons.Outlined.Translate
        SourceFeature.LOGIN -> Icons.Outlined.Login
        SourceFeature.CONFIGURABLE -> Icons.Outlined.Tune
        SourceFeature.RELATED -> Icons.Outlined.Hub
        SourceFeature.NSFW -> Icons.Outlined.Explicit
        SourceFeature.METADATA -> Icons.Outlined.Info
        SourceFeature.FOLLOWS -> Icons.Outlined.Sync
        SourceFeature.ALTERNATIVE_DOMAINS -> Icons.Outlined.Link
        SourceFeature.CHAPTER_COMMENTS -> Icons.Outlined.Comment
    }

/**
 * Inline pill "⚙ BUILT-IN" for rows and headers. [compact] drops the label and keeps the icon.
 */
@Composable
fun SourceKindBadge(
    kind: SourceKind,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(kind.containerColor())
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = kind.icon,
            contentDescription = stringResource(kind.titleRes),
            tint = kind.contentColor(),
            modifier = Modifier.size(12.dp),
        )
        if (!compact) {
            Text(
                text = stringResource(kind.shortLabelRes),
                color = kind.contentColor(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** Cover badge (18 dp high, for `BadgeGroup`) with the kind icon on the kind colour. */
@Composable
fun SourceKindCoverBadge(kind: SourceKind) {
    Badge(
        imageVector = kind.icon,
        color = kind.containerColor(),
        iconColor = kind.contentColor(),
    )
}

/**
 * Row of small feature icons for lists. `null` features = not detected yet (renders nothing);
 * only [SourceFeature.inlineFeatures] are shown, at most [max].
 */
@Composable
fun SourceFeatureIcons(
    features: Set<SourceFeature>?,
    modifier: Modifier = Modifier,
    max: Int = 6,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (features.isNullOrEmpty()) return
    val shown = SourceFeature.inlineFeatures.filter { it in features }.take(max)
    if (shown.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { feature ->
            Icon(
                imageVector = feature.icon,
                contentDescription = stringResource(feature.titleRes),
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
