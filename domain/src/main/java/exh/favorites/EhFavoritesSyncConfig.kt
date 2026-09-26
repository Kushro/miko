package exh.favorites

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

// MIKO -->

/**
 * One of the 10 fixed E-Hentai/ExHentai favorite slots (`favcat` 0..9) and the LOCAL category it
 * is mapped to.
 *
 * The mapping is keyed by category **name** (not id): backups restore categories by name and a
 * fresh install re-assigns ids, so names are the only stable handle. `RenameCategory` /
 * `DeleteCategory` keep the mapping in sync.
 */
@Serializable
data class EhFavoritesSlotMapping(
    val slot: Int,
    val categoryName: String? = null,
    val enabled: Boolean = false,
) {
    val isMapped: Boolean
        get() = !categoryName.isNullOrBlank()

    /** Enabled AND mapped: takes part in the sync (subject to the upstream-empty rule). */
    val isActive: Boolean
        get() = enabled && isMapped
}

/**
 * Persistent configuration of the E-Hentai favorites sync: exactly [SLOT_COUNT] entries, one per
 * upstream slot, always in slot order.
 */
@Serializable
data class EhFavoritesSyncConfig(
    val slots: List<EhFavoritesSlotMapping> = defaultSlots(),
) {

    /** Slots that are enabled and mapped to a category name. */
    val activeSlots: List<EhFavoritesSlotMapping>
        get() = slots.filter { it.isActive }

    /** Enabled+mapped slots whose local category is used by more than one enabled slot. */
    val duplicateCategoryNames: Set<String>
        get() = activeSlots.groupingBy { it.categoryName!! }.eachCount().filterValues { it > 1 }.keys

    /**
     * The sync may run only when at least one slot is active and no local category is mapped to
     * two enabled slots.
     */
    val isConfigured: Boolean
        get() = activeSlots.isNotEmpty() && duplicateCategoryNames.isEmpty()

    fun slotOf(index: Int): EhFavoritesSlotMapping = slots.getOrElse(index) { EhFavoritesSlotMapping(index) }

    fun categoryNameFor(slot: Int): String? = slotOf(slot).takeIf { it.isActive }?.categoryName

    fun slotFor(categoryName: String): Int? = activeSlots.firstOrNull { it.categoryName == categoryName }?.slot

    fun withSlot(slot: Int, transform: (EhFavoritesSlotMapping) -> EhFavoritesSlotMapping): EhFavoritesSyncConfig =
        copy(slots = normalized(slots).map { if (it.slot == slot) transform(it) else it })

    fun withCategoryRenamed(oldName: String, newName: String): EhFavoritesSyncConfig =
        copy(slots = normalized(slots).map { if (it.categoryName == oldName) it.copy(categoryName = newName) else it })

    fun withCategoryRemoved(name: String): EhFavoritesSyncConfig =
        copy(slots = normalized(slots).map { if (it.categoryName == name) it.copy(categoryName = null) else it })

    fun toJson(): String = json.encodeToString(serializer(), copy(slots = normalized(slots)))

    companion object {
        /** E-Hentai always exposes exactly 10 favorite slots (`favcat` 0..9). */
        const val SLOT_COUNT = 10

        val default = EhFavoritesSyncConfig()

        fun defaultSlots(): List<EhFavoritesSlotMapping> = List(SLOT_COUNT) { EhFavoritesSlotMapping(it) }

        /**
         * The legacy behaviour as an explicit mapping: slot i ↔ the i-th non-system local category
         * (by order), enabled. Used by the "Map by order" action of the settings dialog.
         */
        fun byOrder(categoryNamesInOrder: List<String>): EhFavoritesSyncConfig = EhFavoritesSyncConfig(
            slots = List(SLOT_COUNT) { index ->
                val name = categoryNamesInOrder.getOrNull(index)
                EhFavoritesSlotMapping(slot = index, categoryName = name, enabled = name != null)
            },
        )

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(raw: String?): EhFavoritesSyncConfig {
            if (raw.isNullOrBlank()) return default
            return try {
                json.decodeFromString(serializer(), raw).let { it.copy(slots = normalized(it.slots)) }
            } catch (e: Exception) {
                default
            }
        }

        /** Always exactly [SLOT_COUNT] entries, in slot order, one per slot. */
        private fun normalized(slots: List<EhFavoritesSlotMapping>): List<EhFavoritesSlotMapping> {
            val bySlot = slots.associateBy { it.slot }
            return List(SLOT_COUNT) { bySlot[it] ?: EhFavoritesSlotMapping(it) }
        }
    }
}

