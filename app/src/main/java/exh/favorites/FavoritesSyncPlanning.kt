package exh.favorites

// MIKO -->

/**
 * The upstream slot a local gallery belongs to: the first of its [categoryIds] that [plan] maps to
 * an ACTIVE slot (mapped + enabled + non-empty upstream). Categories mapped to inactive slots take
 * no part in this run, so they are skipped over — the same rule the "gallery in several mapped
 * categories" check applies (it only counts active categories). Returns `null` when the gallery
 * only lives in categories that take no part in this sync run.
 *
 * Pure on purpose: it is the whole "which galleries does this sync touch" rule of
 * [LocalFavoritesStorage], unit-tested without Android, Injekt or the network.
 */
fun slotOfLocalCategories(plan: EhFavoritesSyncPlan, categoryIds: List<Long>): Int? =
    categoryIds.asSequence().mapNotNull { plan.slotFor(it) }.firstOrNull(plan::isActive)

/** Keeps only the entries whose upstream slot is active in [plan]. */
fun <T> filterEntriesForPlan(plan: EhFavoritesSyncPlan, entries: List<T>, slotOf: (T) -> Int): List<T> =
    entries.filter { plan.isActive(slotOf(it)) }
// MIKO <--
