// ==UserScript==
// @name         CapCut - Klik Lanjutkan dengan alamat email (Presisi)
// @version      3.0.0
// @description  Temukan dan klik tombol "Lanjutkan dengan alamat email" di CapCut dengan selector presisi
// @author       RoCat User
// @category     Tools
// @match        https://www.capcut.com/*
// ==/UserScript==

function onLaunch() {
    RoCat.render([
        { type: "clear" },
        { type: "badges", badges: ["🎯 CapCut Presisi Click Test"] },
        { type: "button", label: "🎯 Klik Lanjutkan dengan alamat email", fn: "clickContinueEmail" },
        { type: "alert", message: "Akan cari elemen dengan teks 'Lanjutkan dengan alamat email' dan klik", level: "info" }
    ]);
}

function clickContinueEmail() {
    try {
        var url = "https://www.capcut.com/id-id/signup";
        
        RoCat.render([
            { type: "clear" },
            { type: "button", label: "🏠 Home", fn: "onLaunch" },
            { type: "badges", badges: ["⏳ Loading..."] },
            { type: "alert", message: "Membuka halaman CapCut...", level: "info" }
        ]);

        RoCatUI.log("📡 Membuka: " + url);

        // 1) Buka halaman
        page.goto(url, { waitUntil: "load", timeout: 30000 });
        page.waitForTimeout(3000);

        // 2) Screenshot sebelum klik
        var beforeScreenshot = page.screenshot({ quality: 95 });
        if (beforeScreenshot) {
            RoCatUI.addImage(beforeScreenshot, "📸 SEBELUM Klik - Halaman Awal", true);
        }

        // 3) Cari elemen "Lanjutkan dengan alamat email" dengan berbagai metode
        RoCatUI.log("🔍 Mencari elemen 'Lanjutkan dengan alamat email'...");
        
        // Method 1: Cari berdasarkan class dan teks
        var searchResult = page.evaluate(function() {
            var results = [];
            
            // Cari semua elemen dengan class yang mengandung "lv-account-login-form-main-field"
            var fields = document.querySelectorAll('[class*="lv-account-login-form-main-field"]');
            
            for (var i = 0; i < fields.length; i++) {
                var el = fields[i];
                var text = el.textContent || "";
                var innerText = el.innerText || "";
                
                // Cek apakah mengandung "Lanjutkan dengan alamat email"
                if (text.includes('Lanjutkan dengan alamat email') || innerText.includes('Lanjutkan dengan alamat email')) {
                    var isClickable = el.tagName === 'BUTTON' || 
                                     el.tagName === 'A' ||
                                     el.getAttribute('role') === 'button' ||
                                     el.onclick !== null ||
                                     el.style.cursor === 'pointer';
                    
                    results.push({
                        tag: el.tagName,
                        className: el.className || "",
                        text: text.trim(),
                        isClickable: isClickable,
                        html: el.outerHTML || "",
                        // Dapatkan parent jika perlu
                        parentTag: el.parentElement ? el.parentElement.tagName : "",
                        parentClass: el.parentElement ? el.parentElement.className : ""
                    });
                }
            }
            
            // Jika tidak ditemukan, coba cari berdasarkan teks aja
            if (results.length === 0) {
                var allElements = document.querySelectorAll('*');
                for (var i = 0; i < allElements.length; i++) {
                    var el = allElements[i];
                    var text = el.textContent || "";
                    if (text.includes('Lanjutkan dengan alamat email')) {
                        results.push({
                            tag: el.tagName,
                            className: el.className || "",
                            text: text.trim().substring(0, 100),
                            isClickable: false,
                            html: (el.outerHTML || "").substring(0, 200)
                        });
                    }
                }
            }
            
            return results;
        });

        // 4) Tampilkan hasil pencarian
        if (searchResult && searchResult.length > 0) {
            RoCatUI.log("✅ Ditemukan " + searchResult.length + " elemen dengan teks 'Lanjutkan dengan alamat email'");
            RoCatUI.addJsonLog(searchResult, "🎯 Elemen yang Ditemukan", true);
            
            // 5) Klik elemen pertama yang ditemukan
            var clickSuccess = false;
            var clickError = "";
            
            try {
                RoCatUI.log("🖱️ Mencoba klik elemen...");
                
                // Gunakan evaluate untuk klik
                var clickResult = page.evaluate(function() {
                    // Cari elemen dengan class dan teks
                    var fields = document.querySelectorAll('[class*="lv-account-login-form-main-field"]');
                    
                    for (var i = 0; i < fields.length; i++) {
                        var el = fields[i];
                        var text = el.textContent || "";
                        var innerText = el.innerText || "";
                        
                        if (text.includes('Lanjutkan dengan alamat email') || innerText.includes('Lanjutkan dengan alamat email')) {
                            // Coba klik
                            try {
                                el.click();
                                return {
                                    success: true,
                                    tag: el.tagName,
                                    className: el.className,
                                    text: text.trim()
                                };
                            } catch (e) {
                                return {
                                    success: false,
                                    error: e.message,
                                    tag: el.tagName
                                };
                            }
                        }
                    }
                    
                    // Jika tidak ditemukan dengan class, coba cari berdasarkan teks
                    var allElements = document.querySelectorAll('*');
                    for (var i = 0; i < allElements.length; i++) {
                        var el = allElements[i];
                        var text = el.textContent || "";
                        if (text.includes('Lanjutkan dengan alamat email') && text.length < 100) {
                            try {
                                el.click();
                                return {
                                    success: true,
                                    tag: el.tagName,
                                    text: text.trim()
                                };
                            } catch (e) {
                                return {
                                    success: false,
                                    error: e.message
                                };
                            }
                        }
                    }
                    
                    return {
                        success: false,
                        error: "Elemen tidak ditemukan"
                    };
                });
                
                if (clickResult && clickResult.success) {
                    clickSuccess = true;
                    RoCatUI.log("✅ Berhasil klik elemen: " + clickResult.tag);
                    RoCatUI.addAlert("✅ Berhasil mengklik 'Lanjutkan dengan alamat email'!", "success");
                } else {
                    clickError = clickResult ? clickResult.error : "Unknown error";
                    RoCatUI.log("❌ Gagal klik: " + clickError);
                }
                
            } catch (e) {
                clickError = e.message;
                RoCatUI.log("❌ Error saat klik: " + e.message);
            }

            // 6) Tunggu setelah klik
            page.waitForTimeout(2000);

            // 7) Screenshot setelah klik
            var afterScreenshot = page.screenshot({ quality: 95 });
            if (afterScreenshot) {
                RoCatUI.addImage(afterScreenshot, "📸 SETELAH Klik - Hasilnya", true);
            }

            // 8) Ambil HTML setelah klik untuk analisis
            var htmlAfter = page.content();
            var docAfter = RoCatDOM.parse(htmlAfter);
            
            // Cek apakah ada form email muncul
            var emailInputs = docAfter.find("input[type='email']");
            var passwordInputs = docAfter.find("input[type='password']");
            var forms = docAfter.find("form");
            
            // 9) Tampilkan hasil
            RoCat.render([
                { type: "clear" },
                { type: "button", label: "🏠 Home", fn: "onLaunch" },
                { type: "button", label: "🔄 Coba Lagi", fn: "clickContinueEmail" }
            ]);

            // Tampilkan screenshot
            if (beforeScreenshot) {
                RoCatUI.addImage(beforeScreenshot, "📸 SEBELUM Klik", true);
            }
            if (afterScreenshot) {
                RoCatUI.addImage(afterScreenshot, "📸 SETELAH Klik", true);
            }

            // Badges status
            var badges = [];
            badges.push("Status: " + (clickSuccess ? "✅ Berhasil Klik" : "❌ Gagal Klik"));
            badges.push("Elemen Ditemukan: " + searchResult.length);
            badges.push("Email Input: " + (emailInputs.length > 0 ? "✅" : "❌"));
            badges.push("Password Input: " + (passwordInputs.length > 0 ? "✅" : "❌"));
            badges.push("Forms: " + forms.length);
            badges.push("URL: " + page.url().replace(/^https?:\/\//, "").substring(0, 25));
            RoCatUI.addBadgeGroup(badges);

            // Alert hasil
            if (clickSuccess) {
                if (emailInputs.length > 0) {
                    RoCatUI.addAlert("✅ Berhasil! Form email muncul setelah klik!", "success");
                } else {
                    RoCatUI.addAlert("✅ Berhasil klik, tapi form email belum terlihat", "info");
                }
            } else {
                RoCatUI.addAlert("⚠️ Gagal mengklik tombol", "warning");
            }

            // Detail JSON
            RoCatUI.addJsonLog({
                url_awal: url,
                url_sekarang: page.url(),
                elemen_ditemukan: searchResult.length,
                berhasil_klik: clickSuccess,
                error: clickError || "tidak ada error",
                email_inputs: emailInputs.length,
                password_inputs: passwordInputs.length,
                forms: forms.length,
                screenshot_before: beforeScreenshot || "gagal",
                screenshot_after: afterScreenshot || "gagal"
            }, "📊 Detail", true);

            // Preview elemen yang ditemukan
            if (searchResult.length > 0) {
                var previewHtml = "<b>🎯 Elemen yang Ditemukan</b><br><br>";
                for (var i = 0; i < Math.min(searchResult.length, 3); i++) {
                    var item = searchResult[i];
                    previewHtml += "<b>" + (i+1) + ".</b> Tag: " + item.tag + "<br>";
                    previewHtml += "Class: " + (item.className || "-") + "<br>";
                    previewHtml += "Text: " + item.text.substring(0, 50) + "<br><br>";
                }
                RoCatUI.addHtmlPreview(previewHtml, "🔍 Detail Elemen");
            }

        } else {
            // Tidak ditemukan
            RoCatUI.log("❌ Tidak ditemukan elemen 'Lanjutkan dengan alamat email'");
            
            // Coba cari elemen yang mirip
            var similarElements = page.evaluate(function() {
                var results = [];
                var allEl = document.querySelectorAll('*');
                for (var i = 0; i < allEl.length; i++) {
                    var el = allEl[i];
                    var text = el.textContent || "";
                    if ((text.includes('Continue') || text.includes('email') || text.includes('Email')) && text.length < 100) {
                        results.push({
                            tag: el.tagName,
                            text: text.trim().substring(0, 100),
                            className: el.className || ""
                        });
                    }
                }
                return results.slice(0, 10);
            });
            
            RoCat.render([
                { type: "clear" },
                { type: "button", label: "🏠 Home", fn: "onLaunch" },
                { type: "button", label: "🔄 Coba Lagi", fn: "clickContinueEmail" },
                { type: "alert", message: "❌ Tidak ditemukan tombol 'Lanjutkan dengan alamat email'", level: "error" }
            ]);
            
            RoCatUI.addBadgeGroup(["❌ Tidak Ditemukan", "Coba: " + (similarElements.length > 0 ? similarElements.length + " elemen mirip" : "tidak ada")]);
            RoCatUI.addJsonLog(similarElements, "🔍 Elemen yang Mirip", true);
            
            var screenshotGagal = page.screenshot({ quality: 90 });
            if (screenshotGagal) {
                RoCatUI.addImage(screenshotGagal, "📸 Halaman - Tidak Ada Tombol", true);
            }
        }

        RoCatUI.log("✅ Selesai!");

    } catch (e) {
        // Error handling
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