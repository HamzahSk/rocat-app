
### Prompt Fase 30: Debugging Perbaikan Interaksi Native pada Headless WebView
**Role & Objective**
Kamu adalah **Senior Android Engineer dan arsitek inti aplikasi RoCat**. Kita sedang melanjutkan **Tahap 30: Perbaikan Interaksi Event DOM (SPA) pada Headless WebView**.
Sebelumnya, kita sudah mengubah implementasi page.click() di HeadlessWebViewManager.kt menggunakan **native touch tap** (MotionEvent.ACTION_DOWN dan ACTION_UP) yang dikirim melalui WebView.dispatchTouchEvent di pusat koordinat elemen. Tujuannya untuk menyelesaikan kendala *untrusted events* dari el.click() pada situs SPA modern (React/Vue) dan sistem anti-bot.
Namun, **kliknya tetap tidak berfungsi atau tidak memberikan respons pada WebView yang berjalan secara headless**.
**Execution Plan (Kerjakan Secara Bertahap)**
Tolong lakukan investigasi mendalam dan selesaikan tugas-tugas berikut untuk memperbaiki masalah ini:
 * **Analisis Validasi Koordinat & Density:** Evaluasi apakah elementBounds (getBoundingClientRect + scrollIntoView) mengembalikan koordinat yang tepat. Pastikan koordinat dari DOM (CSS pixels) dikonversi dengan benar ke koordinat layar Android (Device Independent Pixels / Density) sebelum dimasukkan ke MotionEvent.
 * **Fokus & State Window:** WebView yang benar-benar *headless* kadang menolak dispatchTouchEvent. Terapkan perbaikan agar WebView merespons *touch event* layaknya sedang aktif di layar (misalnya dengan memanipulasi fokus).
 * **Perbaikan Parameter & Thread Blocking:** Periksa kembali parameter pada MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0) dan mekanisme CountDownLatch di dispatchNativeTap. Pastikan UI thread tidak terblokir terlalu lama yang membuat WebView gagal merender *frame* respons dari klik tersebut.
 * **Pengujian Kompatibilitas & Kompilasi:** Lakukan kompilasi (./gradlew assembleDebug) dan pastikan arsitektur *native touch* yang sudah diperbaiki ini berjalan mulus tanpa mengganggu metode *fetch* lama.
**Memory and Constraints (CRITICAL)**
 * **BACA ATURAN MEMORI:** Wajib memperbarui log di ai_memory/00_INDEX.md dan membuat catatan teknis baru (misalnya ai_memory/task_YYYYMMDD_HHMM_tahap30_puppeteer_click_fix_v2.md).
 * **Sifat Sinkron Rhino:** Ingat bahwa mesin Rhino kita **TIDAK mendukung async/await**. Panggilan seperti page.click() harus tetap dikelola menggunakan mekanisme *thread-blocking* (seperti CountDownLatch atau *Coroutines runBlocking*) di sisi Kotlin.
 * **Dukungan Ganda:** Skrip yang menggunakan fetch() dan RoCatDOM harus tetap bekerja 100% normal tanpa gangguan.
 * **Anti-Crash:** Setiap *error/throw* di dalam *bridge* harus ditangkap oleh Kotlin dan tidak boleh menyebabkan aplikasi *crash*. Pertahankan logika catch Throwable yang sudah ada.
 
 lakukan testing pada script capcut_test.js
