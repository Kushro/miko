package eu.kanade.domain.recommendation.providers

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * MIKO — C14 (I2). [TagFilterMatcher] is the pure core of [TaguOsekkaiProvider]: whether a seed tag
 * is found among a source's filters, how it is selected, and how the results it returns are scored
 * — exercised here against synthetic [FilterList]s so no real [eu.kanade.tachiyomi.source.Source] is
 * needed, per `Filter.Group<CheckBox>`/`Filter.Group<TriState>`/`Filter.Select` (Mihon extensions and
 * Kotatsu's `KotatsuFilterBuilder` both boil down to these three shapes).
 */
@Execution(ExecutionMode.CONCURRENT)
class TaguOsekkaiProviderTest {

    private class TestCheckBox(name: String) : Filter.CheckBox(name)
    private class TestCheckGroup(name: String, state: List<TestCheckBox>) : Filter.Group<TestCheckBox>(name, state)
    private class TestTriState(name: String) : Filter.TriState(name)
    private class TestTriGroup(name: String, state: List<TestTriState>) : Filter.Group<TestTriState>(name, state)
    private class TestSelect(name: String, values: Array<String>) : Filter.Select<String>(name, values)

    // region normalize

    @Test
    fun `normalize lowercases and strips everything but letters and digits`() {
        TagFilterMatcher.normalize("Slice-of-Life 2") shouldBe "sliceoflife2"
        TagFilterMatcher.normalize("  Isekai!! ") shouldBe "isekai"
    }

    // endregion
    // region matches / mark

    @Test
    fun `matches and marks a CheckBox child of a Group by normalized name`() {
        val target = TestCheckBox("Slice of Life")
        val filters = FilterList(TestCheckGroup("Genres", listOf(TestCheckBox("Isekai"), target)))

        TagFilterMatcher.matches(filters, "sliceoflife") shouldBe true
        target.state shouldBe false

        TagFilterMatcher.mark(filters, "sliceoflife") shouldBe true
        target.state shouldBe true
    }

    @Test
    fun `matches and marks a TriState child of a Group by normalized name`() {
        val target = TestTriState("Comedy")
        val filters = FilterList(TestTriGroup("Genres", listOf(TestTriState("Drama"), target)))

        TagFilterMatcher.mark(filters, "comedy") shouldBe true
        target.state shouldBe Filter.TriState.STATE_INCLUDE
    }

    @Test
    fun `matches and marks a Select value by normalized text`() {
        val select = TestSelect("Genre", arrayOf("Any", "Action", "Isekai"))
        val filters = FilterList(select)

        TagFilterMatcher.mark(filters, "isekai") shouldBe true
        select.state shouldBe 2
    }

    @Test
    fun `no match leaves filters untouched and reports false`() {
        val checkbox = TestCheckBox("Isekai")
        val filters = FilterList(TestCheckGroup("Genres", listOf(checkbox)))

        TagFilterMatcher.matches(filters, "romance") shouldBe false
        TagFilterMatcher.mark(filters, "romance") shouldBe false
        checkbox.state shouldBe false
    }

    // endregion
    // region score

    @Test
    fun `score is the squared share of seed tags found in the result genre`() {
        val seed = setOf("isekai", "comedy", "romance", "drama")
        // 2 of 4 seed tags present -> (2/4)^2 = 0.25
        TagFilterMatcher.score("Isekai, Comedy, Action", seed) shouldBe 0.25
    }

    @Test
    fun `score is 1 when every seed tag is present`() {
        val seed = setOf("isekai", "comedy")
        TagFilterMatcher.score("Comedy, Isekai, Slice of Life", seed) shouldBe 1.0
    }

    @Test
    fun `score is 0 without genre or without seed tags`() {
        TagFilterMatcher.score(null, setOf("isekai")) shouldBe 0.0
        TagFilterMatcher.score("", setOf("isekai")) shouldBe 0.0
        TagFilterMatcher.score("Isekai", emptySet()) shouldBe 0.0
    }

    // endregion
    // region ordering

    private data class Ranked(val title: String, val genre: String?)

    @Test
    fun `sorting by score descending is stable for equal scores`() {
        val seed = setOf("isekai")
        val results = listOf(
            Ranked("A", "Isekai"),
            Ranked("B", "Comedy"),
            Ranked("C", "Isekai"),
            Ranked("D", null),
        )

        val ranked = results.sortedByDescending { TagFilterMatcher.score(it.genre, seed) }

        // A and C tie at score 1.0 and must keep their relative order; B and D tie at 0.0 likewise.
        ranked.map { it.title } shouldBe listOf("A", "C", "B", "D")
    }

    // endregion
}
