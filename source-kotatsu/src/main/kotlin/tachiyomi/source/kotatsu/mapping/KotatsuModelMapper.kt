// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(InternalParsersApi::class)

package tachiyomi.source.kotatsu.mapping

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaState
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.util.generateUid
import kotlin.math.floor
import kotlin.math.max

/*
 * Conversions between the parsers library models and Mihon's `S*` models.
 *
 * Mihon identifies content by `(sourceId, url)` and the relative urls of both ecosystems have the
 * same contract, so urls are copied verbatim and are the only thing that has to survive a restart.
 * The Kotatsu `id` fields are rebuilt on demand with `generateUid`, which is exactly the value the
 * parsers themselves would have produced for the same url.
 *
 * Known lossy conversions (documented in `docs/features/sources/en.md`):
 * `Manga.altTitles`, `Manga.rating`, per-manga `Manga.contentRating`, `MangaChapter.branch`
 * (folded into the scanlator), `MangaChapter.volume` (folded into the chapter name) and the
 * `COMPLETED` / `PUBLISHING_FINISHED` distinction.
 */

fun Manga.toSManga(initialized: Boolean = false): SManga = SManga.create().also { sManga ->
    sManga.url = url
    sManga.title = title
    sManga.thumbnail_url = largeCoverUrl ?: coverUrl
    sManga.author = authors.firstOrNull()
    // Mihon has a single artist field: everyone past the first author is folded into it.
    sManga.artist = authors.drop(1).joinToString(", ").ifEmpty { null }
    sManga.description = description
    sManga.genre = tags.joinToString(", ") { it.title }.ifEmpty { null }
    sManga.status = state.toSMangaStatus()
    sManga.initialized = initialized
}

/**
 * Rebuilds the minimal [Manga] a parser needs to resolve details, chapters or related manga.
 *
 * Only `id`, `url` and `source` are contractually required to be preserved by `getDetails`, and all
 * three are derived from data Mihon does persist.
 */
fun SManga.toManga(parser: MangaParser, baseUrl: String): Manga = Manga(
    id = parser.generateUid(url),
    title = title,
    altTitles = emptySet(),
    url = url,
    publicUrl = absoluteUrl(baseUrl, url),
    rating = RATING_UNKNOWN,
    contentRating = null,
    coverUrl = thumbnail_url,
    tags = emptySet(),
    state = null,
    authors = emptySet(),
    largeCoverUrl = null,
    description = description,
    chapters = null,
    source = parser.source,
)

fun MangaChapter.toSChapter(): SChapter = SChapter.create().also { sChapter ->
    sChapter.url = url
    sChapter.name = buildChapterName(title, number, volume)
    // Mihon treats a negative number as "unknown"; Kotatsu uses 0 for the same thing.
    sChapter.chapter_number = if (number > 0f) number else UNKNOWN_CHAPTER_NUMBER
    sChapter.scanlator = listOfNotNull(scanlator, branch)
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
        .ifEmpty { null }
    sChapter.date_upload = uploadDate
}

/**
 * Maps a parser chapter list to Mihon's expected order: newest first.
 *
 * Parsers normalise their site order to ascending (that is what the `reversed` flag of
 * `util/Chapters.kt#mapChapters` is for), but a handful build the list straight from a
 * newest-first endpoint. Reversing blindly would corrupt those, so the existing order is probed
 * first and the list is only reversed when it really is ascending.
 */
fun List<MangaChapter>.toSChapters(): List<SChapter> {
    val ordered = if (isNewestFirst()) this else asReversed()
    return ordered.map { it.toSChapter() }
}

fun SChapter.toMangaChapter(parser: MangaParser): MangaChapter = MangaChapter(
    id = parser.generateUid(url),
    title = name,
    number = max(chapter_number, 0f),
    volume = 0,
    url = url,
    scanlator = scanlator,
    uploadDate = date_upload,
    branch = null,
    source = parser.source,
)

fun MangaPage.toPage(index: Int): Page = Page(index = index, url = url, imageUrl = null)

fun Page.toMangaPage(parser: MangaParser): MangaPage = MangaPage(
    id = parser.generateUid(url),
    url = url,
    preview = null,
    source = parser.source,
)

fun MangaState?.toSMangaStatus(): Int = when (this) {
    MangaState.ONGOING -> SManga.ONGOING
    MangaState.FINISHED -> SManga.COMPLETED
    MangaState.PAUSED -> SManga.ON_HIATUS
    MangaState.ABANDONED -> SManga.CANCELLED
    MangaState.RESTRICTED -> SManga.LICENSED
    MangaState.UPCOMING, null -> SManga.UNKNOWN
}

internal fun buildChapterName(title: String?, number: Float, volume: Int): String = buildString {
    if (volume > 0) {
        append("Vol. ").append(volume).append(' ')
    }
    val chapterTitle = title?.takeIf { it.isNotBlank() }
    when {
        chapterTitle != null -> append(chapterTitle)
        number > 0f -> append("Chapter ").append(number.formatChapterNumber())
        else -> append("Chapter")
    }
}

/** `3.0` reads as `3`, `3.5` stays `3.5`. */
internal fun Float.formatChapterNumber(): String = if (isFinite() && this == floor(this)) {
    toLong().toString()
} else {
    toString()
}

internal fun absoluteUrl(baseUrl: String, url: String): String = when {
    url.isEmpty() -> baseUrl
    url.startsWith("http://") || url.startsWith("https://") -> url
    url.startsWith("//") -> "https:$url"
    url.startsWith("/") -> baseUrl.trimEnd('/') + url
    else -> baseUrl.trimEnd('/') + "/" + url
}

/**
 * Decides whether a parser chapter list is already newest-first by looking at the direction of
 * every consecutive pair (not just the two ends, which misfires when numbering restarts per
 * volume/season). Upload dates win when they are present on both sides of a step; chapter numbers
 * are the fallback. Ties and unknown values are ignored. Ascending (Kotatsu's convention) is the
 * default when there is no evidence either way.
 */
internal fun List<MangaChapter>.isNewestFirst(): Boolean {
    if (size < 2) return false
    var descending = 0
    var ascending = 0
    for (i in 1 until size) {
        val prev = this[i - 1]
        val cur = this[i]
        val step = when {
            prev.uploadDate > 0L && cur.uploadDate > 0L && prev.uploadDate != cur.uploadDate ->
                prev.uploadDate.compareTo(cur.uploadDate)
            prev.number > 0f && cur.number > 0f && prev.number != cur.number ->
                prev.number.compareTo(cur.number)
            else -> 0
        }
        if (step > 0) {
            descending++
        } else if (step < 0) {
            ascending++
        }
    }
    return descending > ascending
}

private const val UNKNOWN_CHAPTER_NUMBER = -1f
