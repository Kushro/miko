Looking to report a bug or request a feature? Use the [issue forms](https://github.com/Kushro/miko/issues/new/choose).

---

Thanks for your interest in contributing to Miko!

# Code contributions

Pull requests are welcome. Open them against **`main`** — that is the default and only
long-lived branch; there is no `develop`.

Feature documentation is available in [English](./docs/README.md) and
[Spanish](./docs/README.es.md). Keep both language versions aligned when documenting
behavior, examples and limitations. Use feature names rather than development-cycle labels.

If you want to take on an [open issue](https://github.com/Kushro/miko/issues), comment on
it so nobody duplicates the work. You do not need permission or an assignment.

## Prerequisites

The ability to use the following is assumed; existing contributors will not teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)

### Tools

- [Android Studio](https://developer.android.com/studio) (or IntelliJ IDEA with the Android plugin)
- An emulator or a physical device with developer options enabled

## Building

| Tool | Version |
|---|---|
| JDK | 17 (bytecode target; CI runs on JDK 21) |
| Android SDK | compileSdk / targetSdk 36, minSdk 26, build-tools 35.0.1 |
| Android NDK | 28.2.13676358 |
| CMake | 3.22.1 |
| NCNN | vendored in `third_party/ncnn-20260526-android-vulkan/` |

**Build on Linux or WSL.** The native upscaling module (NDK + CMake + NCNN) is fragile on
Windows because of path-length limits. A working WSL invocation:

```bash
# In your Linux/WSL shell, configure JAVA_HOME and ANDROID_HOME for your installation.
cd /path/to/miko
bash ./gradlew assembleDebug
```

Note that a build over the `/mnt/…` bridge is slow (tens of minutes on a cold cache); a
checkout inside the WSL filesystem is much faster.

### Required checks before opening a PR

Run these in order. "It compiles" is not enough — `spotlessCheck` is a CI gate.

```bash
./gradlew spotlessApply    # fix formatting
./gradlew spotlessCheck    # must pass
./gradlew assembleDebug    # or :app:compileDebugKotlin for a faster compile-only check
./gradlew testReleaseUnitTest
```

After changing SQLDelight `.sq` / `.sqm` files: `./gradlew :data:generateSqlDelightInterface`.

## Code namespaces and origin markers

The Kotlin namespaces `eu.kanade.tachiyomi`, `tachiyomi.*` and `mihon.*` are
historical and intentionally unchanged. They are part of the existing code structure;
changing them would touch large parts of the project and complicate integrating changes
from the projects Miko builds on.

Code from different projects uses inline markers to make its origin and Miko-specific
additions easier to identify. **Never delete a marker that is not yours.**

```kotlin
// MIKO -->  … // MIKO <--   Miko   (new: use this for all Miko-specific code)
// KMK -->  … // KMK <--   Komikku
// SY  -->  … // SY  <--   TachiyomiSY
// EXH -->  … // EXH <--   E-Hentai / exh
```

In XML use `<!-- MIKO --> … <!-- MIKO <-- -->`, in YAML/shell `# MIKO -->`. One-word brand
replacements inside user-facing strings do not need a marker — a comment there is just noise.

## Strings and translations

Strings live in moko-resources modules. Put a new string in the module that owns the feature:

| String kind | Module | Resource class |
|---|---|---|
| Miko-only | `i18n-miko/` | `MKMR` |
| Komikku-inherited | `i18n-kmk/` | `KMR` |
| TachiyomiSY-inherited | `i18n-sy/` | `SYMR` |
| Mihon / upstream-shared | `i18n/` | `MR` |

Hard rules:

- New Miko strings go in **`i18n-miko/src/commonMain/moko-resources/base/`** only. Do not
  add them to `i18n/`, `i18n-kmk/` or `i18n-sy/` — that would collide on every upstream merge.
- Only edit the `base/` locale by hand. Non-`base` locale files are translation output.
- There is **no Weblate project for Miko yet**, so for now translations are ordinary PRs
  against the locale files. Keep such PRs separate from code changes.

## Crash reporting and analytics

Miko ships with **no telemetry**. The Firebase Analytics/Crashlytics plugins are commented
out in `app/build.gradle.kts`, no `google-services.json` is in the repo, and the certificate
gate in `telemetry/…/TelemetryConfig.kt` is empty. Do not turn any of this on in a PR — it is a
project-level decision, not a code change.

(For the record: there is no ACRA in this codebase and no `standard` source set. Older
CONTRIBUTING text inherited from upstream said otherwise and was wrong.)

## Supporting Cloud Sync — Google Drive

Google Drive sync needs your own OAuth client; the repo does not ship one.

1. Go to the [Google Cloud Console](https://console.cloud.google.com) and create a project.
2. APIs & Services → Library → enable **Google Drive API**.
3. APIs & Services → OAuth consent screen → fill in app name, support email, contact info.
4. Add the `.../auth/drive.appdata` and `.../auth/drive.file` scopes.
5. Publish the consent screen (no test users needed).
6. APIs & Services → Credentials → Create credentials → OAuth client ID → **Android**.
7. Set `eu.kanade.google.oauth` as the package name.
8. Get the SHA-1 with `keytool -printcert -jarfile <your>.apk` and paste it in.
9. Under advanced settings, enable **Custom URL scheme**.
10. Download the JSON, rename it to `client_secrets.json`, and drop it in
    `app/src/main/assets/`.

## Forks of Miko

Forks are fine as long as they follow [the project's LICENSE](./LICENSE) (GPL-3.0-only) and
preserve the Apache-2.0 attributions in [`NOTICE`](./NOTICE).

When forking, remember to:

- Change the app name (`app_name` in `i18n/…/base/strings.xml`) and the app icon
- Change the `applicationId` in [`app/build.gradle.kts`](./app/build.gradle.kts) so your build
  does not collide with Miko installs
- Point or disable the update checker
  ([`AppUpdateChecker.kt`](./app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt)
  and `Constants.MIKO_GITHUB_REPO`)
