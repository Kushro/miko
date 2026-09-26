package eu.kanade.presentation.library

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.tachiyomi.ui.library.CategoryLibrarySettingsResolver
import eu.kanade.tachiyomi.ui.library.LibrarySettingsScreenModel
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.toggle
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.LibraryFilterKey
import tachiyomi.domain.category.model.LibraryFilterSettings
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroupLayer
import tachiyomi.domain.library.model.LibraryGroupSort
import tachiyomi.domain.library.model.LibraryGroupType
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.miko.MKMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.BaseSortItem
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun LibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: LibrarySettingsScreenModel,
    category: Category?,
    // SY -->
    hasCategories: Boolean,
    // SY <--
    // KMK -->
    categories: List<Category>,
    // KMK <--
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
            // SY -->
            stringResource(SYMR.strings.group),
            // SY <--
            // MIKO -->
            stringResource(MKMR.strings.action_special),
            // MIKO <--
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> FilterPage(
                    screenModel = screenModel,
                    // KMK -->
                    categories = categories,
                    // KMK <--
                    // MIKO -->
                    category = category,
                    // MIKO <--
                )
                1 -> SortPage(
                    category = category,
                    screenModel = screenModel,
                )
                2 -> DisplayPage(
                    screenModel = screenModel,
                    // MIKO -->
                    category = category,
                    // MIKO <--
                )
                // SY -->
                3 -> GroupPage(
                    screenModel = screenModel,
                    hasCategories = hasCategories,
                    // MIKO -->
                    category = category,
                    // MIKO <--
                )
                // SY <--
                // MIKO -->
                4 -> SpecialPage(
                    category = category,
                    screenModel = screenModel,
                )
                // MIKO <--
            }
        }
    }
}

