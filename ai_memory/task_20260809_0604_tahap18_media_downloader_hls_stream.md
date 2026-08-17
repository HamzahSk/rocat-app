# Tahap 18 — Media Template, Downloader (Image & Video), HLS Video Streaming (ExoPlayer/Media3)

**Tanggal:** 2026-08-09 · **Status:** SELESAI · **Build:** `:app:assembleDebug` SUCCESS, `:app:assemblePreview` (R8) SUCCESS, `./gradlew test` SUCCESS (rhino 20 test hijau, termasuk 2 test `addImage`/`addVideo` baru).

## Tujuan
Menambahkan komponen UI media kaya untuk gambar & video (kartu pratinjau + tombol simpan/download ke storage SAF via `StorageManager.saveFileToScrapeFolder`), serta pemutar video native berkinerja tinggi dengan AndroidX Media3 (ExoPlayer) yang mendukung HLS (`.m3u8`) full screen. Skrip TikTok / Reel / Video Downloader kini bisa langsung memutar hasil scrape inline sekaligus mengunduhnya sekali klik.

## Perubahan

### Tahap 18.1 — Template Image Preview & Downloader
- `ScriptUIComponent.Image(url, title="", allowDownload=true)` menggantikan `Thumbnail` (backward-compat: `thumbnailPreview(url)` tetap menambah komponen `Image`).
- `app/rocat/ui/components/ImagePreviewCard.kt`: `ElevatedCard` + Coil `AsyncImage` (fit, max 300dp) + judul opsional + tombol download di pojok kanan atas (ikon download / spinner progress / ceklis).
- `app/rocat/media/MediaDownloader.kt`: unduh via OkHttp `NetworkHelper.client` (membawa UA browser + stealth headers + cookie jar bersama → media autentik ikut terunduh), stream ke `ByteArray` dengan callback progress, lalu `StorageManager.saveFileToScrapeFolder(folder, fileName, mimeType, bytes)` → file benar-benar tertulis di `[Utama]/Scrapes/[scriptId]/`. `inferFileName` (dari URL) + `mimeTypeToExtension` fallback.
- Toast konfirmasi sukses/gagal via `MediaDownloaderState` (`DownloadStatus`: Idle/Downloading(progress)/Done/Failed) + `DownloadActionButton` (sharing di `MediaDownloadComponents.kt`).

### Tahap 18.2 — Template Video Preview & Downloader
- `ScriptUIComponent.Video(url, title="", isStreamHls=false, allowDownload=true)`.
- `app/rocat/ui/components/VideoPreviewCard.kt`: placeholder 16:9 (ikon play) + judul + tombol **Play Inline** (toggle → `RocatVideoPlayer` inline) dan **Download Video** (async `Dispatchers.IO`, progress spinner di tombol, simpan ke folder scrape SAF). `videoMimeFor(url, isStreamHls)` pilih mime (mp4/webm/m3u8).

### Tahap 18.3 — AndroidX Media3 (ExoPlayer) & HLS Streaming
- Dependensi `androidx.media3:media3-exoplayer:1.4.1` + `media3-exoplayer-hls:1.4.1` + `media3-ui:1.4.1` di `gradle/libs.versions.toml` (version ref `media3 = "1.4.1"`) + `app/build.gradle.kts`.
- `app/rocat/ui/components/RocatVideoPlayer.kt`: `AndroidView` membungkus `PlayerView`; `ExoPlayer.Builder(context)` + `DefaultHttpDataSource.Factory` (allowCrossProtocolRedirects, UA browser). Jika `isHls` atau URL `.m3u8`/`hls://` → `HlsMediaSource.Factory`, selainnya `ProgressiveMediaSource.Factory`. `DisposableEffect(exoPlayer)` release saat keluar composition/URL ganti.
- **Full screen**: tombol `Fullscreen` overlay kanan-atas → `Dialog(usePlatformDefaultWidth=false, decorFitsSystemWindows=false)` berisi `PlayerView` kedua memakai instance player yang SAMA (inline di-detach via `update { player = null }`). Orientasi `SENSOR_LANDSCAPE` + sembunyikan system bars (`WindowInsetsControllerCompat`, immersive swipe) saat masuk; `PORTRAIT` + tampilkan lagi saat keluar (Back/tombol `FullscreenExit` → `onDismissRequest`). `DialogWindowProvider` dipakai untuk ambil window dialog. `MainActivity` ditambah `android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize|uiMode"` agar rotasi tidak me-recreate activity (state full screen bertahan).

### Tahap 18.4 — Native Bridge `RoCatUI`
- `ScriptUiBridge` + metode `addImage(url, title="", allowDownload=true)` dan `addVideo(url, title="", isStreamHls=false, allowDownload=true)`.
- Rhino `RoCatUiBridge` mengekspos keduanya + helper `argBoolean` baru (default Boolean); `thumbnailPreview`/`videoPreview` lama tetap ada.
- `ScriptCanvasViewModel` memetakan `addImage` → `ScriptUIComponent.Image`, `addVideo` → `ScriptUIComponent.Video`.
- `ScriptCanvasScreen` merender `Image` → `ImagePreviewCard`, `Video` → `VideoPreviewCard` (string baru: `download`, `downloadVideo`, `playInline`, `closePlayer`, `imageSaved`, `videoSaved`, `downloadFailed`).

### Tahap 18.5 — Verifikasi & Memory
- Build & test SUCCESS (detail di atas).
- `00_INDEX.md` diperbarui + file catatan ini dibuat.

## Catatan Teknis Penting
- `DialogWindowProvider` di Compose 1.7 sudah STABIL (tidak perlu `@OptIn(ExperimentalComposeUiApi::class)` — annotation itu bahkan unresolved).
- `CircularProgressIndicator` material3 1.3 memakai overload lambda `progress = { fraction }`, bukan `progress = Float`.
- Media3 aman untuk R8 (`:app:assemblePreview` minify SUCCESS tanpa rule tambahan — artifact media3 sudah bawa consumer rules).
- Untuk full screen video, `configChanges` di manifest adalah kunci: tanpa itu rotasi orientation me-recreate Activity dan Dialog/state full screen hilang.
- `CloudflareInterceptor` aman dipanggil dari thread IO (posting WebView ke main thread via Handler), jadi `MediaDownloader` bisa memakai `networkHelper.client` penuh.
- Ikon `Icons.Filled.Fullscreen`/`FullscreenExit`/`Download`/`CheckCircle` berasal dari `material-icons-extended` (sudah ada di dependency).
