# E-Hentai favorites synchronization

**English** · [Español](./es.md) · [Documentation](../../README.md)

Map the ten remote favorite slots to local categories before synchronizing. Miko
uses category names rather than category positions and never renames or reorders
local categories to match the website.

## Configure the mapping

1. Log in to ExHentai and open **Settings → E-Hentai → Favorites sync → Favorite category mapping**.
2. Refresh remote slot names/counts.
3. Assign a local category to each desired slot and enable it. **Map by order** supplies
   an editable initial mapping, not a permanent positional rule.
4. Resolve duplicate category assignments and save. At least one active mapping is required.

A missing mapped category stops synchronization. Category rename/delete operations
update the stored mapping. A gallery belonging to multiple active mapped categories
is rejected; membership in inactive/unmapped categories does not participate.

## Empty slots and snapshots

Slots with no downloaded remote galleries are skipped **in both directions**:
no pull, no push and no snapshot entry. Cached display counts do not override this
rule. If every enabled slot is empty, the operation returns without taking sync locks.

`favorite_entry` snapshots use `(gid, token, category)`, with category meaning the
remote slot. Differences are restricted to active slots. Re-enabling a slot or
repopulating an empty one triggers a first-run merge of both sides, which may upload
local entries. Read-only sync suppresses local-to-remote changes.

## Technical reference

`EhFavoritesSyncConfig` and `EhFavoritesSyncPlan` live in `domain/.../exh/favorites/`.
`exhFavoritesSyncConfig()` is backed up; `exhFavoritesUpstreamSlots()` is display-only
app state. `FavoritesSyncHelper` validates login/configuration, fetches galleries,
builds the plan and applies changes. `LocalFavoritesStorage` handles snapshots;
`FavoritesSyncPlanning` contains pure slot selection rules.

Tests in `EhFavoritesSyncConfigTest` and `FavoritesSyncPlanTest` cover mapping,
empty slots and active-category selection. Validate network failures, renames and
read-only behavior with an account before changing synchronization logic.
