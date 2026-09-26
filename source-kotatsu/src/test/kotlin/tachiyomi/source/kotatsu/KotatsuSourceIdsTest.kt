package tachiyomi.source.kotatsu

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.koitharu.kotatsu.parsers.model.MangaParserSource

@Execution(ExecutionMode.CONCURRENT)
class KotatsuSourceIdsTest {

    @Test
    fun `ids are unique across every parser source`() {
        val bySource = MangaParserSource.entries.associateWith { KotatsuSourceIds.idOf(it) }
        val duplicates = bySource.entries
            .groupBy({ it.value }, { it.key.name })
            .filterValues { it.size > 1 }

        duplicates.entries.map { "${it.key} <- ${it.value}" }.shouldBeEmpty()
        bySource.values.toSet().size shouldBe MangaParserSource.entries.size
    }

    @Test
    fun `ids never collide with the ids reserved by the app`() {
        val reserved = RESERVED_SOURCE_IDS
        val clashes = MangaParserSource.entries
            .filter { KotatsuSourceIds.idOf(it) in reserved }
            .map { it.name }

        clashes.shouldBeEmpty()
    }

    @Test
    fun `ids are positive`() {
        // Mihon treats the sign bit as free space; a negative id would break source lookups.
        val negative = MangaParserSource.entries
            .filter { KotatsuSourceIds.idOf(it) < 0L }
            .map { it.name }

        negative.shouldBeEmpty()
    }

    @Test
    fun `ids are derived from the prefixed entry name and are stable`() {
        KotatsuSourceIds.KEY_PREFIX shouldBe "kotatsu/"

        val first = KotatsuSourceIds.idOf("MANGADEX")
        first shouldBe KotatsuSourceIds.idOf("MANGADEX")
        first shouldNotBe KotatsuSourceIds.idOf("MANGADEXX")
        // The prefix is what keeps these apart from extension ids built as "<name>/<lang>/<version>".
        first shouldNotBe KotatsuSourceIds.idOf("")
    }

    @Test
    fun `preference key matches the file used by Mihon per-source settings`() {
        KotatsuSourceIds.preferenceKeyOf("MANGADEX") shouldBe "source_${KotatsuSourceIds.idOf("MANGADEX")}"
    }

    private companion object {

        /**
         * Ids the app hands out itself, copied from `source-local/LocalSource.ID` and
         * `source-api/src/commonMain/kotlin/exh/source/SourceIds.kt`. Only the hand-picked small
         * ids and the delegated-source constants are listed: the per-language extension id maps in
         * that file are themselves MD5-derived, so a clash with them is not something this test
         * could meaningfully guard against.
         */
        val RESERVED_SOURCE_IDS = setOf(
            0L, // LocalSource.ID
            6900L, // LEWD_SOURCE_SERIES
            6901L, // EH_OLD_ID
            6902L, // EXH_OLD_ID
            6907L, // NHENTAI_OLD_ID
            6909L, // TSUMINO_OLD_ID
            6912L, // HBROWSE_OLD_ID
            6969L, // MERGED_SOURCE_ID
            1713178126840476467L, // EH_SOURCE_ID
            6225928719850211219L, // EXH_SOURCE_ID
            7309872737163460316L, // NHENTAI_SOURCE_ID
            2221515250486218861L, // PURURIN_SOURCE_ID
            6707338697138388238L, // TSUMINO_SOURCE_ID
            1802675169972965535L, // EIGHTMUSES_SOURCE_ID
            1401584337232758222L, // HBROWSE_SOURCE_ID
        )
    }
}
