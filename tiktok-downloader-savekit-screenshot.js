// ==UserScript==
// @name         TikTok Downloader (SaveKit) - Screenshot
// @version      4.0.0
// @description  Unduh video & audio TikTok - Dengan Screenshot & Debug
// @author       RoCat AI
// @category     Downloader
// @icon         https://savekit.io/favicon.ico
// @match        https://savekit.io/*
// @settings     quality: select: default=hd, options=sd,hd, label=Kualitas Video
// @settings     autoDownload: boolean: default=false, label=Auto Unduh
// ==/UserScript==

var BASE = "https://savekit.io/id/tiktok-video-downloader";

// --- Lifecycle ---
function onLaunch() {
    try {
        RoCat.render([
            { type: "clear" },
            { type: "text", content: "🎬 TikTok Downloader", style: "heading" },
            { type: "text", content: "Unduh video MP4 & audio MP3 dari TikTok", style: "body" },
            { type: "divider" },
            { type: "layout", layout: "row", children: [
                { type: "input", id: "tiktok_url", hint: "Tempel URL TikTok...", flex: 3 },
                { type: "button", label: "🔍 Proses", fn: "processVideo", flex: 1 }
            ]},
            { type: "alert", message: "Script akan screenshot hasil setelah proses", level: "info" }
        ]);

        RoCatUI.log("✅ Siap menerima URL TikTok");
    } catch (e) {
        RoCatUI.log("❌ onLaunch: " + e.message);
    }
}

