
# Role and Objective
Kamu adalah **Senior Android Engineer dan QA Automation Specialist**. Pada langkah sebelumnya, yaitu "Tahap 26: Resolusi Kritis JavaScript Engine via Dokumentasi Internal", kita telah mencoba memperbaiki masalah *rendering* halaman yang bergantung penuh pada JavaScript agar tidak tampil *blank*.
Oleh karena itu, kita masuk ke **Tahap 27: Otomatisasi Emulator & Verifikasi Rendering Web Modern**.
Tugas utamamu di tahap ini adalah mengadaptasi *script* GitHub Actions untuk menjalankan Android Emulator, mengeksekusi pengujian UI secara spesifik pada URL modern (CapCut), menangkap log aktivitas WebView, dan melakukan perbaikan fitur jika *rendering* masih gagal.
# Memory and Constraints (CRITICAL)
 1. **BACA ATURAN MEMORI:** Wajib memperbarui log di ai_memory/00_INDEX.md dan membuat catatan teknis di ai_memory/task_YYYYMMDD_HHMM_tahap27_emulator_js_verification.md.
 2. **Fokus Pengujian:** Memastikan URL [https://www.capcut.com/id-id/signup](https://www.capcut.com/id-id/signup) terender dengan sempurna (tidak *blank* putih) di dalam WebView.
# Execution Plan (Kerjakan Secara Bertahap)
### Tahap 27.1: Penyesuaian Script CI/CD (GitHub Actions)
 * Evaluasi *script* YAML emulator yang diberikan sebelumnya.
 * Ubah identitas *package* app.komikku.roccky menjadi *package* aplikasi kita yang sebenarnya (misalnya app.rocat).
 * Sesuaikan *path* instalasi APK pada perintah adb install agar secara akurat menunjuk ke *output build* proyek kita (contoh: app/build/outputs/apk/debug/app-debug.apk).
### Tahap 27.2: Modifikasi Skenario Pengujian (Monkey ke Intent Spesifik)
 * Hapus perintah adb shell monkey yang bersifat acak.
 * Ganti dengan eksekusi adb shell am start (Intent) yang secara eksplisit membuka fitur In-App Browser kita dan langsung memuat URL: [https://www.capcut.com/id-id/signup](https://www.capcut.com/id-id/signup).
 * Tambahkan perintah jeda (sleep 15 atau lebih) setelah aplikasi terbuka untuk memberikan waktu bagi mesin JavaScript merender halaman *Single Page Application* (SPA) tersebut secara penuh.
### Tahap 27.3: Ekstraksi Log & Tangkapan Layar
 * Modifikasi perintah ekstraksi logcat agar secara spesifik menarik *log* dari *tag* WebViewJS (yang diimplementasikan pada Tahap 26) beserta *error* sistem lainnya.
 * Pastikan alur pengambilan tangkapan layar (screencap) berjalan dengan baik sehingga kita memiliki bukti visual apakah halaman berhasil dimuat atau masih *blank*.
### Tahap 27.4: Evaluasi & Perbaikan Fitur Browser
 * Analisis asumsi hasil *logcat* dan *screencap*. Jika situs web modern seperti CapCut diindikasikan masih gagal dimuat (misalnya karena terblokir deteksi otomatis atau isu DOM/CORS), segera modifikasi pengaturan WebView di kode Android kita.
 * Pastikan implementasi penanganan agen pengguna (*User-Agent*), perizinan DOM, dan eksekusi JavaScript benar-benar tanpa celah untuk web modern.
 * Jalankan kompilasi menyeluruh (./gradlew assembleDebug) setelah perbaikan dilakukan.
