# Tahap 19 — Anichin Scraper Skrip End-to-End (Canvas: Home → Search → Detail → Episode HLS)

**Tanggal:** 2026-08-09 · **Status:** SELESAI · **Build:** `rocat-app` `sh gradlew :scripting:rhino:testDebugUnitTest` SUCCESS (22 test hijau — 20 RhinoScriptEngineTest + 2 AnichinScraperTest baru).

## Tujuan
Menulis skrip canvas produksi nyata (`scrape_anichin.js`, 320 baris, di repo root) yang memanfaatkan seluruh fondasi Tahap 8–18: `RoCatDOM` (parsing HTML), `RoCatUI.addGrid/addImage/addVideo` (kanvas), `fetch()` sync OkHttp, dan HLS streaming Media3. Skrip memandu alur lengkap **Home (rilisan terbaru) → Pencarian → Detail seri + daftar episode → Ekstraksi stream HLS** dari anichin.cafe, lalu memberikannya ke `RoCatUI.addVideo(..., isStreamHls = true)` untuk pemutar native. Dilengkapi unit test end-to-end yang memakai HTML canned (tanpa jaringan nyata).

## Perubahan

### Skrip `scrape_anichin.js`
- `onLaunch()`: `RoCatUI.clear()` → input pencarian + tombol "🔍 Cari" → `fetch(BASE_URL + "/")` → `parseAnimeCards()` (selector `.bixbox article .bsx`, dedup URL, judul `.tt h2` fallback `.tt`, cover `img[src]`) → `RoCatUI.addGrid(3, JSON.stringify(items), "openDetail")`.
- `doSearch(inputs)`: baca input via `readInput` (dukung objek map maupun `.get()`), `fetch(BASE_URL + "/page/1?s=" + encodeURIComponent(q))` → grid hasil.
- `openDetail(payloadStr)`: `JSON.parse` payload grid → `fetch(item.url)` → `RoCatDOM.parse`: judul `.entry-title`, cover `.thumb img` → `RoCatUI.addImage(cover, title, true)`, sinopsis `.synp .entry-content` → `log`, episode `.eplister ul li a` (nomor `.epl-num`) → `RoCatUI.addGrid(3, ..., "openEpisode")`.
- `openEpisode(payloadStr)`: `fetch(item.url)` → `select.mirror option` ber-`data-index` → `decodeBase64(value)` → regex `src="(.*?)"` → jika iframe memuat `anichin.stream`: `masterPlaylistUrl()` (bangun `https://anichin.stream/hls/<id>.m3u8`) → `pickBestVariant()` → `RoCatUI.addVideo(playUrl, title + " · " + serverName, true, true)`; mirror non-HLS (mis. OK.ru) hanya dicatat via `RoCatUI.log`.
- `pickBestVariant(masterUrl)`: ambil master m3u8, parse baris `#EXT-X-STREAM-INF:` → pasangan `{attrs, url}`; baris STREAM-INF **tanpa URI** (varian 1080p anichin yang malformed) di-buang (`pending = null`); pilih varian skor tertinggi (`variantScore`: tinggi RESOLUTION, fallback BANDWIDTH); URL relatif di-resolve via `resolveUrl`.
- `decodeBase64()`: pakai Android `android.util.Base64` bila ada, fallback decoder murni JS `b64Decode` (pad otomatis, `b64Val` map charset `B64_CHARS`).

### Unit test `AnichinScraperTest.kt`
- `script compiles and drives search-to-video canvas flow`: menjalankan skrip ASLI dari repo root (lokasi dicari dari `user.dir` dengan fallback 4 path) lewat `RhinoScriptEngine` + `ScriptUiBridge` recorder; meng-assert tiap tahap: onLaunch menggambar input+button+grid berisi "Perfect World"; doSearch menghasilkan grid berisi "Perfect World Movie"; openDetail menampilkan `image:...Perfect-World.webp:Perfect World:true` + grid episode; openEpisode memilih **varian 720p VALID** (`https://cdn.example/720.m3u8`) — bukan master malformed 1080p — dengan `isStreamHls=true` + `allowDownload=true`, judul `Perfect World - Ep 281 · Premium 1`, dan mirror OK.ru tidak dirender sebagai kartu.
- `pure-js base64 decoder handles padded input`: membandingkan `b64Decode` (decoder murni JS) dengan `java.util.Base64` untuk string ber-padding `==`.

## Catatan Teknis Penting
- **`DefaultScriptEnvironment.fetchImpl` TIDAK dipakai oleh `fetch()` skrip** — bridge Rhino `fetch()` selalu melewati OkHttp client (`RhinoScriptEngine(okHttpClient)`). Untuk mock hermetic, pasang **`okhttp3.Interceptor`** yang meng-intercept request ke `anichin.cafe` / `anichin.stream` dan membalas dengan HTML canned (`Response.Builder` + `.toResponseBody(mediaType)`; okhttp 4.12, pakai `ResponseBody.Companion.toResponseBody` + `MediaType.Companion.toMediaType`). Urutan kecocokan URL: `/hls/test123.m3u8` → masterPlaylist, `-episode-` → halaman episode, `/seri/` → detail, `/page/1?s=` → hasil pencarian, else → home.
- Mode interpretasi Rhino (`optimizationLevel = -1`) berlaku: bilangan bulat dikembalikan sebagai `Double`; skrip memakai `parseInt`/string concat dengan aman.
- Master m3u8 anichin.stream nyata mengandung `#EXT-X-STREAM-INF` **tanpa URI** untuk varian tertinggi → mengirim master ke ExoPlayer akan gagal parse; skrip menyerahkan **URL varian valid** langsung ke `addVideo`.
- `readInput()` harus menangani dua bentuk inputs (`{ get }` map vs objek polos) karena pemanggilan via `invokeNamedFunction` dengan `Map<String, String>`.
- Validasi sintaks skrip dilakukan oleh `node --check` (jika node tersedia) dan dikonfirmasi ulang oleh test yang memanggil `onLaunch` (kompilasi Rhino langsung gagal bila ada error syntax).

## Verifikasi
- `cd rocat-app && sh gradlew :scripting:rhino:testDebugUnitTest` → **BUILD SUCCESSFUL** (2 test AnichinScraperTest hijau; suite rhino penuh 22 test hijau).
- Laporan: `rocat-app/scripting/rhino/build/reports/tests/testDebugUnitTest/index.html`.
- Skrip berjalan di atas fondasi yang sudah diverifikasi Tahap 8–18 (RoCatDOM/RoCatUI/fetch/HLS).