// --- Proses URL ---
function processVideo(inputs) {
    try {
        var url = (inputs && inputs.tiktok_url || "").trim();
        if (!url) {
            RoCatUI.addAlert("⚠️ Masukkan URL TikTok!", "warning");
            return;
        }

        if (!url.match(/tiktok\.com\/.*\/video\/\d+/i) && !url.match(/vt\.tiktok\.com/i)) {
            RoCatUI.addAlert("⚠️ URL TikTok tidak valid!", "warning");
            return;
        }

        RoCat.render([
            { type: "clear" },
            { type: "button", label: "← Kembali", fn: "onLaunch" },
            { type: "text", content: "📥 Memproses...", style: "heading" },
            { type: "alert", message: "Mengambil data dari SaveKit...", level: "info" }
        ]);

        RoCatUI.log("⏳ Memproses: " + url);

        // --- Buka SaveKit ---
        RoCatUI.log("📄 Membuka savekit.io...");
        page.goto(BASE, { waitUntil: "domcontentloaded", timeout: 15000 });
        page.waitForTimeout(1000);

        // --- Isi URL ---
        RoCatUI.log("✏️ Mengisi URL...");
        var inputFound = false;
        var selectors = [
            '#search-box',
            'input[placeholder*="Tempel URL"]',
            'input[type="text"][placeholder*="URL"]',
            'input[type="url"]'
        ];

        for (var i = 0; i < selectors.length; i++) {
            try {
                if (page.locator(selectors[i]).exists()) {
                    page.fill(selectors[i], url);
                    inputFound = true;
                    RoCatUI.log("✅ Input ditemukan: " + selectors[i]);
                    break;
                }
            } catch (e) {}
        }

        if (!inputFound) {
            RoCatUI.addAlert("❌ Gagal menemukan input URL", "error");
            RoCatUI.addButton("← Kembali", "onLaunch");
            return;
        }

        // --- Klik tombol Unduh ---
        RoCatUI.log("🖱️ Mengklik tombol Unduh...");
        var clicked = false;
        var clickSelectors = [
            'button[type="submit"]',
            '.search-button',
            'button[class*="search-button"]',
            'button[class*="bg-linear"]'
        ];

        for (var j = 0; j < clickSelectors.length; j++) {
            try {
                if (page.locator(clickSelectors[j]).exists()) {
                    page.click(clickSelectors[j]);
                    clicked = true;
                    RoCatUI.log("✅ Klik: " + clickSelectors[j]);
                    break;
                }
            } catch (e) {}
        }

        if (!clicked) {
            try {
                page.click('button:has-text("Unduh")');
                clicked = true;
            } catch (e) {}
        }

        if (!clicked) {
            RoCatUI.addAlert("❌ Gagal menemukan tombol Unduh", "error");
            RoCatUI.addButton("← Kembali", "onLaunch");
            return;
        }

        // --- Tunggu hasil muncul ---
        RoCatUI.log("⏳ Menunggu hasil (5 detik)...");
        page.waitForTimeout(10000);

        // --- SCREENSHOT ---
        RoCatUI.log("📸 Mengambil screenshot...");
        var screenshotPath = "";
        try {
            screenshotPath = page.screenshot();
            RoCatUI.log("✅ Screenshot tersimpan: " + screenshotPath);
        } catch (e) {
            RoCatUI.log("⚠️ Gagal screenshot: " + e.message);
        }

        // --- Ambil semua link dari halaman ---
        RoCatUI.log("🔍 Mendeteksi semua link...");
        
        var result = page.evaluate(function() {
            var data = {
                allLinks: [],
                videoLinks: [],
                audioLinks: [],
                downloadButtons: [],
                pageInfo: {
                    title: document.title,
                    url: window.location.href
                }
            };

            // 1. Ambil semua link
            var links = document.querySelectorAll('a[href]');
            for (var i = 0; i < links.length; i++) {
                var link = links[i];
                var href = link.getAttribute('href');
                var text = link.textContent || '';
                var download = link.getAttribute('download') || '';
                
                if (href) {
                    data.allLinks.push({
                        href: href,
                        text: text.trim(),
                        download: download
                    });
                    
                    // Video MP4
                    if (href.includes('.mp4') || href.includes('/video/') || 
                        text.toLowerCase().includes('mp4') || download.includes('.mp4')) {
                        data.videoLinks.push(href);
                    }
                    
                    // Audio MP3
                    if (href.includes('.mp3') || href.includes('/audio/') || 
                        text.toLowerCase().includes('mp3') || download.includes('.mp3')) {
                        data.audioLinks.push(href);
                    }
                }
            }

            // 2. Cari tombol download
            var btns = document.querySelectorAll('[class*="download"], button[class*="download"], .download-btn');
            for (var j = 0; j < btns.length; j++) {
                var btn = btns[j];
                data.downloadButtons.push({
                    text: btn.textContent || '',
                    class: btn.className || '',
                    href: btn.getAttribute('href') || btn.getAttribute('data-url') || ''
                });
            }

            // 3. Cari video player
            var video = document.querySelector('video');
            if (video && video.src) {
                data.videoLinks.push(video.src);
            }

            // 4. Cari semua teks yang mengandung MP4/MP3
            var allText = document.body ? document.body.textContent : '';
            var mp4Matches = allText.match(/[^\s]*\.mp4[^\s]*/gi) || [];
            var mp3Matches = allText.match(/[^\s]*\.mp3[^\s]*/gi) || [];
            
            for (var m = 0; m < mp4Matches.length; m++) {
                if (mp4Matches[m].startsWith('http') || mp4Matches[m].startsWith('/')) {
                    data.videoLinks.push(mp4Matches[m]);
                }
            }
            for (var n = 0; n < mp3Matches.length; n++) {
                if (mp3Matches[n].startsWith('http') || mp3Matches[n].startsWith('/')) {
                    data.audioLinks.push(mp3Matches[n]);
                }
            }

            // 5. Cari element dengan attribute data-video atau data-audio
            var allElements = document.querySelectorAll('[data-video], [data-audio], [data-url]');
            for (var k = 0; k < allElements.length; k++) {
                var el = allElements[k];
                var videoData = el.getAttribute('data-video');
                var audioData = el.getAttribute('data-audio');
                var urlData = el.getAttribute('data-url');
                
                if (videoData) data.videoLinks.push(videoData);
                if (audioData) data.audioLinks.push(audioData);
                if (urlData) {
                    if (urlData.includes('.mp4')) data.videoLinks.push(urlData);
                    if (urlData.includes('.mp3')) data.audioLinks.push(urlData);
                }
            }

            return data;
        });

        // --- Tampilkan hasil ---
        RoCatUI.clear();
        RoCatUI.addButton("← Kembali", "onLaunch");

        // --- Tampilkan Info Halaman ---
        RoCatUI.addText("📄 Informasi Halaman", "title");
        RoCatUI.addBadgeGroup(["URL: " + result.pageInfo.url, "Title: " + result.pageInfo.title]);

        // --- Tampilkan Screenshot ---
        if (screenshotPath) {
            RoCatUI.addDivider(2, "#e0e0e0");
            RoCatUI.addText("📸 Screenshot Hasil", "title");
            try {
                RoCatUI.addImage("file://" + screenshotPath, "Screenshot SaveKit", false);
                RoCatUI.log("📸 Screenshot ditampilkan");
            } catch (e) {
                RoCatUI.log("⚠️ Gagal tampilkan screenshot: " + e.message);
                RoCatUI.addAlert("Screenshot tersimpan di: " + screenshotPath, "info");
            }
        }

        // --- Tampilkan Semua Link ---
        RoCatUI.addDivider(2, "#e0e0e0");
        RoCatUI.addText("🔗 Semua Link yang Ditemukan (" + result.allLinks.length + ")", "title");
        
        if (result.allLinks.length > 0) {
            var linkText = "";
            for (var l = 0; l < Math.min(result.allLinks.length, 20); l++) {
                var link = result.allLinks[l];
                linkText += "• " + link.text + " → " + link.href + "\n";
            }
            if (result.allLinks.length > 20) {
                linkText += "... dan " + (result.allLinks.length - 20) + " link lainnya";
            }
            RoCatUI.addHtmlPreview(linkText.replace(/\n/g, "<br>"), "Daftar Link");
        }

        // --- Tampilkan Video Links ---
        if (result.videoLinks.length > 0) {
            RoCatUI.addDivider(2, "#e0e0e0");
            RoCatUI.addText("🎬 Video MP4 (" + result.videoLinks.length + ")", "title");
            
            var allMedia = [];
            
            for (var v = 0; v < result.videoLinks.length; v++) {
                var vidUrl = result.videoLinks[v];
                
                // Fix URL
                if (vidUrl.startsWith('/')) {
                    vidUrl = 'https://savekit.io' + vidUrl;
                }
                
                if (vidUrl.startsWith('http')) {
                    RoCatUI.addVideo(
                        vidUrl,
                        "Video " + (v + 1),
                        false,
                        true,
                        { "Referer": "https://savekit.io/" }
                    );
                    
                    allMedia.push({
                        url: vidUrl,
                        name: 'tiktok_video_' + (v + 1) + '.mp4',
                        type: 'video'
                    });
                    
                    RoCatUI.log("✅ Video: " + vidUrl);
                }
            }
            
            // Tombol unduh semua video
            if (allMedia.length > 0) {
                RoCatUI.addButton("📥 Unduh Semua Video (" + allMedia.length + ")", function() {
                    downloadAllMedia(allMedia);
                });
            }
        }

        // --- Tampilkan Audio Links ---
        if (result.audioLinks.length > 0) {
            RoCatUI.addDivider(2, "#e0e0e0");
            RoCatUI.addText("🎵 Audio MP3 (" + result.audioLinks.length + ")", "title");
            
            var allAudio = [];
            
            for (var a = 0; a < result.audioLinks.length; a++) {
                var audUrl = result.audioLinks[a];
                
                // Fix URL
                if (audUrl.startsWith('/')) {
                    audUrl = 'https://savekit.io' + audUrl;
                }
                
                if (audUrl.startsWith('http')) {
                    RoCatUI.addAudio(
                        audUrl,
                        "Audio " + (a + 1),
                        true,
                        { "Referer": "https://savekit.io/" }
                    );
                    
                    allAudio.push({
                        url: audUrl,
                        name: 'tiktok_audio_' + (a + 1) + '.mp3',
                        type: 'audio'
                    });
                    
                    RoCatUI.log("✅ Audio: " + audUrl);
                }
            }
            
            // Tombol unduh semua audio
            if (allAudio.length > 0) {
                RoCatUI.addButton("📥 Unduh Semua Audio (" + allAudio.length + ")", function() {
                    downloadAllMedia(allAudio);
                });
            }
        }

        // --- Jika tidak ada media ditemukan ---
        if (result.videoLinks.length === 0 && result.audioLinks.length === 0) {
            RoCatUI.addAlert("⚠️ Tidak ditemukan video/audio. Coba periksa screenshot.", "warning");
            
            // Tampilkan HTML untuk debug
            try {
                var html = page.content();
                RoCatUI.addJsonLog({
                    url: result.pageInfo.url,
                    htmlLength: html.length,
                    linksFound: result.allLinks.length,
                    videoLinks: result.videoLinks.length,
                    audioLinks: result.audioLinks.length
                }, "Debug Info", true);
            } catch (e) {}
        }

        // --- Auto Download ---
        if (RoCat.settings && RoCat.settings.autoDownload) {
            var allMedia = [];
            for (var v2 = 0; v2 < result.videoLinks.length; v2++) {
                var url = result.videoLinks[v2];
                if (url.startsWith('/')) url = 'https://savekit.io' + url;
                if (url.startsWith('http')) {
                    allMedia.push({
                        url: url,
                        name: 'tiktok_video_' + (v2 + 1) + '.mp4',
                        type: 'video'
                    });
                }
            }
            for (var a2 = 0; a2 < result.audioLinks.length; a2++) {
                var url = result.audioLinks[a2];
                if (url.startsWith('/')) url = 'https://savekit.io' + url;
                if (url.startsWith('http')) {
                    allMedia.push({
                        url: url,
                        name: 'tiktok_audio_' + (a2 + 1) + '.mp3',
                        type: 'audio'
                    });
                }
            }
            if (allMedia.length > 0) {
                RoCatUI.log("🤖 Auto-download aktif...");
                downloadAllMedia(allMedia);
            }
        }

        RoCatUI.addAlert("💡 Screenshot diambil untuk melihat hasil di SaveKit", "info");

    } catch (e) {
        RoCatUI.addAlert("❌ Error: " + e.message, "error");
        RoCatUI.log("❌ processVideo error: " + e.message);
        RoCatUI.addButton("← Kembali", "onLaunch");
    }
}

