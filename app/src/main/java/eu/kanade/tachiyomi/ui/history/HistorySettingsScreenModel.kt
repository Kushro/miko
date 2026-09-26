package eu.kanade.tachiyomi.ui.history

import cafe.adriel.voyager.core.model.ScreenModel
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.history.model.HistorySort
import tachiyomi.domain.history.service.HistoryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HistorySettingsScreenModel(
    val historyPreferences: HistoryPreferences = Injekt.get(),
) : ScreenModel {

    fun toggleFilter(preference: (HistoryPreferences) -> Preference<TriState>) {
        preference(historyPreferences).getAndSet {
            it.next()
        }
    }

    fun toggleSwitch(preference: (HistoryPreferences) -> Preference<Boolean>) {
        preference(historyPreferences).getAndSet { !it }
    }

    // MIKO -->
    /**
     * Picks the order of the History list. Tapping the mode already in use flips its direction;
     * picking another one switches to it with that mode's natural direction: descending for the
     * "bigger is more interesting" ones ([HistorySort.LAST_READ], [HistorySort.READ_DURATION],
     * [HistorySort.CHAPTER_NUMBER]) and ascending for the alphabetical ones
     * ([HistorySort.TITLE], [HistorySort.SOURCE]).
     */
    fun setSort(sort: HistorySort) {
        val sortPreference = historyPreferences.historySort()
        if (sortPreference.get() == sort) {
            historyPreferences.historySortDescending().getAndSet { !it }
        } else {
            sortPreference.set(sort)
            historyPreferences.historySortDescending().set(sort.defaultDescending)
        }
    }

    fun toggleHideRead() {
        historyPreferences.historyHideRead().getAndSet { !it }
    }

    private val HistorySort.defaultDescending: Boolean
        get() = when (this) {
            HistorySort.LAST_READ, HistorySort.READ_DURATION, HistorySort.CHAPTER_NUMBER -> true
            HistorySort.TITLE, HistorySort.SOURCE -> false
        }
    // MIKO <--
}
