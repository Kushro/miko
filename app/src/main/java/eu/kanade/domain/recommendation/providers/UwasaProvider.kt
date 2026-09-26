package eu.kanade.domain.recommendation.providers

import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.recommendation.RecommendationGroup
import eu.kanade.domain.recommendation.RecommendationProvider
import eu.kanade.domain.recommendation.RecommendationProviderId
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import tachiyomi.source.kotatsu.KotatsuSource

// MIKO -->

/**
 * **Uwasa** — the Kotatsu criterion (see [RecommendationProviderId.UWASA]).
 *
 * - Kotatsu-backed source (`KotatsuSource`): only `Source.getRelatedMangaListByExtension`, which
 *   runs the parser's `getRelatedManga` (site block, or Kotatsu's own keyword finder). Never the
 *   second keyword pass the KMK pipeline adds.
 * - Extension source: `getRelatedMangaListByExtension` when the source supports it, plus a
 *   keyword search (`stripKeywordForRelatedMangas` on title and original title) where a result
 *   counts only if its title contains the keyword and **only the smallest non-empty keyword group
 *   is pushed**.
 * - The seed itself (same url) is always removed.
 */
class UwasaProvider(
    private val networkToLocalManga: NetworkToLocalManga,
) : RecommendationProvider {

    override val id: RecommendationProviderId = RecommendationProviderId.UWASA

    override fun isAvailable(source: Source, manga: Manga): Boolean =
        source !is StubSource && source is CatalogueSource

    override suspend fun recommend(
        source: Source,
        manga: Manga,
        onError: (Throwable) -> Unit,
        push: suspend (RecommendationGroup) -> Unit,
    ) {
        val sManga = manga.toSManga()

        // Extension's own related block ("" keyword), seed always removed (see class KDoc).
        suspend fun pushExtensionRelated() {
            source.getRelatedMangaListByExtension(sManga) { pair, _ ->
                val mangas = pair.second.filter { it.url != manga.url }
                if (mangas.isNotEmpty()) {
                    push(RecommendationGroup(id, pair.first, networkToLocalManga.persistRelated(source.id, mangas)))
                }
            }
        }

        if (source is KotatsuSource) {
            // Only the parser's own related list — never the keyword search pass.
            pushExtensionRelated()
            return
        }

        val catalogueSource = source as? CatalogueSource ?: return

        supervisorScope {
            if (catalogueSource.supportsRelatedMangas && !catalogueSource.disableRelatedMangas) {
                launch { pushExtensionRelated() }
            }

            if (!catalogueSource.disableRelatedMangas && !catalogueSource.disableRelatedMangasBySearch) {
                launch {
                    val keywords = with(catalogueSource) {
                        (manga.title.stripKeywordForRelatedMangas() + manga.ogTitle.stripKeywordForRelatedMangas()).distinct()
                    }
                    if (keywords.isEmpty()) return@launch

                    val filterList = catalogueSource.getFilterList()
                    val perKeyword = keywords.map { keyword ->
                        async {
                            keyword to try {
                                Result.success(
                                    filterUwasaSearchResults(
                                        catalogueSource.getSearchManga(1, keyword, filterList).mangas,
                                        keyword,
                                        manga.url,
                                    ),
                                )
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                Result.failure(e)
                            }
                        }
                    }.awaitAll()

                    perKeyword.forEach { (_, result) -> result.onFailure(onError) }

                    val smallest = selectSmallestNonEmptyGroup(
                        perKeyword.mapNotNull { (keyword, result) -> result.getOrNull()?.let { keyword to it } },
                    )

                    if (smallest != null) {
                        val (keyword, mangas) = smallest
                        push(RecommendationGroup(id, keyword, networkToLocalManga.persistRelated(source.id, mangas)))
                    }
                }
            }
        }
    }
}

/**
 * Keeps only the search results that actually count as a "related" hit for Uwasa's keyword pass:
 * not the seed itself, and the result's title must contain the searched [keyword]. Extracted so
 * it is unit-testable without a real [Source].
 */
internal fun filterUwasaSearchResults(mangas: List<SManga>, keyword: String, seedUrl: String): List<SManga> =
    mangas.filter { it.url != seedUrl && it.title.contains(keyword, ignoreCase = true) }

/**
 * Uwasa's keyword pass pushes a single group: the smallest non-empty one. Extracted so it is
 * unit-testable without coroutines/network involved.
 */
internal fun selectSmallestNonEmptyGroup(groups: List<Pair<String, List<SManga>>>): Pair<String, List<SManga>>? =
    groups.filter { it.second.isNotEmpty() }.minByOrNull { it.second.size }

// MIKO <--
