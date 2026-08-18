# Tahap 32 - WebView sweb-master integration

Audit `sweb-master/app/src/main/java/landau/sweb/MainActivity.java` against RoCat.

## Perubahan

- `WebViewUtil` now enables `javaScriptCanOpenWindowsAutomatically` in addition to
  JavaScript, DOM storage, database storage, cache, mixed content compatibility,
  and wide viewport settings.
- Fixed a semantic bug in cookie setup: `acceptThirdPartyCookies(webView)` was only
  a getter. RoCat now calls `setAcceptThirdPartyCookies(webView, true)`, matching the
  required cross-domain login behavior used by modern SPAs.
- Browser UI no longer forces Chrome 141 as its mobile User-Agent. It leaves the
  installed WebView UA unchanged, matching sweb-master's `createWebView()` and
  reducing synthetic-UA / anti-bot rejection risk. Desktop mode remains explicit.
- Added a `DownloadListener` to hand downloadable responses to the platform handler,
  based on sweb-master's download hook. Existing Rhino bridges and scraper networking
  are untouched; WebView remains owned by Compose and destroyed in `DisposableEffect`.

## Rationale

The most actionable blank-screen cause found in the comparison was the third-party
cookie getter being used instead of the setter. SPA authentication often bootstraps
through a different origin, so blocked cookies can leave the app shell unhydrated.
Ad-block interception was not imported because blocking a required JS chunk can create
the same blank-screen symptom.
