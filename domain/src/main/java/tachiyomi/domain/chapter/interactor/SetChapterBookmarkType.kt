package tachiyomi.domain.chapter.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterBookmarkRepository
import tachiyomi.domain.chapter.repository.ChapterRepository

/**
 * MIKO — sets the kind of a chapter bookmark.
 *
 * Choosing a kind is also how a chapter gets bookmarked: the chapters are marked
 * `bookmark = true` first (so the "un-bookmark clears the kind" trigger never fires on them) and
 * the kind is written afterwards. Passing [ChapterBookmarkType.GENERIC] keeps the bookmark and just
 * drops the refinement.
 *
 * Removing the bookmark itself stays with `UpdateChapter(bookmark = false)`; the database trigger
 * deletes the kind row for every path that does so.
 */
class SetChapterBookmarkType(
    private val repository: ChapterBookmarkRepository,
    private val chapterRepository: ChapterRepository,
) {

    suspend fun await(chapterId: Long, type: ChapterBookmarkType) = awaitAll(listOf(chapterId), type)

    suspend fun awaitAll(chapterIds: List<Long>, type: ChapterBookmarkType) {
        if (chapterIds.isEmpty()) return
        try {
            chapterRepository.updateAll(chapterIds.map { ChapterUpdate(id = it, bookmark = true) })
            repository.setTypes(chapterIds, type)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
