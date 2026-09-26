# Library organization

**English** · [Español](./es.md) · [Documentation](../../README.md)

Use categories, local tags and ratings to organize manga independently of source metadata.

## Categories and independent settings

Tap the category title to select another visible category. The picker respects category
visibility and the current grouping; it does not expose hidden categories.
Use the **Special** tab in library settings to give a category its own filters, sorting,
display, columns and grouping. Categories without a stored override use global settings.

`CategoryLibrarySettingsResolver` resolves and snapshots these settings. Overrides live
in `category_library_settings` (migration `51.sqm`) as `CategoryLibrarySettings` JSON.
Unknown JSON keys are ignored and defaults support older backups. `BackupCategory`
field `1000` carries the override. Deleting a category removes its settings.
Some inherited controls, including badges and included/excluded category filters,
remain global. Library title counts can still reflect the global filter.

## Tags and ratings

- Add local tags from manga details or copy a source genre into your tags. Removing a
  local tag never edits source metadata. Suggestions exclude tags already assigned.
- Search by tag text or `namespace:tag`. Use **My tags** to include or exclude tags
  and group entries by local tags. Untagged entries have their own group.
- Set a local rating from 1 to 5 or clear it. Filter rated/unrated manga, sort by
  local rating or group by score. Site ratings and tracker ratings are separate values.

`manga_tags` and `manga_ratings` are introduced in `49.sqm`. Tag uniqueness is
case-insensitive per manga; `rename` uses `UPDATE OR REPLACE` to handle collisions.
The display value is `namespace:name` when a namespace exists. An unrated manga has
no rating row, not a stored zero. Backup fields on `BackupManga` are `1000` for local
tags and `1001` for local rating; the backup uses zero to represent no rating.
Repositories and interactors live under `domain/.../manga/` and `data/.../manga/`.

## Similar-title grouping

Select **Similar titles** as a grouping layer and adjust the threshold from 50–100%
(default 60%). This layer cannot accompany genre or exact duplicate-title grouping.
The global preference is `library_title_similarity_threshold`; category overrides use
`CategoryLibrarySettings.titleSimilarityThreshold`.

`RuijiTitleClusterer` lowercases with `Locale.ROOT`, retains Unicode letters/digits
and computes Sørensen–Dice similarity over character-bigram multisets. Empty titles
never match; identical nonempty titles score 1. Union-find single-linkage connects
matching pairs, so two titles can share a group through intermediate titles.

`LibraryGroupingEngine.ruijiTitleBuckets` clusters distinct normalized titles before
per-item grouping and caches assignments in a 64-entry LRU. The shortest title is
the representative, with a deterministic tie-break. Natural order puts larger groups
first and singletons in “No similar titles”. A changed representative can reset a
group's collapsed state. Floating-point precision can affect exact threshold boundaries.

## Verification

Algorithm tests cover Unicode, similarity, empty inputs, transitive matches and
determinism. Category tests cover serialization and global/override resolution.
Run the category-settings SQL check from the repository root:

```bash
python3 scripts/checks/category-settings.py data/src/main/sqldelight/tachiyomi/migrations/51.sqm data/src/main/sqldelight/tachiyomi/data/category_library_settings.sq
```
