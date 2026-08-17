# Tahap 23 — Pembaruan Dokumentasi Scripting (`DOCS_SCRIPTING.md`) & Perbaikan Skrip Scraper (`testscrape.txt` → `fixed_testscrape.js`)

**Tanggal:** 2026-08-09 · **Status:** SELESAI · **Build:** `cd rocat-app && sh gradlew :scripting:rhino:testDebugUnitTest` SUCCESS (**rhino 33 test hijau**: 20 RhinoScriptEngineTest + 2 AnichinScraperTest + 9 RoCatScriptTemplateTest + 2 FixedTestscrapeScraperTest).

## Tujuan
Tahap 23: (1) memperbarui `rocat-app/DOCS_SCRIPTING.md` agar mencerminkan semua
penyederhanaan API & komponen UI baru Tahap 22 (gaya kanvas baru, `RoCat.render`,
`RoCat.safeParseJson`, `RoCat.fetchJson`, `addJsonLog`/`addHtmlPreview`/`addAudio`/
`addAlert`/`addBadgeGroup`), serta (2) menganalisis & memperbaiki skrip scraper draf
`testscrape.txt` (XVideos) hingga berjalan mulus dengan format API terbaru, lalu
memvalidasinya lewat unit test Rhino yang menjalankan skrip asli.

## Analisis Skrip Asli (`testscrape.txt`)
Draf memakai API lama/rapuh dan beberapa perilaku salah:
1. **`JSON.parse(payloadStr)` tanpa guard** di `openDetail`/`openVideo` — payload grid
   rusak langsung melempar `SyntaxError` (hanya tertelan `try/catch`, tanpa umpan balik).
2. **UI verbose gaya lama**: puluhan `RoCatUI.clear()` + `addInput`/`addButton`/`log`
   terpisah di setiap fungsi, dan status selalu lewat `log` (tidak ada banner).
3. **Ekstraksi script html5player selalu gagal diam-diam**: memakai
   `doc.textOf("script:containsData(html5player)")` dan fallback `scripts[i].text` —
   di Jsoup, konten `<script>`/`<style>` adalah **CDATA** sehingga `Element.text()`
   mengembalikan string **kosong** (`script.text` → `""`). Verifikasi empiris via
   jsoup 1.18.1: `text() len=0` sedangkan `data()`/`html()` memuat kode JS (`len=141`).
   Akibatnya variabel `script` selalu `""` → include `:containsData` memang match, tapi
   `textOf` memanggil `.text()` → `""` → selalu `alert("Tidak ditemukan script ...")`.
4. **Judul kartu `[tag]` double-space**: `replace(/\[.*?\]/g,"")` pada "Sample [4K] Title"
   menghasilkan "Sample  Title" (spasi ganda) yang mengganggu pencarian/tampilan.
5. Genre/author detail yang berhasil di-parse tidak dirender sebagai chip (`addBadgeGroup`),
   tidak ada kartu debug untuk sumber stream (`addJsonLog`).

## Perubahan

### `fixed_testscrape.js` (BARU, root repo, v3.0.0)
- Metadata `==UserScript==` dipertahankan (icon/favicon xvideos, category Anime).
- Migrasi API Tahap 22:
  - `onLaunch`/`doSearch` → `RoCat.render([clear, input "query", button "🔍 Cari"])` +
    `RoCatUI.addAlert(msg, "warning"/"error"/"info")` untuk status halaman gagal/tanpa hasil.
  - `openDetail`/`openVideo` → `RoCat.safeParseJson(payloadStr, {})` + guard `!item.url`.
  - Detail: cover via `meta[property=og:image]` → `RoCat.render({type:"image",...})`,
    genre → `RoCatUI.addBadgeGroup(JSON.stringify(root.textsOf("div.video-metadata ul li a.is-keyword")))`.
  - `openVideo`: seluruh kualitas stream didebug via `RoCatUI.addJsonLog(sources, "Kualitas tersedia", true)`;
    sukses/gagal → `addAlert`; HLS → `pickBestVariant` → `RoCatUI.addVideo(playUrl, title+" · "+label, isHls, true)`.
- **Fix akar ekstraksi html5player** (`extractPlayerScript`): baca **`innerHtml`** element
  `<script>` (bukan `.text`/`textOf`), seleksi dengan `doc.find("script:containsData(html5player.setVideoUrlLow)")`
  → fallback `:containsData(setVideoHLS)` → `:containsData(html5player)` → fallback manual loop
  `scripts[i].innerHtml.indexOf("html5player")`. `extractVideoUrl` tetap regex `\('URL'\)`/`("URL")`.
