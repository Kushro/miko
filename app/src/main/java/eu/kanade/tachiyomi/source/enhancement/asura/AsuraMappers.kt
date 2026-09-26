package eu.kanade.tachiyomi.source.enhancement.asura

import eu.kanade.domain.source.enhancement.SourceComment
import eu.kanade.domain.source.enhancement.SourceCommentsPage
import java.time.Instant

/*
 * MIKO — Asura Scans wire models → the source-agnostic comment models the UI consumes.
 *
 * Pure and network-free (unit tested in `AsuraMappersTest`). [SourceComment.text] keeps the site's
 * raw markup: rendering it is the UI's job.
 */

/** Roles worth a badge next to the author's name; anything else ("user") is not shown. */
private val BADGE_ROLES = setOf("admin", "moderator", "premium")

private const val PREMIUM_BADGE = "premium"

private const val UNKNOWN_AUTHOR = "?"

fun AsuraCommentDto.toSourceComment(): SourceComment = SourceComment(
    id = id.toString(),
    author = user?.username?.takeIf { it.isNotBlank() } ?: UNKNOWN_AUTHOR,
    text = content.orEmpty(),
    date = parseAsuraDate(createdAt),
    avatarUrl = user?.profilePictureUrl?.takeIf { it.isNotBlank() },
    authorBadge = user?.badge(),
    likes = upvotes,
    dislikes = downvotes,
    imageUrls = (mediaUrls.orEmpty() + gifUrl)
        .filterNotNull()
        .filter { it.isNotBlank() }
        .distinct(),
    isEdited = isEdited,
    isPinned = isPinned,
    replyCount = replyCount,
    replies = replies.orEmpty().map { it.toSourceComment() },
)

fun AsuraCommentsPageDto.toPage(): SourceCommentsPage = SourceCommentsPage(
    comments = data.orEmpty().map { it.toSourceComment() },
    hasNextPage = meta?.hasMore ?: false,
    total = meta?.total,
)

/** The role badge, falling back to "premium" for a premium user with an unremarkable role. */
private fun AsuraUserDto.badge(): String? {
    val normalisedRole = role?.trim()?.lowercase()
    return when {
        normalisedRole != null && normalisedRole in BADGE_ROLES -> normalisedRole
        isPremium -> PREMIUM_BADGE
        else -> null
    }
}

/**
 * Epoch millis of an Asura timestamp, tolerating fractional seconds and returning 0 (the "unknown"
 * value of [SourceComment.date]) for anything unparseable.
 */
internal fun parseAsuraDate(raw: String?): Long {
    if (raw.isNullOrBlank()) return 0L
    return runCatching { Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)
}
