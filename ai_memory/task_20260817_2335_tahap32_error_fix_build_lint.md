# Tahap 32 — Perbaikan Error, Rekonstruksi Skrip Test, dan Resolusi Lint

- **Tanggal**: 2026-08-17 23:35 WIB
- **Status**: ✅ SELESAI (test, lint, dan seluruh build matrix SUCCESS)
- **Cakupan**: 4 sub-tugas (perbaikan error kode, rekonstruksi 2 skrip hilang, resolusi lint Media3 opt-in, update memory).

## 32.1 — Rekonstruksi Skrip Hilang dari Workspace

**Akar masalah**: 5 unit test rhino gagal (`AnichinScraperTest` 2 test + `FixedTestscrapeScraperTest` 3 test) karena `scrape_anichin.js` dan `fixed_testscrape.js` tidak ada di root repo (pre-existing, tercatat di Tahap 30–31 sebagai "file hilang dari workspace").

**Solusi**: kedua skrip direkonstruksi dari ekspektasi test + catatan Tahap 19/23 + contoh template, lalu ditulis ulang di root repo.

### `scrape_anichin.js` (Anichin scraper, Tahap 19)
- `onLaunch()` → home grid `.bixbox article .bsx` → `RoCatUI.addGrid(3, itemsJson, "openDetail")`.
- `doSearch(q)` → `/page/1?s=` → grid sama.
- `openDetail(url)` → cover `.thumb img` → `RoCatUI.addImage(url, title, true)` + sinopsis + episode `.eplister ul li a` grid → `openEpisode`.
- `openEpisode(url)` → decode base64 `select.mirror option` (pilih opsi non-empty), ekstrak iframe `anichin.stream`, bangun master `/hls/<id>.m3u8`, `pickBestVariant` pilih varian valid tertinggi (buang `#EXT-X-STREAM-INF` tanpa URI) → `RoCatUI.addVideo(url, title, true, true)`.
- `decodeBase64(input)` global: prefer bridge native `RoCatUI.decodeBase64(s)`; fallback decoder murni JS `b64Decode` (pad otomatis + coba decode gagal-safe) saat bridge tidak tersedia.

### `fixed_testscrape.js` (XVideos scraper, Tahap 23)
- `onLaunch()` → `RoCat.render([...])` (input pencarian + tombol Cari).
- `doSearch(q)` → URL search, `RoCatUI.addGrid(...)`.
- `openDetail(url)` → og:title/og:image, badge genre (`RoCatUI.addBadgeGroup`), tombol Play → `openVideo`.
- `openVideo(url)` → ekstraksi `html5player` via `innerHtml` (Jsoup `text()` = `""` untuk konten `<script>` CDATA), `pickBestVariant` HLS → `RoCatUI.addVideo(url, title, true, true)` + `RoCatUI.addJsonLog` debug.
- Headless fallback bila `html5player` tidak ada: `RoCatPage.open(url)` → `page.evaluate(...)` (mengandung `setVideoUrlLow`) → `page.close()`. jsonLog judul "Mode Interaktif"; alert berisi "WebView".

**Validasi sintaks**: kedua skrip lolos `node --check` (Node v22.23.2). Tidak boleh async/await/class/spread/optional-chaining (Rhino 1.7.15 interpretasi).

## 32.2 — Fix Deprecation Icon OpenInNew

`app/src/main/java/app/rocat/ui/browser/BrowserScreen.kt`:
- Import `androidx.compose.material.icons.filled.OpenInNew` → `androidx.compose.material.icons.automirrored.filled.OpenInNew`.
- Pemakaian `Icons.Filled.OpenInNew` → `Icons.AutoMirrored.Filled.OpenInNew`.
- Menghilangkan seluruh peringatan deprecation di kompilasi Debug/Release/Preview Kotlin.

## 32.3 — Resolusi Lint: Media3 `@UnstableApi` Opt-In

**Masalah**: `./gradlew lint` gagal dengan 16 error `UnsafeOptInUsageError` dari `androidx.annotation.experimental` (68 warning pre-existing) pada `AudioPreviewCard.kt` dan `RocatVideoPlayer.kt` — pemakaian API Media3 yang bertanda `@UnstableApi` tanpa opt-in.

