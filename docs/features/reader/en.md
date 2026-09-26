# Reading and image enhancement

**English** · [Español](./es.md) · [Documentation](../../README.md)

Miko provides configurable viewers, reading directions, double pages and autoscroll.
Use reader settings to select image enhancement locally or through a companion server.
The Upscaling Hub manages models, progress and enhanced chapters. Slider markers show
download and enhancement progress. Scanlator priorities choose one version of each
chapter while retaining reading progress for hidden duplicates.

## Enhance a cover

1. Add the manga to your library and open its full-screen cover.
2. Choose **Enhance** and select the on-device or remote engine.
3. Wait for completion or cancel explicitly. The result becomes the custom cover.

The remote option stays disabled until a configured server reports it is ready.
On-device models ship in the APK and use the reader's model, scale, noise and
performance settings. Native progress is shown when available; otherwise a spinner
indicates work. There is no separate per-cover model selector, preview or undo.
Use the cover's edit/delete action to restore the original.

## Maintain cover storage

The viewer shows file size when measurable. **Redownload** confirms removal of the
custom/enhanced cover and cached network file before requesting the original again.
**Recompress** writes lossy WebP at quality 85 and keeps it only if smaller.
These maintenance actions apply to library entries with network sources, not local
files. On narrow screens they are grouped in the overflow menu.

## Technical reference and limitations

`MangaCoverScreenModel` owns enhancement state separately from the manga dialog so
progress overlays do not replace the cover viewer. It requests software bitmaps with
hardware allocation and memory-cache insertion disabled. Coil-owned bitmaps are never
recycled by the feature. Results are clamped, encoded and saved through `Manga.editCover`;
`coverLastModified` invalidates image keys.

Remote cancellation stops coroutine work. A blocking native call finishes before
its cancelled result is recycled. The native engine has a single model/mutex, so
changing models while reader enhancement runs can interrupt that page's enhancement.
Recompression uses a temporary file plus rename and rechecks size before replacement.
Large images may be sampled to limit memory. These paths still need device checks.
