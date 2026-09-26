package tachiyomi.domain.chapter.model

/**
 * MIKO — the *kind* of a chapter bookmark (`chapters.bookmark`).
 *
 * Every bookmarked chapter is a [GENERIC] favorite by default; the user can refine it into one of
 * the other kinds. The value lives in its own table (`chapter_bookmark_types`, one row per chapter
 * whose kind is not [GENERIC]) so the inherited `chapters` schema, mappers and backup stay
 * untouched — same pattern as `manga_ratings`. Un-bookmarking a chapter drops its row.
 *
 * @param id the stable value stored in the database and in backups. Never renumber.
 */
enum class ChapterBookmarkType(val id: Int) {
    GENERIC(0),
    PLOT_TWIST(1),
    CHARACTER_GROWTH(2),
    ART(3),
    ;

    companion object {
        /** Unknown ids (e.g. from a newer backup) fall back to [GENERIC]. */
        fun fromId(id: Int?): ChapterBookmarkType = entries.firstOrNull { it.id == id } ?: GENERIC
    }
}