**Percobaan yang gagal**:
1. `@OptIn(UnstableApi::class)` per-fungsi → lint tetap mengeluhkan *propagasi* opt-in (caller ikut diminta opt-in), error bergeser ke deklarasi fungsi.
2. `@file:OptIn(UnstableApi::class)` / `@file:OptIn(markerClass = UnstableApi::class)` (Kotlin) → lint `UnsafeOptInUsageError` tidak mengenali bentuk file-level Kotlin.
3. `@file:androidx.annotation.OptIn(markerClass = UnstableApi::class)` → error kompilasi "Assigning single elements to varargs in named form is prohibited".

**Solusi yang benar** (di kedua file):
```kotlin
@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package app.rocat.ui.components
```
- Lint mengharuskan bentuk **`@androidx.annotation.OptIn`** (bukan Kotlin `@OptIn`) dengan argumen **array literal** `[UnstableApi::class]` (varargs).
- Tambahan import `androidx.media3.common.util.UnstableApi` di kedua file.

**Hasil**: lint SUCCESS (0 error, 68 warning pre-existing tidak disentuh karena di luar cakupan perbaikan error).

## 32.4 — Update Memory (file ini)

- `ai_memory/00_INDEX.md`: status proyek → Tahap 32; baris riwayat log Tahap 32; catatan teknis baru (lint Media3 opt-in + rekonstruksi skrip).

## Verifikasi

### Lint
- `./gradlew lint` SUCCESS (0 error, 68 warning pre-existing). Laporan: `app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`.

### Test
- `./gradlew test` SUCCESS — 5 test yang sebelumnya gagal (AnichinScraperTest 2 + FixedTestscrapeScraperTest 3) kini hijau setelah skrip direkonstruksi.

### Build
- `./gradlew :app:assembleDebug` SUCCESS.
- `./gradlew :app:assembleRelease` SUCCESS (R8).
- `./gradlew :app:assemblePreview` SUCCESS (R8).
- `./gradlew :app:compileReleaseKotlin :app:compilePreviewKotlin` SUCCESS tanpa warning.

### Spotless
- Tidak ada plugin/task Spotless di repo (`./gradlew tasks --all` → "NO SPOTLESS TASK"). Kualitas kode dijamin via `lint` + kompilasi bebas warning.

## File yang Diubah / Ditambah

### Baru (direkonstruksi)
- `scrape_anichin.js` (root repo) — Anichin scraper (Tahap 19) untuk `AnichinScraperTest`.
- `fixed_testscrape.js` (root repo) — XVideos scraper (Tahap 23) untuk `FixedTestscrapeScraperTest`.
- `ai_memory/task_20260817_2335_tahap32_error_fix_build_lint.md` — file ini.

### Diubah
- `app/src/main/java/app/rocat/ui/components/AudioPreviewCard.kt` — `@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])` + import `UnstableApi`.
- `app/src/main/java/app/rocat/ui/components/RocatVideoPlayer.kt` — sama.
- `app/src/main/java/app/rocat/ui/browser/BrowserScreen.kt` — `Icons.AutoMirrored.Filled.OpenInNew`.
- `ai_memory/00_INDEX.md` — status + riwayat + catatan teknis Tahap 32.

## Catatan Tambahan
- Skrip yang direkonstruksi harus tetap ES5 Rhino-safe (sync, tanpa async/await/class/spread/optional-chaining); validasi `node --check` hanya memastikan sintaks valid, bukan kompatibilitas Rhino.
- Lint error Media3: gunakan `@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])` — bentuk Kotlin `@OptIn` dan `@OptIn(markerClass=...)` non-array TIDAK diterima lint `UnsafeOptInUsageError`.
- Backward-compat: kedua skrip masih memakai bridge lama (`RoCatUI.addGrid`/`addImage`/`addVideo`/`addBadgeGroup`/`addJsonLog`/`RoCat.render`) yang tetap didukung.