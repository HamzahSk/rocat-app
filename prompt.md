Prompt Fase 36: Peningkatan UI & Kompatibilitas Komik Reader

📋 Role & Objective

Kamu adalah Senior Android Engineer & UI/UX Designer untuk aplikasi RoCat. Kita memasuki Fase 36: Peningkatan UI & Kompatibilitas Komik Reader.

Berdasarkan hasil pengujian Fase 35, ditemukan beberapa masalah kritis:

1. UI Canvas Tidak Konsisten — Margin, padding, dan spacing antar komponen tidak sesuai. Layout terlihat berantakan.
2. Komik Reader Belum Optimal — Skrip CGBUM masih memiliki bug dan pengalaman membaca kurang nyaman.
3. Kurangnya Template Khusus — Tidak ada template standar untuk skrip komik reader yang bisa menjadi referensi.
4. Kompatibilitas — Skrip lama (non-komik) harus tetap berfungsi tanpa perubahan.

---

🎯 Tujuan Utama

1. Perbaikan UI Canvas

Buat sistem layout yang lebih konsisten dengan:

· Margin & padding yang seragam (16dp untuk semua sisi)
· Spacing antar komponen yang teratur (8dp, 12dp, 16dp)
· Tombol yang lebih rapi dengan ukuran yang proporsional
· Grid yang responsif dengan jarak antar item yang konsisten

2. Template Komik Reader

Buat template skrip khusus untuk komik reader dengan fitur:

Fitur Deskripsi
Daftar Komik Grid dengan cover, judul, status
Pencarian Cari komik berdasarkan judul
Filter Filter berdasarkan genre, status, tipe
Detail Komik Cover besar, sinopsis, genre, author, status
Daftar Chapter Grid atau list chapter dengan navigasi
Pembaca Gambar Mode single page, scroll all pages, zoom
Riwayat Baca Simpan progress chapter terakhir
Bookmark Tandai komik favorit

3. Referensi dari Mihon (Tachiyomi)

Ambil inspirasi dari UI/UX Mihon:

```
Mihon UI Elements:
├── Bottom Navigation
│   ├── Library (komik tersimpan)
│   ├── Browse (cari & discover)
│   ├── Updates (chapter terbaru)
│   └── More (pengaturan)
├── Grid View (2-3 kolom)
│   ├── Cover image (rounded corners)
│   ├── Title (1-2 lines, ellipsis)
│   └── Status badge (ongoing/completed)
├── Detail View
│   ├── Hero cover (full width, blur background)
│   ├── Title & author
│   ├── Description (expandable)
│   ├── Genre chips
│   └── Chapter list (with download indicator)
└── Reader View
    ├── Single page (fit to width)
    ├── Double page (landscape)
    ├── Webtoon mode (continuous scroll)
    └── Navigation (left/right tap)
```

---

🏗️ Struktur Template Komik Reader

Metadata Template

```javascript
// ==UserScript==
// @name         [Nama Sumber] Komik Reader
// @version      1.0.0
// @description  Baca komik dari [nama website] dengan dukungan pencarian, filter, dan pembaca gambar.
// @author       [Nama Author]
// @category     Komik
// @icon         https://[website]/favicon.ico
// @match        https://[website]/*
// @include      https://*.[website]/*
//
// // --- PENGATURAN YANG DAPAT DISESUAIKAN ---
// @settings     quality: select: default=50, options=30,50,70,90, label=Kualitas Gambar
// @settings     useProxy: boolean: default=true, label=Gunakan Proxy (hemat kuota)
// @settings     proxyUrl: string: default=, label=Custom Proxy URL
// @settings     readingMode: select: options=single,scroll,webtoon, default=single, label=Mode Baca
// @settings     loadOnDemand: boolean: default=true, label=Load On Demand (hemat data)
// @settings     maxThumbs: number: default=20, min=5, max=50, label=Maksimal Thumbnail
// @settings     autoSaveProgress: boolean: default=true, label=Simpan Progress Otomatis
// @settings     showHistory: boolean: default=true, label=Tampilkan Riwayat Baca
// ==/UserScript==
```

Struktur Kode Template

