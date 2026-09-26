package eu.kanade.tachiyomi.source.enhancement.comix

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.enhancement.CommentsSort
import eu.kanade.domain.source.enhancement.EnhancementGroup
import eu.kanade.domain.source.enhancement.SourceComment
import eu.kanade.domain.source.enhancement.SourceCommentsPage
import eu.kanade.domain.source.enhancement.SourceCommentsProvider
import eu.kanade.domain.source.enhancement.SourceEnhancement
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.Json
import tachiyomi.i18n.miko.MKMR
import uy.kohesive.injekt.injectLazy
import java.io.IOException

/**
 * MIKO — series and chapter comments from comix.to, for the Keiyoushi "Comix" extension.
 *
 * Two translations sit between the extension's data and the site's thread API:
 *
 * 1. **Page → thread id.** The extension only knows the page paths (`/title/<hid>-<slug>` and
 *    `…/<chapterId>-chapter-<n>`); the API keys comments by an opaque thread id. The site's own
 *    client resolves it with `threads/lookup?page_identifier=manga<id>[_chap<n>_vol<v>]&page_url=…`,
 *    where `<id>` is the manga's numeric id — which only the request-signed `/api/v1/manga` returns.
 *    So the resolution goes in layers, cheapest first: lookup by `page_url` alone; then the page's
 *    HTML (thread id if it is in there, else the numeric ids from `<script id="initial-data">`) and
 *    a second lookup with the full identifier. Results are cached per page path.
 * 2. **Cursor → page number.** The API pages with an opaque cursor; the provider contract wants
 *    1-based pages. Cursors are recorded per (thread, sort) as pages are fetched in order, which is
 *    how the comments loader always asks for them.
 *
 * Replies beyond the inline preview are fetched with `parent_id` + the comment's own cursor, kept
 * in a small in-memory map keyed by comment id (see [SourceCommentsProvider.getReplies]).
 */
