package eu.kanade.tachiyomi.data.download

/**
 * MIKO — a downloaded chapter that may be purged to get back under the storage quota.
 *
 * This file holds the pure selection policy behind the quota (see [DownloadQuotaEnforcer]), kept
 * free of Android and IO so it can be unit tested (`DownloadQuotaPolicyTest`).
 *
 * @param chapterId id of the chapter in the database.
 * @param mangaId id of the manga the chapter belongs to.
 * @param sizeBytes size on disk of the chapter directory (or `.cbz`).
 * @param lastModified last modification time of that directory, in millis.
 */
data class PurgeCandidate(
    val chapterId: Long,
    val mangaId: Long,
    val sizeBytes: Long,
    val lastModified: Long,
)

/**
 * Picks the chapters to delete so the downloads fit back inside the quota.
 *
 * Oldest first (by [PurgeCandidate.lastModified] ascending, ties broken by chapter id so the result
 * is deterministic), accumulating just enough candidates to free `usageBytes - quotaBytes` and not
 * one more.
 *
 * @param candidates every purgeable chapter; the caller is responsible for leaving out the ones it
 * wants to protect (queued/downloading chapters).
 * @param usageBytes current size of the downloads.
 * @param quotaBytes the quota in bytes; `0` (or lower) means no quota.
 * @return the candidates to delete, in deletion order; empty when nothing has to be freed.
 */
fun selectChaptersToPurge(
    candidates: List<PurgeCandidate>,
    usageBytes: Long,
    quotaBytes: Long,
): List<PurgeCandidate> {
    if (quotaBytes <= 0L) return emptyList()

    val excessBytes = usageBytes - quotaBytes
    if (excessBytes <= 0L) return emptyList()

    val selected = mutableListOf<PurgeCandidate>()
    var freedBytes = 0L
    for (candidate in candidates.sortedWith(compareBy({ it.lastModified }, { it.chapterId }))) {
        if (freedBytes >= excessBytes) break
        selected += candidate
        freedBytes += candidate.sizeBytes
    }
    return selected
}
