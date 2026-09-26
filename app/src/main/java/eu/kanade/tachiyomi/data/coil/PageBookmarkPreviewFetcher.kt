package eu.kanade.tachiyomi.data.coil

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import tachiyomi.domain.manga.interactor.GetPageBookmarkPreviews
import tachiyomi.domain.manga.model.PageBookmarkPreviewCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

/**
 * MIKO — Coil [Fetcher] for a persisted page-bookmark capture ("Moments", C20): reads the WEBP
 * blob out of `page_bookmark_previews` by bookmark id (molded on [BufferedSourceFetcher], the
 * blob never touches disk on the way in). [PageBookmarkPreviewKeyer] keys the request so a
 * recompression is picked up automatically.
 */
class PageBookmarkPreviewFetcher(
    private val data: PageBookmarkPreviewCover,
    private val options: Options,
    private val getPageBookmarkPreviews: Lazy<GetPageBookmarkPreviews>,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = getPageBookmarkPreviews.value.await(data.bookmarkId)
            ?: throw IOException("No stored preview for bookmark ${data.bookmarkId}")
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(bytes) },
                fileSystem = options.fileSystem,
            ),
            mimeType = "image/webp",
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory(
        private val getPageBookmarkPreviewsLazy: Lazy<GetPageBookmarkPreviews> = lazy { Injekt.get<GetPageBookmarkPreviews>() },
    ) : Fetcher.Factory<PageBookmarkPreviewCover> {

        override fun create(data: PageBookmarkPreviewCover, options: Options, imageLoader: ImageLoader): Fetcher {
            return PageBookmarkPreviewFetcher(data, options, getPageBookmarkPreviewsLazy)
        }
    }
}
