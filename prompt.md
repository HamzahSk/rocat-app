# Role and Objective
Kamu adalah **Senior Android Engineer, UI/UX Expert, dan WebView Architect** handal. Setelah sukses dengan Tahap 24 (Modernisasi UI & Injeksi Header Media), sekarang kita memasuki **Tahap 25: Perombakan UI/UX In-App Browser, Dukungan Mode Desktop & Optimalisasi JavaScript Engine**.
Berdasarkan evaluasi, komponen Browser saat ini memiliki tampilan yang terlalu kuno, minim fitur standar, dan gagal merender halaman web berbasis JavaScript modern secara utuh. Fokus utama tahap ini adalah:
 1. **Optimalisasi WebView Engine:** Memperbaiki konfigurasi *core* WebView agar web yang *heavy-JavaScript* (seperti SPA - Single Page Applications) dapat dimuat dan dirender dengan sempurna tanpa elemen yang hilang.
 2. **Injeksi Fitur Browser Modern:** Menambahkan fitur krusial seperti *Toggle Mode Desktop*, *Pull-to-Refresh*, dan indikator *loading* yang lebih baik.
 3. **Modernisasi UI/UX (Material 3):** Merombak total antarmuka *in-app browser* (khususnya *address bar* dan menu navigasi) menjadi lebih kekinian, bersih, dan fungsional.
# Memory and Constraints (CRITICAL)
 1. **BACA ATURAN MEMORI:**
   * Wajib memperbarui log di ai_memory/00_INDEX.md dan membuat catatan teknis di ai_memory/task_YYYYMMDD_HHMM_tahap25_browser_revamp_and_js_engine.md.
 2. **Context Path:**
   * Komponen utama berada di *layer* UI Compose yang memuat AndroidView untuk WebView (misalnya RocatBrowser.kt atau komponen serupa).
   * Pastikan manajemen *state* untuk pengaturan *browser* (seperti status Mode Desktop) disimpan menggunakan ViewModel atau DataStore.
# Execution Plan (Kerjakan Secara Bertahap)
### Tahap 25.1: Optimalisasi WebView Engine (JavaScript & DOM Fix)
Banyak situs modern tidak akan tampil jika konfigurasi WebSettings tidak maksimal.
 * Modifikasi inisialisasi WebView. Aktifkan eksekusi JavaScript secara eksplisit dengan settings.javaScriptEnabled = true.
 * Aktifkan DOM Storage (settings.domStorageEnabled = true) dan Database Storage agar situs yang membutuhkan *local storage* atau manipulasi DOM kompleks (seperti React/Vue/Angular) tidak *nge-blank* atau tampil setengah.
 * Pastikan pengaturan *mixed content* diatur dengan aman namun fungsional (settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE).
### Tahap 25.2: Implementasi Mode Desktop & Fitur Ekstra
Tambahkan kontrol *user-agent* untuk menyimulasikan pengalaman *desktop*.
 * **Desktop Mode Toggle:** Buat fungsi dinamis yang mengubah settings.userAgentString. Jika Mode Desktop aktif, gunakan *User-Agent PC/Mac* standar dan set settings.loadWithOverviewMode = true serta settings.useWideViewPort = true. Jika nonaktif, kembalikan ke *User-Agent mobile* bawaan.
 * **Pull-to-Refresh:** Bungkus komponen WebView dengan SwipeRefreshLayout (jika menggunakan *View system*) atau pullRefresh modifier di Jetpack Compose agar pengguna bisa memuat ulang halaman dengan mudah.
 * **WebChromeClient Custom:** Implementasikan WebChromeClient untuk menangkap progres *loading* halaman (onProgressChanged) dan tampilkan di UI.
### Tahap 25.3: Modernisasi UI/UX (Redesign Browser)
Tinggalkan desain *address bar* dan navigasi yang terkesan kaku/kuno.
 * **Top Bar / Address Bar:** Gunakan komponen Material Design 3. Buat *search/URL bar* yang membulat (*pill-shaped*), lengkap dengan ikon gembok (SSL indicator), tombol *clear text*, dan tombol *Refresh/Stop*.
 * **Bottom/Top Navigation Menu:** Tambahkan *overflow menu* (ikon tiga titik) yang elegan berisi opsi:
   * Beralih ke Mode Desktop (dengan *Switch/Checkbox*)
   * Muat Ulang (*Reload*)
   * Salin Tautan (*Copy Link*)
   * Buka di Browser Eksternal
 * **Progress Bar:** Tambahkan LinearProgressIndicator (MD3) yang tipis dan animatif di bawah *address bar* yang terhubung langsung dengan progres dari WebChromeClient.
### Tahap 25.4: Pengujian Rendering & Fungsionalitas
 * Uji *browser* dengan membuka situs web berat yang bergantung pada JavaScript (seperti Twitter/X versi web atau situs *streaming*). Pastikan semua elemen (termasuk *lazy-loaded images* dan *dynamic DOM*) ter-render sempurna.
 * Uji *toggle* Mode Desktop: pastikan tampilan web langsung menyesuaikan (*responsive*) saat *User-Agent* diubah dan halaman dimuat ulang.
### Tahap 25.5: Code Formatting & Build Verification
 * Jalankan *formatter* (contoh: ./gradlew spotlessApply atau ktlintFormat) agar kode Compose dan WebView yang baru ditambahkan tetap rapi.
 * Lakukan kompilasi (./gradlew assembleDebug) untuk memastikan integrasi WebView dengan Compose berjalan mulus tanpa kebocoran memori (*memory leak*).
 * Perbarui ai_memory/00_INDEX.md dengan menandai Tahap 25 selesai.
