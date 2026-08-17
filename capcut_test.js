// ==UserScript==
// @name         CapCut - Auto Create Account (Non-Pro) with Native Touch
// @version      8.0.0
// @description  Auto generate akun CapCut non-pro dengan native touch click (Tahap 30)
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

        // Gunakan page.click() dengan native touch (MotionEvent)
        // Coba berbagai selector
        var selectors = [
            "div:has-text('Continue with email')",
            "div:has-text('Lanjutkan dengan alamat email')",
            "[class*='lv-account-login-form-main-field']:has-text('email')",
            "[class*='lv-account-login-form-main-field']:has-text('Email')",
            "span:has-text('Continue with email')",
            "span:has-text('Lanjutkan dengan alamat email')"
        ];

        var clickSuccess = false;
        var clickedSelector = "";

        for (var i = 0; i < selectors.length; i++) {
            if (clickSuccess) break;
            try {
                RoCatUI.log("🔄 Mencoba selector: " + selectors[i]);
                // page.click() sekarang menggunakan native touch (Tahap 30)
                page.click(selectors[i]);
                clickSuccess = true;
                clickedSelector = selectors[i];
                RoCatUI.log("✅ Berhasil klik dengan selector: " + selectors[i]);
            } catch (e) {
                RoCatUI.log("⚠️ Selector gagal: " + e.message);
            }
        }

        // Jika semua selector gagal, coba dengan locator
        if (!clickSuccess) {
            RoCatUI.log("🔄 Mencoba dengan locator...");
            try {
                var locator = page.locator("div:has-text('Continue with email')");
                var result = locator.click();
                if (result && result.success) {
                    clickSuccess = true;
                    RoCatUI.log("✅ Berhasil klik dengan locator");
                }
            } catch (e) {
                RoCatUI.log("⚠️ Locator gagal: " + e.message);
            }
        }

        // Fallback terakhir: evaluate + click
        if (!clickSuccess) {
            RoCatUI.log("🔄 Fallback terakhir: evaluate + click");
            var evalResult = page.evaluate(function() {
                var elements = document.querySelectorAll('*');
                for (var i = 0; i < elements.length; i++) {
                    var el = elements[i];
                    var text = (el.textContent || "").trim();
                    if (text === 'Continue with email' || text === 'Lanjutkan dengan alamat email') {
                        var parent = el.closest('div, button, a');
                        if (parent) {
                            parent.click();
                            return { success: true, method: 'parent_click' };
                        }
                        el.click();
                        return { success: true, method: 'direct_click' };
                    }
                }
                return { success: false };
            });
            
            if (evalResult && evalResult.success) {
                clickSuccess = true;
                RoCatUI.log("✅ Berhasil klik dengan evaluate: " + evalResult.method);
            }
        }

        // Tunggu setelah klik
        page.waitForTimeout(2500);
        
        // ===== CEK APAKAH KLIK BERHASIL =====
        var checkResult = page.evaluate(function() {
            // Cek apakah ada input email yang muncul
            var emailInputs = document.querySelectorAll('input[type="email"], input[name="username"], input[placeholder*="Email"]');
            var visibleEmail = false;
            for (var i = 0; i < emailInputs.length; i++) {
                if (emailInputs[i].offsetParent !== null) {
                    visibleEmail = true;
                    break;
                }
            }
            
            // Cek apakah masih ada tombol "Continue with email"
            var stillHasButton = false;
            var allText = document.body.textContent || "";
            if (allText.includes('Continue with email') || allText.includes('Lanjutkan dengan alamat email')) {
                stillHasButton = true;
            }
            
            return {
                hasEmailInput: visibleEmail,
                stillHasButton: stillHasButton,
                success: visibleEmail && !stillHasButton
            };
        });
        
        // Screenshot setelah klik
        var s2 = page.screenshot({ quality: 90 });
        if (s2) {
            stepScreenshots.push({ step: "Setelah Klik Continue with email", path: s2 });
            RoCatUI.addImage(s2, "📸 STEP 2: Setelah Klik Continue with email", true);
        }
        
        // ===== IF CLICK FAILED =====
        if (!checkResult.success) {
            RoCatUI.addAlert("❌ Gagal mengklik 'Continue with email'!", "error");
            
            // Tampilkan informasi debug
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
                click_attempted: clickSuccess,
                has_email_input: checkResult.hasEmailInput,
                still_has_button: checkResult.stillHasButton,
                debug: debugInfo,
                total_screenshots: stepScreenshots.length
            }, "❌ Debug Klik Gagal", true);
            
            RoCatUI.addHtmlPreview(
                "<b>❌ Gagal Mengklik Tombol</b><br><br>" +
                "Tombol 'Continue with email' tidak berhasil diklik.<br><br>" +
                "<b>Debug Info:</b><br>" +
                "Title: " + (debugInfo.pageTitle || "-") + "<br>" +
                "Tombol ditemukan: " + (debugInfo.buttons ? debugInfo.buttons.join(", ") : "-") + "<br><br>" +
                "<b>Kemungkinan Penyebab:</b><br>" +
                "• Halaman masih loading<br>" +
                "• Ada overlay yang menghalangi<br>" +
                "• Tombol belum fully rendered<br><br>" +
                "<b>Solusi:</b><br>" +
                "1. Refresh halaman dan coba lagi<br>" +
                "2. Tunggu lebih lama sebelum klik<br>" +
                "3. Coba di browser biasa untuk test",
                "⚠️ Error Klik"
            );
            
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
        
        RoCatUI.log("✅ Klik 'Continue with email' berhasil!");
        RoCatUI.addAlert("✅ Form email muncul!", "success");

        // ===== STEP 3: Isi Email =====
        RoCatUI.log("✉️ Mengisi email: " + email);
        
        // Tunggu input email muncul
        var emailInputReady = false;
        for (var retry = 0; retry < 8; retry++) {
            var checkInput = page.evaluate(function() {
                var inputs = document.querySelectorAll('input[type="email"], input[name="username"], input[placeholder*="Email"]');
                for (var i = 0; i < inputs.length; i++) {
                    if (inputs[i].offsetParent !== null) {
                        return { found: true };
                    }
                }
                return { found: false };
            });
            
            if (checkInput && checkInput.found) {
                emailInputReady = true;
                break;
            }
            RoCatUI.log("⏳ Menunggu input email muncul... (percobaan " + (retry+1) + "/8)");
            page.waitForTimeout(1000);
        }
        
        if (!emailInputReady) {
            RoCatUI.addAlert("⚠️ Input email tidak muncul", "warning");
            RoCatUI.log("❌ Input email tidak ditemukan");
            
            // Screenshot kondisi error
            var errorScreenshot = page.screenshot({ quality: 90 });
            if (errorScreenshot) {
                RoCatUI.addImage(errorScreenshot, "📸 Error: Input Email Tidak Muncul", true);
            }
            return;
        }
        
        // Isi email dengan page.fill (React-friendly)
        try {
            page.fill('input[type="email"], input[name="username"]', email);
            RoCatUI.log("✅ Email berhasil diisi dengan page.fill()");
        } catch (e) {
            RoCatUI.log("⚠️ page.fill gagal, mencoba evaluate...");
            var fillResult = page.evaluate(function(email) {
                var inputs = document.querySelectorAll('input[type="email"], input[name="username"], input[placeholder*="Email"]');
                for (var i = 0; i < inputs.length; i++) {
                    var input = inputs[i];
                    if (input.offsetParent !== null) {
                        input.focus();
                        input.value = email;
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        return { success: true };
                    }
                }
                return { success: false };
            }, email);
            
            if (fillResult && fillResult.success) {
                RoCatUI.log("✅ Email diisi dengan evaluate");
            } else {
                RoCatUI.log("❌ Gagal mengisi email");
                return;
            }
        }
        
        page.waitForTimeout(1500);
        
        var s3 = page.screenshot({ quality: 90 });
        if (s3) {
            stepScreenshots.push({ step: "Email Diisi", path: s3 });
            RoCatUI.addImage(s3, "📸 STEP 3: Email Diisi", true);
        }

        // ===== STEP 4: Klik Continue =====
        RoCatUI.log("🖱️ Mencari tombol 'Continue'...");
        
        // Gunakan native touch click
        try {
            page.click('button:has-text("Continue"), button:has-text("Lanjutkan")');
            RoCatUI.log("✅ Klik Continue berhasil");
        } catch (e) {
            RoCatUI.log("⚠️ Gagal klik Continue, mencoba evaluate...");
            var continueResult = page.evaluate(function() {
                var buttons = document.querySelectorAll('button, a, div[role="button"]');
                var targets = ['Continue', 'Lanjutkan', 'Next', 'Selanjutnya'];
                for (var i = 0; i < buttons.length; i++) {
                    var btn = buttons[i];
                    var text = (btn.textContent || "").trim();
                    for (var t = 0; t < targets.length; t++) {
                        if (text.includes(targets[t])) {
                            if (btn.offsetParent !== null) {
                                btn.click();
                                return { success: true, text: text };
                            }
                        }
                    }
                }
                return { success: false };
            });
            
            if (continueResult && continueResult.success) {
                RoCatUI.log("✅ Klik Continue berhasil dengan evaluate");
            }
        }
        
        page.waitForTimeout(1800);

        var s4 = page.screenshot({ quality: 90 });
        if (s4) {
            stepScreenshots.push({ step: "Setelah Klik Continue", path: s4 });
            RoCatUI.addImage(s4, "📸 STEP 4: Setelah Klik Continue", true);
        }

        // ===== STEP 5: Isi Password =====
        RoCatUI.log("🔑 Mengisi password...");
        
        try {
            page.fill('input[type="password"]', password);
            RoCatUI.log("✅ Password diisi dengan page.fill()");
        } catch (e) {
            RoCatUI.log("⚠️ page.fill gagal, mencoba evaluate...");
            var passResult = page.evaluate(function(password) {
                var inputs = document.querySelectorAll('input[type="password"]');
                for (var i = 0; i < inputs.length; i++) {
                    var input = inputs[i];
                    if (input.offsetParent !== null) {
                        input.focus();
                        input.value = password;
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        return { success: true };
                    }
                }
                return { success: false };
            }, password);
            
            if (passResult && passResult.success) {
                RoCatUI.log("✅ Password diisi dengan evaluate");
            }
        }
        
        page.waitForTimeout(1500);

        var s5 = page.screenshot({ quality: 90 });
        if (s5) {
            stepScreenshots.push({ step: "Password Diisi", path: s5 });
            RoCatUI.addImage(s5, "📸 STEP 5: Password Diisi", true);
        }

        // ===== STEP 6: Klik Sign Up =====
        RoCatUI.log("🖱️ Mencari tombol 'Sign Up'...");
        
        try {
            page.click('button:has-text("Sign Up"), button:has-text("Sign up"), button:has-text("Daftar")');
            RoCatUI.log("✅ Klik Sign Up berhasil");
        } catch (e) {
            RoCatUI.log("⚠️ Gagal klik Sign Up, mencoba evaluate...");
            var signupResult = page.evaluate(function() {
                var buttons = document.querySelectorAll('button, a, div[role="button"]');
                var targets = ['Sign Up', 'Sign up', 'Signup', 'Register', 'Daftar'];
                for (var i = 0; i < buttons.length; i++) {
                    var btn = buttons[i];
                    var text = (btn.textContent || "").trim();
                    for (var t = 0; t < targets.length; t++) {
                        if (text.includes(targets[t])) {
                            if (btn.offsetParent !== null) {
                                btn.click();
                                return { success: true, text: text };
                            }
                        }
                    }
                }
                return { success: false };
            });
            
            if (signupResult && signupResult.success) {
                RoCatUI.log("✅ Klik Sign Up berhasil dengan evaluate");
            }
        }
        
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
        
        // Fill Year
        try {
            page.fill('input[placeholder*="Year"], input[placeholder*="Tahun"]', yearStr);
            RoCatUI.log("✅ Year diisi");
        } catch (e) {
            RoCatUI.log("⚠️ Gagal isi Year, mencoba evaluate...");
            page.evaluate(function(year) {
                var inputs = document.querySelectorAll('input[placeholder*="Year"], input[placeholder*="Tahun"]');
                for (var i = 0; i < inputs.length; i++) {
                    var input = inputs[i];
                    if (input.offsetParent !== null) {
                        input.value = year;
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        return;
                    }
                }
            }, yearStr);
        }
        page.waitForTimeout(500);

        var s7a = page.screenshot({ quality: 90 });
        if (s7a) {
            stepScreenshots.push({ step: "Year Diisi", path: s7a });
            RoCatUI.addImage(s7a, "📸 STEP 7a: Year Diisi", true);
        }

        // Select Month - gunakan native touch click
        try {
            page.click('[role="combobox"]:has-text("Month"), [role="combobox"]:has-text("Bulan")');
            page.waitForTimeout(500);
            page.click('option:has-text("' + monthName + '"), [role="option"]:has-text("' + monthName + '")');
            RoCatUI.log("✅ Month dipilih");
        } catch (e) {
            RoCatUI.log("⚠️ Gagal pilih Month, mencoba evaluate...");
            page.evaluate(function(monthName) {
                var selects = document.querySelectorAll('select, [role="combobox"]');
                for (var i = 0; i < selects.length; i++) {
                    var sel = selects[i];
                    if (sel.offsetParent !== null) {
                        sel.click();
                        var options = sel.querySelectorAll('option, [role="option"]');
                        for (var o = 0; o < options.length; o++) {
                            if ((options[o].textContent || "").includes(monthName)) {
                                options[o].click();
                                return;
                            }
                        }
                    }
                }
            }, monthName);
        }
        page.waitForTimeout(500);

        var s7b = page.screenshot({ quality: 90 });
        if (s7b) {
            stepScreenshots.push({ step: "Month Dipilih", path: s7b });
            RoCatUI.addImage(s7b, "📸 STEP 7b: Month Dipilih", true);
        }

        // Select Day
        try {
            page.click('[role="combobox"]:has-text("Day"), [role="combobox"]:has-text("Hari")');
            page.waitForTimeout(500);
            page.click('option:has-text("' + dayStr + '"), [role="option"]:has-text("' + dayStr + '")');
            RoCatUI.log("✅ Day dipilih");
        } catch (e) {
            RoCatUI.log("⚠️ Gagal pilih Day, mencoba evaluate...");
            page.evaluate(function(dayStr) {
                var selects = document.querySelectorAll('select, [role="combobox"]');
                for (var i = 0; i < selects.length; i++) {
                    var sel = selects[i];
                    if (sel.offsetParent !== null) {
                        sel.click();
                        var options = sel.querySelectorAll('option, [role="option"]');
                        for (var o = 0; o < options.length; o++) {
                            if ((options[o].textContent || "").trim() === dayStr) {
                                options[o].click();
                                return;
                            }
                        }
                    }
                }
            }, dayStr);
        }
        page.waitForTimeout(500);

        var s7c = page.screenshot({ quality: 90 });
        if (s7c) {
            stepScreenshots.push({ step: "Day Dipilih", path: s7c });
            RoCatUI.addImage(s7c, "📸 STEP 7c: Day Dipilih", true);
        }

        // ===== STEP 8: Klik Next =====
        RoCatUI.log("🖱️ Mencari tombol 'Next'...");
        
        try {
            page.click('button:has-text("Next"), button:has-text("Lanjutkan"), button:has-text("Submit")');
            RoCatUI.log("✅ Klik Next berhasil");
        } catch (e) {
            RoCatUI.log("⚠️ Gagal klik Next, mencoba evaluate...");
            page.evaluate(function() {
                var buttons = document.querySelectorAll('button, a, div[role="button"]');
                var targets = ['Next', 'Lanjutkan', 'Submit', 'Berikutnya'];
                for (var i = 0; i < buttons.length; i++) {
                    var btn = buttons[i];
                    var text = (btn.textContent || "").trim();
                    for (var t = 0; t < targets.length; t++) {
                        if (text.includes(targets[t])) {
                            if (btn.offsetParent !== null) {
                                btn.click();
                                return;
                            }
                        }
                    }
                }
            });
        }
        
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
            var otpElements = document.querySelectorAll('[class*="otp"], [id*="otp"], [placeholder*="OTP"]');
            if (otpElements.length > 0) {
                hasOTP = true;
            }
            
            var allText = document.body.textContent || "";
            if (allText.toLowerCase().includes('otp') || 
                allText.toLowerCase().includes('verification') || 
                allText.toLowerCase().includes('verify')) {
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
        badges.push("OTP: " + (otpCheck.hasOTP ? "✅ Ya" : "❌ Tidak"));
        badges.push("URL: " + page.url().replace(/^https?:\/\//, "").substring(0, 25));
        RoCatUI.addBadgeGroup(badges);

        // Info akun
        var infoHtml = "<b>✅ Registrasi Selesai!</b><br><br>";
        infoHtml += "<b>📧 Email:</b> " + email + "<br>";
        infoHtml += "<b>🔑 Password:</b> " + password + "<br>";
        infoHtml += "<b>📅 Tanggal Lahir:</b> " + monthName + " " + dayStr + ", " + yearStr + "<br><br>";
        
        if (otpCheck.hasOTP) {
            infoHtml += "<b>⚠️ OTP Diperlukan!</b><br>";
            infoHtml += "Cek email Anda untuk kode verifikasi.<br>";
            infoHtml += "Masukkan OTP di browser untuk menyelesaikan registrasi.<br><br>";
        } else {
            infoHtml += "<b>✅ Tidak Perlu OTP</b><br>";
            infoHtml += "Akun mungkin sudah berhasil dibuat.<br><br>";
        }
        
        infoHtml += "<b>📸 Screenshot:</b><br>";
        for (var i = 0; i < stepScreenshots.length; i++) {
            infoHtml += (i+1) + ". " + stepScreenshots[i].step + "<br>";
        }
        
        infoHtml += "<br><b>⚠️ Catatan:</b><br>";
        infoHtml += "• Simpan data ini dengan aman<br>";
        infoHtml += "• Akun ini adalah akun NON-PRO<br>";
        infoHtml += "• Native touch click (Tahap 30) digunakan untuk klik<br>";
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
            otp_required: otpCheck.hasOTP,
            total_screenshots: stepScreenshots.length,
            screenshots: screenshotsInfo,
            native_touch_used: true,
            status: "selesai"
        }, "📊 Detail Lengkap", true);

        RoCatUI.log("✅ Registrasi selesai! Total " + stepScreenshots.length + " screenshot.");
        
        if (otpCheck.hasOTP) {
            RoCatUI.addAlert("📧 Cek email untuk OTP!", "info");
        } else {
            RoCatUI.addAlert("✅ Akun berhasil dibuat!", "success");
        }

    } catch (e) {
        var errorScreenshot = page ? page.screenshot({ quality: 90 }) : null;
        
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