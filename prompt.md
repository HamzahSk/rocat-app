
# Role and Objective
Kamu adalah **Senior Android Engineer dan arsitek inti aplikasi RoCat**. Pada tahap-tahap sebelumnya, kita telah membangun mesin skrip berbasis Rhino 1.7.15 yang mengeksekusi JavaScript secara sinkron menggunakan fetch (OkHttp) dan RoCatDOM (Jsoup).
Namun, banyak situs web modern yang sangat bergantung pada JavaScript (SPA, perlindungan Cloudflare tingkat lanjut, dll.) yang tidak bisa di-*scrape* hanya dengan HTTP *request* statis.
Oleh karena itu, kita masuk ke **Tahap 29: Implementasi Browserless/Puppeteer-like API Engine & Pembaruan Dokumentasi**.
Tugas utamamu adalah merancang API baru (seperti page.goto, wait_until, click, type, screenshot) yang mengendalikan sebuah *headless* WebView di Android, serta **memperbarui dokumentasi resmi** agar API baru ini tercatat dengan baik. Sistem baru ini harus berjalan berdampingan dengan API fetch/RoCatDOM lama tanpa merusak *backward compatibility*.
# Memory and Constraints (CRITICAL)
 1. **BACA ATURAN MEMORI:** Wajib memperbarui log di ai_memory/00_INDEX.md dan membuat catatan teknis di ai_memory/task_YYYYMMDD_HHMM_tahap29_puppeteer_engine.md.
 2. **Sifat Sinkron Rhino:** Ingat bahwa mesin Rhino kita **TIDAK mendukung async/await**. Panggilan seperti page.goto(url) atau page.click() dari dalam skrip JS harus dikelola menggunakan mekanisme *thread-blocking* (seperti CountDownLatch atau *Coroutines runBlocking*) di sisi Kotlin, sehingga skrip JS tetap bisa ditulis secara sekuensial.
 3. **Dukungan Ganda:** Skrip yang menggunakan fetch() dan RoCatDOM harus tetap bekerja 100% normal tanpa gangguan. Objek baru (misal: page) hanya aktif jika dipanggil secara eksplisit.
 4. **Anti-Crash:** Sama seperti implementasi Tahap 22.1, setiap *error/throw* di dalam bridge harus ditangkap oleh Kotlin dan tidak boleh menyebabkan aplikasi *crash*.
# Execution Plan (Kerjakan Secara Bertahap)
### Tahap 29.1: Arsitektur Headless WebView (Browser Engine)
 * Buat kelas pengelola WebView di latar belakang (*hidden/headless WebView*) di Android.
 * Konfigurasikan WebView ini agar mendukung eksekusi JavaScript secara penuh, penyimpanan DOM, dan injeksi *User-Agent* khusus.
 * Pastikan *lifecycle* WebView ini dikelola dengan aman untuk mencegah *memory leak* saat *Script Canvas* dibuka atau ditutup.
### Tahap 29.2: Pembuatan Bridge Kotlin <-> Rhino (RoCatBrowser) Lengkap
 * Buat *bridge* baru (misalnya ScriptBrowserBridge yang diekspos sebagai global objek page ke dalam mesin Rhino).
 * Implementasikan fungsi-fungsi berikut di sisi Kotlin yang memblokir *thread* Rhino secara aman:
   * page.goto(url): Memuat URL dan menunggu hingga halaman selesai dimuat.
   * page.waitForSelector(selector, timeout): Menunggu hingga elemen DOM tertentu dirender.
   * page.click(selector): Menyimulasikan klik pada elemen HTML tertentu.
   * page.type(selector, text): Menyimulasikan input teks ke dalam kolom *input*.
   * page.scrollTo(x, y) atau page.scrollBottom(): Menggulir halaman untuk memicu *lazy-load*.
   * page.evaluate(jsString): Mengirim skrip untuk dieksekusi di dalam konteks web WebView dan mengembalikan hasilnya.
   * page.content(): Mengambil *source* HTML (DOM yang sudah dirender JS) secara penuh.
   * page.screenshot(fileName): Mengambil tangkapan layar dari WebView yang sedang berjalan dan menyimpannya.
### Tahap 29.3: Penyesuaian Komunikasi Thread
 * Terapkan mekanisme komunikasi antar-*thread* (misalnya *Handler* atau *MainScope Coroutines*) karena metode WebView di Android hanya bisa dipanggil dari Main/UI Thread, sedangkan Rhino berjalan di Background Thread.
### Tahap 29.4: Pembaruan Dokumentasi (DOCS_SCRIPTING.md)
 * Buka dan baca file DOCS_SCRIPTING.md.
 * Tambahkan bab baru (misalnya **Bab 7. Browserless / Headless WebView API**) di bagian bawah dokumen.
 * Tuliskan dokumentasi lengkap mengenai objek global page beserta semua metode barunya (goto, click, type, dll.) mengikuti gaya penulisan dan format *Markdown* tabel yang sudah ada di dokumen tersebut.
 * Berikan satu contoh *snippet* kode (*Boilerplate*) cara menggabungkan page.goto dengan RoCatDOM.parse dan RoCatUI.addImage.
### Tahap 29.5: Pengujian Kompatibilitas
 * Buat draf skrip JS sederhana (contoh test_browserless.js) yang mendemonstrasikan metode baru (navigasi, klik, ketik, ekstrak DOM, *screenshot*).
 * Lakukan kompilasi (./gradlew assembleDebug) dan pastikan arsitektur baru berjalan mulus tanpa mengganggu metode *fetch* lama.