- Normalisasi judul: `replace(/\[.*?\]/g," ").replace(/\s+/g," ").trim()` (hilangkan spasi ganda).
- Helper lain dipertahankan: `readInput` (map polos & `{get}`), `parseVideoCards` (data-src→src),
  `pickBestVariant` (buang `#EXT-X-STREAM-INF` tanpa URI), `variantScore`, `resolveUrl`.

### `FixedTestscrapeScraperTest.kt` (BARU, scripting/rhino)
Meniru `AnichinScraperTest`: `fetch()` skrip selalu lewat OkHttp, jadi mock via `okhttp3.Interceptor`
yang membalas HTML canned untuk host `xvideos.com` (home / search `?k=` / video `/video` / `\\.m3u8`):
1. `script drives search-to-video canvas flow with tahap22 api` — onLaunch (input query + grid 3 kolom,
   judul tanpa `[4K]` + url absolut) → doSearch (grid hasil) → openDetail (image og:image + badge 4K/HD +
   tombol "▶️ Putar Video") → openVideo (jsonLog debug, videoCard HLS **720.m3u8** (varian VALID tertinggi,
   varian 1080 malformed di-buang) diakhiri `:true:true`).
2. `malformed payloads are handled without throwing` — payload `"not-json-{{"` di openDetail/openVideo
   tetap `ScriptResult.Success` dan memunculkan `alert:error:`; query kosong `"   "` → `alert:warning:` dan
   TIDAK ada grid.

### `rocat-app/DOCS_SCRIPTING.md`
- §1.3 contoh pola navigasi ditulis ulang gaya baru (`RoCat.render([...])` + `safeParseJson` + guard).
- §5 Boilerplate diganti versi Tahap 22/23: `RoCat.render` untuk form/home/pencarian,
  `RoCat.safeParseJson` di `openDetail`/`playEpisode`, `addAlert` status, `addBadgeGroup` genre,
  `addVideo(..., true, true)` HLS.
- §6 Praktik Terbaik diperluas jadi 8 butir: #3 gambar ulang dengan `RoCat.render(clear+...)`,
  #4 wajib `RoCat.safeParseJson(str, {})`, #6 **konten `<script>` = CDATA** (`text()` Jsoup `""`;
  baca `innerHtml`; `script:containsData(...)` didukung `find/select`), #8 `addJsonLog` untuk data struktur.
- Lampiran ditambah referensi `fixed_testscrape.js` (selain `scrape_anichin.js`).
- Catatan: §2.5 (5 template card), §2.6 (`RoCat.render`), §4.4 (`safeParseJson`/`fetchJson`) sudah
  terdokumentasi (_akan_ dicek konsisten, tidak diubah).

## Catatan Teknis Penting
- **Jsoup CDATA**: `<script>`/`<style>` content adalah data, bukan teks — `Element.text()`
  (`JsoupElement.text`) mengembalikan `""`, jadi `textOf("script:containsData(...)")` selalu `""`
  walau selector match. Ambil **`innerHtml`** (`node.html()`) yang memuat kode JS mentah; buang
  `scripts[i].text`/`textOf(...)` untuk skrip. (Verifikasi: jsoup 1.18.1 `selectFirst("script").text().length()=0`,
  `.html().length()>0`.)
- `RoCat.render({type:"image", url:""})` tetap memanggil `addImage` walau URL kosong → di skrip
  guard `if (cover !== "")` sebelum mendeskripsikan image (hindari kartu kosong).
- `RoCat.safeParseJson` + guard `!item.url` adalah pola baku untuk payload grid yang mungkin kosong/
  korup; `addAlert(...,"error")` memberi tahu pengguna vs hanya log.
- Test skrip real wajib interceptor OkHttp (bukan `DefaultScriptEnvironment.fetchImpl` karena
  `BridgeFetch` selalu lewat client).

## Verifikasi
- `cd rocat-app && sh gradlew :scripting:rhino:testDebugUnitTest` → **BUILD SUCCESSFUL** (33 test hijau).
- `node --check fixed_testscrape.js` → syntax OK.
- `DOCS_SCRIPTING.md`: 48 fence ` ``` ` (genap), tanpa sisa typo (`Roam`/`Roof`/`onStarter`/`safeParseJSON`).