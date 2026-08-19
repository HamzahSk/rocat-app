// ==UserScript==
// @name         CGBUM Reader
// @version      1.1.0
// @description  Baca komik dari CGBUM (manga, manhwa, manhua) dengan dukungan pencarian, filter, dan pembaca gambar.
// @author       RoCat AI
// @category     Komik
// @icon         https://cgbum.xyz/favicon.ico
// @match        https://cgbum.xyz/*
// @include      https://*.cgbum.xyz/*
// @settings     quality: select: default=50, options=30,50,70,90, label=Kualitas Gambar (WP Proxy)
// @settings     useWpProxy: boolean: default=true, label=Gunakan WordPress Proxy (hemat kuota)
// @settings     proxyUrl: string: default=, label=Custom Proxy URL (kosongkan jika tidak pakai)
// @settings     readingMode: select: options=single,scroll,webtoon, default=single, label=Mode Baca
// @settings     autoSaveProgress: boolean: default=true, label=Simpan Progress Otomatis
// ==/UserScript==

var BASE_URL = "https://cgbum.com";

// --- State management ---
var _state = {
    lastDetailUrl: "",
    lastChapterUrl: "",
    chapterPages: [],
    chapterTitle: ""
};

function saveReadingProgress(chapter) {
    if (RoCat.settings.autoSaveProgress === false || !chapter) return;
    RoCat.storage.set("progress:last", JSON.stringify({ title: chapter.title || "Chapter", url: chapter.url || "" }));
}

// --- String helper ---
function substringAfter(str, prefix) {
    if (!str || !prefix) return str || "";
    var idx = str.indexOf(prefix);
    if (idx === -1) return str;
    return str.substring(idx + prefix.length);
}

