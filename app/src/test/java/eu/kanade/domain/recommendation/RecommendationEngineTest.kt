package eu.kanade.domain.recommendation

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.manga.model.Manga
import java.util.Collections

/**
 * MIKO — C14. What the engine promises to the screen model: it runs exactly the configured
 * providers, a failing provider neither stops the others nor poisons the cache, and a clean run is
 * served from the cache on the next visit.
 */
@Execution(ExecutionMode.CONCURRENT)
class RecommendationEngineTest {

    private val source = FakeSource(7L)
    private val seed = Manga.create().copy(id = 1L, url = "/seed", source = 7L, ogTitle = "Seed")

    private class FakeProvider(
        override val id: RecommendationProviderId,
        private val available: Boolean = true,
        private val groups: List<RecommendationGroup> = emptyList(),
        private val throws: Throwable? = null,
    ) : RecommendationProvider {
        var calls = 0

        override fun isAvailable(source: Source, manga: Manga): Boolean = available

        override suspend fun recommend(
            source: Source,
            manga: Manga,
            onError: (Throwable) -> Unit,
            push: suspend (RecommendationGroup) -> Unit,
        ) {
            calls++
            throws?.let { throw it }
            groups.forEach { push(it) }
        }
    }

    private fun engine(settings: RecommendationSettings, providers: List<RecommendationProvider>): RecommendationEngine {
        // InMemoryPreferenceStore only honours values given at construction (set() is not stored).
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    "miko_recommendation_settings",
                    settings,
                    RecommendationSettings.default,
                ),
            ),
        )
        return RecommendationEngine(RecommendationPreferences(store), providers, RecommendationCache())
    }

    private fun group(id: RecommendationProviderId, keyword: String = "") =
        RecommendationGroup(id, keyword, listOf(Manga.create().copy(id = 2L, url = "/$keyword$id", source = 7L)))

    @Test
    fun `single mode runs only the selected provider`() = runTest {
        val osusume = FakeProvider(RecommendationProviderId.OSUSUME, groups = listOf(group(RecommendationProviderId.OSUSUME)))
        val uwasa = FakeProvider(RecommendationProviderId.UWASA, groups = listOf(group(RecommendationProviderId.UWASA)))
        val engine = engine(RecommendationSettings(single = RecommendationProviderId.UWASA), listOf(osusume, uwasa))

        val pushed = mutableListOf<RecommendationGroup>()
        engine.fetch(source, seed, onError = { error("unexpected $it") }) { pushed += it }

        engine.activeProviderIds() shouldContainExactly listOf(RecommendationProviderId.UWASA)
        pushed.map { it.provider } shouldContainExactly listOf(RecommendationProviderId.UWASA)
        osusume.calls shouldBe 0
        uwasa.calls shouldBe 1
    }

    @Test
    fun `multi mode runs every enabled provider and skips unavailable ones`() = runTest {
        val osusume = FakeProvider(RecommendationProviderId.OSUSUME, groups = listOf(group(RecommendationProviderId.OSUSUME)))
        val uwasa = FakeProvider(RecommendationProviderId.UWASA, groups = listOf(group(RecommendationProviderId.UWASA)))
        val tagu = FakeProvider(RecommendationProviderId.TAGU_OSEKKAI, available = false)
        val tracker = FakeProvider(RecommendationProviderId.TRACKER, groups = listOf(group(RecommendationProviderId.TRACKER)))
        val settings = RecommendationSettings(
            mode = RecommendationMode.MULTI,
            enabled = setOf(RecommendationProviderId.OSUSUME, RecommendationProviderId.UWASA, RecommendationProviderId.TAGU_OSEKKAI),
        )
        val engine = engine(settings, listOf(osusume, uwasa, tagu, tracker))

        val pushed = Collections.synchronizedList(mutableListOf<RecommendationGroup>())
        engine.fetch(source, seed, onError = { error("unexpected $it") }) { pushed += it }

        pushed.map { it.provider } shouldContainExactlyInAnyOrder
            listOf(RecommendationProviderId.OSUSUME, RecommendationProviderId.UWASA)
        tagu.calls shouldBe 0
        tracker.calls shouldBe 0
    }

    @Test
    fun `a throwing provider is reported and does not stop the others`() = runTest {
        val boom = IllegalStateException("boom")
        val osusume = FakeProvider(RecommendationProviderId.OSUSUME, throws = boom)
        val uwasa = FakeProvider(RecommendationProviderId.UWASA, groups = listOf(group(RecommendationProviderId.UWASA)))
        val settings = RecommendationSettings(
            mode = RecommendationMode.MULTI,
            enabled = setOf(RecommendationProviderId.OSUSUME, RecommendationProviderId.UWASA),
        )
        val engine = engine(settings, listOf(osusume, uwasa))

        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val pushed = Collections.synchronizedList(mutableListOf<RecommendationGroup>())
        engine.fetch(source, seed, onError = { errors += it }) { pushed += it }

        errors shouldContainExactly listOf(boom)
        pushed.map { it.provider } shouldContainExactly listOf(RecommendationProviderId.UWASA)
    }

    @Test
    fun `a clean run is cached and a failed run is retried`() = runTest {
        val clean = FakeProvider(RecommendationProviderId.UWASA, groups = listOf(group(RecommendationProviderId.UWASA, "k")))
        val failing = FakeProvider(RecommendationProviderId.OSUSUME, throws = IllegalStateException("boom"))
        val settings = RecommendationSettings(
            mode = RecommendationMode.MULTI,
            enabled = setOf(RecommendationProviderId.OSUSUME, RecommendationProviderId.UWASA),
        )
        val engine = engine(settings, listOf(clean, failing))

        repeat(2) {
            val pushed = Collections.synchronizedList(mutableListOf<RecommendationGroup>())
            engine.fetch(source, seed, onError = {}) { pushed += it }
            pushed.map { it.provider to it.keyword } shouldContainExactly listOf(RecommendationProviderId.UWASA to "k")
        }

        clean.calls shouldBe 1 // second visit served from the cache
        failing.calls shouldBe 2 // failures are never cached
    }

    private class FakeSource(override val id: Long) : Source {
        override val name: String = "Fake"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false
        override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
            throw UnsupportedOperationException()
        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = throw UnsupportedOperationException()
        override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()
    }
}
