# Project status

**English** · [Español](./es.md) · [Documentation](../../README.md)

Miko implements built-in parsers, source presets/enhancements, independent library
categories, local tags/ratings, download controls, image enhancement, Moments,
navigation presets, recommendations and mapped E-Hentai favorites synchronization.

## Verification

The repository includes JVM tests, SQL checks and Gradle formatting/build checks.
Use [Contributing](../../../CONTRIBUTING.md) for commands. A successful build does
not establish that every website or Android device works.

Device testing remains important for captures, zoom/double-page reading, restoration
of backups with captures, download restrictions and source performance. Each feature
guide records its limitations. Old local APKs are not evidence for a new build.

## Remaining work

- Test network-dependent parsers, WebView requirements and site enhancements.
- Check inherited global settings inside independently configured library categories.
- Test Moments' capture/rendering/compression paths on Android and CBZ preview fallback.
- Evaluate recommendation quality across long titles, languages and external trackers.
- Verify release secrets, integrations and native dependency licensing before distribution.

For publication steps, see [Build and signing](../release/en.md).
