# Tahap 24 — Media HTTP Headers & Modernisasi UI

- Tanggal: 2026-08-16
- Sub-tahap: 24.1 bridge API, 24.2 lapisan native (Coil/Media3/downloader), 24.3 UI/UX modernisasi, 24.4 docs.
- Status: SELESAI — `:scripting:rhino:testDebugUnitTest` 44 hijau, `:app:assembleDebug` SUCCESS.

## Ringkasan

Menambahkan dukungan HTTP header (terutama `Referer`) ke seluruh media yang dimuat skrip
(gambar, video HLS, audio, cover grid) — header skrip menang, `Referer` yang hilang diisi
otomatis dari metadata `@match`/`@include` lalu origin URL media. Sekaligus memodernisasi
Scripts list + Script Canvas (swipe actions, animasi, kartu template).

## 24.1 — Bridge API & helper (scripting)

- `scripting/api/.../ScriptUiBridge.kt`: `addImage`/`addVideo`/`addAudio` kini `(url, title, allowDownload, headers = emptyMap())`, `addGrid(columns, itemsJson, onClickFunction, headers = emptyMap())` + KDoc.
- **Baru** `scripting/api/src/main/java/app/rocat/scripting/api/MediaHeaders.kt`:
  - `urlOrigin(url)`: `scheme://host[:port]`, hanya http/https, `host.trimStart('.')`.
  - `baseUrlFromMatch(match)`: strip `*`, `trimEnd('/', ' ')`, retry `://.` → `://` (java.net.URI menolak host bertitik-awal).
  - `baseUrlFromMatches(matches)`: pertama yang valid.
  - `effectiveMediaHeaders(url, scriptHeaders, scriptBaseUrl)`: header skrip dipertahankan (`Referer` dinormalisasi casing), yang hilang diisi dari `scriptBaseUrl` → origin URL.
- `scripting/rhino/.../RhinoScriptEngine.kt`: `RoCatUiBridge` `addImage`(idx 3)/`addVideo`(idx 4)/`addGrid`(idx 3)/`addAudio`(idx 3) baca headers via helper baru `argHeaders(args, index)` (objek JS → `NativeJSON.stringify`; string JSON → kotlinx `Json.parseToJsonElement`; tambahan `import kotlinx.serialization.json.contentOrNull`).
- `scripting/rhino/.../RoCatCoreWrapper.kt`: `renderOne` meneruskan atribut `headers` untuk descriptor image/video/audio.

## 24.2 — Lapisan native (app)

- `ScriptCanvasViewModel`: `resolveHeaders(headers, url)` + `scriptBaseUrl()` (`baseUrlFromMatches(script.matches)`); `thumbnailPreview`/`videoPreview`/`addImage`/`addVideo`/`addAudio` resolve. **Fix** `addGrid`: resolve **per item** (`item.copy(headers = resolveHeaders(headers, item.imageUrl))`) — kode lama `resolveHeaders(headers, scriptBaseUrl())` gagal tipe (`String?` vs `String`) dan salah semantik.
- `MediaDownloader.download/downloadFromUrl/fetchBytes` + `MediaDownloadComponents.start`: param `headers` (OkHttp `addHeader`).
- **Coil 3**: `ImagePreviewCard` & `GridView` pakai `coil3.network.NetworkHeaders` + `coil3.network.httpHeaders` — Coil 3 TIDAK punya `ImageRequest.Builder.addHeader`.
- **Media3**: `RocatVideoPlayer` (param `headers` → `DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)`), `VideoPreviewCard`, `AudioPreviewCard`.
- `ScriptCanvasScreen`: meneruskan `component.headers` ke ImagePreviewCard/VideoPreviewCard/AudioPreviewCard.

## 24.3 — Modernisasi UI

- `ScriptsScreen`:
  - `SwipeToDismissBox` (material3 1.3.1): EndToStart → delete (AlertDialog konfirmasi), StartToEnd → edit; `confirmValueChange` return `false` → snap-back. Background berwarna `errorContainer`/`secondaryContainer` + ikon Delete/Edit + `dismissState.dismissDirection`.
  - `StatusChip`: `animateColorAsState` (bg/fg) + `AnimatedContent` label.
  - Card pakai `combinedClickable(interactionSource, indication = ripple(), ...)` + `RoundedCornerShape(16.dp)`.
- `ScriptCanvasScreen`: per-item `Modifier.animateItem()` + `animateContentSize()` (anti-flicker clear/redraw), hint dibungkus `AnimatedVisibility(fadeIn/fadeOut)`; `@OptIn(ExperimentalFoundationApi)`.
- **Baru** `ScriptCanvasCard.kt`: `ElevatedCard` RoundedCornerShape 20dp, elevation animasi 2→8dp saat pressed, padding horizontal 16dp; dipakai `JsonLogCard`/`HtmlPreviewCard`/`AlertBannerCard`/`BadgeGroupCard`.

## 24.4 — Dokumentasi

- `DOCS_SCRIPTING.md`: §2.2 `addImage`/`addVideo` param `headers`; §2.3 `addGrid` headers; §2.5 `addAudio` headers; §2.6 descriptor `headers` (image/video/audio/grid); catatan auto-`Referer`; boilerplate §5 `addVideo(..., headers)` untuk html5player.

## 24.5 — Testing & Build

- Test recorder di 4 file test menerima `headers` dan merekam `:${headers.toSortedMap()}`; assertion exact di-update `:{}`; assert `endsWith(":true:true")` → `:true:true:{}`; `emptyMap()` → `emptyMap<String, String>()` (inferensi JUnit).
- Test baru `RoCatScriptTemplateTest`: headers objek/JSON-string diteruskan (image/video/audio), render attribute headers, `effectiveMediaHeaders` fallback, `baseUrlFromMatches`.
- `sh gradlew :scripting:rhino:testDebugUnitTest` → 44 test hijau.
- `sh gradlew :app:compileDebugKotlin` → `:app:assembleDebug` SUCCESS.

## Catatan

- Coil 3 headers: `NetworkHeaders.Builder().apply { set(k, v) }.build()` + `.httpHeaders(...)`.
- `java.net.URI("https://.example.org").getHost()` == null → butuh retry `replace("://.", "://")`.
- `contentOrNull` adalah extension — perlu `import kotlinx.serialization.json.contentOrNull`.
- Crossfade berbasis state bersama tak bisa menampilkan konten lama; gunakan `animateItem()` (idiomatis LazyColumn).
