package eu.kanade.domain.recommendation.providers

import eu.kanade.tachiyomi.source.model.SManga
import exh.recs.sources.RECOMMENDS_SOURCE
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * MIKO — C14. Pure logic extracted from [TrackerProvider]: mapping a tracker's raw [SManga]
 * results into the *external* [tachiyomi.domain.manga.model.Manga] entries the UI treats specially
 * (never persisted, opens smart search / the tracker's site instead of `MangaScreen`).
 */
@Execution(ExecutionMode.CONCURRENT)
class TrackerProviderTest {

    @Test
    fun `toExternalRecommendations maps every result to the external source and keeps its title and url`() {
        val recs = listOf(
            SManga.create().also {
                it.url = "https://anilist.co/manga/1"
                it.title = "One Piece"
            },
            SManga.create().also {
                it.url = "https://anilist.co/manga/2"
                it.title = "Naruto"
            },
        )

        val result = recs.toExternalRecommendations()

        result.size shouldBe 2
        result.forEach { it.source shouldBe RECOMMENDS_SOURCE }
        result.forEach { it.id shouldBe -1L }
        result.map { it.url } shouldBe recs.map { it.url }
        result.map { it.ogTitle } shouldBe recs.map { it.title }
    }

    @Test
    fun `toExternalRecommendations on an empty list stays empty`() {
        val result = emptyList<SManga>().toExternalRecommendations()

        result.size shouldBe 0
    }
}
