package eu.kanade.domain.source.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// MIKO -->

/**
 * A named set of source ids for the "eye" dialogs of History and Moments (C20). Tapping a preset
 * toggles its sources over the current screen's hidden set: when every id is already hidden they
 * are removed, otherwise they are added. Presets are shortcuts, never the state itself — each
 * screen keeps its own hidden StringSet preference.
 */
data class SourceHidePreset(
    val name: String,
    val sourceIds: Set<Long>,
)

/**
 * Every saved preset, shared by both screens and persisted as one JSON preference
 * (`SourcePreferences.sourceHidePresets()`).
 *
 * Invariants (enforced by [normalized], applied by [fromJson] and [toJson]): names are unique
 * (later entries win, preserving first-seen position), blank names and empty id sets are dropped.
 */
data class SourceHidePresets(
    val presets: List<SourceHidePreset> = emptyList(),
) {

    fun normalized(): SourceHidePresets {
        val byName = LinkedHashMap<String, SourceHidePreset>()
        presets.forEach { preset ->
            if (preset.name.isBlank() || preset.sourceIds.isEmpty()) return@forEach
            byName[preset.name] = preset
        }
        return SourceHidePresets(byName.values.toList())
    }

    /** Adds or replaces (by name) one preset. */
    fun save(name: String, sourceIds: Set<Long>): SourceHidePresets {
        val trimmed = name.trim()
        val without = presets.filterNot { it.name == trimmed }
        return SourceHidePresets(without + SourceHidePreset(trimmed, sourceIds)).normalized()
    }

    fun delete(name: String): SourceHidePresets {
        return SourceHidePresets(presets.filterNot { it.name == name })
    }

    fun toJson(): String {
        val n = normalized()
        return json.encodeToString(
            Stored.serializer(),
            Stored(presets = n.presets.map { StoredPreset(it.name, it.sourceIds.toList()) }),
        )
    }

    /** On-disk shape: plain fields, so a value written by a newer build degrades per field. */
    @Serializable
    private data class StoredPreset(
        val name: String = "",
        val sourceIds: List<Long> = emptyList(),
    )

    @Serializable
    private data class Stored(
        val presets: List<StoredPreset> = emptyList(),
    )

    companion object {
        val EMPTY = SourceHidePresets()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(raw: String?): SourceHidePresets {
            if (raw.isNullOrBlank()) return EMPTY
            return try {
                val stored = json.decodeFromString(Stored.serializer(), raw)
                SourceHidePresets(
                    presets = stored.presets.map { SourceHidePreset(it.name, it.sourceIds.toSet()) },
                ).normalized()
            } catch (e: Exception) {
                EMPTY
            }
        }
    }
}

// MIKO <--
