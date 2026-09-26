package eu.kanade.tachiyomi.source.enhancement.asura

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.enhancement.CommentsSort
import eu.kanade.domain.source.enhancement.EnhancementGroup
import eu.kanade.domain.source.enhancement.RemoteRatingProvider
import eu.kanade.domain.source.enhancement.SourceComment
import eu.kanade.domain.source.enhancement.SourceCommentsPage
import eu.kanade.domain.source.enhancement.SourceCommentsProvider
import eu.kanade.domain.source.enhancement.SourceEnhancement
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.miko.MKMR
import java.io.IOException
import kotlin.math.abs

/**
 * MIKO — the site's own score for an entry, shown next to the user's stars.
 *
 * Asura Scans reports a 0..10 double; the provider contract wants 0f..1f. A failure is never fatal
 * here: the rating is a decoration, so anything unexpected simply yields no rating.
 */
class AsuraScansRatingEnhancement(
    private val api: AsuraScansApi = AsuraScansApi.instance,
) : SourceEnhancement {

    override val name: String = "Asura Scans site rating"

    override val titleRes: StringResource = MKMR.strings.enhancement_asura_rating

    override val group: EnhancementGroup = EnhancementGroup.ASURA_SCANS

    override val remoteRating: RemoteRatingProvider = object : RemoteRatingProvider {
        override suspend fun getRemoteRating(manga: SManga): Float? {
            val slug = seriesSlugOf(manga.url) ?: return null
            return try {
                val rating = api.getSeries(slug)?.rating ?: return null
                if (rating <= 0.0) return null
                (rating / MAX_SITE_RATING).toFloat().coerceIn(0f, 1f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not fetch the Asura Scans rating of $slug" }
                null
            }
        }
    }

    private companion object {
        const val MAX_SITE_RATING = 10.0
    }
}

/**
 * MIKO — series and chapter comments from asurascans.com.
 *
 * The site keys comments by internal numeric ids, while the extension only stores slugs and chapter
 * numbers, so each call first resolves the id (both resolutions are cached in [AsuraScansApi]).
 * Failures throw: unlike the rating, a comments screen that silently shows nothing would be a lie.
 */
class AsuraScansCommentsEnhancement(
    private val api: AsuraScansApi = AsuraScansApi.instance,
) : SourceEnhancement {

    override val name: String = "Asura Scans comments"

    override val titleRes: StringResource = MKMR.strings.enhancement_asura_comments

    override val group: EnhancementGroup = EnhancementGroup.ASURA_SCANS

    override val extraFeatures: Set<SourceFeature> = setOf(SourceFeature.CHAPTER_COMMENTS)

    override val comments: SourceCommentsProvider = object : SourceCommentsProvider {

        override suspend fun getMangaComments(manga: SManga, page: Int, sort: CommentsSort): SourceCommentsPage {
            val seriesId = api.getSeries(seriesSlug(manga))?.id
                ?: throw IOException("Series not found on Asura Scans")
            return api.getSeriesComments(seriesId, page, sort).toPage()
        }

        override suspend fun getChapterComments(
            manga: SManga,
            chapter: SChapter,
            page: Int,
            sort: CommentsSort,
        ): SourceCommentsPage {
            val slug = seriesSlug(manga)
            val number = chapterNumberOf(chapter.url)
                ?: throw IOException("Unrecognised Asura Scans chapter URL: ${chapter.url}")
            return api.getChapterComments(resolveChapterId(slug, number), page, sort).toPage()
        }

        override suspend fun getReplies(comment: SourceComment): List<SourceComment> {
            if (comment.replies.isNotEmpty()) return comment.replies
            val commentId = comment.id.toLongOrNull() ?: return emptyList()
            return api.getReplies(commentId).map { it.toSourceComment() }
        }
    }

    private fun seriesSlug(manga: SManga): String = seriesSlugOf(manga.url)
        ?: throw IOException("Unrecognised Asura Scans series URL: ${manga.url}")

    /**
     * The site's id for chapter [number] of [slug]. A miss against a *cached* list can simply mean
     * the chapter is newer than the cache, so in that case the list is re-fetched once before giving
     * up; a miss against a fresh answer is final.
     */
    private suspend fun resolveChapterId(slug: String, number: Float): Long {
        val servedFromCache = api.hasCachedChapters(slug)
        api.getChapters(slug).findByNumber(number)?.let { return it.id }

        if (servedFromCache) {
            api.invalidateChapters(slug)
            api.getChapters(slug).findByNumber(number)?.let { return it.id }
        }
        throw IOException("Chapter not found on Asura Scans")
    }

    private fun List<AsuraChapterDto>.findByNumber(number: Float): AsuraChapterDto? =
        firstOrNull { abs(it.number - number.toDouble()) < NUMBER_TOLERANCE }

    private companion object {
        const val NUMBER_TOLERANCE = 0.001
    }
}
