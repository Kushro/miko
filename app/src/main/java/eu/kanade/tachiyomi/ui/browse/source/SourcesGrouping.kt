package eu.kanade.tachiyomi.ui.browse.source

import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.domain.source.model.SourceKind
import eu.kanade.domain.source.model.SourcesGroupMode
import eu.kanade.domain.source.model.SourcesSortMode
import eu.kanade.presentation.browse.SourceHeaderLabel
import eu.kanade.presentation.browse.SourceUiModel
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source

/*
 * MIKO — pure grouping and sorting of the Sources tab.
 *
 * `GetEnabledSources.subscribe()` emits a *flattened* list: the plain copy of every source, one
 * extra copy per source category it belongs to (with [Source.category] set), and a floating
 * "last used" copy. The special buckets keep the upstream order — last used, pinned, preset
 * categories — and only the remaining plain copies are grouped according to the user's
 * [SourcesGroupMode].
 */

/** Key of the "last used" group; mirrors `SourcesScreenModel.LAST_USED_KEY`. */
const val SOURCES_LAST_USED_KEY = "last_used"

/** Key of the "pinned" group; mirrors `SourcesScreenModel.PINNED_KEY`. */
const val SOURCES_PINNED_KEY = "pinned"

/** Key prefix of a preset (source category) group; mirrors `SourcesScreenModel.CATEGORY_KEY_PREFIX`. */
const val SOURCES_CATEGORY_KEY_PREFIX = "category-"

private const val KIND_KEY_PREFIX = "kind-"
private const val LANG_KEY_PREFIX = "lang-"

/** One rendered group: a header (unless [label] is null) followed by its sources. */
private class SourceGroup(
    val key: String,
    val label: SourceHeaderLabel?,
    val kind: SourceKind?,
    val sources: List<Pair<Source, SourceKind>>,
)

/**
 * Builds the flat list the Sources tab renders.
 *
 * @param sources every enabled source already filtered (search, preset, NSFW, kind), paired with
 * its [SourceKind] so the kind is computed once per emission and never in composition.
 * @param features detected capabilities per source id; a missing entry means "not detected yet".
 * @param collapsedKeys keys of the groups the user collapsed: their header is kept and their items
 * are dropped.
 */
fun groupAndSortSources(
    sources: List<Pair<Source, SourceKind>>,
    features: Map<Long, Set<SourceFeature>>,
    groupMode: SourcesGroupMode,
    sortMode: SourcesSortMode,
    descending: Boolean,
    collapsedKeys: Set<String>,
): List<SourceUiModel> {
    val comparator = sourceComparator(sortMode, descending, features)

    val categorised = sources.filter { it.first.category != null }
    val rest = sources.filter { it.first.category == null }
    val lastUsed = rest.filter { it.first.isUsedLast }
    val pinned = rest.filter { !it.first.isUsedLast && Pin.Actual in it.first.pin }
    val plain = rest.filter { !it.first.isUsedLast && Pin.Actual !in it.first.pin }

    val groups = buildList {
        if (lastUsed.isNotEmpty()) {
            add(SourceGroup(SOURCES_LAST_USED_KEY, SourceHeaderLabel.LastUsed, null, lastUsed))
        }
        if (pinned.isNotEmpty()) {
            add(SourceGroup(SOURCES_PINNED_KEY, SourceHeaderLabel.Pinned, null, pinned))
        }
        addAll(categoryGroups(categorised))
        addAll(mainGroups(plain, groupMode))
    }

    return buildList {
        groups.forEach { group ->
            val label = group.label
            val collapsed = label != null && group.key in collapsedKeys
            if (label != null) {
                add(
                    SourceUiModel.Header(
                        key = group.key,
                        label = label,
                        count = group.sources.size,
                        kind = group.kind,
                        collapsed = collapsed,
                    ),
                )
            }
            if (collapsed) return@forEach
            group.sources.sortedWith(comparator).forEach { (source, kind) ->
                add(SourceUiModel.Item(source = source, kind = kind, features = features[source.id]))
            }
        }
    }
}

