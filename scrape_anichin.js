// ==UserScript==
// @name         Anichin Scraper
// @namespace    rocat.anichin
// @version      1.0.0
// @description  Anichin (anichin.cafe) scraper end-to-end: Home -> Search -> Detail -> Episode HLS (Tahap 19)
// @author       RoCat User
// @category     Anime
// @match        https://anichin.cafe/*
// @icon         https://anichin.cafe/favicon.ico
// ==/UserScript==

var BASE_URL = "https://anichin.cafe";
var STREAM_HOST = "anichin.stream";

// ===== UTILITY FUNCTIONS =====

/** Reads a value from an inputs object that may be a plain map or a { get } wrapper. */
function readInput(inputs, id) {
    if (inputs === null || inputs === undefined) return "";
    if (typeof inputs.get === "function") {
        var v = inputs.get(id);
        return v === null || v === undefined ? "" : String(v);
    }
    var direct = inputs[id];
    return direct === null || direct === undefined ? "" : String(direct);
}

function normalizeTitle(t) {
    return String(t || "").replace(/\[.*?\]/g, "").replace(/\s+/g, " ").trim();
}

function resolveUrl(base, u) {
    if (!u) return "";
    if (/^https?:\/\//i.test(u)) return u;
    if (u.charAt(0) === "/") return base + u;
    return base + "/" + u;
}

function fetchText(url) {
    try {
        var res = fetch(url);
        if (!res || !res.ok) return "";
        return String(res.body || "");
    } catch (e) {
        return "";
    }
}

// ===== BASE64 =====

var B64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

/** Pure-JS base64 decoder (fallback when the native RoCatUI.decodeBase64 is absent). */
function b64Decode(input) {
    var s = String(input || "").replace(/\s+/g, "");
    if (s.length === 0) return "";
    var out = "";
    var buffer = 0;
    var bits = 0;
    for (var i = 0; i < s.length; i++) {
        var c = s.charAt(i);
        if (c === "=") break;
        var val = B64_CHARS.indexOf(c);
        if (val < 0) continue;
        buffer = (buffer << 6) | val;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out += String.fromCharCode((buffer >> bits) & 0xFF);
        }
    }
    return out;
}

/**
 * Decodes a base64 string. Prefers the native bridge (Android android.util.Base64,
 * Tahap 20.1) which is far faster on large iframe blobs; falls back to pure JS.
 */
function decodeBase64(input) {
    if (typeof RoCatUI !== "undefined" && RoCatUI !== null && RoCatUI.decodeBase64) {
        var native = RoCatUI.decodeBase64(String(input || ""));
        if (native) return native;
    }
    return b64Decode(input);
}

// ===== ANIME CARD PARSING =====

function parseAnimeCards(html) {
    var doc = RoCatDOM.parse(html);
    var cards = doc.find(".bixbox article .bsx");
    var items = [];
    var seen = {};
    for (var i = 0; i < cards.length; i++) {
        var card = cards[i];
        var a = card.find("a[href]");
        if (a.length === 0) continue;
        var href = resolveUrl(BASE_URL, a[0].attr("href"));
        if (!href || seen[href]) continue;
        seen[href] = true;
        var title = normalizeTitle(card.textOf(".tt h2") || card.textOf(".tt") || a[0].text);
        var image = card.attrOf("img", "src") || "";
        items.push({ title: title, image: image, url: href });
    }
    return items;
}

// ===== HLS MASTER PLAYLIST =====

/**
 * Parses an HLS master playlist and returns the best VALID variant (an entry whose
 * #EXT-X-STREAM-INF is immediately followed by a URI). Malformed variants (STREAM-INF
 * with no URI — e.g. the 1080p line on anichin.stream) are discarded.
 */
function pickBestVariant(masterUrl) {
    var body = fetchText(masterUrl);
    if (!body) return null;
    var lines = body.split("\n");
    var pending = null;
    var entries = [];
    for (var i = 0; i < lines.length; i++) {
        var line = lines[i].trim();
        if (line === "") continue;
        if (line.indexOf("#EXT-X-STREAM-INF:") === 0) {
            pending = { attrs: line.substring(line.indexOf(":") + 1), url: "" };
        } else if (pending !== null) {
            if (line.charAt(0) !== "#") {
                pending.url = resolveUrl(masterUrl, line);
                entries.push(pending);
            }
            pending = null;
        }
    }
    var best = null;
    var bestScore = -1;
    for (var j = 0; j < entries.length; j++) {
        var e = entries[j];
        if (!e.url) continue;
        var score = variantScore(e.attrs);
        if (score > bestScore) {
            bestScore = score;
            best = e;
        }
    }
    return best;
}

