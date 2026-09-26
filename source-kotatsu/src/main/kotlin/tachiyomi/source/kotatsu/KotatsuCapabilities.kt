// SPDX-License-Identifier: GPL-3.0-or-later
package tachiyomi.source.kotatsu

/**
 * MIKO — static, network-free description of what a Kotatsu parser can do, flattened to plain
 * booleans so the app layer can show capability chips without depending on the parsers library.
 *
 * Everything here comes from `MangaListFilterCapabilities`, `availableSortOrders`,
 * `authorizationProvider`, the parser's `ConfigKey`s and the `MangaParserSource` enum constants.
 * Computing it instantiates the parser once (it is `by lazy` in [KotatsuParserSource]), so callers
 * should do it off the main thread and cache the result per source.
 */
data class KotatsuCapabilities(
    val search: Boolean,
    val searchWithFilters: Boolean,
    val multipleTags: Boolean,
    val tagExclusion: Boolean,
    val year: Boolean,
    val yearRange: Boolean,
    val authorSearch: Boolean,
    val originalLocale: Boolean,
    /** `MangaParserSource.locale` is empty → the parser serves several languages. */
    val multiLanguage: Boolean,
    /** `SortOrder.UPDATED` is available → "latest" listing. */
    val latest: Boolean,
    /** Any of the popularity/rating/newest/alphabetical/added sort orders beyond UPDATED. */
    val extraSortOrders: Boolean,
    /** The parser implements `MangaParserAuthProvider`. */
    val login: Boolean,
    /** The parser exposes more than the domain key in `onCreateConfig`. */
    val configurable: Boolean,
    /** `configKeyDomain.presetValues.size > 1`. */
    val alternativeDomains: Boolean,
    val nsfw: Boolean,
)
