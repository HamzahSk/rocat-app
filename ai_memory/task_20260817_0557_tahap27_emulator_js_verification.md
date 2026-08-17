# Tahap 27 — Otomatisasi Emulator & Verifikasi Rendering Web Modern (CapCut)

- Tanggal: 2026-08-17
- Sub-tahap: 27.1 penyesuaian script CI/CD, 27.2 monkey → Intent spesifik, 27.3 ekstraksi log & screencap, 27.4 evaluasi & perbaikan browser, 27.5 memory & build.
- Status: SELESAI — `:app:assembleDebug` SUCCESS; **verifikasi emulator nyata di runner**: URL `https://www.capcut.com/id-id/signup` TERENDER (judul `Daftar - CapCut`, screenshot 14% non-putih dengan warna tema CapCut, tanpa error JS/AndroidRuntime/chromium).

## Ringkasan

Mengadaptasi otomasi emulator Android ke proyek ini (package sebenarnya `app.rocat`,
debug = `app.rocat.debug`), mengganti pengujian acak `adb shell monkey` dengan Intent
eksplisit ke In-App Browser + URL CapCut, menarik log **`WebViewJS`** (Tahap 26) beserta
error sistem, mengambil tangkapan layar sebagai bukti visual, lalu memperkuat WebView
agar SPA modern tak pernah blank (recovery renderer crash, autoplay media, cookie, debug).

## 27.1 — Penyesuaian Script CI/CD (GitHub Actions)

- **Baru** `.github/workflows/emulator-webview-test.yml` (reactivecircus/android-emulator-runner v2):
  - Identitas package disesuaikan ke package asli: `app.rocat` (production) / **`app.rocat.debug`**
    (debug — build type debug memakai `applicationIdSuffix = ".debug"`).
  - Path APK disesuaikan ke output build nyata: karena **ABI splits aktif**, output debug =
    `app/build/outputs/apk/debug/app-x86_64-debug.apk` (fallback `app-universal-debug.apk`);
    diverifikasi dengan `aapt dump badging` → `package: name='app.rocat.debug'`,
    `launchable-activity: app.rocat.ui.main.MainActivity`.
  - `./gradlew :app:assembleDebug` sebagai langkah build; emulator API 34 x86_64 Pixel 6,
    headless (`-no-window -gpu swiftshader_indirect`), `disable-animations`.

## 27.2 — Modifikasi Skenario Pengujian (Monkey → Intent Spesifik)

- Perintah `adb shell monkey` acak DIHAPUS (tidak ada di workflow ini; seluruh alur memakai Intent eksplisit).
- `adb shell am start -n app.rocat.debug/app.rocat.ui.main.MainActivity --es app.rocat.EXTRA_URL "https://www.capcut.com/id-id/signup"` + **`sleep 20`** (>15s) agar mesin JS merender SPA penuh.
- **Fitur deep-link baru** agar Intent benar-benar membuka In-App Browser langsung ke URL:
  - `MainActivity`: baca `EXTRA_URL = "app.rocat.EXTRA_URL"` → `RoCatApp(initialUrl)`.
  - `RoCatNav`: `RoCatApp(initialUrl)` **melewati gerbang first-launch storage** saat `initialUrl != null`
    (browser tidak butuh folder storage); `RoCatAppNav(initialUrl)` memulai back stack di `KEY_BROWSER`
    dan meneruskan `initialUrl` ke `BrowserScreen`.
  - `BrowserViewModel`: `navigateTo(url)` (normalisasi + set `loadNonce+1`) + `acceptInitialUrl(url)`
    (guard `initialUrlConsumed` — dipakai sekali per instance, pindah tab tidak mengulang).
  - `BrowserScreen(initialUrl)`: `LaunchedEffect(initialUrl) { viewModel.acceptInitialUrl(it) }`.
  - Alur terverifikasi di emulator: factory WebView memuat home default (google) lalu `acceptInitialUrl`
    memuat CapCut → logcat menunjukkan transisi tersebut.

## 27.3 — Ekstraksi Log & Tangkapan Layar

- `adb logcat -d -v time -s WebViewJS:* AndroidRuntime:E chromium:E WebViewFactory:E > webview_js.log`
  — menarik konsol JS (tag `WebViewJS` Tahap 26) + error sistem saja.
- `adb exec-out screencap -p > capcut_signup.png` → di-upload sebagai artifact `webview-render-evidence`
  (pada job: `if: always()` supaya bukti tetap terambil walau step gagal).

