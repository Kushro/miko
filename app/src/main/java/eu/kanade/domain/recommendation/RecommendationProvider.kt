package eu.kanade.domain.recommendation

import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.model.Manga

// MIKO -->

/**
 * One batch of suggestions produced by a provider for a seed entry.
 *
 * @property provider which system produced it — the expanded screen groups by this and the
 * multi-system mode de-dupes by its priority.
 * @property keyword `""` for "the source's own related list"; otherwise the search keyword / tag /
 * tracker name the group was built from (shown as the group subtitle, tappable as a search).
 * @property mangas domain entries. Entries that belong to the seed's source are already persisted
 * (`NetworkToLocalManga`) so they carry real ids; external entries (trackers) have `id == -1` and
 * `source == exh.recs.sources.RECOMMENDS_SOURCE` and must never be inserted.
 */
data class RecommendationGroup(
    val provider: RecommendationProviderId,
    val keyword: String,
    val mangas: List<Manga>,
)

/**
 * A system that finds titles related to a seed entry — see [RecommendationProviderId] for what
 * each one does. Implementations live in `eu.kanade.domain.recommendation.providers`; the
 * [RecommendationEngine] decides which ones run and in which order.
 */
interface RecommendationProvider {

    val id: RecommendationProviderId

    /**
     * Cheap, network-free check: can this provider do anything useful for [manga] on [source]?
     * (e.g. Uwasa and Tagu Osekkai need a `CatalogueSource`, nothing works on a `StubSource`).
     */
    fun isAvailable(source: Source, manga: Manga): Boolean

    /**
     * Streams [RecommendationGroup]s through [push] as soon as each is ready. May push zero groups.
     * Non-fatal problems with one sub-step should be reported through [onError] and the rest
     * should continue; throwing aborts only this provider (the engine reports it through
     * [onError] too). Must rethrow `CancellationException`.
     */
    suspend fun recommend(
        source: Source,
        manga: Manga,
        onError: (Throwable) -> Unit,
        push: suspend (RecommendationGroup) -> Unit,
    )
}

// MIKO <--