class ComixCommentsEnhancement(
    private val api: ComixApi = ComixApi.instance,
) : SourceEnhancement {

    override val name: String = "Comix comments"

    override val titleRes: StringResource = MKMR.strings.enhancement_comix_comments

    override val group: EnhancementGroup = EnhancementGroup.COMIX

    override val extraFeatures: Set<SourceFeature> = setOf(SourceFeature.CHAPTER_COMMENTS)

    private val json: Json by injectLazy()

    /** Page path → thread id. */
    private val threadIds = lruMap<String, Long>(THREAD_CACHE_SIZE)

    /** "threadId|sort" → cursors by page (index 0 = cursor to request page 2, …) and items seen. */
    private val chains = lruMap<String, PageChain>(CHAIN_CACHE_SIZE)

    /** Comment id → what is needed to fetch the rest of its replies. */
    private val replyRefs = lruMap<Long, ReplyRef>(REPLY_REF_CACHE_SIZE)

    override val comments: SourceCommentsProvider = object : SourceCommentsProvider {

        override suspend fun getMangaComments(manga: SManga, page: Int, sort: CommentsSort): SourceCommentsPage {
            val path = seriesPagePathOf(manga.url)
                ?: throw IOException("Unrecognised Comix series URL: ${manga.url}")
            val threadId = resolveThreadId(path, chapterId = null)
            return fetchPage(threadId, sort, page)
        }

        override suspend fun getChapterComments(
            manga: SManga,
            chapter: SChapter,
            page: Int,
            sort: CommentsSort,
        ): SourceCommentsPage {
            val path = chapterPagePathOf(chapter.url)
                ?: throw IOException("Unrecognised Comix chapter URL: ${chapter.url}")
            val chapterId = chapterIdOf(path)
                ?: throw IOException("Unrecognised Comix chapter URL: ${chapter.url}")
            val threadId = resolveThreadId(path, chapterId)
            return fetchPage(threadId, sort, page)
        }

        override suspend fun getReplies(comment: SourceComment): List<SourceComment> {
            if (comment.replies.size >= comment.replyCount) return comment.replies
            val id = comment.id.toLongOrNull() ?: return comment.replies
            val ref = synchronized(replyRefs) { replyRefs[id] } ?: return comment.replies

            val known = comment.replies.mapTo(HashSet()) { it.id }
            val fetched = ArrayList<SourceComment>()
            var cursor: String? = ref.cursor
            var pages = 0
            while (pages < MAX_REPLY_PAGES) {
                val result = api.getComments(ref.threadId, CommentsSort.TOP, cursor, parentId = id)
                val items = result.items.orEmpty()
                if (items.isEmpty()) break
                items.forEach(::rememberReplyRefs)
                fetched += items.toFlatReplies().filter { known.add(it.id) }
                pages++
                cursor = result.cursor
                if (cursor.isNullOrBlank() || comment.replies.size + fetched.size >= comment.replyCount) break
            }
            return comment.replies + fetched
        }
    }

    // --- thread resolution ---------------------------------------------------------------------

    /**
     * Thread id of the page at [path]; [chapterId] is set for chapter pages. Cached. Throws when the
     * site simply does not know the page.
     */
    private suspend fun resolveThreadId(path: String, chapterId: Long?): Long {
        synchronized(threadIds) { threadIds[path] }?.let { return it }

        val threadId = api.lookupThread(path)?.id
            ?: resolveThreadIdFromPage(path, chapterId)
            ?: throw IOException("Comments thread not found on Comix for $path")

        synchronized(threadIds) { threadIds[path] = threadId }
        return threadId
    }

    /** The slow path: the page HTML, then a lookup with the identifier the site's client would send. */
    private suspend fun resolveThreadIdFromPage(path: String, chapterId: Long?): Long? {
        val html = api.getPageHtml(path)
        findThreadIdInPage(html)?.let { return it }

        val hid = hidOf(path) ?: return null
        val data = extractInitialData(html, json) ?: return null
        val mangaId = findMangaId(data, hid) ?: return null
        val identifier = if (chapterId == null) {
            seriesPageIdentifier(mangaId)
        } else {
            val (number, volume) = findChapterNumberAndVolume(data, chapterId)
                ?: (chapterNumberOf(path) ?: return null) to 0
            chapterPageIdentifier(mangaId, number, volume)
        }
        return api.lookupThread(path, identifier)?.id
    }

    // --- paging --------------------------------------------------------------------------------

    private suspend fun fetchPage(threadId: Long, sort: CommentsSort, page: Int): SourceCommentsPage {
        val key = "$threadId|${sort.name}"
        val chain = if (page <= 1) {
            PageChain().also { synchronized(chains) { chains[key] = it } }
        } else {
            synchronized(chains) { chains[key] }
                ?: throw IOException("Comix comments must be loaded from the first page")
        }
        val cursor = if (page <= 1) {
            null
        } else {
            chain.cursorFor(page) ?: throw IOException("Comix comments page $page is not reachable yet")
        }

        val result = api.getComments(threadId, sort, cursor)
        val items = result.items.orEmpty()
        items.forEach(::rememberReplyRefs)
        val total = result.thread?.mainCommentCount

        val comments = items.map { it.toSourceComment() }
        chain.record(page, result.cursor, items.size)
        val hasNext = items.isNotEmpty() &&
            !result.cursor.isNullOrBlank() &&
            (total == null || chain.loaded < total)
        return SourceCommentsPage(comments = comments, hasNextPage = hasNext, total = total)
    }

    /** Remembers, for [comment] and every reply nested in it, how to fetch its remaining replies. */
    private fun rememberReplyRefs(comment: ComixCommentDto) {
        val threadId = comment.threadId ?: return
        if (comment.replyCount > 0) {
            synchronized(replyRefs) { replyRefs[comment.id] = ReplyRef(threadId, comment.cursor.orEmpty()) }
        }
        comment.replies.orEmpty().forEach(::rememberReplyRefs)
    }

    /** Cursors of a (thread, sort) listing, filled in as pages are fetched in order. */
    private class PageChain {
        private val nextCursors = ArrayList<String?>()
        var loaded = 0
            private set

        /** The cursor that requests [page] (2-based), when the previous page was fetched. */
        fun cursorFor(page: Int): String? = nextCursors.getOrNull(page - 2)?.takeIf { it.isNotBlank() }

        fun record(page: Int, nextCursor: String?, count: Int) {
            while (nextCursors.size < page) nextCursors.add(null)
            nextCursors[page - 1] = nextCursor
            loaded += count
        }
    }

    private class ReplyRef(val threadId: Long, val cursor: String)

    private companion object {
        const val THREAD_CACHE_SIZE = 128
        const val CHAIN_CACHE_SIZE = 16
        const val REPLY_REF_CACHE_SIZE = 2048
        const val MAX_REPLY_PAGES = 5

        fun <K, V> lruMap(maxSize: Int): LinkedHashMap<K, V> = object : LinkedHashMap<K, V>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > maxSize
        }
    }
}
