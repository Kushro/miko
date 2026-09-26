# Recommendations

**English** · [Español](./es.md) · [Documentation](../../README.md)

Choose providers in **Settings → Advanced → Suggestion system**. Results appear
as related titles in manga details. Use one provider or a prioritized combination.

| Provider | Behavior |
|---|---|
| Osusume (`OSUSUME`, default) | Existing Komikku related results plus title-word searches. |
| Uwasa (`UWASA`) | Parser-provided relations for Kotatsu; site relations and the smallest nonempty keyword group for extensions. |
| Zokuhen (`ZOKUHEN`) | Removes trailing volume markers, searches up to three decreasing prefixes per title and ranks by Dice similarity. |
| Tagu Osekkai (`TAGU_OSEKKAI`) | Uses local tags then source genres, up to two tag searches, ranked by affinity. |
| AniList/MAL (`TRACKER`) | External community recommendations; tap for smart search or long-press for the tracker page. |

In multi-system mode providers run concurrently under supervision. Priority orders
their groups and deduplicates repeated entries. This is a combined result, not a
fallback chain. Single mode defaults to Osusume; Zokuhen must be enabled explicitly.

## Similarity and persistence

Zokuhen uses both title and original title as seeds. The first query with qualifying
results wins. The threshold is 50–100%, default 60%, and the dialog shows its slider
while Zokuhen participates. Results exclude the seed, sort by similarity and stop
at 20. Long titles only try their three longest candidate prefixes.

`RecommendationEngine` invokes the providers selected in `RecommendationPreferences`.
Settings serialize to `miko_recommendation_settings`; `zokuhenThreshold` is part of
that JSON. A provider/source/URL cache avoids repeated work. Confirming settings
invalidates the cache; restoring preferences from a backup does not immediately
clear every existing in-memory result.

The only caller is `MangaScreenModel.fetchRelatedMangasFromSource`. Existing related-title
and search-disable preferences remain authoritative. Kotatsu filter options use
the suspend `KotatsuSource.awaitFilterList()`. Provider code and pure ranking tests
live under `eu/kanade/domain/recommendation/` in app source and tests.

## Limitations

Tag matching normalizes names but does not translate between languages. NSFW metadata
is source-level, not per result. Tracker entries are transient (`source = -1`), never
inserted directly and cannot be favorited from the row; find a source entry first.
Device and real-source testing remain necessary for network-dependent results.
