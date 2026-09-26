package eu.kanade.domain.recommendation.providers

import eu.kanade.domain.recommendation.RecommendationSettings
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.library.service.RuijiTitleClusterer

/**
 * MIKO — C19. Zokuhen's interesting logic is pure (ladder building + Dice acceptance/ranking), so
 * it is tested here without a real `Source`, like the other provider tests.
 */
@Execution(ExecutionMode.CONCURRENT)
class ZokuhenProviderTest {

    // --- zokuhenSearchQueries ---

    @Test
    fun `trailing volume number is dropped and the ladder decreases one word per step`() {
        zokuhenSearchQueries("Blue Eyes 1") shouldContainExactly listOf("Blue Eyes", "Blue")
    }

    @Test
    fun `vol part season roman and fused markers are stripped iteratively`() {
        zokuhenSearchQueries("Blue Eyes Vol. 3") shouldContainExactly listOf("Blue Eyes", "Blue")
        zokuhenSearchQueries("Blue Eyes Part 2") shouldContainExactly listOf("Blue Eyes", "Blue")
        zokuhenSearchQueries("Blue Eyes Season 2") shouldContainExactly listOf("Blue Eyes", "Blue")
        zokuhenSearchQueries("Blue Eyes II") shouldContainExactly listOf("Blue Eyes", "Blue")
        zokuhenSearchQueries("Blue Eyes v2") shouldContainExactly listOf("Blue Eyes", "Blue")
    }

    @Test
    fun `a separator left dangling after its number is stripped too`() {
        zokuhenSearchQueries("Blue Eyes - 2") shouldContainExactly listOf("Blue Eyes", "Blue")
    }

    @Test
    fun `a number that is the whole name is kept`() {
        zokuhenSearchQueries("86") shouldContainExactly listOf("86")
    }

    @Test
    fun `a single-word title produces a single query`() {
        zokuhenSearchQueries("Berserk") shouldContainExactly listOf("Berserk")
    }

    @Test
    fun `a spaceless title drops its trailing digits when enough remains`() {
        zokuhenSearchQueries("ワンピース2") shouldContainExactly listOf("ワンピース")
    }

    @Test
    fun `the ladder is capped at three prefixes`() {
        zokuhenSearchQueries("One Two Three Four Five") shouldContainExactly listOf(
            "One Two Three Four Five",
            "One Two Three Four",
            "One Two Three",
        )
    }

    @Test
    fun `at least one token always survives the stripping`() {
        zokuhenSearchQueries("Vol 2") shouldContainExactly listOf("Vol")
        zokuhenSearchQueries("") shouldBe emptyList()
        zokuhenSearchQueries("   ") shouldBe emptyList()
    }

    // --- rankZokuhenResults ---

    private fun manga(title: String, url: String = "/$title"): SManga =
        SManga.create().apply {
            this.title = title
            this.url = url
        }

    private fun seeds(vararg titles: String): List<String> =
        titles.map { RuijiTitleClusterer.normalizeTitle(it) }

    private val defaultThreshold = RecommendationSettings.DEFAULT_ZOKUHEN_THRESHOLD

    @Test
    fun `the sequel passes and unrelated titles sharing a word do not`() {
        val results = listOf(manga("Blue Lock"), manga("Blue Eyes 2"), manga("Blue Period"))
        rankZokuhenResults(results, seeds("Blue Eyes 1"), "/seed", defaultThreshold)
            .map { it.title } shouldContainExactly listOf("Blue Eyes 2")
    }

    @Test
    fun `the seed itself is removed even on a perfect title match`() {
        val results = listOf(manga("Blue Eyes 1", url = "/seed"), manga("Blue Eyes 2"))
        rankZokuhenResults(results, seeds("Blue Eyes 1"), "/seed", defaultThreshold)
            .map { it.title } shouldContainExactly listOf("Blue Eyes 2")
    }

    @Test
    fun `survivors are sorted by similarity descending`() {
        val results = listOf(manga("Blue Eyes 2"), manga("Blue Eyes"))
        rankZokuhenResults(results, seeds("Blue Eyes 1"), "/seed", defaultThreshold)
            .map { it.title } shouldContainExactly listOf("Blue Eyes", "Blue Eyes 2")
    }

    @Test
    fun `a stricter threshold drops what the default accepts`() {
        // "Blue Eyes 2" scores 0.875 against the seed, "Blue Eyes" 0.933 — only the latter
        // survives a 90 % threshold.
        val results = listOf(manga("Blue Eyes 2"), manga("Blue Eyes"))
        rankZokuhenResults(results, seeds("Blue Eyes 1"), "/seed", 90)
            .map { it.title } shouldContainExactly listOf("Blue Eyes")
    }

    @Test
    fun `the best score over all seed titles counts`() {
        // The Japanese candidate is nothing like the localized seed but matches the original title.
        val results = listOf(manga("ブルーアイズ 2"))
        rankZokuhenResults(results, seeds("Blue Eyes 1", "ブルーアイズ 1"), "/seed", defaultThreshold)
            .map { it.title } shouldContainExactly listOf("ブルーアイズ 2")
    }

    @Test
    fun `the group is capped at MAX_RESULTS`() {
        val results = (1..25).map { manga("Blue Eyes $it", url = "/u$it") }
        rankZokuhenResults(results, seeds("Blue Eyes 1"), "/seed", defaultThreshold).size shouldBe
            ZokuhenProvider.MAX_RESULTS
    }
}
