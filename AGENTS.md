# Miko – AI Agent Guide

Miko is an Android manga reader (min SDK 26, target SDK 36, JVM 17 / Kotlin) forked from **Komikku**, which itself descends from **TachiyomiSY** + **Mihon** + **Tachiyomi**. Stack: Jetpack Compose + Material3, Voyager navigation, SQLDelight, Injekt DI, NCNN/Vulkan for on-device upscaling. `applicationId`: **`app.miko`**.

The Kotlin namespace (`eu.kanade.tachiyomi`, `tachiyomi.*`, `mihon.*`, `exh.*`) is **deliberately unchanged** — renaming packages would make merges from upstream impossible. Merge upstream is `komikku-app/komikku` (git remote `upstream`); keep the diff in files upstream touches often as small as you can.

License: **GPL-3.0-only** (`LICENSE`). Inherited Apache-2.0 code keeps its licence and attribution (`LICENSE-APACHE`, `NOTICE`) — do not strip either.

---

## Mandatory rules for AI agents

**Read this section before every change.** These rules override shortcuts (e.g. copying nearby `MR` imports or only running `compileDebugKotlin`).

### Git

| Rule | Required behavior |
|------|-------------------|
| Branch | Create a **feature branch** for the task (`git checkout -b <type>/<short-description>`). |
| Commit | **OK** on a feature branch when work is ready. **Never** commit directly to `master` / `main` unless the user explicitly asks. |
| Push | **OK** to push the **current feature branch** when work is ready. **Never** push to `master` / `main` unless the user explicitly asks. |

Before `git push`, confirm the current branch is not `master` or `main` (`git branch --show-current`).

### Internationalization (strings)

| String kind | Module | Resource class | Base folder only |
|-------------|--------|----------------|------------------|
| **Miko-only (all new work)** | `i18n-miko/` | **`MKMR`** | `i18n-miko/src/commonMain/moko-resources/base/` |
| Komikku-inherited (KMK UI, library-update errors, WebDAV, Discord, etc.) | `i18n-kmk/` | **`KMR`** | `i18n-kmk/src/commonMain/moko-resources/base/` |
| Shared Mihon / upstream behavior | `i18n/` | **`MR`** | `i18n/src/commonMain/moko-resources/base/` |
| TachiyomiSY-inherited | `i18n-sy/` | **`SYMR`** | `i18n-sy/src/commonMain/moko-resources/base/` |

**Hard rules:**

- **Never** add Miko-specific strings to `i18n/`, `i18n-kmk/` or `i18n-sy/` — those three are upstream layers and any addition collides on the next merge. New strings go to `i18n-miko/` (namespace `tachiyomi.i18n.miko`, gradle accessor `projects.i18nMiko`).
- **Never** edit non-`base` locale `strings.xml` or `plurals.xml` files in `i18n/`, `i18n-kmk/`, or `i18n-sy/` — translations there are owned by upstream's Weblate. Miko has **no Weblate project**, so `i18n-miko/` locales are edited by hand in ordinary PRs.
- Import: `import tachiyomi.i18n.miko.MKMR` for Miko strings, `tachiyomi.i18n.kmk.KMR` for inherited Komikku strings.
- If a change is inside `// MIKO -->` … `// MIKO <--` or adds Miko-only behavior, default to **`MKMR` + `i18n-miko`**.

**Self-check before finishing:** `git diff` must not add new `<string name="…">` or `<plurals name="…">` entries under non-`base` locales in `i18n/src/`, `i18n-kmk/src/`, or `i18n-sy/src/`.

### Formatting & build verification

**“Build passes” is not enough.** After Kotlin/XML edits, run **in this order** before marking work complete:

```bash
./gradlew spotlessApply    # fix formatting
./gradlew spotlessCheck    # must pass (same as CI)
./gradlew assembleDebug    # or :app:compileDebugKotlin for a faster compile-only check
```

- **Do not** skip `spotlessCheck` when verifying changes.
- If `spotlessCheck` fails, run `spotlessApply` and re-run `spotlessCheck`.
- **Build on Linux/WSL, not Windows** — the NDK/CMake/NCNN native module trips over Windows path limits. Exact invocation from a Windows host:

