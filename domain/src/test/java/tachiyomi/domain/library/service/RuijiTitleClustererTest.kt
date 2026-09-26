package tachiyomi.domain.library.service

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class RuijiTitleClustererTest {

    // region normalizeTitle

    @Test
    fun `normalizeTitle lowercases and strips symbols and spaces`() {
        RuijiTitleClusterer.normalizeTitle("Hello, World! 123") shouldBe "helloworld123"
    }

    @Test
    fun `normalizeTitle preserves CJK characters`() {
        RuijiTitleClusterer.normalizeTitle("進撃の巨人") shouldBe "進撃の巨人"
    }

    @Test
    fun `normalizeTitle preserves Cyrillic characters`() {
        RuijiTitleClusterer.normalizeTitle("Мой Тайтл!") shouldBe "мойтайтл"
    }

    @Test
    fun `normalizeTitle of empty string is empty`() {
        RuijiTitleClusterer.normalizeTitle("") shouldBe ""
    }

    // endregion

    // region similarity

    @Test
    fun `similarity of two empty strings is zero, not one`() {
        RuijiTitleClusterer.similarity("", "") shouldBe 0.0
    }

    @Test
    fun `similarity is zero when only one side is empty`() {
        RuijiTitleClusterer.similarity("", "abc") shouldBe 0.0
        RuijiTitleClusterer.similarity("abc", "") shouldBe 0.0
    }

    @Test
    fun `similarity of identical non-empty strings is one`() {
        RuijiTitleClusterer.similarity("onepiece", "onepiece") shouldBe 1.0
    }

    @Test
    fun `similarity is zero when either side is shorter than two chars and they differ`() {
        RuijiTitleClusterer.similarity("a", "ab") shouldBe 0.0
        RuijiTitleClusterer.similarity("ab", "a") shouldBe 0.0
        RuijiTitleClusterer.similarity("a", "b") shouldBe 0.0
    }

    @Test
    fun `similarity matches a hand-computed Dice value with bigram multiplicity`() {
        // bigrams("aaa") = {aa, aa} (multiset, size 2); bigrams("aa") = {aa} (size 1).
        // Multiset intersection respects multiplicity: min(2, 1) = 1.
        // Dice = 2*1 / (2+1) = 2/3.
        RuijiTitleClusterer.similarity("aaa", "aa") shouldBe (2.0 / 3.0)
    }

    @Test
    fun `similarity is symmetric`() {
        RuijiTitleClusterer.similarity("aaa", "aa") shouldBe RuijiTitleClusterer.similarity("aa", "aaa")
        RuijiTitleClusterer.similarity("abcd", "bcde") shouldBe RuijiTitleClusterer.similarity("bcde", "abcd")
    }

    // endregion

    // region cluster

    @Test
    fun `cluster connects a transitive chain into one cluster even when the ends fall under threshold`() {
        // bigrams(abcd) = {ab,bc,cd}; bigrams(bcde) = {bc,cd,de}; bigrams(cdef) = {cd,de,ef}.
        // sim(abcd,bcde) = 2*2/6 = 0.667 (66%) ; sim(bcde,cdef) = 2*2/6 = 0.667 (66%) ; both >= 50.
        // sim(abcd,cdef) = 2*1/6 = 0.333 (33%) ; below 50 on its own.
        val titles = listOf("abcd", "bcde", "cdef")

        val result = RuijiTitleClusterer.cluster(titles, thresholdPercent = 50)

        result[0] shouldBe result[1]
        result[1] shouldBe result[2]
    }

    @Test
    fun `cluster keeps pairs under threshold in separate clusters`() {
        val titles = listOf("wxyz", "stuv")

        val result = RuijiTitleClusterer.cluster(titles, thresholdPercent = 50)

        result[0] shouldNotBe result[1]
    }

    @Test
    fun `cluster at threshold 100 only groups genuinely identical titles`() {
        val titles = listOf("abc", "abd", "abc")

        val result = RuijiTitleClusterer.cluster(titles, thresholdPercent = 100)

        result[0] shouldBe result[2]
        result[1] shouldNotBe result[0]
    }

    @Test
    fun `cluster never groups empty titles with each other`() {
        val titles = listOf("", "")

        val result = RuijiTitleClusterer.cluster(titles, thresholdPercent = 50)

        result[0] shouldNotBe result[1]
    }

    @Test
    fun `cluster is deterministic for the same input`() {
        val titles = listOf("abcd", "bcde", "cdef", "wxyz", "stuv", "")

        val first = RuijiTitleClusterer.cluster(titles, thresholdPercent = 50)
        val second = RuijiTitleClusterer.cluster(titles, thresholdPercent = 50)

        first shouldBe second
    }

    @Test
    fun `cluster result size equals input size`() {
        val titles = listOf("abcd", "bcde", "cdef", "wxyz", "stuv", "")

        RuijiTitleClusterer.cluster(titles, thresholdPercent = 50).size shouldBe titles.size
    }

    @Test
    fun `cluster roots are consistent - equal roots iff same cluster`() {
        val titles = listOf("abcd", "bcde", "cdef", "wxyz", "stuv")

        val result = RuijiTitleClusterer.cluster(titles, thresholdPercent = 50)

        // abcd/bcde/cdef form one chain-connected cluster (see the transitive-chain test above).
        result[0] shouldBe result[1]
        result[1] shouldBe result[2]
        // wxyz and stuv share no bigrams with anything, including each other.
        result[3] shouldNotBe result[0]
        result[4] shouldNotBe result[0]
        result[3] shouldNotBe result[4]
    }

    // endregion
}
