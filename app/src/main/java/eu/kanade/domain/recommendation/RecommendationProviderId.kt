package eu.kanade.domain.recommendation

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.miko.MKMR

// MIKO -->

/**
 * The systems that can build the "Suggestions" row of an entry page (C14).
 *
 * The romaji names are both the user-facing brand of each mode and the stable internal key that
 * is persisted in [RecommendationSettings] — so the KDoc of every entry states what the mode
 * actually does, and the UI shows [summaryRes] next to [titleRes] for the same reason.
 *
 * Ordered by the default priority used by the multi-system mode.
 */
enum class RecommendationProviderId(
    /** Stable key written to preferences/JSON. Never rename. */
    val key: String,
    val titleRes: StringResource,
    /** One-line explanation of how the mode finds titles, shown under the name in Settings. */
    val summaryRes: StringResource,
) {
    /**
     * **Osusume** ("recommendation") — the unchanged Komikku pipeline: the extension/parser's own
     * related list (`Source.getRelatedMangaListByExtension`) **plus** one search per word of the
     * title (`getRelatedMangaListBySearch`), every group shown, nothing filtered beyond de-dupe.
     * This is the default and behaves exactly like the app did before C14.
     */
    OSUSUME("osusume", MKMR.strings.recommendation_provider_osusume, MKMR.strings.recommendation_provider_osusume_summary),

    /**
     * **Uwasa** ("rumour / word of mouth") — the Kotatsu criterion. Kotatsu-backed sources only run
     * the parser's `getRelatedManga` (which already falls back to Kotatsu's `RelatedMangaFinder`),
     * never the second keyword pass. Extension sources use the site's own related block when the
     * extension provides one, then a keyword search where a result must contain the keyword in its
     * title and only the smallest non-empty keyword group is kept. The seed is excluded and results
     * are cached for the process lifetime.
     */
    UWASA("uwasa", MKMR.strings.recommendation_provider_uwasa, MKMR.strings.recommendation_provider_uwasa_summary),

    /**
     * **Zokuhen** ("sequel / continuation") — same-series finder. Searches the source with the
     * title minus its trailing volume markers ("Blue Eyes 1" → "Blue Eyes"), retrying with one
     * word fewer while nothing acceptable comes back, and keeps only results whose titles are
     * Dice-similar to the seed (60%+ via the C17 `RuijiTitleClusterer`), pushed as a single group
     * sorted by similarity — so sequels and same-series entries surface first instead of drowning
     * in the word-by-word noise of the KMK pipeline.
     */
    ZOKUHEN("zokuhen", MKMR.strings.recommendation_provider_zokuhen, MKMR.strings.recommendation_provider_zokuhen_summary),

    /**
     * **Tagu Osekkai** ("tag meddling") — per-entry tag affinity: takes the entry's tags (source
     * genres + the user's local tags from C10), finds the matching tag filters of the same source,
     * searches with them and ranks the results by how many of the seed's tags they share.
     */
    TAGU_OSEKKAI("tagu_osekkai", MKMR.strings.recommendation_provider_tagu_osekkai, MKMR.strings.recommendation_provider_tagu_osekkai_summary),

    /**
     * **AniList/MAL** — community recommendations from the trackers, through the existing
     * `exh/recs` paging sources (AniList GraphQL + Jikan/MyAnimeList). Entries are *external*: they
     * carry `source = RECOMMENDS_SOURCE` and are never inserted in the database; tapping one opens
     * the smart search, long-pressing opens the tracker page.
     */
    TRACKER("tracker", MKMR.strings.recommendation_provider_tracker, MKMR.strings.recommendation_provider_tracker_summary),
    ;

    companion object {
        fun fromKey(key: String?): RecommendationProviderId? = entries.firstOrNull { it.key == key }
    }
}

// MIKO <--
