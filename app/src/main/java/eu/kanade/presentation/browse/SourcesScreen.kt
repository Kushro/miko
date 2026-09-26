package eu.kanade.presentation.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.domain.source.model.SourceKind
import eu.kanade.domain.source.model.SourcesDisplayMode
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.browse.components.SourceGridItem
import eu.kanade.presentation.browse.components.SourceKindBadge
import eu.kanade.presentation.browse.components.icon
import eu.kanade.presentation.components.AnimatedFloatingSearchBox
import eu.kanade.presentation.components.SOURCE_SEARCH_BOX_HEIGHT
import eu.kanade.presentation.components.SourcesSearchBox
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.browse.source.SourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.source.EH_SOURCE_ID
import exh.source.EXH_SOURCE_ID
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.FastScrollLazyVerticalGrid
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.Scroller.STICKY_HEADER_KEY_PREFIX
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.secondaryItemAlpha
import tachiyomi.source.local.LocalSource
import tachiyomi.source.local.isLocal

@Composable
fun SourcesScreen(
    state: SourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source, Listing) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    // KMK -->
    @Suppress("UNUSED_PARAMETER") modifier: Modifier = Modifier,
    onChangeSearchQuery: (String?) -> Unit,
    // KMK <--
    // MIKO -->
    onClearActivePreset: () -> Unit,
    onClickManagePresets: () -> Unit,
    onSetKindFilter: (SourceKind?) -> Unit,
    onToggleGroupCollapsed: (String) -> Unit,
    onEnsureFeatures: (Long) -> Unit,
    // MIKO <--
) {
    // KMK -->
    val lazyListState = rememberLazyListState()
    // MIKO -->
    val lazyGridState = rememberLazyGridState()
    // MIKO <--

    BackHandler(enabled = !state.searchQuery.isNullOrBlank()) {
        onChangeSearchQuery("")
    }
    // KMK <--

    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        // MIKO -->
        state.searchQuery == null && state.isEmpty && state.hasActivePreset -> EmptyScreen(
            stringRes = MKMR.strings.source_preset_empty,
            modifier = Modifier.padding(contentPadding),
            actions = persistentListOf(
                EmptyScreenAction(
                    stringRes = MKMR.strings.source_preset_manage,
                    icon = Icons.Outlined.Bookmarks,
                    onClick = onClickManagePresets,
                ),
            ),
        )
        // MIKO <--
        // KMK -->
        state.searchQuery == null &&
            // KMK <--
            state.isEmpty -> EmptyScreen(
            MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        // KMK -->
        else -> Box(
            modifier = Modifier.padding(contentPadding),
        ) {
            val density = LocalDensity.current
            var searchBoxHeight by remember { mutableStateOf(SOURCE_SEARCH_BOX_HEIGHT) }

            // MIKO -->
            if (state.displayMode == SourcesDisplayMode.GRID) {
                FastScrollLazyVerticalGrid(
                    columns = GridCells.Adaptive(SOURCE_GRID_MIN_WIDTH),
                    state = lazyGridState,
                    contentPadding = PaddingValues(top = searchBoxHeight, bottom = MaterialTheme.padding.medium),
                ) {
                    sourcesGridContent(
                        state = state,
                        onClickItem = onClickItem,
                        onLongClickItem = onLongClickItem,
                        onClearActivePreset = onClearActivePreset,
                        onSetKindFilter = onSetKindFilter,
                        onToggleGroupCollapsed = onToggleGroupCollapsed,
                        onEnsureFeatures = onEnsureFeatures,
                    )
                }
            } else {
                // MIKO <--
                FastScrollLazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(top = searchBoxHeight),
                    // KMK <--
                ) {
                    // MIKO -->
                    sourcesListContent(
                        state = state,
                        onClickItem = onClickItem,
                        onClickPin = onClickPin,
                        onLongClickItem = onLongClickItem,
                        onClearActivePreset = onClearActivePreset,
                        onSetKindFilter = onSetKindFilter,
                        onToggleGroupCollapsed = onToggleGroupCollapsed,
                        onEnsureFeatures = onEnsureFeatures,
                    )
                    // MIKO <--
                }
            }

            // KMK -->
            // MIKO -->
            // The animated box only knows how to follow a LazyListState, so the grid keeps a plain
            // one pinned to the top.
            if (state.displayMode == SourcesDisplayMode.GRID) {
                SourcesSearchBox(
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = onChangeSearchQuery,
                    placeholderText = stringResource(KMR.strings.action_search_for_source),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        )
                        .align(Alignment.TopCenter),
                    onGloballyPositioned = { layoutCoordinates ->
                        searchBoxHeight = with(density) {
                            layoutCoordinates.size.height.toDp() + 2 * MaterialTheme.padding.small
                        }
                    },
                )
            } else {
                // MIKO <--
                AnimatedFloatingSearchBox(
                    listState = lazyListState,
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = onChangeSearchQuery,
                    placeholderText = stringResource(KMR.strings.action_search_for_source),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(
                            horizontal = MaterialTheme.padding.medium,
                            vertical = MaterialTheme.padding.small,
                        )
                        .align(Alignment.TopCenter),
                    onGloballyPositioned = { layoutCoordinates ->
                        searchBoxHeight = with(density) {
                            layoutCoordinates.size.height.toDp() + 2 * MaterialTheme.padding.small
                        }
                    },
                )
            }
            // KMK <--
        }
    }
}

