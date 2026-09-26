package tachiyomi.domain.chapter.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.domain.chapter.repository.ChapterBookmarkRepository

/** MIKO — reads the kind of chapter bookmarks (missing = [ChapterBookmarkType.GENERIC]). */
class GetChapterBookmarkTypes(
    private val repository: ChapterBookmarkRepository,
) {

    /** chapterId → type for the non-GENERIC chapters of one manga. */
    fun subscribe(mangaId: Long): Flow<Map<Long, ChapterBookmarkType>> = repository.subscribeTypesByMangaId(mangaId)

    /** chapterId → type for every non-GENERIC chapter in the database. */
    fun subscribeAll(): Flow<Map<Long, ChapterBookmarkType>> = repository.subscribeAllTypes()

    suspend fun await(mangaId: Long): Map<Long, ChapterBookmarkType> = repository.getTypesByMangaId(mangaId)

    suspend fun awaitOne(chapterId: Long): ChapterBookmarkType = repository.getType(chapterId)
}
