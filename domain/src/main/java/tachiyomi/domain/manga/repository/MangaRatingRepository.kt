package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow

/** MIKO — user's own 1..5 star rating per manga (`manga_ratings`). */
interface MangaRatingRepository {

    /** mangaId → rating (1..5) for every rated manga. */
    fun subscribeAll(): Flow<Map<Long, Int>>

    fun subscribe(mangaId: Long): Flow<Int?>

    suspend fun get(mangaId: Long): Int?

    suspend fun getAll(): Map<Long, Int>

    /** null or 0 clears the rating. Values are coerced into 1..5. */
    suspend fun set(mangaId: Long, rating: Int?)
}
