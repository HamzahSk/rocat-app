# RoCat Scripting API — Dokumentasi Resmi

Panduan lengkap untuk membuat skrip _scraper_ (userscript) untuk aplikasi **RoCat**.
Dokumen ini mencerminkan API yang dibangun di tahap-tahap pengembangan sebelumnya:
`ScriptUiBridge` (global `RoCatUI`), `JsoupBridge` (global `RoCatDOM`), `fetch()`
sinkron berbasis OkHttp, dan integrasi Media3/ExoPlayer untuk pemutaran HLS.

---

## Ringkasan Eksekusi & Linguistik

Skrip RoCat adalah **JavaScript murni** yang dijalankan oleh mesin **Rhino 1.7.15**
dalam mode interpretasi. Dua konsekuensi penting:

| Hal | Fakta |
|-----|-------|
| `async` / `await` | **TIDAK didukung.** Tulis semua alur secara sinkron. |
| `import`/`export` | Tidak didukung — tidak ada bundler. Semua kode dalam satu file. |
| `class`, spread `...`, optional chaining `?.` | Tidak didukung oleh Rhino 1.7.15. |
| `let`, `const`, arrow function, template literal, generator, `Promise` | Didukung. |
| Watchdog | Setiap run punya batas instruksi (10.000.000) — `while(true)` akan di-hentikan. |
| Anti-crash | Setiap error/throw ditangkap Kotlin dan ditampilkan sebagai output, **tidak pernah menutup app**. |

Global yang tersedia di setiap skrip:

- `fetch(url, options)` — HTTP sinkron berbasis OkHttp (Response `.text()`/`.json()`).
- `RoCat` — wrapper universal bawaan: `render(items)`, `safeParseJson(str, fallback)`,
  `fetchJson(url, options)` (Tahap 22.1 — selalu tersedia, tak pernah crash).
- `RoCatUI` — bridge UI Compose (hanya tersedia saat skrip dibuka di **Canvas**).
- `RoCatDOM` — bridge parsing HTML berbasis Jsoup (pengganti Cheerio).
- `RoCatPage` — bridge *headless WebView* tingkat rendah (Tahap 23; hanya aktif bila
  mesin browser tersedia — lihat **Bab 7**).
- `RoCatBrowser` / `page` — API *browserless* ala Puppeteer/Playwright (Tahap 25/29;
  `page` = facade global praktis yang mengendalikan satu tab WebView tersembunyi).
- `JSON`, `Math`, `String`, `encodeURIComponent`, dll. — standar JS.

---

## 1. Struktur Dasar Skrip & Metadata

### 1.1 Blok Metadata Wajib

Tiap skrip **dianjurkan** membuka file dengan blok `==UserScript==` (gaya
Tampermonkey/Greasemonkey). Parser `ScriptMetadataParser` membaca blok ini, dan
bila blok tidak ada ia *fallback* ke pemindaian setiap baris `// @tag value`.

```javascript
// ==UserScript==
// @name         Anichin Scraper
// @version      2.0.0
// @description  Cari, baca detail, dan streaming HLS (.m3u8) anime dari Anichin
//               via RoCatUI.addVideo (baris lanjutan @description digabung newline).
// @author       RoCat AI
// @category     Anime
// @icon         https://anichin.cafe/path/icon.png
// @match        https://anichin.cafe/*
// @match        https://anichin.stream/*
// @include      https://*.anichin.cafe/*
// ==/UserScript==
```

### 1.2 Tag yang Didukung

| Tag | Arti | Keterangan |
|-----|------|-----------|
| `@name` | Nama skrip | Menjadi judul kartu & top bar Canvas. **Wajib diisi.** |
| `@version` | Versi | Bentuk semver (contoh `1.0.0`). Default `0.0.0`. |
| `@description` | Deskripsi | Mendukung **multi-baris** (`//` lanjutan digabung newline). |
| `@author` | Pembuat | Mendukung multi-baris. |
| `@icon` | URL cover ikon | `@iconURL` juga diterima sebagai alias. |
| `@category` | Label kategorisasi | Dipakai untuk mengelompokkan skrip di daftar (fallback `@group`). Kosong → grup "Others". |
| `@match` | Daftar pola URL | Digabung dengan `@include` menjadi allow-list (informational). |
| `@include` | Daftar pola URL | Alias `@match`. |
| `@grant` | Izin | `none` dirender sebagaimana adanya; saat ini tidak dipakai untuk gating API. |

Semua tag bersifat case-*insensitive*. `@name` adalah satu-satunya nilai wajib yang
memengaruhi tampilan; jika tidak ada, aplikasi memakai nama file/generated id.

### 1.3 Siklus Hidup Skrip (Script Lifecycle)

Skrip dijalankan di **Canvas** (kanvas per-skrip). Alur hidupnya:

1. Pengguna mengimpor / memilih skrip → layar **Script Canvas** terbuka.
2. Aplikasi membersihkan kanvas lalu **secara otomatis memanggil `onLaunch()`**
   setiap kali kanvas dibuka atau source skrip berubah (kode diedit).
3. `onLaunch()` menggambar antarmuka awal lewat `RoCatUI.*` (input, tombol, grid…).
4. Interaksi selanjutnya **dikendalikan sepenuhnya oleh JavaScript**:
   - Menekan tombol → fungsi bernama dipanggil dengan **objek input** (`{ id: value }`).
   - Menekan tile grid → fungsi bernama dipanggil dengan **payload JSON string**.
   - Skrip "berpindah halaman" dengan memanggil `RoCatUI.clear()` lalu menggambar ulang.
5. Skrip tanpa `onLaunch()` tidak *canvas-driven* — tidak terjadi apa-apa di kanvas
   (tidak error). Skrip seperti itu bisa dijalankan lewat entry point `main()` (jika ada).

Contoh pola navigasi (Search → Grid → Detail) — **gaya baru memakai `RoCat.render`**
(lihat §2.6): satu panggilan menggambar seluruh kanvas alih-alih banyak `RoCatUI.*`:

```javascript
function onLaunch() {
    RoCat.render([
        { type: "clear" },
        { type: "input", id: "query", hint: "Cari anime..." },
        { type: "button", label: "Cari", fn: "doSearch" },
        { type: "alert", message: "Menampilkan rilisan terbaru", level: "info" }
    ]);
    // ... fetch home + RoCatUI.addGrid(3, JSON.stringify(items), "openDetail")
}

function doSearch(inputs) {
    // inputs.query -> teks yang diketik user; payload tak-pernah-crash:
    var q = (inputs && inputs.query || "").trim();
    RoCat.render([
        { type: "clear" },
        { type: "button", label: "← Kembali", fn: "onLaunch" },
        { type: "input", id: "query", hint: "Cari anime..." },
        { type: "button", label: "Cari Lagi", fn: "doSearch" }
    ]);
    // ... fetch + parse ...
    RoCatUI.addGrid(3, JSON.stringify(results), "openDetail");
}

function openDetail(itemJsonString) {
    var item = RoCat.safeParseJson(itemJsonString, {}); // payload rusak -> {}
    if (!item || !item.url) { RoCatUI.addAlert("Item tidak valid.", "error"); return; }
    RoCatUI.clear();
    RoCatUI.addButton("← Kembali", "onLaunch");
    // buka halaman detail item...
}
```

