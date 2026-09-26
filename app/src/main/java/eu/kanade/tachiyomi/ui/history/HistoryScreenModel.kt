package eu.kanade.tachiyomi.ui.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import androidx.compose.ui.util.fastFilter
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.components.SourceVisibilityRow
import eu.kanade.presentation.components.buildSourceVisibilityRows
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.core.common.utils.mutate
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.mapAsCheckboxState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.history.interactor.RemoveHistory
import tachiyomi.domain.history.model.HistorySort
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.history.service.HistoryPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaWithChapterCount
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

class HistoryScreenModel(
    private val addTracks: AddTracks = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga = Injekt.get(),
    private val getHistory: GetHistory = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getNextChapters: GetNextChapters = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val removeHistory: RemoveHistory = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    private val sourceManager: SourceManager = Injekt.get(),
    // KMK -->
    private val historyPreferences: HistoryPreferences = Injekt.get(),
    // KMK <--
    // MIKO -->
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    // MIKO <--
) : StateScreenModel<HistoryScreenModel.State>(State()) {

    private val _events: Channel<Event> = Channel(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    // KMK -->
    // First and last selected index in list
    private val selectedPositions: Array<Int> = arrayOf(-1, -1)
    // KMK <--

    // MIKO -->
    /**
     * Display names of the sources, resolved once each: [sortHistory] asks for one per entry on
     * every emission. Declared before [init] because the collection it feeds starts there.
     */
    private val sourceNameCache = ConcurrentHashMap<Long, String>()
    // MIKO <--

    init {
        screenModelScope.launch {
            // KMK -->
            combine(
                // KMK <--
                state.map { it.searchQuery }
                    .distinctUntilChanged(),
                // KMK -->
                getHistoryItemPreferenceFlow()
                    .distinctUntilChanged(),
                // MIKO -->
                getScopeCategoryFlow()
                    .distinctUntilChanged(),
                getHiddenSourceIdsFlow()
                    .distinctUntilChanged(),
            ) { query, itemPreferences, scopeCategory, hiddenSourceIds ->
                HistoryQueryInputs(query, itemPreferences, scopeCategory, hiddenSourceIds)
            }
                .flatMapLatest { (query, pref, scopeCategory, hiddenSourceIds) ->
                    // MIKO <--
                    // KMK <--
                    getHistory.subscribe(
                        query ?: "",
                        // KMK -->
                        unfinishedManga = pref.filterUnfinishedManga.toBooleanOrNull(),
                        // MIKO --> "hide read" is the same query parameter, forced on
                        unfinishedChapter = if (pref.hideRead) {
                            true
                        } else {
                            pref.filterUnfinishedChapter.toBooleanOrNull()
                        },
                        // MIKO <--
                        nonLibraryEntries = pref.filterNonLibraryManga.toBooleanOrNull(),
                        // KMK <--
                        // MIKO -->
                        categoryId = scopeCategory?.id,
                        // MIKO <--
                    )
                        .distinctUntilChanged()
                        // MIKO --> the query is always `readAt DESC`; every other order is Kotlin-side.
                        // Source counts are taken from the sorted-but-unfiltered list (order doesn't
                        // change the counts) so the eye dialog can show them even while sources are hidden.
                        .map { history ->
                            val sorted = sortHistory(history, pref.sort, pref.sortDescending, ::sourceNameOf)
                            val recordCounts = sorted.groupingBy { it.coverData.sourceId }.eachCount()
                            val visible = sorted.fastFilter { it.coverData.sourceId !in hiddenSourceIds }
                            Triple(visible, recordCounts, hiddenSourceIds)
                        }
                        // MIKO <--
                        .catch { error ->
                            logcat(LogPriority.ERROR, error)
                            _events.send(Event.InternalError)
                        }
                        .flowOn(Dispatchers.IO)
                }
                .collect { (visible, recordCounts, hiddenSourceIds) ->
                    mutableState.update {
                        it.copy(
                            // KMK -->
                            isLoading = false,
                            list = visible.toImmutableList(),
                            // KMK <--
                            // MIKO -->
                            sourceRecordCounts = recordCounts,
                            hiddenSourceIds = hiddenSourceIds,
                            // MIKO <--
                        )
                    }
                }
        }

        // KMK -->
        // MIKO --> the category scope and "hide read" count as active filters too; the sort mode
        // travels along because the list layout (date headers or not) depends on it
        combine(
            getHistoryItemPreferenceFlow(),
            getScopeCategoryFlow(),
        ) { prefs, scopeCategory ->
            val hasFilter = listOf(
                prefs.filterUnfinishedManga,
                prefs.filterUnfinishedChapter,
                prefs.filterNonLibraryManga,
            )
                .any { it != TriState.DISABLED }
            (hasFilter || prefs.hideRead || scopeCategory != null) to prefs.sort
        }
            .distinctUntilChanged()
            .onEach { (hasActiveFilters, sort) ->
                mutableState.update { state ->
                    state.copy(hasActiveFilters = hasActiveFilters, sort = sort)
                }
            }
            // MIKO <--
            .launchIn(screenModelScope)
        // KMK <--

        // MIKO -->
        getScopeCategoryFlow()
            .distinctUntilChanged()
            .onEach { scopeCategory ->
                mutableState.update { state ->
                    state.copy(scopeCategory = scopeCategory)
                }
            }
            .launchIn(screenModelScope)
        // MIKO <--
    }

    // MIKO -->
    private fun sourceNameOf(sourceId: Long): String =
        sourceNameCache.getOrPut(sourceId) { sourceManager.getOrStub(sourceId).name }

    /**
     * Category the history is currently scoped to, or `null` when the scope is off, the library
     * has no real active category, or the stored id no longer resolves to an existing category.
     */
    private fun getScopeCategoryFlow(): Flow<Category?> {
        return combine(
            historyPreferences.scopeToLibraryCategory().changes(),
            libraryPreferences.activeCategoryId().changes(),
            getCategories.subscribe(),
        ) { scopeEnabled, activeCategoryId, categories ->
            if (!scopeEnabled || activeCategoryId < 0) {
                null
            } else {
                categories.firstOrNull { it.id == activeCategoryId }
            }
        }
    }

    fun disableCategoryScope() {
        historyPreferences.scopeToLibraryCategory().set(false)
    }

    /** Source ids hidden from History by the toolbar eye, resolved from the stored StringSet. */
    private fun getHiddenSourceIdsFlow(): Flow<Set<Long>> {
        return historyPreferences.hiddenSources().changes()
            .map { ids -> ids.mapNotNullTo(mutableSetOf(), String::toLongOrNull) }
    }

    /**
     * Opens the eye dialog. The [SourceVisibilityRow] list is built once here (not on every
     * recomposition) from the union of installed sources and the sources present in the
     * currently-unfiltered history, using the record counts already tracked in [State].
     */
    fun showSourceVisibilityDialog() {
        val rows = buildSourceVisibilityRows(sourceManager, state.value.sourceRecordCounts)
        mutableState.update { it.copy(dialog = Dialog.SourceVisibility(rows)) }
    }

    fun setHiddenSources(ids: Set<Long>) {
        historyPreferences.hiddenSources().set(ids.map(Long::toString).toSet())
    }

    fun saveSourceHidePreset(name: String, sourceIds: Set<Long>) {
        sourcePreferences.sourceHidePresets().set(sourcePreferences.sourceHidePresets().get().save(name, sourceIds))
    }

    fun deleteSourceHidePreset(name: String) {
        sourcePreferences.sourceHidePresets().set(sourcePreferences.sourceHidePresets().get().delete(name))
    }

    /** Soft-deletes every entry currently listed (i.e. within the active scope and filters). */
    fun removeShownFromHistory() {
        val mangaIds = state.value.list.map { it.mangaId }.distinct()
        if (mangaIds.isEmpty()) return
        screenModelScope.launchIO {
            removeHistory.awaitManga(mangaIds)
        }
        toggleSelectionMode(false)
    }

    fun showDeleteScopedDialog() {
        mutableState.update { it.copy(dialog = Dialog.DeleteScoped) }
    }
    // MIKO <--

    suspend fun getNextChapter(): Chapter? {
        return withIOContext { getNextChapters.await(onlyUnread = false).firstOrNull() }
    }

    fun getNextChapterForManga(mangaId: Long, chapterId: Long) {
        screenModelScope.launchIO {
            sendNextChapterEvent(getNextChapters.await(mangaId, chapterId, onlyUnread = false))
        }
    }

    private suspend fun sendNextChapterEvent(chapters: List<Chapter>) {
        val chapter = chapters.firstOrNull()
        _events.send(Event.OpenChapter(chapter))
    }

    // KMK -->
    fun removeFromHistory(toDelete: List<HistoryWithRelations>) {
        screenModelScope.launchIO {
            removeHistory.await(toDelete.map { it.id })
        }
        toggleSelectionMode(false)
    }

    fun removeAllFromHistory(toDelete: List<HistoryWithRelations>) {
        screenModelScope.launchIO {
            removeHistory.awaitManga(toDelete.map { it.mangaId })
        }
        toggleSelectionMode(false)
    }
    // KMK <--

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val result = removeHistory.awaitAll()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
        toggleSelectionMode(false)
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    private fun moveMangaToCategory(mangaId: Long, categories: Category?) {
        val categoryIds = listOfNotNull(categories).map { it.id }
        moveMangaToCategory(mangaId, categoryIds)
    }

    private fun moveMangaToCategory(mangaId: Long, categoryIds: List<Long>) {
        screenModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(manga.id, categories)
        if (manga.favorite) return

        screenModelScope.launchIO {
            updateManga.awaitUpdateFavorite(manga.id, true)
        }
    }

    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun addFavorite(mangaId: Long) {
        screenModelScope.launchIO {
            val manga = getManga.await(mangaId) ?: return@launchIO

            val duplicates = getDuplicateLibraryManga(manga)
            if (duplicates.isNotEmpty()) {
                mutableState.update { it.copy(dialog = Dialog.DuplicateManga(manga, duplicates)) }
                return@launchIO
            }

            addFavorite(manga)
        }
    }

    fun addFavorite(manga: Manga) {
        screenModelScope.launchIO {
            // Move to default category if applicable
            val categories = getCategories()
            val defaultCategoryId = libraryPreferences.defaultCategory().get().toLong()
            val defaultCategory = categories.find { it.id == defaultCategoryId }

            when {
                // Default category set
                defaultCategory != null -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launchIO
                    moveMangaToCategory(manga.id, defaultCategory)
                }

                // Automatic 'Default' or no categories
                defaultCategoryId == 0L || categories.isEmpty() -> {
                    val result = updateManga.awaitUpdateFavorite(manga.id, true)
                    if (!result) return@launchIO
                    moveMangaToCategory(manga.id, null)
                }

                // Choose a category
                else -> showChangeCategoryDialog(manga)
            }

            // Sync with tracking services if applicable
            addTracks.bindEnhancedTrackers(manga, sourceManager.getOrStub(manga.source))
        }
    }

    fun showMigrateDialog(target: Manga, current: Manga) {
        mutableState.update { currentState ->
            currentState.copy(dialog = Dialog.Migrate(target = target, current = current))
        }
    }

    private fun showChangeCategoryDialog(manga: Manga) {
        screenModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            mutableState.update { currentState ->
                currentState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = manga,
                        initialSelection = categories.mapAsCheckboxState { it.id in selection }.toImmutableList(),
                    ),
                )
            }
        }
    }

    // KMK -->
    data class HistorySelectionOptions(
        val selected: Boolean,
        val fromLongPress: Boolean = false,
    )

    fun toggleSelection(
        item: HistoryWithRelations,
        selectionOptions: HistorySelectionOptions,
    ) {
        val (selected, fromLongPress) = selectionOptions

        mutableState.update { state ->
            if (item.chapterId in state.selection == selected) return@update state
            val selectedIndex = state.list.indexOfFirst { it.chapterId == item.chapterId }
            if (selectedIndex < 0) return@update state

            val selection = state.selection.mutate { list ->
                state.list.run {
                    val firstSelection = list.isEmpty()
                    if (selected) list.add(item.chapterId) else list.remove(item.chapterId)

                    if (firstSelection) {
                        // Since it can go into selectionMode from toolbar (without long press), we need to set both positions here
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else if (selected && fromLongPress) {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inBetweenItem = get(it)
                            if (inBetweenItem.chapterId !in list) {
                                list.add(inBetweenItem.chapterId)
                            }
                        }
                    } else if (!fromLongPress) {
                        if (!selected) {
                            if (selectedIndex == selectedPositions[0]) {
                                selectedPositions[0] = indexOfFirst { it.chapterId in list }
                            } else if (selectedIndex == selectedPositions[1]) {
                                selectedPositions[1] = indexOfLast { it.chapterId in list }
                            }
                        } else {
                            if (selectedIndex < selectedPositions[0]) {
                                selectedPositions[0] = selectedIndex
                            } else if (selectedIndex > selectedPositions[1]) {
                                selectedPositions[1] = selectedIndex
                            }
                        }
                    }
                }
            }
            state.copy(
                selection = selection,
                selectionMode = selected || selection.isNotEmpty(),
            )
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        mutableState.update { state ->
            val selection = if (selected) {
                state.list.mapTo(mutableSetOf()) { it.chapterId }
            } else {
                emptySet()
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            state.copy(
                selection = selection,
                selectionMode = selected,
            )
        }
    }

    fun invertSelection() {
        mutableState.update { state ->
            val selection = state.selection.mutate { list ->
                state.list.forEach { item ->
                    if (!list.remove(item.chapterId)) list.add(item.chapterId)
                }
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            state.copy(selection = selection)
        }
    }

    fun toggleSelectionMode(newMode: Boolean? = null) {
        if (newMode == false || state.value.selectionMode) {
            toggleAllSelection(false)
        } else {
            mutableState.update { it.copy(selectionMode = newMode ?: !it.selectionMode) }
        }
    }
    // KMK <--

    private fun getHistoryItemPreferenceFlow(): Flow<ItemPreferences> {
        return combine(
            historyPreferences.filterUnfinishedManga().changes(),
            historyPreferences.filterUnfinishedChapter().changes(),
            historyPreferences.filterNonLibraryManga().changes(),
            // MIKO --> `combine` only types up to five flows, so the sort pair travels together
            historyPreferences.historyHideRead().changes(),
            combine(
                historyPreferences.historySort().changes(),
                historyPreferences.historySortDescending().changes(),
            ) { sort, descending -> sort to descending },
        ) { unfinishedManga, unfinishedChapter, nonLibraryManga, hideRead, (sort, sortDescending) ->
            ItemPreferences(
                filterUnfinishedManga = unfinishedManga,
                filterUnfinishedChapter = unfinishedChapter,
                filterNonLibraryManga = nonLibraryManga,
                hideRead = hideRead,
                sort = sort,
                sortDescending = sortDescending,
            )
            // MIKO <--
        }
    }

    fun showFilterDialog() {
        mutableState.update { it.copy(dialog = Dialog.FilterSheet) }
    }

    @Immutable
    private data class ItemPreferences(
        val filterUnfinishedManga: TriState,
        val filterUnfinishedChapter: TriState,
        val filterNonLibraryManga: TriState,
        // MIKO -->
        val hideRead: Boolean,
        val sort: HistorySort,
        val sortDescending: Boolean,
        // MIKO <--
    )
    // KMK <--

    // MIKO -->
    /** Inputs of the list-driving `combine` in [init]; a plain [Triple] only fits three. */
    private data class HistoryQueryInputs(
        val query: String?,
        val itemPreferences: ItemPreferences,
        val scopeCategory: Category?,
        val hiddenSourceIds: Set<Long>,
    )
    // MIKO <--

    @Immutable
    data class State(
        val searchQuery: String? = null,
        // KMK -->
        val list: ImmutableList<HistoryWithRelations> = persistentListOf(),
        val isLoading: Boolean = true,
        // KMK <--
        val dialog: Dialog? = null,
        // KMk -->
        val selection: Set<Long> = emptySet(),
        val hasActiveFilters: Boolean = false,
        val selectionMode: Boolean = false,
        // MIKO -->
        /** Library category the list is scoped to, `null` when the scope is off. */
        val scopeCategory: Category? = null,
        /** Order [list] is already in; only [HistorySort.LAST_READ] gets date headers. */
        val sort: HistorySort = HistorySort.LAST_READ,
        /** Source ids hidden by the toolbar eye; [list] is already filtered against this set. */
        val hiddenSourceIds: Set<Long> = emptySet(),
        /** Per-source record counts of the sorted-but-unfiltered list, for the eye dialog rows. */
        val sourceRecordCounts: Map<Long, Int> = emptyMap(),
        // MIKO <--
    ) {
        val selected
            get() = list.fastFilter { it.chapterId in selection }

        fun getUiModel(): List<HistoryUiModel> {
            val items = list.map { HistoryUiModel.Item(it) }
            // MIKO --> date headers only make sense while the list is ordered by date
            if (!sort.supportsDateHeaders) return items
            // MIKO <--
            return items.insertSeparators { before, after ->
                val beforeDate = before?.item?.readAt?.time?.toLocalDate()
                val afterDate = after?.item?.readAt?.time?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> HistoryUiModel.Header(afterDate)
                    // Return null to avoid adding a separator between two items.
                    else -> null
                }
            }
        }
    }
    // KMK <--

    sealed interface Dialog {
        data object DeleteAll : Dialog
        // KMK -->
        data class Delete(val histories: List<HistoryWithRelations>) : Dialog {
            constructor(history: HistoryWithRelations) : this(listOf(history))
        }
        // KMK <--
        data class DuplicateManga(val manga: Manga, val duplicates: List<MangaWithChapterCount>) : Dialog
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class Migrate(val target: Manga, val current: Manga) : Dialog
        // KMK -->
        data object FilterSheet : Dialog
        // KMK <--
        // MIKO -->
        /** "Delete history": everything, or only what the current scope/filters show. */
        data object DeleteScoped : Dialog

        /** The toolbar eye. [rows] is materialized once, at the moment the dialog is opened. */
        data class SourceVisibility(val rows: ImmutableList<SourceVisibilityRow>) : Dialog
        // MIKO <--
    }

    // KMK -->
    private fun TriState.toBooleanOrNull(): Boolean? {
        return when (this) {
            TriState.DISABLED -> null
            TriState.ENABLED_IS -> true
            TriState.ENABLED_NOT -> false
        }
    }
    // KMK <--

    sealed interface Event {
        data class OpenChapter(val chapter: Chapter?) : Event
        data object InternalError : Event
        data object HistoryCleared : Event
    }
}
