package eu.kanade.domain.source.model

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import exh.source.MERGED_SOURCE_ID
import exh.source.eHentaiSourceIds
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.model.StubSource
import tachiyomi.source.kotatsu.KotatsuSource
import tachiyomi.source.local.LocalSource

/**
 * MIKO — unit tests for `classifySourceKind` (see `SourceKindClassifier.kt`).
 *
 * Only the pure function is exercised: `Source.kind` and `sourceKindOf` resolve `SourceManager`,
 * `ExtensionManager` and `SourceEnhancementRegistry` through Injekt, which is not bootstrapped in a
 * JVM unit test.
 */
class SourceKindClassifierTest {

    private val plainSource = FakeSource(id = 42L)

    /**
     * A runtime source that also implements [KotatsuSource].
     *
     * It has to be a mock: `KotatsuSource.parserSource` is typed `MangaParserSource`, and the
     * parsers library is an `implementation` dependency of `:source-kotatsu`, so that type is not on
     * the app module's compile classpath and the interface cannot be implemented by hand here.
     * `classifySourceKind` only does an `is` check, so no member is ever called.
     */
    private val kotatsuSource: Source = mockk("kotatsu-source", true, KotatsuSource::class)

    private fun classify(
        id: Long = 42L,
        runtime: Source? = plainSource,
        hasInstalledExtension: Boolean = false,
        hasEnhancement: Boolean = false,
    ) = classifySourceKind(id, runtime, hasInstalledExtension, hasEnhancement)

    @Test
    fun `the local source is LOCAL whatever the runtime says`() {
        assertEquals(SourceKind.LOCAL, classify(id = LocalSource.ID, runtime = null))
        assertEquals(SourceKind.LOCAL, classify(id = LocalSource.ID, hasInstalledExtension = true))
    }

    @Test
    fun `app-native ids are BUILT_IN_DEDICATED before anything else is looked at`() {
        assertEquals(SourceKind.BUILT_IN_DEDICATED, classify(id = MERGED_SOURCE_ID, runtime = null))
        eHentaiSourceIds.forEach { id ->
            assertEquals(SourceKind.BUILT_IN_DEDICATED, classify(id = id, runtime = null), "id $id")
        }
    }

    @Test
    fun `a missing runtime source is NOT_INSTALLED`() {
        assertEquals(SourceKind.NOT_INSTALLED, classify(runtime = null))
        assertEquals(SourceKind.NOT_INSTALLED, classify(runtime = null, hasInstalledExtension = true))
    }

    @Test
    fun `a stub is NOT_INSTALLED`() {
        val stub = StubSource(id = 42L, lang = "en", name = "Gone")

        assertEquals(SourceKind.NOT_INSTALLED, classify(runtime = stub))
        assertEquals(SourceKind.NOT_INSTALLED, classify(runtime = stub, hasEnhancement = true))
    }

    @Test
    fun `a Kotatsu parser is BUILT_IN, or BUILT_IN_DEDICATED with an enhancement`() {
        assertEquals(SourceKind.BUILT_IN, classify(runtime = kotatsuSource))
        assertEquals(SourceKind.BUILT_IN_DEDICATED, classify(runtime = kotatsuSource, hasEnhancement = true))
    }

    @Test
    fun `a bundled parser stays built-in even when an extension declares the same id`() {
        assertEquals(
            SourceKind.BUILT_IN,
            classify(runtime = kotatsuSource, hasInstalledExtension = true),
        )
    }

    @Test
    fun `an installed extension is EXTENSION, or EXTENSION_ENHANCED with an enhancement`() {
        assertEquals(SourceKind.EXTENSION, classify(hasInstalledExtension = true))
        assertEquals(
            SourceKind.EXTENSION_ENHANCED,
            classify(hasInstalledExtension = true, hasEnhancement = true),
        )
    }

    @Test
    fun `an unattributable loaded source falls back to the extension kinds`() {
        assertEquals(SourceKind.EXTENSION, classify(hasInstalledExtension = false))
        assertEquals(
            SourceKind.EXTENSION_ENHANCED,
            classify(hasInstalledExtension = false, hasEnhancement = true),
        )
    }

    @Test
    fun `every kind is reachable`() {
        val reached = setOf(
            classify(id = LocalSource.ID, runtime = null),
            classify(runtime = kotatsuSource),
            classify(id = MERGED_SOURCE_ID, runtime = null),
            classify(hasInstalledExtension = true),
            classify(hasInstalledExtension = true, hasEnhancement = true),
            classify(runtime = null),
        )

        assertEquals(SourceKind.entries.toSet(), reached)
    }

    /** Minimal loaded source that is neither a stub, nor a Kotatsu parser, nor an SY delegate. */
    private class FakeSource(
        override val id: Long,
        override val name: String = "Fake",
        override val lang: String = "en",
        override val supportsLatest: Boolean = false,
    ) : Source {
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