---

## 2. Dokumentasi UI Bridge — global `RoCatUI`

Objek global `RoCatUI` tersedia saat skrip dijalankan di **Script Canvas**
(bridge `ScriptUiBridge` aktif). Semua panggilan *thread-safe*: hasil dimarshal ke
main thread oleh aplikasi, dan **kegagalan di dalam bridge tidak pernah menghentikan
skrip** (exception ditelan dan skrip lanjut).

### 2.1 Input & Interaksi

#### `RoCatUI.addInput(id, hint)`
Menambahkan satu kolom input teks yang diidentifikasikan oleh `id`.

| Parameter | Tipe | Deskripsi |
|-----------|------|-----------|
| `id` | `string` | Kunci unik; dipakai sebagai nama properti objek input. |
| `hint` | `string` | Placeholder yang ditampilkan di kolom. |

Jika skrip memanggil `addInput` berulang untuk `id` sama, nilai input lama **dipertahankan**
dan hanya hint yang disegarkan. Saat tombol ditekan, semua input yang **tidak kosong**
dikumpulkan menjadi satu objek `{ id: value }`.

#### `RoCatUI.addButton(label, functionName)`
Menambahkan tombol. Saat ditekan, aplikasi memanggil fungsi bernama `functionName`
dan meneruskan semua input sebagai **satu argumen objek**.

```javascript
RoCatUI.addInput("video_url", "Tempel URL video...");
RoCatUI.addButton("Ekstrak", "onExtract");

// definisi handler → menerima objek input
function onExtract(inputs) {
    var url = inputs.video_url;
    // ...
}
```

> Fungsi target yang tidak ada di skrip → output `Script has no function named '...'`.
> Return `undefined`/`null` dibulatkan menjadi string kosong (tidak muncul di console).

### 2.2 Media & Pratinjau

#### `RoCatUI.addImage(url, title, allowDownload, headers)`
Menampilkan kartu pratinjau gambar (dimuat dengan Coil).

| Parameter | Tipe | Default | Deskripsi |
|-----------|------|---------|-----------|
| `url` | `string` | — | URL gambar. |
| `title` | `string` | `""` | Judul tampil di atas gambar. |
| `allowDownload` | `boolean` | `true` | `true` → tampilkan tombol "simpan ke folder scrape". |
| `headers` | `object` \| `string` | `{}` | **Tahap 24.1** — HTTP header tambahan (objek `{ "Referer": "https://…" }` atau JSON string). Dikirim saat memuat gambar AND saat mengunduhnya. |

Contoh penutup sampul di halaman detail:

```javascript
RoCatUI.addImage(coverUrl, title, true);   // dengan tombol download
RoCatUI.addImage(coverUrl, title, false);  // tanpa download
```

#### `RoCatUI.addVideo(url, title, isStreamHls, allowDownload, headers)`
Menampilkan kartu video dengan **pemutar inline Media3/ExoPlayer native** + tombol
download dan toggle full screen.

| Parameter | Tipe | Default | Deskripsi |
|-----------|------|---------|-----------|
| `url` | `string` | — | URL sumber video (progressive MP4/WebM atau `.m3u8`). |
| `title` | `string` | `""` | Judul di kartu. |
| `isStreamHls` | `boolean` | `false` | `true` → dikonfigurasi sebagai **HLS media source**. |
| `allowDownload` | `boolean` | `true` | Tampilkan/sembunyikan tombol unduh. |
| `headers` | `object` \| `string` | `{}` | **Tahap 24.1** — HTTP header tambahan untuk streaming HLS (dipakai pada request playlist + segment) dan unduhan. |

**Penting — HLS otomatis memakai ExoPlayer native.** Pemutar memilih
`HlsMediaSource` bila `isStreamHls === true` **atau** URL mengandung `.m3u8`
/ berawal `hls://`; selain itu memakai `ProgressiveMediaSource`. Jadi Anda bahkan
bisa begitu:

```javascript
RoCatUI.addVideo("https://anichin.stream/hls/abc123.m3u8", "EP 1", true, true);
```

#### `RoCatUI.thumbnailPreview(url)` dan `RoCatUI.videoPreview(url)`
Peninggalan **backward-compatibility** — tetap tersedia dan berfungsi:

- `thumbnailPreview(url)` → setara `addImage(url)` (gambar saja, tanpa title).
  Kompatibel dengan skrip lama.
- `videoPreview(url)` → mode lama yang membuka video lewat `Intent.ACTION_VIEW`
  (keluar ke pemutar luar). **Disarankan beralih ke `addVideo`** yang memutar inline.

> **Otomatis `Referer` (Tahap 24.1).** Header yang Anda berikan menang mutlak. Namun
> bila `headers` **tidak** memuat `Referer`, aplikasi mengisinya secara otomatis dari
> origin URL media; jika ada pattern `@match`/`@include` pada metadata skrip, origin
> `@match` dipakai lebih dulu (fallback ke origin URL media). Ini mencukupi untuk
> hotlink-protection umum tanpa Anda perlu menulis `Referer` manual. Contoh kasus
> video `html5player` yang butuh `Referer` situs asal:

```javascript
RoCatUI.addVideo(
    "https://anichin.stream/play/abc123",
    "EP 1", true, true,
    { "Referer": "https://anichin.stream/" }   // wajib bila server memverifikasi asal
);
```

### 2.3 Layout — `RoCatUI.addGrid(columns, itemsJson, onClickFunction, headers)`

Membuat grid media ala Mihon.

| Parameter | Tipe | Deskripsi |
|-----------|------|-----------|
| `columns` | `number` | Jumlah kolom (di-clamp `1..8`). |
| `itemsJson` | `string` | **JSON array** objek (lihat format di bawah). |
| `onClickFunction` | `string` | Nama fungsi yang dipanggil saat tile diketuk, dengan **payload JSON string** item tsb. |
| `headers` | `object` \| `string` | **Tahap 24.1** — HTTP header tambahan yang diterapkan pada **tiap cover** grid (opsional). |

