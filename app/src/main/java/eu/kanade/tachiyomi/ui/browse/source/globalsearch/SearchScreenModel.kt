package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.source.sourceIdsInPreset
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.core.common.preference.toggle
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

abstract class SearchScreenModel(
    initialState: State = State(),
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
    // MIKO -->
    private val getSourceCategories: GetSourceCategories = Injekt.get(),
    // MIKO <--
) : StateScreenModel<SearchScreenModel.State>(initialState) {

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages().get()
    private val disabledSources = sourcePreferences.disabledSources().get()
    protected val pinnedSources = sourcePreferences.pinnedSources().get()

    // MIKO -->
    private val activeSourcePreset = sourcePreferences.activeSourcePreset().get()
    private val hasActiveSourcePreset = activeSourcePreset.isNotEmpty()

    /**
     * Ids of the sources belonging to the active preset; empty when no preset is active.
     * Entries of `sourcesTabSourcesInCategories` have the shape `"<sourceId>|<category>"`.
     */
    private val sourcesInActivePreset: Set<Long> = sourceIdsInPreset(
        membership = sourcePreferences.sourcesTabSourcesInCategories().get(),
        preset = activeSourcePreset,
    )
    // MIKO <--

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null

    protected var extensionFilter: String? = null

    open val sortComparator = { map: Map<Source, SearchItemResult> ->
        compareBy<Source>(
            { (map[it] as? SearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        screenModelScope.launch {
            preferences.globalSearchFilterState().changes().collectLatest { state ->
                mutableState.update { it.copy(onlyShowHasResults = state) }
            }
        }
        // KMK -->
        screenModelScope.launch {
            // MIKO -->
            // The stored filter can name a source preset that no longer exists, so the preset list
            // travels in the same collector: a stale name falls back to `All`, resets the
            // preference and is surfaced once through `State.stalePreset`.
            combine(
                preferences.globalSearchPinnedState().changes(),
                getSourceCategories.subscribe(),
            ) { filter, presets -> filter to presets }
                .collectLatest { (filter, presets) ->
                    val stalePreset = (filter as? SourceFilter.Preset)?.name
                        ?.takeIf { name -> name !in presets }
                    if (stalePreset != null) {
                        preferences.globalSearchPinnedState().set(SourceFilter.All)
                    }
                    mutableState.update {
                        it.copy(
                            sourceFilter = if (stalePreset != null) SourceFilter.All else filter,
                            presets = presets.toImmutableList(),
                            stalePreset = stalePreset ?: it.stalePreset,
                        )
                    }
                }
            // MIKO <--
        }
        // KMK <--
    }

    @Composable
    fun getManga(initialManga: Manga): androidx.compose.runtime.State<Manga> {
        return produceState(initialValue = initialManga) {
            getManga.subscribe(initialManga.url, initialManga.source)
                .filterNotNull()
                .collectLatest { manga ->
                    value = manga
                }
        }
    }

    // MIKO -->
    /**
     * Sources reachable by the search before the [SourceFilter] chip is applied: enabled language,
     * not disabled, and inside the active source preset of the Sources tab (if any).
     */
    private fun sourcesBeforeFilter(): List<Source> {
        return sourceManager.getVisibleSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .filter { !hasActiveSourcePreset || it.id in sourcesInActivePreset }
    }

    /**
     * Same list restricted to [preset] instead of the active preset. Membership is read on demand:
     * it is only needed while filtering and changes are rare.
     */
    private fun sourcesInPreset(preset: String): List<Source> {
        val presetSourceIds = sourceIdsInPreset(
            membership = preferences.sourcesTabSourcesInCategories().get(),
            preset = preset,
        )
        return sourceManager.getVisibleSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .filter { it.id in presetSourceIds }
    }
    // MIKO <--

    open fun getEnabledSources(): List<Source> {
        // MIKO -->
        // Picking a preset inside the search *replaces* the active-preset restriction: the explicit
        // choice made here wins over the one made in the Sources tab.
        val chipPreset = (state.value.sourceFilter as? SourceFilter.Preset)?.name
        val sources = if (chipPreset != null) sourcesInPreset(chipPreset) else sourcesBeforeFilter()
        // MIKO <--
        return sources.sortedWith(
            compareBy(
                { "${it.id}" !in pinnedSources },
                { "${it.name.lowercase()} (${it.lang})" },
            ),
        )
    }

    // KMK -->
    fun hasPinnedSources(): Boolean {
        // MIKO -->
        // Computed before the chip filter: whether the "Pinned" chip is worth offering must not
        // depend on the chip that happens to be selected, or picking a preset with no pinned
        // sources in it would hide the way back to "Pinned".
        return sourcesBeforeFilter().any { "${it.id}" in pinnedSources }
        // MIKO <--
    }

    fun shouldPinnedSourcesHidden() {
        // MIKO -->
        // Only the pinned-only filter is invalidated by "there are no pinned sources"; a preset
        // selection must survive.
        if (preferences.globalSearchPinnedState().get() != SourceFilter.PinnedOnly) return
        // MIKO <--
        if (!hasPinnedSources()) {
            preferences.globalSearchPinnedState().set(SourceFilter.All)
        }
    }
    // KMK <--

    private fun getSelectedSources(): List<Source> {
        val enabledSources = getEnabledSources()

        val filter = extensionFilter
        if (filter.isNullOrEmpty()) {
            return enabledSources
        }

        // SY -->
        val filteredSourceIds = extensionManager.installedExtensionsFlow.value
            .filter { it.pkgName == filter }
            .flatMap { it.sources }
            .map { it.id }
        return enabledSources.filter { it.id in filteredSourceIds }
        // SY <--
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: SourceFilter) {
        preferences.globalSearchPinnedState().set(filter)
        // MIKO -->
        // Apply it right away instead of waiting for the preference collector: `search()` reads the
        // filter from the state, and its "same query, same filter" short-circuit would otherwise
        // drop the very search this tap asked for. The collector later sets the same value.
        mutableState.update { it.copy(sourceFilter = filter) }
        // MIKO <--
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState().toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return

        val sameQuery = this.lastQuery == query
        if (sameQuery && this.lastSourceFilter == sourceFilter) return

        this.lastQuery = query
        this.lastSourceFilter = sourceFilter

        searchJob?.cancel()

        val sources = getSelectedSources()

        // Reuse previous results if possible
        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        } else {
            updateItems(
                sources
                    .associateWith { SearchItemResult.Loading }
                    .toPersistentMap(),
            )
        }

        searchJob = ioCoroutineScope.launch {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is SearchItemResult.Loading) {
                        return@async
                    }

                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchManga(1, query.sanitize(), source.getFilterList())
                        }

                        val titles = page.mangas
                            .map { it.toDomainManga(source.id) }
                            .distinctBy { it.url }
                            .let { networkToLocalManga(it) }

                        if (isActive) {
                            updateItem(source, SearchItemResult.Success(titles))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, SearchItemResult.Error(e))
                        }
                    }
                }
            }
                .awaitAll()
        }
    }

    private fun updateItems(items: Map<Source, SearchItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: Source, result: SearchItemResult) {
        updateItems(state.value.items + (source to result))
    }

    fun setMigrateDialog(currentId: Long, target: Manga) {
        screenModelScope.launchIO {
            val current = getManga.await(currentId) ?: return@launchIO
            mutableState.update { it.copy(dialog = Dialog.Migrate(target, current)) }
        }
    }

    fun clearDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    // MIKO -->
    /** Consumes the one-shot "the selected preset is gone" notice. */
    fun dismissStalePreset() {
        mutableState.update { it.copy(stalePreset = null) }
    }
    // MIKO <--

    @Immutable
    data class State(
        val from: Manga? = null,
        val searchQuery: String? = null,
        val sourceFilter: SourceFilter = SourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: PersistentMap<Source, SearchItemResult> = persistentMapOf(),
        val dialog: Dialog? = null,
        // MIKO -->
        /** Source presets that can be picked as a filter, sorted case-insensitively. */
        val presets: ImmutableList<String> = persistentListOf(),
        /** Name of a selected preset that no longer exists; shown once as a snackbar. */
        val stalePreset: String? = null,
        // MIKO <--
    ) {
        val progress: Int = items.count { it.value !is SearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
            .toImmutableMap()
    }

    sealed interface Dialog {
        data class Migrate(val target: Manga, val current: Manga) : Dialog
    }
}

