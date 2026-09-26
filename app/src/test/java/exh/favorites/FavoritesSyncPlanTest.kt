package exh.favorites

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

/**
 * MIKO — unit tests of the pure slot filtering used by the E-Hentai favorites sync
 * (see `FavoritesSyncPlanning.kt` and `docs/features/favorites-sync/en.md`).
 */
@Execution(ExecutionMode.CONCURRENT)
class FavoritesSyncPlanTest {

    private val categories = listOf(
        10L to "Reading",
        20L to "Later",
        30L to "Done",
        40L to "Unmapped",
    )

    /**
     * slot 0 → "Reading" (2 galleries upstream), slot 1 → "Later" (empty upstream),
     * slot 2 → "Done" but disabled, slot 5 → "Gone" (category deleted locally, only in [missing]).
     */
    private fun plan(
        upstreamSlotOfEachGallery: List<Int> = listOf(0, 0, 3),
        config: EhFavoritesSyncConfig = EhFavoritesSyncConfig.default
            .withSlot(0) { it.copy(categoryName = "Reading", enabled = true) }
            .withSlot(1) { it.copy(categoryName = "Later", enabled = true) }
            .withSlot(2) { it.copy(categoryName = "Done", enabled = false) },
    ) = EhFavoritesSyncPlan.build(
        config = config,
        localCategories = categories,
        upstreamSlotOfEachGallery = upstreamSlotOfEachGallery,
    )

    @Test
    fun `only mapped, enabled and non-empty slots are active`() {
        val plan = plan()
        plan.activeSlots shouldBe setOf(0)
        plan.skippedEmptySlots shouldBe setOf(1)
        plan.isActive(0) shouldBe true
        plan.isActive(1) shouldBe false
        plan.isActive(2) shouldBe false
        plan.isActive(3) shouldBe false
    }

    @Test
    fun `a gallery takes the slot of its first mapped and active category`() {
        val plan = plan()
        slotOfLocalCategories(plan, listOf(10L)) shouldBe 0
        // "Later" is mapped but empty upstream → not active → dropped
        slotOfLocalCategories(plan, listOf(20L)) shouldBe null
        // "Done" is mapped but disabled → dropped
        slotOfLocalCategories(plan, listOf(30L)) shouldBe null
        // Only unmapped categories → dropped
        slotOfLocalCategories(plan, listOf(40L)) shouldBe null
        // The first mapped category wins, unmapped ones are ignored
        slotOfLocalCategories(plan, listOf(40L, 10L)) shouldBe 0
        slotOfLocalCategories(plan, emptyList()) shouldBe null
    }

    @Test
    fun `a gallery whose first mapped category is inactive falls through to the next active one`() {
        val plan = plan()
        // "Later" comes first but is inactive (empty upstream): it takes no part in this run, so the
        // gallery syncs through "Reading" — consistent with the multiple-categories check, which
        // only counts active categories.
        slotOfLocalCategories(plan, listOf(20L, 10L)) shouldBe 0
        // Only inactive/unmapped categories → dropped
        slotOfLocalCategories(plan, listOf(20L, 40L)) shouldBe null
    }

    @Test
    fun `entries of inactive slots never enter a change set`() {
        val plan = plan()
        val entries = listOf(0 to "a", 1 to "b", 2 to "c", 9 to "d")
        filterEntriesForPlan(plan, entries) { it.first } shouldBe listOf(0 to "a")
    }

    @Test
    fun `an unconfigured mapping produces no active slot at all`() {
        val plan = plan(config = EhFavoritesSyncConfig.default)
        plan.activeSlots shouldBe emptySet()
        plan.skippedEmptySlots shouldBe emptySet()
        filterEntriesForPlan(plan, listOf(0, 1, 2)) { it } shouldBe emptyList()
        slotOfLocalCategories(plan, listOf(10L, 20L)) shouldBe null
    }

    @Test
    fun `a slot mapped to a deleted category is reported and never active`() {
        val plan = plan(
            config = EhFavoritesSyncConfig.default
                .withSlot(0) { it.copy(categoryName = "Reading", enabled = true) }
                .withSlot(5) { it.copy(categoryName = "Gone", enabled = true) },
            upstreamSlotOfEachGallery = listOf(0, 5, 5),
        )
        plan.missingCategoryNames shouldBe listOf("Gone")
        plan.activeSlots shouldBe setOf(0)
        plan.categoryIdFor(5) shouldBe null
        filterEntriesForPlan(plan, listOf(0, 5)) { it } shouldBe listOf(0)
    }

    @Test
    fun `every gallery of a mapped slot resolves back to its local category`() {
        val plan = plan(upstreamSlotOfEachGallery = listOf(0, 0, 1, 1))
        plan.activeSlots shouldBe setOf(0, 1)
        plan.categoryIdFor(0) shouldBe 10L
        plan.categoryIdFor(1) shouldBe 20L
        plan.slotFor(10L) shouldBe 0
        plan.slotFor(40L) shouldBe null
        slotOfLocalCategories(plan, listOf(20L)) shouldBe 1
    }
}
