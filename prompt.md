
### Prompt Fase 31: Stabilisasi Komponen UI, Perbaikan Media, dan Peningkatan Template
**Role & Objective**
Kamu adalah **Senior Android Engineer dan arsitek inti aplikasi RoCat**. Kita sekarang masuk ke **Tahap 31: Stabilisasi Komponen UI, Perbaikan Media, dan Peningkatan Template**.
Secara fungsional skrip sudah berjalan, namun terdapat beberapa *bug* kritis pada antarmuka pengguna (UI) yang dihasilkan dari eksekusi skrip:
 1. **Bug Tombol:** Jika terdapat lebih dari satu tombol, mengklik tombol pertama akan memicu animasi *loading* pada tombol kedua (meskipun tidak ada aksi *load* sebenarnya pada tombol kedua).
 2. **Bug Media (Image & Video):** Tampilan layar penuh (*full screen*) terasa "flat" dan tidak membaur dengan baik. Lebih parah lagi, fitur *download* mengalami *error* dan gagal menyimpan file ke *storage* perangkat.
 3. **Kekurangan Fitur Template:** Template UI yang ada masih terlalu kaku dan butuh peningkatan, seperti penambahan tombol *copy* (salin) pada komponen yang relevan.
**Execution Plan (Kerjakan Secara Bertahap)**
Tolong lakukan investigasi dan selesaikan tugas-tugas berikut:
 * **Perbaikan Isolasi State Tombol:** Periksa implementasi *state management* (kemungkinan di ScriptCanvasViewModel atau komponen Compose tombol). Pastikan *state loading* diikat secara unik ke id atau referensi tombol yang sedang ditekan, sehingga tombol lain tidak ikut bereaksi.
 * **Perbaikan Full Screen & Download Media:**
   * Perbaiki UI *full screen* untuk ImagePreviewCard dan VideoPreviewCard/RocatVideoPlayer. Pastikan penanganan WindowInsets (System Bars) diterapkan dengan benar agar tampilannya imersif dan tidak "flat".
   * Lakukan *debugging* pada MediaDownloader dan StorageManager.saveFileToScrapeFolder. Temukan dan perbaiki penyebab file gagal disimpan ke *storage* (periksa *stream handling*, validasi URI, dan izin SAF).
 * **Peningkatan Template UI:** Tingkatkan *template card* yang sudah ada (misalnya untuk teks, log, atau hasil *scrape*). Tambahkan tombol aksi seperti "Copy" menggunakan ClipboardManager dan berikan *feedback* berupa Toast kepada pengguna.
 * **Pengujian Kompatibilitas & Kompilasi:** Lakukan kompilasi (./gradlew assembleDebug) dan pastikan seluruh UI merespons dengan benar tanpa *lag* atau *crash*.
**Memory and Constraints (CRITICAL)**
 * **BACA ATURAN MEMORI:** Wajib memperbarui log di ai_memory/00_INDEX.md dan membuat catatan teknis baru (misalnya ai_memory/task_YYYYMMDD_HHMM_tahap31_ui_media_fixes.md).
 * **Sifat Sinkron Rhino:** Ingat bahwa mesin Rhino kita **TIDAK mendukung async/await**. Interaksi UI ke eksekusi skrip harus dikelola dengan aman di sisi Kotlin.
 * **Dukungan Ganda:** Skrip lama yang menggunakan fungsi UI dasar harus tetap bekerja 100% normal (tetap *backward-compatible*).
 * **Anti-Crash:** Setiap *error/throw* saat memproses UI atau mengunduh file harus ditangkap oleh Kotlin (blok try-catch) dan diberikan Toast atau *Log* UI, bukan membuat aplikasi *force close*.
Silakan langsung di-copas ke *coding assistant* kamu! Untuk template baru, apakah ada komponen spesifik lain yang ingin kamu tambahkan tombol *copy*-nya (misalnya khusus untuk *Alert Banner* atau *Badge*), atau cukup untuk hasil keluaran teks dan JSON saja?
