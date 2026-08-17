# Tahap 25 — Perombakan UI/UX In-App Browser, Mode Desktop & Optimalisasi JavaScript Engine

- Tanggal: 2026-08-17
- Sub-tahap: 25.1 engine WebView, 25.2 mode desktop + fitur ekstra, 25.3 modernisasi UI, 25.4 testing, 25.5 build.
- Status: SELESAI — `:app:compileDebugKotlin` + `:app:assembleDebug` SUCCESS.

## Ringkasan

Merombak total tab Browser dalam aplikasi: konfigurasi inti WebView diperkuat agar situs
modern berbasis JavaScript (SPA React/Vue/Angular) ter-render utuh, mode Desktop baru
(mengganti User-Agent + viewport lebar) dengan toggle yang persisten, pull-to-refresh,
progress bar MD3, dan address bar berbentuk pil dengan indikator SSL, tombol clear,
refresh↔stop, serta overflow menu tiga titik. Seluruh state UI dipindahkan ke
`BrowserViewModel` baru (pola mihon `StateViewModel` + `SettingsRepository` untuk persist).

## 25.1 — Optimalisasi WebView Engine (core/common)

- `core/common/.../util/WebViewUtil.kt`:
  - `setDefaultSettings` kini menambahkan `mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE` (JS/DOM storage/database sudah aktif sebelumnya) — situs dengan sumber campuran / local-storage tidak lagi blank.
  - Konstanta baru `DESKTOP_USER_AGENT` = Chrome 141 Windows x64 standar.
  - Fungsi baru `applyDesktopMode(webView, desktop, mobileUserAgent = DEFAULT_USER_AGENT)`: swap `userAgentString` antara desktop dan mobile bawaan, selalu `useWideViewPort = true` + `loadWithOverviewMode = true` agar halaman re-layout responsif setelah reload.

## 25.2 — Mode Desktop & Fitur Ekstra (app)

- `settings/SettingsRepository.kt`: pref baru `desktopMode` (Boolean, default `false`) — status mode desktop persisten antar sesi.
- **Baru** `ui/browser/BrowserViewModel.kt`:
  - `UiState`: `urlInput`, `currentUrl`, `canGoBack/Forward`, `progress`, `desktopMode`, `isLoading`, `loadNonce` (counter navigasi agar URL sama pun bisa dimuat ulang).
  - `BrowserCommand` (sealed: `Reload`, `SetDesktopMode(enabled)`) dikirim lewat `SharedFlow(extraBufferCapacity=4)` — WebView tetap milik layer UI, ViewModel hanya menyimpan state + perintah.
  - Callback `onPageStarted/onPageFinished/onProgressChanged/refreshNavState` menerima `NavigationState(canGoBack, canGoForward)` dari UI (nilai baca dari WebView asli).
  - `submitUrl()` menormalkan input (logic `normalizeUrl` lama dipindah ke sini), `setDesktopMode` persist + emit command.
- `BrowserScreen.kt`: WebViewClient `onPageStarted/onPageFinished/onReceivedError` push ke ViewModel; WebChromeClient `onProgressChanged` → progress + `isRefreshing=false` saat selesai; `LaunchedEffect(state.loadNonce)` memuat URL (guard `lastAppliedNonce` anti double-load dengan factory AndroidView); `LaunchedEffect(Unit)` koleksi `commands` → reload / `applyDesktopMode` + reload.
- Pull-to-refresh: **`androidx.compose.material3.pulltorefresh.PullToRefreshBox`** + `rememberPullToRefreshState` (material3 1.3.1, `@OptIn(ExperimentalMaterial3Api)`) membungkus AndroidView; `isRefreshing` dipicu onRefresh dan ditutup dari WebViewClient/ChromeClient.

## 25.3 — Modernisasi UI (Material 3)

- Address bar **pil** (`RoundedCornerShape(50)` + `Surface(surfaceContainerHigh)` + `BasicTextField`): ikon **gembok** `Lock` (https, primary) / `LockOpen` (bukan https, error), tombol **clear** (Close, hanya saat teks non-kosong), tombol **Go** (ArrowForward), Enter fisik + IME `Go` sama-sama submit.
- Tombol **refresh ↔ stop** (Refresh saat idle, Close saat loading), back/forward, dan **overflow menu tiga titik** (`MoreVert`) → `ModalBottomSheet` berisi: Desktop mode (`Switch` + `ListItem` klikable), Muat Ulang, Salin Tautan (clipboard + Toast), Buka di Browser Eksternal (`Intent.ACTION_VIEW` + `FLAG_ACTIVITY_NEW_TASK`, runCatching).
- **Progress bar** tipis `LinearProgressIndicator(progress = { state.progress / 100f })` di bawah address bar, terhubung langsung `onProgressChanged`.
- Indikator SSL dari `Uri.parse(currentUrl).scheme == "https"`.
- i18n baru (EN/ID): `moreOptions/desktopMode/copyLink/openInBrowser/linkCopied/clearText/reload/secureSite/insecureSite`.

## 25.4 — Pengujian

- `:app:compileDebugKotlin` SUCCESS, `:app:assembleDebug` SUCCESS (152 task).
- `:app:testDebugUnitTest` NO-SOURCE, `:core:common:testDebugUnitTest` hijau.
- `:scripting:rhino:testDebugUnitTest`: **46 dari 51 hijau** — 5 gagal (2 `AnichinScraperTest` + 3 `FixedTestscrapeScraperTest`) karena **pre-existing**: data `scrape_anichin.js` / `fixed_testscrape.js` TIDAK ada di checkout ini (bukan file yang dilacak git), bukan disebabkan perubahan Tahap 25.
- Pengujian rendering situs heavy-JS & toggle desktop membutuhkan perangkat/emulator (tidak tersedia di CI) — verifikasi manual disarankan di device (Twitter/X web, situs streaming, `onReceivedSslError` default tetap lanjut).

## 25.5 — Build & Format

- Tidak ada plugin spotless/ktlint di proyek (grep build files kosong) → formatter tidak tersedia; kode mengikuti gaya ktlint proyek secara manual.
- `gradlew` wrapper jar tidak ada di repo → build memakai Gradle 8.11.1 (sesuai `gradle-wrapper.properties`) yang diunduh manual ke `/tmp/gradle-8.11.1/`.

## Catatan

- **WebView tetap dimiliki UI layer**; ViewModel mengeluarkan `BrowserCommand` (SharedFlow) — ini menghindari kebocoran WebView di ViewModel dan menjaga `DisposableEffect destroy()` bekerja seperti sebelumnya.
- `stringResource()` TIDAK boleh dipanggil di callback onClick → `linkCopiedMessage` ditangkap di body composable dulu.
- Double-load start page dicegah: factory AndroidView menjalankan `loadUrl` + set `lastAppliedNonce`; `LaunchedEffect(loadNonce)` melewatkan nonce yang sama.
- `@OptIn(ExperimentalMaterial3Api::class)` dibutuhkan untuk `PullToRefreshBox` + `ModalBottomSheet` (material3 1.3.1).
- `onReceivedError(view, request, error)` = overload API 23+ (minSdk 26 aman).