**Format JSON**: array dari objek; tiap objek minimal membawa `title` dan `image`
(URL). Sifat tambahan lain (`id`, `url`, `episode`, dsb.) **dipertahankan** lalu
dikirim ulang ke skrip ketika tile diketuk.

```javascript
var results = [
    { id: "1", title: "Perfect World", image: "https://…/cover1.jpg", url: "https://…/seri/perf" },
    { id: "2", title: "Lingwu Continent", image: "https://…/cover2.jpg", url: "https://…/ser/ling" },
];
RoCatUI.addGrid(3, JSON.stringify(results), "openDetail");
```

Pola pemanggilannya di skrip:

```javascript
function openDetail(itemJsonString) {
    var item = JSON.parse(itemJsonString);   // kembali ke objek asli
    var u = item.url;                        // alamat detail yang mau dibuka
    // ...
}
```

> Payload yang bukan JSON array valid → grid tidak dirender (silakan cek output console).
> Grid item yang kosong `title`/`image` tetap dirender dengan placeholder.

### 2.4 Utilitas UI

#### `RoCatUI.clear()`
Menghapus semua komponen yang sedang dirender. Dipakai untuk "berpindah halaman" —
pola utama navigasi skrip: `clear()` lalu gambar ulang.

#### `RoCatUI.log(text)`
Menambahkan satu baris pesan ke area log skrip. Tempat utama untuk memberi umpan
balik kepada pengguna selama scraping.

```javascript
RoCatUI.log("⏳ Memuat " + n + " episode...");
if (!res.ok) RoCatUI.log("Gagal: status " + res.status);
```

### 2.5 Template Cards (Tahap 22.2)

Lima kartu siap-pakai untuk tipe data umum. Semua **fault-tolerant**: argumen yang
buruk tidak pernah menghentikan skrip (kartu hanya tidak dirender).

#### `RoCatUI.addJsonLog(dataJson, title, allowCopy)`
Kartu log JSON **pretty-printed** + tombol "Copy JSON" (Toast konfirmasi).

| Parameter | Tipe | Default | Deskripsi |
|-----------|------|---------|-----------|
| `dataJson` | `string` \| `object` \| `array` | — | Data yang ditampilkan; objek/array JS di-serialisasi otomatis. |
| `title` | `string` | `""` | Judul kartu. |
| `allowCopy` | `boolean` | `true` | Tampilkan tombol salin. |

```javascript
RoCatUI.addJsonLog({ title: "X", count: 3 }, "Log", true);
RoCatUI.addJsonLog(JSON.stringify(hasil), "Data mentah", true);
```

#### `RoCatUI.addHtmlPreview(htmlContent, title)`
Pratinjau HTML kaya (tebal/miring/garis bawah/tautan/daftar) yang dirender **inline**
tanpa WebView. Tautan dibuka di browser sistem.

| Parameter | Tipe | Deskripsi |
|-----------|------|-----------|
| `htmlContent` | `string` | HTML sumber (mis. sinopsis dengan `<b>`, `<a href=...>`). |
| `title` | `string` | Judul kartu (opsional). |

```javascript
RoCatUI.addHtmlPreview("<b>" + title + "</b><br>" + sinopsis, "Sinopsis");
```

#### `RoCatUI.addAudio(url, title, allowDownload, headers)`
Kartu pemutar audio inline (Play/Pause + seek bar) dengan tombol unduh opsional.

| Parameter | Tipe | Default | Deskripsi |
|-----------|------|---------|-----------|
| `url` | `string` | — | URL file audio (MP3/M4A/…). |
| `title` | `string` | `""` | Judul kartu. |
| `allowDownload` | `boolean` | `true` | Tampilkan tombol "Unduh Audio" ke folder scrape. |
| `headers` | `object` \| `string` | `{}` | **Tahap 24.1** — HTTP header tambahan untuk streaming audio dan unduhan. |

#### `RoCatUI.addAlert(message, type)`
Banner ber-ikon berwarna untuk status singkat.

| Parameter | Tipe | Default | Deskripsi |
|-----------|------|---------|-----------|
| `message` | `string` | — | Pesan yang ditampilkan. |
| `type` | `string` | `"info"` | `"info"` / `"warning"` / `"error"` / `"success"`. Nilai lain → fallback `info`. |

```javascript
RoCatUI.addAlert("Hasil pencarian untuk: " + q, "info");
RoCatUI.addAlert("Halaman tidak mengembalikan kartu anime.", "warning");
```

#### `RoCatUI.addBadgeGroup(badges)`
Sederet chip/badge (genre, status episode, dsb.).

| Parameter | Tipe | Deskripsi |
|-----------|------|-----------|
| `badges` | `string[]` \| `string` | Array JS ATAU string JSON (`["Ongoing","HD"]`). |

```javascript
RoCatUI.addBadgeGroup(["Ongoing", "HD", "Action", "Rating 8.5"]);
RoCatUI.addBadgeGroup(JSON.stringify(genreList));
```

### 2.6 Simplification API — `RoCat.render(items)` (Tahap 22.1)

Alih-alih memanggil `RoCatUI.clear()` + satu-per-satu `addInput`/`addButton`/…, sebuah
skrip bisa **menggambar seluruh kanvas dengan satu panggilan** `RoCat.render(...)`.
Menerima satu descriptor ATAU array descriptor; tiap descriptor adalah objek dengan
kunci `type` (+ field sesuai tipe). Deskriptor yang salah/null diabaikan tanpa error.

| `type` | Field yang dibaca | Panggilan yang dihasilkan |
|--------|-------------------|---------------------------|
| `"clear"` / `"reset"` | — | `RoCatUI.clear()` |
| `"input"` | `id`, `hint` | `RoCatUI.addInput(id, hint)` |
| `"button"` | `label`, `fn` (alias `function`/`onClick`) | `RoCatUI.addButton(label, fn)` |
| `"image"` | `url` (alias `src`), `title`, `download`, `headers` | `RoCatUI.addImage(url, title, download, headers)` |
| `"video"` | `url`, `title`, `hls`, `download`, `headers` | `RoCatUI.addVideo(url, title, hls, download, headers)` |
| `"audio"` | `url`, `title`, `download`, `headers` | `RoCatUI.addAudio(url, title, download, headers)` |
| `"json"` | `data` (alias `json`), `title`, `copy` | `RoCatUI.addJsonLog(data, title, copy)` |
| `"html"` | `html` (alias `content`), `title` | `RoCatUI.addHtmlPreview(html, title)` |
| `"alert"` | `message` (alias `text`), `level` | `RoCatUI.addAlert(message, level)` |
| `"badges"` | `badges` (alias `items`/`list`) | `RoCatUI.addBadgeGroup(badges)` |
| `"grid"` | `columns`, `items` (alias `entries`), `onClick` (alias `fn`), `headers` | `RoCatUI.addGrid(columns, items, onClick, headers)` |
| `"log"` | `text` (alias `message`) | `RoCatUI.log(text)` |

