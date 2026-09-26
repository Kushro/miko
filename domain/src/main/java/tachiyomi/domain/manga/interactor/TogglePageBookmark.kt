package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.model.PageBookmark
import tachiyomi.domain.manga.repository.PageBookmarkRepository

/** MIKO — adds/removes per-page bookmarks. */
class TogglePageBookmark(
    private val repository: PageBookmarkRepository,
) {

    /**
     * @param scrollFraction how far into the page the reader was (0..1), or null when that is
     * unknown — a paged reading mode, or a page that is not currently laid out. See [PageBookmark].
     * @param focusFraction where on the page the user's attention was (0..1): the long-pressed
     * point, or the viewport centre for the top-bar toggle; null when unknown. See [PageBookmark].
     * @param previewWebp the viewport capture (C20), persisted atomically with the bookmark; only
     * meaningful on the insert path — a toggle that removes ignores it. Null = no capture (page
     * still loading, capture failed): the bookmark is created anyway, without a preview row.
     * @return true when the page ended up bookmarked, false when the bookmark was removed.
     */
    suspend fun await(
        mangaId: Long,
        chapterId: Long,
        pageIndex: Int,
        imageUrl: String?,
        scrollFraction: Float? = null,
        focusFraction: Float? = null,
        previewWebp: ByteArray? = null,
    ): Boolean {
        val existing = repository.get(chapterId, pageIndex)
        return if (existing != null) {
            repository.delete(existing.id)
            false
        } else {
            // null = lost the race against another insert: the page is bookmarked either way.
            val inserted = repository.insert(
                mangaId,
                chapterId,
                pageIndex,
                imageUrl,
                note = null,
                scrollFraction = scrollFraction,
                focusFraction = focusFraction,
                previewWebp = previewWebp,
            ) != null
            inserted || repository.get(chapterId, pageIndex) != null
        }
    }

    suspend fun add(
        mangaId: Long,
        chapterId: Long,
        pageIndex: Int,
        imageUrl: String?,
        note: String? = null,
        scrollFraction: Float? = null,
        focusFraction: Float? = null,
        previewWebp: ByteArray? = null,
    ): Long? = repository.insert(
        mangaId,
        chapterId,
        pageIndex,
        imageUrl,
        note,
        scrollFraction,
        focusFraction,
        previewWebp,
    )

    suspend fun remove(id: Long) = repository.delete(id)

    suspend fun remove(chapterId: Long, pageIndex: Int) = repository.delete(chapterId, pageIndex)

    suspend fun removeAll(mangaId: Long) = repository.deleteByMangaId(mangaId)

    /** Drops every page bookmark of every manga ("delete all" in the bookmarks screen). */
    suspend fun removeAll() = repository.deleteAll()

    suspend fun updateNote(id: Long, note: String?) = repository.updateNote(id, note)

    /**
     * Stores the page image URL the preview just re-resolved against the source, so the list
     * thumbnail (keyed by that URL in `ChapterCache`) finds the freshly cached image.
     */
    suspend fun updateImageUrl(id: Long, imageUrl: String?) = repository.updateImageUrl(id, imageUrl)

    suspend fun replaceAll(mangaId: Long, bookmarks: List<PageBookmark>) = repository.replaceAll(mangaId, bookmarks)
}
