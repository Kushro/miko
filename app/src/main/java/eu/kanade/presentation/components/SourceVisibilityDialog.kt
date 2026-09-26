package eu.kanade.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import eu.kanade.domain.source.model.SourceHidePreset
import eu.kanade.presentation.browse.components.SourceIcon
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.domain.source.model.StubSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.domain.source.model.Source as DomainSource

// MIKO — shared "eye" dialog of History and Moments (C20): per-screen source hiding + presets.

/** One row of the eye dialog: a source and how many records of the host screen it owns. */
data class SourceVisibilityRow(
    val source: DomainSource,
    val recordCount: Int,
)

/**
 * Union of the installed sources and the sources present in the host screen's records (so a
 * source can be hidden before anything of it was ever read), ordered records-first (count
 * descending) then alphabetically. [recordCounts] maps sourceId -> number of records of the host
 * screen (callers already hold these counts; taking the map avoids materializing a multiset and
 * makes collapsing every count to 1 impossible). `getAll()` includes the ~1300 built-in Kotatsu
 * parsers — the dialog copes with that via lazy rows and the search field, never trim the list
 * here.
 */
fun buildSourceVisibilityRows(
    sourceManager: SourceManager,
    recordCounts: Map<Long, Int>,
): ImmutableList<SourceVisibilityRow> {
    val byId = LinkedHashMap<Long, SourceVisibilityRow>()
    sourceManager.getAll().forEach { source ->
        byId[source.id] = SourceVisibilityRow(source.toVisibilityRowSource(), recordCounts[source.id] ?: 0)
    }
    recordCounts.keys.forEach { id ->
        if (id !in byId) {
            byId[id] = SourceVisibilityRow(sourceManager.getOrStub(id).toVisibilityRowSource(), recordCounts[id] ?: 0)
        }
    }
    return byId.values
        .sortedWith(
            compareByDescending<SourceVisibilityRow> { it.recordCount }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.source.name },
        )
        .toImmutableList()
}

/** Minimal domain wrapper: exactly what [SourceIcon] and the row label need. */
private fun eu.kanade.tachiyomi.source.Source.toVisibilityRowSource(): DomainSource = DomainSource(
    id = id,
    lang = lang,
    name = name,
    supportsLatest = false,
    isStub = this is StubSource,
)

/**
 * The eye dialog. Checked = shown; the working copy of the hidden set only persists on OK
 * (via [onConfirm] — the caller writes its screen's preference). Preset chips toggle-apply their
 * sources over the working set (all already hidden → removed, otherwise added, D11); the chip's
 * X deletes the preset after confirmation; "save preset" snapshots the working set under a name
 * (an existing name is replaced). Preset edits persist immediately through their callbacks.
 */
@Composable
fun SourceVisibilityDialog(
    rows: ImmutableList<SourceVisibilityRow>,
    hiddenSourceIds: Set<Long>,
    presets: ImmutableList<SourceHidePreset>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
    onSavePreset: (String, Set<Long>) -> Unit,
    onDeletePreset: (String) -> Unit,
) {
    var hidden by remember { mutableStateOf(hiddenSourceIds) }
    var query by remember { mutableStateOf("") }
    var savingPreset by remember { mutableStateOf(false) }
    var deletingPreset by remember { mutableStateOf<String?>(null) }

    val filteredRows = remember(rows, query) {
        if (query.isBlank()) {
            rows
        } else {
            rows.filter { it.source.name.contains(query, ignoreCase = true) }.toImmutableList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MKMR.strings.source_visibility_title)) },
        text = {
            Column {
                if (presets.isNotEmpty()) {
                    Text(
                        text = stringResource(MKMR.strings.source_visibility_presets),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        presets.forEach { preset ->
                            val applied = preset.sourceIds.isNotEmpty() && hidden.containsAll(preset.sourceIds)
                            FilterChip(
                                selected = applied,
                                onClick = {
                                    hidden = if (applied) hidden - preset.sourceIds else hidden + preset.sourceIds
                                },
                                label = { Text(text = preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(MR.strings.action_delete),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { deletingPreset = preset.name },
                                    )
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(text = stringResource(MKMR.strings.source_visibility_search_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = MaterialTheme.padding.small),
                )
                Box {
                    val state = rememberLazyListState()
                    LazyColumn(
                        state = state,
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        items(
                            items = filteredRows,
                            contentType = { "source" },
                            key = { it.source.id },
                        ) { row ->
                            val shown = row.source.id !in hidden
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        hidden = if (shown) hidden + row.source.id else hidden - row.source.id
                                    }
                                    .minimumInteractiveComponentSize()
                                    .clip(MaterialTheme.shapes.small)
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.small),
                            ) {
                                SourceIcon(source = row.source)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = MaterialTheme.padding.small),
                                ) {
                                    Text(
                                        text = row.source.visualName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (row.recordCount > 0) {
                                            pluralStringResource(
                                                MKMR.plurals.source_visibility_records,
                                                row.recordCount,
                                                row.recordCount,
                                            )
                                        } else {
                                            stringResource(MKMR.strings.source_visibility_no_records)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Checkbox(checked = shown, onCheckedChange = null)
                            }
                        }
                    }
                    if (state.canScrollBackward) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                    if (state.canScrollForward) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = true),
        confirmButton = {
            FlowRow {
                if (hidden.isEmpty()) {
                    TextButton(onClick = { hidden = rows.map { it.source.id }.toSet() }) {
                        Text(text = stringResource(MKMR.strings.source_visibility_hide_all))
                    }
                } else {
                    TextButton(onClick = { hidden = emptySet() }) {
                        Text(text = stringResource(MKMR.strings.source_visibility_show_all))
                    }
                }
                TextButton(
                    onClick = { savingPreset = true },
                    enabled = hidden.isNotEmpty(),
                ) {
                    Text(text = stringResource(MKMR.strings.source_visibility_save_preset))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
                TextButton(
                    onClick = {
                        onConfirm(hidden)
                        onDismissRequest()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            }
        },
    )

    if (savingPreset) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { savingPreset = false },
            title = { Text(text = stringResource(MKMR.strings.source_visibility_save_preset)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(text = stringResource(MKMR.strings.source_visibility_preset_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSavePreset(name.trim(), hidden)
                        savingPreset = false
                    },
                    enabled = name.isNotBlank() && hidden.isNotEmpty(),
                ) {
                    Text(text = stringResource(MR.strings.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { savingPreset = false }) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    deletingPreset?.let { name ->
        AlertDialog(
            onDismissRequest = { deletingPreset = null },
            text = {
                Text(text = stringResource(MKMR.strings.source_visibility_delete_preset_confirm, name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePreset(name)
                        deletingPreset = null
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingPreset = null }) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
