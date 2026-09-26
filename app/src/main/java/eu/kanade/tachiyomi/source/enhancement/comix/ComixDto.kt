package eu.kanade.tachiyomi.source.enhancement.comix

import kotlinx.serialization.Serializable

/*
 * MIKO — wire models for the comment threads of comix.to (`/api/v1/threads/...`), the only part of
 * the site's API this enhancement reads. Unlike `/api/v1/manga`, the thread endpoints are not
 * request-signed; they do sit behind Cloudflare, which Mihon's interceptor clears.
 *
 * Field names are camelCase on the wire already. Only ids are required; everything else has a
 * default so a renamed or dropped field degrades instead of throwing (the app's shared
 * [kotlinx.serialization.json.Json] ignores unknown keys and treats explicit nulls as defaults).
 */

/** Envelope of every thread endpoint: `{"status":"ok","result":{...}}`. */
@Serializable
data class ComixThreadResponseDto(
    val status: String? = null,
    val result: ComixThreadResultDto? = null,
)

/**
 * `result` of `GET /threads/{id}/comments` (with `items` + `cursor`) and of `GET /threads/lookup`
 * (only `thread`).
 */
@Serializable
data class ComixThreadResultDto(
    val thread: ComixThreadDto? = null,
    /** Number of items in this page. */
    val count: Int? = null,
    val items: List<ComixCommentDto>? = null,
    /** Opaque cursor for the next page (`"0:4691716:5"`); empty when the site has nothing more. */
    val cursor: String? = null,
)

@Serializable
data class ComixThreadDto(
    val id: Long,
    /** `manga` for both series and chapter threads. */
    val objectType: String? = null,
    /** `manga32026` for a series, `manga32026_chap200_vol0` for a chapter. */
    val pageIdentifier: String? = null,
    val pageUrl: String? = null,
    val pageTitle: String? = null,
    /** Every comment including replies. */
    val commentCount: Int? = null,
    /** Top-level comments only — the total the "Comments" screen shows. */
    val mainCommentCount: Int? = null,
    val isClosed: Boolean = false,
)

@Serializable
data class ComixCommentDto(
    val id: Long,
    val threadId: Long? = null,
    /** `0` for a top-level comment. */
    val parentId: Long? = null,
    val status: String? = null,
    val user: ComixUserDto? = null,
    /** HTML: `<br/>`, `<b>`, `<i>`, `<s>`, `<span class="spoil">`, `<img class="rich-img">`. */
    val contentHtml: String? = null,
    val media: ComixMediaDto? = null,
    val likeCount: Int? = null,
    val dislikeCount: Int? = null,
    /** Direct replies only. */
    val replyCount: Int = 0,
    val isPinned: Boolean = false,
    val isEdited: Boolean = false,
    /** The site only sends a relative label (`"9mos ago"`, `"2w ago"`). */
    val createdAtFormatted: String? = null,
    /** Replies shipped inline (a preview, may be nested several levels deep). */
    val replies: List<ComixCommentDto>? = null,
    /** How many of [replyCount] are in [replies]. */
    val shownReplies: Int? = null,
    /** Cursor to fetch the replies after the inline ones (`GET …/comments?parent_id=…&cursor=…`). */
    val cursor: String? = null,
)

@Serializable
data class ComixUserDto(
    val id: Long? = null,
    val hashId: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    /** Site-relative (`/images/avatars/1/1874.webp?t=…`) or null. */
    val avatar: String? = null,
    val isMod: Boolean = false,
)

@Serializable
data class ComixMediaDto(
    val filename: String? = null,
    /** Site-relative (`/images/cmm/2a/2a8c….webp`). */
    val url: String? = null,
    val isSpoiler: Boolean = false,
)