@Suppress("UnusedReceiverParameter")
@Composable
private fun ColumnScope.FilterPage(
    screenModel: LibrarySettingsScreenModel,
    categories: List<Category>,
    // MIKO -->
    category: Category?,
    // MIKO <--
) {
    // MIKO --> a "special" category shows (and writes) its own filters instead of the global ones
    val specialSettings by screenModel.specialSettings.collectAsState()
    val filters = category?.let { specialSettings[it.id] }?.filters
    // MIKO <--
    val filterDownloaded by screenModel.libraryPreferences.filterDownloaded().collectAsState()
    val downloadedOnly by screenModel.preferences.downloadedOnly().collectAsState()
    val autoUpdateMangaRestrictions by screenModel.libraryPreferences.autoUpdateMangaRestrictions().collectAsState()

    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = if (downloadedOnly) {
            TriState.ENABLED_IS
        } else {
            /* MIKO --> */ filters?.downloaded ?: /* MIKO <-- */ filterDownloaded
        },
        enabled = !downloadedOnly,
        onClick = {
            screenModel.toggleFilter(
                // MIKO -->
                category,
                LibraryFilterKey.DOWNLOADED,
                // MIKO <--
                LibraryPreferences::filterDownloaded,
            )
        },
    )
    val filterUnread by screenModel.libraryPreferences.filterUnread().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = /* MIKO --> */ filters?.unread ?: /* MIKO <-- */ filterUnread,
        onClick = {
            screenModel.toggleFilter(
                // MIKO -->
                category,
                LibraryFilterKey.UNREAD,
                // MIKO <--
                LibraryPreferences::filterUnread,
            )
        },
    )
    val filterStarted by screenModel.libraryPreferences.filterStarted().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.label_started),
        state = /* MIKO --> */ filters?.started ?: /* MIKO <-- */ filterStarted,
        onClick = {
            screenModel.toggleFilter(
                // MIKO -->
                category,
                LibraryFilterKey.STARTED,
                // MIKO <--
                LibraryPreferences::filterStarted,
            )
        },
    )
    val filterBookmarked by screenModel.libraryPreferences.filterBookmarked().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = /* MIKO --> */ filters?.bookmarked ?: /* MIKO <-- */ filterBookmarked,
        onClick = {
            screenModel.toggleFilter(
                // MIKO -->
                category,
                LibraryFilterKey.BOOKMARKED,
                // MIKO <--
                LibraryPreferences::filterBookmarked,
            )
        },
    )
    val filterCompleted by screenModel.libraryPreferences.filterCompleted().collectAsState()
    TriStateItem(
        label = stringResource(MR.strings.completed),
        state = /* MIKO --> */ filters?.completed ?: /* MIKO <-- */ filterCompleted,
        onClick = {
            screenModel.toggleFilter(
                // MIKO -->
                category,
                LibraryFilterKey.COMPLETED,
                // MIKO <--
                LibraryPreferences::filterCompleted,
            )
        },
    )
    // TODO: re-enable when custom intervals are ready for stable
    if ((!isReleaseBuildType) && LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in autoUpdateMangaRestrictions) {
        val filterIntervalCustom by screenModel.libraryPreferences.filterIntervalCustom().collectAsState()
        TriStateItem(
            label = stringResource(MR.strings.action_filter_interval_custom),
            state = /* MIKO --> */ filters?.intervalCustom ?: /* MIKO <-- */ filterIntervalCustom,
            onClick = {
                screenModel.toggleFilter(
                    // MIKO -->
                    category,
                    LibraryFilterKey.INTERVAL_CUSTOM,
                    // MIKO <--
                    LibraryPreferences::filterIntervalCustom,
                )
            },
        )
    }
    // SY -->
    val filterLewd by screenModel.libraryPreferences.filterLewd().collectAsState()
    TriStateItem(
        label = stringResource(SYMR.strings.lewd),
        state = /* MIKO --> */ filters?.lewd ?: /* MIKO <-- */ filterLewd,
        onClick = {
            screenModel.toggleFilter(
                // MIKO -->
                category,
                LibraryFilterKey.LEWD,
                // MIKO <--
                LibraryPreferences::filterLewd,
            )
        },
    )
    // SY <--

    // MIKO -->
    val filterRated by screenModel.libraryPreferences.filterRated().collectAsState()
    TriStateItem(
        label = stringResource(MKMR.strings.filter_rated),
        state = filters?.rated ?: filterRated,
        onClick = {
            screenModel.toggleFilter(category, LibraryFilterKey.RATED, LibraryPreferences::filterRated)
        },
    )

    val tagNames by screenModel.tagNames.collectAsState()
    TagsFilter(
        screenModel = screenModel,
        category = category,
        filters = filters,
        tagNames = tagNames,
    )
    // MIKO <--

    // KMK -->
    CategoriesFilter(
        libraryPreferences = screenModel.libraryPreferences,
        categories = categories,
    )
    // KMK <--

    val trackers by screenModel.trackersFlow.collectAsState()
    when (trackers.size) {
        0 -> {
            // No trackers
        }
        1 -> {
            val service = trackers[0]
            val filterTracker by screenModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
            TriStateItem(
                label = stringResource(MR.strings.action_filter_tracked),
                state = /* MIKO --> */ filters?.tracking(service.id) ?: /* MIKO <-- */ filterTracker,
                onClick = { screenModel.toggleTracker(/* MIKO --> */ category, service.id /* MIKO <-- */) },
            )
        }
        else -> {
            HeadingItem(MR.strings.action_filter_tracked)
            trackers.map { service ->
                val filterTracker by screenModel.libraryPreferences.filterTracking(service.id.toInt()).collectAsState()
                TriStateItem(
                    label = service.name,
                    state = /* MIKO --> */ filters?.tracking(service.id) ?: /* MIKO <-- */ filterTracker,
                    onClick = { screenModel.toggleTracker(/* MIKO --> */ category, service.id /* MIKO <-- */) },
                )
            }
        }
    }
}

