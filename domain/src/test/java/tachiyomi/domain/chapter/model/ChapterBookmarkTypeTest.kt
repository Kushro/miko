package tachiyomi.domain.chapter.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * MIKO — the ids of [ChapterBookmarkType] are persisted in `chapter_bookmark_types.type` and in
 * backups (`BackupChapterBookmarkType.type`), so they are part of the on-disk format and must not
 * move.
 */
@Execution(ExecutionMode.CONCURRENT)
class ChapterBookmarkTypeTest {

    @Test
    fun `ids are stable and contiguous from zero`() {
        ChapterBookmarkType.GENERIC.id shouldBe 0
        ChapterBookmarkType.PLOT_TWIST.id shouldBe 1
        ChapterBookmarkType.CHARACTER_GROWTH.id shouldBe 2
        ChapterBookmarkType.ART.id shouldBe 3
        ChapterBookmarkType.entries.map { it.id } shouldBe listOf(0, 1, 2, 3)
    }

    @Test
    fun `fromId resolves every known id`() {
        ChapterBookmarkType.entries.forEach { type ->
            ChapterBookmarkType.fromId(type.id) shouldBe type
        }
    }

    @Test
    fun `fromId falls back to GENERIC for unknown ids`() {
        ChapterBookmarkType.fromId(4) shouldBe ChapterBookmarkType.GENERIC
        ChapterBookmarkType.fromId(-1) shouldBe ChapterBookmarkType.GENERIC
        ChapterBookmarkType.fromId(Int.MAX_VALUE) shouldBe ChapterBookmarkType.GENERIC
    }

    @Test
    fun `fromId falls back to GENERIC for null`() {
        ChapterBookmarkType.fromId(null) shouldBe ChapterBookmarkType.GENERIC
    }
}
