package eu.kanade.domain.source.model

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * MIKO — C20. The presets object is the only thing persisted for the eye dialogs' shared presets,
 * so the invariants [SourceHidePresets.normalized] promises (unique names, no blanks/empties,
 * tolerant JSON) are what protect both screens from a bad or old value.
 */
@Execution(ExecutionMode.CONCURRENT)
class SourceHidePresetsTest {

    @Test
    fun `json round trip keeps names, ids and order`() {
        val presets = SourceHidePresets(
            presets = listOf(
                SourceHidePreset("NSFW", setOf(1L, 2L, 3L)),
                SourceHidePreset("Kotatsu", setOf(42L)),
            ),
        )
        val restored = SourceHidePresets.fromJson(presets.toJson())
        restored.presets.map { it.name } shouldContainExactly listOf("NSFW", "Kotatsu")
        restored.presets[0].sourceIds shouldBe setOf(1L, 2L, 3L)
        restored.presets[1].sourceIds shouldBe setOf(42L)
    }

    @Test
    fun `garbage, blank and null json fall back to empty`() {
        SourceHidePresets.fromJson(null) shouldBe SourceHidePresets.EMPTY
        SourceHidePresets.fromJson("") shouldBe SourceHidePresets.EMPTY
        SourceHidePresets.fromJson("not json at all") shouldBe SourceHidePresets.EMPTY
        SourceHidePresets.fromJson("""{"presets": 12}""") shouldBe SourceHidePresets.EMPTY
    }

    @Test
    fun `unknown keys from a newer build are ignored per field`() {
        val raw = """{"presets":[{"name":"A","sourceIds":[7],"color":"red"}],"futureFlag":true}"""
        val restored = SourceHidePresets.fromJson(raw)
        restored.presets.map { it.name } shouldContainExactly listOf("A")
        restored.presets[0].sourceIds shouldBe setOf(7L)
    }

    @Test
    fun `normalized drops blanks and empties and dedupes by name keeping the latest ids`() {
        val presets = SourceHidePresets(
            presets = listOf(
                SourceHidePreset("A", setOf(1L)),
                SourceHidePreset("  ", setOf(2L)),
                SourceHidePreset("B", emptySet()),
                SourceHidePreset("A", setOf(3L)),
            ),
        ).normalized()
        presets.presets.map { it.name } shouldContainExactly listOf("A")
        presets.presets[0].sourceIds shouldBe setOf(3L)
    }

    @Test
    fun `save replaces by name and delete removes`() {
        val base = SourceHidePresets.EMPTY.save("A", setOf(1L)).save("B", setOf(2L))
        base.presets.map { it.name } shouldContainExactly listOf("A", "B")

        val replaced = base.save("A", setOf(9L))
        replaced.presets.first { it.name == "A" }.sourceIds shouldBe setOf(9L)

        val deleted = replaced.delete("B")
        deleted.presets.map { it.name } shouldContainExactly listOf("A")
    }
}
