package eu.kanade.domain.recommendation.providers

import eu.kanade.domain.recommendation.RecommendationGroup
import eu.kanade.domain.recommendation.RecommendationPreferences
import eu.kanade.domain.recommendation.RecommendationProvider
import eu.kanade.domain.recommendation.RecommendationProviderId
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.domain.library.service.RuijiTitleClusterer
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource

// MIKO -->

/**
 * **Zokuhen** — same-series finder (see [RecommendationProviderId.ZOKUHEN]).
 *
 * Builds one search ladder per seed title ([Manga.title] and [Manga.ogTitle]): the title minus its
 * trailing volume markers as a full phrase, then one word fewer per attempt
 * ([zokuhenSearchQueries]). The combined ladders run **sequentially** and the first query whose
 * results survive the Dice acceptance ([rankZokuhenResults]) wins: one group is pushed, keyword =
 * the winning query, sorted by similarity to the seed. The seed itself is always removed. The
 * acceptance threshold is `RecommendationSettings.zokuhenThreshold` (the dialog's slider, default
 * 60, range 50–100), read once per run.
 *
 * Being a pure search criterion, both KMK per-source switches gate it (`disableRelatedMangas`,
 * `disableRelatedMangasBySearch`) and Kotatsu sources need no special path — their
 * `getSearchManga` is a regular text search.
 */
class ZokuhenProvider(
    private val networkToLocalManga: NetworkToLocalManga,
    private val preferences: RecommendationPreferences,
) : RecommendationProvider {

    override val id: RecommendationProviderId = RecommendationProviderId.ZOKUHEN

    override fun isAvailable(source: Source, manga: Manga): Boolean =
        source !is StubSource && source is CatalogueSource

    override suspend fun recommend(
        source: Source,
        manga: Manga,
        onError: (Throwable) -> Unit,
        push: suspend (RecommendationGroup) -> Unit,
    ) {
        val catalogueSource = source as? CatalogueSource ?: return
        if (catalogueSource.disableRelatedMangas || catalogueSource.disableRelatedMangasBySearch) return

        val normalizedSeeds = listOf(manga.title, manga.ogTitle)
            .map { RuijiTitleClusterer.normalizeTitle(it) }
            .filter { it.isNotEmpty() }
            .distinct()
        if (normalizedSeeds.isEmpty()) return

        val queries = (zokuhenSearchQueries(manga.title) + zokuhenSearchQueries(manga.ogTitle))
            .map { it.sanitize() }
            .filter { it.length >= 2 }
            .distinctBy { it.lowercase() }

        val thresholdPercent = preferences.settings().get().zokuhenThreshold

        val filterList = catalogueSource.getFilterList()
        for (query in queries) {
            val ranked = try {
                rankZokuhenResults(
                    catalogueSource.getSearchManga(1, query, filterList).mangas,
                    normalizedSeeds,
                    manga.url,
                    thresholdPercent,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onError(e)
                continue
            }
            if (ranked.isNotEmpty()) {
                push(RecommendationGroup(id, query, networkToLocalManga.persistRelated(source.id, ranked)))
                return
            }
        }
    }

    companion object {
        /** Network cost bound: at most this many prefixes (the longest ones) tried per seed title. */
        const val MAX_QUERIES_PER_TITLE = 3

        /** Results kept in the pushed group. */
        const val MAX_RESULTS = 20
    }
}

private val WHITESPACE = Regex("""\s+""")

/** `12`, `０３` — any pure (unicode) digit token. */
private val NUMBER_TOKEN = Regex("""^\p{Nd}+$""")

/** `II`, `xiv` — short roman numerals ([ivx] covers volumes up to 39; L/C/D/M would be absurd). */
private val ROMAN_NUMERAL_TOKEN = Regex("""^[ivxIVX]{1,4}$""")

/** `Vol`, `vol.`, `Vol.3`, `volume64`, `Part2`, `season` — a volume word, digits optional. */
private val VOLUME_WORD_TOKEN = Regex("""^(?:vol|volume|part|season)\.?\p{Nd}*$""", RegexOption.IGNORE_CASE)

/** `v2`, `S3` — the one-letter marker forms, which only count when fused with digits. */
private val FUSED_VOLUME_TOKEN = Regex("""^[vsVS]\p{Nd}+$""")

/**
 * True for a trailing token [zokuhenSearchQueries] drops before searching: pure numbers, roman
 * numerals, volume words, fused `v2`/`s3` forms, and tokens with no letter or digit at all
 * (separators like `-` or `~` left dangling once their number is gone).
 */
internal fun isVolumeMarkerToken(token: String): Boolean =
    token.none { it.isLetterOrDigit() } ||
        NUMBER_TOKEN.matches(token) ||
        ROMAN_NUMERAL_TOKEN.matches(token) ||
        VOLUME_WORD_TOKEN.matches(token) ||
        FUSED_VOLUME_TOKEN.matches(token)

/**
 * The decreasing-prefix search ladder of [title]: whitespace tokens minus the trailing
 * volume-marker tokens ([isVolumeMarkerToken] — at least one token always survives), then the full
 * phrase followed by prefixes one word shorter each, capped at
 * [ZokuhenProvider.MAX_QUERIES_PER_TITLE]. A single-token title (e.g. CJK without spaces) gets its
 * trailing digits trimmed instead, when at least 2 chars remain — so a number-only name like "86"
 * is kept whole. Queries shorter than 2 chars are dropped. Extracted so it is unit-testable
 * without a real [Source].
 */
internal fun zokuhenSearchQueries(title: String): List<String> {
    var tokens = title.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()

    while (tokens.size > 1 && isVolumeMarkerToken(tokens.last())) {
        tokens = tokens.dropLast(1)
    }

    if (tokens.size == 1) {
        val single = tokens.single()
        val trimmed = single.trimEnd { it.isDigit() }
        val query = if (trimmed.length >= 2) trimmed else single
        return if (query.length >= 2) listOf(query) else emptyList()
    }

    return (tokens.size downTo 1)
        .asSequence()
        .map { count -> tokens.take(count).joinToString(" ") }
        .take(ZokuhenProvider.MAX_QUERIES_PER_TITLE)
        .filter { it.length >= 2 }
        .toList()
}

/**
 * Zokuhen's acceptance and ranking: the seed (same url) is removed, each result's normalized title
 * is scored against every entry of [normalizedSeeds] (the best score counts, so a Japanese
 * original title can accept what the localized title cannot), results below [thresholdPercent]
 * (the user's `zokuhenThreshold`, default 60) are dropped and the survivors come back sorted by
 * score — stable, so the source's own order breaks ties — capped at
 * [ZokuhenProvider.MAX_RESULTS]. Extracted so it is unit-testable without a real [Source].
 */
internal fun rankZokuhenResults(
    mangas: List<SManga>,
    normalizedSeeds: List<String>,
    seedUrl: String,
    thresholdPercent: Int,
): List<SManga> =
    mangas
        .filter { it.url != seedUrl }
        .map { manga ->
            val normalized = RuijiTitleClusterer.normalizeTitle(manga.title)
            manga to normalizedSeeds.maxOf { seed -> RuijiTitleClusterer.similarity(seed, normalized) }
        }
        .filter { (_, score) -> score * 100 >= thresholdPercent }
        .sortedByDescending { (_, score) -> score }
        .map { (manga, _) -> manga }
        .take(ZokuhenProvider.MAX_RESULTS)

// MIKO <--