@Suppress("UnusedReceiverParameter")
@Composable
private fun ColumnScope.SortPage(
    category: Category?,
    screenModel: LibrarySettingsScreenModel,
) {
    val trackers by screenModel.trackersFlow.collectAsState()
    // SY -->
    val globalSortMode by screenModel.libraryPreferences.sortingMode().collectAsState()
    // KMK -->
    val sortingMode = if (!screenModel.ungrouped) {
        category.sort.type
    } else {
        globalSortMode.type
    }
    val sortDescending = if (!screenModel.ungrouped) {
        !category.sort.isAscending
    } else {
        !globalSortMode.isAscending
    }
    // KMK <--
    val hasSortTags by remember {
        screenModel.libraryPreferences.sortTagsForLibrary().changes()
            .map { it.isNotEmpty() }
    }.collectAsState(initial = screenModel.libraryPreferences.sortTagsForLibrary().get().isNotEmpty())
    // SY <--

    // MIKO -->
    // A "special" category keeps its sort in its own bundle instead of `Category.flags`, so the
    // rows below read [effectiveSortingMode] / [effectiveSortDescending].
    val specialSettings by screenModel.specialSettings.collectAsState()
    val specialSort = category?.let { specialSettings[it.id] }?.sortMode
    val effectiveSortingMode = specialSort?.type ?: sortingMode
    val effectiveSortDescending = specialSort?.let { !it.isAscending } ?: sortDescending
    // MIKO <--

    val options = remember(trackers.isEmpty()/* SY --> */, hasSortTags/* SY <-- */) {
        val trackerMeanPair = if (trackers.isNotEmpty()) {
            MR.strings.action_sort_tracker_score to LibrarySort.Type.TrackerMean
        } else {
            null
        }
        // SY -->
        val tagSortPair = if (hasSortTags) {
            SYMR.strings.tag_sorting to LibrarySort.Type.TagList
        } else {
            null
        }
        // SY <--
        listOfNotNull(
            MR.strings.action_sort_alpha to LibrarySort.Type.Alphabetical,
            MR.strings.action_sort_total to LibrarySort.Type.TotalChapters,
            MR.strings.action_sort_last_read to LibrarySort.Type.LastRead,
            MR.strings.action_sort_last_manga_update to LibrarySort.Type.LastUpdate,
            MR.strings.action_sort_unread_count to LibrarySort.Type.UnreadCount,
            MR.strings.action_sort_latest_chapter to LibrarySort.Type.LatestChapter,
            MR.strings.action_sort_chapter_fetch_date to LibrarySort.Type.ChapterFetchDate,
            MR.strings.action_sort_date_added to LibrarySort.Type.DateAdded,
            trackerMeanPair,
            // SY -->
            tagSortPair,
            // SY <--
            // MIKO -->
            MKMR.strings.sort_by_rating to LibrarySort.Type.Rating,
            // MIKO <--
            MR.strings.action_sort_random to LibrarySort.Type.Random,
        )
    }

    options.map { (titleRes, mode) ->
        if (mode == LibrarySort.Type.Random) {
            BaseSortItem(
                label = stringResource(titleRes),
                icon = Icons.Default.Refresh
                    .takeIf {
                        /* MIKO --> */ effectiveSortingMode /* MIKO <-- */ == LibrarySort.Type.Random
                    },
                onClick = {
                    screenModel.setSort(category, mode, LibrarySort.Direction.Ascending)
                },
            )
            return@map
        }
        SortItem(
            label = stringResource(titleRes),
            // MIKO -->
            sortDescending = effectiveSortDescending.takeIf { effectiveSortingMode == mode },
            // MIKO <--
            onClick = {
                // MIKO -->
                val isTogglingDirection = effectiveSortingMode == mode
                // MIKO <--
                val direction = when {
                    isTogglingDirection -> if (/* MIKO --> */ effectiveSortDescending /* MIKO <-- */) {
                        LibrarySort.Direction.Ascending
                    } else {
                        LibrarySort.Direction.Descending
                    }
                    // MIKO --> highest rating first the first time "My rating" is picked
                    mode == LibrarySort.Type.Rating -> LibrarySort.Direction.Descending
                    // MIKO <--
                    else -> if (/* MIKO --> */ effectiveSortDescending /* MIKO <-- */) {
                        LibrarySort.Direction.Descending
                    } else {
                        LibrarySort.Direction.Ascending
                    }
                }
                screenModel.setSort(category, mode, direction)
            },
        )
    }
}

