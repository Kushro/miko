package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * MIKO — a per-page bookmark inside a [BackupManga].
 *
 * Chapters have no stable cross-device id, so the owning chapter travels as its [chapterUrl] (the
 * same trick [BackupHistory] uses); on restore it is resolved against the chapters of the manga
 * being restored and dropped when no chapter matches.
 */
@Serializable
data class BackupPageBookmark(
    @ProtoNumber(1) var chapterUrl: String,
    @ProtoNumber(2) var pageIndex: Int,
    @ProtoNumber(3) var imageUrl: String? = null,
    @ProtoNumber(4) var note: String? = null,
    @ProtoNumber(5) var createdAt: Long = 0,
    /** Fraction (0..1) of the page that was scrolled past; null when unknown. See `PageBookmark`. */
    @ProtoNumber(6) var scrollFraction: Float? = null,
    /** Fraction (0..1) of the page height the user focused on (thumbnail/preview centre); null when unknown. */
    @ProtoNumber(7) var focusFraction: Float? = null,
    /**
     * The persisted viewport capture (WEBP, C20), gated at creation by
     * `BackupOptions.momentCaptures`. Empty = no capture travelled (pre-C20 backups instantiate
     * the default and restore fine); the writer caps blobs at 1.5 MB (PageBookmarkPreviewCodec).
     */
    @ProtoNumber(8) var previewWebp: ByteArray = ByteArray(0),
)
