// SPDX-License-Identifier: GPL-3.0-or-later
package tachiyomi.source.kotatsu

import android.app.Application
import org.koitharu.kotatsu.parsers.model.MangaParserSource

/**
 * MIKO — builds the [KotatsuParserSource] adapter for one parser entry.
 *
 * The context parameter is [KotatsuLoaderContext] (not the library's `MangaLoaderContext`) because
 * the adapter needs the host-side extras this module adds on top of it.
 */
typealias KotatsuSourceFactory = (MangaParserSource, KotatsuLoaderContext, Application) -> KotatsuParserSource

/**
 * MIKO — registry of **dedicated parsers**: sites for which Miko ships its own subclass of
 * [KotatsuParserSource] instead of the generic adapter.
 *
 * [KotatsuSourceRegistry] asks this object for every entry of `MangaParserSource`, so adding one
 * dedicated site costs a single map entry and never touches the generic path. There are no
 * overrides yet — the map is the seam, not a feature.
 *
 * **Adding one** (see `docs/features/sources/en.md`):
 * 1. Subclass [KotatsuParserSource] in this module, e.g.
 *    `class MangaDexKotatsuSource(parserSource, loader, app) : KotatsuParserSource(...)`, and
 *    override only what needs to change (`getFilterList`, `setupPreferenceScreen`, `supportsLatest`,
 *    `isNsfw`, `getRelatedMangaListByExtension`, `capabilities`, …).
 * 2. Add `MangaParserSource.MANGADEX to ::MangaDexKotatsuSource` to [dedicatedParsers].
 * 3. To make the UI label it `BUILT_IN_DEDICATED`, register a matching
 *    `SourceEnhancement` in the app module (`AppModule`, `SourceEnhancementRegistry(listOf(...))`)
 *    whose `matches` is `{ it is KotatsuSource && it.parserName == MangaParserSource.MANGADEX.name }`.
 *    Subclassing alone changes behaviour but not the kind badge.
 */
object KotatsuDedicatedParsers {

    /** Used whenever no dedicated subclass is registered for a parser. */
    val default: KotatsuSourceFactory = ::KotatsuParserSource

    /** Parser entries that get their own [KotatsuParserSource] subclass. */
    val dedicatedParsers: Map<MangaParserSource, KotatsuSourceFactory> = emptyMap()

    /** Whether [source] is served by a dedicated subclass. */
    fun isDedicated(source: MangaParserSource): Boolean = source in dedicatedParsers

    /** The factory to build [source] with: the dedicated one when there is one, [default] otherwise. */
    fun factoryFor(source: MangaParserSource): KotatsuSourceFactory = dedicatedParsers[source] ?: default
}
