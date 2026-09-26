// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(InternalParsersApi::class)

package tachiyomi.source.kotatsu

import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.util.generateUid
import tachiyomi.source.kotatsu.mapping.absoluteUrl
import tachiyomi.source.kotatsu.mapping.buildChapterName
import tachiyomi.source.kotatsu.mapping.formatChapterNumber
import tachiyomi.source.kotatsu.mapping.toManga
import tachiyomi.source.kotatsu.mapping.toSChapter
import tachiyomi.source.kotatsu.mapping.toSChapters
import tachiyomi.source.kotatsu.mapping.toSManga
import tachiyomi.source.kotatsu.mapping.toSMangaStatus

@Execution(ExecutionMode.CONCURRENT)
class KotatsuModelMapperTest {

    private val parser = FakeMangaParser()
    private val baseUrl = "https://${FakeMangaParser.DOMAIN}"

    @Test
    fun `manga state maps to the matching SManga status`() {
        MangaState.ONGOING.toSMangaStatus() shouldBe SManga.ONGOING
        MangaState.FINISHED.toSMangaStatus() shouldBe SManga.COMPLETED
        MangaState.PAUSED.toSMangaStatus() shouldBe SManga.ON_HIATUS
        MangaState.ABANDONED.toSMangaStatus() shouldBe SManga.CANCELLED
        MangaState.RESTRICTED.toSMangaStatus() shouldBe SManga.LICENSED
        MangaState.UPCOMING.toSMangaStatus() shouldBe SManga.UNKNOWN
    }

    @Test
    fun `an unknown state maps to SManga UNKNOWN`() {
        val unknown: MangaState? = null
        unknown.toSMangaStatus() shouldBe SManga.UNKNOWN
    }

    @Test
    fun `manga maps url title cover authors and tags`() {
        val manga = parser.mangas.first()

        val sManga = manga.toSManga(initialized = true)

        sManga.url shouldBe "/manga/first"
        sManga.title shouldBe "First"
        // The large cover wins so the details screen does not download the thumbnail twice.
        sManga.thumbnail_url shouldBe manga.largeCoverUrl
        sManga.author shouldBe "Author One"
        sManga.artist shouldBe "Author Two, Author Three"
        sManga.genre shouldBe "Action, Comedy"
        sManga.status shouldBe SManga.ONGOING
        sManga.initialized shouldBe true
    }

    @Test
    fun `manga from a listing is not marked as initialized`() {
        parser.mangas.first().toSManga().initialized shouldBe false
    }

    @Test
    fun `SManga round trips back into the manga the parser needs`() {
        val original = parser.mangas.first()

        val rebuilt = original.toSManga().toManga(parser, baseUrl)

        rebuilt.url shouldBe original.url
        rebuilt.id shouldBe parser.generateUid(original.url)
        rebuilt.source shouldBe parser.source
        rebuilt.publicUrl shouldBe original.publicUrl
    }

    @Test
    fun `chapter name prefixes the volume and drops a trailing zero decimal`() {
        buildChapterName(title = null, number = 3f, volume = 2) shouldBe "Vol. 2 Chapter 3"
        buildChapterName(title = null, number = 3.5f, volume = 0) shouldBe "Chapter 3.5"
        buildChapterName(title = "Named", number = 4f, volume = 1) shouldBe "Vol. 1 Named"
        buildChapterName(title = "  ", number = 0f, volume = 0) shouldBe "Chapter"
    }

    @Test
    fun `chapter number formatting`() {
        3f.formatChapterNumber() shouldBe "3"
        3.5f.formatChapterNumber() shouldBe "3.5"
    }

    @Test
    fun `an unknown chapter number becomes negative one`() {
        chapter(number = 0f).toSChapter().chapter_number shouldBe -1f
        chapter(number = 2f).toSChapter().chapter_number shouldBe 2f
    }

    @Test
    fun `scanlator and branch are merged and deduplicated`() {
        chapter(number = 1f, scanlator = "Scans", branch = "English").toSChapter().scanlator shouldBe "Scans · English"
        chapter(number = 1f, scanlator = "Scans", branch = "Scans").toSChapter().scanlator shouldBe "Scans"
        chapter(number = 1f, scanlator = null, branch = null).toSChapter().scanlator shouldBe null
    }

    @Test
    fun `an ascending chapter list is reversed to newest first`() {
        val ascending = listOf(chapter(number = 1f), chapter(number = 2f), chapter(number = 3f))

        ascending.toSChapters().map { it.chapter_number } shouldBe listOf(3f, 2f, 1f)
    }

    @Test
    fun `a chapter list already sorted newest first is left alone`() {
        val descending = listOf(chapter(number = 3f), chapter(number = 2f), chapter(number = 1f))

        descending.toSChapters().map { it.chapter_number } shouldBe listOf(3f, 2f, 1f)
    }

    @Test
    fun `chapters without numbers fall back to the upload date to detect the order`() {
        val ascending = listOf(
            chapter(number = 0f, uploadDate = 1_000L),
            chapter(number = 0f, uploadDate = 2_000L),
        )

        ascending.toSChapters().map { it.date_upload } shouldBe listOf(2_000L, 1_000L)
    }

    @Test
    fun `relative urls are resolved against the base url`() {
        absoluteUrl(baseUrl, "/manga/first") shouldBe "https://fake.test/manga/first"
        absoluteUrl(baseUrl, "manga/first") shouldBe "https://fake.test/manga/first"
        absoluteUrl(baseUrl, "https://other.test/x") shouldBe "https://other.test/x"
        absoluteUrl(baseUrl, "//other.test/x") shouldBe "https://other.test/x"
        absoluteUrl(baseUrl, "") shouldBe baseUrl
    }

    private fun chapter(
        number: Float,
        scanlator: String? = "Scans",
        branch: String? = null,
        uploadDate: Long = 0L,
    ) = MangaChapter(
        id = number.toLong(),
        title = null,
        number = number,
        volume = 0,
        url = "/manga/first/$number",
        scanlator = scanlator,
        uploadDate = uploadDate,
        branch = branch,
        source = parser.source,
    )
}
