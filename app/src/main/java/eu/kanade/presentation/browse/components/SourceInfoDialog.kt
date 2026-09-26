package eu.kanade.presentation.browse.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.domain.source.model.SourceKind
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

/**
 * MIKO — "Source info" dialog: what kind of source this is and which features were detected.
 * Reachable from the source long-press menu, the manga details header and the migration screens.
 *
 * @param features detected features, or null while they are still being computed.
 * @param extensionLabel e.g. "Extension: Foo (v1.4.2)"; null for non-extension sources.
 * @param parserLabel e.g. "Kotatsu parser: MANGADEX"; null for non-Kotatsu sources.
 */
@Composable
fun SourceInfoDialog(
    sourceName: String,
    sourceId: Long,
    languageLabel: String?,
    kind: SourceKind,
    features: Set<SourceFeature>?,
    extensionLabel: String?,
    parserLabel: String?,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_close))
            }
        },
        title = { Text(text = sourceName) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    SourceKindBadge(kind = kind)
                    Text(
                        text = stringResource(kind.titleRes),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    text = stringResource(kind.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.secondaryItemAlpha(),
                )
                languageLabel?.let {
                    Text(text = stringResource(MKMR.strings.source_info_language, it), style = MaterialTheme.typography.bodySmall)
                }
                extensionLabel?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                parserLabel?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = stringResource(MKMR.strings.source_info_id, sourceId.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.secondaryItemAlpha(),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.extraSmall))

                Text(
                    text = stringResource(MKMR.strings.source_features),
                    style = MaterialTheme.typography.titleSmall,
                )
                when {
                    features == null -> Text(
                        text = stringResource(MKMR.strings.source_features_loading),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.secondaryItemAlpha(),
                    )
                    features.isEmpty() -> Text(
                        text = stringResource(MKMR.strings.source_features_none),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.secondaryItemAlpha(),
                    )
                    else -> SourceFeature.entries.forEach { feature ->
                        val has = feature in features
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            Icon(
                                imageVector = if (has) Icons.Outlined.Check else Icons.Outlined.Remove,
                                contentDescription = null,
                                tint = if (has) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp),
                            )
                            Icon(
                                imageVector = feature.icon,
                                contentDescription = null,
                                tint = if (has) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(feature.titleRes),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = if (has) Modifier else Modifier.secondaryItemAlpha(),
                            )
                        }
                    }
                }
            }
        },
    )
}
