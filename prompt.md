
### Prompt Fase 30: Perbaikan Interaksi Native pada Headless WebView
**Role & Objective**
Kamu adalah **Senior Android Engineer dan arsitek inti aplikasi RoCat**. Kita sekarang masuk ke **Tahap 30: Perbaikan Interaksi Event DOM (SPA) pada Headless WebView**.
Pada Tahap 29, kita telah merancang API baru (seperti page.goto, click, type, screenshot) yang mengendalikan *headless* WebView. Namun, saat pengujian skrip otomatisasi di situs SPA modern (seperti CapCut), eksekusi klik menggunakan fungsi JavaScript biasa di dalam page.evaluate() tidak bekerja, dan halaman tidak merespons (tangkapan layar tidak berubah).
**Execution Plan (Kerjakan Secara Bertahap)**
Tolong selesaikan tugas-tugas berikut untuk memperbaiki masalah ini:
 * **Analisis Kendala DOM & Anti-Bot:** Evaluasi mengapa injeksi click() via JavaScript gagal memicu *event listener* pada situs web modern yang dilindungi anti-bot atau dibangun menggunakan SPA (React/Vue).
 * **Pembaruan Engine RoCatBrowser (Kotlin):** Tingkatkan metode page.click(selector) di dalam ScriptBrowserBridge agar tidak hanya mengandalkan JS element.click(). Implementasikan simulasi *native touch* (misalnya menggunakan MotionEvent.ACTION_DOWN dan ACTION_UP) di HeadlessWebViewManager pada koordinat elemen yang dituju agar klik terdeteksi sebagai interaksi layar asli oleh situs.
 * **Perbaikan Skrip (JS):** Berikan versi perbaikan dari skrip CapCut saya. Alih-alih menggunakan serangkaian fungsi klik *evaluate* yang rumit, terapkan API bawaan page.click(selector) secara langsung.
 * **Pengujian Kompatibilitas & Kompilasi:** Lakukan kompilasi (./gradlew assembleDebug) dan pastikan arsitektur *native touch* baru ini berjalan mulus tanpa mengganggu metode *fetch* lama.
**Memory and Constraints (CRITICAL)**
 * **BACA ATURAN MEMORI:** Wajib memperbarui log di ai_memory/00_INDEX.md dan membuat catatan teknis di ai_memory/task_YYYYMMDD_HHMM_tahap30_puppeteer_click_fix.md.
 * **Sifat Sinkron Rhino:** Ingat bahwa mesin Rhino kita **TIDAK mendukung async/await**. Panggilan seperti page.click() harus dikelola menggunakan mekanisme *thread-blocking* (seperti CountDownLatch atau *Coroutines runBlocking*) di sisi Kotlin.
 * **Dukungan Ganda:** Skrip yang menggunakan fetch() dan RoCatDOM harus tetap bekerja 100% normal tanpa gangguan.
 * **Anti-Crash:** Setiap *error/throw* di dalam bridge harus ditangkap oleh Kotlin dan tidak boleh menyebabkan aplikasi *crash*.