```bash
# In your Linux/WSL shell, configure JAVA_HOME and ANDROID_HOME for your installation.
cd /path/to/miko
bash ./gradlew assembleDebug
```

  A build over the `/mnt/…` bridge takes ~20 minutes cold. When several agents work in parallel on one working tree, **only the orchestrator builds** — parallel Gradle runs on the same tree corrupt each other's caches.

---

## Module layout

| Module | Purpose |
|--------|---------|
| `app/` | UI (`eu.kanade.*`, `exh/`, `mihon/`), DI, workers, build variants |
| `domain/` | Use cases in `…/interactor/` (e.g. `GetManga`), models, repo interfaces |
| `data/` | SQLDelight DB, `*RepositoryImpl` (`tachiyomi.data.*`) |
| `core:common/` | Network (OkHttp), security, storage, shared utils |
| `core:archive/` | CBZ/archive reading with optional encryption |
| `core-metadata/` | Comic-info metadata parsing |
| `source-api/` / `source-local/` | Extension `Source` API + local source |
| `source-kotatsu/` | **Miko**: built-in Kotatsu parsers exposed as `HttpSource` (`tachiyomi.source.kotatsu`) |
| `presentation-core/` | Shared Compose components |
| `presentation-widget/` | Home-screen Glance widget |
| `i18n/` | Mihon strings → `MR` (moko-resources) |
| `i18n-miko/` | **Miko strings → `MKMR`** (all new strings go here) |
| `i18n-kmk/` | Komikku strings → `KMR` |
| `i18n-sy/` | TachiyomiSY strings → `SYMR` |
| `flagkit/` | Country-flag drawables |
| `telemetry/` | Firebase/Crashlytics (noop unless `-Pinclude-telemetry`) |
| `macrobenchmark/` | Macrobenchmark tests |

Dependency flow: `app` → `domain` → `source-api`; `data` implements `domain` repos.

Version catalogs: `gradle/libs.versions.toml`, `kotlinx.versions.toml`, `androidx.versions.toml`, `compose.versions.toml`, `sy.versions.toml`.

---

## Architecture

**DI** – `uy.kohesive.injekt` (not Hilt). Register in `AppModule.kt`, `DomainModule.kt`, `KMKDomainModule.kt`, `SYDomainModule.kt` via `addSingleton` / `addSingletonFactory`. Resolve with `Injekt.get<T>()` or `injectLazy<T>()`.

