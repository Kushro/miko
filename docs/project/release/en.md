# Build, release and signing

**English** · [Español](./es.md) · [Documentation](../../README.md)

Build Miko in Linux or WSL with `JAVA_HOME` and `ANDROID_HOME` configured for your
installation. Requirements are in the [README](../../../README.md#building).

## Build an APK

From the checkout root, run these commands in order:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew assembleDebug
```

Use `assembleRelease` for a release APK or `assemblePreview` for the preview variant.
Native upscaling requires the NDK, CMake and vendored NCNN SDK. Keep the wrapper,
source modules and `third_party/` in the checkout.

## Configure GitHub Actions signing

Workflows use `r0adkll/sign-android-release`. Configure repository secrets:

| Secret | Value |
|---|---|
| `SIGNING_KEY` | Base64-encoded release keystore |
| `ALIAS` | Signing key alias |
| `KEY_STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password |

Validate the secrets in repository settings. Source files cannot prove they exist.
Keep the signing key backed up outside the repository to support updates.

## Sign locally

```bash
bash scripts/sign-apk.sh
```

The helper asks for the APK, SDK, keystore and alias. `apksigner` prompts for passwords.
For explicit input/output paths, run from the checkout root:

```bash
mkdir -p dist
bash scripts/sign-apk.sh app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk dist/Miko-signed.apk
```

Optional variables are `ANDROID_HOME` (or `ANDROID_SDK_ROOT`), `KEYSTORE` and
`KEY_ALIAS`. Bash/GNU utilities are required. The helper refuses to overwrite output,
aligns the APK and verifies its signature. It reads no adjacent password files.
GitHub Actions does not depend on this local helper.

## Distribution requirements

- Keep keystores, passwords and OAuth/Firebase configuration out of Git.
- Miko uses `app.miko` with variant suffixes. Identity constants live in
  `core/common/src/main/kotlin/tachiyomi/core/common/Constants.kt`.
- Existing OAuth schemes are compatibility contracts. Configure new integrations
  in provider consoles before enabling them. Firebase remains opt-in.
- Preserve `LICENSE`, `LICENSE-APACHE`, `NOTICE` and dependency notices. The vendored
  NCNN SDK still needs its BSD-3-Clause license text before binary distribution.
- Icon source artwork is in `docs/miko/brand/`; regenerate icons from those sources.
