# Tahap 29 — Browserless/Puppeteer-like API Engine & Pembaruan Dokumentasi

**Status:** Selesai

**Tanggal:** 2026-08-17

## Ringkasan Perubahan

Tahap 29 melengkapi API *browserless* (headless WebView) ala Puppeteer/Playwright yang mengendalikan WebView tersembunyi dari skrip Rhino sinkron (tanpa async/await).

**Bridge & Engine (Kotlin):**
- `ScriptBrowserBridge` (scripting/api) + **dua method baru**: `scrollTo(x, y)` dan `scrollBottom()` (pemicu lazy-load / infinite scroll) dengan default no-op (backward-compatible untuk semua fake/test lama).
- `RoCatPageBridge` (Rhino) + `put("scrollTo")` / `put("scrollBottom")` — primitif native global `RoCatPage`.
- `HeadlessWebViewManager` + implementasi `scrollTo` (via `window.scrollTo`) & `scrollBottom` (via `document.documentElement.scrollHeight`), tetap lewat main-thread Handler + CountDownLatch.
- `RoCatBrowserBridge` (app-side) override dua method di atas dengan `runCatching` (anti-crash).

**JS Polyfill `RoCatBrowserWrapper` (ES5, Rhino-1.7.15-safe):**
- `Page.prototype.type(selector, text, delay)` — ketik huruf demi huruf (via locator).
- `Page.prototype.scrollTo(x, y)` / `Page.prototype.scrollBottom()`.
- **Global `page` baru** — facade Puppeteer-like praktis yang menempel ke singleton `RoCatBrowser.getInstance().page()`: `page.goto / waitForSelector / waitForTimeout / click / type / fill / scrollTo / scrollBottom / evaluate / content / url / title / screenshot / cookies / setCookie / clearCookies / locator / goBack / goForward / reload / stop / close`. Hanya aktif bila browser bridge tersedia (`typeof page === "undefined"` di eksekusi polos).

**Dokumentasi `DOCS_SCRIPTING.md`:** Bab **7. Browserless / Headless WebView API** — kapan memakai (dual-mode statis vs interaktif), model eksekusi sinkron/anti-crash, tabel lengkap method `page`, detail `goto`/`locator`/`evaluate`/`screenshot`, pola `scrollBottom` lazy-load, boilerplate gabungan `page.goto` + `RoCatDOM.parse` + `RoCatUI.addImage`; daftar global + Lampiran diperbarui.

**Pengujian:**
- `test_browserless.js` baru (root repo): demo onLaunch → runDemo (goto/type/click/waitForSelector/scrollTo/scrollBottom/evaluate/content/screenshot/close) + runStatic (fetch+RoCatDOM tetap normal).
- `TestBrowserlessScraperTest.kt` baru (3 test rhino): onLaunch tak menyentuh browser; runDemo menjalankan alur browserless sinkron penuh (assert semua call bridge + parse `Budi` via RoCatDOM + screenshot + close); runStatic tetap kerja tanpa WebView.
- `RoCatBrowserAutomationTest` +3 test: global `page` hanya ada bila bridge hadir; `page` facade drive goto/click/type/content/screenshot; `scrollTo`/`scrollBottom` forward ke bridge native.
- `./gradlew :scripting:rhino:testDebugUnitTest`: **57 test, 52 hijau** (5 gagal = pre-existing, `scrape_anichin.js`/`fixed_testscrape.js` tidak ada di repo). `:app:assembleDebug` **SUCCESS**.

## Tugas Selanjutnya
- Verifikasi di emulator/device (screenshot + log WebViewJS) bila perlu.
- Pertimbangkan dokumentasi `page.evaluate` untuk objek DOM besar (hanya properti yang bisa diserialisasi).
