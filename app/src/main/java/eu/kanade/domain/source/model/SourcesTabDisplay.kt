package eu.kanade.domain.source.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.miko.MKMR

/*
 * MIKO — display options of the Sources tab (grouping, sorting, list vs grid), persisted in
 * SourcePreferences.
 */

enum class SourcesGroupMode(val titleRes: StringResource) {
    /** Groups by [SourceKind], in enum order. */
    KIND(MKMR.strings.sources_group_kind),

    /** Groups by [SourceKind], then by language inside each kind. */
    KIND_AND_LANGUAGE(MKMR.strings.sources_group_kind_language),

    /** Upstream behaviour: one group per language. */
    LANGUAGE(MKMR.strings.sources_group_language),

    /** Flat list (pinned / last used / preset categories still come first). */
    NONE(MKMR.strings.sources_group_none),
}

enum class SourcesSortMode(val titleRes: StringResource) {
    NAME(MKMR.strings.sources_sort_name),
    LANGUAGE(MKMR.strings.sources_sort_language),
    KIND(MKMR.strings.sources_sort_kind),

    /** Sources with more detected [SourceFeature]s first (undetected ones sort last). */
    FEATURES(MKMR.strings.sources_sort_features),
}

enum class SourcesDisplayMode(val titleRes: StringResource) {
    LIST(MKMR.strings.sources_display_list),
    GRID(MKMR.strings.sources_display_grid),
}