// MIKO -->
private val SOURCE_GRID_MIN_WIDTH = 96.dp
private val GROUP_BOX_CORNER = 12.dp

/**
 * List body of the Sources tab: the active preset chip, the source-type filter chips, and then the
 * grouped sources — as rounded group boxes when the user asked for them, as sticky headers
 * otherwise (the upstream look).
 */
private fun LazyListScope.sourcesListContent(
    state: SourcesScreenModel.State,
    onClickItem: (Source, Listing) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClearActivePreset: () -> Unit,
    onSetKindFilter: (SourceKind?) -> Unit,
    onToggleGroupCollapsed: (String) -> Unit,
    onEnsureFeatures: (Long) -> Unit,
) {
    if (state.hasActivePreset) {
        item(key = "miko-active-source-preset", contentType = "active-preset") {
            ActivePresetChip(
                preset = state.activePreset,
                onClear = onClearActivePreset,
                modifier = Modifier
                    .animateItemFastScroll()
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.extraSmall,
                    ),
            )
        }
    }
    if (state.showKindFilter) {
        item(key = "miko-source-kind-filter", contentType = "kind-filter") {
            SourceKindFilterRow(
                counts = state.kindCounts,
                selected = state.kindFilter,
                onSelect = onSetKindFilter,
                modifier = Modifier.animateItemFastScroll(),
            )
        }
    }
    state.items.forEachIndexed { index, model ->
        when (model) {
            is SourceUiModel.Header -> {
                if (state.groupBoxes) {
                    item(key = "header-${model.key}", contentType = "header") {
                        SourceGroupBox(
                            firstItem = true,
                            lastItem = model.collapsed,
                            modifier = Modifier.animateItemFastScroll(),
                        ) {
                            SourceHeader(
                                header = model,
                                collapsible = state.collapsibleGroups,
                                onClick = { onToggleGroupCollapsed(model.key) },
                            )
                        }
                    }
                } else {
                    stickyHeader(
                        key = "$STICKY_HEADER_KEY_PREFIX-header-${model.key}",
                        contentType = "header",
                    ) {
                        SourceHeader(
                            header = model,
                            collapsible = state.collapsibleGroups,
                            onClick = { onToggleGroupCollapsed(model.key) },
                            modifier = Modifier
                                .animateItemFastScroll()
                                .background(MaterialTheme.colorScheme.background)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
            is SourceUiModel.Item -> {
                val firstOfBox = state.items.getOrNull(index - 1).let { it == null || it is SourceUiModel.Header }
                val lastOfBox = state.items.getOrNull(index + 1).let { it == null || it is SourceUiModel.Header }
                item(key = "source-${model.source.key()}", contentType = "item") {
                    if (state.showFeatureChips) {
                        FeatureRequest(sourceId = model.source.id, onEnsureFeatures = onEnsureFeatures)
                    }
                    val row = @Composable {
                        SourceItem(
                            source = model.source,
                            kind = model.kind.takeIf { state.showKindBadge },
                            features = model.features.takeIf { state.showFeatureChips },
                            // SY -->
                            showLatest = state.showLatest,
                            showPin = state.showPin,
                            // SY <--
                            onClickItem = onClickItem,
                            onLongClickItem = onLongClickItem,
                            onClickPin = onClickPin,
                        )
                    }
                    if (state.groupBoxes) {
                        SourceGroupBox(
                            firstItem = firstOfBox,
                            lastItem = lastOfBox,
                            modifier = Modifier.animateItemFastScroll(),
                            content = row,
                        )
                    } else {
                        Box(modifier = Modifier.animateItemFastScroll()) { row() }
                    }
                }
            }
        }
    }
}

/** Grid body: same content as [sourcesListContent], with full-width headers and no group boxes. */
private fun LazyGridScope.sourcesGridContent(
    state: SourcesScreenModel.State,
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClearActivePreset: () -> Unit,
    onSetKindFilter: (SourceKind?) -> Unit,
    onToggleGroupCollapsed: (String) -> Unit,
    onEnsureFeatures: (Long) -> Unit,
) {
    if (state.hasActivePreset) {
        item(
            key = "miko-active-source-preset",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "active-preset",
        ) {
            ActivePresetChip(
                preset = state.activePreset,
                onClear = onClearActivePreset,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.extraSmall,
                ),
            )
        }
    }
    if (state.showKindFilter) {
        item(
            key = "miko-source-kind-filter",
            span = { GridItemSpan(maxLineSpan) },
            contentType = "kind-filter",
        ) {
            SourceKindFilterRow(
                counts = state.kindCounts,
                selected = state.kindFilter,
                onSelect = onSetKindFilter,
            )
        }
    }
    state.items.forEach { model ->
        when (model) {
            is SourceUiModel.Header -> item(
                key = "header-${model.key}",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                SourceHeader(
                    header = model,
                    collapsible = state.collapsibleGroups,
                    onClick = { onToggleGroupCollapsed(model.key) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is SourceUiModel.Item -> item(
                key = "source-${model.source.key()}",
                contentType = "item",
            ) {
                if (state.showFeatureChips) {
                    FeatureRequest(sourceId = model.source.id, onEnsureFeatures = onEnsureFeatures)
                }
                SourceGridItem(
                    source = model.source,
                    kind = model.kind.takeIf { state.showKindBadge },
                    features = model.features.takeIf { state.showFeatureChips },
                    onClickItem = { onClickItem(model.source, Listing.Popular) },
                    onLongClickItem = { onLongClickItem(model.source) },
                )
            }
        }
    }
}

/**
 * Asks the screen model to detect the capabilities of a row that just became visible; detection
 * itself never happens in composition.
 */
@Composable
private fun FeatureRequest(sourceId: Long, onEnsureFeatures: (Long) -> Unit) {
    LaunchedEffect(sourceId) { onEnsureFeatures(sourceId) }
}

/** Rounded container that turns consecutive rows of one group into a single "group box". */
@Composable
private fun SourceGroupBox(
    firstItem: Boolean,
    lastItem: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = remember(firstItem, lastItem) {
        val top = if (firstItem) GROUP_BOX_CORNER else 0.dp
        val bottom = if (lastItem) GROUP_BOX_CORNER else 0.dp
        RoundedCornerShape(top, top, bottom, bottom)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = MaterialTheme.padding.small,
                end = MaterialTheme.padding.small,
                top = if (firstItem) MaterialTheme.padding.small else 0.dp,
                bottom = if (lastItem) MaterialTheme.padding.small else 0.dp,
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
    ) {
        content()
    }
}

/** Exclusive "sub-tabs" that restrict the list to one [SourceKind]. */
@Composable
private fun SourceKindFilterRow(
    counts: ImmutableMap<SourceKind, Int>,
    selected: SourceKind?,
    onSelect: (SourceKind?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.extraSmall,
            ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(text = stringResource(MKMR.strings.source_kind_all), maxLines = 1) },
        )
        counts.forEach { (kind, count) ->
            FilterChip(
                selected = selected == kind,
                onClick = { onSelect(kind.takeIf { it != selected }) },
                label = { Text(text = "${stringResource(kind.titleRes)} ($count)", maxLines = 1) },
                leadingIcon = {
                    Icon(
                        imageVector = kind.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun SourceHeaderLabel.displayName(): String {
    val context = LocalContext.current
    return when (this) {
        is SourceHeaderLabel.Language -> LocaleHelper.getSourceDisplayName(code, context)
        is SourceHeaderLabel.KindAndLanguage -> LocaleHelper.getSourceDisplayName(code, context)
        is SourceHeaderLabel.Kind -> stringResource(kind.titleRes)
        is SourceHeaderLabel.Category -> name
        SourceHeaderLabel.Pinned -> stringResource(MR.strings.pinned_sources)
        SourceHeaderLabel.LastUsed -> stringResource(MR.strings.last_used_source)
    }
}
// MIKO <--

// MIKO -->
/**
 * Indicator shown on top of the source list while a preset is active. Clicking it clears the
 * preset and brings every source back.
 */
@Composable
private fun ActivePresetChip(
    preset: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClear,
        label = {
            Text(
                text = stringResource(MKMR.strings.source_preset_active, preset),
                maxLines = 1,
            )
        },
        modifier = modifier,
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Bookmarks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(MKMR.strings.source_preset_all),
            )
        },
    )
}
// MIKO <--

// MIKO -->
/**
 * Group header: label, source-type badge, source count and — when groups are collapsible — the
 * chevron that folds the group away.
 */
@Composable
private fun SourceHeader(
    header: SourceUiModel.Header,
    collapsible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = collapsible, onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Text(
            text = header.label.displayName(),
            style = MaterialTheme.typography.header,
        )
        header.kind?.let { SourceKindBadge(kind = it) }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(MKMR.strings.sources_count, header.count),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.secondaryItemAlpha(),
        )
        if (collapsible) {
            Icon(
                imageVector = if (header.collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
// MIKO <--

@Composable
private fun SourceItem(
    source: Source,
    // SY -->
    showLatest: Boolean,
    showPin: Boolean,
    // SY <--
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    modifier: Modifier = Modifier,
    // MIKO -->
    kind: SourceKind? = null,
    features: Set<SourceFeature>? = null,
    // MIKO <--
) {
    BaseSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        // MIKO -->
        kind = kind,
        features = features,
        // MIKO <--
        action = {
            if (source.supportsLatest /* SY --> */ && showLatest /* SY <-- */) {
                TextButton(onClick = { onClickItem(source, Listing.Latest) }) {
                    Text(
                        text = stringResource(MR.strings.latest),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            // SY -->
            if (showPin) {
                SourcePinButton(
                    isPinned = Pin.Pinned in source.pin,
                    onClick = { onClickPin(source) },
                )
            }
            // SY <--
        },
    )
}

@Composable
private fun SourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = SECONDARY_ALPHA,
        )
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
fun SourceOptionsDialog(
    source: Source,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    // SY -->
    onClickSetCategories: (() -> Unit)?,
    onClickToggleDataSaver: (() -> Unit)?,
    // SY <--
    // MIKO -->
    onClickAddToNewPreset: (() -> Unit)?,
    onClickSourceInfo: (() -> Unit)?,
    // MIKO <--
    onDismiss: () -> Unit,
    // KMK -->
    onClickSettings: (() -> Unit)? = null,
    // KMK <--
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (!source.isLocal()) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY -->
                if (onClickSetCategories != null) {
                    Text(
                        text = stringResource(MR.strings.categories),
                        modifier = Modifier
                            .clickable(onClick = onClickSetCategories)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // MIKO -->
                if (onClickAddToNewPreset != null) {
                    Text(
                        text = stringResource(MKMR.strings.source_preset_add_to_new),
                        modifier = Modifier
                            .clickable(onClick = onClickAddToNewPreset)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // MIKO <--
                if (onClickToggleDataSaver != null) {
                    Text(
                        text = if (source.isExcludedFromDataSaver) {
                            stringResource(SYMR.strings.data_saver_stop_exclude)
                        } else {
                            stringResource(SYMR.strings.data_saver_exclude)
                        },
                        modifier = Modifier
                            .clickable(onClick = onClickToggleDataSaver)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY <--
                // KMK -->
                if (onClickSettings != null &&
                    source.installedExtension !== null &&
                    source.id !in listOf(LocalSource.ID, EH_SOURCE_ID, EXH_SOURCE_ID)
                ) {
                    Text(
                        text = stringResource(MR.strings.label_extension_info),
                        modifier = Modifier
                            .clickable(onClick = onClickSettings)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // KMK <--
                // MIKO -->
                if (onClickSourceInfo != null) {
                    Text(
                        text = stringResource(MKMR.strings.source_info),
                        modifier = Modifier
                            .clickable(onClick = onClickSourceInfo)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // MIKO <--
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed interface SourceUiModel {
    // MIKO -->
    /** A source row. [kind] is resolved once per emission in the screen model, never in composition. */
    data class Item(
        val source: Source,
        val kind: SourceKind,
        val features: Set<SourceFeature>?,
    ) : SourceUiModel

    /** A group header. [key] is what [SourcesScreenModel.toggleGroupCollapsed] persists. */
    data class Header(
        val key: String,
        val label: SourceHeaderLabel,
        val count: Int,
        val kind: SourceKind?,
        val collapsed: Boolean,
    ) : SourceUiModel
    // MIKO <--
}

// MIKO -->
/** What a [SourceUiModel.Header] says, resolved to a string only at draw time. */
sealed interface SourceHeaderLabel {
    data class Language(val code: String) : SourceHeaderLabel
    data class Kind(val kind: SourceKind) : SourceHeaderLabel
    data class KindAndLanguage(val kind: SourceKind, val code: String) : SourceHeaderLabel
    data class Category(val name: String) : SourceHeaderLabel
    data object Pinned : SourceHeaderLabel
    data object LastUsed : SourceHeaderLabel
}
// MIKO <--

// SY -->
@Composable
fun SourceCategoriesDialog(
    source: Source,
    categories: ImmutableList<String>,
    onClickCategories: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val newCategories = remember(source) {
        mutableStateListOf<String>().also { it += source.categories }
    }
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                categories.forEach { category ->
                    LabeledCheckbox(
                        label = category,
                        checked = category in newCategories,
                        onCheckedChange = {
                            if (it) {
                                newCategories += category
                            } else {
                                newCategories -= category
                            }
                        },
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onClickCategories(newCategories.toList()) }) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
// SY <--