// --- Download media ---
function downloadAllMedia(mediaList) {
    try {
        if (!mediaList || mediaList.length === 0) {
            RoCatUI.addAlert("⚠️ Tidak ada media", "warning");
            return;
        }

        var successCount = 0;
        var failCount = 0;

        for (var i = 0; i < mediaList.length; i++) {
            var media = mediaList[i];
            try {
                RoCatUI.log("⏳ Mengunduh " + media.name + "...");
                
                var res = fetch(media.url, {
                    method: "GET",
                    headers: { "Referer": "https://savekit.io/" }
                });
                
                if (res.ok) {
                    var mimeType = media.type === 'video' ? 'video/mp4' : 'audio/mpeg';
                    var savedUri = RoCatUI.save(media.name, res.body, mimeType);
                    if (savedUri) {
                        RoCatUI.log("✅ Tersimpan: " + media.name);
                        successCount++;
                    } else {
                        RoCatUI.log("❌ Gagal simpan: " + media.name);
                        failCount++;
                    }
                } else {
                    RoCatUI.log("❌ Gagal unduh: " + media.name + " (status " + res.status + ")");
                    failCount++;
                }
            } catch (e) {
                RoCatUI.log("❌ Error unduh " + media.name + ": " + e.message);
                failCount++;
            }
        }

        if (successCount > 0) {
            RoCatUI.addAlert("✅ Berhasil mengunduh " + successCount + " file!", "success");
        }
        if (failCount > 0) {
            RoCatUI.addAlert("⚠️ " + failCount + " file gagal", "warning");
        }
        
        RoCatUI.log("📊 Selesai: " + successCount + " berhasil, " + failCount + " gagal");
        
    } catch (e) {
        RoCatUI.log("❌ downloadAllMedia error: " + e.message);
        RoCatUI.addAlert("❌ Gagal mengunduh media", "error");
    }
}

// --- Fungsi untuk mengecek status ---
function checkStatus() {
    RoCatUI.addAlert("🔄 Script siap digunakan", "info");
    RoCatUI.log("📊 Status: OK - TikTok Downloader v4.0.0");
}