Contoh — membandingkan gaya lama vs baru:

```javascript
// Gaya lama: banyak panggilan
RoCatUI.clear();
RoCatUI.addInput("query", "Cari...");
RoCatUI.addButton("Cari", "doSearch");
RoCatUI.addAlert("Perhatian", "warning");

// Gaya baru: satu panggilan
RoCat.render([
    { type: "clear" },
    { type: "input", id: "query", hint: "Cari..." },
    { type: "button", label: "Cari", fn: "doSearch" },
    { type: "alert", message: "Perhatian", level: "warning" },
    { type: "badges", badges: ["Ongoing", "HD"] },
    { type: "json", title: "Data", data: { a: 1, b: "x" }, copy: true }
]);
```

> `RoCat.render` berjalan meskipun `RoCatUI` tidak tersedia (mis. eksekusi polos di
> luar Canvas) — panggilan UI hanya dilewati, tidak error.

---

## 3. DOM Parsing: global `RoCatDOM`

`RoCatDOM` adalah **bridge DOM native berbasis Jsoup**. Ia menggantikan Cheerio atau
Jsoup murni dari sisi skrip: skrip tidak perlu `import`/bundel apa pun.

### 3.1 Parsing

#### `RoCatDOM.parse(html)`
Mem-parsing string HTML dan mengembalikan element **root wrapper**. Biasakan:

```javascript
var root = RoCatDOM.parse(htmlSource);
```

#### Pintasan statis (tanpa root)

| Fungsi | Mengembalikan |
|--------|--------------|
| `RoCatDOM.select(html, selector)` | Array element wrapper dari semua match `selector`. |
| `RoCatDOM.selectText(html, selector)` | Teks match pertama (`""` bila tak ada). |
| `RoCatDOM.selectAttr(html, selector, attr)` | Nilai atribut match pertama (`""`). |
| `RoCatDOM.selectHtml(html, selector)` | Outer HTML match pertama (`""`). |
| `RoCatDOM.has(html, selector)` | `boolean` — apakah ada match. |

Semua pemilihan memakai **selector CSS** yang didukung Jsoup (mis. `.eplister ul li a`,
`select.mirror option`, `script[type="application/ld+json"]`).

### 3.2 Metode Element (wrapper)

Setiap element wrapper (root maupun hasil `.find()`/`select()`) punya anggota:

| Properti/Fungsi | Tipe hasil | Keterangan |
|----------------|-----------|------------|
| `.text` | `string` | Teks elemen (trimmed). |
| `.html` | `string` | Outer HTML (termasuk tag sendiri). |
| `.innerHtml` | `string` | Inner HTML (hanya children). |
| `.attrs` | `object` | Peta nama → nilai semua atribut. |
| `.attr(name)` | `string` | Nilai atribut `name`, `""` bila absen. |
| `.has(selector)` | `boolean` | Elemen sendiri match `selector`. |
| `.contains(selector)` | `boolean` | Ada descendant yang match `selector`. |
| `.find(selector)` | `array` | Semua descendant match → array element baru. |
| `.textOf(selector)` | `string` | Teks descendant pertama yang match. |
| `.attrOf(selector, attr)` | `string` | Atribut descendant pertama yang match. |
| `.textsOf(selector)` | `array<string>` | Teks semua descendant yang match. |
| `.nextElement(selector)` | element \| `null` | Sibling berikutnya yang match `selector`. |

### 3.3 Contoh Ekstraksi (Anichin/Manga)

**Daftar episode dari halaman detail:**

```javascript
var doc = RoCatDOM.parse(html);
var eps = doc.find(".eplister ul li a");
var epItems = [];
for (var i = 0; i < eps.length; i++) {
    var a = eps[i];
    var url = a.attr("href");
    if (!url) continue;
    epItems.push({
        title: a.textOf(".epl-num") || ("Episode " + (i + 1)),
        image: "",
        url: url
    });
}
RoCatUI.addGrid(3, JSON.stringify(epItems), "openEpisode");
```

**Judul + sinopsis:**

```javascript
var title   = root.textOf(".entry-title");       // teks pertama
var synopsis= root.textOf(".synp .entry-content");// ""
var cover   = root.attrOf(".thumb img", "src");
```

**Atribut + sibling berikutnya (contoh MangaUpdates):**

```javascript
var valueBox = keys[i].nextElement(".info-box ... sContent");
```

---

## 4. Network & Utilities

### 4.1 `fetch(url, options)` — sinkron

`fetch` adalah fungsi **sinkron** bawaan yang melewati OkHttp client aplikasi.

```javascript
var res = fetch("https://anichin.cafe/", "GET", {}, null);
var html = res.text();
```

Dua bentuk pemanggilan:

```javascript
// Bentuk posisional: fetch(url, method, headers, body)
fetch(url, "POST", { "Content-Type": "application/json" }, '{"q":"naruto"}');

// Bentuk options: fetch(url, { method, headers, body })
fetch(url, { method: "GET", headers: { "X-Custom": "1" } });
```

**Objek Response** yang dikembalikan (autentik, tanpa promise):

| Anggota | Tipe | Keterangan |
|---------|------|------------|
| `.status` | `number` | Kode HTTP. `0` bila gagal jaringan. |
| `.statusText` | `string` | Pesan status. |
| `.ok` | `boolean` | `status` 200–299. |
| `.body` | `string` | Raw body response. |
| `.headers` | `object` | Map `name → value` (nilai pertama). |
| `.error` | `string?` | Pesan error bila request gagal (`null`/undefined). |
| `.text()` | `string` | Body sebagai string. |
| `.json()` | any | Parsing body sebagai JSON; **melempar `Error` JS yang bisa di-`try/catch`** bila body kosong/invalid. |

**Never throws untuk error jaringan**: koneksi gagal / timeout / DNS dilaporkan lewat
`.error` + `.status === 0`, bukan exception — skrip tidak bisa “gantung” atau crash app.
Timeout per-call pada klien skrip: connect 10 s, read 10 s, call 30 s.

> `res.json()` pada JSON invalid (Tahap 22.1): kini melempar `Error` JS biasa sehingga
> `try { res.json() } catch (e) { ... }` bekerja. Untuk parsing yang tak pernah throw
> gunakan `RoCat.safeParseJson(res.body, fallback)`.

### 4.2 Stealth & Interceptor (otomatis)

Setiap request `fetch()` melewati stack HTTP aplikasi sehingga **otomatis** mendapat:

