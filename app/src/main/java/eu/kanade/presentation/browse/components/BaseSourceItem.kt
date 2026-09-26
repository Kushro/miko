package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.domain.source.model.SourceKind
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.presentation.core.util.secondaryItemAlpha

@Composable
fun BaseSourceItem(
    source: Source,
    modifier: Modifier = Modifier,
    showLanguageInContent: Boolean = true,
    onClickItem: () -> Unit = {},
    onLongClickItem: () -> Unit = {},
    icon: @Composable RowScope.(Source) -> Unit = defaultIcon,
    action: @Composable RowScope.(Source) -> Unit = {},
    // MIKO -->
    kind: SourceKind? = null,
    features: Set<SourceFeature>? = null,
    // MIKO <--
    content: @Composable RowScope.(Source, String?, /* KMK --> */ String /* KMK <-- */) -> Unit =
        // MIKO -->
        { itemSource, langString, lang ->
            DefaultSourceItemContent(
                source = itemSource,
                sourceLangString = langString,
                lang = lang,
                kind = kind,
                features = features,
            )
        },
    // MIKO <--
) {
    val sourceLangString = LocaleHelper.getSourceDisplayName(source.lang, LocalContext.current).takeIf {
        showLanguageInContent
    }
    BaseBrowseItem(
        modifier = modifier,
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        icon = { icon.invoke(this, source) },
        action = { action.invoke(this, source) },
        content = { content.invoke(this, source, sourceLangString, /* KMK --> */ source.lang /* KMK <-- */) },
    )
}

private val defaultIcon: @Composable RowScope.(Source) -> Unit = { source ->
    SourceIcon(source = source)
}

// MIKO -->
/**
 * Default two-line row: the source name, then language, NSFW marker, the [SourceKind] badge and
 * the detected capabilities. `null` [kind]/[features] simply render nothing, which is what the
 * screens that switched those overlays off pass.
 */
@Composable
private fun RowScope.DefaultSourceItemContent(
    source: Source,
    sourceLangString: String?,
    lang: String,
    kind: SourceKind?,
    features: Set<SourceFeature>?,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = MaterialTheme.padding.medium)
            .weight(1f),
    ) {
        Text(
            text = source.name +
                // KMK -->
                (
                    source.installedExtension?.let { extension ->
                        " (${extension.name})".takeIf { extension.name != source.name }
                    } ?: ""
                    ),
            // KMK <--
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        // KMK -->
        Row(
            modifier = Modifier.secondaryItemAlpha(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            // MIKO -->
            verticalAlignment = Alignment.CenterVertically,
            // MIKO <--
        ) {
            // KMK <--
            if (sourceLangString != null) {
                Text(
                    text = /* KMK --> */ FlagEmoji.getEmojiLangFlag(lang) + " " + /* KMK <-- */
                        sourceLangString,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // KMK -->
            if (source.installedExtension?.isNsfw == true) {
                Text(
                    text = stringResource(MR.strings.ext_nsfw_short).uppercase(),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // MIKO -->
            if (kind != null) {
                SourceKindBadge(kind = kind)
            }
            SourceFeatureIcons(features = features)
            // MIKO <--
        }
        // KMK <--
    }
}
// MIKO <--
