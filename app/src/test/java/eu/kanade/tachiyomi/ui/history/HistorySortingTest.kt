package eu.kanade.tachiyomi.ui.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.model.HistorySort
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * MIKO — unit tests for the History ordering (see `HistorySorting.kt`).
 */
class HistorySortingTest {

    private val sourceNames = mapOf(
        1L to "zenith",
        2L to "Alpha",
        3L to "beta",
    )

    private fun sourceNameOf(sourceId: Long): String = sourceNames[sourceId].orEmpty()

    private fun history(
        id: Long,
        title: String = "Title $id",
        sourceId: Long = 1L,
        chapterNumber: Double = 1.0,
        readDuration: Long = 0L,
        readAt: Date? = Date(id * 1000L),
    ) = HistoryWithRelations(
        id = id,
        chapterId = id,
        mangaId = id,
        ogTitle = title,
        chapterNumber = chapterNumber,
        read = false,
        lastPageRead = 0L,
        totalCountCalculated = 1L,
        readCountCalculated = 0L,
        readAt = readAt,
        readDuration = readDuration,
        coverData = MangaCover(
            mangaId = id,
            sourceId = sourceId,
            // Keep it false: a favorite cover resolves the custom thumbnail through Injekt.
            isMangaFavorite = false,
            ogUrl = null,
            lastModified = 0L,
        ),
    )

    private fun sort(
        list: List<HistoryWithRelations>,
        sort: HistorySort,
        descending: Boolean = false,
    ) = sortHistory(list, sort, descending, ::sourceNameOf).map { it.id }

    @Test
    fun `LAST_READ descending puts the most recent entry first`() {
        val list = listOf(history(1), history(3), history(2))

        assertEquals(listOf(3L, 2L, 1L), sort(list, HistorySort.LAST_READ, descending = true))
        assertEquals(listOf(1L, 2L, 3L), sort(list, HistorySort.LAST_READ))
    }

    @Test
    fun `entries without a read date sink to the bottom of LAST_READ in both directions`() {
        val list = listOf(history(1, readAt = null), history(2), history(3))

        assertEquals(listOf(3L, 2L, 1L), sort(list, HistorySort.LAST_READ, descending = true))
        assertEquals(listOf(2L, 3L, 1L), sort(list, HistorySort.LAST_READ))
    }

    @Test
    fun `TITLE ignores case`() {
        val list = listOf(
            history(1, title = "banana"),
            history(2, title = "Apple"),
            history(3, title = "cherry"),
        )

        assertEquals(listOf(2L, 1L, 3L), sort(list, HistorySort.TITLE))
        assertEquals(listOf(3L, 1L, 2L), sort(list, HistorySort.TITLE, descending = true))
    }

    @Test
    fun `SOURCE orders by the display name of the source and then by title`() {
        val list = listOf(
            history(1, title = "b", sourceId = 1L), // zenith
            history(2, title = "z", sourceId = 2L), // Alpha
            history(3, title = "a", sourceId = 2L), // Alpha
            history(4, title = "a", sourceId = 3L), // beta
        )

        assertEquals(listOf(3L, 2L, 4L, 1L), sort(list, HistorySort.SOURCE))
        // Descending reverses the whole comparator, secondary title key included.
        assertEquals(listOf(1L, 4L, 2L, 3L), sort(list, HistorySort.SOURCE, descending = true))
    }

    @Test
    fun `CHAPTER_NUMBER orders numerically`() {
        val list = listOf(
            history(1, chapterNumber = 10.0),
            history(2, chapterNumber = 2.5),
            history(3, chapterNumber = 9.0),
        )

        assertEquals(listOf(2L, 3L, 1L), sort(list, HistorySort.CHAPTER_NUMBER))
        assertEquals(listOf(1L, 3L, 2L), sort(list, HistorySort.CHAPTER_NUMBER, descending = true))
    }

    @Test
    fun `READ_DURATION orders by time spent reading`() {
        val list = listOf(
            history(1, readDuration = 500L),
            history(2, readDuration = 0L),
            history(3, readDuration = 1_500L),
        )

        assertEquals(listOf(3L, 1L, 2L), sort(list, HistorySort.READ_DURATION, descending = true))
        assertEquals(listOf(2L, 1L, 3L), sort(list, HistorySort.READ_DURATION))
    }

    @Test
    fun `ties fall back to the most recently read, whatever the direction`() {
        val list = listOf(
            history(1, title = "same", readAt = Date(1_000L)),
            history(2, title = "same", readAt = Date(3_000L)),
            history(3, title = "same", readAt = Date(2_000L)),
        )

        assertEquals(listOf(2L, 3L, 1L), sort(list, HistorySort.TITLE))
        assertEquals(listOf(2L, 3L, 1L), sort(list, HistorySort.TITLE, descending = true))
    }

    @Test
    fun `lists of less than two entries are returned untouched`() {
        val single = listOf(history(7))

        assertEquals(emptyList<Long>(), sort(emptyList(), HistorySort.TITLE))
        assertEquals(listOf(7L), sort(single, HistorySort.SOURCE, descending = true))
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() {
            // `HistoryWithRelations.title` resolves `GetCustomMangaInfo` through Injekt while the
            // instance is being built, so it has to be registered before the first construction.
            val alreadyRegistered = runCatching { Injekt.get<GetCustomMangaInfo>() }.isSuccess
            if (alreadyRegistered) return
            Injekt.addSingletonFactory {
                GetCustomMangaInfo(
                    object : CustomMangaRepository {
                        override fun get(mangaId: Long): CustomMangaInfo? = null
                        override fun set(mangaInfo: CustomMangaInfo) = Unit
                    },
                )
            }
        }
    }
}
