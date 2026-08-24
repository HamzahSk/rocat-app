### Prompt Fase 38: Overhaul UI/UX Mode Komik & Integrasi Coil3 Full Screen Reader (Mihon Style)

**Role & Objective**
Kamu adalah **Senior Android Engineer & UI/UX Designer** untuk aplikasi RoCat. Kita sekarang masuk ke **Tahap 38: Overhaul UI/UX Mode Komik**.
Saat ini, tampilan mode komik masih sangat berantakan: grid terasa kaku, UI tidak proporsional, dan masalah fatalnya, gambar komik sering kali tidak terlihat atau gagal dirender.

**Referensi Wajib (Harus Dianalisis Terlebih Dahulu):**
1. **Source Code Referensi:** Pelajari folder `mihon_ROCCKY` yang berada di *root directory*. Jadikan *codebase* ini sebagai **kiblat/referensi utama** untuk:
   - Pengaturan *grid layout* katalog yang dinamis dan estetis.
   - Desain dan proporsi *button*, fitur pencarian (*search*), dan *filter*.
   - Logika render gambar menggunakan **Coil3** (terutama implementasi *decode bitmap* untuk *full-screen reader*).
2. **Visual Assessment (Screenshot):** Buka dan analisis file gambar `Screenshot_2026-08-24-22-39-24-24_561ca50af7d682e47269c1681227843c.jpg` di *root folder*. Pahami tata letak yang salah saat ini (tombol raksasa, layout terbuang, gambar tidak muncul) agar kamu tahu apa yang harus diperbaiki.

**Execution Plan (Timebox: Maksimal 1 Jam):**

1. **Investigasi & Analisis Referensi:**
   - Cek screenshot untuk melihat *bug* visual saat ini.
   - Eksplorasi folder `mihon_ROCCKY` untuk memahami bagaimana arsitektur UI dan Coil3 diimplementasikan di sana.

2. **Perombakan UI/UX, Grid, & Komponen:**
   - Rombak *layout* Compose pada mode komik. Buat *grid* komik terlihat luwes, responsif, dan tidak kaku (seperti Mihon).
   - Rapikan ukuran tombol navigasi, *search bar*, dan menu filter. Hindari penggunaan ukuran *full-width* yang berlebihan dan memakan tempat (sesuaikan proporsi dengan komponen `row` dengan `flex` yang tepat).

3. **Perbaikan Image Loading (Coil3 & Full Screen Reader):**
   - Rombak total cara aplikasi memuat gambar komik. Gunakan metode *decode bitmap* Coil3 seperti yang diterapkan di referensi `mihon_ROCCKY`.
   - Pastikan *full-screen reader* berfungsi sempurna: gambar terentang dengan benar (tidak gepeng), mendukung mode *seamless* (tanpa jarak antar gambar untuk *webtoon*), dan tidak lag saat memuat *bitmap* ukuran besar.
   - Tambahkan *error state handling* yang baik jika gambar gagal dimuat (misalnya karena 403 Forbidden atau *timeout*).

4. **Code Quality & Build Test (Wajib):**
   - Pastikan kode yang ditulis rapi dan sesuai standar.
   - Jalankan *formatter*: `./gradlew spotlessApply`
   - Pastikan kompilasi sukses tanpa error: `./gradlew assembleDebug`

5. **Pembaruan Memori & Dokumentasi:**
   - Patuhi instruksi sistem pada `memory_prompt.md`.
   - Buat catatan tugas baru di folder `ai_memory/` (misal: `task_YYYYMMDD_HHMM_Fase_38.md`) yang berisi status, ringkasan perbaikan, dan langkah selanjutnya.
   - Update `ai_memory/00_INDEX.md` dengan menyematkan log terbaru ini.

**Constraints:**
- Pastikan perubahan ini hanya berdampak pada pembaca komik dan **TIDAK merusak** *scraper* atau pemutar video yang sudah berjalan.
