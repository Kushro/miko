package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * MIKO — the kind of a chapter bookmark inside a [BackupManga].
 *
 * Only chapters whose kind is not `GENERIC` travel here; a generic bookmark is already carried by
 * `BackupChapter.bookmark`. Chapters have no stable cross-device id, so the chapter travels as its
 * [chapterUrl] (the same trick [BackupPageBookmark] and [BackupHistory] use) and is resolved
 * against the chapters of the manga being restored.
 *
 * [type] is [tachiyomi.domain.chapter.model.ChapterBookmarkType.id]; unknown values fall back to
 * `GENERIC` on restore.
 */
@Serializable
data class BackupChapterBookmarkType(
    @ProtoNumber(1) var chapterUrl: String,
    @ProtoNumber(2) var type: Int = 0,
)
