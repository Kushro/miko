package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import eu.kanade.domain.source.model.SourcesDisplayMode
import eu.kanade.domain.source.model.SourcesGroupMode
import eu.kanade.domain.source.model.SourcesSortMode
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * MIKO — display options of the Sources tab (sorting, grouping, list vs grid), opened from the
 * toolbar. Same shape as the library's settings sheet so both feel like the same control.
 */
@Composable
fun SourcesSettingsDialog(
    onDismissRequest: () -> Unit,
    preferences: SourcePreferences,
    onSetSortMode: (SourcesSortMode, Boolean) -> Unit,
    onSetGroupMode: (SourcesGroupMode) -> Unit,
    onSetDisplayMode: (SourcesDisplayMode) -> Unit,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_sort),
            stringResource(MKMR.strings.sources_group_by),
            stringResource(MR.strings.action_display),
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> SortPage(preferences = preferences, onSetSortMode = onSetSortMode)
                1 -> GroupPage(preferences = preferences, onSetGroupMode = onSetGroupMode)
                2 -> DisplayPage(preferences = preferences, onSetDisplayMode = onSetDisplayMode)
            }
        }
    }
}

@Suppress("UnusedReceiverParameter")
@Composable
private fun ColumnScope.SortPage(
    preferences: SourcePreferences,
    onSetSortMode: (SourcesSortMode, Boolean) -> Unit,
) {
    val sortMode by preferences.sourcesTabSortMode().collectAsState()
    val descending by preferences.sourcesTabSortDescending().collectAsState()

    SourcesSortMode.entries.fastForEach { mode ->
        SortItem(
            label = stringResource(mode.titleRes),
            sortDescending = descending.takeIf { sortMode == mode },
            onClick = {
                // Tapping the active mode flips the direction; picking another one keeps it.
                onSetSortMode(mode, if (sortMode == mode) !descending else descending)
            },
        )
    }
}

@Suppress("UnusedReceiverParameter")
@Composable
private fun ColumnScope.GroupPage(
    preferences: SourcePreferences,
    onSetGroupMode: (SourcesGroupMode) -> Unit,
) {
    val groupMode by preferences.sourcesTabGroupMode().collectAsState()

    HeadingItem(MKMR.strings.sources_group_by)
    SourcesGroupMode.entries.fastForEach { mode ->
        RadioItem(
            label = stringResource(mode.titleRes),
            selected = groupMode == mode,
            onClick = { onSetGroupMode(mode) },
        )
    }

    CheckboxItem(
        label = stringResource(MKMR.strings.sources_group_boxes),
        pref = preferences.sourcesTabGroupBoxes(),
    )
    CheckboxItem(
        label = stringResource(MKMR.strings.sources_collapsible_groups),
        pref = preferences.sourcesTabCollapsibleGroups(),
    )
}

@Suppress("UnusedReceiverParameter")
@Composable
private fun ColumnScope.DisplayPage(
    preferences: SourcePreferences,
    onSetDisplayMode: (SourcesDisplayMode) -> Unit,
) {
    val displayMode by preferences.sourcesTabDisplayMode().collectAsState()

    SettingsChipRow(MR.strings.action_display_mode) {
        SourcesDisplayMode.entries.fastForEach { mode ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { onSetDisplayMode(mode) },
                label = { Text(stringResource(mode.titleRes)) },
            )
        }
    }

    CheckboxItem(
        label = stringResource(MKMR.strings.sources_show_feature_chips),
        pref = preferences.sourcesTabShowFeatureChips(),
    )
    CheckboxItem(
        label = stringResource(MKMR.strings.sources_show_kind_badge),
        pref = preferences.showSourceKindBadge(),
    )
}
