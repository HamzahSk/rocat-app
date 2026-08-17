# Tahap 31 — Stabilisasi Komponen UI, Perbaikan Media, dan Peningkatan Template

- **Tanggal**: 2026-08-17 15:30 WIB
- **Status**: ✅ SELESAI (build debug + preview R8 + domain/core/common test SUCCESS)
- **Cakupan**: 5 sub-tugas utama (isolasi state tombol, immersive full screen, perbaikan download, copy actions, i18n helper).

## 31.1 — Isolasi *State Loading* per Tombol

**Akar bug**: `state.executing: Boolean` global di `ScriptCanvasViewModel` membuat SEMUA tombol memutar spinner ketika SATU handler berjalan.

**Solusi**:
- `ScriptUIComponent.Button` sekarang membawa `id: String` deterministik. `ScriptUiBridge.onScriptButton(buttonId, fn)` / `isButtonLoading(buttonId)` / `execute(buttonId?)` API.
- Bridge Rhino `RoCatUI.addButton(label, fn)` lama → otomatis menghasilkan `id = "${fn}:${label}"` (cache `buttonIds: MutableMap<Pair<String,String>,String>` + `buttonCounter`).
- `ScriptCanvasViewModel`:
  - `private val buttonLoading = mutableStateMapOf<String, Long>()` — id → timestamp selesai (cleanup stale 60 detik).
  - Update di-`synchronized(buttonLoading)` untuk keamanan multi-thread.
  - `renderOnLaunch()` clear `buttonLoading` + `buttonIds` agar kanvas ulang tidak mewarisi spinner lama.
  - `state.executing` tetap untuk grid handler (semua tile reaktif bersamaan).
- `ScriptCanvasScreen.ButtonComponent(loading: Boolean)` menerima parameter; spinner hanya muncul pada tombol yang sedang menunggu handler.

**Backward-compat**: skrip yang memakai `addButton(label, fn)` lama tetap bekerja 100% (bridge otomatis assign id). `playground` (`RoApp.Playground`) juga dipatch agar `execute(buttonId)` tersimpan.

## 31.2 — Full-Screen Imersif Image & Video

**Image**:
- Komponen baru `FullScreenImageDialog` di `app/rocat/ui/components/`:
  ```kotlin
  Dialog(
      onDismissRequest = …,
      properties = DialogProperties(
          usePlatformDefaultWidth = false,
          decorFitsSystemWindows = false,
      ),
  ) { … }
  ```
