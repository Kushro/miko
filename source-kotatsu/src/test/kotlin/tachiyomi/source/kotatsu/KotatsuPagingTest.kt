// SPDX-License-Identifier: GPL-3.0-or-later
package tachiyomi.source.kotatsu

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.source.kotatsu.mapping.KotatsuPaging

@Execution(ExecutionMode.CONCURRENT)
class KotatsuPagingTest {

    @Test
    fun `the first page always starts at offset zero`() {
        val paging = KotatsuPaging()

        paging.offsetFor(KEY, 1) shouldBe 0
        paging.offsetFor(KEY, 0) shouldBe 0
    }

    @Test
    fun `sequential pages accumulate the returned item counts`() {
        val paging = KotatsuPaging()

        paging.record(KEY, 1, 20)
        paging.offsetFor(KEY, 2) shouldBe 20

        paging.record(KEY, 2, 15)
        paging.offsetFor(KEY, 3) shouldBe 35

        paging.record(KEY, 3, 15)
        paging.offsetFor(KEY, 4) shouldBe 50
    }

    @Test
    fun `a jump ahead is unknown and falls back to the last known page`() {
        val paging = KotatsuPaging()
        paging.record(KEY, 1, 20)
        paging.record(KEY, 2, 20)

        paging.offsetFor(KEY, 6) shouldBe null
        paging.lastKnownPage(KEY, 6) shouldBe 3
        paging.offsetFor(KEY, 3) shouldBe 40
    }

    @Test
    fun `the last known page is the first one when nothing was recorded`() {
        val paging = KotatsuPaging()

        paging.lastKnownPage(KEY, 5) shouldBe 1
        paging.lastKnownPage(KEY, 1) shouldBe 1
    }

    @Test
    fun `recording a page whose offset is unknown changes nothing`() {
        val paging = KotatsuPaging()

        paging.record(KEY, 4, 20)

        paging.offsetFor(KEY, 5) shouldBe null
        paging.lastKnownPage(KEY, 5) shouldBe 1
    }

    @Test
    fun `recording the first page again drops the accumulated offsets`() {
        val paging = KotatsuPaging()
        paging.record(KEY, 1, 20)
        paging.record(KEY, 2, 15)
        paging.offsetFor(KEY, 3) shouldBe 35

        paging.record(KEY, 1, 10)

        paging.offsetFor(KEY, 2) shouldBe 10
        paging.offsetFor(KEY, 3) shouldBe null
    }

    @Test
    fun `reset forgets a single key`() {
        val paging = KotatsuPaging()
        paging.record(KEY, 1, 20)
        paging.record(OTHER_KEY, 1, 30)

        paging.reset(KEY)

        paging.offsetFor(KEY, 2) shouldBe null
        paging.offsetFor(OTHER_KEY, 2) shouldBe 30
    }

    @Test
    fun `each key keeps its own running offset`() {
        val paging = KotatsuPaging()

        paging.record(KEY, 1, 20)
        paging.record(OTHER_KEY, 1, 5)

        paging.offsetFor(KEY, 2) shouldBe 20
        paging.offsetFor(OTHER_KEY, 2) shouldBe 5
    }

    private companion object {
        const val KEY = "POPULARITY|0"
        const val OTHER_KEY = "UPDATED|0"
    }
}
