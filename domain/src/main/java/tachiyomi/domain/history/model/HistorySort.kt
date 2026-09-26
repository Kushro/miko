package tachiyomi.domain.history.model

/**
 * MIKO — sort modes of the History screen. [LAST_READ] is upstream's order (and the only one that
 * groups entries under date headers).
 */
enum class HistorySort {
    LAST_READ,
    TITLE,
    SOURCE,
    CHAPTER_NUMBER,
    READ_DURATION,
    ;

    /** Date headers only make sense when the list is ordered by date. */
    val supportsDateHeaders: Boolean
        get() = this == LAST_READ
}
