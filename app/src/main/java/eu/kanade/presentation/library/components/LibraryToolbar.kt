package eu.kanade.presentation.library.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.RadioMenuItem
import eu.kanade.presentation.components.SearchToolbar
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.theme.active

@Composable
fun LibraryToolbar(
    hasActiveFilters: Boolean,
    selectedCount: Int,
    title: LibraryToolbarTitle,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
    onClickFilter: () -> Unit,
    onClickRefresh: () -> Unit,
    onClickGlobalUpdate: () -> Unit,
    onClickOpenRandomManga: () -> Unit,
    onClickSyncNow: () -> Unit,
    // SY -->
    onClickSyncExh: (() -> Unit)?,
    isSyncEnabled: Boolean,
    // SY <--
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
    onInvalidateDownloadCache: (Context) -> Unit,
    // KMK -->
    onClickToggleGroupsCollapse: (() -> Unit)?,
    groupsCollapsed: Boolean,
    // KMK <--
    // MIKO -->
    categories: List<Category> = emptyList(),
    selectedCategoryIndex: Int = 0,
    getItemCountForCategory: (Category) -> Int? = { null },
    onSelectCategory: ((Int) -> Unit)? = null,
    showCategoryDropdown: Boolean = false,
    // MIKO <--
) = when {
    selectedCount > 0 -> LibrarySelectionToolbar(
        selectedCount = selectedCount,
        onClickUnselectAll = onClickUnselectAll,
        onClickSelectAll = onClickSelectAll,
        onClickInvertSelection = onClickInvertSelection,
    )
    else -> LibraryRegularToolbar(
        title = title,
        hasFilters = hasActiveFilters,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onClickFilter = onClickFilter,
        onClickRefresh = onClickRefresh,
        onClickGlobalUpdate = onClickGlobalUpdate,
        onClickOpenRandomManga = onClickOpenRandomManga,
        onClickSyncNow = onClickSyncNow,
        // SY -->
        onClickSyncExh = onClickSyncExh,
        isSyncEnabled = isSyncEnabled,
        // SY <--
        scrollBehavior = scrollBehavior,
        onInvalidateDownloadCache = onInvalidateDownloadCache,
        // KMK -->
        onClickToggleGroupsCollapse = onClickToggleGroupsCollapse,
        groupsCollapsed = groupsCollapsed,
        // KMK <--
        // MIKO -->
        categories = categories,
        selectedCategoryIndex = selectedCategoryIndex,
        getItemCountForCategory = getItemCountForCategory,
        onSelectCategory = onSelectCategory,
        showCategoryDropdown = showCategoryDropdown,
        // MIKO <--
    )
}

