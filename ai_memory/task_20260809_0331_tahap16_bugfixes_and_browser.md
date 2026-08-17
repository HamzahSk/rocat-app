# Tahap 16 — Fix Storage & Clear Data + Implementasi In-App Browser Bebas

**Tanggal:** 2026-08-09
**Status:** SELESAI
**Build:** `./gradlew :app:assembleDebug` SUCCESS, `./gradlew test` SUCCESS (rhino test baru: `RoCatUI.save`), `./gradlew :app:assemblePreview` (R8 minify) SUCCESS.

## Ringkasan
Memperbaiki dua bug kritis: (1) hasil scrape tidak pernah tersimpan ke storage, dan
(2) "Clear Cache & Cookies" tidak membersihkan sampai akar (inklusi WebView). Lalu
merombak navigasi: tab **Playground dihapus total** dan digantikan tab **Browser**
peramban web penuh yang bisa membuka URL bebas.

### 16.1 — Perbaikan Bug Storage (Save Scraped Files)
- `StorageManager.saveFileToScrapeFolder(folder, fileName, mimeType, content)` (fungsi baru):
  `DocumentFile.findFile(fileName) ?: createFile(mimeType, fileName)` lalu stream bytes
  via `context.contentResolver.openOutputStream(file.uri)` → file **benar-benar tersimpan**
  ke storage. Ada overload `String` (UTF-8), `sanitizeFileName()` (strip `/\:*?"<>|`, max
  120 char), mode `"wt"` dengan fallback default.
- Akar masalah: `createScrapeFolder()` hanya membuat sub-folder, tidak pernah ada kode
  yang menulis file ke dalamnya.
- Modul scripting kini memakai pipeline tersebut:
  - `ScriptUiBridge` (scripting:api) + method `saveFile(fileName, content, mimeType)` → String (URI hasil).
  - `RhinoScriptEngine` `RoCatUiBridge` + global `RoCatUI.save(fileName, content, mimeType?)` (default `text/plain`).
  - `ScriptCanvasViewModel` implementasi `saveFile` → `storageManager.saveFileToScrapeFolder` dalam `runBlocking` (aman: bridge dipanggil di thread IO Rhino).
  - Unit test rhino baru: `rocatui forwards saveFile calls with a default mime type`.

### 16.2 — Perbaikan Bug Clear Data (Cache & Cookies)
- **Cookies** (`SettingsViewModel.deleteCookies()`): selain `cookieDao.deleteAll()`,
  kini juga `android.webkit.CookieManager.getInstance().removeAllCookies(null)` + `flush()`.
  Karena `AndroidCookieJar` (OkHttp ↔ WebView) berbagi `CookieManager`, membersihkan Room
  saja tidak menutup sesi login/Cloudflare yang sesungguhnya.
- **Cache** (`SettingsViewModel.clearCache()`): selain `StorageManager.clearCache()`
  (Coil memory/disk + `cacheDir`), kini juga `WebView(context).clearCache(true)` (main thread)
  agar cache halaman WebView ikut hilang sampai ke akar.
- `SettingsViewModel` kini menerima injeksi `Context` (`Injekt.get()` dari `AppModule`).

### 16.3 — Refaktor Navigasi (Hapus Playground → Tab Browser)
- `RoCatNav.kt`: `Screen.Playground`, `KEY_PLAYGROUND`, rute, item bottom bar, import
  `PlaygroundScreen` dan ikon `Icons.Filled.PlayArrow` DIHAPUS TOTAL. Ditambahkan
  `Screen.Browser` + `KEY_BROWSER`; item nav ketiga kini `Browser` dengan ikon
  `Icons.Filled.Public` dan label `StringKey.browser`.
- `di/AppModule.kt` & `di/AppViewModelFactory.kt`: registrasi `PlaygroundViewModel` dihapus.
- Direktori `app/rocat/ui/playground/` DIHAPUS (PlaygroundScreen, PlaygroundViewModel,
  ResultFormatter). Komponen bersama dipindah ke package baru **`app/rocat/ui/components/`**
  (`ScriptUIComponent.kt` dan `GridView.kt` berisi `GridComponent` + `parseGrid`); import
  `ScriptCanvasScreen`/`ScriptCanvasViewModel` diperbarui.