/** How many distinct sources of each kind the given (unfiltered by kind) list holds. */
fun sourceKindCounts(sources: List<Pair<Source, SourceKind>>): Map<SourceKind, Int> {
    val seen = mutableMapOf<SourceKind, MutableSet<Long>>()
    sources.forEach { (source, kind) ->
        seen.getOrPut(kind) { mutableSetOf() } += source.id
    }
    return SourceKind.entries
        .filter { seen[it]?.isNotEmpty() == true }
        .associateWith { seen.getValue(it).size }
}

private fun categoryGroups(sources: List<Pair<Source, SourceKind>>): List<SourceGroup> {
    return sources
        .groupBy { it.first.category!! }
        .toSortedMap()
        .map { (name, items) ->
            SourceGroup(
                key = SOURCES_CATEGORY_KEY_PREFIX + name,
                label = SourceHeaderLabel.Category(name),
                kind = null,
                sources = items,
            )
        }
}

private fun mainGroups(
    sources: List<Pair<Source, SourceKind>>,
    groupMode: SourcesGroupMode,
): List<SourceGroup> {
    if (sources.isEmpty()) return emptyList()
    return when (groupMode) {
        SourcesGroupMode.KIND -> {
            val grouped = sources.groupBy { it.second }
            SourceKind.entries
                .filter { it in grouped }
                .map { kind ->
                    SourceGroup(
                        key = KIND_KEY_PREFIX + kind.name,
                        label = SourceHeaderLabel.Kind(kind),
                        kind = kind,
                        sources = grouped.getValue(kind),
                    )
                }
        }
        SourcesGroupMode.KIND_AND_LANGUAGE -> {
            val grouped = sources.groupBy { it.second to it.first.lang }
            grouped.keys
                .sortedWith(
                    compareBy<Pair<SourceKind, String>>(
                        { it.first.ordinal },
                        { languageOrder(it.second) },
                        { it.second },
                    ),
                )
                .map { (kind, lang) ->
                    SourceGroup(
                        key = "$KIND_KEY_PREFIX${kind.name}-$LANG_KEY_PREFIX$lang",
                        label = SourceHeaderLabel.KindAndLanguage(kind, lang),
                        kind = kind,
                        sources = grouped.getValue(kind to lang),
                    )
                }
        }
        SourcesGroupMode.LANGUAGE -> {
            val grouped = sources.groupBy { it.first.lang }
            grouped.keys
                .sortedWith(compareBy<String>({ languageOrder(it) }, { it }))
                .map { lang ->
                    SourceGroup(
                        key = LANG_KEY_PREFIX + lang,
                        label = SourceHeaderLabel.Language(lang),
                        kind = null,
                        sources = grouped.getValue(lang),
                    )
                }
        }
        SourcesGroupMode.NONE -> listOf(SourceGroup(key = "", label = null, kind = null, sources = sources))
    }
}

/** Sources without a language go last, exactly like the upstream `TreeMap` comparator. */
private fun languageOrder(lang: String): Int = if (lang.isEmpty()) 1 else 0

private fun sourceComparator(
    sortMode: SourcesSortMode,
    descending: Boolean,
    features: Map<Long, Set<SourceFeature>>,
): Comparator<Pair<Source, SourceKind>> {
    val byName = compareBy(String.CASE_INSENSITIVE_ORDER) { it: Pair<Source, SourceKind> -> it.first.name }
    return when (sortMode) {
        SourcesSortMode.NAME -> byName.maybeReversed(descending)
        SourcesSortMode.LANGUAGE -> compareBy<Pair<Source, SourceKind>> { it.first.lang }
            .then(byName)
            .maybeReversed(descending)
        SourcesSortMode.KIND -> compareBy<Pair<Source, SourceKind>> { it.second.ordinal }
            .then(byName)
            .maybeReversed(descending)
        // Sources whose capabilities are not known yet always sink to the bottom, whatever the
        // direction: they would otherwise jump around as the cache fills in.
        SourcesSortMode.FEATURES -> {
            val undetectedLast = compareBy<Pair<Source, SourceKind>> { features[it.first.id] == null }
            val byCount = if (descending) {
                compareBy<Pair<Source, SourceKind>> { features[it.first.id]?.size ?: 0 }
            } else {
                compareByDescending<Pair<Source, SourceKind>> { features[it.first.id]?.size ?: 0 }
            }
            undetectedLast.then(byCount).then(byName)
        }
    }
}

private fun <T> Comparator<T>.maybeReversed(descending: Boolean): Comparator<T> =
    if (descending) reversed() else this