1. **Cloudflare Bypasser** — `CloudflareInterceptor` mendeteksi challenge
   (HTTP 403/503 + `Server: cloudflare` + “Just a moment…”) lalu menyelesaikannya via
   WebView headless, mengambil `cf_clearance` ke cookie jar bersama, dan **retry** request.
2. **User-Agent kustom** — `UserAgentInterceptor` menempel UA browser-grade
   (default `Chrome/141`) bila belum diset. UA bisa diatur pengguna di **Settings → Jaringan**.
3. **Header stealth browser** — `StealthHeadersInterceptor` menambah `Accept-Language`,
   `Sec-CH-UA*`, `Sec-Fetch-*` default Chromium bila skrip/request tidak menentukannya.
4. **Custom DNS / DoH** — `DoHResolver` (System / Cloudflare / Google / Quad9 / kustom)
   sesuai pengaturan; fallback ke DNS sistem bila DoH gagal.
5. **Cookie jar bersama** — OkHttp berbagi cookie identik dengan WebView in-app
   (`AndroidCookieJar`): sesi login lewat tab **Browser** otomatis terpakai scraper.

> **Urutan prioritas:** header yang ANDA berikan secara eksplisit selalu menang
> atas nilai default (UA/stealth). Bila `headers` berisi `User-Agent`, itu yang dipakai.

Skrip mendapat berita terbaru secara otomatis: engine **di-rebuild** oleh `ScriptManager`
bila user mengubah User-Agent/DNS, jadi Anda tidak perlu menulis ulang apa pun.

### 4.3 Utilitas Native

#### `RoCatUI.decodeBase64(str)`
Dekode Base64 → UTF-8 menggunakan **decoder native** (`android.util.Base64` di app,
fallback `java.util.Base64` di test). Jauh lebih cepat untuk blobs iframe yang besar
(jangan menyalin decoder JS di semua skrip).

| Perilaku | Hasil |
|----------|-------|
| Input valid | string UTF-8 ter-decode. |
| Padding salah / whitespace | dipad & dibersihkan otomatis. |
| Gagal / bukan Base64 | `""` (empty string, tidak pernah throw). |

```javascript
var raw = opt.attr("value");              // "PGlmcmFtZSBzcmM9Imh0dHBzOi8vLi4u" 
var decoded = RoCatUI.decodeBase64(raw);
var m = decoded.match(/src=\"(.*?)\"/);
if (m) console.log("iframe:", m[1]);
```

#### `RoCatUI.save(fileName, content, mimeType)`
Menulis `content` sebagai file **nyata** ke folder scrape milik skrip
`[MainDirectory]/Scrapes/<scriptId>/` (SAF/StorageManager). Return: **string URI** file
yang tertulis, atau `""` bila gagal.

```javascript
var uri = RoCatUI.save("result.json", JSON.stringify(data), "application/json");
if (uri !== "") RoCatUI.log("Tersimpan: " + uri);
```

Parameter `mimeType` default `text/plain`. Nama file dinormalisasi aplikasi.

### 4.4 Wrapper Parsing — `RoCat.safeParseJson` / `RoCat.fetchJson`

#### `RoCat.safeParseJson(str, fallback)`
Mem-parsing JSON **tanpa pernah melempar**. Return `fallback` (default `null`) bila
`str` null/undefined/bukan JSON valid.

```javascript
var item = RoCat.safeParseJson(payloadStr, {});   // payload grid rusak → {}
var v = RoCat.safeParseJson(res.body, 0);
```

#### `RoCat.fetchJson(url, options)`
`fetch()` + parse otomatis: mengembalikan objek JSON bila `res.ok` dan body JSON valid,
selain itu `null` (HTTP error / body bukan JSON / jaringan gagal). Berguna untuk
endpoint JSON API.

```javascript
var data = RoCat.fetchJson(BASE + "/api/detail?id=" + id);
if (data) { /* pakai data */ } else { RoCatUI.addAlert("Gagal memuat API", "error"); }
```

> `fetchJson` sengaja mem-parsing lewat `JSON.parse` internal (bukan `res.json()`) agar
> body yang bukan JSON menjadi `null`, bukan exception.

---

## 5. Contoh Skrip Lengkap (Boilerplate)

Contoh fiktif yang menggabungkan semua API — **versi Tahap 22/23** memakai
`RoCat.render`, `RoCat.safeParseJson`, `addAlert`, `addBadgeGroup` dan pemutar HLS:

