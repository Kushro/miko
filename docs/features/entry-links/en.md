# Entry links

**English** · [Español](./es.md) · [Documentation](../../README.md)

Share a manga from its overflow menu to include both a Miko entry link and the
public website URL. The latter remains useful for recipients without Miko.
Reader and WebView sharing still use ordinary website URLs.

## Link format

```text
miko://entry?s=<sourceId>&t=<title>&u=<base64url(SManga.url)>
```

Required fields are source ID `s` (decimal Long), title `t` (URL-encoded UTF-8) and
source-relative identifier `u` (URL-safe Base64 without padding). Optional fields:
`n` source name, `l` language, `v` version ID, `cu` chapter identifier (same Base64
encoding), `a` author (up to 80 characters), `g` first five comma-separated genres
and `st` status. Unknown fields are ignored. A public URL is not a substitute for
the raw `SManga.url`/`SChapter.url` identifiers.

## Opening a link

Miko resolves the source ID, seeds the manga from the link and merges fetched
details without losing its identifiers. A chapter identifier opens that chapter
after lookup or refresh. Missing sources offer extension browsing or title search.
Malformed required fields produce an invalid-link screen, not an unrelated search.

Other URLs use `ResolvableSource`, then an installed-source host fallback, then
search when nothing matches. Host matching ignores case and a leading `www.`.
The manifest registers the `miko://entry` scheme; it does not register verified
HTTP App Links or a web landing page.

## Technical reference and limits

`MikoEntryLink` provides pure `toQueryParams`/`parseQuery` functions with JVM tests;
URI wrappers use Android APIs. `DeepLinkScreenModel` resolves entries and chapters.
Custom schemes depend on the receiving app recognizing them and Miko being installed.
Links are unsigned and unversioned; parsing must remain backward-compatible.
Authors are truncated and comma-containing genres lose exact boundaries. Alternate
or configurable source hosts may not match the HTTP fallback.
