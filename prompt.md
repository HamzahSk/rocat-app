
### Prompt Fase 42: Network Interception & XHR Capture pada Headless WebView
**Role & Objective**
Kamu adalah **Senior Android Engineer** untuk RoCat. Kita masuk ke **Tahap 42: Network Interception pada Headless WebView**. Tujuannya adalah membuat objek page mampu menangkap *response* JSON/XHR dari *background request* situs SPA.
**Mandatory Tasks (WAJIB PERTAMA)**
 * **Pembaruan Memori:** Buat task_YYYYMMDD_HHMM_Fase_42.md di ai_memory/ berisi log implementasi *Network Interception* ini. Update 00_INDEX.md.
 * **Code Quality:** Pastikan *build* sukses dengan bash ./gradlew assembleDebug.
**Analisis Masalah**
Situs seperti SaveKit memuat tombol unduhan dari *request* JSON terpisah pasca-klik. DOM parser sering gagal karena telat atau elemen disembunyikan di Shadow DOM. Solusi terbaik adalah mencegat langsung *response* fetch/XHR di dalam WebView, lalu mengeksposnya ke *scraper* via API baru page.waitForResponse(). Karena WebViewClient.shouldInterceptRequest tidak bisa membaca *body response* secara *native*, kita harus menggunakan teknik *JS Monkey-patching*.
**Execution Plan (Maks 1 Jam):**
 1. **Injeksi Interceptor JS (HeadlessWebViewManager.kt):**
   * Buat *string* JS *polyfill* yang meng-*override* window.fetch dan XMLHttpRequest.prototype.open/send.
   * Setiap kali *request* selesai, kirim URL dan *body response* (JSON/Text) ke Kotlin melalui fungsi bridge baru, misalnya RoCatBrowserBridge.onNetworkResponse(url, body).
   * Suntikkan JS ini menggunakan evaluateJavascript tepat setelah WebView selesai inisialisasi atau pada onPageStarted.
 2. **State Management di Kotlin:**
   * Tambahkan koleksi thread-safe (misal ConcurrentHashMap<String, String>) di HeadlessWebViewManager untuk menyimpan *intercepted responses* berdasarkan URL.
 3. **Ekspansi API Browserless (page):**
   * Tambahkan fungsi page.waitForResponse(urlPattern, timeout) di JS Polyfill (RoCatBrowserWrapper.js).
   * Fungsi ini akan melakukan *polling* ke bridge *native* untuk mengecek apakah ada *intercepted response* yang URL-nya cocok dengan urlPattern.
   * Jika cocok, kembalikan *body response* tersebut sebagai *string* ke skrip pengguna.
**Constraints:**
 * Pastikan *monkey-patching* tidak merusak fungsi asli fetch dan XHR dari situs target.
 * Batasi ukuran *body* yang disimpan di memori agar tidak OOM (misal, hanya simpan *response* dengan tipe application/json atau di bawah 1MB).