// --- Process image URL with proxy ---
function processImageUrl(url, useWpProxy, quality, proxyUrl) {
    if (!url) return "";

    if (useWpProxy) {
        var urlWithoutScheme = url.replace(/^https?:\/\//, "");
        return "https://i0.wp.com/" + urlWithoutScheme + "?quality=" + (quality || "50");
    }

    if (proxyUrl && proxyUrl.trim()) {
        var pUrl = proxyUrl.trim();
        if (pUrl.indexOf("%s") !== -1) {
            return pUrl.replace(/%s/g, url);
        }
        return pUrl + url;
    }

    return url;
}

// --- Parse comic grid from HTML ---
function parseComicGrid(html) {
    try {
        var root = RoCatDOM.parse(html);
        var cards = root.find(".comic-grid .comic-card");
        var out = [];

        for (var i = 0; i < cards.length; i++) {
            var el = cards[i];
            var titleEl = el.find(".comic-card-title a");
            var url = "";
            var title = "";

            if (titleEl.length > 0) {
                title = titleEl[0].text.trim() || "Tanpa Judul";
                url = titleEl[0].attr("href") || "";
            }

            if (!url) {
                var aEl = el.find("a");
                if (aEl.length > 0) {
                    url = aEl[0].attr("href") || "";
                }
            }

            var cover = el.attrOf(".comic-card-cover img", "abs:src") || 
                       el.attrOf(".comic-card-cover img", "src") || "";

            out.push({
                title: title,
                image: cover,
                url: substringAfter(url, BASE_URL)
            });
        }

        return out;
    } catch (e) {
        RoCatUI.log("❌ parseComicGrid: " + e.message);
        return [];
    }
}

// --- Render UI utama ---
function renderMainUI() {
    RoCat.render([
        { type: "clear" },
        { type: "text", content: "📚 CGBUM Reader", style: "title" },
        { type: "layout", layout: "row", padding: 8, margin: 16, spacing: 8, align: "center", children: [
            { type: "autocomplete", id: "keyword", hint: "Cari komik...", historyKey: "cgbum-search", flex: 3 },
            { type: "button", label: "🔍 Cari", fn: "doSearch", flex: 1 }
        ]},
        { type: "layout", layout: "row", margin: 16, spacing: 8, align: "center", children: [
            { type: "button", label: "📖 Populer", fn: "loadPopular", flex: 1 },
            { type: "button", label: "🆕 Terbaru", fn: "loadLatest", flex: 1 },
            { type: "button", label: "🔧 Filter", fn: "showFilters", flex: 1 }
        ]},
        { type: "divider" },
        { type: "alert", message: "Pilih komik di bawah atau cari judul favoritmu!", level: "info" }
    ]);
}

// --- Lifecycle: dipanggil otomatis saat kanvas dibuka ---
function onLaunch() {
    try {
        // Reset state
        _state.lastDetailUrl = "";
        _state.lastChapterUrl = "";
        _state.chapterPages = [];
        _state.chapterTitle = "";

        // Render UI utama
        renderMainUI();

        // Muat komik populer sebagai default
        loadPopular();
    } catch (e) {
        RoCatUI.log("❌ onLaunch: " + e.message);
    }
}

// --- Load komik populer ---
function loadPopular() {
    try {
        // Hapus hanya grid dan alert, pertahankan UI utama
        // Cara: render ulang UI utama, tapi tanpa alert
        renderMainUI();
        
        // Tampilkan alert loading
        RoCatUI.addAlert("📖 Memuat komik populer...", "info");

        var res = fetch(BASE_URL + "/daftar-komik", "GET", {}, null);
        if (!res.ok) {
            RoCatUI.addAlert("Gagal memuat populer (" + res.status + ")", "error");
            return;
        }

        var items = parseComicGrid(res.text());
        if (items.length === 0) {
            RoCatUI.addAlert("Tidak ada komik ditemukan.", "warning");
            return;
        }

        RoCatUI.log("✅ Ditemukan " + items.length + " komik populer");
        RoCatUI.addGrid(3, JSON.stringify(items), "openDetail");
    } catch (e) {
        RoCatUI.log("❌ loadPopular: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

// --- Load komik terbaru ---
function loadLatest() {
    try {
        renderMainUI();
        RoCatUI.addAlert("🆕 Memuat komik terbaru...", "info");

        var res = fetch(BASE_URL + "/last-update", "GET", {}, null);
        if (!res.ok) {
            RoCatUI.addAlert("Gagal memuat terbaru (" + res.status + ")", "error");
            return;
        }

        var items = parseComicGrid(res.text());
        if (items.length === 0) {
            RoCatUI.addAlert("Tidak ada komik terbaru.", "warning");
            return;
        }

        RoCatUI.log("✅ Ditemukan " + items.length + " komik terbaru");
        RoCatUI.addGrid(3, JSON.stringify(items), "openDetail");
    } catch (e) {
        RoCatUI.log("❌ loadLatest: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

// --- Pencarian komik ---
function doSearch(inputs) {
    try {
        var q = (inputs && inputs.keyword || "").trim();
        if (q === "") {
            RoCatUI.addAlert("Masukkan kata kunci.", "warning");
            return;
        }

        renderMainUI();
        RoCatUI.addAlert("🔍 Mencari \"" + q + "\"...", "info");

        // Coba API search dulu (JSON)
        var searchUrl = BASE_URL + "/search-suggest.php?q=" + encodeURIComponent(q);
        var res = fetch(searchUrl, "GET", {}, null);

        if (res.ok && res.body && res.body.trim().startsWith("[")) {
            try {
                var results = JSON.parse(res.body);
                if (results && results.length > 0) {
                    var items = [];
                    for (var i = 0; i < results.length; i++) {
                        var dto = results[i];
                        items.push({
                            title: dto.title || "Tanpa Judul",
                            image: dto.cover || "",
                            url: substringAfter(dto.url || "", BASE_URL) || "/komik/" + (dto.slug || ""),
                            slug: dto.slug || ""
                        });
                    }
                    RoCatUI.log("✅ Ditemukan " + items.length + " hasil dari search API");
                    RoCatUI.addGrid(2, JSON.stringify(items), "openDetail");
                    return;
                }
            } catch (e) {
                // JSON parse gagal, fallback ke HTML
            }
        }

        // Fallback: coba search via HTML
        var htmlRes = fetch(BASE_URL + "/daftar-komik?keyword=" + encodeURIComponent(q), "GET", {}, null);
        if (!htmlRes.ok) {
            RoCatUI.addAlert("Pencarian gagal (" + htmlRes.status + ")", "error");
            return;
        }

        var items = parseComicGrid(htmlRes.text());
        if (items.length === 0) {
            RoCatUI.addAlert("Tidak ada hasil untuk \"" + q + "\"", "info");
            return;
        }

        RoCatUI.log("✅ Ditemukan " + items.length + " hasil");
        RoCatUI.addGrid(2, JSON.stringify(items), "openDetail");
    } catch (e) {
        RoCatUI.log("❌ doSearch: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

// --- Tampilkan filter ---
function showFilters() {
    try {
        RoCat.render([
            { type: "clear" },
            { type: "text", content: "🔧 Filter Komik", style: "title" },
            { type: "button", label: "🏠 Kembali", fn: "onLaunch" },
            { type: "dropdown", id: "type", label: "Tipe", options: ["Semua", "Manga", "Manhwa", "Manhua", "Pornhwa"], default: "Semua" },
            { type: "dropdown", id: "status", label: "Status", options: ["Semua", "Ongoing", "Tamat"], default: "Semua" },
            { type: "autocomplete", id: "genre", hint: "Genre (pisahkan dengan koma)...", historyKey: "cgbum-genres" },
            { type: "button", label: "🔍 Terapkan Filter", fn: "applyFilters" }
        ]);
    } catch (e) {
        RoCatUI.log("❌ showFilters: " + e.message);
    }
}

// --- Terapkan filter ---
function applyFilters(inputs) {
    try {
        var type = (inputs && inputs.type || "Semua").toLowerCase();
        var status = (inputs && inputs.status || "Semua").toLowerCase();
        var genreInput = (inputs && inputs.genre || "").trim();

        var params = [];
        if (type !== "semua") params.push("type=" + encodeURIComponent(type));
        if (status !== "semua") params.push("status=" + encodeURIComponent(status));

        if (genreInput) {
            var genres = genreInput.split(",");
            for (var i = 0; i < genres.length; i++) {
                var g = genres[i].trim();
                if (g) {
                    params.push("genres[]=" + encodeURIComponent(g));
                }
            }
        }

        var url = BASE_URL + "/daftar-komik";
        if (params.length > 0) {
            url += "?" + params.join("&");
        }

        // Kembali ke home dengan filter
        renderMainUI();
        RoCatUI.addAlert("🔍 Memuat dengan filter...", "info");

        var res = fetch(url, "GET", {}, null);
        if (!res.ok) {
            RoCatUI.addAlert("Gagal memuat (" + res.status + ")", "error");
            return;
        }

        var items = parseComicGrid(res.text());
        if (items.length === 0) {
            RoCatUI.addAlert("Tidak ada komik dengan filter tersebut.", "info");
            return;
        }

        RoCatUI.log("✅ Ditemukan " + items.length + " komik");
        RoCatUI.addGrid(2, JSON.stringify(items), "openDetail");
    } catch (e) {
        RoCatUI.log("❌ applyFilters: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

// --- Buka detail komik ---
function openDetail(payload) {
    try {
        var item = RoCat.safeParseJson(payload, {});
        if (!item || !item.url) {
            RoCatUI.addAlert("Item tidak valid.", "error");
            return;
        }

        // Simpan URL detail untuk navigasi
        _state.lastDetailUrl = item.url;

        var url = item.url;
        if (url.indexOf("http") !== 0) {
            url = BASE_URL + url;
        }

        RoCatUI.clear();
        RoCatUI.render({type:"layout",layout:"row",margin:8,spacing:8,children:[{type:"button",label:"🏠 Home",fn:"onLaunch",flex:1},{type:"button",label:"🔙 Kembali",fn:"loadPopular",flex:1}]});
        RoCatUI.log("⏳ Memuat detail: " + (item.title || "Komik"));

        var res = fetch(url, "GET", {}, null);
        if (!res.ok) {
            RoCatUI.addAlert("Gagal memuat detail (" + res.status + ")", "error");
            return;
        }

        var doc = RoCatDOM.parse(res.text());
        var container = doc.find(".comic-detail");
        
        if (container.length === 0) {
            RoCatUI.addAlert("Tidak dapat menemukan detail komik.", "error");
            RoCatUI.addHtmlPreview(res.text().substring(0, 500), "HTML mentah (500 char)");
            return;
        }
        
        var detailEl = container[0];

        // Ambil info detail
        var title = detailEl.textOf(".comic-info h1") || item.title || "Tanpa Judul";
        var cover = detailEl.attrOf(".comic-cover img", "abs:src") || item.image || "";
        var status = detailEl.textOf(".badge-status") || "";
        var type = detailEl.textOf(".badge-type-text") || "";

        // Genre
        var genreElements = detailEl.find(".comic-genres .genre-pill");
        var genres = [];
        for (var i = 0; i < genreElements.length; i++) {
            var g = genreElements[i].text.trim();
            if (g) genres.push(g);
        }

        // Author
        var author = "";
        var metaRows = detailEl.find(".comic-meta-simple .meta-row");
        for (var i = 0; i < metaRows.length; i++) {
            var row = metaRows[i];
            var label = row.textOf(".meta-label").toLowerCase().trim();
            if (label.indexOf("author") !== -1) {
                author = row.textOf(".meta-value") || "";
                break;
            }
        }

        var synopsis = detailEl.textOf(".synopsis-content") || "";

        // Tampilkan info
        RoCat.render([
            { type: "image", url: cover, title: title, download: true },
            { type: "text", content: "📖 " + title, style: "heading" },
            { type: "badges", badges: genres.length > 0 ? [status, type].concat(genres.slice(0, 5)) : [status, type] },
            { type: "text", content: "✍️ Author: " + (author || "Tidak diketahui"), style: "body" }
        ]);

        if (synopsis) {
            RoCatUI.addHtmlPreview(synopsis, "📝 Sinopsis");
        }

        // Ambil daftar chapter
        var chapters = detailEl.find(".chapter-grid .ch-grid-item");
        var chapterList = [];
        for (var i = 0; i < chapters.length; i++) {
            var ch = chapters[i];
            var chUrl = ch.attr("href") || "";
            var chTitle = ch.attr("title") || "";
            var chNum = ch.attr("data-chapter") || "";

            chapterList.push({
                title: chTitle || "Chapter " + chNum,
                url: substringAfter(chUrl, BASE_URL),
                chapter: chNum,
                index: i
            });
        }

        if (chapterList.length === 0) {
            RoCatUI.addAlert("Tidak ada chapter tersedia.", "warning");
            return;
        }

        RoCatUI.log("📑 Ditemukan " + chapterList.length + " chapter");
        RoCatUI.addGrid(4, JSON.stringify(chapterList), "openChapter");
    } catch (e) {
        RoCatUI.log("❌ openDetail: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

// --- Buka chapter dan tampilkan halaman ---
function openChapter(payload) {
    try {
        var chapter = RoCat.safeParseJson(payload, {});
        if (!chapter || !chapter.url) {
            RoCatUI.addAlert("Chapter tidak valid.", "error");
            return;
        }

        // Simpan URL chapter
        _state.lastChapterUrl = chapter.url;
        _state.chapterTitle = chapter.title || "Chapter";
        saveReadingProgress(chapter);

        var url = chapter.url;
        if (url.indexOf("http") !== 0) {
            url = BASE_URL + url;
        }

        RoCatUI.clear();
        RoCatUI.render({type:"layout",layout:"row",margin:8,spacing:8,children:[{type:"button",label:"🏠 Home",fn:"onLaunch",flex:1},{type:"button",label:"🔙 Detail",fn:"backToDetail",flex:1}]});
        function backToDetail() {
            if (_state.lastDetailUrl) {
                var item = { url: _state.lastDetailUrl };
                openDetail(JSON.stringify(item));
            } else {
                loadPopular();
            }
        }
        RoCatUI.log("⏳ Membuka chapter: " + (chapter.title || "Chapter"));

        var res = fetch(url, "GET", {}, null);
        if (!res.ok) {
            RoCatUI.addAlert("Gagal memuat chapter (" + res.status + ")", "error");
            return;
        }

        var doc = RoCatDOM.parse(res.text());
        var pages = doc.find(".reader-images .page-container");

        if (pages.length === 0) {
            RoCatUI.addAlert("Tidak ada halaman di chapter ini.", "warning");
            return;
        }

        // Kumpulkan URL gambar
        var imageUrls = [];
        for (var i = 0; i < pages.length; i++) {
            var imgUrl = pages[i].attr("data-url") || "";
            if (!imgUrl) {
                imgUrl = pages[i].attr("abs:data-url") || "";
            }
            if (imgUrl) {
                imageUrls.push(imgUrl);
            }
        }

        if (imageUrls.length === 0) {
            RoCatUI.addAlert("Tidak ada gambar ditemukan.", "warning");
            return;
        }

        // Simpan halaman ke state
        _state.chapterPages = imageUrls;

        RoCatUI.log("📄 Memuat " + imageUrls.length + " halaman");

        // Siapkan header untuk request gambar
        var headers = {
            "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            "DNT": "1",
            "Referer": BASE_URL + "/"
        };

        // Gunakan pengaturan dari RoCat.settings
        var useWpProxy = RoCat.settings.useWpProxy;
        if (useWpProxy === undefined || useWpProxy === null) useWpProxy = true;

        var quality = RoCat.settings.quality || "50";
        var proxyUrl = RoCat.settings.proxyUrl || "";

        var readingMode = RoCat.settings.readingMode || "single";
        if (readingMode === "scroll" || readingMode === "webtoon") {
            for (var pageIndex = 0; pageIndex < imageUrls.length; pageIndex++) {
                var pageUrl = processImageUrl(imageUrls[pageIndex], useWpProxy, quality, proxyUrl);
                RoCatUI.addImage(pageUrl, readingMode === "webtoon" ? "" : "📄 " + (pageIndex + 1) + " / " + imageUrls.length, readingMode !== "webtoon", headers, readingMode === "webtoon");
            }
            RoCatUI.addAlert("✅ " + imageUrls.length + " halaman dimuat dalam mode " + readingMode, "success");
            return;
        }

        var firstImage = imageUrls[0];
        if (firstImage) RoCatUI.addImage(processImageUrl(firstImage, useWpProxy, quality, proxyUrl), "📄 Halaman 1 / " + imageUrls.length, true, headers);

        // Tampilkan grid mini dari semua halaman (thumbnail)
        var thumbItems = [];
        var maxThumbs = Math.min(imageUrls.length, 50);
        for (var i = 0; i < maxThumbs; i++) {
            var thumbUrl = processImageUrl(imageUrls[i], useWpProxy, quality, proxyUrl);
            thumbItems.push({
                title: "Halaman " + (i + 1),
                image: thumbUrl,
                url: imageUrls[i],
                index: i
            });
        }

        RoCatUI.addGrid(5, JSON.stringify(thumbItems), "viewPage");

        // Tambahkan tombol navigasi
        RoCatUI.addButton("📖 Baca Semua", "readAllPages");
        RoCatUI.addAlert("✅ " + imageUrls.length + " halaman dimuat", "success");

    } catch (e) {
        RoCatUI.log("❌ openChapter: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

// --- Lihat halaman individual ---
function viewPage(payload) {
    try {
        var page = RoCat.safeParseJson(payload, {});
        if (!page || !page.url) {
            RoCatUI.addAlert("Halaman tidak valid.", "error");
            return;
        }

        var useWpProxy = RoCat.settings.useWpProxy;
        if (useWpProxy === undefined || useWpProxy === null) useWpProxy = true;
        var quality = RoCat.settings.quality || "50";
        var proxyUrl = RoCat.settings.proxyUrl || "";

        var finalUrl = processImageUrl(page.url, useWpProxy, quality, proxyUrl);

        var headers = {
            "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            "DNT": "1",
            "Referer": BASE_URL + "/"
        };

        RoCatUI.clear();
        RoCatUI.addButton("🔙 Kembali ke Chapter", function() {
            if (_state.chapterPages && _state.chapterPages.length > 0) {
                var chapterData = {
                    title: _state.chapterTitle || "Chapter",
                    url: _state.lastChapterUrl || ""
                };
                openChapter(JSON.stringify(chapterData));
            }
        });
        RoCatUI.addImage(finalUrl, "📄 " + (page.title || "Halaman"), true, headers);

    } catch (e) {
        RoCatUI.log("❌ viewPage: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}

// --- Baca semua halaman (scroll mode) ---
function readAllPages() {
    try {
        if (!_state.chapterPages || _state.chapterPages.length === 0) {
            RoCatUI.addAlert("Tidak ada halaman untuk dibaca.", "warning");
            return;
        }

        var pages = _state.chapterPages;
        var useWpProxy = RoCat.settings.useWpProxy;
        if (useWpProxy === undefined || useWpProxy === null) useWpProxy = true;
        var quality = RoCat.settings.quality || "50";
        var proxyUrl = RoCat.settings.proxyUrl || "";

        var headers = {
            "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
            "DNT": "1",
            "Referer": BASE_URL + "/"
        };

        RoCatUI.clear();
        RoCatUI.addButton("🔙 Kembali ke Chapter", function() {
            if (_state.chapterPages && _state.chapterPages.length > 0) {
                var chapterData = {
                    title: _state.chapterTitle || "Chapter",
                    url: _state.lastChapterUrl || ""
                };
                openChapter(JSON.stringify(chapterData));
            }
        });
        RoCatUI.addAlert("📖 Membaca " + pages.length + " halaman (scroll ke bawah)", "info");

        // Tampilkan semua halaman dalam satu view (scrollable)
        for (var i = 0; i < pages.length; i++) {
            var finalUrl = processImageUrl(pages[i], useWpProxy, quality, proxyUrl);
        RoCatUI.addImage(finalUrl, "📄 Halaman " + (i + 1) + "/" + pages.length, true, headers, true);
        }

        RoCatUI.addAlert("✅ Selesai memuat semua halaman", "success");

    } catch (e) {
        RoCatUI.log("❌ readAllPages: " + e.message);
        RoCatUI.addAlert("Error: " + e.message, "error");
    }
}