```javascript
// ============================================
// KONFIGURASI
// ============================================
var SOURCE = {
    name: "Nama Sumber",
    baseUrl: "https://website.com",
    // Selector untuk setiap elemen
    selectors: {
        comicGrid: ".comic-grid .comic-card",
        comicTitle: ".comic-card-title a",
        comicCover: ".comic-card-cover img",
        comicStatus: ".badge-status",
        detailContainer: ".comic-detail",
        chapterList: ".chapter-grid .ch-grid-item",
        readerImages: ".reader-images .page-container"
    }
};

// ============================================
// STATE MANAGEMENT
// ============================================
var State = {
    currentComic: null,
    currentChapter: null,
    chapters: [],
    pages: [],
    readingProgress: {},
    bookmarks: []
};

// ============================================
// UTILITY FUNCTIONS
// ============================================
function normalizeUrl(url) {
    if (!url) return "";
    if (url.startsWith("http")) return url;
    if (url.startsWith("//")) return "https:" + url;
    return SOURCE.baseUrl + url;
}

function extractId(url) {
    // Ekstrak ID dari URL
    var match = url.match(/\/(komik|series|manga)\/([^\/]+)/);
    return match ? match[2] : "";
}

// ============================================
// UI RENDER FUNCTIONS
// ============================================
function renderMainUI() {
    RoCat.render([
        { type: "clear" },
        { type: "text", content: "📚 " + SOURCE.name + " Reader", style: "title" },
        { type: "divider", thickness: 2, color: "#e0e0e0" },
        
        // Search Bar
        { type: "layout", layout: "row", padding: 8, children: [
            { type: "autocomplete", id: "search", hint: "Cari komik...", 
              historyKey: "comic-search", flex: 3 },
            { type: "button", label: "🔍", fn: "doSearch", flex: 1 }
        ]},
        
        // Navigation Buttons
        { type: "layout", layout: "row", padding: 8, children: [
            { type: "button", label: "📖 Populer", fn: "loadPopular", flex: 1 },
            { type: "button", label: "🆕 Terbaru", fn: "loadLatest", flex: 1 },
            { type: "button", label: "⭐ Favorit", fn: "loadFavorites", flex: 1 },
            { type: "button", label: "🔧 Filter", fn: "showFilters", flex: 1 }
        ]},
        
        { type: "divider" },
        { type: "alert", message: "Pilih komik favoritmu!", level: "info" }
    ]);
    
    // Load default
    loadPopular();
}

function renderComicDetail(comic) {
    RoCat.render([
        { type: "clear" },
        { type: "layout", layout: "row", padding: 8, children: [
            { type: "button", label: "🏠 Home", fn: "onLaunch", flex: 1 },
            { type: "button", label: "🔙 Kembali", fn: "loadPopular", flex: 1 },
            { type: "button", label: "⭐ Bookmark", fn: "toggleBookmark", flex: 1 }
        ]},
        { type: "divider" }
    ]);
    
    // Hero Cover
    RoCatUI.addImage(comic.cover, comic.title, true);
    
    // Info
    RoCat.render([
        { type: "text", content: "📖 " + comic.title, style: "heading" },
        { type: "badges", badges: comic.genres ? [comic.status, comic.type].concat(comic.genres.slice(0, 5)) : [comic.status, comic.type] },
        { type: "text", content: "✍️ " + (comic.author || "Tidak diketahui"), style: "body" },
        { type: "text", content: "📊 " + (comic.rating || "N/A"), style: "caption" }
    ]);
    
    // Sinopsis
    if (comic.synopsis) {
        RoCatUI.addHtmlPreview(comic.synopsis, "📝 Sinopsis");
    }
    
    // Progress
    if (State.readingProgress[comic.id]) {
        RoCatUI.addAlert("📌 Lanjutkan dari Chapter " + State.readingProgress[comic.id], "info");
    }
    
    // Chapter List
    renderChapterList(comic.chapters);
}

function renderChapterList(chapters) {
    if (!chapters || chapters.length === 0) {
        RoCatUI.addAlert("Tidak ada chapter tersedia.", "warning");
        return;
    }
    
    var chapterItems = [];
    for (var i = 0; i < chapters.length; i++) {
        var ch = chapters[i];
        var isRead = State.readingProgress[ch.comicId] && 
                     State.readingProgress[ch.comicId] >= ch.number;
        
        chapterItems.push({
            title: (isRead ? "✅ " : "") + ch.title,
            image: "",
            url: ch.url,
            chapter: ch.number,
            comicId: ch.comicId,
            isRead: isRead
        });
    }
    
    RoCatUI.addGrid(3, JSON.stringify(chapterItems), "openChapter");
    RoCatUI.log("📑 " + chapters.length + " chapter tersedia");
}

function renderReader(pages) {
    var mode = RoCat.settings.readingMode || "single";
    
    RoCatUI.clear();
    RoCatUI.addButton("🔙 Detail", function() {
        renderComicDetail(State.currentComic);
    });
    
    if (mode === "single") {
        renderSinglePage(pages);
    } else if (mode === "scroll") {
        renderAllPages(pages);
    } else if (mode === "webtoon") {
        renderWebtoonMode(pages);
    }
}

function renderSinglePage(pages) {
    var currentPage = 0;
    
    function showPage(index) {
        if (index < 0 || index >= pages.length) return;
        currentPage = index;
        RoCatUI.clear();
        RoCatUI.addButton("🔙 Detail", function() {
            renderComicDetail(State.currentComic);
        });
        
        var headers = { "Referer": SOURCE.baseUrl + "/" };
        var url = processImageUrl(pages[index]);
        RoCatUI.addImage(url, "📄 " + (index + 1) + "/" + pages.length, true, headers);
        
        // Navigation
        RoCat.render([
            { type: "layout", layout: "row", padding: 8, children: [
                { type: "button", label: "⏪", fn: function() { showPage(currentPage - 1); }, flex: 1 },
                { type: "button", label: "📖 Semua", fn: function() { renderReader(pages); }, flex: 1 },
                { type: "button", label: "⏩", fn: function() { showPage(currentPage + 1); }, flex: 1 }
            ]}
        ]);
    }
    
    showPage(0);
}

function renderAllPages(pages) {
    var headers = { "Referer": SOURCE.baseUrl + "/" };
    RoCatUI.addAlert("📖 " + pages.length + " halaman (scroll ke bawah)", "info");
    
    for (var i = 0; i < pages.length; i++) {
        var url = processImageUrl(pages[i]);
        RoCatUI.addImage(url, "📄 " + (i + 1) + "/" + pages.length, true, headers);
    }
}

function renderWebtoonMode(pages) {
    // Webtoon mode: continuous vertical scroll
    var headers = { "Referer": SOURCE.baseUrl + "/" };
    RoCatUI.addAlert("📱 Mode Webtoon - " + pages.length + " halaman", "info");
    
    for (var i = 0; i < pages.length; i++) {
        var url = processImageUrl(pages[i]);
        RoCatUI.addImage(url, "", false, headers);
    }
}

// ============================================
// DATA FETCHING FUNCTIONS
// ============================================
function fetchComics(url, callback) {
    try {
        RoCatUI.log("⏳ Memuat data...");
        var res = fetch(url, "GET", {}, null);
        
        if (!res.ok) {
            RoCatUI.addAlert("Gagal memuat (" + res.status + ")", "error");
            return;
        }
        
        var doc = RoCatDOM.parse(res.text());
        var comics = parseComicList(doc);
        callback(comics);
    } catch (e) {
        RoCatUI.log("❌ Error: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

function fetchComicDetail(url, callback) {
    try {
        var res = fetch(url, "GET", {}, null);
        if (!res.ok) {
            RoCatUI.addAlert("Gagal memuat detail (" + res.status + ")", "error");
            return;
        }
        
        var doc = RoCatDOM.parse(res.text());
        var comic = parseComicDetail(doc, url);
        callback(comic);
    } catch (e) {
        RoCatUI.log("❌ Error: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

function fetchChapterPages(url, callback) {
    try {
        var res = fetch(url, "GET", {}, null);
        if (!res.ok) {
            RoCatUI.addAlert("Gagal memuat chapter (" + res.status + ")", "error");
            return;
        }
        
        var doc = RoCatDOM.parse(res.text());
        var pages = parseChapterPages(doc);
        callback(pages);
    } catch (e) {
        RoCatUI.log("❌ Error: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

// ============================================
// PARSING FUNCTIONS (HARUS DITIMPA SESUAI SOURCE)
// ============================================
function parseComicList(doc) {
    var cards = doc.find(SOURCE.selectors.comicGrid);
    var results = [];
    
    for (var i = 0; i < cards.length; i++) {
        var el = cards[i];
        var titleEl = el.find(SOURCE.selectors.comicTitle);
        var title = titleEl.length > 0 ? titleEl[0].text.trim() : "Tanpa Judul";
        var url = titleEl.length > 0 ? titleEl[0].attr("href") : "";
        var cover = el.attrOf(SOURCE.selectors.comicCover, "abs:src") || 
                    el.attrOf(SOURCE.selectors.comicCover, "src") || "";
        
        results.push({
            id: extractId(url),
            title: title,
            cover: cover,
            url: normalizeUrl(url),
            status: el.textOf(SOURCE.selectors.comicStatus) || ""
        });
    }
    
    return results;
}

function parseComicDetail(doc, url) {
    var container = doc.find(SOURCE.selectors.detailContainer);
    if (container.length === 0) {
        return null;
    }
    var el = container[0];
    
    return {
        id: extractId(url),
        title: el.textOf(".comic-info h1") || "Tanpa Judul",
        cover: el.attrOf(".comic-cover img", "abs:src") || "",
        status: el.textOf(".badge-status") || "",
        type: el.textOf(".badge-type-text") || "",
        author: el.textOf(".comic-author") || "",
        rating: el.textOf(".comic-rating") || "",
        synopsis: el.textOf(".synopsis-content") || "",
        genres: el.textsOf(".comic-genres .genre-pill"),
        chapters: parseChapterList(el)
    };
}

function parseChapterList(detailEl) {
    var chapters = detailEl.find(SOURCE.selectors.chapterList);
    var results = [];
    
    for (var i = 0; i < chapters.length; i++) {
        var ch = chapters[i];
        results.push({
            title: ch.attr("title") || "Chapter " + (i + 1),
            url: normalizeUrl(ch.attr("href") || ""),
            number: parseFloat(ch.attr("data-chapter") || (i + 1)),
            comicId: ch.attr("data-comic-id") || ""
        });
    }
    
    return results;
}

function parseChapterPages(doc) {
    var pages = doc.find(SOURCE.selectors.readerImages);
    var urls = [];
    
    for (var i = 0; i < pages.length; i++) {
        var url = pages[i].attr("data-url") || 
                  pages[i].attr("abs:data-url") || 
                  pages[i].attr("src") || "";
        if (url) urls.push(url);
    }
    
    return urls;
}

// ============================================
// IMAGE PROCESSING
// ============================================
function processImageUrl(url) {
    if (!url) return "";
    
    var useProxy = RoCat.settings.useProxy !== false;
    var quality = RoCat.settings.quality || "50";
    var proxyUrl = RoCat.settings.proxyUrl || "";
    
    if (useProxy) {
        var clean = url.replace(/^https?:\/\//, "");
        return "https://i0.wp.com/" + clean + "?quality=" + quality;
    }
    
    if (proxyUrl) {
        return proxyUrl.replace(/%s/g, url);
    }
    
    return url;
}

// ============================================
// READING PROGRESS & BOOKMARKS
// ============================================
function saveProgress(comicId, chapter) {
    State.readingProgress[comicId] = chapter;
    RoCat.saveHistory("comic_progress_" + comicId, String(chapter));
}

function loadProgress(comicId) {
    var saved = RoCat.getHistory("comic_progress_" + comicId);
    return saved ? parseInt(saved) : 0;
}

function toggleBookmark() {
    if (!State.currentComic) return;
    var id = State.currentComic.id;
    var index = State.bookmarks.indexOf(id);
    
    if (index === -1) {
        State.bookmarks.push(id);
        RoCatUI.addAlert("⭐ Ditambahkan ke favorit", "success");
    } else {
        State.bookmarks.splice(index, 1);
        RoCatUI.addAlert("⭐ Dihapus dari favorit", "info");
    }
    
    RoCat.saveHistory("bookmarks", JSON.stringify(State.bookmarks));
}

function loadFavorites() {
    var bookmarks = RoCat.getHistory("bookmarks");
    if (bookmarks) {
        State.bookmarks = JSON.parse(bookmarks) || [];
    }
    
    if (State.bookmarks.length === 0) {
        RoCatUI.addAlert("Belum ada komik favorit.", "warning");
        return;
    }
    
    // Load all bookmarked comics
    var items = [];
    for (var i = 0; i < State.bookmarks.length; i++) {
        var id = State.bookmarks[i];
        // Need to fetch detail for each bookmark
        // This is simplified - in real implementation, you'd cache this
        items.push({
            title: "Loading...",
            image: "",
            url: SOURCE.baseUrl + "/komik/" + id,
            id: id
        });
    }
    
    RoCatUI.addGrid(2, JSON.stringify(items), "openDetail");
}

// ============================================
// LIFECYCLE FUNCTIONS
// ============================================
function onLaunch() {
    try {
        // Load saved state
        var bookmarks = RoCat.getHistory("bookmarks");
        if (bookmarks) {
            State.bookmarks = JSON.parse(bookmarks) || [];
        }
        
        renderMainUI();
    } catch (e) {
        RoCatUI.log("❌ onLaunch: " + e.message);
    }
}

function loadPopular() {
    fetchComics(SOURCE.baseUrl + "/daftar-komik", function(comics) {
        if (comics.length === 0) {
            RoCatUI.addAlert("Tidak ada komik ditemukan.", "warning");
            return;
        }
        RoCatUI.log("✅ " + comics.length + " komik dimuat");
        RoCatUI.addGrid(3, JSON.stringify(comics), "openDetail");
    });
}

function loadLatest() {
    fetchComics(SOURCE.baseUrl + "/last-update", function(comics) {
        if (comics.length === 0) {
            RoCatUI.addAlert("Tidak ada komik terbaru.", "warning");
            return;
        }
        RoCatUI.log("✅ " + comics.length + " komik terbaru");
        RoCatUI.addGrid(3, JSON.stringify(comics), "openDetail");
    });
}

function doSearch(inputs) {
    var q = (inputs && inputs.search || "").trim();
    if (!q) {
        RoCatUI.addAlert("Masukkan kata kunci.", "warning");
        return;
    }
    
    renderMainUI();
    RoCatUI.addAlert("🔍 Mencari \"" + q + "\"...", "info");
    
    var url = SOURCE.baseUrl + "/daftar-komik?keyword=" + encodeURIComponent(q);
    fetchComics(url, function(comics) {
        if (comics.length === 0) {
            RoCatUI.addAlert("Tidak ada hasil untuk \"" + q + "\"", "info");
            return;
        }
        RoCatUI.log("✅ " + comics.length + " hasil ditemukan");
        RoCatUI.addGrid(2, JSON.stringify(comics), "openDetail");
    });
}

function showFilters() {
    RoCat.render([
        { type: "clear" },
        { type: "text", content: "🔧 Filter Komik", style: "title" },
        { type: "button", label: "🏠 Kembali", fn: "onLaunch" },
        { type: "divider" },
        { type: "dropdown", id: "type", label: "Tipe", 
          options: ["Semua", "Manga", "Manhwa", "Manhua"], default: "Semua" },
        { type: "dropdown", id: "status", label: "Status", 
          options: ["Semua", "Ongoing", "Tamat"], default: "Semua" },
        { type: "autocomplete", id: "genre", hint: "Genre...", historyKey: "comic-genres" },
        { type: "button", label: "🔍 Terapkan", fn: "applyFilters" }
    ]);
}

function applyFilters(inputs) {
    var params = [];
    if (inputs.type && inputs.type !== "Semua") {
        params.push("type=" + encodeURIComponent(inputs.type.toLowerCase()));
    }
    if (inputs.status && inputs.status !== "Semua") {
        params.push("status=" + encodeURIComponent(inputs.status.toLowerCase()));
    }
    if (inputs.genre && inputs.genre.trim()) {
        var genres = inputs.genre.split(",");
        for (var i = 0; i < genres.length; i++) {
            params.push("genres[]=" + encodeURIComponent(genres[i].trim()));
        }
    }
    
    var url = SOURCE.baseUrl + "/daftar-komik";
    if (params.length > 0) {
        url += "?" + params.join("&");
    }
    
    renderMainUI();
    RoCatUI.addAlert("🔍 Memuat filter...", "info");
    fetchComics(url, function(comics) {
        if (comics.length === 0) {
            RoCatUI.addAlert("Tidak ada komik dengan filter tersebut.", "info");
            return;
        }
        RoCatUI.log("✅ " + comics.length + " komik ditemukan");
        RoCatUI.addGrid(2, JSON.stringify(comics), "openDetail");
    });
}

function openDetail(payload) {
    try {
        var comic = RoCat.safeParseJson(payload, {});
        if (!comic || !comic.url) {
            RoCatUI.addAlert("Item tidak valid.", "error");
            return;
        }
        
        State.currentComic = comic;
        
        fetchComicDetail(comic.url, function(detail) {
            if (!detail) {
                RoCatUI.addAlert("Gagal memuat detail.", "error");
                return;
            }
            
            // Merge with existing data
            comic = Object.assign(comic, detail);
            State.currentComic = comic;
            renderComicDetail(comic);
        });
    } catch (e) {
        RoCatUI.log("❌ openDetail: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

function openChapter(payload) {
    try {
        var chapter = RoCat.safeParseJson(payload, {});
        if (!chapter || !chapter.url) {
            RoCatUI.addAlert("Chapter tidak valid.", "error");
            return;
        }
        
        State.currentChapter = chapter;
        
        // Save progress
        if (RoCat.settings.autoSaveProgress !== false) {
            saveProgress(chapter.comicId || State.currentComic.id, chapter.number);
        }
        
        fetchChapterPages(chapter.url, function(pages) {
            if (pages.length === 0) {
                RoCatUI.addAlert("Tidak ada halaman ditemukan.", "warning");
                return;
            }
            
            State.pages = pages;
            renderReader(pages);
        });
    } catch (e) {
        RoCatUI.log("❌ openChapter: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

// ============================================
// INIT
// ============================================
// Auto-execute when script is loaded
try {
    onLaunch();
} catch (e) {
    RoCatUI.log("❌ Init error: " + e.message);
}
```

