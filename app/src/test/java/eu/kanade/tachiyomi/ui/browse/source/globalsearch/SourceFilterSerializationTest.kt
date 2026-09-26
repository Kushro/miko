package eu.kanade.tachiyomi.ui.browse.source.globalsearch

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * MIKO — unit tests for [SourceFilter.Serializer], the string form stored in
 * `SourcePreferences.globalSearchPinnedState()`.
 */
class SourceFilterSerializationTest {

    @Test
    fun `All and PinnedOnly keep the strings the old enum wrote`() {
        SourceFilter.Serializer.serialize(SourceFilter.All) shouldBe "All"
        SourceFilter.Serializer.serialize(SourceFilter.PinnedOnly) shouldBe "PinnedOnly"
    }

    @Test
    fun `values stored by the previous enum still decode`() {
        SourceFilter.Serializer.deserialize("All") shouldBe SourceFilter.All
        SourceFilter.Serializer.deserialize("PinnedOnly") shouldBe SourceFilter.PinnedOnly
    }

    @Test
    fun `a preset is stored with the PRESET prefix`() {
        SourceFilter.Serializer.serialize(SourceFilter.Preset("Manga")) shouldBe "PRESET:Manga"
    }

    @Test
    fun `round-trips every case`() {
        listOf(
            SourceFilter.All,
            SourceFilter.PinnedOnly,
            SourceFilter.Preset("Manga"),
            SourceFilter.Preset("Weird|Name"),
            SourceFilter.Preset("PRESET:looks like a prefix"),
            SourceFilter.Preset("  spaces  "),
            SourceFilter.Preset("日本語"),
        ).forEach { filter ->
            SourceFilter.Serializer.deserialize(SourceFilter.Serializer.serialize(filter)) shouldBe filter
        }
    }

    @Test
    fun `unknown or malformed values fall back to the default`() {
        SourceFilter.Serializer.deserialize("") shouldBe SourceFilter.PinnedOnly
        SourceFilter.Serializer.deserialize("garbage") shouldBe SourceFilter.PinnedOnly
        SourceFilter.Serializer.deserialize("ALL") shouldBe SourceFilter.PinnedOnly
        SourceFilter.Serializer.deserialize("Preset:Manga") shouldBe SourceFilter.PinnedOnly
        // A preset name is never empty, so `"PRESET:"` alone is not a valid selection.
        SourceFilter.Serializer.deserialize("PRESET:") shouldBe SourceFilter.PinnedOnly
    }

    @Test
    fun `presets with the same name are equal, so chip selection compares by value`() {
        val selected: SourceFilter = SourceFilter.Preset("Manga")

        (selected == SourceFilter.Preset("Manga")) shouldBe true
        (selected == SourceFilter.Preset("manga")) shouldBe false
        (selected == SourceFilter.All) shouldBe false
    }
}
