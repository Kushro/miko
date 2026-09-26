# Download controls

**English** · [Español](./es.md) · [Documentation](../../README.md)

Configure download restrictions in Settings → Downloads. Miko retains Wi-Fi-only
downloads, download ahead, deletion after reading and a persistent manually ordered queue.

## Charging and schedule

Enable charging-only downloads or an off-peak window. These restrict automatic
starts and resumptions. **Start now** bypasses these two restrictions for that start;
it never bypasses Wi-Fi-only downloads. A manual pause schedules no automatic resume.

Windows use local minutes since midnight: start inclusive, end exclusive. An end
before the start crosses midnight; equal times allow the whole day. A chapter
started inside the window can finish after it closes. `DownloadJob` schedules
resumption with a charging constraint or a delay. `DownloadRestrictions` holds the
pure scheduling rules.

## Speed and storage

- Speed is a global limit in KiB/s; zero means unlimited. `DownloadThrottle` uses
  one process-wide token bucket with a one-second burst, shared by concurrent downloads.
  Preference changes apply live. Throttling wraps image-body copying in 8 KiB chunks,
  not the shared OkHttp client, so reader requests are unaffected.
- Storage quota is in MiB; zero disables it. Usage includes chapter directories and
  CBZ archives. `DownloadCache` memoizes sizes and invalidates them on mutations.
- `DownloadQuotaEnforcer` selects oldest downloads first, using file modification
  time or chapter fetch time, with chapter ID as tie-break. It deletes only enough
  to meet the quota through `DownloadManager.deleteChapters()`.
- Bookmarked chapters, excluded categories, local sources and queued/downloading
  chapters are protected. If the quota cannot be met, downloading stops.

Enforcement runs before a chapter starts and after completion with a five-second
debounce. Deletion is asynchronous; the enforcer waits a bounded period for the
index. Changing the quota alone does not purge files. The settings usage card is
not live download progress, and the quota dialog rejects values below current usage.

## Download unread library chapters

Open the download queue overflow menu, choose the unread-library download action
and confirm the number of series. Miko queues unread chapters not yet downloaded,
including existing merged-source handling.

## Technical reference

Preferences live in `DownloadPreferences` and travel in preference backups.
Implementation is in `app/.../data/download/`, with pure selection logic in
`PurgeCandidate.kt`. `DownloadRestrictionsTest` and `DownloadQuotaPolicyTest` cover
schedule boundaries and purge protections. Queue order remains the priority;
there is no additional priority field or separate scheduler.
