// ==UserScript==
// @name         XVideos Camera Scraper
// @namespace    rocat.xvideos
// @version      3.0.0
// @description  XVideos scraper (Tahap 23): home, search, detail with badges, HLS via html5player + headless fallback
// @author       RoCat User
// @category     Anime
// @match        https://www.xvideos.com/*
// @icon         https://www.xvideos.com/favicon.ico
// ==/UserScript==

var BASE_URL = "https://www.xvideos.com";

// ===== UTILITY FUNCTIONS =====

function readInput(inputs, id) {
    if (inputs === null || inputs === undefined) return "";
    if (typeof inputs.get === "function") {
        var v = inputs.get(id);
        return v === null || v === undefined ? "" : String(v);
    }
    var direct = inputs[id];
    return direct === null || direct === undefined ? "" : String(direct);
}

/** Normalizes a card title: strips [tag] markers and collapses double spaces. */
function normalizeTitle(t) {
    return String(t || "").replace(/\[.*?\]/g, " ").replace(/\s+/g, " ").trim();
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

// ===== VIDEO CARD PARSING =====

function parseVideoCards(html) {
    var doc = RoCatDOM.parse(html);
    var cards = doc.find("div.thumb-block");
    var items = [];
    var seen = {};
    for (var i = 0; i < cards.length; i++) {
        var card = cards[i];
        var a = card.find("a[href]");
        if (a.length === 0) continue;
        var href = resolveUrl(BASE_URL, a[0].attr("href"));
        if (!href || seen[href]) continue;
        seen[href] = true;
        var title = normalizeTitle(card.textOf("p.title a") || card.textOf(".title a") || a[0].text);
        var image = card.attrOf("img", "src") || card.attrOf("img", "data-src") || "";
        items.push({ title: title, image: image, url: href });
    }
    return items;
}

// ===== HLS MASTER PLAYLIST =====

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

function variantScore(attrs) {
    var res = /RESOLUTION=\d+x(\d+)/.exec(attrs);
    if (res) return parseInt(res[1], 10) * 1000;
    var bw = /BANDWIDTH=(\d+)/.exec(attrs);
    if (bw) return parseInt(bw[1], 10);
    return 0;
}

function bestLabel(attrs) {
    var res = /RESOLUTION=\d+x(\d+)/.exec(attrs);
    if (res) return res[1] + "p";
    var bw = /BANDWIDTH=(\d+)/.exec(attrs);
    if (bw) return Math.round(parseInt(bw[1], 10) / 1000) + "K";
    return "HLS";
}

// ===== HTML5PLAYER EXTRACTION =====

/**
 * Returns the raw JS of the html5player <script> (or ""). Reads `innerHtml` because
 * Jsoup treats <script> content as CDATA and `text()` always returns "".
 */
function extractPlayerScript(html) {
    var doc = RoCatDOM.parse(html);
    var node = doc.find("script:containsData(html5player.setVideoUrlLow)");
    if (node.length === 0) node = doc.find("script:containsData(setVideoHLS)");
    if (node.length === 0) node = doc.find("script:containsData(html5player)");
    if (node.length > 0) {
        var inner = node[0].innerHtml;
        if (inner) return inner;
    }
    var scripts = doc.find("script");
    for (var i = 0; i < scripts.length; i++) {
        var s = scripts[i].innerHtml;
        if (s.indexOf("html5player") >= 0) return s;
    }
    return "";
}

/** Extracts the URL passed to a setter like setVideoUrlLow('...') / setVideoHLS('...'). */
function extractVideoUrl(script, fnName) {
    if (!script) return "";
    var re = new RegExp(fnName + "\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");
    var m = re.exec(script);
    return m ? m[1] : "";
}

// ===== CANVAS FLOW =====

function onLaunch() {
    RoCat.render([
        { type: "clear" },
        { type: "input", id: "query", hint: "Kata kunci pencarian..." },
        { type: "button", label: "🔍 Cari", fn: "doSearch" }
    ]);
    var html = fetchText(BASE_URL + "/");
    if (!html) {
        RoCatUI.addAlert("Gagal memuat halaman utama.", "error");
        return;
    }
    var items = parseVideoCards(html);
    if (items.length) {
        RoCatUI.addGrid(3, JSON.stringify(items), "openDetail");
    } else {
        RoCatUI.addAlert("Tidak ada video di halaman utama.", "info");
    }
}

function doSearch(inputs) {
    var q = readInput(inputs, "query").trim();
    if (!q) {
        RoCatUI.addAlert("Kata kunci pencarian kosong.", "warning");
        return;
    }
    var html = fetchText(BASE_URL + "/?k=" + encodeURIComponent(q));
    if (!html) {
        RoCatUI.addAlert("Pencarian gagal dimuat.", "error");
        return;
    }
    var items = parseVideoCards(html);
    if (items.length) {
        RoCatUI.addGrid(3, JSON.stringify(items), "openDetail");
    } else {
        RoCatUI.addAlert("Tidak ada hasil untuk \"" + q + "\".", "info");
    }
}

function openDetail(payloadStr) {
    var item = RoCat.safeParseJson(payloadStr, {});
    if (!item || !item.url) {
        RoCatUI.addAlert("Payload item tidak valid.", "error");
        return;
    }
    var html = fetchText(item.url);
    if (!html) {
        RoCatUI.addAlert("Gagal memuat halaman detail.", "error");
        return;
    }
    var doc = RoCatDOM.parse(html);
    var title = doc.attrOf("meta[property='og:title']", "content") || item.title || "";
    var cover = doc.attrOf("meta[property='og:image']", "content") || item.image || "";
    if (cover) {
        RoCat.render({ type: "image", url: cover, title: title, download: true });
    }
    var keywords = doc.textsOf("div.video-metadata ul li a.is-keyword");
    if (keywords.length) {
        RoCatUI.addBadgeGroup(JSON.stringify(keywords));
    }
    RoCatUI.addButton("▶️ Putar Video", "openVideo");
}

function openVideo(payloadStr) {
    var item = RoCat.safeParseJson(payloadStr, {});
    if (!item || !item.url) {
        RoCatUI.addAlert("Payload video tidak valid.", "error");
        return;
    }
    var html = fetchText(item.url);
    if (!html) {
        RoCatUI.addAlert("Gagal memuat halaman video.", "error");
        return;
    }
    var playerScript = extractPlayerScript(html);
    if (playerScript) {
        var low = extractVideoUrl(playerScript, "setVideoUrlLow");
        var high = extractVideoUrl(playerScript, "setVideoUrlHigh");
        var hls = extractVideoUrl(playerScript, "setVideoHLS");
        var sources = [];
        if (low) sources.push({ quality: "SD", url: low, type: "MP4" });
        if (high) sources.push({ quality: "HD", url: high, type: "MP4" });
        if (hls) sources.push({ quality: "HLS", url: hls, type: "HLS" });
        if (sources.length) {
            RoCatUI.addJsonLog(JSON.stringify(sources), "Kualitas tersedia", true);
        }
        if (hls) {
            var best = pickBestVariant(hls);
            if (best && best.url) {
                RoCatUI.addVideo(best.url, item.title + " · " + bestLabel(best.attrs), true, true);
            } else {
                RoCatUI.addVideo(hls, item.title + " · HLS", true, true);
            }
        } else if (sources.length) {
            RoCatUI.addVideo(sources[0].url, item.title + " · " + sources[0].quality, false, true);
        } else {
            RoCatUI.addAlert("Tidak ada sumber video ditemukan.", "error");
        }
        return;
    }

    // html5player is JS-generated — invisible to fetch()+Jsoup. Fall back to the
    // headless WebView (RoCatPage) to let the live page render the player.
    if (typeof RoCatPage === "undefined" || RoCatPage === null) {
        RoCatUI.addAlert("Tidak dapat mengekstrak sumber video.", "error");
        return;
    }
    RoCatUI.addAlert("Player dibuat via JavaScript; membuka di WebView headless.", "warning");
    RoCatPage.open(item.url, 20000);
    var player = RoCatPage.evaluate("(function(){ try { var u = {}; if (window.html5player) { u.low = html5player.videoUrlLow || ''; u.high = html5player.videoUrlHigh || ''; u.hls = html5player.videoHLS || ''; } if (html5player.setVideoUrlLow) {} return JSON.stringify(u); } catch (e) { return '{}'; } })()");
    var low = (player && player.low) ? player.low : "";
    var high = (player && player.high) ? player.high : "";
    var hls = (player && player.hls) ? player.hls : "";
    var sources = [];
    if (low) sources.push({ quality: "SD", url: low, type: "MP4" });
    if (high) sources.push({ quality: "HD", url: high, type: "MP4" });
    if (hls) sources.push({ quality: "HLS", url: hls, type: "HLS" });
    if (sources.length) {
        RoCatUI.addJsonLog(JSON.stringify(sources), "Mode Interaktif", true);
    }
    if (hls) {
        RoCatUI.addVideo(hls, item.title + " · HLS", true, true);
    } else if (sources.length) {
        RoCatUI.addVideo(sources[0].url, item.title + " · " + sources[0].quality, false, true);
    } else {
        RoCatUI.addAlert("WebView tidak menemukan player.", "error");
    }
    RoCatPage.close();
}