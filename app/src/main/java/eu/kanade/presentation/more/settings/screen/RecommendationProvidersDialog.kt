package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.kanade.domain.recommendation.RecommendationMode
import eu.kanade.domain.recommendation.RecommendationProviderId
import eu.kanade.domain.recommendation.RecommendationSettings
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// MIKO --> whole file is new (C14, Settings → Advanced → Suggestion system dialog)

/**
 * Settings → Advanced → "Suggestion system" dialog (C14): pick a single provider, or switch to a
 * reorderable multi-provider priority list, calqued on `ScanlatorFilterDialog`.
 */
@Composable
fun RecommendationProvidersDialog(
    settings: RecommendationSettings,
    onDismissRequest: () -> Unit,
    onConfirm: (RecommendationSettings) -> Unit,
) {
    var mode by remember(settings) { mutableStateOf(settings.mode) }
    var single by remember(settings) { mutableStateOf(settings.single) }
    val order = remember(settings) { settings.order.toMutableStateList() }
    val enabled = remember(settings) { settings.enabled.toMutableStateList() }
    var zokuhenThreshold by remember(settings) { mutableStateOf(settings.zokuhenThreshold) }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        order.add(to.index, order.removeAt(from.index))
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MKMR.strings.recommendation_dialog_title)) },
        text = {
            Column {
                // Mode switch: single system vs. prioritized multi-system.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            mode = if (mode == RecommendationMode.MULTI) {
                                RecommendationMode.SINGLE
                            } else {
                                RecommendationMode.MULTI
                            }
                        }
                        .padding(vertical = MaterialTheme.padding.small),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(MKMR.strings.recommendation_mode_multi),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(MKMR.strings.recommendation_mode_multi_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = mode == RecommendationMode.MULTI,
                        onCheckedChange = {
                            mode = if (it) RecommendationMode.MULTI else RecommendationMode.SINGLE
                        },
                    )
                }

                Text(
                    text = stringResource(
                        if (mode == RecommendationMode.SINGLE) {
                            MKMR.strings.recommendation_mode_single_hint
                        } else {
                            MKMR.strings.recommendation_priority_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = MaterialTheme.padding.small),
                )

                // Provider list, always in priority order; drag handle only usable in MULTI mode.
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(
                        items = order,
                        key = { it },
                    ) { providerId ->
                        ReorderableItem(reorderableState, key = providerId) { _ ->
                            val isMulti = mode == RecommendationMode.MULTI
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isMulti) {
                                            if (providerId in enabled) {
                                                enabled.remove(providerId)
                                            } else {
                                                enabled.add(providerId)
                                            }
                                        } else {
                                            single = providerId
                                        }
                                    }
                                    .padding(horizontal = MaterialTheme.padding.small, vertical = MaterialTheme.padding.small),
                            ) {
                                if (isMulti) {
                                    Checkbox(
                                        checked = providerId in enabled,
                                        onCheckedChange = {
                                            if (it) enabled.add(providerId) else enabled.remove(providerId)
                                        },
                                    )
                                } else {
                                    RadioButton(
                                        selected = providerId == single,
                                        onClick = { single = providerId },
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = MaterialTheme.padding.small),
                                ) {
                                    Text(
                                        text = stringResource(providerId.titleRes),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = stringResource(providerId.summaryRes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isMulti) {
                                    Icon(
                                        imageVector = Icons.Outlined.DragHandle,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(MaterialTheme.padding.small)
                                            .draggableHandle(),
                                    )
                                }
                            }
                        }
                    }
                }

                // Zokuhen's similarity threshold (C19) — only while Zokuhen would actually run
                // with the state currently shown by the dialog.
                val zokuhenActive = if (mode == RecommendationMode.SINGLE) {
                    single == RecommendationProviderId.ZOKUHEN
                } else {
                    RecommendationProviderId.ZOKUHEN in enabled
                }
                if (zokuhenActive) {
                    SliderItem(
                        value = zokuhenThreshold,
                        valueRange = RecommendationSettings.ZOKUHEN_THRESHOLD_RANGE,
                        label = stringResource(MKMR.strings.recommendation_zokuhen_threshold),
                        valueString = "$zokuhenThreshold%",
                        onChange = { zokuhenThreshold = it },
                    )
                }
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmButton = {
            FlowRow {
                TextButton(
                    onClick = {
                        val default = RecommendationSettings.default
                        mode = default.mode
                        single = default.single
                        order.clear()
                        order.addAll(default.order)
                        enabled.clear()
                        enabled.addAll(default.enabled)
                        zokuhenThreshold = default.zokuhenThreshold
                    },
                ) {
                    Text(text = stringResource(MKMR.strings.recommendation_reset))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                TextButton(
                    enabled = mode == RecommendationMode.SINGLE || enabled.isNotEmpty(),
                    onClick = {
                        onConfirm(
                            RecommendationSettings(
                                mode = mode,
                                single = single,
                                order = order.toList(),
                                enabled = enabled.toSet(),
                                zokuhenThreshold = zokuhenThreshold,
                            ).normalized(),
                        )
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    )
}

// MIKO <--
