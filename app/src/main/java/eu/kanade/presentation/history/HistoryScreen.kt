package eu.kanade.presentation.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.components.HistoryItem
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel.HistorySelectionOptions
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.active
import java.time.LocalDate

@Composable
fun HistoryScreen(
    state: HistoryScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (mangaId: Long) -> Unit,
    onClickResume: (mangaId: Long, chapterId: Long) -> Unit,
    onClickFavorite: (mangaId: Long) -> Unit,
    onDialogChange: (HistoryScreenModel.Dialog?) -> Unit,
    // KMK -->
    toggleSelectionMode: () -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onHistorySelected: (HistoryWithRelations, HistorySelectionOptions) -> Unit,
    onFilterClicked: () -> Unit,
    hasActiveFilters: Boolean,
    usePanoramaCover: Boolean,
    // KMK <--
    // MIKO -->
    onClearScope: () -> Unit,
    onDeleteScopedClicked: () -> Unit,
    onSourceVisibilityClicked: () -> Unit,
    // MIKO <--
) {
    // KMK -->
    BackHandler(enabled = state.selectionMode, onBack = toggleSelectionMode)
    // KMK <--

    // MIKO -->
    val scopeCategory = state.scopeCategory
    val scopeCategoryName = if (scopeCategory != null) scopeCategory.visualName else null
    // MIKO <--

    Scaffold(
        topBar = { scrollBehavior ->
            // KMK -->
            when {
                state.selectionMode -> HistorySelectionToolbar(
                    selectedCount = state.selection.size,
                    onCancelActionMode = toggleSelectionMode,
                    onClickSelectAll = { onSelectAll(true) },
                    onClickInvertSelection = onInvertSelection,
                    onClickClearHistory = { onDialogChange(HistoryScreenModel.Dialog.Delete(state.selected)) },
                )
                // KMK <--
                else -> SearchToolbar(
                    titleContent = { AppBarTitle(stringResource(MR.strings.history)) },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = onSearchQueryChange,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                // KMK -->
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_filter),
                                    icon = Icons.Outlined.FilterList,
                                    iconTint = if (hasActiveFilters) MaterialTheme.colorScheme.active else LocalContentColor.current,
                                    onClick = onFilterClicked,
                                ),
                                // KMK <--
                                // MIKO -->
                                AppBar.Action(
                                    title = stringResource(MKMR.strings.action_source_visibility),
                                    icon = Icons.Outlined.Visibility,
                                    iconTint = if (state.hiddenSourceIds.isNotEmpty()) {
                                        MaterialTheme.colorScheme.active
                                    } else {
                                        LocalContentColor.current
                                    },
                                    onClick = onSourceVisibilityClicked,
                                ),
                                // MIKO <--
                                AppBar.Action(
                                    title = stringResource(MR.strings.pref_clear_history),
                                    // KMK -->
                                    icon = Icons.Outlined.Checklist,
                                    onClick = toggleSelectionMode,
                                    // KMK <--
                                ),
                                // MIKO -->
                                AppBar.OverflowAction(
                                    title = stringResource(MKMR.strings.history_delete_scope_title),
                                    onClick = onDeleteScopedClicked,
                                ),
                                // MIKO <--
                            ),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        state.list.let {
            // KMK -->
            if (state.isLoading) {
                // KMK <--
                LoadingScreen(Modifier.padding(contentPadding))
            } else if (it.isEmpty()) {
                val msg = if (!state.searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.information_no_recent_manga
                }
                // MIKO --> keep the scope chip reachable when the scope leaves nothing to show
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (scopeCategoryName != null) {
                        HistoryScopeChip(
                            categoryName = scopeCategoryName,
                            onClear = onClearScope,
                            modifier = Modifier.padding(
                                horizontal = MaterialTheme.padding.medium,
                                vertical = MaterialTheme.padding.extraSmall,
                            ),
                        )
                    }
                    EmptyScreen(
                        stringRes = msg,
                        modifier = Modifier.weight(1f),
                    )
                }
                // MIKO <--
            } else {
                // KMK -->
                // MIKO --> the sort mode decides whether date headers are inserted
                val uiModels = remember(state.list, state.sort) { state.getUiModel() }
                // MIKO <--
                // KMK <--
                HistoryScreenContent(
                    // KMK -->
                    state = state,
                    history = uiModels,
                    // KMK <--
                    contentPadding = contentPadding,
                    onClickCover = { history -> onClickCover(history.mangaId) },
                    onClickResume = { history -> onClickResume(history.mangaId, history.chapterId) },
                    onClickDelete = { item -> onDialogChange(HistoryScreenModel.Dialog.Delete(item)) },
                    onClickFavorite = { history -> onClickFavorite(history.mangaId) },
                    // KMK -->
                    selectionMode = state.selectionMode,
                    onHistorySelected = onHistorySelected,
                    usePanoramaCover = usePanoramaCover,
                    // KMK <--
                    // MIKO -->
                    scopeCategoryName = scopeCategoryName,
                    onClearScope = onClearScope,
                    // MIKO <--
                )
            }
        }
    }
}