## 27.4 — Evaluasi & Perbaikan Fitur Browser (Berdasarkan Bukti Nyata)

### Hasil Verifikasi Emulator Nyata (dijalankan langsung di runner CI ini, KVM):
- **Logcat `WebViewJS`**: `I/WebViewJS: page title: Daftar - CapCut` → SPA berhasil *hydrate*.
  Warning yang muncul hanya preload `crossorigin` / `link preload not used` (benign), TIDAK ada
  SyntaxError / Cross-Origin / CSP / AndroidRuntime / chromium error.
- **Screenshot** dianalisis programatik (Pillow): 1080x2400, **86.0% putih / 14.0% non-putih**,
  warna teratas = `(243,237,247)` lavender & `(254,247,255)` (tema CapCut) + `(73,69,79)` teks gelap
  → **TERRENDER, BUKAN blank** (halaman blank murni ≈ 99-100% putih).
- Kesimpulan: setelah perbaikan Tahap 25/26 + penyetelan Tahap 27.4, CapCut signup TIDAK gagal dimuat.

### Perbaikan pencegahan (agar web modern "tanpa celah"):
- **`onRenderProcessGone`** (WebViewClient, API 26+): gejala klasik blank putih = renderer process
  crash/tewas. Bila `didCrash()` → log `WebViewJS` ERROR, `destroy()` WebView mati, `webViewEpoch += 1`.
  `BrowserScreen` membungkus AndroidView dengan **`key(webViewEpoch)`** (Compose `androidx.compose.runtime.key`)
  sehingga AndroidView + factory dibuat ulang dan memuat `state.currentUrl` — halaman tidak lagi
  menggantung putih (catatan: **Compose UI 1.7 (BOM 2024.12.01) TIDAK punya parameter `key` di
  `AndroidView`**; kena compile error, diganti `key(webViewEpoch) { AndroidView(...) }`).
- **`onPageFinished`**: bila `view.title` kosong → `Log.w(WebViewJS, "onPageFinished with blank title ...")`
  — penanda SPA tak hydrate sehingga CI bisa deteksi blank dari logcat.
- **`onReceivedTitle`** (WebChromeClient): `Log.i(WebViewJS, "page title: ...")` — bukti positif render.
- **`WebViewUtil.setDefaultSettings`**: +`mediaPlaybackRequiresUserGesture = false` (autoplay media
  inline tanpa tap — situs media/video editor), +`CookieManager.setAcceptCookie(true)` eksplisit
  (di samping `acceptThirdPartyCookies`).
- **`RoApp.onCreate`**: `if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)` —
  chrome://inspect + log WebView lebih kaya di build debug.
- Kebijakan Tahap 26 dipertahankan: `setSupportMultipleWindows(true)` TANPA override `onCreateWindow`,
  `onPermissionRequest` grant, `mixedContentMode` COMPATIBILITY, UA `NetworkHelper.DEFAULT_USER_AGENT`.

## 27.5 — Memory & Build

- `:app:assembleDebug` SUCCESS (152 task; 1 kali gagal transien saat kode belum diperbaiki: parameter
  `key` AndroidView, langsung diperbaiki dengan `androidx.compose.runtime.key`).
- `:app:testDebugUnitTest` & `:core:common:testDebugUnitTest` NO-SOURCE di checkout ini — build hijau.
- Format manual (tak ada spotless/ktlint, konsisten Tahap 25/26).
- Emulator API 34 (google_apis, x86_64) dipasang via sdkmanager di runner & dijalankan dengan
  `-no-window -gpu swiftshader_indirect` (KVM: perlu `sudo chmod 666 /dev/kvm` bila user bukan grup kvm).

## Catatan

- **Package di workflow**: `app.rocat.debug` (debug) bukan `app.rocat` (release) — debug punya suffix.
  Activity FQCN `app.rocat.ui.main.MainActivity` (relative ke namespace, bukan applicationId).
- **Deep link = fitur riil** (bukan hanya untuk test): aktivitas bisa dibuka dari luar langsung ke
  browser + URL tanpa melewati wizard storage; `acceptInitialUrl` consumed-once mencegah re-load
  saat kembali ke tab browser.
- **BUKTI render terbaik dari logcat**: tag `WebViewJS` `page title:` non-kosong = SPA hydrate;
  `onPageFinished with blank title` = curiga blank. Screenshot dianalisis komputasional bila tak bisa
  dilihat mata (fraksi non-putih + palet warna).
- Jangan pernah menambahkan override `onCreateWindow` tanpa alasan keamanan kuat (aturan Tahap 26).