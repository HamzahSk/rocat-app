# Tahap 26 — Resolusi Kritis JavaScript Engine via Dokumentasi Internal (DOCS_WEBVIEW.md)

- Tanggal: 2026-08-17
- Sub-tahap: 26.1 analisis DOCS_WEBVIEW.md, 26.2 implementasi fix JS engine, 26.3 integrasi & pembersihan settings, 26.4 testing & JS error logging, 26.5 memory & build.
- Status: SELESAI — `:app:compileDebugKotlin` + `:app:assembleDebug` SUCCESS (`:app:testDebugUnitTest` NO-SOURCE, `:core:common:testDebugUnitTest` hijau).

## Ringkasan

Menyelesaikan masalah rendering halaman berbasis JavaScript (SPA / web modern yang
sering blank) dengan **membaca & menaati DOCS_WEBVIEW.md** — dokumentasi internal Android
WebView yang selama ini belum dijadikan acuan. Akar masalah ditemukan dari dua instruksi
utama dokumen yang belum dipenuhi: (1) **library Jetpack Webkit `androidx.webkit:webkit`**
belum ada di dependensi proyek, sehingga device dengan System WebView lama tidak bisa
memakai kapabilitas WebView terbaru (halaman heavy-JS tidak ter-render / blank), dan
(2) **WebChromeClient** hanya meng-handle `onProgressChanged` — padahal dokumen menuntut
fullscreen (`onShowCustomView`/`onHideCustomView`), perizinan (`onPermissionRequest`), dan
log konsol JS (`onConsoleMessage`) agar situs modern bisa berjalan penuh & error-nya
terlihat di Logcat.

## 26.1 — Analisis DOCS_WEBVIEW.md

Instruksi kunci yang ditemukan & diverifikasi terhadap kode Tahap 25:

| Instruksi dokumen | Status Tahap 25 | Aksi Tahap 26 |
|---|---|---|
| §"Work with WebView on earlier versions": tambah **Jetpack Webkit** `androidx.webkit:webkit:1.8.0` | ❌ belum ada di `libs.versions.toml`/`app/build.gradle.kts` | ✅ **DITAMBAHKAN** (akar masalah utama) |
| §"Use JavaScript": `setJavaScriptEnabled(true)` | ✅ sudah | — |
| §"Manage windows": `setSupportMultipleWindows(true)` TANPA override `onCreateWindow` (blok popup `target="_blank"` paling aman) | ✅ sudah benar | dipertahankan + KDoc diperjelas |
| §"WebChromeClient": fullscreen / windows / JS dialog / permission | ❌ hanya `onProgressChanged` | ✅ `onShowCustomView`/`onHideCustomView` + `onPermissionRequest` + `onConsoleMessage` |
| §"WebViewClient": event rendering, intercept URL | ✅ default WebViewClient = link dimuat di dalam WebView | — |
| §INTERNET permission | ✅ ada di manifest | — |

## 26.2 — Implementasi Fix JavaScript Engine

- `gradle/libs.versions.toml`: library baru `androidx-webkit = { group = "androidx.webkit", name = "webkit", version = "1.8.0" }` (sesuai versi persis di DOCS_WEBVIEW.md) + komentar Tahap 26.2.
- `app/build.gradle.kts`: `implementation(libs.androidx.webkit)` (app module).
- `app/.../ui/browser/BrowserScreen.kt` (WebChromeClient — komponen browser, sesuai Context Path):
  - `onShowCustomView(view, callback)` + `onHideCustomView()`: fullscreen HTML5 video. View dari page disimpan ke `fullscreenView` (Compose `mutableStateOf<View?>`), callback disimpan ke `customViewCallback`, lalu dirender sebagai **overlay full-screen hitam** di dalam `Box` root (menutupi address bar): `AndroidView(factory = { video })` edge-to-edge + tombol **Close** pojok kanan atas (`IconButton` + `Icons.Filled.Close`, tint putih). Guard anti-tumpuk: bila `fullscreenView` sudah terisi, callback baru langsung `onCustomViewHidden()`.
  - `onPermissionRequest(request)`: `request?.grant(request.resources)` — situs modern yang butuh geolocation / media / protected-media tidak lagi menggantung menunggu jawaban (default WebChromeClient = deny).
  - `onConsoleMessage(message)`: **JS console → Logcat** (Tahap 26.4) — lihat bawah.
  - `closeFullscreen()` local fun + `BackHandler(enabled = fullscreenView != null)` yang dikomposisi SETELAH `BackHandler(canGoBack)` sehingga Back saat fullscreen menutup video dulu (LIFO back dispatcher).
