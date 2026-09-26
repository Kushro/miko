package eu.kanade.domain.recommendation

// MIKO -->

/**
 * Process-wide memory of finished suggestion runs, keyed by (provider, source, seed url) — the
 * Kotatsu "related manga" cache, generalised to every provider.
 *
 * Why: the common flow is entry → related entry → back, and without this every "back" re-runs
 * N searches. Only complete, error-free runs are stored ([RecommendationEngine] decides), so a
 * failed provider is retried on the next visit. Bounded LRU; entries are lists of domain mangas
 * whose ids are stable for the process lifetime.
 */
class RecommendationCache(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {

    data class Key(val provider: RecommendationProviderId, val sourceId: Long, val mangaUrl: String)

    private val lru = object : LinkedHashMap<Key, List<RecommendationGroup>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, List<RecommendationGroup>>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: Key): List<RecommendationGroup>? = lru[key]

    @Synchronized
    fun put(key: Key, groups: List<RecommendationGroup>) {
        lru[key] = groups
    }

    @Synchronized
    fun clear() = lru.clear()

    companion object {
        const val DEFAULT_MAX_ENTRIES = 32
    }
}

// MIKO <--
