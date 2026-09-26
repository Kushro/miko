package eu.kanade.tachiyomi.data.coil

import coil3.key.Keyer
import coil3.request.Options
import tachiyomi.domain.manga.model.PageBookmarkPreviewCover

/**
 * MIKO — Coil cache key for a persisted page-bookmark capture ("Moments", C20). Includes
 * [PageBookmarkPreviewCover.updatedAt] so a recompression — which bumps that timestamp — busts
 * the cached decode without any manual invalidation (molded on [PagePreviewKeyer]).
 */
class PageBookmarkPreviewKeyer : Keyer<PageBookmarkPreviewCover> {
    override fun key(data: PageBookmarkPreviewCover, options: Options): String {
        return "pb-preview;${data.bookmarkId};${data.updatedAt}"
    }
}
