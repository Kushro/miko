package eu.kanade.domain.recommendation.providers

import eu.kanade.domain.recommendation.RecommendationGroup
import eu.kanade.domain.recommendation.RecommendationProvider
import eu.kanade.domain.recommendation.RecommendationProviderId
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import exh.recs.sources.AniListPagingSource
import exh.recs.sources.MyAnimeListPagingSource
import exh.recs.sources.RECOMMENDS_SOURCE
import exh.recs.sources.TrackerRecommendationPagingSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import mihon.domain.manga.model.toDomainManga
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga

// MIKO -->

/**
 * **AniList/MAL** — community recommendations through the existing `exh/recs` paging sources
 * (`AniListPagingSource`, `MyAnimeListPagingSource`; see [RecommendationProviderId.TRACKER]).
 * Both run in parallel; each pushes one group whose keyword is the tracker name
 * ([KEYWORD_ANILIST] / [KEYWORD_MAL]). Entries are **external**: `toDomainManga(RECOMMENDS_SOURCE)`,
 * never persisted; `NoResultsException` is an empty group, not an error.
 */
class TrackerProvider : RecommendationProvider {

    override val id: RecommendationProviderId = RecommendationProviderId.TRACKER

    override fun isAvailable(source: Source, manga: Manga): Boolean = true

    override suspend fun recommend(
        source: Source,
        manga: Manga,
        onError: (Throwable) -> Unit,
        push: suspend (RecommendationGroup) -> Unit,
    ) {
        suspend fun fetch(pagingSource: TrackerRecommendationPagingSource, keyword: String) {
            try {
                val mangas = pagingSource.requestNextPage(1).mangas.toExternalRecommendations()
                push(RecommendationGroup(id, keyword, mangas))
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoResultsException) {
                // No recommendations from this tracker — not an error.
            } catch (e: NoSuchElementException) {
                // MyAnimeListPagingSource.getRecsBySearch does `.first()` on the search hits: an
                // entry the tracker does not know is also "no recommendations", not an error.
            } catch (e: Throwable) {
                onError(e)
            }
        }

        supervisorScope {
            launch { fetch(AniListPagingSource(manga), KEYWORD_ANILIST) }
            launch { fetch(MyAnimeListPagingSource(manga), KEYWORD_MAL) }
        }
    }

    companion object {
        /** Group keywords — also what the UI shows as the badge/label of external entries. */
        const val KEYWORD_ANILIST = "AniList"
        const val KEYWORD_MAL = "MyAnimeList"

        /** Short badge label for [KEYWORD_MAL]; [KEYWORD_ANILIST] is short enough as is. */
        const val BADGE_MAL = "MAL"
    }
}

/**
 * Maps a tracker's raw results to *external* recommendation entries: `source = RECOMMENDS_SOURCE`,
 * never persisted (see class KDoc). Extracted so the mapping is unit-testable without a real
 * [TrackerRecommendationPagingSource].
 */
internal fun List<SManga>.toExternalRecommendations(): List<Manga> = map { it.toDomainManga(RECOMMENDS_SOURCE) }

// MIKO <--
