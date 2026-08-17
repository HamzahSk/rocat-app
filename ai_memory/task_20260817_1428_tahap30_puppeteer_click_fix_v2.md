# Tahap 30 v2 — Debugging Perbaikan Interaksi Native Touch pada Headless WebView

**Status:** Selesai

**Tanggal:** 2026-08-17

## Masalah
Tap native (MotionEvent `ACTION_DOWN`→`ACTION_UP` via `WebView.dispatchTouchEvent`)
di Tahap 30 v1 tetap tidak memberi respons pada WebView headless. Dugaan akar (dipandu
execution plan): koordinat DOM yang tidak dikonversi ke koordinat layar, fokus/window
state view yang tidak aktif, dan sinkronisasi viewport renderer setelah `measure`/`layout`.

## Analisis Akar (3 temuan)

1. **Koordinat CSS-px ≠ view-px (bug utama).** `getBoundingClientRect()`/`window.innerWidth`
   hidup dalam **CSS pixel**, sedangkan `MotionEvent` yang dikirim ke
   `WebView.dispatchTouchEvent` hidup dalam **pixel view (layar WebView)**. WebView
   memetakan layout viewport ke box hasil `measure` (1366×768), jadi konversinya
   `scaleX = viewWidth / window.innerWidth`. V1 mengirim koordinat CSS mentah → tap hanya
   kena jika viewport kebetulan 1:1 — meleset di layar hi-dpi (`device-width` < px),
   halaman tanpa viewport meta (layout default 980px), atau halaman yang di-zoom.

2. **View headless tidak "aktif".** WebView yang tidak pernah di-attach ke window tidak
   punya fokus & window-focus → sebagian jalur input internal (focus steering, touch-mode,
   selection) dapat menolak/menelan event sintetis.

3. **Viewport renderer basi.** Setelah `measure`+`layout` ke 1366×768, renderer butuh
   waktu menyerap ukuran baru; v1 langsung membaca `getBoundingClientRect` dari viewport
   lama → koordinat salah walau scale sudah benar.

## Ringkasan Perubahan

**`HeadlessWebViewManager.kt` (app):**
- `click()` baru: `measureIfNeeded` → `prepareForInteraction` (fokus/window-state) →
  `viewportScale` (poll `window.innerWidth/Height` sampai stabil → rasio CSS→view px,
  clamp 0.1..10) → `elementBounds` (CSS px) → pusat dikali scale → `dispatchNativeTap`.
- `prepareForInteraction(wv)` baru: `isEnabled/focusable/focusableInTouchMode/clickable`
  = true, `setLongClickable(false)`, `onWindowFocusChanged(true)`, `requestFocus(FOCUS_DOWN)`,
  `requestFocusFromTouch()` — semua best-effort + `catch Throwable`.
- `viewportScale(wv)` baru: 3 percobaan, dua baca `window.innerWidth/Height` identik =
  stabil; `null` → fallback JS click.
- `measureIfNeeded`: setelah layout, **kick compositor** (`Bitmap` scratch + `wv.draw`) —
  view detached tanpa window tidak punya choreographer; frame pertama memaksa renderer
  memakai ukuran baru (trik sama dengan `screenshot()`).
- `dispatchNativeTap`: tambah `Thread.sleep(TAP_SETTLE_MS=120)` sebelum DOWN (settle
  layout/scroll `scrollIntoView`; hanya thread skrip yang tidur — UI thread bebas),
  `TAP_GAP_MS` 60→80ms. Parameter `MotionEvent.obtain(downTime, downTime, ACTION_DOWN,
  x, y, 0)` (metaState Int `0`) tetap; `.source = SOURCE_TOUCHSCREEN` dipertahankan.
  Semua latch `CountDownLatch` tetap di main thread singkat, thread skrip yang parkir.

**`capcut_test.js` v9 (repo root):**
- Helper `findAndTagButton(labels)` (hanya CSS selector valid `querySelectorAll` —
  buang pseudo `:has-text()` yang tak bisa di-parse `document.querySelector`),
  `clickButtonByText(labels)` (tag `data-rocat-click="1"` → `page.click('[data-rocat-click="1"]')`),
  `setSelectValue(labels)`.
- Alur `createAccount` (entry UI) memakai native touch untuk: Continue-with-email,
  Continue, Sign Up, Next; email/password via `page.fill`; birthday via `setSelectValue`.
- `jsonLog` "📊 Detail Lengkap" + field baru `berhasil_tap`, `email_inputs`, `password_inputs`.

**`CapCutNativeClickTest.kt`:**
- Test 2 sekarang invoke `createAccount` (bukan `clickContinueEmail` — fungsi v4 yang
  sudah tidak ada di skrip v9); onLaunch assert `createAccount`.
- `FakeBrowser.evaluate` dirombak: branch `data-rocat-click` → `{success:true,...}`,
  `offsetParent` → `{email_inputs:1,hasEmailInput:true}`, `otp` → `{hasOTP:false}`
  (urutan penting), dll.

**Docs:** `DOCS_SCRIPTING.md` §7.2a — langkah native tap diperbarui dengan konversi
koordinat CSS→view px (rasio `viewWidth/innerWidth`), fokus/window-state, settle.

## Pengujian
- `:scripting:rhino:testDebugUnitTest`: **59 test, 54 hijau** (5 gagal = pre-existing,
  `scrape_anichin.js`/`fixed_testscrape.js` tidak ada di repo). `CapCutNativeClickTest`
  2/2 hijau, `TestBrowserlessScraperTest` 3/3 hijau (fetch/RoCatDOM tak tersentuh).
- `:app:assembleDebug` **SUCCESS** (152 task).

## Tugas Selanjutnya
- Verifikasi emulator/device nyata: tap native dengan koordinat terskala pada CapCut
  (screenshot sebelum/sesudah + logcat `WebViewJS`).
- Bila elemen target berada di dalam iframe (dialog login CapCut bisa di-iframe),
  `document.querySelector` top-frame tak menjangkaunya — pertimbangkan traversal iframe.
- Dukungan `ACTION_MOVE`/multi-touch untuk gestur kompleks bila diperlukan.