// ==UserScript==
// @name         CapCut - Klik Lanjutkan dengan alamat email (Native Touch)
// @version      4.0.0
// @description  Buka halaman signup CapCut, temukan tombol "Lanjutkan dengan alamat email",
//               tandai dengan data-rocat-click, lalu klik lewat page.click() yang memakai
//               tap native (MotionEvent ACTION_DOWN/ACTION_UP) — bukan JS el.click().
// @author       RoCat User
// @category     Tools
// @match        https://www.capcut.com/*
// ==/UserScript==

var CAPCUT_URL = "https://www.capcut.com/id-id/signup";
var EMAIL_TEXT = "Lanjutkan dengan alamat email";

function onLaunch() {
    RoCat.render([
        { type: "clear" },
        { type: "badges", badges: ["🎯 CapCut Native Touch Click Test"] },
        { type: "button", label: "🚀 Klik Lanjutkan dengan alamat email", fn: "clickContinueEmail" },
        { type: "alert", message: "page.click() memakai tap native (MotionEvent) — bukan JS el.click() yang isTrusted=false.", level: "info" }
    ]);
}

function clickContinueEmail() {
    try {
        RoCat.render([
            { type: "clear" },
            { type: "button", label: "🏠 Home", fn: "onLaunch" },
            { type: "badges", badges: ["⏳ Loading..."] },
            { type: "alert", message: "Membuka halaman CapCut...", level: "info" }
        ]);

        RoCatUI.log("📡 Membuka: " + CAPCUT_URL);

        // 1) Buka halaman + tunggu sampai form selector utama muncul (SPA render).
        page.goto(CAPCUT_URL, { waitUntil: "load", timeout: 30000 });
        var found = page.waitForSelector('[class*="lv-account-login-form-main-field"]', 30000);
        page.waitForTimeout(1500);
        RoCatUI.log("🔍 Form CapCut ditemukan: " + found);

        // Screenshot sebelum klik
        var before = page.screenshot({ quality: 95 });

        // 2) Temukan tombol dengan teks yang tepat, lalu TANDAI dengan atribut data
        //    sehingga page.click() bisa menargetkannya dengan selector CSS yang pasti.
        var marker = page.evaluate(function () {
            var fields = document.querySelectorAll('[class*="lv-account-login-form-main-field"]');
            for (var i = 0; i < fields.length; i++) {
                var el = fields[i];
                var text = (el.textContent || "") + " " + (el.innerText || "");
                if (text.indexOf('Lanjutkan dengan alamat email') !== -1) {
                    el.setAttribute('data-rocat-click', '1');
                    return {
                        tagged: true,
                        tag: el.tagName,
                        className: el.className || "",
                        index: i
                    };
                }
            }
            return { tagged: false };
        });

        if (!marker || !marker.tagged) {
            RoCatUI.addAlert("❌ Tombol 'Lanjutkan dengan alamat email' tidak ditemukan", "error");
            RoCatUI.addJsonLog(marker || {}, "Hasil Pencarian", true);
            return;
        }
        RoCatUI.addJsonLog(marker, "🎯 Elemen yang Ditandai", true);

        // 3) KLIK NATIVE TOUCH — page.click() membidik koordinat pusat elemen lalu
        //    mengirim MotionEvent ACTION_DOWN -> ACTION_UP lewat WebView sehingga situs
        //    melihat event isTrusted=true (anti-bot / SPA tidak bisa mengabaikannya).
        RoCatUI.log("🖱️ Tap native via page.click('[data-rocat-click=\"1\"]')...");
        var clicked = page.click('[data-rocat-click="1"]');
        RoCatUI.log("Hasil page.click: " + JSON.stringify(clicked));

        // 4) Tunggu render ulang SPA, lalu verifikasi hasilnya.
        page.waitForTimeout(2500);
        var after = page.screenshot({ quality: 95 });

        var html = page.content();
        var doc = RoCatDOM.parse(html);
        var emailInputs = doc.find("input[type='email']");
        var passwordInputs = doc.find("input[type='password']");
        var forms = doc.find("form");

        RoCat.render([
            { type: "clear" },
            { type: "button", label: "🏠 Home", fn: "onLaunch" },
            { type: "button", label: "🔄 Coba Lagi", fn: "clickContinueEmail" }
        ]);

        if (before) RoCatUI.addImage(before, "📸 SEBELUM Klik", true);
        if (after) RoCatUI.addImage(after, "📸 SETELAH Klik", true);

        RoCatUI.addBadgeGroup([
            "Status: " + (clicked.success ? "✅ Tap Native OK" : "❌ Tap Gagal"),
            "Email Input: " + (emailInputs.length > 0 ? "✅" : "❌"),
            "Password Input: " + (passwordInputs.length > 0 ? "✅" : "❌"),
            "Forms: " + forms.length,
            "URL: " + page.url().replace(/^https?:\/\//, "").substring(0, 25)
        ]);

        if (clicked.success) {
            if (emailInputs.length > 0) {
                RoCatUI.addAlert("✅ Berhasil! Form email muncul setelah tap native.", "success");
            } else {
                RoCatUI.addAlert("✅ Tap native diterima, tapi form email belum terlihat", "info");
            }
        } else {
            RoCatUI.addAlert("⚠️ Tap native tidak diterima oleh situs", "warning");
        }

        RoCatUI.addJsonLog({
            url_awal: CAPCUT_URL,
            url_sekarang: page.url(),
            elemen: marker,
            berhasil_tap: clicked.success,
            error: clicked.error || "tidak ada error",
            email_inputs: emailInputs.length,
            password_inputs: passwordInputs.length,
            forms: forms.length,
            screenshot_before: before || "gagal",
            screenshot_after: after || "gagal"
        }, "📊 Detail", true);

        RoCatUI.log("✅ Selesai!");

    } catch (e) {
        RoCat.render([
            { type: "clear" },
            { type: "button", label: "🏠 Home", fn: "onLaunch" },
            { type: "button", label: "🔄 Coba Lagi", fn: "clickContinueEmail" },
            { type: "alert", message: "❌ Error: " + e.message, level: "error" }
        ]);
        RoCatUI.log("❌ Error: " + e.message);
        RoCatUI.addJsonLog({
            error: e.message,
            stack: e.stack || "tidak ada stack"
        }, "Error Detail", true);
    }
}