private val displayModes = listOf(
    MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
    MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
    MR.strings.action_display_list to LibraryDisplayMode.List,
    MR.strings.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
    // KMK -->
    KMR.strings.action_display_comfortable_grid_panorama to LibraryDisplayMode.ComfortableGridPanorama,
    // KMK <--
)

@Suppress("UnusedReceiverParameter")
@Composable
private fun ColumnScope.DisplayPage(
    screenModel: LibrarySettingsScreenModel,
    // MIKO -->
    category: Category?,
    // MIKO <--
) {
    // MIKO --> display mode and columns come from the bundle when the category is "special"
    val specialSettings by screenModel.specialSettings.collectAsState()
    val special = category?.let { specialSettings[it.id] }
    // MIKO <--
    val globalDisplayMode by screenModel.libraryPreferences.displayMode().collectAsState()
    // MIKO -->
    val displayMode = special?.displayMode ?: globalDisplayMode
    // MIKO <--
    SettingsChipRow(MR.strings.action_display_mode) {
        displayModes.map { (titleRes, mode) ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { screenModel.setDisplayMode(/* MIKO --> */ category, /* MIKO <-- */ mode) },
                label = { Text(stringResource(titleRes)) },
            )
        }
    }

    if (displayMode != LibraryDisplayMode.List) {
        val configuration = LocalConfiguration.current
        // MIKO --> the orientation is needed on its own to address the right bundle field
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        // MIKO <--
        val columnPreference = remember {
            if (/* MIKO --> */ isLandscape /* MIKO <-- */) {
                screenModel.libraryPreferences.landscapeColumns()
            } else {
                screenModel.libraryPreferences.portraitColumns()
            }
        }

        val globalColumns by columnPreference.collectAsState()
        // MIKO -->
        val columns = when {
            special == null -> globalColumns
            isLandscape -> special.landscapeColumns
            else -> special.portraitColumns
        }
        // MIKO <--
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(MR.strings.pref_library_columns),
            valueString = if (columns > 0) {
                columns.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            // MIKO -->
            onChange = { screenModel.setColumns(category, isLandscape, it) },
            // MIKO <--
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    HeadingItem(MR.strings.overlay_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_download_badge),
        pref = screenModel.libraryPreferences.downloadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_unread_badge),
        pref = screenModel.libraryPreferences.unreadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_local_badge),
        pref = screenModel.libraryPreferences.localBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_language_badge),
        pref = screenModel.libraryPreferences.languageBadge(),
    )
    // KMK -->
    val showLang by screenModel.libraryPreferences.languageBadge().collectAsState()
    if (showLang) {
        CheckboxItem(
            label = stringResource(KMR.strings.action_display_language_icon),
            pref = screenModel.libraryPreferences.useLangIcon(),
        )
    }
    CheckboxItem(
        label = stringResource(KMR.strings.action_display_source_badge),
        pref = screenModel.libraryPreferences.sourceBadge(),
    )
    // KMK <--
    // MIKO -->
    CheckboxItem(
        label = stringResource(MKMR.strings.action_display_source_kind_badge),
        pref = screenModel.libraryPreferences.sourceKindBadge(),
    )
    // MIKO <--
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_continue_reading_button),
        pref = screenModel.libraryPreferences.showContinueReadingButton(),
    )

    HeadingItem(MR.strings.tabs_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_tabs),
        pref = screenModel.libraryPreferences.categoryTabs(),
    )
    // MIKO -->
    CheckboxItem(
        label = stringResource(MKMR.strings.action_display_category_dropdown),
        pref = screenModel.libraryPreferences.categoryDropdown(),
    )
    // MIKO <--
    // KMK -->
    CheckboxItem(
        label = stringResource(KMR.strings.action_show_hidden_categories),
        pref = screenModel.libraryPreferences.showHiddenCategories(),
    )
    // KMK <--
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_number_of_items),
        pref = screenModel.libraryPreferences.categoryNumberOfItems(),
    )
}

