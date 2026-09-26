package eu.kanade.domain.source.enhancement

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.i18n.miko.MKMR

/**
 * MIKO — unit tests for [SourceEnhancementRegistry].
 *
 * The point under test is the group switch: every lookup a consumer uses (features, comments, site
 * rating) must go dark when the user turns the group off, while the Settings-facing
 * [SourceEnhancementRegistry.enhancementsIn] keeps listing the enhancement so the switch can still
 * describe what it covers.
 */
class SourceEnhancementRegistryTest {

    private val asuraSource = FakeSource(id = ASURA_SCANS_SOURCE_ID)
    private val otherSource = FakeSource(id = 1L)

    private fun registry(groupEnabled: Boolean): SourceEnhancementRegistry {
        val store = if (groupEnabled) {
            InMemoryPreferenceStore()
        } else {
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference(
                        "miko_enhancement_group_${EnhancementGroup.ASURA_SCANS.key}",
                        false,
                        true,
                    ),
                ),
            )
        }
        return SourceEnhancementRegistry(
            enhancements = listOf(FakeEnhancement),
            preferences = EnhancementPreferences(store),
        )
    }

    @Test
    fun `a group is enabled by default`() {
        assertTrue(registry(groupEnabled = true).isEnabled(FakeEnhancement))
    }

    @Test
    fun `an enabled group exposes its enhancement to every consumer`() {
        val registry = registry(groupEnabled = true)

        assertEquals(listOf(FakeEnhancement), registry.enhancementsFor(asuraSource))
        assertTrue(registry.hasEnhancement(asuraSource))
        assertEquals(setOf(SourceFeature.CHAPTER_COMMENTS), registry.extraFeatures(asuraSource))
        assertNotNull(registry.commentsProvider(asuraSource))
        assertNotNull(registry.remoteRatingProvider(asuraSource))
    }

    @Test
    fun `a disabled group hides its enhancement from every consumer`() {
        val registry = registry(groupEnabled = false)

        assertFalse(registry.isEnabled(FakeEnhancement))
        assertEquals(emptyList<SourceEnhancement>(), registry.enhancementsFor(asuraSource))
        assertFalse(registry.hasEnhancement(asuraSource))
        assertEquals(emptySet<SourceFeature>(), registry.extraFeatures(asuraSource))
        assertNull(registry.commentsProvider(asuraSource))
        assertNull(registry.remoteRatingProvider(asuraSource))
    }

    @Test
    fun `enhancementsIn ignores the switch so Settings can always describe the group`() {
        listOf(true, false).forEach { enabled ->
            val registry = registry(groupEnabled = enabled)

            assertEquals(
                listOf(FakeEnhancement),
                registry.enhancementsIn(EnhancementGroup.ASURA_SCANS),
                "groupEnabled=$enabled",
            )
            assertEquals(listOf(FakeEnhancement), registry.all, "groupEnabled=$enabled")
        }
    }

    @Test
    fun `a source of another site is never enhanced`() {
        val registry = registry(groupEnabled = true)

        assertEquals(emptyList<SourceEnhancement>(), registry.enhancementsFor(otherSource))
        assertFalse(registry.hasEnhancement(otherSource))
        assertNull(registry.commentsProvider(otherSource))
    }

    /** Minimal enhancement carrying one of each optional provider, so all lookups are covered. */
    private object FakeEnhancement : SourceEnhancement {
        override val name: String = "Fake Asura enhancement"
        override val titleRes: StringResource = MKMR.strings.enhancement_asura_comments
        override val group: EnhancementGroup = EnhancementGroup.ASURA_SCANS
        override val extraFeatures: Set<SourceFeature> = setOf(SourceFeature.CHAPTER_COMMENTS)
        override val comments: SourceCommentsProvider = FakeComments
        override val remoteRating: RemoteRatingProvider = FakeRating
    }

    private object FakeComments : SourceCommentsProvider {
        override suspend fun getMangaComments(manga: SManga, page: Int, sort: CommentsSort) =
            SourceCommentsPage(comments = emptyList(), hasNextPage = false)

        override suspend fun getChapterComments(
            manga: SManga,
            chapter: SChapter,
            page: Int,
            sort: CommentsSort,
        ) = SourceCommentsPage(comments = emptyList(), hasNextPage = false)
    }

    private object FakeRating : RemoteRatingProvider {
        override suspend fun getRemoteRating(manga: SManga): Float = 0.96f
    }

    /** Minimal loaded source; only [id] matters to [EnhancementGroup.matches]. */
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
