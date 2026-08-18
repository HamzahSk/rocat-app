// ==UserScript==
// @name         CapCut - Auto Create Account (Non-Pro) with Native Touch
// @version      9.1.0
// @description  Auto generate akun CapCut non-pro dengan native touch click (Tahap 30 v2: fix koordinat CSS-px→view-px & fokus WebView)
// @author       RoCat User
// @category     Tools
// @match        https://www.capcut.com/*
// ==/UserScript==

// ===== UTILITY FUNCTIONS =====
function generatePassword(length) {
    length = length || 12;
    var chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#';
    var pw = '';
    for (var i = 0; i < length; i++) {
        pw += chars[Math.floor(Math.random() * chars.length)];
    }
    return pw;
}

function generateBirthday() {
    var day = Math.floor(Math.random() * 28) + 1;
    var month = Math.floor(Math.random() * 12) + 1;
    var year = Math.floor(Math.random() * (2004 - 1980 + 1)) + 1980;
    return { day: day, month: month, year: year };
}

var MONTH_EN = {
    1: 'January', 2: 'February', 3: 'March', 4: 'April',
    5: 'May', 6: 'June', 7: 'July', 8: 'August',
    9: 'September', 10: 'October', 11: 'November', 12: 'December'
};

var stepScreenshots = [];
var accountData = {};

/**
 * Finds the first visible element whose text contains one of [labels], tags it with
 * data-rocat-click="1" so the native-touch bridge can target it, and returns
 * { success, text }. Uses only plain CSS selectors (document.querySelectorAll) — never
 * Playwright pseudo-selectors like :has-text() which querySelector cannot parse.
 */
function findAndTagButton(labels) {
    try {
        var result = page.evaluate(function(labels) {
            var names = JSON.parse(labels);
            var all = document.querySelectorAll('button, a, [role="button"], input[type="submit"]');
            for (var i = 0; i < all.length; i++) {
                var el = all[i];
                if (el.offsetParent === null) continue;
                var text = (el.textContent || '').trim();
                for (var j = 0; j < names.length; j++) {
                    if (text.indexOf(names[j]) !== -1) {
                        el.setAttribute('data-rocat-click', '1');
                        return { success: true, text: text };
                    }
                }
            }
            return { success: false };
        }, JSON.stringify(labels));
        return result || { success: false };
    } catch (e) {
        return { success: false };
    }
}

/**
 * Clicks a button by its visible text using the native-touch bridge (Tahap 30): tags the
 * element with data-rocat-click="1" then page.click('[data-rocat-click="1"]') → a real
 * MotionEvent ACTION_DOWN/ACTION_UP tap on the WebView. Returns true when the element was
 * found and the native click was dispatched.
 */
function clickButtonByText(labels) {
    var tagged = findAndTagButton(labels);
    if (!tagged || !tagged.success) return false;
    try {
        page.click('[data-rocat-click="1"]');
        return true;
    } catch (e) {
        return false;
    }
}

/** Sets a <select>/[role=combobox] option whose text matches one of [labels]. */
function setSelectValue(labels) {
    try {
        var result = page.evaluate(function(selector) {
            var names = JSON.parse(selector);
            var selects = document.querySelectorAll('select, [role="combobox"]');
            for (var i = 0; i < selects.length; i++) {
                var sel = selects[i];
                if (sel.offsetWidth <= 0) continue;
                var opts = sel.querySelectorAll('option, [role="option"]');
                for (var o = 0; o < opts.length; o++) {
                    var t = (opts[o].textContent || '').trim();
                    for (var j = 0; j < names.length; j++) {
                        if (t.indexOf(names[j]) !== -1) {
                            sel.value = opts[o].value;
                            sel.dispatchEvent(new Event('change', { bubbles: true }));
                            sel.dispatchEvent(new Event('input', { bubbles: true }));
                            return { success: true };
                        }
                    }
                }
            }
            return { success: false };
        }, JSON.stringify(labels));
        return result !== null && result !== undefined && result.success === true;
    } catch (e) {
        return false;
    }
}

