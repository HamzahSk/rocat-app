
### Prompt Fase 39: Sinkronisasi Jaringan Coil3 & Perbaikan Indikator Loading
**Role & Objective**
Kamu adalah **Senior Android Engineer & UI/UX Designer** untuk aplikasi RoCat. Kita sekarang masuk ke **Tahap 39: Sinkronisasi Jaringan Coil3 & Perbaikan Indikator Loading**.
Pada Fase 38, kita berhasil merombak UI komik, namun muncul masalah baru: gambar *preview* di dalam reader sering gagal dimuat (muncul tulisan "Gagal memuat gambar"), padahal saat tombol *download* ditekan, gambar berhasil diunduh.
**Analisis Masalah Utama:**
Akar masalahnya ada pada perbedaan koneksi. Fitur unduhan (di MediaDownloader.kt) berhasil karena secara eksplisit memanggil networkHelper.client(). Klien OkHttp khusus ini dirancang untuk membawa *browser-grade user-agent*, *custom DoH DNS*, *stealth headers*, dan *shared cookie jar* (mampu menembus proteksi anti-bot/Cloudflare). Sebaliknya, *preview* gambar yang menggunakan AsyncImage di ImagePreviewCard.kt dan GridView.kt masih memakai koneksi bawaan Coil yang polos sehingga dicurigai sebagai *bot* dan diblokir (403 Forbidden).
**Execution Plan (Timebox: Maksimal 1 Jam):**
 1. **Investigasi & Sinkronisasi Global Coil3:**
   * Cari lokasi inisialisasi konfigurasi Coil (biasanya di *Application class* seperti App.kt, RoCatApp.kt, atau di dalam modul *Dependency Injection* seperti AppModule.kt).
   * Ubah konfigurasi SingletonImageLoader dari Coil3 agar secara global menggunakan *instance* OkHttp dari NetworkHelper.client().
   * Pastikan *Dependency Injection* (Injekt/Hilt/Koin) mem-*provide* NetworkHelper dengan benar ke dalam *factory* Coil.
 2. **Perbaikan State & Indikator Loading di UI:**
   * Buka file app/src/main/java/app/rocat/ui/components/ImagePreviewCard.kt.
   * Perbaiki *cache key* pada imageRequest dengan memastikan parameter headers ikut dimasukkan ke dalam remember(url, headers) agar Coil memperbarui *request* jika ada perubahan *header* (seperti Referer).
   * Tambahkan state indikator *loading* (CircularProgressIndicator) pada komponen AsyncImage dengan menangkap *event* onLoading, onSuccess, dan onError. Jangan biarkan UI hanya diam kosong lalu tiba-tiba *error*.
 3. **Code Quality & Build Test (Wajib):**
   * Pastikan kode yang ditulis rapi, sesuai standar Compose, dan aman dari *memory leak*.
   * Jika tersedia, jalankan *formatter*: bash ./gradlew spotlessApply
   * Pastikan kompilasi sukses tanpa *error*: bash ./gradlew assembleDebug
 4. **Pembaruan Memori & Dokumentasi:**
   * Patuhi instruksi sistem pada memory_prompt.md.
   * Buat catatan tugas baru di folder ai_memory/ (misal: task_YYYYMMDD_HHMM_Fase_39.md) yang berisi status, ringkasan perbaikan, dan langkah selanjutnya.
   * Update ai_memory/00_INDEX.md dengan menyematkan log terbaru ini.
**Constraints:**
 * Perubahan konfigurasi OkHttp pada Coil ini **TIDAK boleh merusak** fungsi MediaDownloader.kt atau pemutar video yang sudah berjalan lancar. Pastikan NetworkHelper.client() direferensikan dengan cara yang aman (perhatikan siklus hidup dan *fingerprint* jaringan).
