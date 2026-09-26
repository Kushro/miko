package eu.kanade.domain.recommendation.providers

import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.recommendation.RecommendationGroup
import eu.kanade.domain.recommendation.RecommendationProvider
import eu.kanade.domain.recommendation.RecommendationProviderId
import eu.kanade.tachiyomi.source.Source
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource

// MIKO -->

/**
 * **Osusume** — the untouched Komikku pipeline (see [RecommendationProviderId.OSUSUME]): delegates
 * to `Source.getRelatedMangaList` (extension related list + one search per title word, all
 * groups pushed as they arrive) and persists every group through [persistRelated], exactly what
 * `MangaScreenModel.fetchRelatedMangasFromSource` did before C14.
 */
class OsusumeProvider(
    private val networkToLocalManga: NetworkToLocalManga,
) : RecommendationProvider {

    override val id: RecommendationProviderId = RecommendationProviderId.OSUSUME

    override fun isAvailable(source: Source, manga: Manga): Boolean = source !is StubSource

    override suspend fun recommend(
        source: Source,
        manga: Manga,
        onError: (Throwable) -> Unit,
        push: suspend (RecommendationGroup) -> Unit,
    ) {
        source.getRelatedMangaList(manga.toSManga(), onError) { pair, _ ->
            push(RecommendationGroup(id, pair.first, networkToLocalManga.persistRelated(source.id, pair.second)))
        }
    }
}

// MIKO <--
