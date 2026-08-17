
# Role and Objective
Kamu adalah **Senior Android Engineer dan spesialis WebView/Browser Architecture**. Kita sudah berhasil merombak UI browser di Tahap 25 menjadi jauh lebih modern, namun sayangnya **masalah rendering web berbasis JavaScript masih belum terselesaikan**. Halaman yang bergantung penuh pada JavaScript (seperti web modern atau Single Page Applications) masih sering tidak termuat dengan sempurna atau layarnya tampil *blank*.
Oleh karena itu, kita masuk ke **Tahap 26: Resolusi Kritis JavaScript Engine via Dokumentasi Internal**.
Tugas utamamu di tahap ini adalah membaca dokumentasi internal dengan saksama, menemukan akar masalahnya, dan mengimplementasikan perbaikan mutlak pada komponen WebView kita.
# Memory and Constraints (CRITICAL)
 1. **BACA ATURAN MEMORI:**
   * Wajib memperbarui log di ai_memory/00_INDEX.md dan membuat catatan teknis di ai_memory/task_YYYYMMDD_HHMM_tahap26_webview_js_critical_fix.md.
 2. **Context Path:**
   * Perbaikan difokuskan pada *file* komponen browser (misalnya RocatBrowser.kt atau *file* WebView Compose terkait).
 3. **REFERENSI UTAMA (WAJIB DIBACA):**
   * Kamu **DIWAJIBKAN** untuk membaca dan menganalisis *file* bernama DOCS_WEBVIEW.md terlebih dahulu sebelum menulis atau mengubah kode apa pun.
# Execution Plan (Kerjakan Secara Bertahap)
### Tahap 26.1: Analisis Dokumentasi DOCS_WEBVIEW.md
 * Baca secara menyeluruh isi dari DOCS_WEBVIEW.md.
 * Cari dan temukan instruksi khusus, *workaround*, atau konfigurasi wajib yang direkomendasikan dalam dokumen tersebut terkait eksekusi JavaScript, penanganan DOM, *Service Workers*, CORS, atau *Mixed Content* yang menjadi kunci agar web modern bisa dirender tanpa *error*.
### Tahap 26.2: Implementasi Fix JavaScript Engine
Berdasarkan temuan dari DOCS_WEBVIEW.md, terapkan perbaikannya ke dalam konfigurasi WebView kita:
 * Pastikan *setup* WebSettings sudah 100% mengikuti standar yang tertulis di dokumen (misalnya terkait setSupportMultipleWindows, domStorageEnabled, databaseEnabled, allowFileAccess, atau konfigurasi *cache* khusus).
 * Jika dokumen menginstruksikan implementasi khusus pada WebViewClient (seperti *override* shouldInterceptRequest, onReceivedSslError) atau WebChromeClient (seperti penanganan *quota*, jendela baru, atau perizinan), segera implementasikan sesuai panduan.
### Tahap 26.3: Integrasi dan Pembersihan Kode
 * Evaluasi pengaturan WebView dari Tahap 25. Hapus atau modifikasi *settings* yang sekiranya berkonflik dengan instruksi utama di DOCS_WEBVIEW.md.
 * Pastikan WebView sekarang benar-benar dioptimalkan untuk menjalankan *script* berat tanpa proses rendering yang terpotong.
### Tahap 26.4: Testing & JS Error Logging
 * Implementasikan fungsi WebChromeClient.onConsoleMessage (atau sejenisnya) untuk menangkap *log/error* JavaScript langsung dari halaman web dan meneruskannya ke Logcat Android. Ini sangat krusial agar jika JS masih gagal, kita bisa melihat detail *error*-nya (misalnya *SyntaxError* atau masalah *Cross-Origin*) dari Logcat.
 * Uji coba dengan memuat ulang URL web yang sebelumnya gagal tampil.
### Tahap 26.5: Update Memory & Code Formatting
 * Catat dengan spesifik konfigurasi apa saja dari DOCS_WEBVIEW.md yang akhirnya diterapkan dan menyelesaikan masalah ini ke dalam log memori (task_...).
 * Jalankan *formatter* (contoh: ./gradlew spotlessApply atau ktlintFormat) untuk menjaga kerapian kode.
 * Lakukan kompilasi menyeluruh (./gradlew assembleDebug) untuk memastikan proyek sukses di-*build* tanpa masalah kompatibilitas.
