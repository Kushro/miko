package eu.kanade.tachiyomi.source.enhancement.asura

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * MIKO — wire models for `https://api.asurascans.com/api`, limited to what the enhancement reads
 * (site rating + comments). The API is read-only and needs no auth.
 *
 * Only ids are required; everything else is optional with a default so a field the site renames or
 * drops degrades instead of throwing. The app's shared [kotlinx.serialization.json.Json] is
 * configured with `ignoreUnknownKeys` and `explicitNulls = false`, so unknown keys are dropped and
 * an explicit `null` falls back to the default.
 */

// ---------------------------------------------------------------------------------------------
// Series
// ---------------------------------------------------------------------------------------------

/** `GET /series/{slug}` — the series sits under a `series` key, next to unrelated extras. */
@Serializable
data class AsuraSeriesResponseDto(
    val series: AsuraSeriesDto? = null,
)

@Serializable
data class AsuraSeriesDto(
    /** Internal numeric id, the one the comments endpoints expect (never the slug). */
    val id: Long,
    val slug: String? = null,
    val title: String? = null,
    /** Site score on a 0..10 scale, e.g. `9.598958333333334`. */
    val rating: Double? = null,
    @SerialName("popularity_rank") val popularityRank: Int? = null,
    /** Public path of the series, with the random suffix: `/comics/absolute-sword-sense-b60d532c`. */
    @SerialName("public_url") val publicUrl: String? = null,
)

// ---------------------------------------------------------------------------------------------
// Chapters
// ---------------------------------------------------------------------------------------------

/** `GET /series/{slug}/chapters` — newest first, not paginated. */
@Serializable
data class AsuraChaptersResponseDto(
    val data: List<AsuraChapterDto>? = null,
)

@Serializable
data class AsuraChapterDto(
    /** Internal numeric id, the one `GET /chapters/{id}/comments` expects. */
    val id: Long,
    /** Chapter number as a decimal (`197`, `197.5`); matched against `SChapter.url`. */
    val number: Double = 0.0,
    val slug: String? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
)

// ---------------------------------------------------------------------------------------------
// Comments
// ---------------------------------------------------------------------------------------------

/** `GET /series/{id}/comments` and `GET /chapters/{id}/comments`. */
@Serializable
data class AsuraCommentsPageDto(
    val data: List<AsuraCommentDto>? = null,
    val meta: AsuraCommentsMetaDto? = null,
)

@Serializable
data class AsuraCommentsMetaDto(
    @SerialName("has_more") val hasMore: Boolean = false,
    /** Total top-level comments of the thread, when reported. */
    val total: Int? = null,
)

@Serializable
data class AsuraCommentDto(
    val id: Long,
    /** Raw text, with the site's lightweight markup; never transformed here. */
    val content: String? = null,
    /** ISO-8601 UTC, sometimes with fractional seconds (`2025-04-25T04:53:53.97559Z`). */
    @SerialName("created_at") val createdAt: String? = null,
    val upvotes: Int? = null,
    val downvotes: Int? = null,
    @SerialName("gif_url") val gifUrl: String? = null,
    @SerialName("media_urls") val mediaUrls: List<String>? = null,
    @SerialName("is_edited") val isEdited: Boolean = false,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("reply_count") val replyCount: Int = 0,
    /** Replies come inline, all of them, one level deep only. */
    val replies: List<AsuraCommentDto>? = null,
    val user: AsuraUserDto? = null,
)

@Serializable
data class AsuraUserDto(
    val id: Long? = null,
    val username: String? = null,
    /** May be an empty string when the user has no avatar. */
    @SerialName("profile_picture_url") val profilePictureUrl: String? = null,
    /** `user`, `admin`, `moderator`, `premium`. */
    val role: String? = null,
    @SerialName("is_premium") val isPremium: Boolean = false,
)