// KMK -->
private fun groupTypeStringRes(type: LibraryGroupType): StringResource {
    return when (type) {
        LibraryGroupType.SOURCE -> KMR.strings.group_by_source
        LibraryGroupType.STATUS -> KMR.strings.group_by_status
        LibraryGroupType.TRACK_STATUS -> KMR.strings.group_by_track_status
        LibraryGroupType.GENRE -> KMR.strings.group_by_genre
        LibraryGroupType.TRACKER_RATING -> KMR.strings.group_by_tracker_rating
        LibraryGroupType.TITLE_DUPLICATES -> KMR.strings.group_by_title_duplicates
        LibraryGroupType.LANGUAGE -> KMR.strings.group_by_language
        LibraryGroupType.AUTHOR -> KMR.strings.group_by_author
        LibraryGroupType.ARTIST -> KMR.strings.group_by_artist
        LibraryGroupType.READ_PROGRESS -> KMR.strings.group_by_read_progress
        LibraryGroupType.DOWNLOAD_STATE -> KMR.strings.group_by_download_state
        LibraryGroupType.DATE_ADDED -> KMR.strings.group_by_date_added
        LibraryGroupType.LAST_READ -> KMR.strings.group_by_last_read
        LibraryGroupType.LATEST_CHAPTER -> KMR.strings.group_by_latest_chapter
        LibraryGroupType.CATEGORY -> KMR.strings.group_by_category
        // MIKO -->
        LibraryGroupType.LOCAL_RATING -> MKMR.strings.group_by_rating
        LibraryGroupType.LOCAL_TAGS -> MKMR.strings.group_by_local_tags
        LibraryGroupType.RUIJI_TITLES -> MKMR.strings.group_by_ruiji_titles
        // MIKO <--
    }
}

private fun groupSortStringRes(sort: LibraryGroupSort): StringResource {
    return when (sort) {
        LibraryGroupSort.NATURAL -> KMR.strings.group_sort_natural
        LibraryGroupSort.ALPHABETICAL -> KMR.strings.group_sort_alphabetical
        LibraryGroupSort.ITEM_COUNT -> KMR.strings.group_sort_count
        LibraryGroupSort.LATEST_CHAPTER -> KMR.strings.group_sort_latest_chapter
        LibraryGroupSort.DATE_ADDED -> KMR.strings.group_sort_date_added
    }
}

/**
 * GENRE, TITLE_DUPLICATES and RUIJI_TITLES all key on the manga's genres/title in ways that
 * don't compose well, so the three are mutually exclusive as layers.
 */
private fun incompatibleGroupTypes(type: LibraryGroupType): Set<LibraryGroupType> {
    return when (type) {
        LibraryGroupType.GENRE -> setOf(LibraryGroupType.TITLE_DUPLICATES, LibraryGroupType.RUIJI_TITLES)
        LibraryGroupType.TITLE_DUPLICATES -> setOf(LibraryGroupType.GENRE, LibraryGroupType.RUIJI_TITLES)
        // MIKO -->
        LibraryGroupType.RUIJI_TITLES -> setOf(LibraryGroupType.GENRE, LibraryGroupType.TITLE_DUPLICATES)
        // MIKO <--
        else -> emptySet()
    }
}