@Composable
private fun HistoryScreenContent(
    // KMK -->
    state: HistoryScreenModel.State,

    history: List<HistoryUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (HistoryWithRelations) -> Unit,
    onClickResume: (HistoryWithRelations) -> Unit,
    onClickDelete: (HistoryWithRelations) -> Unit,
    onClickFavorite: (HistoryWithRelations) -> Unit,
    // KMK -->
    selectionMode: Boolean,
    onHistorySelected: (HistoryWithRelations, HistorySelectionOptions) -> Unit,
    usePanoramaCover: Boolean,
    // KMK <--
    // MIKO -->
    scopeCategoryName: String?,
    onClearScope: () -> Unit,
    // MIKO <--
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        // MIKO -->
        if (scopeCategoryName != null) {
            item(
                key = "miko-history-scope-chip",
                contentType = "scope-chip",
            ) {
                HistoryScopeChip(
                    categoryName = scopeCategoryName,
                    onClear = onClearScope,
                    modifier = Modifier
                        .animateItemFastScroll()
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.extraSmall,
                        ),
                )
            }
        }
        // MIKO <--
        items(
            items = history,
            key = { "history-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is HistoryUiModel.Header -> "header"
                    is HistoryUiModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is HistoryUiModel.Header -> {
                    ListGroupHeader(
                        modifier = Modifier.animateItemFastScroll(),
                        text = relativeDateText(item.date),
                    )
                }
                is HistoryUiModel.Item -> {
                    val value = item.item
                    // KMK -->
                    val isSelected = remember(state.selection) { value.chapterId in state.selection }
                    // KMK <--
                    HistoryItem(
                        modifier = Modifier.animateItemFastScroll(),
                        history = value,
                        onClickCover = { onClickCover(value) },
                        // KMK -->
                        onClick = {
                            when {
                                selectionMode -> onHistorySelected(
                                    item.item,
                                    HistorySelectionOptions(
                                        selected = !isSelected,
                                        fromLongPress = false,
                                    ),
                                )
                                else -> onClickResume(value)
                            }
                        },
                        onLongClick = {
                            onHistorySelected(
                                item.item,
                                HistorySelectionOptions(
                                    selected = !isSelected,
                                    fromLongPress = true,
                                ),
                            )
                        },
                        // KMK <--
                        onClickDelete = { onClickDelete(value) },
                        onClickFavorite = { onClickFavorite(value) },
                        // KMK -->
                        selected = isSelected,
                        readProgress = value.lastPageRead
                            .takeIf { !value.read && it > 0L }
                            ?.let {
                                stringResource(
                                    MR.strings.chapter_progress,
                                    it + 1,
                                )
                            },
                        hasUnread = value.unreadCount > 0,
                        usePanoramaCover = usePanoramaCover,
                        // KMK <--
                    )
                }
            }
        }
    }
}

// MIKO -->
/**
 * Indicator shown while History follows the library's active category. Clicking it turns the
 * scope off (the preference itself lives in the filter sheet).
 */
@Composable
private fun HistoryScopeChip(
    categoryName: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClear,
        label = {
            Text(
                text = stringResource(MKMR.strings.history_scope_category, categoryName),
                maxLines = 1,
            )
        },
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Label,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(MR.strings.action_reset),
            )
        },
    )
}
// MIKO <--

sealed interface HistoryUiModel {
    data class Header(val date: LocalDate) : HistoryUiModel
    // KMK -->
    data class Item(val item: HistoryWithRelations) : HistoryUiModel
    // KMK <--
}

// KMK -->
@Composable
private fun HistorySelectionToolbar(
    selectedCount: Int,
    onCancelActionMode: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    onClickClearHistory: () -> Unit,
) {
    AppBar(
        titleContent = { Text(text = "$selectedCount") },
        actions = {
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_all),
                        icon = Icons.Outlined.SelectAll,
                        onClick = onClickSelectAll,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.action_select_inverse),
                        icon = Icons.Outlined.FlipToBack,
                        onClick = onClickInvertSelection,
                    ),
                    AppBar.Action(
                        title = stringResource(MR.strings.pref_clear_history),
                        icon = Icons.Outlined.DeleteSweep,
                        onClick = onClickClearHistory,
                        enabled = selectedCount > 0,
                    ),
                ),
            )
        },
        isActionMode = true,
        onCancelActionMode = onCancelActionMode,
    )
}
// KMK <--

@PreviewLightDark
@Composable
internal fun HistoryScreenPreviews(
    @PreviewParameter(HistoryScreenModelStateProvider::class)
    historyState: HistoryScreenModel.State,
) {
    TachiyomiPreviewTheme {
        HistoryScreen(
            state = historyState,
            snackbarHostState = SnackbarHostState(),
            onSearchQueryChange = {},
            onClickCover = {},
            onClickResume = { _, _ -> run {} },
            onDialogChange = {},
            onClickFavorite = {},
            // KMK -->
            toggleSelectionMode = {},
            onSelectAll = {},
            onInvertSelection = {},
            onHistorySelected = { _, _ -> },
            onFilterClicked = {},
            hasActiveFilters = true,
            usePanoramaCover = true,
            // KMK <--
            // MIKO -->
            onClearScope = {},
            onDeleteScopedClicked = {},
            onSourceVisibilityClicked = {},
            // MIKO <--
        )
    }
}
