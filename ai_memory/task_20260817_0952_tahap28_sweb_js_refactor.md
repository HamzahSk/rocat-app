# Tahap 28 — Refactor Mesin JavaScript WebView Berdasarkan Referensi sweb-master

- Tanggal: 2026-08-17
- Sub-tahap: 28.1 analisis `sweb-master`, 28.2 refactor WebViewUtil + BrowserScreen + i18n, 28.3 verifikasi emulator (isolasi penyebab blank).
- Status: SELESAI — `:app:assembleDebug` SUCCESS + unit test hijau (NO-SOURCE app / hijau core); refactor konfigurasi JS engine meniru `sweb-master` selesai & terverifikasi di emulator nyata. Temuan verifikasi: halaman CapCut saat ini (redesign **dark theme**, `arco-theme="dark"`) tidak me-*paint* penuh di engine Chrome-113 WebView dalam SEMUA konfigurasi — ini masalah page/engine, bukan settings aplikasi.

## Ringkasan

Meniru konfigurasi WebView yang terbukti menjalankan JS di aplikasi browser referensi
**`sweb-master`** (package `landau.sweb`) untuk memperkuat mesin JS In-App Browser
rocat-app: `allowUniversalAccessFromFileURLs`, `layoutAlgorithm=SINGLE_COLUMN`,
cache via `LOAD_DEFAULT` (menggantikan `setAppCacheEnabled` yang API-nya dihapus),
`onReceivedSslError` → dialog Proceed/Cancel, dan fallback `intent://` →
`browser_fallback_url`. Verifikasi di emulator menemukan bahwa halaman CapCut yang
SEDANG BERLANGSUNG diuji (redesign dark) punya **error hydration React sisi-page
(#418/#423/#425) + penolakan CSP inline-script** yang muncul di setiap konfigurasi —
termasuk browser sistem — pada engine Chrome-113; bukan blank sistem (judul ter-hydrate
`Daftar - CapCut`, tanpa crash AndroidRuntime/chromium), tapi painting tema halaman
dibatasi oleh versi engine. Kesimpulan jujur didokumentasikan di KDoc `WebViewUtil`.

## 28.1 — Analisis Mesin JS sweb-master

Sumber: `sweb-master/app/src/main/java/landau/sweb/MainActivity.java`.
Konfigurasi `createWebView()` referensi:
- `WebSettings`: `setLayoutAlgorithm(SINGLE_COLUMN)`, `setAllowUniversalAccessFromFileURLs(true)`,
  `setJavaScriptEnabled(true)`, `setCacheMode(LOAD_DEFAULT)`, `setAppCacheEnabled(true)`,
  `setDomStorageEnabled(true)`, `setBuiltInZoomControls(true)`, `setDisplayZoomControls(false)`,
  `setLoadWithOverviewMode(true)`, UA desktop/mobile toggle + `setUseWideViewPort`.
- `WebChromeClient`: `onProgressChanged` (injectCSS), `onShowCustomView`/`onHideCustomView`,
  `onShowFileChooser`.
- `WebViewClient`: `onPageStarted`/`onPageFinished` (injectCSS), `onReceivedHttpAuthRequest`,
  `shouldInterceptRequest` (AdBlocker), `shouldOverrideUrlLoading` (fallback `intent://`),
  `onReceivedSslError` (dialog Proceed/Cancel via `SslErrorHandler`).

## 28.2 — Refactor Mesin JS (meniru sweb-master)

### `core/common/.../WebViewUtil.kt` — `setDefaultSettings`
- **DITAMBAH** `allowUniversalAccessFromFileURLs = true` (mirror sweb `createWebView()`: frame
  file:// boleh akses resource cross-origin).
- **DITAMBAH** `layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN` (mirror sweb;
  deprecated di Chromium modern namun dipertahankan demi fidelitas referensi; catatan KDoc
  menjelaskan hasil verifikasi jujur).
- **`setAppCacheEnabled(true)` TIDAK bisa disalin**: API dihapus dari `WebSettings` pada
  compileSdk 33+ (build error "Unresolved reference 'appCacheEnabled'") → `cacheMode =
  LOAD_DEFAULT` (yang sudah ada) mewarisi niat sweb; dicatat di komentar + KDoc.
- **`setBuiltInZoomControls`/`setDisplayZoomControls`/`useWideViewPort`/`loadWithOverviewMode`**
  sudah sesuai sejak Tahap 25 — tidak berubah.
- KDoc diperbarui: konfigurasi kini diklaim bersumber dari sweb-master (Tahap 28) + kompatibel
  DOCS_WEBVIEW.md (Tahap 26) + mihon; klaim SINGLE_COLUMN ditulis jujur (lihat Ringkasan).

### `app/.../ui/browser/BrowserScreen.kt`
- `WebViewClient.onReceivedSslError` (baru, mirror sweb): dialog `AlertDialog` dengan
  `SslErrorHandler.proceed()` / `cancel()`, judul = `StringKey.insecureConnectionTitle`, pesan =
  `String.format(insecureConnectionMessage, error.url, errorDescription)` dengan deskripsi error
  (`SSL_NOTYETVALID/SSL_EXPIRED/SSL_IDMISMATCH/SSL_UNTRUSTED/SSL_DATE_INVALID`), tombol
  `proceed`/`cancel`. Default WebView = CANCEL → halaman blank putih; dialog memberi kontrol user.
  Konstanta benar: **`SSL_IDMISMATCH`** (`SSL_HOSTMISMATCH` TIDAK ada di SDK 35).
- `WebViewClient.shouldOverrideUrlLoading` (baru, mirror sweb): `intent://` → parse
  `;S.browser_fallback_url=` → `Uri.decode` + `view.loadUrl(fallback)` → in-app browser tak
  dead-end pada scheme yang tak bisa dimuat.
- Factory AndroidView: `WebViewUtil.setDefaultSettings(view, NetworkHelper.DEFAULT_USER_AGENT)`
  — browser memakai identitas jaringan bersama app (Chrome/141, sama dengan OkHttp/skrip).
- Import baru: `android.app.AlertDialog`, `android.webkit.SslErrorHandler`,
  `app.rocat.core.common.network.NetworkHelper`.

### i18n (`StringKey.kt`, `Strings.kt`)
- Baru: `insecureConnectionTitle` ("Insecure connection"/"Koneksi tidak aman"),
  `insecureConnectionMessage` ("The site's security certificate could not be verified.\nURL:
  %1\$s\n\nError: %2\$s\n\nDo you want to continue anyway?" / versi ID), `proceed`
  ("Proceed"/"Lanjutkan").
- `StringKey.cancel` **sudah ada** (tidak ditambah; pemakaian langsung `StringKey.cancel`).
- **Escape Kotlin**: placeholder `%1$s`/`%2$s` di string `Strings.kt` harus ditulis `%1\$s`
  (tanpa escape → "Unresolved reference 's'").

## 28.3 — Verifikasi Emulator & Isolasi Penyebab Blank

Lingkungan: emulator API 34 google_apis x86_64 (headless, swiftshader), WebView terpasang =
**Chrome 113.0.5672.136**, mode malam emulator = nonaktif. Skenario sama seperti Tahap 27:
`am start ... --es app.rocat.EXTRA_URL "https://www.capcut.com/id-id/signup"`, `sleep 20-25`,
`adb logcat -d -s WebViewJS:* AndroidRuntime:E chromium:E` + `screencap`.

### Eksperimen kontrol (matriks konfigurasi di-emulator)
| Konfigurasi | Screenshot (non-putih) | Catatan |
|---|---|---|
| sweb penuh (SINGLE_COLUMN + allowUniversalAccess + wide-viewport + Chrome/141) | awalnya **92%** (dark), lalu tidak terulang → **3.5%** | render dark = varian page (A/B/transien), bukan hasil settings stabil |
| tanpa SINGLE_COLUMN (+ Chrome/141) | 3.5% | — |
| bare WebView (hanya `javaScriptEnabled`) | 33.4% | konten SSR parsial |
| settings penuh + UA default (Chrome/113) | 3.5% | — |
| SINGLE_COLUMN + UA inferred (Chrome/113) | 3.5% | — |
| browser sistem (AOSP, kontrol) | 12.4% | juga TIDAK paint penuh tema dark |

### Temuan kunci (probe DOM via `evaluateJavascript` sementara, lalu dihapus)
- Judul ter-hydrate: `page title: Sign up - CapCut` → `Daftar - CapCut` (SPA jalan).
- DOM probe: `innerHTML` 29.6 KB, `scrollHeight: 0`, body bg transparan → putih, `arco-theme="dark"`,
  `document.styleSheets` 27→59 (CSS eksternal ter-load), viewport 412px. Tema dark + layout halaman
  hanya muncul bila React mount; **React gagal hydrate** di Chrome-113 (error #418/#423/#425 + CSP
  inline-script ditolak) di SETIAP konfigurasi termasuk bare.
- Browser sistem (AOSP) juga tak bisa paint penuh (87.6% putih) → masalah page/engine-version,
  bukan settings WebView aplikasi. `getInferredUserAgent` (Chrome/113) TIDAK memperbaiki — varian
  server per-UA tidak menentukan paint.
- Tanpa AndroidRuntime FATAL, tanpa crash proses chromium (onRenderProcessGone tak terpicu).

### Keputusan
- Konfigurasi final = mirror sweb-master (SINGLE_COLUMN + allowUniversalAccessFromFileURLs +
  LOAD_DEFAULT + JS/DOM/database + wide-viewport + multiwindow + zoom) + identitas UA bersama
  (Chrome/141). Eksperimen UA (getInferredUserAgent) di-revert (tidak membantu, dan memecah
  konsistensi identitas OkHttp/WebView).
- KDoc mencatat jujur: CapCut saat ini (dark redesign) tak paint penuh di Chrome-113 WebView pada
  konfigurasi apa pun (termasuk browser sistem) — bukan blank sistem (judul ter-hydrate, tanpa
  crash), residual blank = page/engine-version, bukan settings.
- Debug probe DOM `evaluateJavascript` dihapus dari `onPageFinished`.

## Build & Test
- `./gradlew :app:assembleDebug` BUILD SUCCESSFUL (warning lama `Icons.Filled.OpenInNew` deprecated).
- `:app:testDebugUnitTest` NO-SOURCE, `:core:common:testDebugUnitTest` hijau.
- Bukti final disimpan ke `ai_memory/evidence/capcut_signup_tahap28.png` +
  `ai_memory/evidence/webview_js_tahap28.log` (log: judul ter-hydrate, error React #418/#423/#425
  page-side, tanpa FATAL AndroidRuntime/chromium).
- Workflow CI `emulator-webview-test.yml` (Tahap 27) tetap valid & digunakan untuk verifikasi ini.

## File yang Diubah
- `core/common/src/main/java/app/rocat/core/common/util/WebViewUtil.kt` — +allowUniversalAccessFromFileURLs, +SINGLE_COLUMN, KDoc sweb-master.
- `app/src/main/java/app/rocat/ui/browser/BrowserScreen.kt` — +onReceivedSslError dialog, +shouldOverrideUrlLoading intent://, factory +UA bersama, import baru.
- `app/src/main/java/app/rocat/i18n/StringKey.kt` / `Strings.kt` — +insecureConnectionTitle/Message, proceed (EN/ID).
- `ai_memory/00_INDEX.md` — index diperbarui (file ini).