@Suppress("UnusedReceiverParameter")
@Composable
private fun ColumnScope.GroupPage(
    screenModel: LibrarySettingsScreenModel,
    hasCategories: Boolean,
    // MIKO -->
    category: Category?,
    // MIKO <--
) {
    val trackers by screenModel.trackersFlow.collectAsState()
    // MIKO --> grouping layers and the genre minimum come from the bundle when "special"
    val specialSettings by screenModel.specialSettings.collectAsState()
    val special = category?.let { specialSettings[it.id] }
    val grouping = special?.libraryGrouping ?: screenModel.libraryGrouping
    val genreGroupMinSize = special?.genreGroupMinSize ?: screenModel.genreGroupMinSize
    val titleSimilarityThreshold = special?.titleSimilarityThreshold ?: screenModel.titleSimilarityThreshold
    // MIKO <--
    val primaryLayer = grouping.layers.getOrNull(0)
    val secondaryLayer = grouping.layers.getOrNull(1)

    if (hasCategories || screenModel.ungrouped) {
        CheckboxItem(
            label = stringResource(KMR.strings.group_ignore_categories),
            checked = screenModel.ungrouped,
            onClick = { screenModel.setUngrouped(!screenModel.ungrouped) },
        )
    }

    val availableTypes = remember(trackers.isEmpty(), hasCategories) {
        LibraryGroupType.entries.filter {
            (it != LibraryGroupType.TRACK_STATUS || trackers.isNotEmpty()) &&
                (it != LibraryGroupType.TRACKER_RATING || trackers.isNotEmpty()) &&
                (it != LibraryGroupType.CATEGORY || hasCategories)
        }
    }

    HeadingItem(KMR.strings.group_layer_primary)
    GroupTypeSelector(
        types = availableTypes,
        selected = primaryLayer?.type,
        onSelect = { type ->
            screenModel.setGroupingLayers(
                // MIKO -->
                category,
                // MIKO <--
                if (type == null) emptyList() else listOf(LibraryGroupLayer(type)),
            )
        },
    )

    if (primaryLayer != null) {
        GroupSortSelector(
            layer = primaryLayer,
            // MIKO -->
            onChange = { updated -> screenModel.setGroupingLayers(category, listOfNotNull(updated, secondaryLayer)) },
            // MIKO <--
        )

        val secondaryExcluded = setOf(primaryLayer.type) + incompatibleGroupTypes(primaryLayer.type)
        HeadingItem(KMR.strings.group_layer_secondary)
        GroupTypeSelector(
            types = availableTypes.filter { it !in secondaryExcluded },
            selected = secondaryLayer?.type,
            onSelect = { type ->
                screenModel.setGroupingLayers(
                    // MIKO -->
                    category,
                    // MIKO <--
                    listOfNotNull(primaryLayer, type?.let { LibraryGroupLayer(it) }),
                )
            },
        )

        if (secondaryLayer != null) {
            GroupSortSelector(
                layer = secondaryLayer,
                // MIKO -->
                onChange = { updated -> screenModel.setGroupingLayers(category, listOf(primaryLayer, updated)) },
                // MIKO <--
            )
        }

        if (grouping.layers.any { it.type == LibraryGroupType.GENRE }) {
            SliderItem(
                // MIKO -->
                value = genreGroupMinSize,
                valueRange = 1..10,
                label = stringResource(KMR.strings.group_genre_min_size),
                valueString = genreGroupMinSize.toString(),
                onChange = { screenModel.setGenreGroupMinSize(category, it) },
                // MIKO <--
            )
        }

        // MIKO -->
        if (grouping.layers.any { it.type == LibraryGroupType.RUIJI_TITLES }) {
            SliderItem(
                value = titleSimilarityThreshold,
                valueRange = 50..100,
                label = stringResource(MKMR.strings.group_ruiji_similarity_threshold),
                valueString = "$titleSimilarityThreshold%",
                onChange = { screenModel.setTitleSimilarityThreshold(category, it) },
            )
        }
        // MIKO <--
    }
}