// ===== MAIN FUNCTIONS =====
function onLaunch() {
    RoCat.render([
        { type: "clear" },
        { type: "badges", badges: ["📝 CapCut Auto Account Generator"] },
        { type: "input", id: "email", hint: "Email untuk registrasi (kosong = auto generate)" },
        { type: "button", label: "🚀 Buat Akun", fn: "createAccount" },
        { type: "alert", message: "Menggunakan native touch click (Tahap 30)", level: "info" }
    ]);
}

// ===== FUNGSI UNTUK MENGECEK INPUT EMAIL DI HALAMAN =====
function checkEmailInputOnPage() {
    try {
        var result = page.evaluate(function() {
            // Cari input email
            var emailInputs = document.querySelectorAll('input[type="email"], input[name="email"], input[placeholder*="Email"], input[placeholder*="email"]');
            var visibleInputs = [];
            for (var i = 0; i < emailInputs.length; i++) {
                if (emailInputs[i].offsetParent !== null) {
                    visibleInputs.push({
                        type: emailInputs[i].type,
                        placeholder: emailInputs[i].placeholder || '',
                        name: emailInputs[i].name || '',
                        id: emailInputs[i].id || ''
                    });
                }
            }
            
            // Cari tombol lanjutkan
            var buttons = document.querySelectorAll('button, a, [role="button"]');
            var continueButtons = [];
            for (var i = 0; i < buttons.length; i++) {
                var text = (buttons[i].textContent || '').trim().toLowerCase();
                if (text.indexOf('continue') !== -1 || 
                    text.indexOf('lanjutkan') !== -1 || 
                    text.indexOf('next') !== -1) {
                    if (buttons[i].offsetParent !== null) {
                        continueButtons.push(text);
                    }
                }
            }
            
            return {
                hasEmailInput: visibleInputs.length > 0,
                emailInputs: visibleInputs,
                continueButtons: continueButtons,
                totalEmailInputs: visibleInputs.length
            };
        });
        return result;
    } catch (e) {
        return { hasEmailInput: false, error: e.message };
    }
}