---

🎨 Desain UI yang Diperbaiki

Layout Guidelines

Komponen Margin Padding Spacing
Container 16dp 16dp -
Text 0 4dp 8dp
Button 4dp 12dp 8dp
Input 0 12dp 8dp
Grid Item 4dp 4dp 8dp
Alert 0 12dp 8dp
Divider 8dp 0 8dp

Color Scheme (Material Design 3)

```kotlin
val colors = mapOf(
    "primary" to "#6750A4",
    "onPrimary" to "#FFFFFF",
    "surface" to "#FDF8FD",
    "surfaceVariant" to "#E7E0EC",
    "onSurface" to "#1D1B20",
    "onSurfaceVariant" to "#49454F",
    "error" to "#BA1A1A",
    "success" to "#006E26",
    "warning" to "#B47109"
)
```

---

🔧 API Baru yang Dibutuhkan

RoCatUI Layout Improvements

```javascript
// Tambahan parameter untuk layout
{
    type: "layout",
    layout: "row",
    padding: 8,        // Padding internal
    margin: 4,         // Margin external
    spacing: 8,        // Spacing antar children
    align: "center",   // Alignment: start, center, end
    children: [...]
}

// Grid dengan spacing yang lebih baik
{
    type: "grid",
    columns: 3,
    spacing: 8,        // Spacing antar item
    padding: 4,        // Padding internal
    items: [...]
}
```

