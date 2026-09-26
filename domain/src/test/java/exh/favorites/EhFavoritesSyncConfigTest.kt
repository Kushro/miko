package exh.favorites

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class EhFavoritesSyncConfigTest {

    @Test
    fun `default config has 10 unmapped disabled slots and is not configured`() {
        val config = EhFavoritesSyncConfig.default
        config.slots.size shouldBe EhFavoritesSyncConfig.SLOT_COUNT
        config.slots.map { it.slot } shouldContainExactly (0 until 10).toList()
        config.activeSlots shouldBe emptyList()
        config.isConfigured shouldBe false
    }

    @Test
    fun `mapped but disabled slot does not configure the sync`() {
        val config = EhFavoritesSyncConfig.default.withSlot(3) { it.copy(categoryName = "Reading") }
        config.isConfigured shouldBe false
        config.withSlot(3) { it.copy(enabled = true) }.isConfigured shouldBe true
    }

    @Test
    fun `enabled but unmapped slot does not configure the sync`() {
        EhFavoritesSyncConfig.default.withSlot(0) { it.copy(enabled = true) }.isConfigured shouldBe false
    }

    @Test
    fun `same category on two enabled slots is a duplicate and blocks the sync`() {
        val config = EhFavoritesSyncConfig.default
            .withSlot(0) { it.copy(categoryName = "A", enabled = true) }
            .withSlot(1) { it.copy(categoryName = "A", enabled = true) }
        config.duplicateCategoryNames shouldBe setOf("A")
        config.isConfigured shouldBe false
        // Disabling one of them resolves it.
        config.withSlot(1) { it.copy(enabled = false) }.isConfigured shouldBe true
    }

    @Test
    fun `json round trip keeps slots ordered and complete`() {
        val config = EhFavoritesSyncConfig.default
            .withSlot(9) { it.copy(categoryName = "Z", enabled = true) }
            .withSlot(2) { it.copy(categoryName = "B", enabled = false) }
        val decoded = EhFavoritesSyncConfig.fromJson(config.toJson())
        decoded shouldBe config
        decoded.slots.map { it.slot } shouldContainExactly (0 until 10).toList()
    }

    @Test
    fun `garbage or blank json decodes to the default`() {
        EhFavoritesSyncConfig.fromJson("") shouldBe EhFavoritesSyncConfig.default
        EhFavoritesSyncConfig.fromJson("{not json") shouldBe EhFavoritesSyncConfig.default
    }

    @Test
    fun `partial json is normalized to 10 slots`() {
        val decoded = EhFavoritesSyncConfig.fromJson(
            """{"slots":[{"slot":4,"categoryName":"Only","enabled":true}]}""",
        )
        decoded.slots.size shouldBe 10
        decoded.categoryNameFor(4) shouldBe "Only"
        decoded.slotFor("Only") shouldBe 4
        decoded.categoryNameFor(0) shouldBe null
    }

    @Test
    fun `rename and delete of a category follow into the mapping`() {
        val config = EhFavoritesSyncConfig.default
            .withSlot(1) { it.copy(categoryName = "Old", enabled = true) }
        config.withCategoryRenamed("Old", "New").categoryNameFor(1) shouldBe "New"
        val removed = config.withCategoryRemoved("Old")
        removed.slotOf(1).categoryName shouldBe null
        removed.slotOf(1).enabled shouldBe true
        removed.isConfigured shouldBe false
    }

    @Test
    fun `byOrder reproduces the legacy positional mapping`() {
        val config = EhFavoritesSyncConfig.byOrder(listOf("A", "B", "C"))
        config.categoryNameFor(0) shouldBe "A"
        config.categoryNameFor(2) shouldBe "C"
        config.slotOf(3).isMapped shouldBe false
        config.slotOf(3).enabled shouldBe false
        config.activeSlots.size shouldBe 3
    }

    @Test
    fun `plan resolves ids, reports missing categories and skips empty upstream slots`() {
        val config = EhFavoritesSyncConfig.default
            .withSlot(0) { it.copy(categoryName = "A", enabled = true) }
            .withSlot(1) { it.copy(categoryName = "Gone", enabled = true) }
            .withSlot(2) { it.copy(categoryName = "C", enabled = true) }
            .withSlot(3) { it.copy(categoryName = "D", enabled = false) }
        val plan = EhFavoritesSyncPlan.build(
            config = config,
            localCategories = listOf(10L to "A", 20L to "C", 30L to "D"),
            // slot 0 has 2 galleries, slot 2 none, slot 3 (disabled) has 1, slot 7 (unmapped) has 1
            upstreamSlotOfEachGallery = listOf(0, 0, 3, 7, -1),
        )
        plan.categoryIdBySlot shouldBe mapOf(0 to 10L, 2 to 20L)
        plan.missingCategoryNames shouldBe listOf("Gone")
        plan.upstreamCounts shouldBe mapOf(0 to 2, 3 to 1, 7 to 1)
        plan.activeSlots shouldBe setOf(0)
        plan.skippedEmptySlots shouldBe setOf(2)
        plan.categoryIdFor(0) shouldBe 10L
        plan.slotFor(20L) shouldBe 2
        plan.slotFor(30L) shouldBe null
        plan.isActive(2) shouldBe false
    }

    @Test
    fun `upstream slots cache round trips`() {
        val slots = listOf(EhFavoritesUpstreamSlot(0, "Favorites 0", 12), EhFavoritesUpstreamSlot(1, "Reading", 0))
        EhFavoritesUpstreamSlots.fromJson(EhFavoritesUpstreamSlots.toJson(slots)) shouldBe slots
        EhFavoritesUpstreamSlots.fromJson("nope") shouldBe emptyList()
        slots[1].isEmpty shouldBe true
    }
}
