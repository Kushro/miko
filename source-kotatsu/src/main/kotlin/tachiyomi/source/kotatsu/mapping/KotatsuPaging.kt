// SPDX-License-Identifier: GPL-3.0-or-later
package tachiyomi.source.kotatsu.mapping

/**
 * Translates Mihon's 1-based page numbers into the accumulated `offset` the parsers library expects.
 *
 * Neither side exposes a fixed page size (`PagedMangaParser.pageSize` is protected and plenty of
 * parsers extend `AbstractMangaParser` with ad-hoc offsets) nor a total count, so the only sound
 * strategy is to remember how many items each page actually returned:
 * `offset(p + 1) = offset(p) + itemsReturnedOn(p)`.
 *
 * Page 1 is always offset 0. Asking for a page whose offset was never computed returns `null`, and
 * the caller is expected to walk forward from [lastKnownPage] recording each hop.
 *
 * Entries are keyed by the caller (order + filter), so switching listing or filters keeps its own
 * running offset. Recording page 1 again drops everything after it, which is what a fresh
 * listing/search of the same key means.
 */
class KotatsuPaging {

    private val offsets = HashMap<String, MutableMap<Int, Int>>()

    /** Offset to request for [page], or `null` when it is not known yet. */
    @Synchronized
    fun offsetFor(key: String, page: Int): Int? {
        if (page <= FIRST_PAGE) return 0
        return offsets[key]?.get(page)
    }

    /** Records that [page] of [key] returned [count] items, making the offset of `page + 1` known. */
    @Synchronized
    fun record(key: String, page: Int, count: Int) {
        if (count < 0) return
        val pages: MutableMap<Int, Int>
        val base: Int
        if (page <= FIRST_PAGE) {
            pages = HashMap()
            offsets[key] = pages
            base = 0
        } else {
            pages = offsets[key] ?: return
            base = pages[page] ?: return
        }
        pages[page] = base
        pages[page + 1] = base + count
    }

    /** Highest page not greater than [upTo] whose offset is known. Always at least page 1. */
    @Synchronized
    fun lastKnownPage(key: String, upTo: Int): Int {
        if (upTo <= FIRST_PAGE) return FIRST_PAGE
        val pages = offsets[key] ?: return FIRST_PAGE
        for (page in upTo downTo FIRST_PAGE + 1) {
            if (pages.containsKey(page)) return page
        }
        return FIRST_PAGE
    }

    /** Forgets everything known about [key]. */
    @Synchronized
    fun reset(key: String) {
        offsets.remove(key)
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
