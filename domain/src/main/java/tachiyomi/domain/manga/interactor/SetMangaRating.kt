package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.MangaRatingRepository

/** MIKO — writes the user's local rating (1..5; null/0 clears). */
class SetMangaRating(
    private val repository: MangaRatingRepository,
) {

    suspend fun await(mangaId: Long, rating: Int?) {
        repository.set(mangaId, rating?.takeIf { it in 1..MAX }?.coerceIn(1, MAX))
    }

    companion object {
        const val MAX = 5
    }
}