```javascript
// ==UserScript==
// @name         Rakun Anime Scraper (Contoh)
// @version      2.0.0
// @description  Boilerplate: onLaunch (RoCat.render) -> pencarian -> grid detail -> streaming HLS.
// @author       RoCat AI
// @category     Anime
// @icon         https://example.com/icon.png
// @match        https://contoh.anime/*
// ==/UserScript==

var BASE = "https://contoh.anime";

// --- Lifecycle: dipanggil otomatis saat kanvas dibuka ---
function onLaunch() {
    try {
        RoCat.render([
            { type: "clear" },
            { type: "input", id: "keyword", hint: "Cari anime / donghua..." },
            { type: "button", label: "Search", fn: "doSearch" },
            { type: "badges", badges: ["Ongoing", "HD"] }
        ]);

        var res = fetch(BASE + "/", "GET", {}, null);
        if (res.ok) {
            var items = parseCards(res.text());
            if (items.length > 0) {
                RoCatUI.log("✅ Ditemukan " + items.length + " judul. Ketuk untuk detail.");
                RoCatUI.addGrid(3, JSON.stringify(items), "openDetail");
            } else {
                RoCatUI.addAlert("Tidak ada kartu di home — gunakan pencarian di atas.", "warning");
            }
        } else {
            RoCatUI.addAlert("Gagal memuat home (" + res.status + ").", "error");
        }
    } catch (e) {
        RoCatUI.log("❌ onLaunch: " + e.message);
    }
}

// ---▶ Dipanggil tombol "Search" — menerima objek { keyword: "..." } ---
function doSearch(inputs) {
    try {
        var q = (inputs && inputs.keyword || "").trim();
        if (q === "") { RoCatUI.addAlert("Masukkan kata kunci.", "warning"); return; }

        RoCat.render([
            { type: "clear" },
            { type: "button", label: "🏠 Home", fn: "onLaunch" },
            { type: "input", id: "keyword", hint: "Cari anime / donghua..." },
            { type: "button", label: "Cari Lagi", fn: "doSearch" },
            { type: "alert", message: "Mencari \"" + q + "\"...", level: "info" }
        ]);

        var res = fetch(BASE + "/search?q=" + encodeURIComponent(q), "GET", {}, null);
        if (!res.ok) { RoCatUI.addAlert("Pencarian gagal (" + res.status + ").", "error"); return; }

        var items = parseCards(res.text());
        if (items.length === 0) { RoCatUI.addAlert("Tidak ada hasil untuk \"" + q + "\".", "info"); return; }
        RoCatUI.addGrid(2, JSON.stringify(items), "openDetail");
    } catch (e) {
        RoCatUI.log("❌ doSearch: " + e.message);
    }
}

// --- Dipanggil saat tile grid diketuk — payload JSON string ---
function openDetail(payload) {
    try {
        var item = RoCat.safeParseJson(payload, {});  // payload rusak -> {} (tak pernah throw)
        if (!item || !item.url) { RoCatUI.addAlert("Item grid tidak valid.", "error"); return; }
        RoCatUI.clear();
        RoCatUI.addButton("🏠 Home", "onLaunch");

        var res = fetch(item.url, "GET", {}, null);
        if (!res.ok) { RoCatUI.addAlert("Detail gagal (" + res.status + ").", "error"); return; }

        var doc = RoCatDOM.parse(res.text());
        // header (Tahap 24.1) opsional; tanpa "Referer" diisi otomatis dari @match.
        RoCatUI.addImage(doc.attrOf(".cover img", "src") || item.image, item.title, true);
        var genres = doc.textsOf(".genre-tags a");
        if (genres.length > 0) RoCatUI.addBadgeGroup(JSON.stringify(genres)); // chip genre
        RoCatUI.log(doc.textOf(".sinopsis") || "No synopsis.");

        var eps = doc.find(".episode-list a");
        var epList = [];
        for (var i = 0; i < eps.length; i++) {
            var u = eps[i].attr("href");
            if (u) { epList.push({ title: "Episode " + (i + 1), image: "", url: u }); }
        }
        if (epList.length === 0) { RoCatUI.addAlert("Tidak ada episode.", "info"); return; }
        RoCatUI.addGrid(3, JSON.stringify(epList), "playEpisode");
    } catch (e) {
        RoCatUI.log("❌ openDetail: " + e.message);
    }
}

// ---▶ Dipanggil saat episode diketuk — panggil pemutar HLS native ---
function playEpisode(payload) {
    try {
        var ep = RoCat.safeParseJson(payload, {});
        if (!ep || !ep.url) { RoCatUI.addAlert("Episode tidak valid.", "error"); return; }
        RoCatUI.clear();
        RoCatUI.addButton("🏠 Home", "onLaunch");
        RoCatUI.log("⏳ Menyiapkan stream: " + ep.title);

        // Contoh: URL master playlist .m3u8 dari sebuah server streaming.
        var hlsUrl = "https://cdn.contoh.anime/hls/ep" + (ep.url || "").replace(/\D+/g, "") + ".m3u8";

        // isStreamHls = true => ExoPlayer memakai HlsMediaSource.
        // headers opsional (Tahap 24.1): wajib saat server memverifikasi asal (mis.
        // html5player); tanpa "Referer" aplikasi mengisinya otomatis dari @match/URL.
        RoCatUI.addVideo(hlsUrl, ep.title, true, true, { "Referer": BASE + "/" });
        RoCatUI.addAlert("Stream siap! Tekan 'Play Inline' untuk memutar.", "success");
    } catch (e) {
        RoCatUI.log("❌ playEpisode: " + e.message);
    }
}

// --- Helper internal (RoCatDOM) ---
function parseCards(html) {
    var root = RoCatDOM.parse(html);
    var cards = root.find(".anime-card");
    var out = [];
    for (var i = 0; i < cards.length; i++) {
        var el = cards[i];
        var url = el.attrOf("a", "href");
        if (!url) continue;
        out.push({
            title: el.textOf("h2") || el.textOf("h3"),
            image: el.attrOf("img", "src") || "",
            url: url
        });
    }
    return out;
}
```

---

## 6. Praktik Terbaik & Batasan

1. **Selalu bungkus dalam `try/catch`** dan bicara via `RoCatUI.log`/`addAlert`. Error
   tidak menggagalkan app, tapi pengguna akan tahu sebab.
2. **Satu kanvas dimulai dari `onLaunch()`** — jangan lakukan fetch berat di luar
   fungsi (misal langsung saat load). Interaksi apa pun diawali oleh JavaScript.
3. **Navigasi = gambar ulang**. Gaya baru: `RoCat.render([{type:"clear"}, ...])` menggambar
   ulang seluruh kanvas dalam satu panggilan (pola stack manual: simpan state di variabel
   global skrip bila perlu).
4. **Parsing payload JSON wajib `RoCat.safeParseJson(str, {})`**, bukan `JSON.parse`
   langsung — payload grid yang rusak tidak boleh menghentikan skrip.
5. **Biasakan memilah varian HLS**: untuk stream seperti `anichin.stream`, fetch master
   `.m3u8`, seleksi varian ber-resolusi, dan kirim URL varian `isStreamHls=true` agar
   ExoPlayer tidak gagal mem-parse master yang berisi `#EXT-X-STREAM-INF` tanpa URI.
6. **Konten `<script>` (html5player, JSON-LD) adalah CDATA**: `text()` Jsoup mengembalikan
   string kosong (`script.text` → `""`, `textOf(...)` tak pernah cocok). Baca **`innerHtml`**
   wrapper RoCatDOM (isi kode JS mentah) dan, bila perlu, seleksi dulu dengan
   `script:containsData(...)` yang didukung `RoCatDOM.find/select`.
7. **Data besar** (Base64 blob, file scrape) → utamakan `RoCatUI.decodeBase64` dan
   `RoCatUI.save` (native, sinkron, dikendalikan).
8. **Jangan andalkan `console.log` tanpa Rhino console** di luar Canvas: gunakan
   `RoCatUI.log` untuk umpan balik visual dan `addJsonLog` untuk data struktur.

---

## 7. Browserless / Headless WebView API

> **Tahap 29.** Banyak situs modern adalah SPA atau dilindungi anti-bot yang DOM-nya
> baru ter-render oleh JavaScript — `fetch()` statis + `RoCatDOM` (Jsoup) tidak cukup.
> Bab ini memperkenalkan API *browserless* yang mengendalikan sebuah **WebView
> headless** (tersembunyi, tidak tampil di layar) milik aplikasi, dengan antarmuka yang
> meniru **Puppeteer/Playwright**.

### 7.1 Kapan Memakainya (Dual-Mode)

| Mode | Alat | Kecepatan | Baterai | Cocok untuk |
|------|------|-----------|---------|-------------|
| **Statis** | `fetch()` + `RoCatDOM` | ⚡ sangat cepat | irit | HTML biasa yang di-render server |
| **Interaktif** | `page.*` (WebView headless) | lambat | boros | SPA, login/form, anti-bot, *lazy-load* |

