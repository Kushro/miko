package eu.kanade.domain.recommendation.providers

import eu.kanade.domain.recommendation.RecommendationGroup
import eu.kanade.domain.recommendation.RecommendationProvider
import eu.kanade.domain.recommendation.RecommendationProviderId
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.manga.interactor.GetMangaTags
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.source.kotatsu.KotatsuSource

// MIKO -->

/**
 * **Tagu Osekkai** — per-entry tag affinity (see [RecommendationProviderId.TAGU_OSEKKAI]).
 *
 * Seed tags = the user's local tags (`GetMangaTags`, first — they express intent) followed by the
 * source genres (`Manga.genre`). For each of the first [MAX_TAGS] seed tags that match a tag filter
 * of the same source (exact match after normalisation on `Filter.Group` children of type
 * `CheckBox`/`TriState`, or a `Filter.Select` value), one search is run with that filter selected
 * and an empty query; results are scored by the share of seed tags present in their genres
 * (squared), the seed is removed, and the group — keyword = the tag — is pushed sorted by score.
 * Kotatsu sources are asked through `KotatsuSource.awaitFilterList()` (the plain `getFilterList()`
 * is a stub until the options load).
 */
class TaguOsekkaiProvider(
    private val networkToLocalManga: NetworkToLocalManga,
    private val getMangaTags: GetMangaTags,
) : RecommendationProvider {

    override val id: RecommendationProviderId = RecommendationProviderId.TAGU_OSEKKAI

    override fun isAvailable(source: Source, manga: Manga): Boolean =
        source !is StubSource && source is CatalogueSource

    override suspend fun recommend(
        source: Source,
        manga: Manga,
        onError: (Throwable) -> Unit,
        push: suspend (RecommendationGroup) -> Unit,
    ) {
        val catalogueSource = source as CatalogueSource

        val seedTags = seedTags(manga)
        if (seedTags.isEmpty()) return
        val seedNormalized = seedTags.map { TagFilterMatcher.normalize(it) }.toSet()

        val initialFilters = freshFilterList(source, catalogueSource)
        val matchedTags = seedTags
            .filter { TagFilterMatcher.matches(initialFilters, TagFilterMatcher.normalize(it)) }
            .take(MAX_TAGS)
        if (matchedTags.isEmpty()) return

        supervisorScope {
            matchedTags.forEach { tag ->
                launch {
                    try {
                        val filters = freshFilterList(source, catalogueSource)
                        if (!TagFilterMatcher.mark(filters, TagFilterMatcher.normalize(tag))) return@launch

                        val results = catalogueSource.getSearchManga(page = 1, query = "", filters = filters)
                            .mangas
                            .filterNot { it.url == manga.url }
                        if (results.isEmpty()) return@launch

                        val ranked = results
                            .sortedByDescending { TagFilterMatcher.score(it.genre, seedNormalized) }
                            .take(MAX_RESULTS_PER_TAG)

                        push(RecommendationGroup(id, tag, networkToLocalManga.persistRelated(source.id, ranked)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        onError(e)
                    }
                }
            }
        }
    }

    /** Seed tags: local tags first (they express intent), then source genres — see [id]'s KDoc. */
    private suspend fun seedTags(manga: Manga): List<String> {
        val local = getMangaTags.await(manga.id).map { it.name }
        val genres = manga.genre.orEmpty()
        return (local + genres)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { TagFilterMatcher.normalize(it) }
    }

    /** A newly-built [FilterList] for [source] — Kotatsu sources need the options loaded first. */
    private suspend fun freshFilterList(source: Source, catalogueSource: CatalogueSource): FilterList =
        (source as? KotatsuSource)?.awaitFilterList() ?: catalogueSource.getFilterList()

    companion object {
        /** Network cost bound: at most this many tag searches per entry. */
        const val MAX_TAGS = 2

        /** Results kept per tag group. */
        const val MAX_RESULTS_PER_TAG = 20
    }
}

// MIKO <--
