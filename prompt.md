Prompt Fase 34: Perombakan UI/UX Aplikasi & Perbaikan Bug Sistematis

Role & Objective

Kamu adalah Senior Android Engineer dan Lead UI/UX Designer untuk aplikasi RoCat. Kita memasuki Tahap 34: Overhaul UI/UX dan Bug Fixing Sistematis.

Laporan dari pengguna dan pengamatan langsung menunjukkan beberapa masalah kritis:

1. UI Terlalu Flat & Tidak Menarik — Aplikasi terlihat kuno, kurang elemen visual modern (gradasi, bayangan, animasi halus, ikon yang lebih ekspresif).
2. Bug Teks di Pengaturan — Pada halaman Pengaturan, teks yang tidak terbaca/terpotong (dari screenshot: L ok as i p en yi m p an an R O CA T). Ini indikasi layout yang rusak atau string resource yang salah.
3. Bug Unduhan di Browser WebView — Saat membuka tautan unduhan di WebView (seperti di vidsave.com), file tidak terunduh. WebView tidak menangani event onDownloadStart dengan benar.
4. Bug Unduhan Gambar di Skrip — Fungsi RoCatUI.addImage dan page.screenshot tidak menyimpan file dengan benar ke storage cache atau folder skrip. Gambar tidak muncul atau gagal diunduh karena path penyimpanan tidak valid atau izin tidak diperbarui.

Tujuan Utama

1. Perombakan UI Aplikasi (Lebih Modern & Berwarna)

· Gaya Material You / Dynamic Color: Terapkan tema dengan Material 3 dan dukungan warna dinamis dari wallpaper. Gunakan color scheme yang lebih hidup dengan aksen warna primer, sekunder, dan tersier.
· Komponen UI Terbaru:
  · Card: Gunakan Card dengan elevation (bayangan) dan shape yang membulat (RoundedCornerShape).
  · Tombol: Gunakan FilledTonalButton atau ElevatedButton dengan ikon untuk tindakan utama. Berikan efek ripple yang halus.
  · Dialog: Dialog yang lebih imersif dengan backdrop blur dan animasi masuk/keluar.
  · Ikon: Gunakan ikon yang lebih ekspresif dari Material Icons Extended atau Feather Icons.
· Halaman Pengaturan:
  · Perbaiki layout yang menyebabkan teks L ok as i p ... tidak terbaca.
  · Ubah item pengaturan menjadi list dengan ikon dan teks yang jelas.
  · Tambahkan preview theme (Light/Dark/System) langsung di halaman Pengaturan.
· Halaman Tambah Skrip (Add Script):
  · Tambahkan tombol "Paste" (tempel dari clipboard) di samping kolom URL dan kolom tempel sumber.
  · Tambahkan tombol "Pilih File" untuk mengimpor skrip dari file lokal (.js).
  · Buat tabs antara "Impor dari URL" dan "Tempel Sumber" menjadi lebih jelas (gunakan TabRow).
· Halaman Edit Skrip: Tambahkan syntax highlighting dasar (walaupun hanya warna yang berbeda untuk kata kunci) dan line numbers untuk memudahkan debugging. Tambahkan tombol "Undo/Redo" dan "Format" untuk merapikan kode.

2. Perbaikan Bug: Unduhan di WebView (Browser Internal)

· Implementasi DownloadListener: Tambahkan WebView.setDownloadListener untuk menangkap semua permintaan unduhan.
· Alur Unduhan: Saat onDownloadStart dipanggil, aplikasi harus:
  1. Menampilkan dialog konfirmasi kepada pengguna (nama file, ukuran).
  2. Menggunakan DownloadManager (Android system service) untuk mengunduh file ke direktori Downloads/RoCat/.
  3. Menampilkan notifikasi setelah unduhan selesai.
· Penanganan Cookie: Pastikan DownloadManager membawa cookie yang sama dengan WebView untuk mengakses tautan yang memerlukan autentikasi/cf_clearance.

3. Perbaikan Bug: Unduhan Gambar & Screenshot di Skrip

