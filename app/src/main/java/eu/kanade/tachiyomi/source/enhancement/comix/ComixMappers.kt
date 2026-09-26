package eu.kanade.tachiyomi.source.enhancement.comix

import eu.kanade.domain.source.enhancement.SourceComment

/*
 * MIKO — comix.to wire models → the source-agnostic [SourceComment] the UI consumes. Pure and
 * network-free (`ComixMappersTest`).
 *
 * comix.to threads nest replies to any depth, while Miko's comment UI shows one level. Replies of
 * replies are therefore **flattened** under the top-level comment, in reading order, each prefixed
 * with `@<username>` of the comment it answers so the conversation stays followable. A flattened
 * reply keeps its own `replyCount` at 0 (the UI only offers "show replies" on top-level comments).
 */

private const val UNKNOWN_AUTHOR = "?"
private const val MOD_BADGE = "mod"

/** Converts a top-level comment and flattens its whole inline reply tree under it. */
fun ComixCommentDto.toSourceComment(now: Long = System.currentTimeMillis()): SourceComment {
    val flat = ArrayList<SourceComment>()
    replies.orEmpty().forEach { reply -> flattenReply(reply, parentUsername = null, into = flat, now = now) }
    return toSourceComment(now, replies = flat, replyCount = maxOf(replyCount, flat.size))
}

/** Converts replies fetched for one parent (`parent_id=…`), flattening whatever they nest. */
fun List<ComixCommentDto>.toFlatReplies(now: Long = System.currentTimeMillis()): List<SourceComment> {
    val flat = ArrayList<SourceComment>()
    forEach { reply -> flattenReply(reply, parentUsername = null, into = flat, now = now) }
    return flat
}

private fun flattenReply(
    reply: ComixCommentDto,
    parentUsername: String?,
    into: MutableList<SourceComment>,
    now: Long,
) {
    val mention = parentUsername?.let { "@$it " }.orEmpty()
    into += reply.toSourceComment(now, replies = emptyList(), replyCount = 0, textPrefix = mention)
    val username = reply.user?.username?.takeIf { it.isNotBlank() }
    reply.replies.orEmpty().forEach { nested -> flattenReply(nested, username, into, now) }
}

private fun ComixCommentDto.toSourceComment(
    now: Long,
    replies: List<SourceComment>,
    replyCount: Int,
    textPrefix: String = "",
): SourceComment {
    val content = commentHtmlToMarkup(contentHtml)
    val mediaUrl = media?.url?.takeIf { it.isNotBlank() }?.let(::absoluteSiteUrl)
    return SourceComment(
        id = id.toString(),
        author = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.username?.takeIf { it.isNotBlank() }
            ?: UNKNOWN_AUTHOR,
        text = textPrefix + content.text,
        date = parseComixRelativeDate(createdAtFormatted, now),
        avatarUrl = user?.avatar?.takeIf { it.isNotBlank() }?.let(::absoluteSiteUrl),
        authorBadge = if (user?.isMod == true) MOD_BADGE else null,
        likes = likeCount,
        dislikes = dislikeCount,
        imageUrls = (content.imageUrls + listOfNotNull(mediaUrl)).distinct(),
        isEdited = isEdited,
        isPinned = isPinned,
        replyCount = replyCount,
        replies = replies,
    )
}
