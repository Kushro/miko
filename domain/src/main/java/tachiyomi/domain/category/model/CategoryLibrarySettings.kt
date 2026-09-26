package tachiyomi.domain.category.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGrouping
import tachiyomi.domain.library.model.LibrarySort

// MIKO -->

/**
 * The tri-state filters of the library "Filter" tab, as one value object.
 *
 * Global filters live in `LibraryPreferences.filterXxx()`; a category with
 * [CategoryLibrarySettings] carries its own copy here instead. [tracking] is keyed by tracker id
 * (only logged-in trackers are ever consulted). [tags] mirrors `filterTags()` +
 * `filterTagsInclude()/Exclude()` (lower-cased local tag names).
 *
 * The KMK category include/exclude filter is deliberately **not** part of this bundle: it is a
 * cross-category filter and stays global.
 */
@Serializable
data class LibraryFilterSettings(
    val downloaded: TriStateValue = TriState.DISABLED,
    val unread: TriStateValue = TriState.DISABLED,
    val started: TriStateValue = TriState.DISABLED,
    val bookmarked: TriStateValue = TriState.DISABLED,
    val completed: TriStateValue = TriState.DISABLED,
    val intervalCustom: TriStateValue = TriState.DISABLED,
    val lewd: TriStateValue = TriState.DISABLED,
    val rated: TriStateValue = TriState.DISABLED,
    val tracking: Map<Long, TriStateValue> = emptyMap(),
    val tags: Boolean = false,
    val includedTags: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
) {

    operator fun get(key: LibraryFilterKey): TriState = when (key) {
        LibraryFilterKey.DOWNLOADED -> downloaded
        LibraryFilterKey.UNREAD -> unread
        LibraryFilterKey.STARTED -> started
        LibraryFilterKey.BOOKMARKED -> bookmarked
        LibraryFilterKey.COMPLETED -> completed
        LibraryFilterKey.INTERVAL_CUSTOM -> intervalCustom
        LibraryFilterKey.LEWD -> lewd
        LibraryFilterKey.RATED -> rated
    }

    fun with(key: LibraryFilterKey, value: TriState): LibraryFilterSettings = when (key) {
        LibraryFilterKey.DOWNLOADED -> copy(downloaded = value)
        LibraryFilterKey.UNREAD -> copy(unread = value)
        LibraryFilterKey.STARTED -> copy(started = value)
        LibraryFilterKey.BOOKMARKED -> copy(bookmarked = value)
        LibraryFilterKey.COMPLETED -> copy(completed = value)
        LibraryFilterKey.INTERVAL_CUSTOM -> copy(intervalCustom = value)
        LibraryFilterKey.LEWD -> copy(lewd = value)
        LibraryFilterKey.RATED -> copy(rated = value)
    }

    fun toggle(key: LibraryFilterKey): LibraryFilterSettings = with(key, get(key).next())

    fun tracking(trackerId: Long): TriState = tracking[trackerId] ?: TriState.DISABLED

    fun withTracking(trackerId: Long, value: TriState): LibraryFilterSettings =
        copy(tracking = tracking + (trackerId to value))

    fun toggleTracking(trackerId: Long): LibraryFilterSettings =
        withTracking(trackerId, tracking(trackerId).next())

    /** True when any filter of this bundle would hide something (same notion as `hasActiveFilters`). */
    val isActive: Boolean
        get() = LibraryFilterKey.entries.any { get(it) != TriState.DISABLED } ||
            tracking.values.any { it != TriState.DISABLED } ||
            (tags && (includedTags.isNotEmpty() || excludedTags.isNotEmpty()))

    companion object {
        val default = LibraryFilterSettings()
    }
}

/** One of the eight tri-state rows of the Filter tab. */
enum class LibraryFilterKey {
    DOWNLOADED,
    UNREAD,
    STARTED,
    BOOKMARKED,
    COMPLETED,
    INTERVAL_CUSTOM,
    LEWD,
    RATED,
}

/**
 * Independent library settings of ONE category ("Special" tab of the library settings dialog).
 *
 * A row exists only for categories that opted in; absence of a row = the category follows the
 * global settings exactly as before. Sort/display/grouping are stored in the same string form the
 * matching preferences use, so their own `Serializer` objects stay the single source of truth.
 */
@Serializable
data class CategoryLibrarySettings(
    val filters: LibraryFilterSettings = LibraryFilterSettings.default,
    /** [LibrarySort.serialize] form. */
    val sort: String = LibrarySort.default.serialize(),
    /** [LibraryDisplayMode.serialize] form. */
    val display: String = LibraryDisplayMode.default.serialize(),
    /** `0` = auto, like `LibraryPreferences.portraitColumns()`. */
    val portraitColumns: Int = 0,
    val landscapeColumns: Int = 0,
    /** [LibraryGrouping.serialize] form; `""` = no grouping layers. */
    val grouping: String = LibraryGrouping.default.serialize(),
    val genreGroupMinSize: Int = DEFAULT_GENRE_GROUP_MIN_SIZE,
    /** Similarity percent (50–100) for `LibraryGroupType.RUIJI_TITLES` sections. */
    val titleSimilarityThreshold: Int = DEFAULT_TITLE_SIMILARITY_THRESHOLD,
) {

    val sortMode: LibrarySort
        get() = LibrarySort.deserialize(sort)

    val displayMode: LibraryDisplayMode
        get() = LibraryDisplayMode.deserialize(display)

    val libraryGrouping: LibraryGrouping
        get() = LibraryGrouping.deserialize(grouping)

    fun withSort(value: LibrarySort) = copy(sort = value.serialize())

    fun withDisplay(value: LibraryDisplayMode) = copy(display = value.serialize())

    fun withGrouping(value: LibraryGrouping) = copy(grouping = value.serialize())

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        /** Mirrors the default of `LibraryPreferences.libraryGenreGroupMinSize()`. */
        const val DEFAULT_GENRE_GROUP_MIN_SIZE = 3

        /** Mirrors the default of `LibraryPreferences.libraryTitleSimilarityThreshold()`. */
        const val DEFAULT_TITLE_SIMILARITY_THRESHOLD = 60

        val default = CategoryLibrarySettings()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Lenient: any unreadable blob falls back to [default] instead of crashing the library. */
        fun fromJson(raw: String?): CategoryLibrarySettings {
            if (raw.isNullOrBlank()) return default
            return try {
                json.decodeFromString(serializer(), raw)
            } catch (e: Exception) {
                default
            }
        }
    }
}

/** [TriState] persisted by name so the enum (in `core:common`) needs no serialization plugin. */
typealias TriStateValue =
    @Serializable(with = TriStateAsStringSerializer::class)
    TriState

object TriStateAsStringSerializer : KSerializer<TriState> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("TriState", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TriState) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): TriState {
        val name = decoder.decodeString()
        return TriState.entries.firstOrNull { it.name == name } ?: TriState.DISABLED
    }
}
// MIKO <--
