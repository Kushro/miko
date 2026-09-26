package eu.kanade.tachiyomi.ui.favorites

import android.app.Application
import eu.kanade.domain.chapter.model.toSChapter
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import exh.util.DataSaver
import exh.util.DataSaver.Companion.getImage
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.TogglePageBookmark
import tachiyomi.domain.manga.model.PageBookmark
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream

/**
 * MIKO — C20: the cascade that resolves where to read a bookmarked page's *full* image from
 * (`ChapterCache` -> downloaded chapter -> source fetch), extracted out of
 * [PageBookmarkPreviewScreenModel] (which it originally lived in, C15) so
 * [eu.kanade.tachiyomi.ui.bookmarks.PageBookmarksScreenModel]'s "generate missing captures"
 * backfill (D14) can resolve the same way without going through the persisted-capture blob
 * shortcut the preview adds on top (D7) — backfill exists precisely to *create* that blob, so it
 * always wants the real page.
 */
class PageBookmarkPageResolver(
    private val chapterCache: ChapterCache = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val getManga: GetManga = Injekt.get(),
    private val getChapter: GetChapter = Injekt.get(),
    private val togglePageBookmark: TogglePageBookmark = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val application: Application = Injekt.get(),
) {

    /**
     * Resolves where to read [bookmark]'s page image from, or `null` when the manga/chapter/page
     * no longer exists. An actual failure (network, disk) is left to throw — the caller decides
     * how to report it.
     */
    suspend fun resolveOpenStream(bookmark: PageBookmark): (() -> InputStream)? {
        val cachedUrl = bookmark.imageUrl
        if (cachedUrl != null && chapterCache.isImageInCache(cachedUrl)) {
            return { chapterCache.getImageFile(cachedUrl).inputStream() }
        }

        val manga = getManga.await(bookmark.mangaId) ?: return null
        val chapter = getChapter.await(bookmark.chapterId) ?: return null
        val source = sourceManager.get(manga.source) ?: return null

        val downloaded = downloadManager.isChapterDownloaded(
            chapter.name,
            chapter.scanlator,
            chapter.url,
            // SY --> the download dir is named after the original title, not a custom one
            manga.ogTitle,
            // SY <--
            manga.source,
        )
        if (downloaded) {
            val uri = downloadManager.buildPageList(source, manga, chapter)
                .getOrNull(bookmark.pageIndex)?.uri ?: return null
            return { application.contentResolver.openInputStream(uri)!! }
        }

        val page = source.getPageList(chapter.toSChapter()).getOrNull(bookmark.pageIndex)
            ?: return null
        if (source is HttpSource) {
            val imageUrl = resolveHttpImageUrl(source, page)
            if (imageUrl != bookmark.imageUrl) {
                togglePageBookmark.updateImageUrl(bookmark.id, imageUrl)
            }
            return { chapterCache.getImageFile(imageUrl).inputStream() }
        }

        val uri = page.uri ?: return null
        return { application.contentResolver.openInputStream(uri)!! }
    }

    /** Ensures [page] has an image URL, fetches and caches it if needed, and returns that URL. */
    private suspend fun resolveHttpImageUrl(source: HttpSource, page: Page): String {
        if (page.imageUrl.isNullOrEmpty()) {
            page.imageUrl = source.getImageUrl(page)
        }
        val imageUrl = page.imageUrl!!
        if (!chapterCache.isImageInCache(imageUrl)) {
            val dataSaver = DataSaver(source, sourcePreferences)
            val response = source.getImage(page, dataSaver)
            chapterCache.putImageToCache(imageUrl, response)
        }
        return imageUrl
    }
}
