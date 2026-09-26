package eu.kanade.tachiyomi.ui.library

import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.TrackerManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.interactor.GetCategoryLibrarySettings
import tachiyomi.domain.category.interactor.SetCategoryLibrarySettings
import tachiyomi.domain.category.interactor.SetDisplayMode
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryLibrarySettings
import tachiyomi.domain.category.model.LibraryFilterKey
import tachiyomi.domain.category.model.LibraryFilterSettings
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroupLayer
import tachiyomi.domain.library.model.LibraryGrouping
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetMangaTags
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration.Companion.seconds

class LibrarySettingsScreenModel(
    val preferences: BasePreferences = Injekt.get(),
    val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val setDisplayMode: SetDisplayMode = Injekt.get(),
    private val setSortModeForCategory: SetSortModeForCategory = Injekt.get(),
    trackerManager: TrackerManager = Injekt.get(),
    // MIKO -->
    getMangaTags: GetMangaTags = Injekt.get(),
    getCategoryLibrarySettings: GetCategoryLibrarySettings = Injekt.get(),
    private val setCategoryLibrarySettings: SetCategoryLibrarySettings = Injekt.get(),
    // MIKO <--
) : ScreenModel {

    val trackersFlow = trackerManager.loggedInTrackersFlow()
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = trackerManager.loggedInTrackers(),
        )

    // MIKO -->
    /**
     * Every local tag in the library, deduplicated case-insensitively and ordered by how many
     * entries carry it (most used first, then alphabetically). Feeds the "My tags" filter picker.
     */
    val tagNames: StateFlow<List<String>> = getMangaTags.subscribeAll()
        .map { tags ->
            tags.groupBy { normalizeLibraryTag(it.displayName) }
                .mapNotNull { (key, group) ->
                    if (key.isEmpty()) null else group.first().displayName.trim() to group.size
                }
                .sortedWith(
                    compareByDescending<Pair<String, Int>> { it.second }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first },
                )
                .map { it.first }
        }
        .stateIn(
            scope = screenModelScope,
            started = SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds),
            initialValue = emptyList(),
        )

    /**
     * The categories with independent ("special") library settings, by category id. Eagerly
     * shared: the write helpers below read `.value` to decide where a change has to land.
     */
    val specialSettings: StateFlow<Map<Long, CategoryLibrarySettings>> =
        getCategoryLibrarySettings.subscribeAll()
            .stateIn(
                scope = screenModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )
    // MIKO <--

    // KMK -->
    val ungrouped by libraryPreferences.libraryUngrouped().asState(screenModelScope)
    val libraryGrouping by libraryPreferences.libraryGrouping().asState(screenModelScope)
    val genreGroupMinSize by libraryPreferences.libraryGenreGroupMinSize().asState(screenModelScope)
    // MIKO -->
    val titleSimilarityThreshold by libraryPreferences.libraryTitleSimilarityThreshold().asState(screenModelScope)
    // MIKO <--

    fun setUngrouped(value: Boolean) {
        screenModelScope.launchIO {
            libraryPreferences.libraryUngrouped().set(value)
        }
    }

    fun setGroupingLayers(layers: List<LibraryGroupLayer>) {
        screenModelScope.launchIO {
            libraryPreferences.libraryGrouping().set(LibraryGrouping(layers))
        }
    }

    fun setGenreGroupMinSize(size: Int) {
        screenModelScope.launchIO {
            libraryPreferences.libraryGenreGroupMinSize().set(size)
        }
    }
    // KMK <--

    // MIKO -->
    fun setTitleSimilarityThreshold(value: Int) {
        screenModelScope.launchIO {
            libraryPreferences.libraryTitleSimilarityThreshold().set(value)
        }
    }
    // MIKO <--

    fun toggleFilter(preference: (LibraryPreferences) -> Preference<TriState>) {
        preference(libraryPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleTracker(id: Int) {
        toggleFilter { libraryPreferences.filterTracking(id) }
    }

    fun setDisplayMode(mode: LibraryDisplayMode) {
        setDisplayMode.await(mode)
    }

    fun setSort(category: Category?, mode: LibrarySort.Type, direction: LibrarySort.Direction) {
        // MIKO -->
        val categoryId = specialCategoryId(category)
        if (categoryId != null) {
            updateSpecial(categoryId) { it.withSort(LibrarySort(mode, direction)) }
            return
        }
        // MIKO <--
        screenModelScope.launchIO {
            setSortModeForCategory.await(category, mode, direction)
        }
    }

    // MIKO -->
    // region "Special" categories
    //
    // Every helper below has the same shape: when [category] is special the change goes to its own
    // bundle (`category_library_settings`), otherwise it keeps the pre-existing global behaviour.

    fun isSpecial(category: Category?): Boolean =
        CategoryLibrarySettingsResolver.isSpecial(category?.id, specialSettings.value)

    /** Id of [category] when it is special, `null` otherwise (i.e. "write to the global prefs"). */
    private fun specialCategoryId(category: Category?): Long? =
        category?.id?.takeIf { CategoryLibrarySettingsResolver.isSpecial(it, specialSettings.value) }

    /** Turns the independent settings of [category] on (snapshotting the current values) or off. */
    fun setSpecial(category: Category?, enabled: Boolean) {
        val id = category?.id ?: return
        screenModelScope.launchIO {
            setCategoryLibrarySettings.await(
                id,
                if (enabled) CategoryLibrarySettingsResolver.snapshot(category, globalSettings()) else null,
            )
        }
    }

    /** "Copy the global settings again": re-snapshot without leaving the special state. */
    fun resetSpecialFromGlobal(category: Category?) {
        val id = category?.id ?: return
        screenModelScope.launchIO {
            setCategoryLibrarySettings.update(id) {
                CategoryLibrarySettingsResolver.snapshot(category, globalSettings())
            }
        }
    }

    fun toggleFilter(
        category: Category?,
        key: LibraryFilterKey,
        preference: (LibraryPreferences) -> Preference<TriState>,
    ) {
        val categoryId = specialCategoryId(category)
        if (categoryId == null) {
            toggleFilter(preference)
        } else {
            updateSpecial(categoryId) { it.copy(filters = it.filters.toggle(key)) }
        }
    }

    fun toggleTracker(category: Category?, trackerId: Long) {
        val categoryId = specialCategoryId(category)
        if (categoryId == null) {
            toggleTracker(trackerId.toInt())
        } else {
            updateSpecial(categoryId) { it.copy(filters = it.filters.toggleTracking(trackerId)) }
        }
    }

    fun toggleTagsFilter(category: Category?) {
        val categoryId = specialCategoryId(category)
        if (categoryId == null) {
            libraryPreferences.filterTags().getAndSet { !it }
        } else {
            updateSpecial(categoryId) { it.copy(filters = it.filters.copy(tags = !it.filters.tags)) }
        }
    }

    fun setTagFilters(category: Category?, included: Set<String>, excluded: Set<String>) {
        val categoryId = specialCategoryId(category)
        if (categoryId == null) {
            libraryPreferences.filterTagsInclude().set(included)
            libraryPreferences.filterTagsExclude().set(excluded)
        } else {
            updateSpecial(categoryId) {
                it.copy(filters = it.filters.copy(includedTags = included, excludedTags = excluded))
            }
        }
    }

    fun setDisplayMode(category: Category?, mode: LibraryDisplayMode) {
        val categoryId = specialCategoryId(category)
        if (categoryId == null) {
            setDisplayMode(mode)
        } else {
            updateSpecial(categoryId) { it.withDisplay(mode) }
        }
    }

    fun setColumns(category: Category?, isLandscape: Boolean, columns: Int) {
        val categoryId = specialCategoryId(category)
        if (categoryId == null) {
            if (isLandscape) {
                libraryPreferences.landscapeColumns().set(columns)
            } else {
                libraryPreferences.portraitColumns().set(columns)
            }
        } else {
            updateSpecial(categoryId) {
                if (isLandscape) it.copy(landscapeColumns = columns) else it.copy(portraitColumns = columns)
            }
        }
    }

    fun setGroupingLayers(category: Category?, layers: List<LibraryGroupLayer>) {
        val categoryId = specialCategoryId(category)
        if (categoryId == null) {
            setGroupingLayers(layers)
        } else {
            updateSpecial(categoryId) { it.withGrouping(LibraryGrouping(layers)) }
        }
    }

    fun setGenreGroupMinSize(category: Category?, size: Int) {
        val categoryId = specialCategoryId(category)
        if (categoryId == null) {
            setGenreGroupMinSize(size)
        } else {
            updateSpecial(categoryId) { it.copy(genreGroupMinSize = size) }
        }
    }

    fun setTitleSimilarityThreshold(category: Category?, value: Int) {
        val categoryId = specialCategoryId(category)
        if (categoryId == null) {
            setTitleSimilarityThreshold(value)
        } else {
            updateSpecial(categoryId) { it.copy(titleSimilarityThreshold = value) }
        }
    }

    private fun updateSpecial(categoryId: Long, transform: (CategoryLibrarySettings) -> CategoryLibrarySettings) {
        screenModelScope.launchIO {
            setCategoryLibrarySettings.update(categoryId, transform)
        }
    }

    /** The values a snapshot copies: what the category shows right now, minus its own bundle. */
    private fun globalSettings(): GlobalLibrarySettings = GlobalLibrarySettings(
        filters = LibraryFilterSettings(
            downloaded = libraryPreferences.filterDownloaded().get(),
            unread = libraryPreferences.filterUnread().get(),
            started = libraryPreferences.filterStarted().get(),
            bookmarked = libraryPreferences.filterBookmarked().get(),
            completed = libraryPreferences.filterCompleted().get(),
            intervalCustom = libraryPreferences.filterIntervalCustom().get(),
            lewd = libraryPreferences.filterLewd().get(),
            rated = libraryPreferences.filterRated().get(),
            tracking = trackersFlow.value.associate {
                it.id to libraryPreferences.filterTracking(it.id.toInt()).get()
            },
            tags = libraryPreferences.filterTags().get(),
            includedTags = libraryPreferences.filterTagsInclude().get(),
            excludedTags = libraryPreferences.filterTagsExclude().get(),
        ),
        display = libraryPreferences.displayMode().get(),
        portraitColumns = libraryPreferences.portraitColumns().get(),
        landscapeColumns = libraryPreferences.landscapeColumns().get(),
        grouping = libraryPreferences.libraryGrouping().get(),
        genreGroupMinSize = libraryPreferences.libraryGenreGroupMinSize().get(),
        titleSimilarityThreshold = libraryPreferences.libraryTitleSimilarityThreshold().get(),
    )
    // endregion
    // MIKO <--
}