- `core/common/.../util/WebViewUtil.kt`: `setDefaultSettings` tidak diubah nilainya (sudah 100% sesuai dokumen: JS/DOM/database enabled, `MIXED_CONTENT_COMPATIBILITY_MODE`, `setSupportMultipleWindows(true)` tanpa `onCreateWindow`, cache `LOAD_DEFAULT`) — KDoc ditulis ulang agar eksplisit mencatat kepatuhan DOCS_WEBVIEW.md.

## 26.3 — Integrasi & Pembersihan Kode

- Evaluasi seluruh settings Tahap 25 terhadap DOCS_WEBVIEW.md: **tidak ada yang berkonflik** — `setSupportMultipleWindows(true)` justru merupakan perilaku aman yang direkomendasikan dokumen (tanpa override `onCreateWindow` popup diblokir), `domStorageEnabled`/`databaseEnabled`/`mixedContentMode` mendukung SPA agar tak blank, cache `LOAD_DEFAULT` standar. Tidak ada setting yang dihapus.
- Tambahan i18n EN/ID: `StringKey.closeFullscreen` ("Exit fullscreen" / "Keluar layar penuh") untuk tombol close overlay.
- Struktur UI `BrowserScreen`: root diubah `Column` → `Box` (agar overlay fullscreen bisa menutupi address bar juga); isi Column di-`re-indent` konsisten.

## 26.4 — Testing & JS Error Logging

- **`onConsoleMessage`** memetakan level `ConsoleMessage.MessageLevel` ke `android.util.Log` dengan **tag `WebViewJS`** (`adb logcat -s WebViewJS`):
  - `ERROR → Log.e`, `WARNING → Log.w`, `DEBUG → Log.d`, lainnya `Log.i`.
  - Format: `sourceId:lineNumber: message` → SyntaxError, masalah Cross-Origin, CSP, dsb. kini terlihat langsung dari Logcat saat halaman gagal.
- Uji coba memuat ulang URL: build + kompilasi sukses; verifikasi render penuh + isi Logcat JS membutuhkan perangkat/emulator (tidak tersedia di CI) — disarankan device: muat situs SPA, cek `WebViewJS` tag, coba video fullscreen & situs yang minta izin.

## 26.5 — Memory & Build

- Tidak ada plugin spotless/ktlint di proyek (grep build files kosong, sama seperti Tahap 25) → formatter tidak tersedia; kode mengikuti gaya ktlint proyek secara manual (indentasi 4 spasi, baris per-arg).
- Build memakai wrapper Gradle 8.11.1 (`sh gradlew`, system gradle 9.7 tidak kompatibel AGP 8.7.3).
- `:app:compileDebugKotlin` SUCCESS (warning pre-existing saja: `databaseEnabled` deprecated API 34, `Icons.Filled.OpenInNew` deprecated, Float/Double di RhinoScriptEngine).
- `:app:assembleDebug` SUCCESS (152 task; kegagalan `packageDebug` pertama bersifat transien — rerun SUCCESS).
- `:app:testDebugUnitTest` NO-SOURCE, `:core:common:testDebugUnitTest` hijau.

## Catatan

- **Akar masalah JS blank (menurut DOCS_WEBVIEW.md)**: (1) Jetpack Webkit belum di-dependensi → device WebView lama tak dapat kapabilitas terbaru; (2) WebChromeClient minim → fullscreen/permission/console error tak tertangani. Keduanya sekarang terimplementasi.
- **Window/popup**: TETAP `setSupportMultipleWindows(true)` tanpa override `onCreateWindow` — persis rekomendasi keamanan dokumen (popup `target="_blank"` diblokir). Jangan menambahkan `onCreateWindow` override tanpa alasan keamanan yang jelas.
- **Fullscreen**: view HTML5 video harus dire-parenting lewat `AndroidView(factory = { video })`; jangan pernah memanggil `loadUrl`/`reload` dari dalam `shouldOverrideUrlLoading` (dokumen §"Handle page navigation").
- **BackHandler LIFO**: handler fullscreen dikomposisi setelah handler riwayat → saat enabled ia menang duluan.
- **JS logging**: tag `WebViewJS` di `BrowserScreen.kt` (`TAG_JS`) — semua `console.*` halaman web masuk Logcat; berguna untuk diagnosa SyntaxError / Cross-Origin / CSP di lapangan.