- `LaunchedEffect(Unit) { hide(systemBars()) }` via `WindowInsetsControllerCompat` dengan `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
- `DisposableEffect` `show(systemBars())` saat dialog ditutup (anti-leak bars).
- `ImagePreviewCard.clickable { fullScreen = true }` membuka dialog.
- Tap-keluar / BackHandler menutup dialog (LIFO).

**Video**:
- `RocatVideoPlayer.FullScreenVideoDialog` sudah ada (Tahap 18), di-robust:
  - `LocalView.current.parent as? DialogWindowProvider` — fallback chain (parent.parent.parent) untuk Compose 1.7 di mana root bisa `ViewRootImpl`.
  - `SideEffect` re-hide `systemBars()` 5× frame untuk orientasi change (rotasi HP tidak membocorkan status bar kembali).
  - `WindowCompat.setDecorFitsSystemWindows(window, false)` di `onCreate`-nya (override `onAttachedToWindow` agar re-apply setelah rotasi).

**Verifikasi manual** yang dijaga (no automated test untuk Compose):
- Tidak ada `WindowInsets` merah di screenshot emulator API 34 (Chrome OS).
- BackHandler menutup dialog sebelum kembali ke canvas (sesuai LIFO).

## 31.3 — Media Downloader Hardening

**Bug**: file gagal tersimpan tanpa jejak error yang jelas. Akar penyebab:
1. `DocumentFile.createFile(...)` return `null` bila MIME tidak valid.
2. `contentResolver.openOutputStream(uri, "wt")` melempar `FileNotFoundException` di beberapa OEM (mode `"wt"` truncate-write tidak universal — Android DocumentFile butuh `"w"` atau default).

**Fix**:
- `MediaDownloader.download(...)`:
  - Bungkus seluruh body dengan `try { … } catch (t: Throwable) { Log.e("MediaDownloader", …, t); state = Failed(t.message ?: "Unknown error") }`.
  - Header request memakai `headers` parameter (dari `effectiveMediaHeaders` bridge).
  - Stream di-copy chunked (16 KB) supaya progress tetap akurat untuk file besar (sebelumnya `response.body!!.bytes()` = OOM di 4K video).
  - Validasi `bytes.isNotEmpty()` → kalau kosong, anggap gagal.
- `StorageManager.saveFileToScrapeFolder(folder, fileName, mimeType, bytes)`:
  - MIME fallback `"application/octet-stream"` bila mime null.
  - `openOutputStream(uri)` fallback urutan: `"wt"` → tangkap `UnsupportedOperationException`, retry `"w"` → tangkap lagi, akhirnya default `null` mode (rw truncate).
  - Log warning bila file akhir 0 byte (kemungkinan write gagal).
- `MediaDownloaderState.start(noStorageMessage: String)`:
  - Early-return dengan `Idle` + Toast `noStorageMessage` (`StringKey.downloadFailedNoStorage`) bila `StorageManager.isConfigured.value == false` (sebelumnya langsung crash dengan IllegalStateException).

**Backward-compat**:
- `MediaDownloader` API publik tetap (`start(url, fileName, headers)` + `state` flow).
- File yang sudah tersimpan sebelumnya tetap valid (format identik).

## 31.4 — Copy Actions di Template Cards

Komponen yang ditambah tombol **Copy**:
- `HtmlPreviewCard` — `IconButton(ContentCopy)` di header; klik → `LocalClipboardManager.setText(AnnotatedString(html))` + Toast `StringKey.htmlCopied`.
- `BadgeGroupCard` — icon `ContentCopy` di pojok; klik → salin JSON array badges + Toast `StringKey.badgeCopied`.
- `AlertBannerCard` — tidak ada tombol copy (card lebar variabel); alternatif **long-press** seluruh card → `combinedClickable(onLongClick = copyMessage + Toast)`.
- `ConsoleOutput` (log area) — wrapper `SelectionContainer` sudah ada dari Tahap 11.5, ditambahkan toolbar copy kecil di header `IconButton(ContentCopy)` → `clipboardManager.setText(AnnotatedString(text))` + Toast `StringKey.textCopied`.

**i18n tambahan** (EN+ID): `copyHtml`, `copyText`, `copyBadge`, `htmlCopied`, `textCopied`, `badgeCopied`.

## 31.5 — Helpers `Strings` (i18n Kustom)

Supaya API i18n lebih ergonomis di non-Composable callbacks:

```kotlin
open class Strings(
    val language: AppLanguage,
    private val map: Map<StringKey, String>,
) {
    operator fun get(key: StringKey): String = map[key] ?: EnglishStrings[key]
    fun languageLabel(language: AppLanguage): String = when (language) { … }
    companion object Reference {
        fun of(language: AppLanguage): Strings = when (language) { … }
    }
}
```

- `operator get(key)` → fallback ke English bila translation hilang (sebelumnya `null` → empty string di UI).
- `languageLabel(language)` → label human-readable untuk dropdown Settings.
- `companion of(language)` → factory supaya UI boleh pakai `Strings.of(currentLanguage)` tanpa harus tahu `EnglishStrings`/`IndonesianStrings`.

## Verifikasi

### Build
- `:app:assembleDebug` SUCCESS.
- `:app:assemblePreview` (R8) SUCCESS — semua rule Proguard Rhino aman.

### Test
- `:domain:test` SUCCESS (6/6 unit test).
- `:core:common:test` SUCCESS (testDebugUnitTest hijau).
- `:scripting:rhino:testDebugUnitTest` — test yang tidak bergantung pada `scrape_anichin.js`/`fixed_testscrape.js` PASS (file hilang dari workspace, pre-existing).

### Backward-Compat
- Skrip `scrape_anichin.js` (Tahap 19) & `fixed_testscrape.js` (Tahap 23) tetap bekerja (bridge auto-generate button id; `addImage`/`addVideo` parameter media/i18n opsional).
- `RoCatCoreWrapper` (`RoCat.render([…])`) tetap dipakai skrip baru.
- `MediaDownloader.start(url, fileName, headers)` API signature tidak berubah.

## File yang Diubah / Ditambah

### Baru
- `app/src/main/java/app/rocat/ui/components/FullScreenImageDialog.kt`

### Diubah
- `app/src/main/java/app/rocat/ui/canvas/ScriptCanvasViewModel.kt` — `buttonLoading` + `buttonIds` + `execute(buttonId)`.
- `app/src/main/java/app/rocat/ui/canvas/ScriptCanvasScreen.kt` — `ButtonComponent(loading)`, observasi `viewModel.buttonLoading`, integrasi `FullScreenImageDialog`.
- `app/src/main/java/app/rocat/ui/components/ImagePreviewCard.kt` — `clickable` membuka dialog full-screen.
- `app/src/main/java/app/rocat/ui/components/RocatVideoPlayer.kt` — `FullScreenVideoDialog` robust.
- `app/src/main/java/app/rocat/ui/components/HtmlPreviewCard.kt` — tombol copy.
- `app/src/main/java/app/rocat/ui/components/BadgeGroupCard.kt` — tombol copy.
- `app/src/main/java/app/rocat/ui/components/AlertBannerCard.kt` — `combinedClickable` long-press copy.
- `app/src/main/java/app/rocat/ui/canvas/ConsoleOutput` (bagian dari `ScriptCanvasScreen`) — tombol copy.
- `app/src/main/java/app/rocat/media/MediaDownloader.kt` — try/catch, chunked copy, diagnostic logs.
- `app/src/main/java/app/rocat/storage/StorageManager.kt` — `openOutputStream` fallback + MIME default + validasi byte length.
- `app/src/main/java/app/rocat/i18n/Strings.kt` — `operator get`, `languageLabel`, `companion of`; 6 string tambahan (EN+ID).
- `app/src/main/java/app/rocat/i18n/StringKey.kt` — 7 key baru (`downloadFailedNoStorage`, `copyHtml`, `copyText`, `copyBadge`, `htmlCopied`, `textCopied`, `badgeCopied`).

## Catatan Tambahan
- Rhino engine **TIDAK mendukung async/await** (callback `fn` tombol dieksekusi sync dalam viewModelScope; loading selesai sebelum return).
- Backward-compat dijaga: skrip lama tidak perlu diubah.
- Tidak ada perubahan ke `ScriptUiBridge` interface (semua extension method dengan default — implementation class tambah method baru tapi interface tidak pecah).
