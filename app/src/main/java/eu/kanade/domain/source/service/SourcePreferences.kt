package eu.kanade.domain.source.service

import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.model.SourceHidePresets
import eu.kanade.domain.source.model.SourcesDisplayMode
import eu.kanade.domain.source.model.SourcesGroupMode
import eu.kanade.domain.source.model.SourcesSortMode
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import eu.kanade.tachiyomi.util.system.LocaleHelper
import mihon.domain.migration.models.MigrationFlag
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.preference.getLongArray
import tachiyomi.domain.library.model.LibraryDisplayMode

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun sourceDisplayMode() = preferenceStore.getObjectFromString(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun enabledLanguages() = preferenceStore.getStringSet("source_languages", LocaleHelper.getDefaultEnabledLanguages())

    fun disabledSources() = preferenceStore.getStringSet("hidden_catalogues", emptySet())

    fun incognitoExtensions() = preferenceStore.getStringSet("incognito_extensions", emptySet())

    fun pinnedSources() = preferenceStore.getStringSet(
        // KMK -->
        PINNED_SOURCES_PREF_KEY,
        // KMK <--
        emptySet(),
    )

    fun lastUsedSource() = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )

    fun showNsfwSource() = preferenceStore.getBoolean("show_nsfw_source", true)

    fun migrationSortingMode() = preferenceStore.getEnum("pref_migration_sorting", SetMigrateSorting.Mode.ALPHABETICAL)

    fun migrationSortingDirection() = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    fun hideInLibraryItems() = preferenceStore.getBoolean("browse_hide_in_library_items", false)

    // KMK -->
    fun hideInLibraryFeedItems() = preferenceStore.getBoolean("feed_hide_in_library_items", false)
    // KMK <--

    // MIKO -->
    /**
     * Named source-id sets shared by the "eye" dialogs of History and Moments (C20). Tapping a
     * preset toggles its sources over the current screen's hidden set; the state itself lives in
     * `HistoryPreferences.hiddenSources()` / `FavoritesPreferences.hiddenSources()`.
     */
    fun sourceHidePresets() = preferenceStore.getObjectFromString(
        "miko_source_hide_presets",
        SourceHidePresets.EMPTY,
        SourceHidePresets::toJson,
        SourceHidePresets::fromJson,
    )
    // MIKO <--

    @Deprecated("Use ExtensionRepoRepository instead", replaceWith = ReplaceWith("ExtensionRepoRepository.getAll()"))
    fun extensionRepos() = preferenceStore.getStringSet("extension_repos", emptySet())

    fun extensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)

    fun trustedExtensions() = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    fun globalSearchFilterState() = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    fun migrationSources() = preferenceStore.getLongArray("migration_sources", emptyList())

    fun migrationFlags() = preferenceStore.getObjectFromInt(
        key = "migration_flags",
        defaultValue = MigrationFlag.entries.toSet(),
        serializer = { MigrationFlag.toBit(it) },
        deserializer = { value: Int -> MigrationFlag.fromBit(value) },
    )

    fun migrationDeepSearchMode() = preferenceStore.getBoolean("migration_deep_search", false)

    fun migrationPrioritizeByChapters() = preferenceStore.getBoolean("migration_prioritize_by_chapters", false)

    fun migrationHideUnmatched() = preferenceStore.getBoolean("migration_hide_unmatched", false)

    fun migrationHideWithoutUpdates() = preferenceStore.getBoolean("migration_hide_without_updates", false)

    // KMK -->
    fun migrationSmartSearchSingleEntry() = preferenceStore.getBoolean("migration_smart_search_single_entry", false)

    // MIKO -->
    // Was `getEnum`; `SourceFilter` is now a sealed interface whose `Preset` case carries a name.
    // The key and the `"All"` / `"PinnedOnly"` strings are unchanged, so stored values still read.
    fun globalSearchPinnedState(): Preference<SourceFilter> = preferenceStore.getObjectFromString(
        key = Preference.appStateKey("global_search_pinned_toggle_state"),
        defaultValue = SourceFilter.PinnedOnly,
        serializer = SourceFilter.Serializer::serialize,
        deserializer = SourceFilter.Serializer::deserialize,
    )
    // MIKO <--

    fun disabledRepos() = preferenceStore.getStringSet("disabled_repos", emptySet())
    // KMK <--

    // SY -->
    fun enableSourceBlacklist() = preferenceStore.getBoolean("eh_enable_source_blacklist", true)

    fun sourcesTabCategories() = preferenceStore.getStringSet("sources_tab_categories", mutableSetOf())

    fun sourcesTabCategoriesFilter() = preferenceStore.getBoolean("sources_tab_categories_filter", false)

    fun sourcesTabSourcesInCategories() = preferenceStore.getStringSet("sources_tab_source_categories", mutableSetOf())

    fun dataSaver() = preferenceStore.getEnum("data_saver", DataSaver.NONE)

    fun dataSaverIgnoreJpeg() = preferenceStore.getBoolean("ignore_jpeg", false)

    fun dataSaverIgnoreGif() = preferenceStore.getBoolean("ignore_gif", true)

    fun dataSaverImageQuality() = preferenceStore.getInt("data_saver_image_quality", 80)

    fun dataSaverImageFormatJpeg() = preferenceStore.getBoolean("data_saver_image_format_jpeg", false)

    fun dataSaverServer() = preferenceStore.getString("data_saver_server", "")

    fun dataSaverColorBW() = preferenceStore.getBoolean("data_saver_color_bw", false)

    fun dataSaverExcludedSources() = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    fun dataSaverDownloader() = preferenceStore.getBoolean("data_saver_downloader", true)

    enum class DataSaver {
        NONE,
        BANDWIDTH_HERO,
        WSRV_NL,
    }

    fun allowLocalSourceHiddenFolders() = preferenceStore.getBoolean("allow_local_source_hidden_folders", false)

    fun preferredMangaDexId() = preferenceStore.getString("preferred_mangaDex_id", "0")

    fun mangadexSyncToLibraryIndexes() = preferenceStore.getStringSet(
        "pref_mangadex_sync_to_library_indexes",
        emptySet(),
    )

    fun recommendationSearchFlags() = preferenceStore.getInt("rec_search_flags", Int.MAX_VALUE)
    // SY <--

    // MIKO -->
    /**
     * Which source systems are active: installable Tachiyomi extensions, the built-in Kotatsu
     * parsers of `:source-kotatsu`, or both.
     */
    fun sourceMode() = preferenceStore.getEnum("source_mode", SourceMode.BOTH)

    enum class SourceMode {
        BOTH,
        EXTENSIONS,
        PARSERS,
    }

    /**
     * Name of the currently active source preset, i.e. the source category that Browse and global
     * search are restricted to. An empty string means "no preset active" (all sources).
     *
     * A preset *is* a source category ([sourcesTabCategories]); this preference only stores which
     * one is active. Not an app-state nor a private key, so it travels in backups like the rest of
     * the source categories.
     */
    fun activeSourcePreset() = preferenceStore.getString("active_source_preset", "")

    // Sources tab display (C8)
    fun sourcesTabGroupMode() = preferenceStore.getEnum("sources_tab_group_mode", SourcesGroupMode.KIND)

    fun sourcesTabSortMode() = preferenceStore.getEnum("sources_tab_sort_mode", SourcesSortMode.NAME)

    fun sourcesTabSortDescending() = preferenceStore.getBoolean("sources_tab_sort_descending", false)

    fun sourcesTabDisplayMode() = preferenceStore.getEnum("sources_tab_display_mode", SourcesDisplayMode.LIST)

    /** Draw each group as a rounded "group box" instead of a bare sticky header. */
    fun sourcesTabGroupBoxes() = preferenceStore.getBoolean("sources_tab_group_boxes", true)

    /** Allow collapsing groups by tapping their header. */
    fun sourcesTabCollapsibleGroups() = preferenceStore.getBoolean("sources_tab_collapsible_groups", true)

    /** Names of the groups the user collapsed (app state: not worth backing up). */
    fun sourcesTabCollapsedGroups() = preferenceStore.getStringSet(
        Preference.appStateKey("sources_tab_collapsed_groups"),
        emptySet(),
    )

    fun sourcesTabShowFeatureChips() = preferenceStore.getBoolean("sources_tab_show_feature_chips", true)

    /** Show the [SourceKind] badge on source rows and cards (Sources tab, global search, migration). */
    fun showSourceKindBadge() = preferenceStore.getBoolean("show_source_kind_badge", true)

    /** Restrict the Sources tab to one kind; empty = all. Not backed up (transient filter). */
    fun sourcesTabKindFilter() = preferenceStore.getString(Preference.appStateKey("sources_tab_kind_filter"), "")
    // MIKO <--

    // KMK -->
    fun relatedMangas() = preferenceStore.getBoolean("related_mangas", true)

    companion object {
        const val PINNED_SOURCES_PREF_KEY = "pinned_catalogues"
    }
    // KMK <--
}
