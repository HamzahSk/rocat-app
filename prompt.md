
### Prompt Fase 40: Perbaikan Decode Gambar Panjang (Mihon-style) & Optimalisasi Memori Reader
**Role & Objective**
Kamu adalah **Senior Android Engineer & UI/UX Designer** untuk aplikasi RoCat. Kita sekarang masuk ke **Tahap 40: Perbaikan Decode Gambar Panjang & Optimalisasi Memori Reader**.
Berdasarkan referensi file Screenshot_2026-08-25-06-13-20-91_561ca50af7d682e47269c1681227843c.jpg, kita bisa melihat gambar pertama berhasil dimuat, namun gambar selanjutnya gagal di-*render* di UI *reader*. Masalah utamanya bukan lagi di jaringan, melainkan proses *decode* gambar yang terlalu panjang (format Webtoon/Manhwa) yang menabrak limit tekstur OpenGL Android atau menyebabkan *Out of Memory* (OOM).
**Analisis Masalah Utama:**
Gambar komik yang panjang (misalnya 1000x8000 piksel) akan ditolak oleh sistem *hardware acceleration* Android jika menggunakan komponen AsyncImage standar dari Coil tanpa konfigurasi khusus. Aplikasi seperti Mihon mengatasi hal ini dengan menggunakan *subsampling* (memuat gambar sebagian sesuai porsi layar) atau membagi gambar raksasa (*image splitting*) secara *on-the-fly*.
**Execution Plan (Timebox: Maksimal 1.5 Jam):**
 1. **Implementasi Subsampling / Large Image Handling:**
   * Modifikasi UI Reader (seperti di ImagePreviewCard.kt atau komponen *reader* utama).
   * Jika menggunakan Jetpack Compose murni, integrasikan *library* pendukung *subsampling* yang kompatibel dengan Coil3 (seperti Telephoto buatan Saket Narayan) **ATAU** buat kustomisasi ImageRequest pada Coil untuk mematikan *Hardware Bitmaps* (allowHardware(false)) khusus untuk gambar yang melampaui batas resolusi tertentu.
   * Alternatif ala Mihon: Buat *decoder* kustom atau utilitas yang mendeteksi rasio tinggi/lebar ekstrem dan memotongnya menjadi *bitmap* terpisah sebelum di-render ke dalam LazyColumn.
 2. **Optimalisasi Memori di Reader (Lazy Loading):**
   * Pastikan LazyColumn atau komponen *list* pada CGBUM Reader mengelola memori dengan benar. Hapus gambar dari memori secara agresif saat *item* keluar dari layar (*off-screen*).
   * Konfigurasi diskCache dan memoryCache pada SingletonImageLoader Coil3 agar memiliki batas maksimal (misal: 25% dari *available RAM*) untuk mencegah penumpukan *cache* komik beresolusi tinggi.
 3. **Code Quality & Build Test (Wajib):**
   * Pastikan transisi antar halaman gambar mulus dan tidak ada efek *stuttering* saat *scroll*.
   * Pastikan kode rapi, sesuai standar Compose.
   * Jalankan *formatter*: bash ./gradlew spotlessApply (jika tersedia).
   * Pastikan kompilasi sukses tanpa *error*: bash ./gradlew assembleDebug
 4. **Pembaruan Memori & Dokumentasi:**
   * Patuhi instruksi sistem pada memory_prompt.md.
   * Buat catatan tugas baru di folder ai_memory/ (misal: task_YYYYMMDD_HHMM_Fase_40.md) yang berisi status, ringkasan implementasi *subsampling/decoding*, dan langkah selanjutnya.
   * Update ai_memory/00_INDEX.md dengan menyematkan log terbaru ini.
**Constraints:**
 * Solusi yang diterapkan harus berjalan lancar (*smooth scrolling*) di *device* dengan spesifikasi *low-end* sekalipun. Hindari proses kalkulasi pemotongan *bitmap* di *Main Thread*.