/** Scores a #EXT-X-STREAM-INF attribute string: RESOLUTION height, fallback BANDWIDTH. */
function variantScore(attrs) {
    var res = /RESOLUTION=(\d+)x(\d+)/.exec(attrs);
    if (res) return parseInt(res[2], 10);
    var bw = /BANDWIDTH=(\d+)/.exec(attrs);
    if (bw) return parseInt(bw[1], 10);
    return 0;
}

// ===== CANVAS FLOW =====

function onLaunch() {
    RoCatUI.clear();
    RoCatUI.addInput("query", "Cari judul anime...");
    RoCatUI.addButton("🔍 Cari", "doSearch");
    var html = fetchText(BASE_URL + "/");
    if (!html) {
        RoCatUI.log("Gagal memuat halaman utama anichin.cafe");
        return;
    }
    var items = parseAnimeCards(html);
    if (items.length) {
        RoCatUI.addGrid(3, JSON.stringify(items), "openDetail");
    } else {
        RoCatUI.log("Tidak ada rilisan terbaru ditemukan.");
    }
}

function doSearch(inputs) {
    var q = readInput(inputs, "query").trim();
    if (!q) {
        RoCatUI.log("Query pencarian kosong.");
        return;
    }
    var html = fetchText(BASE_URL + "/page/1?s=" + encodeURIComponent(q));
    if (!html) {
        RoCatUI.log("Pencarian gagal dimuat.");
        return;
    }
    var items = parseAnimeCards(html);
    if (items.length) {
        RoCatUI.addGrid(3, JSON.stringify(items), "openDetail");
    } else {
        RoCatUI.log("Tidak ada hasil untuk \"" + q + "\".");
    }
}

function openDetail(payloadStr) {
    var item = RoCat.safeParseJson(payloadStr, {});
    if (!item || !item.url) return;
    var html = fetchText(item.url);
    if (!html) {
        RoCatUI.log("Gagal memuat detail: " + item.url);
        return;
    }
    var doc = RoCatDOM.parse(html);
    var title = doc.textOf("h1.entry-title") || item.title || "";
    var cover = doc.attrOf(".thumb img", "src") || item.image || "";
    if (cover) {
        RoCatUI.addImage(cover, title, true);
    }
    var synopsis = doc.textOf(".synp .entry-content");
    if (synopsis) {
        RoCatUI.log("Sinopsis: " + synopsis);
    }
    var eps = doc.find(".eplister ul li a[href]");
    var epItems = [];
    for (var i = 0; i < eps.length; i++) {
        var href = resolveUrl(BASE_URL, eps[i].attr("href"));
        if (!href) continue;
        var num = eps[i].textOf(".epl-num");
        epItems.push({
            title: (item.title || title) + " - Ep " + (num || (i + 1)),
            url: href,
            image: ""
        });
    }
    if (epItems.length) {
        RoCatUI.addGrid(3, JSON.stringify(epItems), "openEpisode");
    } else {
        RoCatUI.log("Tidak ada daftar episode.");
    }
}

function openEpisode(payloadStr) {
    var item = RoCat.safeParseJson(payloadStr, {});
    if (!item || !item.url) return;
    var html = fetchText(item.url);
    if (!html) {
        RoCatUI.log("Gagal memuat halaman episode: " + item.url);
        return;
    }
    var doc = RoCatDOM.parse(html);
    var opts = doc.find("select.mirror option");
    for (var i = 0; i < opts.length; i++) {
        var opt = opts[i];
        var enc = opt.attr("value");
        if (!enc) continue;
        var serverName = opt.text.trim() || ("Server " + (i + 1));
        var iframeHtml = decodeBase64(enc);
        var m = /src="([^"]+)"/.exec(iframeHtml);
        if (!m) continue;
        var src = m[1];
        if (src.indexOf(STREAM_HOST) >= 0) {
            var id = /[?&]id=([^&]+)/.exec(src);
            if (!id) continue;
            var masterUrl = "https://" + STREAM_HOST + "/hls/" + id[1] + ".m3u8";
            var best = pickBestVariant(masterUrl);
            var playUrl = (best && best.url) ? best.url : masterUrl;
            RoCatUI.addVideo(playUrl, item.title + " · " + serverName, true, true);
        } else {
            RoCatUI.log("Mirror non-HLS (" + serverName + "): " + src);
        }
    }
}