sealed interface SourceFilter {

    data object All : SourceFilter

    data object PinnedOnly : SourceFilter

    // MIKO -->
    /**
     * Restrict the search to the sources of one source preset (i.e. one source category).
     *
     * This *replaces* the active-preset restriction of the Sources tab while it is selected.
     */
    data class Preset(val name: String) : SourceFilter

    /**
     * String form stored in `SourcePreferences.globalSearchPinnedState()`.
     *
     * `"All"` / `"PinnedOnly"` are byte-compatible with what `getEnum` used to write, so the
     * preference survives the enum → sealed interface change without a migration. Anything else
     * that is not a `"PRESET:<name>"` entry (including a preset with an empty name, which cannot
     * exist) falls back to the default, [PinnedOnly].
     */
    object Serializer {

        private const val ALL = "All"
        private const val PINNED_ONLY = "PinnedOnly"
        private const val PRESET_PREFIX = "PRESET:"

        fun serialize(value: SourceFilter): String = when (value) {
            SourceFilter.All -> ALL
            SourceFilter.PinnedOnly -> PINNED_ONLY
            is SourceFilter.Preset -> PRESET_PREFIX + value.name
        }

        fun deserialize(serialized: String): SourceFilter = when {
            serialized == ALL -> SourceFilter.All
            serialized == PINNED_ONLY -> SourceFilter.PinnedOnly
            serialized.startsWith(PRESET_PREFIX) -> {
                val name = serialized.removePrefix(PRESET_PREFIX)
                if (name.isEmpty()) SourceFilter.PinnedOnly else SourceFilter.Preset(name)
            }
            else -> SourceFilter.PinnedOnly
        }
    }
    // MIKO <--
}

sealed interface SearchItemResult {
    data object Loading : SearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : SearchItemResult

    data class Success(
        val result: List<Manga>,
    ) : SearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !this.isEmpty)
    }
}