**UI & navigation** – [Voyager](https://voyager.adriel.cafe/): `Screen` in `eu.kanade.tachiyomi.ui.*`, composables in `eu.kanade.presentation.*`. Base type: `eu.kanade.presentation.util.Screen`. State via `rememberScreenModel { … }`; most models extend `StateScreenModel<State>` or bases like `SearchScreenModel`; some use plain `ScreenModel`. Prefer `screenModelScope` and `ioCoroutineScope`; use `launchIO` / `withIOContext` from `tachiyomi.core.common.util.lang`. `rememberCoroutineScope()` is fine in Compose; long-lived services may use their own `CoroutineScope`.

**Activities (not Voyager)** – `MainActivity` (shell), `ReaderActivity` + `ReaderViewModel`, `WebViewActivity`, `UnlockActivity`, OAuth login activities, `DeepLinkActivity`. Reader: `ReaderActivity.newIntent(context, mangaId, chapterId)`. Web: both `WebViewScreen` (Voyager) and `WebViewActivity.newIntent(...)`.

Example: `DeepLinkScreen` + `DeepLinkScreenModel` in `app/src/main/java/eu/kanade/tachiyomi/ui/deeplink/`.

**Domain / data** – One class per operation under `domain/…/interactor/` (verb names, not `*Interactor` suffix). Also `app/src/main/java/eu/kanade/domain/…/interactor/` for app-specific cases. Wire repos in `eu.kanade.domain.DomainModule.kt` (+ `KMKDomainModule`, `SYDomainModule`).

**Database** – SQLDelight in `data/src/main/sqldelight/tachiyomi/` (`.sq` queries, `migrations/*.sqm`). After schema changes add a new `.sqm` and often `// KMK` blocks in `.sq` / mappers. Regenerate: `./gradlew :data:generateSqlDelightInterface` (or any compile that touches `:data`).

**App preference migrations** – `app/src/main/java/mihon/core/migration/migrations/` (`mihon.core.migration.Migration`).

**Images** – Coil 3 (`coil3.*`, `context.imageLoader`). No Glide/Picasso.

---

## Miko-specific work

- **Strings:** see [Mandatory rules – Internationalization](#mandatory-rules-for-ai-agents). Summary: new work → **`MKMR`** / `i18n-miko/…/base/` only.
- **Identity constants** live in one place: `core/common/src/main/kotlin/tachiyomi/core/common/Constants.kt` (`MIKO_APP_NAME`, `MIKO_GITHUB_OWNER`, `MIKO_GITHUB_REPO`, `URL_MIKO_*`). Keep repository identity changeable from this file rather than duplicating literals in application code.
- Inherited Komikku code/DI: search `// KMK` (e.g. `KMKDomainModule`, `HideCategory`, library-update errors). Leave those blocks intact; add new behaviour in a `// MIKO` block next to them.
- Prefs: `eu.kanade.domain.*.service.*Preferences` (e.g. `SourcePreferences.relatedMangas()`).
- **Navigation bar presets** (Miko): the bottom bar / rail is no longer the hardcoded `HomeScreen.TABS`. Order and visibility come from `UiPreferences.bottomNavPreset()` resolved by the pure `resolveNavTabs(...)` in `eu.kanade.domain.ui.model.NavPreset`; the SY switches `showNavUpdates` / `showNavHistory` only apply to the `CUSTOM` preset. Read `docs/features/navigation/en.md` before touching `HomeScreen`, `UpdatesTab`/`HistoryTab.isEnabled()` or `SettingsAppearanceScreen.getNavbarGroup()`.

### Names kept on purpose (do NOT "clean these up")

These still say *komikku* and that is intentional. Renaming them breaks builds, trust checks or upstream merges:

| Thing | Why it stays |
|---|---|
| `drawable/ic_komikku.xml`, `ic_komikku_dark.xml`, `drawable/komikku.png` | File names kept so ~22 `R.drawable.*` call-sites and upstream merges are untouched. **Contents** are the Miko mark (torii vectors / miko-portrait raster). |
| `mipmap/komikku.png` | Badge for Komikku's *extension repo* in the repo-management UI, tied to `KOMIKKU_SIGNATURE`. Not Miko branding. |
| `KOMIKKU_SIGNATURE` / `REPO_SIGNATURE` (`ExtensionStore.kt`, `TrustExtension.kt`) | Real SHA-256 signing-certificate fingerprints. Cryptographic values, not text. |
| `i18n-kmk` module and `KMR` | Upstream string layer; renaming it would conflict with every Komikku merge. |
| `com.github.komikku-app:*` in `gradle/libs.versions.toml` | Real JitPack Maven coordinates. |
| `com.github.clquwu:kotatsu-parsers-redo:<hash>` (alias `kotatsuParsers` in `gradle/libs.versions.toml`) | Real JitPack coordinate pinned to a commit hash; the alias is camelCase so the accessor stays `libs.kotatsuParsers`. The `org.koitharu.kotatsu.parsers.*` package inside it is the upstream library's, not ours — never rename it or its ProGuard rules. |
| `komikku://` / `mihon://` schemes in the manifest | Registered OAuth redirect URIs on AniList/MAL/Bangumi/Shikimori. `miko://` was **added**, not substituted. |
| `.tachibk`, `tachiyomi.extension` feature string, `eu.kanade.*` packages | Ecosystem/back-compat contracts. |

---

## Extensions & sources

- Catalog sources: installable APK extensions (not in this repo).
- In-repo: delegated sources and metadata in `exh/` (E-Hentai, NHentai, MangaDex, `exh/recs/`).
- In-repo (**Miko**): `source-kotatsu/` wraps every non-broken `MangaParserSource` of the
  `kotatsu-parsers-redo` library as a `KotatsuParserSource : HttpSource`, registered by
  `AndroidSourceManager` next to extensions and `LocalSource`. Gated by
  `SourcePreferences.sourceMode()` (`BOTH` / `EXTENSIONS` / `PARSERS`).
  Read `docs/features/sources/en.md` before touching it.
- **Source taxonomy** (Miko): every source is classified into a `SourceKind` (`LOCAL` / `BUILT_IN` /
  `BUILT_IN_DEDICATED` / `EXTENSION` / `EXTENSION_ENHANCED` / `NOT_INSTALLED`) by the pure
  `classifySourceKind(...)`, and its `SourceFeature`s are detected statically by
  `SourceCapabilities` behind `SourceCapabilitiesCache` — never call either from a composable.
  `SourceEnhancement`/`SourceEnhancementRegistry` is what promotes a source to the
  `*_DEDICATED`/`*_ENHANCED` kinds and is the seam for per-source extras (chapter comments);
  `KotatsuDedicatedParsers` is the matching seam inside `source-kotatsu`. Read
  `docs/features/sources/en.md` before touching source classification, capability icons or the
  Sources tab grouping.
- **Source presets** (Miko): a preset *is* a SY source category; only `active_source_preset`
  (`SourcePreferences.activeSourcePreset()`) is new, and it filters the Sources tab and global
  search exclusively. Global search additionally offers one chip per preset
  (`SourceFilter.Preset(name)`, persisted in `global_search_pinned_toggle_state`). Read
  `docs/features/sources/en.md` before touching source categories.
- **Library "Special" categories** (Miko): a category with a row in `category_library_settings`
  (migration 51, `CategoryLibrarySettings` JSON) keeps its own filters/sort/display/grouping;
  `CategoryLibrarySettingsResolver` decides what is effective per category. Read
  `docs/features/library/en.md` before touching `LibraryScreenModel`'s pipelines or
  `LibrarySettingsDialog`.
- **"Similar titles" grouping** (Miko): `LibraryGroupType.RUIJI_TITLES` clusters library titles
  by Dice-bigram similarity (`RuijiTitleClusterer` in `:domain`, pure and fully unit-tested;
  union-find single-linkage). `LibraryGroupingEngine.ruijiTitleBuckets` intercepts the type before
  the per-item `keysFor` loop and memoizes cluster assignments in an LRU cache; the threshold pref
  is `library_title_similarity_threshold` plus `CategoryLibrarySettings.titleSimilarityThreshold`.
  Read `docs/features/library/en.md` before touching any of it.
- **E-Hentai favorites sync** (Miko): upstream slots 0..9 are mapped to local categories **by
  name** (`ExhPreferences.exhFavoritesSyncConfig()`, `EhFavoritesSyncPlan`); the sync refuses to
  run without a mapping and skips slots that are empty upstream. Read
  `docs/features/favorites-sync/en.md` before touching `exh/favorites/`.
- **Suggestion systems** (Miko): the related-titles row is built by `RecommendationEngine`
  running the providers picked in `RecommendationPreferences.settings()` (`OSUSUME` = the untouched
  KMK pipeline, `UWASA`, `ZOKUHEN` = decreasing-prefix search ranked by Dice similarity,
  `TAGU_OSEKKAI`, `TRACKER`; romaji keys with explanatory KDoc in
  `RecommendationProviderId`). `MangaScreenModel.fetchRelatedMangasFromSource` is the only call
  site. Read `docs/features/recommendations/en.md` before touching `eu.kanade.domain.recommendation`,
  `RelatedManga*` or the Settings → Advanced group.
- `source-api`: `eu.kanade.tachiyomi.source.*` — avoid breaking extension ABI.
- **Downloads** (Miko): charging / off-peak restrictions, a process-wide speed limit and a
  storage quota with oldest-first purge sit on top of Mihon's downloader
  (`DownloadRestrictions.kt`, `DownloadThrottle.kt`, `DownloadQuotaEnforcer.kt` +
  `PurgeCandidate.kt`). Read `docs/features/downloads/en.md` before touching
  `Downloader`/`DownloadJob`/`DownloadCache`.
- **Trust all extensions** (ported from Taison): `ExtensionManager.trustAll()` loops the pending
  `untrustedExtensionMapFlow` and delegates each one to `trust(extension)`; the "Trust all" button
  lives in the Extensions tab's "Installed" header (`ExtensionsScreenModel.trustAllUntrusted()` /
  `State.untrustedCount` / `State.isTrustAllInProgress`). Read `docs/features/sources/en.md`.

---

## Build & CI

Build types: `debug` (`.dev`), `release`, `releaseTest` (`.rt`), `foss` (`.foss`), `preview` (`.beta`, CI default), `benchmark`.

Gradle `-P` flags (`buildSrc/.../BuildConfig.kt`):

| Flag | Effect |
|------|--------|
| `include-telemetry` | Firebase Analytics + Crashlytics |
| `enable-updater` | In-app update checker |
| `disable-code-shrink` | Skip R8 minification |
| `include-dependency-info` | Dependency metadata in APK |

```bash
./gradlew spotlessApply              # format (run before spotlessCheck)
./gradlew spotlessCheck              # REQUIRED before considering work done (CI gate)
./gradlew assemblePreview            # main CI/dev APK
./gradlew assemblePreview -Pinclude-telemetry -Penable-updater  # full upstream CI build
./gradlew testReleaseUnitTest        # CI unit tests (or ./gradlew test for all modules)
./gradlew installDebug               # device install
./gradlew :data:generateSqlDelightInterface  # after .sq / .sqm changes
```

**Agent verification checklist (minimum):** `spotlessApply` → `spotlessCheck` → `assembleDebug` (or `compileDebugKotlin` only if the user asked for a quick compile check—but still run Spotless).

JVM target **17** (CI runs the build on JDK 21 — see `.github/.java-version`).

Native upscaling: NDK **28.2.13676358**, CMake **3.22.1**, NCNN vendored in `third_party/ncnn-20260526-android-vulkan/` (override with `-PncnnSdkDir=…`, `ncnn.sdk.dir` in `local.properties`, or `NCNN_SDK_DIR`).

Release signing happens in CI via `r0adkll/sign-android-release` with the `SIGNING_KEY` / `ALIAS` / `KEY_STORE_PASSWORD` / `KEY_PASSWORD` secrets — there is no keystore in the repo and no Gradle `signingConfig` for release. Configure secrets in repository settings; see `docs/project/release/en.md`.

---

## Fork-origin markers

Preserve inline blocks when editing — **never delete a marker that is not yours**:

```kotlin
// MIKO -->  … // MIKO <--   Miko (use this for ALL new code)
// KMK -->  … // KMK <--   Komikku
// SY -->   … // SY <--    TachiyomiSY
// EXH -->  … // EXH <--   E-Hentai / exh
```

XML: `<!-- MIKO --> … <!-- MIKO <-- -->`. YAML / shell: `# MIKO -->`. Gradle `.kts` uses the Kotlin form (`// MIKO -->`). A one-word brand replacement inside a user-facing string or a User-Agent literal does **not** need a marker — the comment would be more noise than the change.

Package roots: `eu.kanade.tachiyomi.*` (legacy UI), `tachiyomi.*` (domain/data), `mihon.*` (Mihon upstream), `exh.*` (enhanced sources).

---

## Tests

- Unit tests: `domain/src/test/`; app: `app/src/test/.../MigratorTest.kt`. No broad UI test suite.

---

## Conventions

- **Logging** – Prefer `xLogE()` / `xLog()` helpers from `exh.log` for KMK-lineage code, Mihon uses `logcat { }` from `tachiyomi.core.common.util.system`. Avoid raw `android.util.Log`.
- **Formatting** – Spotless + ktlint (`buildSrc/.../mihon.code.lint.gradle.kts`). No wildcard imports, imports alphabetically ordered, trailing commas as in the surrounding file. Agents **must** run `spotlessApply` and `spotlessCheck` (see [Mandatory rules](#mandatory-rules-for-ai-agents)).
- **Fork edits** – New Miko features inside `// MIKO` islands; keep `// KMK` / `// SY` / `// EXH` blocks intact when merging upstream.

---

## Key files

- `App.kt` – Injekt bootstrap, logging setup
- `MainActivity.kt` – Voyager host
- `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` – core DI
- `app/src/main/java/eu/kanade/domain/DomainModule.kt` – domain interactors
- `buildSrc/.../BuildConfig.kt`, `AndroidConfig.kt` – flags, SDK versions
- `app/build.gradle.kts`, `settings.gradle.kts`
- `core/common/.../Constants.kt` – Miko identity constants (`MIKO_*`, `URL_MIKO_*`)
- `docs/README.md` / `docs/README.es.md` – feature documentation in English and Spanish
- `docs/project/release/en.md` – build, signing, integrations and distribution
- `docs/miko/brand/` – icon source art (`miko-logo-source.jpg`), the torii geometry
  (`torii_path.txt`) and the build-type mark SVGs. Regenerate icons from here, never by hand.
- `docs/features/downloads/en.md` – download restrictions, speed limit and storage quota
- `docs/features/navigation/en.md` – navigation bar presets (`NavPreset`, `resolveNavTabs`)
- `docs/features/sources/en.md` – `SourceKind`, `SourceFeature`, `SourceEnhancement` and the
  dedicated-Kotatsu-parser seam
- `docs/features/library/en.md` – local tags, local rating and per-page bookmarks (`manga_tags`,
  `manga_ratings`, `page_bookmarks`, migration `49.sqm`) plus how they travel in backups
- `docs/features/library/en.md` – library filter ("My tags", include/exclude) and grouping
  (`LibraryGroupType.LOCAL_TAGS`) by local tags
- `docs/features/moments/en.md` – chapter bookmark kinds
  (`ChapterBookmarkType`, `chapter_bookmark_types`, migration `50.sqm`, backup field 1003; the
  glyph `ChapterBookmarkTypeIcon` and the picker `ChapterBookmarkTypeDialog`)
- `docs/features/moments/en.md` – page previews, notes, captures, chapter bookmarks and optional mascot
- `docs/features/moments/en.md` – per-page bookmarks in the reader: `scroll_fraction` (restore) vs
  `focus_fraction` (thumbnail/preview centring, migration `53.sqm`); read before touching
  `WebtoonViewer.longTapListener`, `ReaderViewModel.togglePageBookmark` or `page_bookmarks.sq`
- `docs/features/library/en.md` – per-category independent library settings ("Special" tab,
  `category_library_settings`, migration `51.sqm`, `BackupCategory` field 1000)
- `docs/features/favorites-sync/en.md` – E-Hentai favorites sync slot ↔ category mapping and the
  empty-slot rule
- `docs/features/recommendations/en.md` – suggestion systems (Osusume / Uwasa / Zokuhen / Tagu Osekkai /
  AniList-MAL), single vs prioritized multi-system mode, `RecommendationEngine` + cache
- `NOTICE`, `LICENSE`, `LICENSE-APACHE` – GPL-3.0 + inherited Apache-2.0 attribution

---

## Build environment (Linux / WSL)

The Android SDK (platform 36, build-tools 35.0.1, platform-tools, cmdline-tools, NDK 28.2.13676358, CMake 3.22.1) lives in `/opt/android-sdk`; `local.properties` carries `sdk.dir`. JDK 21 compiles fine to JVM target 17.

Export the environment before invoking `./gradlew` in a fresh shell:

```bash
export ANDROID_HOME=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/default
```

| Task | Command |
|------|---------|
| **Required format fix** | `./gradlew spotlessApply` (run first after code edits) |
| **Required format gate** | `./gradlew spotlessCheck` (must pass before task is done) |
| Debug APK build | `./gradlew assembleDebug` |
| Preview APK build (CI) | `./gradlew assemblePreview` |
| Unit tests (CI) | `./gradlew testReleaseUnitTest` |
| All module tests | `./gradlew test` |
| SQLDelight codegen | `./gradlew :data:generateSqlDelightInterface` |

### Gotchas

- First Gradle build downloads ~1 GB of dependencies; subsequent builds use the Gradle cache and are much faster.
- A build run over the WSL `/mnt/<drive>/…` bridge is roughly 20 minutes cold. A checkout inside the WSL filesystem is much faster.
- `local.properties` is `.gitignore`d — recreate it if missing.
- Without an emulator or device, `installDebug` fails; verify with `assembleDebug`.
- `google-services.json` and `client_secrets.json` are not present (CI secrets); builds without `-Pinclude-telemetry` succeed without them.
- Gradle daemon may use significant memory (`-Xmx4g` in `gradle.properties`). If OOM occurs, kill and restart the daemon with `./gradlew --stop`.
- `app/.cxx/` is native build cache and is **untracked** (`.gitignore`). Do not re-add it.
