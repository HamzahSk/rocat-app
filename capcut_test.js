// ==UserScript==
// @name         CapCut - Auto Create Account (Non-Pro) with Native Touch
// @version      9.0.0
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

        // ===== CEK APAKAH KLIK BERHASIL (form email muncul?) =====
        var checkResult = page.evaluate(function() {
            var inputs = document.querySelectorAll('input[type="email"], input[name="username"], input[placeholder*="Email"]');
            var visible = 0;
            for (var i = 0; i < inputs.length; i++) {
                if (inputs[i].offsetParent !== null) visible++;
            }
            return { email_inputs: visible, hasEmailInput: visible > 0 };
        });

        var s2 = page.screenshot({ quality: 90 });
        if (s2) {
            stepScreenshots.push({ step: "Setelah Klik Continue with email", path: s2 });
            RoCatUI.addImage(s2, "📸 STEP 2: Setelah Klik Continue with email", true);
        }

        var emailInputs = (checkResult && checkResult.email_inputs) || 0;
        var passwordInputs = 0;

        // ===== IF CLICK FAILED =====
        if (!checkResult || !checkResult.hasEmailInput) {
            RoCatUI.addAlert("❌ Gagal mengklik 'Continue with email'!", "error");

            var debugInfo = page.evaluate(function() {
                var allText = document.body.textContent || "";
                var buttons = document.querySelectorAll('button, a, div[role="button"]');
                var buttonTexts = [];
                for (var i = 0; i < buttons.length; i++) {
                    var text = (buttons[i].textContent || "").trim();
                    if (text.length > 0 && text.length < 100) {
                        buttonTexts.push(text);
                    }
                }
                return {
                    pageTitle: document.title,
                    bodyText: allText.substring(0, 500),
                    buttons: buttonTexts.slice(0, 10)
                };
            });

            RoCatUI.addJsonLog({
                click_attempted: tapOk,
                has_email_input: false,
                debug: debugInfo || null,
                total_screenshots: stepScreenshots.length
            }, "❌ Debug Klik Gagal", true);

            RoCat.render([
                { type: "clear" },
                { type: "button", label: "🏠 Home", fn: "onLaunch" },
                { type: "button", label: "🔄 Coba Lagi", fn: "createAccount" },
                { type: "alert", message: "❌ Gagal klik tombol", level: "error" }
            ]);

            if (s2) {
                RoCatUI.addImage(s2, "📸 Kondisi setelah klik gagal", true);
            }

            RoCatUI.log("❌ Proses dihentikan karena gagal klik");
            return;
        }

        RoCatUI.log("✅ Form email muncul!");
        RoCatUI.addAlert("✅ Form email muncul!", "success");

        // ===== STEP 3: Isi Email =====
        RoCatUI.log("✉️ Mengisi email: " + email);
        page.fill('input[type="email"], input[name="username"]', email);
        RoCatUI.log("✅ Email diisi dengan page.fill()");
        page.waitForTimeout(1500);

        var s3 = page.screenshot({ quality: 90 });
        if (s3) {
            stepScreenshots.push({ step: "Email Diisi", path: s3 });
            RoCatUI.addImage(s3, "📸 STEP 3: Email Diisi", true);
        }

        // ===== STEP 4: Klik Continue (native touch) =====
        RoCatUI.log("🖱️ Mencari tombol 'Continue'...");
        var continueOk = clickButtonByText(['Continue', 'Lanjutkan']);
        RoCatUI.log(continueOk ? "✅ Klik Continue berhasil (native)" : "⚠️ Tombol Continue tidak ditemukan");
        page.waitForTimeout(1800);

        var s4 = page.screenshot({ quality: 90 });
        if (s4) {
            stepScreenshots.push({ step: "Setelah Klik Continue", path: s4 });
            RoCatUI.addImage(s4, "📸 STEP 4: Setelah Klik Continue", true);
        }

        // ===== STEP 5: Isi Password =====
        RoCatUI.log("🔑 Mengisi password...");
        page.fill('input[type="password"]', password);
        passwordInputs = 1;
        RoCatUI.log("✅ Password diisi dengan page.fill()");
        page.waitForTimeout(1500);

        var s5 = page.screenshot({ quality: 90 });
        if (s5) {
            stepScreenshots.push({ step: "Password Diisi", path: s5 });
            RoCatUI.addImage(s5, "📸 STEP 5: Password Diisi", true);
        }

        // ===== STEP 6: Klik Sign Up (native touch) =====
        RoCatUI.log("🖱️ Mencari tombol 'Sign Up'...");
        var signupOk = clickButtonByText(['Sign Up', 'Sign up', 'Daftar']);
        RoCatUI.log(signupOk ? "✅ Klik Sign Up berhasil (native)" : "⚠️ Tombol Sign Up tidak ditemukan");
        page.waitForTimeout(1800);

        var s6 = page.screenshot({ quality: 90 });
        if (s6) {
            stepScreenshots.push({ step: "Setelah Klik Sign Up", path: s6 });
            RoCatUI.addImage(s6, "📸 STEP 6: Setelah Klik Sign Up", true);
        }

        // ===== STEP 7: Isi Birthday =====
        var dayStr = String(birthday.day);
        var yearStr = String(birthday.year);
        var monthName = MONTH_EN[birthday.month];

        RoCatUI.log("📅 Mengisi tanggal lahir: " + monthName + " " + dayStr + ", " + yearStr);

        // Year
        try {
            page.fill('input[placeholder*="Year"], input[placeholder*="Tahun"]', yearStr);
            RoCatUI.log("✅ Year diisi");
        } catch (e) {
            RoCatUI.log("⚠️ Gagal isi Year: " + e.message);
        }
        page.waitForTimeout(500);

        var s7a = page.screenshot({ quality: 90 });
        if (s7a) {
            stepScreenshots.push({ step: "Year Diisi", path: s7a });
            RoCatUI.addImage(s7a, "📸 STEP 7a: Year Diisi", true);
        }

        // Month
        var monthSet = setSelectValue([monthName]);
        RoCatUI.log(monthSet ? "✅ Month dipilih" : "⚠️ Month gagal dipilih (perlu manual)");
        page.waitForTimeout(500);

        var s7b = page.screenshot({ quality: 90 });
        if (s7b) {
            stepScreenshots.push({ step: "Month Dipilih", path: s7b });
            RoCatUI.addImage(s7b, "📸 STEP 7b: Month Dipilih", true);
        }

        // Day
        var daySet = setSelectValue([dayStr]);
        RoCatUI.log(daySet ? "✅ Day dipilih" : "⚠️ Day gagal dipilih (perlu manual)");
        page.waitForTimeout(500);

        var s7c = page.screenshot({ quality: 90 });
        if (s7c) {
            stepScreenshots.push({ step: "Day Dipilih", path: s7c });
            RoCatUI.addImage(s7c, "📸 STEP 7c: Day Dipilih", true);
        }

        // ===== STEP 8: Klik Next (native touch) =====
        RoCatUI.log("🖱️ Mencari tombol 'Next'...");
        var nextOk = clickButtonByText(['Next', 'Lanjutkan', 'Submit']);
        RoCatUI.log(nextOk ? "✅ Klik Next berhasil (native)" : "⚠️ Tombol Next tidak ditemukan");
        page.waitForTimeout(3000);

        var s8 = page.screenshot({ quality: 90 });
        if (s8) {
            stepScreenshots.push({ step: "Setelah Klik Next", path: s8 });
            RoCatUI.addImage(s8, "📸 STEP 8: Setelah Klik Next", true);
        }

        // ===== STEP 9: Screenshot Final =====
        var sFinal = page.screenshot({ quality: 95 });
        if (sFinal) {
            stepScreenshots.push({ step: "HASIL AKHIR", path: sFinal });
            RoCatUI.addImage(sFinal, "📸 FINAL: Hasil Registrasi", true);
        }

        // ===== STEP 10: Cek OTP =====
        var otpCheck = page.evaluate(function() {
            var hasOTP = false;
            var allText = document.body.textContent || '';
            if (allText.toLowerCase().indexOf('otp') !== -1 ||
                allText.toLowerCase().indexOf('verification') !== -1 ||
                allText.toLowerCase().indexOf('verify') !== -1) {
                hasOTP = true;
            }
            return { hasOTP: hasOTP };
        });

        // ===== Tampilkan Hasil =====
        RoCat.render([
            { type: "clear" },
            { type: "button", label: "🏠 Home", fn: "onLaunch" },
            { type: "button", label: "🔄 Coba Lagi", fn: "createAccount" },
            { type: "alert", message: "✅ Proses registrasi selesai!", level: "success" }
        ]);

        // Tampilkan semua screenshot
        RoCatUI.addBadgeGroup(["📸 Total Screenshot: " + stepScreenshots.length]);

        // Badges
        var badges = [];
        badges.push("📧 Email: " + email);
        badges.push("🔑 Password: " + password);
        badges.push("📅 DOB: " + monthName + " " + dayStr + ", " + yearStr);
        badges.push("OTP: " + ((otpCheck && otpCheck.hasOTP) ? "✅ Ya" : "❌ Tidak"));
        badges.push("URL: " + page.url().replace(/^https?:\/\//, "").substring(0, 25));
        RoCatUI.addBadgeGroup(badges);

        // Info akun
        var infoHtml = "<b>✅ Registrasi Selesai!</b><br><br>";
        infoHtml += "<b>📧 Email:</b> " + email + "<br>";
        infoHtml += "<b>🔑 Password:</b> " + password + "<br>";
        infoHtml += "<b>📅 Tanggal Lahir:</b> " + monthName + " " + dayStr + ", " + yearStr + "<br><br>";

        if (otpCheck && otpCheck.hasOTP) {
            infoHtml += "<b>⚠️ OTP Diperlukan!</b><br>";
            infoHtml += "Cek email Anda untuk kode verifikasi.<br>";
            infoHtml += "Masukkan OTP di browser untuk menyelesaikan registrasi.<br><br>";
        } else {
            infoHtml += "<b>✅ Tidak Perlu OTP</b><br>";
            infoHtml += "Akun mungkin sudah berhasil dibuat.<br><br>";
        }

        infoHtml += "<b>📸 Screenshot:</b><br>";
        for (var i = 0; i < stepScreenshots.length; i++) {
            infoHtml += (i + 1) + ". " + stepScreenshots[i].step + "<br>";
        }

        infoHtml += "<br><b>⚠️ Catatan:</b><br>";
        infoHtml += "• Simpan data ini dengan aman<br>";
        infoHtml += "• Akun ini adalah akun NON-PRO<br>";
        infoHtml += "• Native touch click (Tahap 30 v2) digunakan untuk klik<br>";
        infoHtml += "• Scroll ke atas untuk melihat semua screenshot";

        RoCatUI.addHtmlPreview(infoHtml, "📝 Data Akun & Screenshot");

        // JSON detail
        var screenshotsInfo = [];
        for (var i = 0; i < stepScreenshots.length; i++) {
            screenshotsInfo.push({
                step: stepScreenshots[i].step,
                path: stepScreenshots[i].path || "gagal"
            });
        }

        RoCatUI.addJsonLog({
            email: email,
            password: password,
            birthday: {
                day: birthday.day,
                month: birthday.month,
                monthName: monthName,
                year: birthday.year
            },
            url: page.url(),
            otp_required: (otpCheck && otpCheck.hasOTP) ? true : false,
            berhasil_tap: tapOk,
            email_inputs: emailInputs,
            password_inputs: passwordInputs,
            total_screenshots: stepScreenshots.length,
            screenshots: screenshotsInfo,
            native_touch_used: true,
            status: "selesai"
        }, "📊 Detail Lengkap", true);

        RoCatUI.log("✅ Registrasi selesai! Total " + stepScreenshots.length + " screenshot.");

        if (otpCheck && otpCheck.hasOTP) {
            RoCatUI.addAlert("📧 Cek email untuk OTP!", "info");
        } else {
            RoCatUI.addAlert("✅ Akun berhasil dibuat!", "success");
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
            stack: e.stack || "tidak ada stack",
            step: stepScreenshots.length + " screenshot berhasil diambil"
        }, "Error Detail", true);
    }
}
