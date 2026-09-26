package tachiyomi.data.manga

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.repository.MangaRatingRepository

/**
 * MIKO — SQLDelight implementation of [MangaRatingRepository] on top of `manga_ratings`.
 *
 * "Not rated" is the absence of a row, so [set] with `null` or a non-positive value deletes it
 * instead of storing a 0.
 */
class MangaRatingRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaRatingRepository {

    override fun subscribeAll(): Flow<Map<Long, Int>> {
        return handler.subscribeToList { manga_ratingsQueries.getAll(::mapRating) }
            .map { it.toMap() }
    }

    override fun subscribe(mangaId: Long): Flow<Int?> {
        return handler.subscribeToOneOrNull { manga_ratingsQueries.get(mangaId) }
            .map { it?.toInt() }
    }

    override suspend fun get(mangaId: Long): Int? {
        return handler.awaitOneOrNull { manga_ratingsQueries.get(mangaId) }?.toInt()
    }

    override suspend fun getAll(): Map<Long, Int> {
        return handler.awaitList { manga_ratingsQueries.getAll(::mapRating) }.toMap()
    }

    override suspend fun set(mangaId: Long, rating: Int?) {
        val coerced = rating?.takeIf { it > 0 }?.coerceAtMost(MAX_RATING)
        if (coerced == null) {
            handler.await { manga_ratingsQueries.delete(mangaId) }
        } else {
            handler.await {
                manga_ratingsQueries.upsert(
                    mangaId = mangaId,
                    rating = coerced.toLong(),
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
    }
}

/** Mirrors `SetMangaRating.MAX`; kept local so `data` does not reach into an interactor. */
private const val MAX_RATING = 5

private fun mapRating(mangaId: Long, rating: Long): Pair<Long, Int> = mangaId to rating.toInt()
