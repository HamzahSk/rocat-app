
### Prompt Fase 33: Perbaikan Sinkronisasi Klik SPA & Full-Page Screenshot pada Headless Browser
**Role & Objective**
Kamu adalah **Senior Android Engineer dan arsitek inti aplikasi RoCat**. Kita masuk ke **Tahap 33: Resolusi Headless Browser (Interaksi SPA & Rendering Screenshot Full-Page)**.
Pengguna melaporkan dua kendala saat menjalankan skrip otomatisasi pada halaman web modern (contoh: CapCut):
 1. Perintah klik pada elemen (seperti tombol "Lanjutkan dengan email") dilaporkan berhasil tanpa *error*, namun halaman sebenarnya tidak berubah (*screenshot* sebelum dan sesudah identik, dan elemen berikutnya tidak ditemukan).
 2. Hasil *screenshot* terpotong pada rasio 16:9. Jika mencoba melakukan *scroll* ke bawah, hasil *screenshot* hanya menampilkan layar putih kosong (*blank render out-of-bounds*).
Tugas utamamu adalah memperbaiki mekanisme *rendering* dan kalkulasi koordinat pada HeadlessWebViewManager.
**Execution Plan (Kerjakan Secara Bertahap):**
 1. **Perbaikan Full-Page Screenshot (Anti-Putih & Anti-Potong):**
   * Saat ini ukuran Headless WebView dikunci di 1366x768. Ubah mekanisme measure dan layout saat fungsi *screenshot* dipanggil.
   * Gunakan computeVerticalScrollRange() atau document.documentElement.scrollHeight untuk mengetahui tinggi asli halaman.
   * Paksa WebView untuk me-layout ulang dirinya sebesar tinggi penuh tersebut sebelum menggambar *bitmap* (Canvas). Pastikan *bitmap* yang dihasilkan memiliki tinggi dinamis sesuai konten, bukan statis 16:9, sehingga saat di-scroll tidak menjadi putih.
 2. **Perbaikan Sinkronisasi Klik & Koordinat (Native Touch):**
   * Di Tahap 30, kita sudah menggunakan dispatchNativeTap (MotionEvent). Jika UI React tidak merespons, kemungkinan koordinat klik meleset karena halaman memiliki *scroll* (CSS pixel vs View pixel belum akurat untuk sumbu Y yang di-scroll).
   * **Tugas:** Perbarui logika elementBounds. Pastikan posisi Y dari elemen dihitung dengan menambahkan window.scrollY jika perlu, lalu pastikan WebView melakukan *scroll* secara fisik (atau *layout* diperbarui) ke titik tersebut sebelum MotionEvent ditembakkan.
   * Tambahkan mekanisme invalidate() atau paksa *Compositor* untuk menggambar ulang *frame* setelah klik terjadi, agar UI SPA tersinkronisasi sebelum baris skrip berikutnya berjalan.
 3. **Verifikasi Skrip capcut_test.js:**
   * Cek kembali helper klik di file JS. Pastikan setelah memanggil page.click(), ada mekanisme *wait* atau penundaan logis yang menunggu elemen UI bereaksi (karena React butuh beberapa milidetik untuk mengubah *state* DOM).
**Memory and Constraints (CRITICAL)**
 * **BACA ATURAN MEMORI:** Catat penyelesaian ini di ai_memory/00_INDEX.md pada entri Tahap 33.
 * Jaga agar HeadlessWebView tetap anti-*crash* dan tidak menyebabkan OutOfMemoryError saat merender halaman yang sangat panjang (batasi maksimal tinggi *bitmap* jika diperlukan, misalnya 5000px).
 * Jangan merusak API RoCatPage dan kompatibilitas *polyfill* yang sudah berjalan.
 * Perbahrui docs scripting nya jika sudah selesai 
