package eu.kanade.tachiyomi.ui.browse.source

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.preference.asState
import eu.kanade.domain.source.enhancement.SourceEnhancementRegistry
import eu.kanade.domain.source.interactor.CreateSourceCategory
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.GetShowLatest
import eu.kanade.domain.source.interactor.GetSourceCategories
import eu.kanade.domain.source.interactor.SetSourceCategories
import eu.kanade.domain.source.interactor.ToggleExcludeFromDataSaver
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.domain.source.model.SourceKind
import eu.kanade.domain.source.model.SourcesDisplayMode
import eu.kanade.domain.source.model.SourcesGroupMode
import eu.kanade.domain.source.model.SourcesSortMode
import eu.kanade.domain.source.model.classifySourceKind
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.domain.source.service.SourceCapabilitiesCache
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.source.service.SourcePreferences.DataSaver
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.SourceUiModel
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.kotatsu.KotatsuSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

class SourcesScreenModel(
    private val getEnabledSources: GetEnabledSources = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val toggleSourcePin: ToggleSourcePin = Injekt.get(),
    // SY -->
    private val uiPreferences: UiPreferences = Injekt.get(),
    private val getSourceCategories: GetSourceCategories = Injekt.get(),
    private val getShowLatest: GetShowLatest = Injekt.get(),
    private val toggleExcludeFromDataSaver: ToggleExcludeFromDataSaver = Injekt.get(),
    private val setSourceCategories: SetSourceCategories = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    val smartSearchConfig: SourcesScreen.SmartSearchConfig?,
    // SY <--
    // MIKO -->
    private val sourceManager: SourceManager = Injekt.get(),
    private val createSourceCategory: CreateSourceCategory = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val enhancementRegistry: SourceEnhancementRegistry = Injekt.get(),
    private val capabilitiesCache: SourceCapabilitiesCache = Injekt.get(),
    // MIKO <--
) : StateScreenModel<SourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    val useNewSourceNavigation by uiPreferences.useNewSourceNavigation().asState(screenModelScope)

    // MIKO -->
    /** Last emission of `collectLatestSources`, so [rebuildItems] can regroup without re-querying. */
    @Volatile
    private var lastSources: List<Pair<Source, SourceKind>> = emptyList()

    @Volatile
    private var lastPrefs: SourcesTabPrefs = SourcesTabPrefs()

    /** Ids whose capabilities were already asked for, so visible rows never queue twice. */
    private val requestedFeatures: MutableSet<Long> = ConcurrentHashMap.newKeySet()
    // MIKO <--

    init {
        // SY -->
        combine(
            // KMK -->
            state.map { Pair(it.searchQuery, it.nsfwOnly) }
                .distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
            // KMK <--
            getEnabledSources.subscribe(),
            // MIKO -->
            combine(
                getSourceCategories.subscribe(),
                sourcePreferences.activeSourcePreset().changes(),
            ) { categories, activePreset -> categories to activePreset },
            // MIKO <--
            getShowLatest.subscribe(smartSearchConfig != null),
            // MIKO -->
            // The typed `combine` tops out at five flows, so "show pin" travels with the view prefs.
            combine(
                flowOf(smartSearchConfig == null),
                sourcesTabPrefs(),
            ) { showPin, prefs -> showPin to prefs },
            // MIKO <--
            ::collectLatestSources,
        )
            .catch {
                logcat(LogPriority.ERROR, it)
                _events.send(Event.FailedFetchingSources)
            }
            .flowOn(Dispatchers.IO)
            .launchIn(screenModelScope)

        // MIKO -->
        capabilitiesCache.changes
            .debounce(FEATURES_DEBOUNCE_MILLIS)
            .onEach { rebuildItems() }
            .flowOn(Dispatchers.IO)
            .launchIn(screenModelScope)
        // MIKO <--

        sourcePreferences.dataSaver().changes()
            .onEach {
                mutableState.update {
                    it.copy(
                        dataSaverEnabled = sourcePreferences.dataSaver().get() != DataSaver.NONE,
                    )
                }
            }
            .launchIn(screenModelScope)
        // SY <--
    }

    private fun collectLatestSources(
        // KMK -->
        filters: Pair<String?, Boolean>,
        unfilteredSources: List<Source>,
        // sources: List<Source>,
        // KMK <--
        // MIKO -->
        categoriesAndPreset: Pair<List<String>, String>,
        // MIKO <--
        showLatest: Boolean,
        // MIKO -->
        showPinAndPrefs: Pair<Boolean, SourcesTabPrefs>,
        // MIKO <--
    ) {
        // MIKO -->
        val (categories, activePreset) = categoriesAndPreset
        val (showPin, prefs) = showPinAndPrefs
        // MIKO <--
        // KMK -->
        val searchQuery = filters.first
        val nsfwOnly = filters.second
        val queryFilter: (String?) -> ((Source) -> Boolean) = { query ->
            filter@{ source ->
                if (query.isNullOrBlank()) return@filter true
                query.split(",").any {
                    val input = it.trim()
                    if (input.isEmpty()) return@any false
                    source.installedExtension?.name?.contains(input, ignoreCase = true) == true ||
                        source.name.contains(input, ignoreCase = true) ||
                        source.id == input.toLongOrNull()
                }
            }
        }
        val sources = filterByActivePreset(unfilteredSources, activePreset)
            // MIKO -->
            .filter { source ->
                if (!nsfwOnly) {
                    true
                } else {
                    // Built-in Kotatsu parsers have no extension metadata; they carry their own NSFW flag.
                    val kotatsuSource = sourceManager.get(source.id) as? KotatsuSource
                    kotatsuSource?.isNsfw ?: (source.installedExtension?.isNsfw != false)
                }
            }
            // MIKO <--
            .filter(queryFilter(searchQuery))
        // KMK <--
        // MIKO -->
        val withKind = withSourceKinds(sources)
        val kindCounts = sourceKindCounts(withKind)
        val kindFilter = prefs.kindFilter?.takeIf { it in kindCounts }
        val filtered = if (kindFilter == null) withKind else withKind.filter { it.second == kindFilter }

        lastSources = filtered
        lastPrefs = prefs

        val features = featuresOfCached(filtered)
        mutableState.update { state ->
            state.copy(
                isLoading = false,
                items = groupAndSortSources(
                    sources = filtered,
                    features = features,
                    groupMode = prefs.effectiveGroupMode(kindFilter),
                    sortMode = prefs.sortMode,
                    descending = prefs.sortDescending,
                    collapsedKeys = prefs.collapsedGroups,
                ).toImmutableList(),
                // SY -->
                categories = categories
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                    .toImmutableList(),
                showPin = showPin,
                showLatest = showLatest,
                // SY <--
                // MIKO -->
                activePreset = activePreset,
                features = features.toImmutableMap(),
                kindCounts = kindCounts.toImmutableMap(),
                kindFilter = kindFilter,
                displayMode = prefs.displayMode,
                groupBoxes = prefs.groupBoxes,
                collapsibleGroups = prefs.collapsibleGroups,
                showFeatureChips = prefs.showFeatureChips,
                showKindBadge = prefs.showKindBadge,
                // MIKO <--
            )
        }
        // MIKO <--
    }

    // MIKO -->
    /**
     * Resolves the [SourceKind] of every source once per emission: `Source.kind` would hit Injekt
     * and the extension list per row, which does not scale to ~1300 sources.
     */
    private fun withSourceKinds(sources: List<Source>): List<Pair<Source, SourceKind>> {
        val extensionSourceIds = extensionManager.installedExtensionsFlow.value
            .flatMapTo(mutableSetOf()) { extension -> extension.sources.map { it.id } }
        val kinds = HashMap<Long, SourceKind>()
        return sources.map { source ->
            source to kinds.getOrPut(source.id) {
                val runtime = sourceManager.get(source.id)
                classifySourceKind(
                    id = source.id,
                    runtime = runtime,
                    hasInstalledExtension = source.id in extensionSourceIds,
                    hasEnhancement = runtime != null && enhancementRegistry.hasEnhancement(runtime),
                )
            }
        }
    }

    /** Capabilities already in the cache; the rest arrive through [ensureFeatures]. */
    private fun featuresOfCached(sources: List<Pair<Source, SourceKind>>): Map<Long, Set<SourceFeature>> {
        return buildMap {
            sources.forEach { (source, _) ->
                if (containsKey(source.id)) return@forEach
                capabilitiesCache.peek(source.id)?.let { put(source.id, it) }
            }
        }
    }

    /** Regroups the last emission after the capabilities cache learned something new. */
    private fun rebuildItems() {
        val sources = lastSources
        if (sources.isEmpty()) return
        val prefs = lastPrefs
        val features = featuresOfCached(sources)
        mutableState.update { state ->
            state.copy(
                items = groupAndSortSources(
                    sources = sources,
                    features = features,
                    groupMode = prefs.effectiveGroupMode(state.kindFilter),
                    sortMode = prefs.sortMode,
                    descending = prefs.sortDescending,
                    collapsedKeys = prefs.collapsedGroups,
                ).toImmutableList(),
                features = features.toImmutableMap(),
            )
        }
    }

    private fun sourcesTabPrefs(): Flow<SourcesTabPrefs> = combine(
        combine(
            sourcePreferences.sourcesTabGroupMode().changes(),
            sourcePreferences.sourcesTabSortMode().changes(),
            sourcePreferences.sourcesTabSortDescending().changes(),
            sourcePreferences.sourcesTabDisplayMode().changes(),
            sourcePreferences.sourcesTabKindFilter().changes(),
        ) { groupMode, sortMode, descending, displayMode, kindFilter ->
            SourcesTabPrefs(
                groupMode = groupMode,
                sortMode = sortMode,
                sortDescending = descending,
                displayMode = displayMode,
                kindFilter = SourceKind.entries.find { it.name == kindFilter },
            )
        },
        combine(
            sourcePreferences.sourcesTabGroupBoxes().changes(),
            sourcePreferences.sourcesTabCollapsibleGroups().changes(),
            sourcePreferences.sourcesTabCollapsedGroups().changes(),
            sourcePreferences.sourcesTabShowFeatureChips().changes(),
            sourcePreferences.showSourceKindBadge().changes(),
        ) { groupBoxes, collapsible, collapsed, featureChips, kindBadge ->
            SourcesTabPrefs(
                groupBoxes = groupBoxes,
                collapsibleGroups = collapsible,
                collapsedGroups = collapsed,
                showFeatureChips = featureChips,
                showKindBadge = kindBadge,
            )
        },
    ) { grouping, display ->
        grouping.copy(
            groupBoxes = display.groupBoxes,
            collapsibleGroups = display.collapsibleGroups,
            collapsedGroups = display.collapsedGroups,
            showFeatureChips = display.showFeatureChips,
            showKindBadge = display.showKindBadge,
        )
    }

    /** Detects the capabilities of a row that just became visible. Idempotent and off the main thread. */
    fun ensureFeatures(sourceId: Long) {
        if (capabilitiesCache.peek(sourceId) != null) return
        if (!requestedFeatures.add(sourceId)) return
        screenModelScope.launchIO { capabilitiesCache.get(sourceId) }
    }

    fun toggleGroupCollapsed(key: String) {
        val preference = sourcePreferences.sourcesTabCollapsedGroups()
        val collapsed = preference.get()
        preference.set(if (key in collapsed) collapsed - key else collapsed + key)
    }

    fun setKindFilter(kind: SourceKind?) {
        sourcePreferences.sourcesTabKindFilter().set(kind?.name.orEmpty())
    }

    fun setGroupMode(mode: SourcesGroupMode) {
        sourcePreferences.sourcesTabGroupMode().set(mode)
    }

    fun setSortMode(mode: SourcesSortMode, descending: Boolean) {
        sourcePreferences.sourcesTabSortMode().set(mode)
        sourcePreferences.sourcesTabSortDescending().set(descending)
    }

    fun setDisplayMode(mode: SourcesDisplayMode) {
        sourcePreferences.sourcesTabDisplayMode().set(mode)
    }
    // MIKO <--

    fun toggleSource(source: Source) {
        toggleSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleSourcePin.await(source)
    }

    // SY -->
    fun toggleExcludeFromDataSaver(source: Source) {
        toggleExcludeFromDataSaver.await(source)
    }

    fun setSourceCategories(source: Source, categories: List<String>) {
        setSourceCategories.await(source, categories)
    }

    fun showSourceCategoriesDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog.SourceCategories(source)) }
    }
    // SY <--

    // MIKO -->
    /**
     * Activates the given preset (a source category name). Passing an empty string, or the name of
     * the preset that is already active, clears it — matching the toggle behaviour of Kotatsu.
     */
    fun setActivePreset(preset: String) {
        val current = sourcePreferences.activeSourcePreset()
        current.set(if (preset == current.get()) "" else preset)
    }

    fun clearActivePreset() {
        sourcePreferences.activeSourcePreset().set("")
    }

    fun showCreatePresetDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog.CreatePresetForSource(source)) }
    }

    /**
     * Creates a new preset (source category) and adds [source] to it, keeping the categories the
     * source already belonged to.
     */
    fun createPresetAndAssign(source: Source, preset: String) {
        screenModelScope.launchIO {
            when (createSourceCategory.await(preset)) {
                CreateSourceCategory.Result.InvalidName -> _events.send(Event.InvalidPresetName)
                CreateSourceCategory.Result.Success -> {
                    setSourceCategories.await(source, (source.categories + preset).toList())
                    _events.send(Event.PresetCreatedAndSourceAdded)
                }
            }
        }
    }
    // MIKO <--

    fun showSourceDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog.SourceLongClick(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    // KMK -->
    fun search(query: String?) {
        mutableState.update {
            it.copy(searchQuery = query)
        }
    }

    fun toggleNsfwOnly() {
        mutableState.update {
            it.copy(nsfwOnly = !it.nsfwOnly)
        }
    }
    // KMK <--

    sealed interface Event {
        data object FailedFetchingSources : Event

        // MIKO -->
        data object PresetCreatedAndSourceAdded : Event
        data object InvalidPresetName : Event
        // MIKO <--
    }

    sealed class Dialog {
        data class SourceLongClick(val source: Source) : Dialog()
        data class SourceCategories(val source: Source) : Dialog()

        // MIKO -->
        data class CreatePresetForSource(val source: Source) : Dialog()
        // MIKO <--
    }

    @Immutable
    data class State(
        val dialog: Dialog? = null,
        val isLoading: Boolean = true,
        val items: ImmutableList<SourceUiModel> = persistentListOf(),
        // SY -->
        val categories: ImmutableList<String> = persistentListOf(),
        val showPin: Boolean = true,
        val showLatest: Boolean = false,
        val dataSaverEnabled: Boolean = false,
        // SY <--
        // KMK -->
        val searchQuery: String? = null,
        val nsfwOnly: Boolean = false,
        // KMK <--
        // MIKO -->
        val activePreset: String = "",
        val features: ImmutableMap<Long, Set<SourceFeature>> = persistentMapOf(),
        val kindCounts: ImmutableMap<SourceKind, Int> = persistentMapOf(),
        val kindFilter: SourceKind? = null,
        val displayMode: SourcesDisplayMode = SourcesDisplayMode.LIST,
        val groupBoxes: Boolean = true,
        val collapsibleGroups: Boolean = true,
        val showFeatureChips: Boolean = true,
        val showKindBadge: Boolean = true,
        // MIKO <--
    ) {
        val isEmpty = items.isEmpty()

        // MIKO -->
        val hasActivePreset = activePreset.isNotEmpty()

        /** The chip row only earns its space once there is more than one kind to pick from. */
        val showKindFilter = kindCounts.size > 1

        /** Capabilities of a source, for consumers outside the list (long-press menu, info dialog). */
        fun featuresOf(sourceId: Long): Set<SourceFeature>? = features[sourceId]
        // MIKO <--
    }

    // MIKO -->
    /**
     * Every display preference of the Sources tab, combined into one flow so the main `combine`
     * stays within its five-flow arity.
     */
    data class SourcesTabPrefs(
        val groupMode: SourcesGroupMode = SourcesGroupMode.KIND,
        val sortMode: SourcesSortMode = SourcesSortMode.NAME,
        val sortDescending: Boolean = false,
        val displayMode: SourcesDisplayMode = SourcesDisplayMode.LIST,
        val kindFilter: SourceKind? = null,
        val groupBoxes: Boolean = true,
        val collapsibleGroups: Boolean = true,
        val collapsedGroups: Set<String> = emptySet(),
        val showFeatureChips: Boolean = true,
        val showKindBadge: Boolean = true,
    ) {
        /**
         * With a kind filter active every remaining source shares the same kind, so the kind level
         * of the grouping is redundant and gets dropped.
         */
        fun effectiveGroupMode(activeKindFilter: SourceKind?): SourcesGroupMode = when {
            activeKindFilter == null -> groupMode
            groupMode == SourcesGroupMode.KIND -> SourcesGroupMode.NONE
            groupMode == SourcesGroupMode.KIND_AND_LANGUAGE -> SourcesGroupMode.LANGUAGE
            else -> groupMode
        }
    }
    // MIKO <--

    companion object {
        const val PINNED_KEY = "pinned"
        const val LAST_USED_KEY = "last_used"

        // MIKO -->
        private const val FEATURES_DEBOUNCE_MILLIS = 200L
        // MIKO <--

        // SY -->
        const val CATEGORY_KEY_PREFIX = "category-"
        // SY <--
    }
}
