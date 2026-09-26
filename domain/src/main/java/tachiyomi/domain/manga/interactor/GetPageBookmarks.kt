package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.PageBookmark
import tachiyomi.domain.manga.model.PageBookmarkWithRelations
import tachiyomi.domain.manga.repository.PageBookmarkRepository

/** MIKO — reads per-page bookmarks. */
class GetPageBookmarks(
    private val repository: PageBookmarkRepository,
) {

    fun subscribeAll(): Flow<List<PageBookmarkWithRelations>> = repository.subscribeAll()

    fun subscribe(mangaId: Long): Flow<List<PageBookmark>> = repository.subscribeByMangaId(mangaId)

    fun subscribeByChapter(chapterId: Long): Flow<List<PageBookmark>> = repository.subscribeByChapterId(chapterId)

    suspend fun await(mangaId: Long): List<PageBookmark> = repository.getByMangaId(mangaId)

    suspend fun await(chapterId: Long, pageIndex: Int): PageBookmark? = repository.get(chapterId, pageIndex)
}
