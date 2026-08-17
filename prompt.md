
# Role and Objective
Kamu adalah **Senior Android Engineer dan QA Automation Specialist**. Pada tahap sebelumnya, yaitu "Tahap 27: Otomatisasi Emulator & Verifikasi Rendering Web Modern", kita menemukan bahwa hasil *rendering* halaman web modern masih mengalami kegagalan atau tampil *blank* putih.
Oleh karena itu, kita masuk ke **Tahap 28: Refaktor Mesin JavaScript WebView Berdasarkan Referensi sweb-master**.
Tugas utamamu di tahap ini adalah menganalisis folder proyek sweb-master yang telah disediakan (sebuah proyek aplikasi *browser* simpel yang sudah diuji dan JavaScript-nya terbukti bekerja), meniru metode konfigurasi WebView dari sana, dan mengimplementasikannya ke dalam proyek kita untuk mengatasi masalah *rendering* tersebut.
# Memory and Constraints (CRITICAL)
 1. **BACA ATURAN MEMORI:** Wajib memperbarui log di ai_memory/00_INDEX.md dan membuat catatan teknis di ai_memory/task_YYYYMMDD_HHMM_tahap28_sweb_js_refactor.md.
 2. **Fokus Referensi:** Gunakan seluruh konfigurasi WebView dari folder proyek sweb-master sebagai acuan mutlak (*ground truth*) untuk eksekusi JavaScript.
 3. **Fokus Pengujian:** Memastikan URL https://www.capcut.com/id-id/signup terender dengan sempurna (tidak *blank* putih) di dalam WebView aplikasi kita.
# Execution Plan (Kerjakan Secara Bertahap)
### Tahap 28.1: Analisis Mendalam Proyek Referensi (sweb-master)
 * Telusuri struktur dan kode sumber di dalam direktori sweb-master.
 * Ekstrak dan pelajari bagaimana WebView diinisialisasi.
 * Identifikasi detail spesifik terkait pengaturan WebSettings (misalnya pengaturan *JavaScriptEnabled*, penanganan DOM, *User-Agent*, *mixed content*, maupun *cache*).
 * Cek apakah terdapat implementasi khusus pada WebChromeClient atau WebViewClient di sweb-master yang memungkinkan mesin JavaScript bekerja secara optimal.
### Tahap 28.2: Refaktor dan Integrasi Konfigurasi
 * Terapkan metode dan pengaturan WebView yang ditemukan dari sweb-master ke dalam kelas In-App Browser proyek kita.
 * Timpa atau hapus pengaturan lama yang gagal merender JavaScript pada iterasi kode sebelumnya.
 * Pastikan implementasi penanganan agen pengguna (*User-Agent*), perizinan DOM, dan eksekusi JavaScript benar-benar diadaptasi secara sempurna agar web modern tidak terblokir.
### Tahap 28.3: Verifikasi via Intent & Emulator (Adaptasi Tahap 27)
 * Setelah perbaikan selesai, kompilasi ulang proyek secara menyeluruh dengan menjalankan perintah ./gradlew assembleDebug.
 * Eksekusi pengujian menggunakan perintah adb shell am start (Intent) yang secara eksplisit membuka fitur In-App Browser kita dan langsung memuat URL: https://www.capcut.com/id-id/signup.
 * Tambahkan jeda waktu tunggu (seperti sleep 15 atau lebih) setelah aplikasi terbuka untuk memberikan waktu *rendering Single Page Application* (SPA) secara utuh.
 * Tangkap *logcat* secara spesifik untuk memastikan tidak ada *error* sistem atau DOM.
 * Ambil dan evaluasi tangkapan layar (*screencap*) untuk memastikan halaman tidak lagi *blank* putih.
