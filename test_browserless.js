// ==UserScript==
// @name         Test Browserless (Demo Tahap 29)
// @version      1.0.0
// @description  Demonstrasi API browserless (headless WebView): goto, type, click,
//               waitForSelector, scrollBottom (lazy-load), evaluate, content,
//               screenshot — dipadukan dengan fetch/RoCatDOM/RoCatUI.
// @author       RoCat AI
// @category     Contoh
// @match        https://example.com/*
// ==/UserScript==

var DEMO_URL = "https://example.com/login";

// --- Lifecycle: dipanggil otomatis saat Canvas dibuka ---
function onLaunch() {
    try {
        RoCat.render([
            { type: "clear" },
            { type: "button", label: "🚀 Jalankan Demo Browserless", fn: "runDemo" },
            { type: "button", label: "📦 Demo Mode Statis (fetch)", fn: "runStatic" },
            { type: "log", text: "Pilih mode: browserless (WebView) atau statis (fetch)." }
        ]);
    } catch (e) {
        RoCatUI.log("onLaunch error: " + e.message);
    }
}

// =====================================================================
// MODE BROWSERLESS — headless WebView (page.goto / click / type / ...)
// =====================================================================
function runDemo() {
    try {
        if (typeof page === "undefined" || page === null) {
            RoCatUI.addAlert("Mesin browser tidak tersedia (hanya tersedia di Canvas).", "error");
            return;
        }

        RoCatUI.clear();
        RoCatUI.addButton("🏠 Home", "onLaunch");
        RoCatUI.addAlert("1. Navigasi — page.goto()", "info");

        // 1) Navigasi + tunggu halaman selesai dimuat.
        page.goto(DEMO_URL, { waitUntil: "load", timeout: 20000 });
        RoCatUI.addAlert("2. Form — page.type() + page.click()", "info");

        // 2) Ketik ke input + klik tombol submit (event React/Vue-friendly).
        var typedUser = page.type("#user", "admin");
        var typedPass = page.type("#pass", "secret123");
        var clicked = page.click("#submit");

        // 3) Tunggu elemen hasil dirender (lazy/JS).
        RoCatUI.addAlert("3. Menunggu DOM — page.waitForSelector()", "info");
        var waited = page.waitForSelector(".dashboard", 10000);

        // 4) Scroll ke dasar untuk memicu lazy-load / infinite scroll.
        RoCatUI.addAlert("4. Scroll lazy-load — page.scrollBottom()", "info");
        page.scrollTo(0, 200);
        page.scrollBottom();
        page.waitForTimeout(800);

        // 5) Eksekusi JS di dalam halaman — page.evaluate().
        var info = page.evaluate(function () {
            return {
                title: document.title,
                cards: document.querySelectorAll(".card").length,
                scrollY: window.scrollY,
                ready: document.readyState
            };
        });

        // 6) Ambil HTML yang sudah dirender — page.content().
        var html = page.content();
        var doc = RoCatDOM.parse(html);
        var name = doc.textOf(".user-name") || "(tidak ketemu .user-name)";

        // 7) Tangkapan layar — page.screenshot().
        var shot = page.screenshot({ quality: 85 });

        RoCatUI.clear();
        RoCatUI.addButton("🏠 Home", "onLaunch");
        RoCatUI.addButton("🔄 Ulangi Demo", "runDemo");
        RoCatUI.addJsonLog({
            url: page.url(),
            typedUser: typedUser.success,
            typedPass: typedPass.success,
            clicked: clicked.success,
            waited: waited,
            info: info,
            contentLength: html.length,
            parsedName: name,
            screenshot: shot
        }, "Hasil Browserless", true);

        if (shot !== "") RoCatUI.log("📸 Screenshot tersimpan: " + shot);
        RoCatUI.addAlert("Selesai — skrip berjalan sinkron tanpa async/await.", "success");

        // Bersihkan WebView agar tidak bocor memori.
        page.close();
    } catch (e) {
        RoCatUI.addAlert("Demo browserless gagal: " + e.message, "error");
        RoCatUI.log("stack: " + (e.stack || ""));
    }
}

// =====================================================================
// MODE STATIS — fetch() + RoCatDOM (tetap berfungsi 100%)
// =====================================================================
function runStatic() {
    try {
        RoCatUI.clear();
        RoCatUI.addButton("🏠 Home", "onLaunch");
        RoCatUI.addButton("🚀 Coba Browserless", "runDemo");

        // fetch() sinkron lama harus tetap normal — backward compatibility.
        var res = fetch("https://example.com/", "GET", {}, null);
        RoCatUI.addAlert("fetch() status: " + res.status + " " + (res.ok ? "OK" : "GAGAL"), "info");

        var html = res.text();
        var doc = RoCatDOM.parse(html);
        var heading = doc.textOf("h1") || doc.textOf(".title") || "(tidak ada h1)";

        RoCatUI.addJsonLog({
            status: res.status,
            ok: res.ok,
            bodyLength: html.length,
            h1: heading
        }, "Hasil Mode Statis", true);
        RoCatUI.addAlert("fetch + RoCatDOM tetap bekerja normal berdampingan dengan page.", "success");
    } catch (e) {
        RoCatUI.addAlert("Mode statis gagal: " + e.message, "error");
    }
}