> **Aturan praktis:** mulai dengan `fetch()` (murah); gunakan `page` **hanya bila**
> DOM penting benar-benar tidak ada di HTML mentah. Kedua mode bisa dicampur dalam satu
> skrip tanpa konflik — objek `page` hanya "hidup" jika Anda memanggilnya.

### 7.2 Model Eksekusi (Sinkron, Anti-Crash)

- Rhino 1.7.15 **tidak mendukung `async`/`await`** — semua panggilan `page.*` adalah
  **sinkron dan memblokir** thread skrip sampai selesai (Kotlin memarkir thread Rhino
  dengan `CountDownLatch`; thread UI Android **tidak pernah** diblokir).
- Skrip tetap ditulis berurutan seperti biasa:
  ```javascript
  page.goto("https://contoh.site/login");
  page.type("#user", "admin");
  page.click("#btn");
  ```
- **Anti-crash:** setiap error di dalam bridge ditangkap Kotlin dan dilaporkan lewat
  nilai kembali (`false` / `""`), **tidak pernah** melempar exception yang mematikan app.
  Satu-satunya pengecualian: `page.waitForSelector` di sisi polyfill **bisa `throw`**
  error JavaScript biasa saat *timeout* — bungkus dengan `try/catch` jika mau ditangani.
- WebView hanya berjalan di *main thread* Android → seluruh komunikasi antar-thread
  di-marshal lewat `Handler` + `CountDownLatch` di `HeadlessWebViewManager`.

### 7.3 Objek Global `page`

Objek **`page`** adalah facade global yang mengendalikan satu tab WebView tersembunyi.
Ia otomatis menempel pada *singleton* `RoCatBrowser.getInstance()`, jadi `page` dan
`RoCatBrowser` selalu berbagi WebView yang sama. `typeof page` = `"object"` hanya jika
host menyediakan mesin browser (di Canvas selalu tersedia); skrip polos melihat
`"undefined"`.

| Metode | Deskripsi |
|--------|-----------|
| `page.goto(url, options?)` | Memuat `url` dan menunggu halaman selesai dimuat (Puppeteer-like). |
| `page.waitForSelector(selector, timeout?)` | Menunggu elemen DOM dirender. `timeout` default 30 s. |
| `page.waitForTimeout(ms)` | Jeda skrip `ms` milidetik (seperti `sleep`). |
| `page.click(selector)` | Klik elemen pertama yang cocok (fallback `element.click()`). |
| `page.type(selector, text, delay?)` | Mengetik `text` huruf demi huruf (default `delay` 50 ms). |
| `page.fill(selector, text)` | Mengisi input sekaligus + event `input`/`change`/`blur`. |
| `page.scrollTo(x, y)` | Menggulir ke koordinat absolut `(x, y)`. |
| `page.scrollBottom()` | Menggulir ke dasar dokumen — memicu **lazy-load**/infinite scroll. |
| `page.evaluate(fnOrJs, args?)` | Menjalankan JS di dalam halaman hidup; menerima fungsi + argumen. |
| `page.content()` | Mengambil HTML penuh yang sudah di-render JS (`outerHTML`). |
| `page.url()` | URL halaman saat ini. |
| `page.title()` | Judul halaman saat ini. |
| `page.screenshot(options?)` | Menangkap layar WebView → PNG; return path absolut file. |
| `page.cookies()` | Array cookie halaman (sinkron dengan jar OkHttp). |
| `page.setCookie(obj)` / `page.clearCookies()` | Atur / bersihkan cookie. |
| `page.locator(selector)` | Objek *locator* (lihat §7.5). |
| `page.goBack()` / `goForward()` / `reload()` / `stop()` | Navigasi riwayat / muat ulang / berhenti. |
| `page.close()` | Melepas WebView tersembunyi & membebaskan memorinya. |

**Contoh alur login + ambil DOM:**

```javascript
page.goto("https://contoh.site/login", { waitUntil: "load", timeout: 20000 });
page.type("#user", "admin");
page.type("#pass", "rahasia123");
page.click("#submit");
page.waitForSelector(".dashboard", 10000);

var html = page.content();          // DOM penuh setelah login
var doc  = RoCatDOM.parse(html);    // parse hasilnya dengan RoCatDOM
var name = doc.textOf(".user-name");
RoCatUI.addAlert("Masuk sebagai " + name, "success");
```

### 7.4 `page.goto(url, options)` — Detail

| Opsi | Tipe | Default | Deskripsi |
|------|------|---------|-----------|
| `waitUntil` | `string` | — | `"load"`/`"complete"` → tunggu `document.readyState === "complete"`; `"domcontentloaded"`/`"interactive"` → tunggu `interactive`. |
| `timeout` | `number` | `30000` | Batas waktu (ms). |

Kembali: objek `page` itu sendiri (untuk *chaining*), atau melempar `Error` JS saat
gagal membuka URL — bungkus dengan `try/catch` bila perlu:

```javascript
try {
    page.goto("https://contoh.site/", { waitUntil: "domcontentloaded", timeout: 15000 });
} catch (e) {
    RoCatUI.addAlert("Gagal membuka halaman: " + e.message, "error");
}
```

### 7.5 `page.locator(selector)` — Object Locator

Untuk operasi berulang pada satu elemen:

| Metode | Deskripsi |
|--------|-----------|
| `locator.exists()` | `boolean` — apakah elemen ada di DOM. |
| `locator.click()` | Klik elemen (hasil `{ success, error? }`). |
| `locator.fill(text)` | Isi input + event React/Vue-friendly (`{ success, error? }`). |
| `locator.type(text, delay?)` | Ketik bertahap. |
| `locator.text()` | `textContent` ter-trim dari elemen. |
| `locator.getAttribute(name)` | Nilai atribut, atau `null`. |
| `locator.waitFor(timeout?)` | Menunggu elemen; **melempar** `Error` saat timeout. |
| `locator.all()` | Array `{ text, html, attributes }` semua match. |
| `locator.clickAll()` | Klik semua match, hasil array `{ index, success, error? }`. |
| `locator.scrollIntoView()` | Gulir elemen ke tengah viewport. |
| `locator.getBoundingRect()` | Objek `{ x, y, width, height, top, … }` dari `getBoundingClientRect()`. |

### 7.6 `page.evaluate` & Ekstraksi DOM

`evaluate` menerima **fungsi** (paling aman, argumen di-serialisasi) atau **string JS**:

```javascript
// Fungsi + argumen → hasil native (objek/array/string/number).
var info = page.evaluate(function () {
    return {
        title: document.title,
        items: document.querySelectorAll(".card").length,
        scrollY: window.scrollY
    };
});
RoCatUI.addJsonLog(info, "Info halaman");

// String JS → hasil di-parsing balik ke nilai JS.
var ready = page.evaluate("document.readyState");
```