· Validasi Path Penyimpanan: Di HeadlessWebViewManager.kt dan ScriptUiBridge.kt, verifikasi bahwa direktori penyimpanan (Scrapes/<scriptId>/) sudah dibuat dan memiliki izin tulis.
· Perbaikan RoCatUI.addImage:
  · Saat allowDownload = true, gambar harus diunduh menggunakan OkHttp (dengan header yang diberikan) dan disimpan ke folder skrip.
  · Kembalikan URI file yang valid ke skrip (bukan path internal yang tidak bisa diakses).
· Perbaikan page.screenshot:
  · Pastikan file PNG yang dihasilkan ditulis ke context.cacheDir atau folder Scrapes dengan nama yang unik (gunakan timestamp).
  · Periksa apakah ada izin penyimpanan (Android 13+ menggunakan MediaStore API untuk gambar).
  · Kembalikan absolute path atau content URI yang valid.
· Penanganan Storage Access Framework (SAF): Pastikan semua operasi penyimpanan melewati SAF jika pengguna memilih direktori kustom (seperti di pengaturan "Ubah direktori penyimpanan").

4. Peningkatan HeadlessWebView (Modern & Stabil)

· User-Agent & Viewport: Update User-Agent ke versi terbaru Chrome (misal Chrome/142.0.0.0) dan pastikan viewport sudah responsif (device-width).
· Debugging: Tambahkan flag untuk mengaktifkan remote debugging (Chrome DevTools) secara opsional di developer options aplikasi.

---

Execution Plan (Kerjakan Secara Bertahap)

Tahap A: Perbaikan Cepat (Bug & Layout)

1. Perbaiki Layout Pengaturan: Cari penyebab teks L ok as i p ... di file layout/Compose. Kemungkinan besar karena Text yang overflow atau Row yang tidak teratur. Ganti dengan LazyColumn atau Column yang terstruktur dengan baik.
2. Perbaiki DownloadManager: Di BrowserActivity/WebViewFragment, tambahkan DownloadListener. Implementasikan logika untuk mengunduh file melalui DownloadManager.

Tahap B: Overhaul UI Aplikasi

1. Tema Global: Ubah Theme.kt untuk menggunakan MaterialTheme dengan skema warna dinamis.
2. Halaman Tambah Skrip: Desain ulang screen. Tambahkan tombol "Paste" (gunakan ClipboardManager) dan "Pilih File".
3. Halaman Edit Skrip: Tambahkan syntax highlighting sederhana (gunakan AnnotatedString untuk membedakan komentar, string, keyword) dan tombol kontrol (Undo/Redo).

Tahap C: Perbaikan Sistem Unduhan & Skrip

1. Perbaiki ScriptUiBridge.save: Pastikan fungsi ini menggunakan MediaStore API untuk Android 10+ atau SAF.
2. Perbaiki HeadlessWebViewManager.screenshot: Validasi write permission sebelum menulis bitmap. Gunakan Bitmap.compress dengan kualitas tinggi.
3. Perbaiki RoCatUI.addImage: Pastikan header dikirim saat mengunduh gambar dan disimpan dengan benar.

Tahap D: Dokumentasi & Memory

· Perbarui docs/DOCS_SCRIPTING.md: Tambahkan catatan tentang perubahan API penyimpanan (jika ada).
· Perbarui ai_memory/00_INDEX.md: Catat penyelesaian Tahap 34 dan perubahan besar pada UI.

---

Constraints & Memory

· Stabilitas: Pastikan tidak ada OutOfMemoryError saat mendownload file besar atau screenshot halaman panjang.
· Kompatibilitas: Perubahan UI tidak boleh merusak fungsi skrip yang sudah ada (kecuali bug yang diperbaiki).
· Memory: Catat semua perubahan di ai_memory/00_INDEX.md dengan tag #Fase34-UIOverhaul.

---

Lampiran: Bukti Bug

· Screenshot 1 (Pengaturan): Teks aneh L ok as i p en yi m p an an R O CA T.
· Screenshot 2 (Add Script): Kurang tombol "Paste" dan "Pilih File".
· Screenshot 3 (WebView): Situs vidsave.com gagal mengunduh karena DownloadListener tidak diimplementasikan.

Target Akhir:

· Aplikasi RoCat memiliki tampilan yang modern, responsif, dan menyenangkan.
· Semua tautan unduhan di WebView berfungsi normal.
· Skrip dapat mengunduh gambar dan screenshot dengan benar.
