package eu.kanade.domain.source.enhancement

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.source.model.SourceFeature
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * MIKO — a piece of dedicated Miko code attached to one source (bundled parser or installed
 * extension). Registering an enhancement is what turns a plain `BUILT_IN`/`EXTENSION` source into
 * `BUILT_IN_DEDICATED`/`EXTENSION_ENHANCED` in the UI, and it is the hook for source-specific
 * features that neither the parsers library nor the extension API offer (comments, site ratings,
 * custom tag systems, …).
 *
 * Every enhancement belongs to an [EnhancementGroup]; the user enables/disables the whole group
 * from Settings (see [EnhancementPreferences]). [SourceEnhancementRegistry] hides disabled
 * enhancements from every consumer, so implementations never check the switch themselves.
 *
 * Implementations must be cheap to construct and side-effect free at registration time.
 */
interface SourceEnhancement {

    /** Stable English name, for logs (e.g. "Asura Scans comments"). */
    val name: String

    /** Localised label shown in Settings under the group's switch ("Site rating", "Comments…"). */
    val titleRes: StringResource

    /** The switch this enhancement follows. */
    val group: EnhancementGroup

    /** Whether this enhancement applies to the given runtime source. Defaults to the group's rule. */
    fun matches(source: Source): Boolean = group.matches(source)

    /** Features this enhancement adds on top of what the source declares by itself. */
    val extraFeatures: Set<SourceFeature>
        get() = emptySet()

    /** Site comments, when this enhancement provides them ([SourceFeature.CHAPTER_COMMENTS]). */
    val comments: SourceCommentsProvider?
        get() = null

    /** The site's own rating for an entry, when this enhancement can fetch it. */
    val remoteRating: RemoteRatingProvider?
        get() = null
}

/**
 * MIKO — fetches the rating the source's site shows for an entry.
 *
 * Same contract as `KotatsuSource.getRemoteRating`: normalised to `0f..1f` (a 9.6/10 site score is
 * `0.96f`), `null` when the site has none or it could not be fetched. `MangaScreenModel` renders it
 * next to the user's own stars through `MangaRatingRow`.
 */
interface RemoteRatingProvider {
    suspend fun getRemoteRating(manga: SManga): Float?
}

/** Sort orders a [SourceCommentsProvider] can offer; providers ignore the ones they do not support. */
enum class CommentsSort {
    TOP,
    NEWEST,
}

/**
 * MIKO — per-series and per-chapter comments coming from the source's site.
 *
 * Pages are 1-based; the provider decides the page size. Both the "Comments" screen opened from the
 * manga details and the sheet opened from the reader consume this.
 */
interface SourceCommentsProvider {

    /** Whether [getMangaComments] is meaningful for this site (some sites only comment chapters). */
    val supportsMangaComments: Boolean
        get() = true

    /** Whether [getChapterComments] is meaningful for this site. */
    val supportsChapterComments: Boolean
        get() = true

    suspend fun getMangaComments(manga: SManga, page: Int, sort: CommentsSort): SourceCommentsPage

    suspend fun getChapterComments(manga: SManga, chapter: SChapter, page: Int, sort: CommentsSort): SourceCommentsPage

    /**
     * Full reply list of [comment], for sites that ship only a preview inline. The default returns
     * whatever [SourceComment.replies] already holds.
     */
    suspend fun getReplies(comment: SourceComment): List<SourceComment> = comment.replies
}

data class SourceCommentsPage(
    val comments: List<SourceComment>,
    val hasNextPage: Boolean,
    /** Total top-level comments when the site reports it, else null. */
    val total: Int? = null,
)

/**
 * One comment. [text] is the raw text as the site stores it — line breaks are `\n` and it may carry
 * the site's lightweight markup (`**bold**`, `*italic*`, `~~strike~~`, `||spoiler||`, `@mention`);
 * the UI is responsible for rendering that.
 */
data class SourceComment(
    val id: String,
    val author: String,
    val text: String,
    /** Epoch millis, 0 when unknown. */
    val date: Long,
    val avatarUrl: String? = null,
    /** Short label for the author's role/status when notable ("admin", "moderator", "premium"), else null. */
    val authorBadge: String? = null,
    val likes: Int? = null,
    val dislikes: Int? = null,
    /** Images/GIFs attached to the comment, absolute URLs, de-duplicated, in display order. */
    val imageUrls: List<String> = emptyList(),
    val isEdited: Boolean = false,
    val isPinned: Boolean = false,
    /** Number of replies the site reports; may exceed [replies].size when replies are lazy. */
    val replyCount: Int = 0,
    val replies: List<SourceComment> = emptyList(),
)

/**
 * MIKO — the list of every [SourceEnhancement] Miko ships. Register new enhancements in the
 * factory in `AppModule` (`addSingletonFactory { SourceEnhancementRegistry(listOf(...), get()) }`).
 *
 * Every lookup respects the group switch: a disabled group's enhancements are invisible here, which
 * is what makes badges, feature icons, the comments button and the site rating all disappear at
 * once when the user turns a group off.
 */
class SourceEnhancementRegistry(
    private val enhancements: List<SourceEnhancement>,
    private val preferences: EnhancementPreferences,
) {

    /** Every registered enhancement, enabled or not. */
    val all: List<SourceEnhancement>
        get() = enhancements

    fun isEnabled(enhancement: SourceEnhancement): Boolean = preferences.isEnabled(enhancement.group)

    /** Enhancements registered under [group], enabled or not (for the Settings screens). */
    fun enhancementsIn(group: EnhancementGroup): List<SourceEnhancement> = enhancements.filter { it.group == group }

    /** Enhancements that apply to [source] **and** whose group is enabled. */
    fun enhancementsFor(source: Source): List<SourceEnhancement> =
        enhancements.filter { it.matches(source) && isEnabled(it) }

    fun hasEnhancement(source: Source): Boolean = enhancementsFor(source).isNotEmpty()

    fun extraFeatures(source: Source): Set<SourceFeature> =
        enhancementsFor(source).flatMapTo(mutableSetOf()) { it.extraFeatures }

    fun commentsProvider(source: Source): SourceCommentsProvider? =
        enhancementsFor(source).firstNotNullOfOrNull { it.comments }

    fun remoteRatingProvider(source: Source): RemoteRatingProvider? =
        enhancementsFor(source).firstNotNullOfOrNull { it.remoteRating }
}