> Hasil `evaluate` selalu di-serialisasi JSON melalui WebView. `undefined`/`null`
> dipetakan ke `null`. Skrip yang mengembalikan `DOM`/`HTMLElement` tak bisa
> dikembalikan — ambil propertinya (mis. `el.outerHTML`) sebagai gantinya.

### 7.7 `page.scrollBottom()` — Memicu Lazy-Load

Situs *infinite scroll* (feed berita, galeri, hasil pencarian) baru merender konten
saat viewport mendekati dasar. Pola umum: gulir ke bawah beberapa kali sambil menunggu
elemen muncul:

```javascript
page.goto("https://contoh.site/feed", { waitUntil: "load", timeout: 20000 });
var attempts = 0;
while (attempts < 5 && !page.locator(".load-more").exists()) {
    page.scrollBottom();
    page.waitForTimeout(1200);
    attempts++;
}
var doc = RoCatDOM.parse(page.content());
var cards = doc.find(".card");
RoCatUI.log("Ditemukan " + cards.length + " kartu setelah " + attempts + "x scroll.");
```

### 7.8 `page.screenshot(options)` — Tangkapan Layar

| Opsi | Tipe | Default | Deskripsi |
|------|------|---------|-----------|
| `path` | `string` | `""` | Path absolut tujuan PNG. Kosong → file bertimestamp di cache app. |
| `quality` | `number` | `80` | Kompresi PNG (0–100). |

Return: **path absolut** file PNG, atau `""` saat gagal. WebView headless digambar ke
`Bitmap` (viewport default `1366×768` bila belum pernah di-attach).

```javascript
var path = page.screenshot({ path: "/storage/emulated/0/Pictures/rocat_shot.png", quality: 90 });
RoCatUI.log("Screenshot tersimpan: " + path);
```

### 7.9 Boilerplate — Gabungkan `page.goto` + `RoCatDOM.parse` + `RoCatUI.addImage`

Contoh lengkap: buka halaman detail berbasis JS, ambil DOM hasil render, dan tampilkan
cover serta sinopsis di Canvas:

```javascript
// ==UserScript==
// @name         Browserless Detail (Contoh)
// @version      1.0.0
// @description  Gabungan page.goto + RoCatDOM.parse + RoCatUI.addImage.
// @author       RoCat AI
// @category     Contoh
// @match        https://contoh.site/*
// ==/UserScript==

function onLaunch() {
    RoCat.render([
        { type: "clear" },
        { type: "input", id: "url", hint: "URL detail (SPA)..." },
        { type: "button", label: "Ambil", fn: "openInteractive" },
        { type: "alert", message: "Mode browserless: halaman di-render WebView dulu.", level: "info" }
    ]);
}

function openInteractive(inputs) {
    try {
        var url = (inputs && inputs.url || "").trim();
        if (!url) { RoCatUI.addAlert("Masukkan URL.", "warning"); return; }

        RoCatUI.addAlert("Membuka " + url + " ...", "info");
        RoCatUI.log("⏳ Menunggu render JavaScript (WebView headless)...");

        // 1) Buka di WebView tersembunyi dan tunggu DOM interaktif ter-render.
        page.goto(url, { waitUntil: "domcontentloaded", timeout: 20000 });
        page.waitForSelector(".detail-box", 10000);

        // 2) Ambil HTML penuh yang sudah dirender JS.
        var html = page.content();

        // 3) Parse dengan RoCatDOM (Jsoup) — hasil render WebView.
        var doc = RoCatDOM.parse(html);
        var cover = doc.attrOf(".cover img", "src") || "";
        var title = doc.textOf(".entry-title") || "Tanpa judul";
        var synopsis = doc.textOf(".sinopsis") || "";

        // 4) Tampilkan hasil di Canvas.
        RoCatUI.clear();
        RoCatUI.addButton("← Ulangi", "onLaunch");
        if (cover) RoCatUI.addImage(cover, title, true);   // kartu gambar + unduh
        RoCatUI.addAlert(title, "success");
        RoCatUI.addHtmlPreview(synopsis, "Sinopsis");
    } catch (e) {
        RoCatUI.addAlert("Gagal: " + e.message, "error");
    }
}
```

> Skrip lengkap demo yang bisa langsung diimpor: `test_browserless.js` di root repo.

---

## Lampiran: Referensi Sumber Implementasi

| Global | Sumber |
|--------|--------|
| `RoCatDOM` | `scripting/rhino/.../JsoupBridge.kt` |
| `RoCatUI` | `scripting/api/.../ScriptUiBridge.kt` + `scripting/rhino/.../RhinoScriptEngine.kt` |
| `RoCat` (render/safeParseJson/fetchJson) | `scripting/rhino/.../RoCatCoreWrapper.kt` (auto-inject) |
| `RoCatPage` / `RoCatBrowser` / `page` | `scripting/api/.../ScriptBrowserBridge.kt` + `scripting/rhino/.../RoCatPageBridge.kt` + `RoCatBrowserWrapper.kt` + `app/.../scripting/HeadlessWebViewManager.kt` |
| `fetch` | `scripting/api/.../network/ScriptFetch.kt` + `BridgeFetch` (Rhino) |
| Canvas / lifecycle | `app/.../ui/canvas/ScriptCanvasViewModel.kt` (entry `onLaunch`) |
| Template cards (JSON/HTML/Audio/Alert/Badge) | `app/.../ui/components/JsonLogCard.kt`, `HtmlPreviewCard.kt`, `AudioPreviewCard.kt`, `AlertBannerCard.kt`, `BadgeGroupCard.kt` |
| Pemutar Media3 / HLS | `app/.../ui/components/RocatVideoPlayer.kt` |
| Grid | `app/.../ui/components/GridView.kt` + `ScriptUIComponent.parseGrid` |
| Network stealth / DoH / UA | `core/.../network/NetworkHelper.kt`, interceptor CF & Stealth |
| Metadata Parser | `domain/.../script/ScriptMetadataParser.kt` |

> Skrip nyata yang memakai seluruh API ini: `scrape_anichin.js` dan
> `fixed_testscrape.js` (perbaikan draf `testscrape.txt`) di root repo — gunakan
> sebagai referensi kerja:
> `onLaunch`/`doSearch`/`openDetail` memakai `RoCat.render([...])`, payload dengan
> `RoCat.safeParseJson`, status dengan `addAlert`, genre dengan `addBadgeGroup`,
> debug stream dengan `addJsonLog`, dan penanganan HLS sungguhan (decode base64/
> ekstraksi `html5player` via `innerHtml` → master `.m3u8` → pilih varian →
> `RoCatUI.addVideo(..., true, true)`).