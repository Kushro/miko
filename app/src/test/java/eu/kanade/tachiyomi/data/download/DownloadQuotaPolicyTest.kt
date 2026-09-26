package eu.kanade.tachiyomi.data.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MIKO — unit tests for the download storage quota selection (see `PurgeCandidate.kt`).
 */
class DownloadQuotaPolicyTest {

    private val mb = 1024L * 1024L

    private fun candidate(
        chapterId: Long,
        sizeMb: Long,
        lastModified: Long,
        mangaId: Long = 1L,
    ) = PurgeCandidate(
        chapterId = chapterId,
        mangaId = mangaId,
        sizeBytes = sizeMb * mb,
        lastModified = lastModified,
    )

    private val candidates = listOf(
        candidate(chapterId = 1, sizeMb = 10, lastModified = 3_000),
        candidate(chapterId = 2, sizeMb = 10, lastModified = 1_000),
        candidate(chapterId = 3, sizeMb = 10, lastModified = 2_000),
    )

    @Test
    fun `no quota purges nothing`() {
        assertTrue(selectChaptersToPurge(candidates, usageBytes = 30 * mb, quotaBytes = 0).isEmpty())
        assertTrue(selectChaptersToPurge(candidates, usageBytes = 30 * mb, quotaBytes = -1).isEmpty())
    }

    @Test
    fun `under or exactly at the quota purges nothing`() {
        assertTrue(selectChaptersToPurge(candidates, usageBytes = 20 * mb, quotaBytes = 30 * mb).isEmpty())
        assertTrue(selectChaptersToPurge(candidates, usageBytes = 30 * mb, quotaBytes = 30 * mb).isEmpty())
    }

    @Test
    fun `over the quota purges the oldest first`() {
        val selected = selectChaptersToPurge(candidates, usageBytes = 30 * mb, quotaBytes = 25 * mb)

        assertEquals(listOf(2L), selected.map { it.chapterId })
    }

    @Test
    fun `purges as many as needed, oldest first, and not one more`() {
        val selected = selectChaptersToPurge(candidates, usageBytes = 30 * mb, quotaBytes = 10 * mb)

        assertEquals(listOf(2L, 3L), selected.map { it.chapterId })
    }

    @Test
    fun `ties on last modified are broken by chapter id`() {
        val tied = listOf(
            candidate(chapterId = 7, sizeMb = 5, lastModified = 1_000),
            candidate(chapterId = 4, sizeMb = 5, lastModified = 1_000),
            candidate(chapterId = 9, sizeMb = 5, lastModified = 1_000),
        )

        val selected = selectChaptersToPurge(tied, usageBytes = 15 * mb, quotaBytes = 8 * mb)

        assertEquals(listOf(4L, 7L), selected.map { it.chapterId })
    }

    @Test
    fun `purging everything is not enough when the candidates don't cover the excess`() {
        val selected = selectChaptersToPurge(candidates, usageBytes = 100 * mb, quotaBytes = 10 * mb)

        assertEquals(listOf(2L, 3L, 1L), selected.map { it.chapterId })
    }

    @Test
    fun `no candidates yields an empty selection`() {
        val selected = selectChaptersToPurge(emptyList(), usageBytes = 100 * mb, quotaBytes = 10 * mb)

        assertTrue(selected.isEmpty())
    }
}
