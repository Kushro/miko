package eu.kanade.domain.recommendation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// MIKO -->

/** How the suggestion systems are combined. */
enum class RecommendationMode {
    /** Exactly one provider runs: [RecommendationSettings.single]. */
    SINGLE,

    /**
     * Every enabled provider runs in parallel; groups are shown in [RecommendationSettings.order]
     * and a title found by several providers is kept only under the highest-priority one.
     */
    MULTI,
}

/**
 * The whole "Suggestion system" configuration, persisted as one JSON preference
 * (`RecommendationPreferences.settings()`, travels in backups).
 *
 * Invariants (enforced by [normalized], applied by [fromJson] and [toJson]): [order] always lists
 * every [RecommendationProviderId] exactly once (unknown keys dropped, missing ones appended in
 * declaration order); [enabled] is a non-empty subset of [order] (falls back to the default when
 * empty).
 *
 * On disk the providers are written by [RecommendationProviderId.key] (see [Stored]) so a value
 * written by a newer build with a provider this build does not know is *trimmed*, not discarded.
 */
data class RecommendationSettings(
    val mode: RecommendationMode = RecommendationMode.SINGLE,
    /** Provider used in [RecommendationMode.SINGLE]. */
    val single: RecommendationProviderId = RecommendationProviderId.OSUSUME,
    /** Priority order used in [RecommendationMode.MULTI], highest first. */
    val order: List<RecommendationProviderId> = DEFAULT_ORDER,
    /** Providers that take part in [RecommendationMode.MULTI]. */
    val enabled: Set<RecommendationProviderId> = DEFAULT_ENABLED,
    /**
     * Dice score (×100) a result must reach in Zokuhen's acceptance/ranking (C19). Clamped to
     * [ZOKUHEN_THRESHOLD_RANGE] by [normalized]; only shown in the dialog while Zokuhen is active.
     */
    val zokuhenThreshold: Int = DEFAULT_ZOKUHEN_THRESHOLD,
) {

    /** The providers that will run, highest priority first, according to [mode]. */
    val activeProviders: List<RecommendationProviderId>
        get() = when (mode) {
            RecommendationMode.SINGLE -> listOf(single)
            RecommendationMode.MULTI -> order.filter { it in enabled }
        }

    fun normalized(): RecommendationSettings {
        val fullOrder = order.distinct() + RecommendationProviderId.entries.filter { it !in order }
        val validEnabled = enabled.filter { it in fullOrder }.toSet().ifEmpty { DEFAULT_ENABLED }
        return copy(
            order = fullOrder,
            enabled = validEnabled,
            zokuhenThreshold = zokuhenThreshold.coerceIn(ZOKUHEN_THRESHOLD_RANGE),
        )
    }

    fun toJson(): String {
        val n = normalized()
        return json.encodeToString(
            Stored.serializer(),
            Stored(
                mode = n.mode.name,
                single = n.single.key,
                order = n.order.map { it.key },
                enabled = n.enabled.map { it.key },
                zokuhenThreshold = n.zokuhenThreshold,
            ),
        )
    }

    /** On-disk shape: plain strings, so unknown providers/modes degrade per field, never as a whole. */
    @Serializable
    private data class Stored(
        val mode: String = RecommendationMode.SINGLE.name,
        val single: String = RecommendationProviderId.OSUSUME.key,
        val order: List<String> = DEFAULT_ORDER.map { it.key },
        val enabled: List<String> = DEFAULT_ENABLED.map { it.key },
        val zokuhenThreshold: Int = DEFAULT_ZOKUHEN_THRESHOLD,
    )

    companion object {
        val DEFAULT_ORDER: List<RecommendationProviderId> = RecommendationProviderId.entries.toList()
        val DEFAULT_ENABLED: Set<RecommendationProviderId> = setOf(RecommendationProviderId.OSUSUME)

        /** C17's default similarity, shared by the Zokuhen slider. */
        const val DEFAULT_ZOKUHEN_THRESHOLD = 60

        /** Same range as the C17 "Similar titles" slider. */
        val ZOKUHEN_THRESHOLD_RANGE = 50..100

        val default = RecommendationSettings()

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(raw: String?): RecommendationSettings {
            if (raw.isNullOrBlank()) return default
            return try {
                val stored = json.decodeFromString(Stored.serializer(), raw)
                RecommendationSettings(
                    mode = RecommendationMode.entries.firstOrNull { it.name == stored.mode } ?: default.mode,
                    single = RecommendationProviderId.fromKey(stored.single) ?: default.single,
                    order = stored.order.mapNotNull(RecommendationProviderId::fromKey),
                    enabled = stored.enabled.mapNotNull(RecommendationProviderId::fromKey).toSet(),
                    zokuhenThreshold = stored.zokuhenThreshold,
                ).normalized()
            } catch (e: Exception) {
                default
            }
        }
    }
}

// MIKO <--
