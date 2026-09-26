<div align="center">

<img width="200" height="200" src="./.github/readme-images/app-icon.png" alt="Miko app icon"/>

# Miko

**English** · [Español](./README.es.md)

[![License: GPL-3.0-only](https://img.shields.io/badge/License-GPL--3.0--only-0877d2?labelColor=27303D)](./LICENSE)

**A free and open source manga reader for Android.**

*Requires Android 8.0 (API 26) or higher.*

</div>

## What is Miko?

Miko started because I wanted to bring together things I liked from other manga readers
in one app and add a few ideas of my own. I started with Komikku, part of the Mihon
family, and a fairly specific goal: using upscaling to improve images that looked like
they'd been through a few too many rounds of copying before reaching the reader.

Along the way, I also found that even the lighter upscaling options could leave my
phone running hot or keep me waiting too long. With a more powerful PC available, it
didn't make much sense to keep making the phone do all that work. That's where
[Miko Kagura](https://github.com/Kushro/miko-kagura) came from: I moved upscaling to
the PC and set it up as a proxy that receives images, processes them, and sends them
back to the reader. Faster processing, more upscaling options, and less work for the
phone. That combination is now my daily driver for reading.

Then everyday use brought new ideas. I kept adding things I needed or simply wanted
to try, like combining built-in sources like Kotatsu's with installable extensions,
plus specific enhancements applied when the app recognizes certain extensions.

Somewhere between practical needs, experiments, and the occasional “what if I added
this?”, Miko found its own direction. I'll keep developing it with that same approach:
adding things I find interesting and useful. And if they happen to be useful to you
too, all the better.

See the [project status](./docs/project/status/en.md) and
[feature documentation](./docs/README.md) for details on what's available, how to
use it, and its limitations.

## Features

### Inherited (available today)

* Online reading from a large catalogue of installable source extensions
* Local reading of downloaded content, plus a local source for your own files
* Configurable reader: multiple viewers, reading directions, page layouts, autoscroll
* Tracker support: MyAnimeList, AniList, Kitsu, MangaUpdates, Shikimori, Bangumi, Kavita, Komga, Suwayomi
* Categories, library filters, dynamic categories and drag-and-drop sorting
* Light/dark themes plus ~20 colour schemes, and per-entry cover-derived theming
* Scheduled library updates with grouped update notifications
* Local backups (`.tachibk`) and cloud sync (Google Drive / WebDAV)
* Feed tab, saved searches, merged entries, source migration (from TachiyomiSY)
* Discord Rich Presence, incognito mode, app lock

### Miko features

* **On-device image upscaling** — waifu2x and Anime4K running locally through NCNN + Vulkan
  (native code in `app/src/main/cpp/`), applied in the reader or baked into downloads.
* **Remote upscaling** — offload to a companion upscaler service when you would rather not
  spend phone battery on it.
* **Upscaling Hub** — one screen to pick models, watch progress and manage already-enhanced
  chapters.
* **Layered library grouping** — group each category page into collapsible sections by up to
  two criteria (source, status, tracking status, genre, tracker rating, duplicate titles,
  language, author, artist, reading progress, download state, date added, last read, latest
  chapter, category), with per-layer sorting and collapse/expand all.
* **Scanlator priorities** — per entry, rank translation groups so only one version of each
  chapter is shown (the highest-priority group that has it); reading progress of hidden
  duplicates is kept and "continue reading" respects the ranking.
* **Reader page slider markers** — see how far ahead pages are downloaded and upscaled right on
  the chapter slider.
* **Built-in Kotatsu sources** — `kotatsu-parsers-redo` exposed alongside extension sources,
  with a switch for extensions, parsers or both. Source presets filter Browse and global search;
  source kinds and capability badges help distinguish them.
* **Download controls** — charging and off-peak restrictions, a speed limit, storage quota with
  oldest-first cleanup and downloading unread library chapters. These are not Futon's entire
  priority/scheduler system.
* **Library and navigation** — configurable navigation presets, per-category “Special” settings,
  local tags and ratings, and grouping of similar titles by an adjustable threshold.
* **Moments** — bookmarked pages (with notes and optional saved captures) and bookmarked
  chapters with types, plus history/source visibility controls and a small optional mascot.
* **More ways to find manga** — optional recommendation providers (source suggestions, tags,
  similar titles, sequels and trackers), source-specific enhancements and E-Hentai favorites
  mapping.
* **Miko theme** — shrine red by default, with Classic Blue, Amber, Onyx Gold and other
  opt-in themes.
* **Telemetry off by default** — no Firebase configuration is distributed in the repository.

Some features require configured sources, trackers or a companion server. The Kotatsu library
does not make every parser reliable on every site. See the [known limitations](./docs/project/status/en.md)
for features not yet tested on a device.

## Download

Signed builds, when available, are published on GitHub Releases:

**https://github.com/Kushro/miko/releases**

Publishing a signed release requires configuring the repository's signing secrets first.
See the [release and local signing guide](./docs/project/release/en.md).

The release workflow builds universal and per-ABI APKs (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`).
If you are unsure which to install, take the universal one.

Miko installs alongside Komikku/Mihon rather than replacing them: its `applicationId` is
`app.miko`.

## Building

Everything is a standard Gradle Android build, with one extra: the native upscaling module.

**Requirements**

| Tool | Version |
|---|---|
| JDK | 17 (bytecode target; CI runs the build on JDK 21) |
| Android SDK | compileSdk / targetSdk **36**, minSdk 26, build-tools 35.0.1 |
| Android NDK | **28.2.13676358** |
| CMake | **3.22.1** |
| NCNN | vendored in `third_party/ncnn-20260526-android-vulkan/` |

The NCNN SDK path is resolved in this order: `-PncnnSdkDir=…`, then `ncnn.sdk.dir` in
`local.properties`, then the `NCNN_SDK_DIR` environment variable, then the vendored copy in
`third_party/`. The vendored copy means a plain checkout builds without extra setup.

**Commands**

```bash
./gradlew spotlessApply     # format (required before spotlessCheck)
./gradlew spotlessCheck     # CI formatting gate
./gradlew assembleDebug     # debug APK  (applicationId app.miko.dev)
./gradlew assemblePreview   # preview APK (applicationId app.miko.beta) — CI default
./gradlew testReleaseUnitTest
```

Build types and their id suffixes: `debug` → `.dev`, `releaseTest` → `.rt`, `foss` → `.foss`,
`preview` → `.beta`, `benchmark` → `.benchmark`, `release` → none.

Building on **Linux or WSL is strongly recommended** — the Windows path-length limit and the
NDK toolchain do not get along. See [`CONTRIBUTING.md`](./CONTRIBUTING.md) for the exact WSL
invocation.

## Before publishing a fork or release

* If publishing a fork, update the GitHub owner/repository in the README, updater and workflows.
* Keep local IDE/AI settings, `local.properties`, OAuth/Firebase JSON, keystores and passwords
  out of Git. The release workflow needs `SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD` and
  `KEY_PASSWORD` as **repository secrets**, not committed files. An ignore rule does not remove
  a secret already committed or present in Git history.
* Do **not** remove source modules, `gradle/`, `third_party/` or the attribution files to make
  the checkout smaller: they are build inputs or license requirements. The vendored NCNN SDK
  still needs its BSD-3-Clause `LICENSE.txt` alongside the binaries before distributing an APK;
  see [`NOTICE`](./NOTICE).

## Issues, feature requests and contributing

Pull requests are welcome. For anything large, open an issue first so we can agree on the
approach before you write code.

* Bugs: include the app version (**More → About**), Android version, device, and steps to
  reproduce. For crashes, use **More → Settings → Advanced → Dump crash logs**.
* Miko does not host extension APKs or manga content. Extension-specific problems generally
  belong to the extension maintainers; built-in parser issues belong to Miko or its parser library.
* See [`CONTRIBUTING.md`](./CONTRIBUTING.md).
* Agent/AI contributors: read [`AGENTS.md`](./AGENTS.md) first.

## Disclaimer

The developers of this application have no affiliation with the content providers available
through it, and this application hosts zero content.

## License

Miko is distributed under the **GNU General Public License v3.0 only** (GPL-3.0-only) —
see [`LICENSE`](./LICENSE).

Large parts of this codebase were inherited from projects licensed under the Apache License
2.0. Those parts keep their original licence and attribution: the Apache-2.0 text is preserved
in [`LICENSE-APACHE`](./LICENSE-APACHE) and the attributions are listed in [`NOTICE`](./NOTICE).

## Origin and acknowledgements

Different projects provided the foundation or inspired specific Miko features:

| Project | Contribution to Miko |
|---|---|
| **Mihon / Tachiyomi** | The core reader, library, tracking and extension architecture. |
| **TachiyomiSY** | Feed tab, merged entries, source migration, delegated/enhanced sources, advanced library search. |
| **Komikku** | Initial foundation; related titles, hidden categories, cover-based theming, bulk favorite, extension repository management and library update errors. |
| **Kotatsu-Redo / Futon** | Inspiration for source presets and download controls; Miko integrates the `kotatsu-parsers-redo` library through its own adapter, not Kotatsu's app or its Cloudflare auto-solver. |
| **Taison** | Inspiration for category selection, history scoping, trust-all and entry links. |

Credits, with thanks:

* [Komikku](https://github.com/komikku-app/komikku) — cuong-tran and contributors (Apache-2.0)
* [TachiyomiSY](https://github.com/jobobby04/TachiyomiSY) — jobobby04 (Apache-2.0)
* [Mihon](https://github.com/mihonapp/mihon) — Mihon contributors (Apache-2.0)
* [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi) — Copyright 2015 Javier Tomás (Apache-2.0)
* [NCNN](https://github.com/Tencent/ncnn) — Tencent (BSD-3-Clause)
* [kotatsu-parsers-redo](https://github.com/clquwu/kotatsu-parsers-redo) (GPL-3.0), used by Miko's built-in sources
