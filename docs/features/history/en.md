# Reading history

**English** · [Español](./es.md) · [Documentation](../../README.md)

Use the History filter sheet to follow the current library category, hide read
entries and choose sorting by last read, title, source, chapter or reading time.
The source-visibility dialog hides selected sources and supports named presets.

## Follow a library category

Enable **Follow the library category** in the filter sheet. A category chip shows
the active restriction; tap it to disable the scope. Category scope excludes entries
outside the library. Default means library entries with no assigned category.

Ungrouped mode, no selected category or a deleted category disables the scope rather
than producing an unexplained empty list. The scope combines with search and other
history filters. `scopeToLibraryCategory()` is backed up; `active_category_id` is
device-local app state and uses `-1` for no active category.

## Delete history

The overflow deletion dialog offers **Everything** or **Only the shown entries**.
Everything deletes all history rows. The scoped option soft-deletes history for
the distinct manga IDs currently shown, respecting filters and search. It affects
those manga, not just one displayed chapter. A category scope preselects the scoped
option. The existing clear-history toolbar action still enters selection mode.

## Technical reference and limits

`LibraryScreenModel.updateActiveCategoryIndex` publishes the category ID.
`HistoryScreenModel` validates it through the category subscription and passes a
nullable category to `GetHistory` and the repository query in `historyView.sq`.
This is query filtering, not an additional history table. Ordering logic is in
`HistorySorting.kt`; settings are in `HistoryPreferences`.

Source visibility uses `pref_history_hidden_sources`; Moments has its own selection.
Named visibility presets are shared through `miko_source_hide_presets`. The UI does
not provide a source/read-status tab pager. Check category changes, stale IDs,
filter combinations and deletion scope on a device before changing these flows.