/**
 * What the favorites page says about one upstream slot: its name and how many galleries it holds.
 * Cached (app-state preference) after each sync / manual refresh, only for display in Settings.
 */
@Serializable
data class EhFavoritesUpstreamSlot(
    val slot: Int,
    val name: String,
    val count: Int,
) {
    val isEmpty: Boolean
        get() = count <= 0
}

object EhFavoritesUpstreamSlots {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(EhFavoritesUpstreamSlot.serializer())

    fun toJson(slots: List<EhFavoritesUpstreamSlot>): String = json.encodeToString(listSerializer, slots)

    fun fromJson(raw: String?): List<EhFavoritesUpstreamSlot> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(listSerializer, raw)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/**
 * Pure resolution of a [EhFavoritesSyncConfig] against the local categories and the upstream
 * counts, computed once per sync run. Everything the sync helper needs to translate between
 * upstream slots and local category ids lives here so it can be unit-tested without Android.
 *
 * @param categoryIdBySlot enabled+mapped slots whose category exists locally
 * @param missingCategoryNames enabled+mapped slots whose category name no longer exists locally
 * @param upstreamCounts galleries per upstream slot as counted from the fetched favorites
 */
data class EhFavoritesSyncPlan(
    val categoryIdBySlot: Map<Int, Long>,
    val missingCategoryNames: List<String>,
    val upstreamCounts: Map<Int, Int>,
) {
    val slotByCategoryId: Map<Long, Int> = categoryIdBySlot.entries.associate { (slot, id) -> id to slot }

    /** Mapped, enabled, existing locally AND non-empty upstream: the slots this run touches. */
    val activeSlots: Set<Int> = categoryIdBySlot.keys.filterTo(sortedSetOf()) { (upstreamCounts[it] ?: 0) > 0 }

    /** Enabled+mapped slots skipped only because they are empty upstream. */
    val skippedEmptySlots: Set<Int> = categoryIdBySlot.keys.filterTo(sortedSetOf()) { (upstreamCounts[it] ?: 0) <= 0 }

    fun categoryIdFor(slot: Int): Long? = categoryIdBySlot[slot]

    fun slotFor(categoryId: Long): Int? = slotByCategoryId[categoryId]

    fun isActive(slot: Int): Boolean = slot in activeSlots

    companion object {
        /**
         * @param localCategories `(id, name)` of the non-system local categories
         * @param upstreamSlotOfEachGallery the `fav` index of every fetched upstream gallery
         */
        fun build(
            config: EhFavoritesSyncConfig,
            localCategories: List<Pair<Long, String>>,
            upstreamSlotOfEachGallery: List<Int>,
        ): EhFavoritesSyncPlan {
            val idByName = localCategories.associate { (id, name) -> name to id }
            val categoryIdBySlot = linkedMapOf<Int, Long>()
            val missing = mutableListOf<String>()
            config.activeSlots.forEach { mapping ->
                val name = mapping.categoryName!!
                val id = idByName[name]
                if (id == null) missing += name else categoryIdBySlot[mapping.slot] = id
            }
            val counts = upstreamSlotOfEachGallery
                .filter { it in 0 until EhFavoritesSyncConfig.SLOT_COUNT }
                .groupingBy { it }
                .eachCount()
            return EhFavoritesSyncPlan(categoryIdBySlot, missing, counts)
        }
    }
}
// MIKO <--
