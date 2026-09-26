# Navigation

**English** · [Español](./es.md) · [Documentation](../../README.md)

Choose a tab layout in **Settings → Appearance → Navigation bar**. The same preset
controls the bottom bar on phones and the navigation rail on tablets.

| Preset | Order |
|---|---|
| `MIHON` | Library, Updates, History, Browse, Moments, More |
| `KOTATSU` | History, Library, Browse, Updates, Moments, More |
| `COMPACT` | Library, Browse, Moments, More |
| `CUSTOM` (default) | MIHON order, with optional Updates and History |

Only CUSTOM uses the inherited Updates/History visibility switches. The first tab
is the home destination and the target of Back. Library search still opens Library.
If a preset change hides the selected tab, Miko selects the home tab.

Hidden Updates/History remain accessible from More, notifications and shortcuts.
They open as pushed screens when absent from the bar. Moments appears before More
in every preset. Its internal identifier remains `NavTabId.FAVORITES`.

## Shortcuts and appearance

The launcher has a Moments shortcut after Browse. It selects `FavoritesTab` directly.
Launchers limited to four shortcuts may hide this fifth entry. Six-tab layouts can
truncate labels on narrow screens. Moments currently has a static icon rather than
an animated one. The mascot can be disabled in Appearance without removing notes.

## Technical reference

`NavPreset.kt` defines IDs, ordered presets and pure `resolveNavTabs()`.
`UiPreferences.bottomNavPreset()` persists `pref_bottom_nav_preset` in preference
backups. `HomeScreen`, More fallback rows and `UpdatesTab`/`HistoryTab.isEnabled()`
must all use the resolver rather than independently reading visibility switches.
`NavPresetTest` checks required tabs, uniqueness and ordering. Launcher definitions
live in `app/shortcuts.xml`; `MainActivity` handles `SHORTCUT_FAVORITES`.
