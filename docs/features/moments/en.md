# Moments

**English** · [Español](./es.md) · [Documentation](../../README.md)

Moments collects saved pages and bookmarked chapters. Some internal class names and
preference keys use `Favorites`; they identify this same feature.

## Save and revisit pages

1. Long-press a page in the reader and choose the page-bookmark action, or use the
   page bookmark button in the toolbar. In double-page mode the action sheet uses the first page.
2. Open Moments → Pages. Tap an entry to reopen the reader at that page.
3. Long-press an entry to open a full-screen preview. Zoom, edit its note, save,
   share or delete it. Delete-all requires confirmation.

Notes appear below each page. A source-visibility dialog filters the list using
screen-specific hidden sources and shared named visibility presets. The optional
Miko mascot reacts to loading, errors and actions; disable it in Appearance settings.
The note remains available when the mascot is disabled.

## Chapter bookmark types

Chapter bookmarks support generic, plot twist, character development and beautiful
art types. Change the type from chapter lists, multi-selection or the reader menu.
The bookmark glyph includes a type symbol and a long-press label. Removing the
bookmark removes its stored type. The Chapters tab filters by type; this filter
is not persisted and chapters have no bookmark date for date-based ordering.

## Positions, captures and backups

| Data | Meaning |
|---|---|
| `pageIndex` | Zero-based index within a chapter; UI displays page numbers starting at one. |
| `scroll_fraction` | Reader restore position, from 0 to 1 of page height (`52.sqm`). |
| `focus_fraction` | Thumbnail/preview focus point (`53.sqm`); does not change reader restoration. |
| `previewWebp` | Optional stored viewport capture, at most 1.5 MB (`54.sqm`). |

Webtoon long-press records the touched point; the toolbar uses the viewport center.
Paged viewers store unknown fractions. Preview alignment falls back from focus to
scroll position, then center. Reader restoration waits for the image's actual size.

`PageCapture` uses PixelCopy on the visible viewport, excluding menu-covered bars.
Brightness/color overlays remain in the capture; an in-flight upscale captures what
is currently visible. Captures and bookmarks are inserted atomically. Coil's preview
fetcher reads the stored blob; without one, preview resolution falls back through
cache, downloaded image and source. CBZ downloads are not handled by that download fallback.

The storage card supports recompression and generating missing captures. Its counts
and maintenance actions use all saved pages, not only the visibility-filtered list.
It appears only when the Pages list is nonempty.

`page_bookmarks` (`49.sqm`) is unique by chapter/page and cascades on parent deletion.
`chapter_bookmark_types` (`50.sqm`) stores types separately from inherited chapter flags.
`page_bookmark_previews` (`54.sqm`) cascades with its bookmark. Replacement during
restore preserves bookmark IDs so capture relationships survive.

`BackupManga` fields `1002` and `1003` carry page bookmarks and chapter types.
`BackupPageBookmark` fields `6`, `7` and `8` carry scroll, focus and capture data.
Captures are included only when the `momentCaptures` backup option is selected.

## Verification and limitations

SQL checks in `scripts/checks/` cover chapter types, page positions and captures
against the real migrations. Their headers list required input files. Android
device checks are still needed for PixelCopy, zoom, double pages, loading pages,
Coil rendering, bulk compression and backup/restore with captures. Opening a
saved page follows normal reading/history behavior, not an automatic incognito mode.