function createAccount(inputs) {
    try {
        var email = (inputs && inputs.email || "").trim();
        stepScreenshots = [];

        if (!email) {
            var randomStr = Math.random().toString(36).substring(2, 10);
            email = "test_" + randomStr + "@gmail.com";
            RoCatUI.log("📧 Email auto-generated: " + email);
        }

        var password = generatePassword(12);
        var birthday = generateBirthday();

        accountData = {
            email: email,
            password: password,
            birthday: birthday
        };

        RoCat.render([
            { type: "clear" },
            { type: "button", label: "🏠 Home", fn: "onLaunch" },
            { type: "badges", badges: ["⏳ Proses Registrasi..."] },
            { type: "alert", message: "Membuka halaman CapCut...", level: "info" }
        ]);

        RoCatUI.log("📡 Memulai proses registrasi...");
        RoCatUI.log("📧 Email: " + email);
        RoCatUI.log("🔑 Password: " + password);

        // ===== STEP 1: Buka Halaman Signup =====
        RoCatUI.log("🌐 Membuka https://www.capcut.com/signup...");
        page.goto("https://www.capcut.com/signup", {
            waitUntil: "domcontentloaded",
            timeout: 30000
        });
        page.waitForTimeout(3000);

        var s1 = page.screenshot({ quality: 90 });
        if (s1) {
            stepScreenshots.push({ step: "Halaman Signup", path: s1 });
            RoCatUI.addImage(s1, "📸 STEP 1: Halaman Signup", true);
        }

        // ===== STEP 2: KLIK "Continue with email" - NATIVE TOUCH =====
        RoCatUI.log("🔍 Mencari dan mengklik 'Continue with email'...");
        RoCatUI.log("🖱️ Menggunakan native touch click (Tahap 30)");

        var tapOk = clickButtonByText(['Continue with email', 'Lanjutkan dengan alamat email']);
        RoCatUI.log(tapOk
            ? "✅ Tap native dikirim untuk 'Continue with email'"
            : "⚠️ Tombol 'Continue with email' tidak ditemukan");

        page.waitForTimeout(2500);

        // ===== CEK APAKAH INPUT EMAIL MUNCUL =====
        RoCatUI.log("🔍 Mengecek apakah input email muncul...");
        var emailCheck = checkEmailInputOnPage();
        
        // Screenshot setelah klik
        var s2 = page.screenshot({ quality: 90 });
        if (s2) {
            stepScreenshots.push({ step: "Setelah Klik Continue with email", path: s2 });
            RoCatUI.addImage(s2, "📸 STEP 2: Setelah Klik Continue with email", true);
        }

        // ===== TAMPILKAN HASIL PENGECEKAN =====
        if (emailCheck && emailCheck.hasEmailInput) {
            // BERHASIL - Input email muncul
            RoCatUI.addAlert("✅ BERHASIL! Input email muncul di halaman!", "success");
            RoCatUI.log("✅ Input email ditemukan! Jumlah: " + emailCheck.totalEmailInputs);
            
            // Tampilkan detail input email
            var emailInputInfo = "📧 Input Email ditemukan:\n";
            for (var i = 0; i < emailCheck.emailInputs.length; i++) {
                var input = emailCheck.emailInputs[i];
                emailInputInfo += "  - Type: " + input.type + 
                                 ", Placeholder: " + input.placeholder + 
                                 ", Name: " + input.name + "\n";
            }
            RoCatUI.log(emailInputInfo);
            
            if (emailCheck.continueButtons && emailCheck.continueButtons.length > 0) {
                RoCatUI.log("🔘 Tombol Continue ditemukan: " + emailCheck.continueButtons.join(", "));
            }
            
            // Tampilkan hasil di UI
            RoCat.render([
                { type: "clear" },
                { type: "button", label: "🏠 Home", fn: "onLaunch" },
                { type: "button", label: "🔄 Coba Lagi", fn: "createAccount" },
                { type: "alert", message: "✅ Klik Berhasil! Input email muncul", level: "success" },
                { type: "badges", badges: ["✅ Input email muncul!", "📧 Email: " + email, "🔑 Password: " + password] }
            ]);
            
            // Tampilkan info detail
            var resultHtml = "<b>✅ BERHASIL!</b><br><br>";
            resultHtml += "Tombol 'Continue with email' berhasil diklik.<br>";
            resultHtml += "Input email muncul di halaman CapCut.<br><br>";
            resultHtml += "<b>📧 Detail Input Email:</b><br>";
            for (var i = 0; i < emailCheck.emailInputs.length; i++) {
                var input = emailCheck.emailInputs[i];
                resultHtml += "• Type: " + input.type + "<br>";
                resultHtml += "  Placeholder: " + input.placeholder + "<br>";
                resultHtml += "  Name: " + input.name + "<br><br>";
            }
            if (emailCheck.continueButtons && emailCheck.continueButtons.length > 0) {
                resultHtml += "<b>🔘 Tombol Lanjutkan:</b> " + emailCheck.continueButtons.join(", ") + "<br>";
            }
            resultHtml += "<br><b>📸 Screenshot:</b> Lihat di atas<br>";
            resultHtml += "<br><b>⚠️ Catatan:</b><br>";
            resultHtml += "• Native touch click (Tahap 30 v2) berhasil<br>";
            resultHtml += "• Input email sudah siap diisi";
            
            RoCatUI.addHtmlPreview(resultHtml, "✅ Hasil Klik Berhasil");
            
            // JSON log
            RoCatUI.addJsonLog({
                status: "success",
                tap_berhasil: tapOk,
                email: email,
                password: password,
                email_inputs_ditemukan: emailCheck.totalEmailInputs,
                email_inputs_detail: emailCheck.emailInputs,
                continue_buttons: emailCheck.continueButtons,
                total_screenshots: stepScreenshots.length
            }, "✅ Detail Keberhasilan", true);
            
        } else {
            // GAGAL - Input email tidak muncul
            RoCatUI.addAlert("❌ GAGAL! Input email tidak muncul!", "error");
            RoCatUI.log("❌ Input email TIDAK ditemukan!");
            
            // Debug info
            var debugInfo = page.evaluate(function() {
                var allInputs = document.querySelectorAll('input');
                var inputTypes = [];
                for (var i = 0; i < allInputs.length; i++) {
                    if (allInputs[i].offsetParent !== null) {
                        inputTypes.push({
                            type: allInputs[i].type,
                            placeholder: allInputs[i].placeholder || '',
                            name: allInputs[i].name || ''
                        });
                    }
                }
                var allText = document.body.textContent || "";
                return {
                    inputs: inputTypes,
                    bodyText: allText.substring(0, 300),
                    url: window.location.href
                };
            });
            
            RoCat.render([
                { type: "clear" },
                { type: "button", label: "🏠 Home", fn: "onLaunch" },
                { type: "button", label: "🔄 Coba Lagi", fn: "createAccount" },
                { type: "alert", message: "❌ Gagal! Input email tidak muncul", level: "error" }
            ]);
            
            var errorHtml = "<b>❌ GAGAL!</b><br><br>";
            errorHtml += "Tombol 'Continue with email' diklik tapi input email tidak muncul.<br><br>";
            errorHtml += "<b>🔍 Debug Info:</b><br>";
            errorHtml += "• URL: " + (debugInfo ? debugInfo.url : "tidak diketahui") + "<br>";
            errorHtml += "• Input ditemukan: " + (debugInfo ? debugInfo.inputs.length : 0) + "<br><br>";
            
            if (debugInfo && debugInfo.inputs.length > 0) {
                errorHtml += "<b>📝 Input yang ditemukan:</b><br>";
                for (var i = 0; i < Math.min(debugInfo.inputs.length, 5); i++) {
                    var inp = debugInfo.inputs[i];
                    errorHtml += "• Type: " + inp.type + ", Placeholder: " + inp.placeholder + "<br>";
                }
            }
            
            errorHtml += "<br><b>📸 Screenshot:</b> Lihat di atas";
            RoCatUI.addHtmlPreview(errorHtml, "❌ Hasil Klik Gagal");
            
            RoCatUI.addJsonLog({
                status: "failed",
                tap_berhasil: tapOk,
                email: email,
                password: password,
                email_input_ditemukan: false,
                debug: debugInfo || null,
                total_screenshots: stepScreenshots.length
            }, "❌ Detail Kegagalan", true);
        }
        
        // Tampilkan screenshot akhir
        if (s2) {
            RoCatUI.addImage(s2, "📸 Kondisi setelah klik", true);
        }

    } catch (e) {
        var errorScreenshot = (typeof page !== "undefined" && page) ? page.screenshot({ quality: 90 }) : null;

        RoCat.render([
            { type: "clear" },
            { type: "button", label: "🏠 Home", fn: "onLaunch" },
            { type: "button", label: "🔄 Coba Lagi", fn: "createAccount" },
            { type: "alert", message: "❌ Error: " + e.message, level: "error" }
        ]);

        if (errorScreenshot) {
            RoCatUI.addImage(errorScreenshot, "📸 Error Screenshot", true);
        }

        RoCatUI.log("❌ Error: " + e.message);
        RoCatUI.addJsonLog({
            error: e.message,
            stack: e.stack || "tidak ada stack"
        }, "Error Detail", true);
    }
}

// ===== INISIALISASI =====
onLaunch();