package eu.kanade.domain.download.interactor

import androidx.compose.ui.util.fastAny
import eu.kanade.core.util.fastFilterNot
import eu.kanade.tachiyomi.data.download.DownloadManager
import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.model.Manga

/**
 * MIKO — queues the unread, not-yet-downloaded chapters of the library.
 *
 * [awaitForManga] holds the per-entry logic that used to live inside
 * `LibraryScreenModel.downloadNextChapters` (including the SY handling of merged sources, whose
 * chapters have to be queued against the merged-from entry, not the merged one), so both the
 * library selection action and the "Download all unread chapters in library" action of the download
 * queue screen share a single implementation.
 */
class DownloadLibraryUnreadChapters(
    private val getFavorites: GetFavorites,
    private val getNextChapters: GetNextChapters,
    private val getMergedMangaById: GetMergedMangaById,
    private val downloadManager: DownloadManager,
) {

    /** Number of library entries the whole-library action would go through. */
    suspend fun libraryCount(): Int = getFavorites.await().size

    /**
     * Queues every unread chapter of every library entry.
     *
     * @return the amount of chapters actually enqueued.
     */
    suspend fun await(): Int {
        return getFavorites.await().sumOf { awaitForManga(it, amount = null) }
    }

    /**
     * Queues the next unread chapters of a single entry.
     *
     * @param amount how many chapters to take, or null for every unread one.
     * @return the amount of chapters actually enqueued.
     */
    suspend fun awaitForManga(manga: Manga, amount: Int? = null): Int {
        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            val mergedMangas = getMergedMangaById.await(manga.id)
                .associateBy { it.id }
            var queued = 0
            getNextChapters.await(manga.id)
                .let { if (amount != null) it.take(amount) else it }
                .groupBy { it.mangaId }
                .forEach ab@{ (mangaId, chapters) ->
                    val mergedManga = mergedMangas[mangaId] ?: return@ab
                    val downloadChapters = chapters.filterNotQueuedOrDownloaded(mergedManga)

                    downloadManager.downloadChapters(mergedManga, downloadChapters)
                    queued += downloadChapters.size
                }
            return queued
        }
        // SY <--

        val chapters = getNextChapters.await(manga.id)
            .filterNotQueuedOrDownloaded(manga)
            .let { if (amount != null) it.take(amount) else it }

        downloadManager.downloadChapters(manga, chapters)
        return chapters.size
    }

    private fun List<Chapter>.filterNotQueuedOrDownloaded(manga: Manga): List<Chapter> {
        return fastFilterNot { chapter ->
            downloadManager.queueState.value.fastAny { chapter.id == it.chapter.id } ||
                downloadManager.isChapterDownloaded(
                    chapter.name,
                    chapter.scanlator,
                    chapter.url,
                    // SY -->
                    manga.ogTitle,
                    // SY <--
                    manga.source,
                )
        }
    }
}
