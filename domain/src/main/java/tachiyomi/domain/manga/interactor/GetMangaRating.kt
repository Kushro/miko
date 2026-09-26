package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.repository.MangaRatingRepository

/** MIKO — reads the user's local rating. */
class GetMangaRating(
    private val repository: MangaRatingRepository,
) {

    fun subscribe(mangaId: Long): Flow<Int?> = repository.subscribe(mangaId)

    fun subscribeAll(): Flow<Map<Long, Int>> = repository.subscribeAll()

    suspend fun await(mangaId: Long): Int? = repository.get(mangaId)

    suspend fun awaitAll(): Map<Long, Int> = repository.getAll()
}
