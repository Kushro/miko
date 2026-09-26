package tachiyomi.domain.chapter.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.BookmarkedChapter
import tachiyomi.domain.chapter.model.ChapterBookmarkType
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterBookmarkRepository
import tachiyomi.domain.chapter.repository.ChapterRepository

/**
 * MIKO — choosing a kind is also how a chapter gets bookmarked, so the order of the two writes
 * matters: the chapters must be marked `bookmark = true` *before* the kind is stored, otherwise the
 * `clear_chapter_bookmark_type_on_unbookmark` trigger would race the row away.
 */
class SetChapterBookmarkTypeTest {

    private lateinit var log: MutableList<String>
    private lateinit var updates: MutableList<ChapterUpdate>
    private lateinit var repository: FakeChapterBookmarkRepository
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var setChapterBookmarkType: SetChapterBookmarkType

    @BeforeEach
    fun beforeEach() {
        log = mutableListOf()
        updates = mutableListOf()
        repository = FakeChapterBookmarkRepository(log)
        chapterRepository = mockk(relaxed = true)
        coEvery { chapterRepository.updateAll(any()) } answers {
            log += "updateAll"
            updates += firstArg<List<ChapterUpdate>>()
        }
        setChapterBookmarkType = SetChapterBookmarkType(repository, chapterRepository)
    }

    @Test
    fun `bookmarks the chapter before writing the kind`() = runTest {
        setChapterBookmarkType.await(1L, ChapterBookmarkType.PLOT_TWIST)

        log shouldBe listOf("updateAll", "setTypes")
        updates shouldBe listOf(ChapterUpdate(id = 1L, bookmark = true))
        repository.types.toMap() shouldBe mapOf(1L to ChapterBookmarkType.PLOT_TWIST)
    }

    @Test
    fun `bookmarks every chapter of a multi-selection`() = runTest {
        setChapterBookmarkType.awaitAll(listOf(1L, 2L, 3L), ChapterBookmarkType.ART)

        updates.map { it.id } shouldBe listOf(1L, 2L, 3L)
        updates.map { it.bookmark } shouldBe listOf(true, true, true)
        repository.types.toMap() shouldBe mapOf(
            1L to ChapterBookmarkType.ART,
            2L to ChapterBookmarkType.ART,
            3L to ChapterBookmarkType.ART,
        )
    }

    @Test
    fun `GENERIC drops the stored kind but keeps the bookmark`() = runTest {
        setChapterBookmarkType.await(1L, ChapterBookmarkType.CHARACTER_GROWTH)
        repository.types.toMap() shouldBe mapOf(1L to ChapterBookmarkType.CHARACTER_GROWTH)

        setChapterBookmarkType.await(1L, ChapterBookmarkType.GENERIC)

        repository.types.toMap() shouldBe emptyMap()
        repository.getType(1L) shouldBe ChapterBookmarkType.GENERIC
        // The bookmark flag itself is never cleared here: that stays with UpdateChapter.
        updates.map { it.bookmark } shouldBe listOf(true, true)
    }

    @Test
    fun `an empty selection touches nothing`() = runTest {
        setChapterBookmarkType.awaitAll(emptyList(), ChapterBookmarkType.PLOT_TWIST)

        log shouldBe emptyList()
        updates shouldBe emptyList()
        repository.types.toMap() shouldBe emptyMap()
    }
}

/** In-memory stand-in: "no row" is [ChapterBookmarkType.GENERIC], like the SQLDelight impl. */
private class FakeChapterBookmarkRepository(
    private val log: MutableList<String>,
) : ChapterBookmarkRepository {

    val types = mutableMapOf<Long, ChapterBookmarkType>()

    override fun subscribeTypesByMangaId(mangaId: Long): Flow<Map<Long, ChapterBookmarkType>> = flowOf(types.toMap())

    override fun subscribeAllTypes(): Flow<Map<Long, ChapterBookmarkType>> = flowOf(types.toMap())

    override suspend fun getTypesByMangaId(mangaId: Long): Map<Long, ChapterBookmarkType> = types.toMap()

    override suspend fun getType(chapterId: Long): ChapterBookmarkType =
        types[chapterId] ?: ChapterBookmarkType.GENERIC

    override suspend fun setType(chapterId: Long, type: ChapterBookmarkType) {
        setTypes(listOf(chapterId), type)
    }

    override suspend fun setTypes(chapterIds: List<Long>, type: ChapterBookmarkType) {
        log += "setTypes"
        chapterIds.forEach { chapterId ->
            if (type == ChapterBookmarkType.GENERIC) {
                types.remove(chapterId)
            } else {
                types[chapterId] = type
            }
        }
    }

    override fun subscribeBookmarkedChapters(): Flow<List<BookmarkedChapter>> = flowOf(emptyList())
}