@Composable
private fun LibraryRegularToolbar(
    title: LibraryToolbarTitle,
    hasFilters: Boolean,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    onClickFilter: () -> Unit,
    onClickRefresh: () -> Unit,
    onClickGlobalUpdate: () -> Unit,
    onClickOpenRandomManga: () -> Unit,
    onClickSyncNow: () -> Unit,
    // SY -->
    onClickSyncExh: (() -> Unit)?,
    isSyncEnabled: Boolean,
    // SY <--
    scrollBehavior: TopAppBarScrollBehavior?,
    onInvalidateDownloadCache: (Context) -> Unit,
    // KMK -->
    onClickToggleGroupsCollapse: (() -> Unit)?,
    groupsCollapsed: Boolean,
    // KMK <--
    // MIKO -->
    categories: List<Category> = emptyList(),
    selectedCategoryIndex: Int = 0,
    getItemCountForCategory: (Category) -> Int? = { null },
    onSelectCategory: ((Int) -> Unit)? = null,
    showCategoryDropdown: Boolean = false,
    // MIKO <--
) {
    val context = LocalContext.current
    val pillAlpha = if (isSystemInDarkTheme()) 0.12f else 0.08f
    // MIKO -->
    val categorySelectorEnabled = showCategoryDropdown && categories.size > 1 && onSelectCategory != null
    var categoryMenuExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(categorySelectorEnabled) {
        if (!categorySelectorEnabled) categoryMenuExpanded = false
    }
    // MIKO <--
    SearchToolbar(
        titleContent = {
            // MIKO -->
            Box {
                val titleModifier = if (categorySelectorEnabled) {
                    Modifier.clickable { categoryMenuExpanded = true }
                } else {
                    Modifier
                }
                Row(
                    modifier = titleModifier,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // MIKO <--
                    Text(
                        text = title.text,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, false),
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (title.numberOfManga != null) {
                        Pill(
                            text = "${title.numberOfManga}",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = pillAlpha),
                            fontSize = 14.sp,
                        )
                    }
                    // MIKO -->
                    if (categorySelectorEnabled) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = stringResource(MR.strings.categories),
                        )
                    }
                }
                if (categorySelectorEnabled) {
                    LibraryCategoryDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                        categories = categories,
                        selectedCategoryIndex = selectedCategoryIndex,
                        getItemCountForCategory = getItemCountForCategory,
                        onSelectCategory = { index ->
                            categoryMenuExpanded = false
                            onSelectCategory?.invoke(index)
                        },
                    )
                }
            }
            // MIKO <--
        },
        searchQuery = searchQuery,
        onChangeSearchQuery = onSearchQueryChange,
        actions = {
            val filterTint = if (hasFilters) MaterialTheme.colorScheme.active else LocalContentColor.current
            AppBarActions(
                persistentListOf(
                    AppBar.Action(
                        title = stringResource(MR.strings.action_filter),
                        icon = Icons.Outlined.FilterList,
                        iconTint = filterTint,
                        onClick = onClickFilter,
                    ),
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.action_update_library),
                        onClick = onClickGlobalUpdate,
                    ),
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.action_update_category),
                        onClick = onClickRefresh,
                    ),
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.action_open_random_manga),
                        onClick = onClickOpenRandomManga,
                    ),
                    AppBar.OverflowAction(
                        title = stringResource(MR.strings.pref_invalidate_download_cache),
                        onClick = {
                            onInvalidateDownloadCache(context)
                        },
                    ),
                ).builder().apply {
                    // KMK -->
                    if (onClickToggleGroupsCollapse != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(
                                    if (groupsCollapsed) KMR.strings.group_expand_all else KMR.strings.group_collapse_all,
                                ),
                                onClick = onClickToggleGroupsCollapse,
                            ),
                        )
                    }
                    // KMK <--
                    // SY -->
                    if (onClickSyncExh != null) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(SYMR.strings.sync_favorites),
                                onClick = onClickSyncExh,
                            ),
                        )
                    }
                    if (isSyncEnabled) {
                        add(
                            AppBar.OverflowAction(
                                title = stringResource(SYMR.strings.sync_library),
                                onClick = onClickSyncNow,
                            ),
                        )
                    }
                    // SY <--
                }.build(),
            )
        },
        scrollBehavior = scrollBehavior,
    )
}

// MIKO -->
/**
 * MIKO — dropdown anchored to the library toolbar title, used to switch the active category when the
 * category tabs are replaced by the title selector (pref `display_category_dropdown`).
 */
@Composable
private fun LibraryCategoryDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    categories: List<Category>,
    selectedCategoryIndex: Int,
    getItemCountForCategory: (Category) -> Int?,
    onSelectCategory: (Int) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset.Zero,
    ) {
        categories.forEachIndexed { index, category ->
            val itemCount = getItemCountForCategory(category)
            RadioMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = category.visualName,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, false),
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (itemCount != null) {
                            Text(
                                text = "$itemCount",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = MaterialTheme.padding.small),
                            )
                        }
                    }
                },
                isChecked = index == selectedCategoryIndex,
                onClick = { onSelectCategory(index) },
            )
        }
    }
}
// MIKO <--

@Composable
private fun LibrarySelectionToolbar(
    selectedCount: Int,
    onClickUnselectAll: () -> Unit,
    onClickSelectAll: () -> Unit,
    onClickInvertSelection: () -> Unit,
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
                ),
            )
        },
        isActionMode = true,
        onCancelActionMode = onClickUnselectAll,
    )
}

@Immutable
data class LibraryToolbarTitle(
    val text: String,
    val numberOfManga: Int? = null,
)
