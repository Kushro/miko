package tachiyomi.domain.chapter.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapter.model.BookmarkedChapter
import tachiyomi.domain.chapter.repository.ChapterBookmarkRepository

/** MIKO — every bookmarked chapter of the database, for the Favorites → Chapters tab. */
class GetBookmarkedChapters(
    private val repository: ChapterBookmarkRepository,
) {

    fun subscribeAll(): Flow<List<BookmarkedChapter>> = repository.subscribeBookmarkedChapters()
}
