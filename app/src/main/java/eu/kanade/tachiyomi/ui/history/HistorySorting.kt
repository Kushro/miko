package eu.kanade.tachiyomi.ui.history

import tachiyomi.domain.history.model.HistorySort
import tachiyomi.domain.history.model.HistoryWithRelations

/*
 * MIKO — pure ordering of the History list.
 *
 * The `history:` query always comes back ordered by `readAt DESC`, so every other order is applied
 * here, in Kotlin, over the already-filtered list. Keeping it out of SQL means no schema change and
 * lets [HistorySort.SOURCE] order by the *display* name of the source, which only the app module
 * can resolve (`SourceManager.getOrStub`).
 */

/**
 * Orders [list] according to [sort].
 *
 * [descending] reverses the whole comparator of the mode — including the title used as the
 * secondary key of [HistorySort.SOURCE]. Entries that were never read (`readAt == null`, which the
 * `history:` query does not return today but the model allows) always sink to the bottom of
 * [HistorySort.LAST_READ], whatever the direction. Ties are broken by "most recently read first",
 * and `sortedWith` is stable, so the incoming (`readAt DESC`) order survives full ties.
 *
 * @param sourceNameOf display name of a source id; the caller is expected to cache it.
 */
fun sortHistory(
    list: List<HistoryWithRelations>,
    sort: HistorySort,
    descending: Boolean,
    sourceNameOf: (Long) -> String,
): List<HistoryWithRelations> {
    if (list.size < 2) return list

    val byMode: Comparator<HistoryWithRelations> = when (sort) {
        HistorySort.LAST_READ -> compareBy { it.readAt?.time ?: Long.MIN_VALUE }
        HistorySort.TITLE -> compareBy<HistoryWithRelations, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        HistorySort.SOURCE ->
            compareBy<HistoryWithRelations, String>(String.CASE_INSENSITIVE_ORDER) {
                sourceNameOf(it.coverData.sourceId)
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        HistorySort.CHAPTER_NUMBER -> compareBy { it.chapterNumber }
        HistorySort.READ_DURATION -> compareBy { it.readDuration }
    }

    val directed = if (descending) byMode.reversed() else byMode
    val ordered = if (sort == HistorySort.LAST_READ) {
        compareBy<HistoryWithRelations> { it.readAt == null }.then(directed)
    } else {
        directed
    }

    return list.sortedWith(ordered.thenByDescending { it.readAt?.time ?: Long.MIN_VALUE })
}