Storage API

```javascript
// Penyimpanan data per-skrip (persistent)
RoCat.storage.set(key, value);  // Simpan data
RoCat.storage.get(key);         // Ambil data
RoCat.storage.remove(key);      // Hapus data
RoCat.storage.clear();          // Hapus semua data

// Contoh penggunaan untuk bookmark
RoCat.storage.set("bookmarks", JSON.stringify(bookmarks));
var bookmarks = JSON.parse(RoCat.storage.get("bookmarks") || "[]");
```

---

✅ Checklist Implementasi

Backend (Kotlin/Android)

· Perbaiki margin & padding di semua komponen UI
· Tambahkan RoCat.storage API untuk penyimpanan data
· Implementasikan layout dengan spacing yang konsisten
· Tambahkan dukungan untuk alignment di layout
· Perbaiki grid spacing

Frontend (Template)

· Buat template komik reader yang lengkap
· Implementasikan bookmark & reading progress
· Tambahkan webtoon mode
· Support multiple reading modes
· Responsive grid untuk semua ukuran layar

Testing

· Test dengan CGBUM dan sumber komik lainnya
· Test kompatibilitas dengan skrip lama
· Test bookmark & progress persistence
· Test reading modes

---

📝 Catatan untuk AI Agent

1. Prioritas Utama:
   · Perbaiki UI agar rapi dan konsisten
   · Buat template komik reader yang solid
   · Jaga kompatibilitas dengan skrip lama
2. Referensi Utama:
   · Mihon app untuk UI/UX komik reader
   · DOCS_SCRIPTING.md untuk API yang tersedia
   · Skrip CGBUM yang sudah ada untuk referensi implementasi
3. Constraint:
   · Tidak boleh merusak skrip yang sudah ada
   · Harus kompatibel dengan Rhino 1.7.15
   · Tidak boleh menggunakan async/await atau class
4. Output yang Diharapkan:
   · Template skrip komik reader yang lengkap
   · Dokumentasi API baru untuk storage & layout
   · Skrip CGBUM yang sudah diperbaiki dengan template baru
   · UI yang konsisten dengan Mihon