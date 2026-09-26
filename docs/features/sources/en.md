# Sources

**English** · [Español](./es.md) · [Documentation](../../README.md)

Miko reads from installed extensions and built-in Kotatsu parsers. Availability and
features depend on each website; a listed source does not guarantee that its site works.

## Choose and organize sources

1. In **Settings → Browse**, choose extensions, built-in parsers or both.
2. Open the Sources tab. Use its display settings to group, sort or switch between list and grid.
3. Long-press a source to configure it, assign categories or open its information dialog.
4. Use the preset menu to activate a named source category. Clear the preset to restore all sources.

A preset filters Sources and global search, not the library or downloads. In global
search, **Pinned** and **All** still respect the active preset. Selecting a specific
preset chip overrides that restriction. Renaming or deleting a preset updates the
saved selection; a stale search preset falls back to All.

## Source kinds and capabilities

| Kind | Meaning |
|---|---|
| `LOCAL` | Files from the local source. |
| `BUILT_IN` | A bundled parser. |
| `BUILT_IN_DEDICATED` | An app-native source or a bundled parser with a matching enhancement. |
| `EXTENSION` | An extension source. |
| `EXTENSION_ENHANCED` | An extension with a delegate or matching enhancement. |
| `NOT_INSTALLED` | A missing source or stub. |

Badges appear in browsing, library covers, search, details and migration. Capabilities
describe supported operations such as search, tags, login, alternative domains and
comments. They are detected from interfaces, metadata and filter definitions, not
by testing the website. Classification prioritizes local/app-native IDs, missing
sources and bundled parsers before installed extensions.

## Site enhancements and extension trust

**Settings → Keiyoushi** and **Embedded providers** expose per-site switches, enabled
by default. Asura Scans provides site ratings and series/chapter comments. Comix
provides comments with cursor pagination, reply flattening and HTML conversion.
Use the Comments action in manga details or the reader's chat button when available.
Disabling a group removes its extra capabilities, rating and comment actions and
invalidates the capability cache.

The Extensions tab's **Trust all** action trusts pending untrusted extensions through
the existing per-extension trust operation. It is a one-time action, not a preference
that automatically trusts future installations.

## Technical reference

- `source-kotatsu/` adapts `kotatsu-parsers-redo` as `KotatsuParserSource : HttpSource`.
  `AndroidSourceManager` registers sources according to `SourcePreferences.sourceMode()`.
  `KotatsuLoaderContext` supplies networking, cookies, configuration and WebView helpers.
- Keep parser IDs stable: library entries and backups refer to them. Preserve the
  dependency's package names and the `libs.kotatsuParsers` catalog accessor.
- Model mapping preserves raw URLs. Alternate titles, per-manga content ratings and
  ratings are not preserved by the base `SManga` conversion; chapter branches become
  scanlators and volumes become chapter-name text. Completed/publishing-finished
  states share a target state. Site ratings can be fetched separately.
- `KotatsuDedicatedParsers` selects factories for dedicated adapters. Register a
  matching `SourceEnhancement` when an adapter should have a dedicated badge.
- `classifySourceKind` is pure. Resolve `SourceCapabilities` through
  `SourceCapabilitiesCache` outside composition; do not perform detection in a composable.
- `SourceEnhancementRegistry` exposes `RemoteRatingProvider` and `SourceCommentsProvider`.
  `EnhancementPreferences.groupEnabled()` controls every applicable registry lookup.
- Presets reuse `sources_tab_categories` and `sources_tab_source_categories`.
  `active_source_preset` stores the selection; `global_search_pinned_toggle_state`
  stores `SourceFilter`, including `Preset(name)`.
- Tests cover classification, capabilities, grouping, preset filters, registry switches
  and site response mapping under `app/src/test/` and `source-kotatsu/src/test/`.

## Limitations

Some parsers require unsupported WebView interception. This integration does not
include Kotatsu's automatic Cloudflare solver. Source icons are shared, and listing
many parsers needs device performance checks. Preset membership is refreshed when
searching, not continuously within an already-open search. Site API changes can
break enhancements independently of the source extension.