- i18n: `StringKey.playground("nav_playground")` DIHAPUS → `browser("nav_browser")`; semua
  kunci Playground-only (selectScript, noEnabledScripts, scriptDrivenUi, scriptDrivenUiBody,
  buildUi, console, copyJson, copyText, json, jsonCopied, textCopied, mediaOutputVideo)
  dibuang dari `StringKey` + `EnglishStrings`/`IndonesianStrings`. Kunci yang masih
  dipakai Canvas (output, running, videoPreview, playVideo, noVideoPlayer dst) dipindah ke
  blok Canvas; blok Browser baru: addressBar, urlPrompt, go, refresh, stop, forward.
  Teks `noScriptsBody` diperbarui (tidak menyebut tab "Playground"); KDoc lama yang
  menyebut "playground" dirapikan.

### 16.4 — `BrowserScreen` (Freestyle Web Browser)
- File baru `app/rocat/ui/browser/BrowserScreen.kt` (Jetpack Compose).
- **Address Bar Bebas**: `OutlinedTextField` menerima URL APA SAJA (misal `https://google.com`)
  atau kata kunci; `normalizeUrl()` ala Chrome: inject `https://`, prefix `www.`, selain itu
  jadi pencarian Google. Trigger: IME Action `Go`, ikon Go, dan tombol Enter hardware.
- **Kontrol Navigasi**: Back (mundur), Forward (`goForward()`), Refresh (`reload()`), dan
  Stop (`stopLoading()` saat loading) yang langsung mengendalikan riwayat WebView; progress
  bar via `WebChromeClient.onProgressChanged` (`LinearProgressIndicator`).
- **Engine WebView**: dirender via `AndroidView(factory=...)` dengan `WebViewUtil.setDefaultSettings()`
  (JS, DOM storage, UA dipinjam dari `NetworkHelper`); `WebViewClient` memperbarui `canGoBack`/
  `canGoForward` + alamat di address bar (`onPageFinished`). `BackHandler(enabled=canGoBack)`
  → tombol Back sistem mundur dalam riwayat halaman; `DisposableEffect` memanggil `destroy()`
  saat tab hilang (anti-leak).
- **Sinkronisasi Cookie (nilai plus)**: karena app memakai `AndroidCookieJar` yang berbasis
  `WebView CookieManager`, sesi login yang dipakai user di browser bebas ini (akun, atau
  `cf_clearance` Cloudflare) otomatis berbagi ke OkHttp yang digunakan mesin scraper — jadi
  `fetch()` dalam skrip langsung menikmati cookie login dari browser.
- Tab Browser tampil sebagai salah satu item `NavigationBar` (Scripts / Browser / Settings).

## Catatan Teknis Penting

### Teknis
- **`RoCatUI.save()`** dikembalikan ke skrip sebagai string `content Uri` (kosong bila
  gagal); pemanggilan di bridge memakai `runBlocking` karena berada di thread IO Rhino.
- **Pitfall Compose**: jangan pakai `WebView(...).apply { ... }` di dalam `AndroidView(factory)`
  dengan nama variabel local (`webViewClient`, `webView`, dst.) — resolusi nama di blok
  `apply` mengacu ke receiver (`WebView`) sehingga muncul error `'val' cannot be reassigned`.
  Gunakan `also { view -> view.xxx = ... }` agar keterangan eksplisit.
- **Interface `ScriptUiBridge` berubah**: semua implementasinya (termasuk `RecordingUiBridge`
  di unit test rhino) wajib menambah `saveFile(...)`; kalau tidak, compile `test` gagal.
- Mode full-screen: bottom bar hanya untuk Scripts/Browser/Settings; Canvas/Detail/Import
  tetap tanpa `NavigationBar` (warisan dari Tahap 13).
- `assembleDebug` + `test` + `assemblePreview` (R8) semuanya SUCCESS; tidak ada aturan
  ProGuard baru yang diperlukan untuk WebView browser baru.