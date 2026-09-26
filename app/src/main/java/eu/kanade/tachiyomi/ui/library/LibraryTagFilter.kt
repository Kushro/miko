package eu.kanade.tachiyomi.ui.library

// MIKO -->
/**
 * MIKO — the include/exclude predicate of the library's local-tag filter.
 *
 * Semantics mirror KMK's category filter: an entry passes when it has **every** included tag and
 * **none** of the excluded ones. An entry with no local tags only passes when nothing is included
 * (an exclusion can never hide it, since it has nothing to exclude).
 *
 * Every comparison is case-insensitive and ignores surrounding whitespace, matching the
 * `(manga_id, name COLLATE NOCASE)` identity of `manga_tags`.
 */
fun matchesTagFilter(
    tags: Collection<String>,
    included: Collection<String>,
    excluded: Collection<String>,
): Boolean {
    if (included.isEmpty() && excluded.isEmpty()) return true

    val mangaTags = tags.map(::normalizeLibraryTag).filter { it.isNotEmpty() }.toSet()

    // Early return: nothing to match against.
    if (mangaTags.isEmpty()) return included.isEmpty()

    val isExcluded = excluded.any { normalizeLibraryTag(it) in mangaTags }
    val isIncluded = included.isEmpty() || included.all { normalizeLibraryTag(it) in mangaTags }

    return !isExcluded && isIncluded
}

/** Canonical form a tag display name is compared and stored in the filter preferences with. */
fun normalizeLibraryTag(tag: String): String = tag.trim().lowercase()
// MIKO <--
