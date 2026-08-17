# Tahap 30 — Perbaikan Interaksi Event DOM (SPA) pada Headless WebView

**Status:** Selesai

**Tanggal:** 2026-08-17

## Analisis Kendala DOM & Anti-Bot

Klik via JS (`el.click()` / `dispatchEvent`) gagal pada SPA modern & anti-bot karena
event sintetis bersifat **untrusted** (`isTrusted === false`) — React/Vue (event
delegation di akar dokumen) dan detektor bot (CapCut, hCaptcha, Cloudflare) menolak
event yang bukan berasal dari input layar asli, sehingga halaman tidak merespons dan
screenshot tidak berubah.

## Ringkasan Perubahan

**Native Touch (Kotlin):**
- `HeadlessWebViewManager.click(selector)` di-upgrade: tidak lagi hanya JS. Kini
  (1) `measureIfNeeded` — layout WebView headless ke viewport default (1366×768, sama
  dengan screenshot), (2) `elementBounds` — cari `getBoundingClientRect` + `scrollIntoView`
  ke tengah, (3) `dispatchNativeTap` — kirim `MotionEvent.ACTION_DOWN` → gap 60ms →
  `ACTION_UP` via `WebView.dispatchTouchEvent` (source `SOURCE_TOUCHSCREEN`) di pusat
  elemen → halaman melihat event `isTrusted=true` (touch/pointer/mouse/click). (4)
  fallback `clickViaJs` (urutan pointer/mouse lama) bila elemen tak ditemukan / tap
  ditolak. Semua anti-crash (`runCatching` di bridge, `catch Throwable` di tiap helper).
- `RoCatBrowserWrapper.kt` (JS polyfill): `Locator.prototype.click` kini memprioritaskan
  `RoCatPage.click(selector)` (bridge native) bila tersedia — fallback JS sintetis hanya
  bila bridge tak ada → `page.click(sel)` & `page.locator(sel).click()` memakai tap native.
- `ScriptBrowserBridge.click` KDoc diperbarui (kontrak: native touch → fallback JS).

**Skrip (JS):**
- `capcut_test.js` v4 ditulis ulang: tidak lagi rantai `page.evaluate` klik rumit —
  temukan tombol `Lanjutkan dengan alamat email`, tandai `data-rocat-click="1"`, lalu
  langsung `page.click('[data-rocat-click="1"]')` (tap native). Verifikasi email/password
  input + screenshot sebelum/sesudah tetap ada.

**Docs:** `DOCS_SCRIPTING.md` Bab 7 — section baru **§7.2a Klik Native Touch (Tahap 30)**
(kenapa `el.click()` gagal + alur MotionEvent), baris tabel `page.click` diperbarui.

**Pengujian:**
- `CapCutNativeClickTest.kt` baru (2 test rhino): onLaunch tak menyentuh browser;
  clickContinueEmail menjalankan alur penuh sinkron (goto → eval marker → native bridge
  `click:[data-rocat-click="1"]` → screenshot → RoCatDOM verifikasi email/password).
- `TestBrowserlessScraperTest` assertion diperbarui: `page.click` kini bridge call,
  bukan evaluate-based click.
- `:scripting:rhino:testDebugUnitTest`: **59 test, 54 hijau** (5 gagal = pre-existing,
  `scrape_anichin.js`/`fixed_testscrape.js` tidak ada di repo). `:app:assembleDebug`
  **SUCCESS**. Fetch/RoCatDOM tak tersentuh (runStatic tetap hijau).

## Tugas Selanjutnya
- Verifikasi emulator/device nyata: tap native vs JS click pada CapCut (screenshot +
  log `WebViewJS`) bila perlu.
- Pertimbangkan dukungan `ACTION_MOVE` / multi-touch untuk gestur kompleks.