@Composable
private fun GroupTypeSelector(
    types: List<LibraryGroupType>,
    selected: LibraryGroupType?,
    onSelect: (LibraryGroupType?) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(KMR.strings.group_layer_none)) },
        )
        types.fastForEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(stringResource(groupTypeStringRes(type))) },
            )
        }
    }
}

@Composable
private fun GroupSortSelector(
    layer: LibraryGroupLayer,
    onChange: (LibraryGroupLayer) -> Unit,
) {
    LibraryGroupSort.entries.forEach { sortBy ->
        SortItem(
            label = stringResource(groupSortStringRes(sortBy)),
            sortDescending = (!layer.ascending).takeIf { layer.sortBy == sortBy },
            onClick = {
                val ascending = if (layer.sortBy == sortBy) !layer.ascending else true
                onChange(layer.copy(sortBy = sortBy, ascending = ascending))
            },
        )
    }
}
// KMK <--

// KMK -->
@Composable
private fun CategoriesFilter(
    libraryPreferences: LibraryPreferences,
    categories: List<Category>,
) {
    val filterCategories by libraryPreferences.filterCategories().collectAsState()

    val filterCategoriesInclude = libraryPreferences.filterCategoriesInclude()
    val filterCategoriesExclude = libraryPreferences.filterCategoriesExclude()
    val included by filterCategoriesInclude.collectAsState()
    val excluded by filterCategoriesExclude.collectAsState()

    var showCategoriesDialog by rememberSaveable { mutableStateOf(false) }
    if (showCategoriesDialog) {
        TriStateListDialog(
            title = stringResource(MR.strings.categories),
            message = stringResource(KMR.strings.pref_library_filter_categories_details),
            items = categories,
            initialChecked = included.mapNotNull { id -> categories.find { it.id.toString() == id } },
            initialInversed = excluded.mapNotNull { id -> categories.find { it.id.toString() == id } },
            itemLabel = { it.visualName },
            onDismissRequest = { showCategoriesDialog = false },
            onValueChanged = { newIncluded, newExcluded ->
                filterCategoriesInclude.set(newIncluded.map { it.id.toString() }.toSet())
                filterCategoriesExclude.set(newExcluded.map { it.id.toString() }.toSet())
                showCategoriesDialog = false
            },
        )
    }

    Row(
        modifier = Modifier
            .clickable(onClick = { libraryPreferences.filterCategories().toggle() })
            .fillMaxWidth()
            .padding(horizontal = SettingsItemsPaddings.Horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Checkbox(
            checked = filterCategories,
            onCheckedChange = null,
        )
        Text(
            text = stringResource(MR.strings.categories),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { showCategoriesDialog = true }) {
            Text(stringResource(MR.strings.action_edit))
        }
    }
}
// KMK <--

// MIKO -->
/**
 * Local-tag twin of [CategoriesFilter]: the checkbox arms the filter, the "Edit" button opens a
 * tri-state picker over every tag in the library. Tag names are stored lowercased in the
 * preferences, which is also how [eu.kanade.tachiyomi.ui.library.matchesTagFilter] compares them.
 *
 * With no local tags anywhere the row stays visible but disabled (no edit button), so the feature
 * is discoverable without offering an empty dialog.
 */
@Composable
private fun TagsFilter(
    screenModel: LibrarySettingsScreenModel,
    category: Category?,
    /** Filters of the category when it is "special"; `null` = read/write the global preferences. */
    filters: LibraryFilterSettings?,
    tagNames: List<String>,
) {
    val libraryPreferences = screenModel.libraryPreferences
    val globalFilterTags by libraryPreferences.filterTags().collectAsState()
    val globalIncluded by libraryPreferences.filterTagsInclude().collectAsState()
    val globalExcluded by libraryPreferences.filterTagsExclude().collectAsState()

    val filterTags = filters?.tags ?: globalFilterTags
    val included = filters?.includedTags ?: globalIncluded
    val excluded = filters?.excludedTags ?: globalExcluded

    val hasTags = tagNames.isNotEmpty()

    var showTagsDialog by rememberSaveable { mutableStateOf(false) }
    if (showTagsDialog && hasTags) {
        TriStateListDialog(
            title = stringResource(MKMR.strings.filter_local_tags_pick),
            items = tagNames,
            initialChecked = tagNames.filter { it.lowercase() in included },
            initialInversed = tagNames.filter { it.lowercase() in excluded },
            itemLabel = { it },
            onDismissRequest = { showTagsDialog = false },
            onValueChanged = { newIncluded, newExcluded ->
                screenModel.setTagFilters(
                    category,
                    newIncluded.map { it.lowercase() }.toSet(),
                    newExcluded.map { it.lowercase() }.toSet(),
                )
                showTagsDialog = false
            },
        )
    }

    Row(
        modifier = Modifier
            .clickable(
                enabled = hasTags,
                onClick = { screenModel.toggleTagsFilter(category) },
            )
            .fillMaxWidth()
            .padding(horizontal = SettingsItemsPaddings.Horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Checkbox(
            checked = filterTags && hasTags,
            onCheckedChange = null,
            enabled = hasTags,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(MKMR.strings.filter_local_tags),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!hasTags) {
                Text(
                    text = stringResource(MKMR.strings.filter_local_tags_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (hasTags) {
            TextButton(onClick = { showTagsDialog = true }) {
                Text(stringResource(MR.strings.action_edit))
            }
        }
    }
}

/**
 * "Special" tab: makes the **active** category's filter, sort, display and grouping independent of
 * the global settings and of every other category.
 *
 * Turning it on snapshots what the category shows right now (so nothing changes visually) into
 * `category_library_settings`; turning it off drops the row and the category follows the global
 * settings again. The system category, the synthetic "ungrouped" page and "no category" cannot be
 * made independent — there is no stable row to key the settings on.
 */
@Composable
private fun SpecialPage(
    category: Category?,
    screenModel: LibrarySettingsScreenModel,
) {
    val context = LocalContext.current
    val specialSettings by screenModel.specialSettings.collectAsState()

    if (category == null || !CategoryLibrarySettingsResolver.canBeSpecial(category, screenModel.ungrouped)) {
        Text(
            text = stringResource(MKMR.strings.category_special_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
        )
    } else {
        val isSpecial = CategoryLibrarySettingsResolver.isSpecial(category.id, specialSettings)
        val categoryName = category.visualName

        Row(
            modifier = Modifier
                .clickable { screenModel.setSpecial(category, !isSpecial) }
                .fillMaxWidth()
                .padding(
                    horizontal = SettingsItemsPaddings.Horizontal,
                    vertical = SettingsItemsPaddings.Vertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(MKMR.strings.category_special_settings_title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(MKMR.strings.category_special_settings_summary, categoryName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = isSpecial,
                onCheckedChange = null,
            )
        }

        Text(
            text = if (isSpecial) {
                stringResource(MKMR.strings.category_special_settings_enabled_hint)
            } else {
                stringResource(MKMR.strings.category_special_settings_disabled_hint)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = SettingsItemsPaddings.Horizontal,
                vertical = SettingsItemsPaddings.Vertical,
            ),
        )

        if (isSpecial) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SettingsItemsPaddings.Horizontal),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        screenModel.resetSpecialFromGlobal(category)
                        context.toast(MKMR.strings.category_special_reset_done)
                    },
                ) {
                    Text(stringResource(MKMR.strings.category_special_reset_from_global))
                }
            }
        }
    }
}
// MIKO <--
