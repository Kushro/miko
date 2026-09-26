package eu.kanade.domain.recommendation.providers

import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * MIKO — C14. Pure logic extracted from [UwasaProvider]'s keyword pass: which search hits count,
 * and which single group survives when several keywords all found something.
 */
@Execution(ExecutionMode.CONCURRENT)
class UwasaProviderTest {

    private fun sManga(url: String, title: String) = SManga.create().also {
        it.url = url
        it.title = title
    }

    @Test
    fun `filterUwasaSearchResults drops the seed and titles that don't contain the keyword`() {
        val seed = sManga("/seed", "Seed Title")
        val match = sManga("/match", "One Piece Extra")
        val noMatch = sManga("/no-match", "Something Else")

        val result = filterUwasaSearchResults(listOf(seed, match, noMatch), keyword = "piece", seedUrl = "/seed")

        result shouldContainExactly listOf(match)
    }

    @Test
    fun `filterUwasaSearchResults keeps the seed's url if the search result is a different url`() {
        val other = sManga("/other", "Piece of cake")

        val result = filterUwasaSearchResults(listOf(other), keyword = "piece", seedUrl = "/seed")

        result shouldContainExactly listOf(other)
    }

    @Test
    fun `filterUwasaSearchResults is case-insensitive on the title match`() {
        val match = sManga("/match", "ONE PIECE")

        val result = filterUwasaSearchResults(listOf(match), keyword = "piece", seedUrl = "/seed")

        result shouldContainExactly listOf(match)
    }

    @Test
    fun `selectSmallestNonEmptyGroup picks the smallest non-empty group`() {
        val big = "big" to listOf(sManga("/1", "A"), sManga("/2", "B"), sManga("/3", "C"))
        val small = "small" to listOf(sManga("/4", "D"))
        val empty = "empty" to emptyList<SManga>()

        val result = selectSmallestNonEmptyGroup(listOf(big, small, empty))

        result shouldBe small
    }

    @Test
    fun `selectSmallestNonEmptyGroup returns null when every group is empty`() {
        val result = selectSmallestNonEmptyGroup(listOf("a" to emptyList<SManga>(), "b" to emptyList()))

        result shouldBe null
    }

    @Test
    fun `selectSmallestNonEmptyGroup ignores empty groups when picking the smallest`() {
        val empty = "empty" to emptyList<SManga>()
        val onlyNonEmpty = "kept" to listOf(sManga("/1", "A"))

        val result = selectSmallestNonEmptyGroup(listOf(empty, onlyNonEmpty))

        result shouldBe onlyNonEmpty
    }
}
