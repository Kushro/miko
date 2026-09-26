package tachiyomi.domain.chapter.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapter.model.BookmarkedChapter
import tachiyomi.domain.chapter.model.ChapterBookmarkType

/**
 * MIKO — the kind of each chapter bookmark (`chapter_bookmark_types`) plus the library-wide list of
 * bookmarked chapters used by the Favorites screen.
 *
 * "Generic" is the absence of a row: [setType] with [ChapterBookmarkType.GENERIC] deletes, and every
 * read maps a missing row to `GENERIC`. Rows cascade away with their chapter and are also dropped
 * by a trigger when the chapter is un-bookmarked.
 */
interface ChapterBookmarkRepository {

    /** chapterId → type for every chapter of [mangaId] whose kind is not GENERIC. */
    fun subscribeTypesByMangaId(mangaId: Long): Flow<Map<Long, ChapterBookmarkType>>

    /** chapterId → type for every non-GENERIC row in the database (Updates / global lists). */
    fun subscribeAllTypes(): Flow<Map<Long, ChapterBookmarkType>>

    suspend fun getTypesByMangaId(mangaId: Long): Map<Long, ChapterBookmarkType>

    suspend fun getType(chapterId: Long): ChapterBookmarkType

    /** GENERIC deletes the row; anything else upserts it. Does **not** touch `chapters.bookmark`. */
    suspend fun setType(chapterId: Long, type: ChapterBookmarkType)

    /** Same as [setType] for many chapters, in one transaction. */
    suspend fun setTypes(chapterIds: List<Long>, type: ChapterBookmarkType)

    /**
     * Every chapter with `bookmark = 1`, joined with its manga, ordered by manga title
     * (case-insensitive) then chapter number descending.
     */
    fun subscribeBookmarkedChapters(): Flow<List<BookmarkedChapter>>
}
