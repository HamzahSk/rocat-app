
### Prompt Fase 32: Resolusi Blank Screen WebView & Adopsi Fitur sweb-master
**Role & Objective**
Kamu adalah **Senior Android Engineer dan arsitek inti aplikasi RoCat**. Kita sekarang masuk ke **Tahap 32: Resolusi Kritis Blank Screen WebView & Integrasi Fitur Referensi (sweb-master)**.
Pengguna telah membuktikan bahwa halaman web modern SPA (seperti https://www.capcut.com/signup) yang sebelumnya tampil *blank* putih di RoCat, ternyata berhasil dimuat dengan sempurna menggunakan aplikasi referensi open-source dari folder sweb-master yang ada di *local environment*.
Tugas utamamu adalah membedah bagaimana sweb-master mengonfigurasi WebView-nya, lalu menerapkan perbaikan tersebut ke RoCat, sekaligus mengambil inspirasi fitur tambahan dari sana.
**Execution Plan (Kerjakan Secara Bertahap)**
Tolong lakukan investigasi mendalam pada *source code* di folder sweb-master dan eksekusi langkah-langkah berikut:
 1. **Audit & Replikasi Ekstrem WebView Settings:**
   * Bandingkan pengaturan di WebViewUtil.kt milik RoCat dengan cara sweb-master menginisialisasi WebView (cari kelas seperti MainActivity, BrowserFragment, atau WebSettings).
   * Fokus pada hal-hal krusial untuk web SPA/React modern: pastikan domStorageEnabled = true, databaseEnabled = true, javaScriptCanOpenWindowsAutomatically, dan konfigurasi WebChromeClient (terutama penanganan memori atau kuota jika ada).
   * **Cek Cookie & Third-Party:** Pastikan CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true) diaktifkan. Web modern sering *blank* jika *third-party cookies* (misal dari domain otentikasi) diblokir.
 2. **Investigasi User-Agent & Anti-Bot:**
   * Cek bagaimana sweb-master menangani User-Agent. Di RoCat, kita sebelumnya memaksakan *User-Agent* desktop/mobile tertentu (Chrome 141). Jika ini memicu *blank* (karena ditolak oleh Cloudflare/CapCut), sesuaikan logikanya mengikuti sweb-master (apakah mereka membiarkan UA bawaan *device* atau menggunakan rotasi khusus?).
 3. **Integrasi Fitur Tambahan Referensi:**
   * Berdasarkan kode di sweb-master, adopsi 1 atau 2 fitur fungsional yang bisa meningkatkan kualitas *in-app browser* RoCat. (Misalnya: cara mereka menangani *Ad-blocking* via shouldInterceptRequest, manajemen *cache/history* yang lebih baik, atau penanganan *download* media). Terapkan ke dalam arsitektur RoCat tanpa merusak fungsionalitas *scraper* Rhino yang sudah berjalan.
 4. **Kompilasi & Pengujian:**
   * Pastikan tidak ada *error* saat kompilasi (./gradlew assembleDebug).
   * Pastikan WebView tidak lagi menampilkan layar putih pada URL modern, dan *event* navigasi tetap terhubung ke BrowserViewModel kita.
**Memory and Constraints (CRITICAL)**
 * **BACA ATURAN MEMORI:** Wajib memperbarui log di ai_memory/00_INDEX.md dan membuat catatan teknis baru (misalnya ai_memory/task_YYYYMMDD_HHMM_tahap32_webview_sweb_integration.md). Jelaskan secara spesifik pengaturan apa dari sweb-master yang menjadi "obat" dari isu *blank screen* tersebut.
 * WebView harus tetap berada di layer UI (Compose), jangan pindahkan *instance* WebView ke dalam ViewModel untuk menghindari *memory leak*.
 * Semua fungsi *bridge* JavaScript (RoCatDOM, RoCatUI, Rhino engine) dari tahap sebelumnya **wajib** tetap berjalan 100% normal.
Dari fitur-fitur bawaan browser yang biasanya ada di *source code* seperti sweb-master (misalnya *Ad-Blocker* bawaan, *Incognito Mode*, atau *Advanced Download Manager*), fitur tambahan mana yang menurutmu paling ingin diprioritaskan untuk diimpor ke RoCat di fase ini?
