# Tahap 31 — Stabilisasi Komponen UI, Perbaikan Media & Peningkatan Template

Tanggal: 2026-08-17
Status: **SELESAI**
Build: `sh gradlew :app:assembleDebug` **SUCCESS**; rhino **59 test, 54 hijau** (5 gagal = pre-existing data script `scrape_anichin.js`/`fixed_testscrape.js` tidak ada di repo — sama seperti Tahap 29/30); `:app:testDebugUnitTest` NO-SOURCE.

## Ringkasan

### (31.1) Isolasi State Loading Tombol (bug #1)
**Akar masalah:** `ScriptCanvasViewModel.State.executing` adalah flag *global*; `ScriptCanvasScreen.ButtonComponent` dirender dengan `enabled = !state.executing` dan menampilkan spinner saat `!enabled` → menekan satu tombol memicu animasi loading di **SEMUA** tombol.
**Perbaikan:**
- `State` + field baru **`activeButton: String?`** (functionName tombol yang sedang jalan).
- `execute()` menerima param `activeButtonName`; `onScriptButton` meneruskan `functionName`, `onGridItemClick` meneruskan `null`; `activeButton` di-set saat mulai dan di-null saat selesai.
- `onScriptButton` di-guard `if (state.value.executing) return` (mencegah re-entry, mempertahankan semantik satu eksekusi).
- `ButtonComponent(label, loading, onClick)`: hanya tombol dengan `loading == true` yang disabled + menampilkan `CircularProgressIndicator`; tombol saudara tetap aktif & normal.
- Backward-compatible: skrip lama dengan `RoCatUI.addButton` tetap berjalan normal.

### (31.2) Full Screen & Immersive Media (bug #2a)
- Helper bersama baru `app/rocat/ui/components/ImmersiveMedia.kt`: `Context.findActivity()`, `Window.hideSystemBars()`/`Window.showSystemBars()` (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
- **`ImagePreviewCard`**: kini punya **full-screen viewer imersif** (`FullScreenImageDialog`): `Dialog` edge-to-edge (`usePlatformDefaultWidth=false`, `decorFitsSystemWindows=false`), latar hitam, `AsyncImage` `ContentScale.Fit`, system bars disembunyikan di window dialog **dan** fallback window activity, scrim gradien tipis di atas agar tombol Close terbaca (tidak "flat"). Klik preview membuka viewer. Tombol **copy URL** (ikon + Toast) selalu tersedia di pojok kanan bawah.
- **`RocatVideoPlayer`/`FullScreenVideoDialog`**: bars disembunyikan di dialog + activity (fallback bila `DialogWindowProvider` tak tersedia — sebelumnya jika `dialogWindow == null` bars TIDAK disembunyikan), scrim gradien atas di belakang tombol exit. Helper `findActivity` dipindah ke `ImmersiveMedia.kt`.

### (31.3) Debug & Hardening Download Media (bug #2b)
- **`MediaDownloader.kt`**: `fetchBytes` ditulis ulang dengan `try/catch` eksplisit + `android.util.Log` (tag `MediaDownloader`) mencatat penyebab nyata: URL invalid (`Request.Builder().url`), HTTP non-2xx, body null, exception network. `download()` mencatat folder null / fetch gagal / gagal simpan; hasil gagal → null (UI tampilkan Toast).
- **`StorageManager.saveFileToScrapeFolder`**: hardening — tolak content kosong; **tambahan ekstensi dari MIME bila nama file tak punya ekstensi** (`extensionForMime`, fix nama `image` tanpa `.jpg` dari URL query-string), log tiap kegagalan (folder null, `createFile` null, `openOutputStream` null, write exception, `flush()`), stream ditutup via `use`.
- **`MediaDownloaderState.start`**: dibungkus `try/catch` (`CancellationException` di-rethrow, sisanya di-log tag `MediaDownloaderState`) → anti-crash; status `Failed` + Toast tetap.
- Validasi URI + izin SAF: `takePersistableUriPermission` (READ|WRITE) sudah benar; `openOutputStream(uri,"wt")` + fallback mode default dipertahankan.

### (31.4) Peningkatan Template UI — Tombol Copy (fitur #3)
- Helper bersama `app/rocat/ui/components/CopyAction.kt`: `CopyIconButton(text,label,message)` (ikon `ContentCopy`, Toast) & `CopyTextButton(text,label,message)` (TextButton, Toast). Pakai `LocalClipboardManager`.
- **Tombol copy ditambahkan ke:** `LogComponent` (teks log), `ConsoleOutput` (hasil/error eksekusi), `AlertBannerCard` (message), `BadgeGroupCard` (badges join `", "`), `HtmlPreviewCard` (teks polos), `ImagePreviewCard` (URL), `VideoPreviewCard` (URL), `AudioPreviewCard` (URL). `JsonLogCard` sudah punya Copy JSON (dipertahankan).
- **i18n** `StringKey` + `EnglishStrings`/`IndonesianStrings` baru: `copy`, `copied`, `copyText`, `textCopied`, `copyUrl`, `urlCopied`.

## Kompatibilitas & Anti-Crash
- Skrip lama memakai `RoCatUI.addInput/addButton/addImage/addVideo/addGrid/log` tetap berfungsi 100% normal.
- Semua error saat download / simpan ditangkap di Kotlin (try-catch) dan di-log via `android.util.Log`; UI memberi Toast — tidak ada force-close.
- Build `:app:assembleDebug` SUCCESS; rhino 54/59 hijau (5 gagal pre-existing data script hilang).