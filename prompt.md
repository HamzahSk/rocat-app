
### Prompt Fase 41: Perbaikan Aspect Ratio Video & Immersive Full Screen Player
**Role & Objective**
Kamu adalah **Senior Android Engineer & UI/UX Designer** untuk aplikasi RoCat. Kita sekarang masuk ke **Tahap 41: Perbaikan Aspect Ratio Video & Immersive Full Screen Player**.
**Mandatory Tasks: Pembaruan Memori & Code Quality (WAJIB DILAKUKAN PERTAMA)**
 * **Pembaruan Memori & Dokumentasi:** Patuhi instruksi sistem pada memory_prompt.md. Buat catatan tugas baru di folder ai_memory/ (misal: task_YYYYMMDD_HHMM_Fase_41.md) yang berisi rencana dan status perbaikan *video player* ini. Update ai_memory/00_INDEX.md dengan menyematkan log terbaru ini.
 * **Code Quality & Build Test:** Selama dan setelah proses modifikasi, pastikan kode rapi sesuai standar Compose. Jalankan *formatter*: bash ./gradlew spotlessApply (jika tersedia), lalu pastikan kompilasi sukses tanpa *error* dengan menjalankan: bash ./gradlew assembleDebug.
**Analisis Masalah Utama**
Tampilan *video player* saat ini memiliki dua kelemahan:
 1. Saat mode *full screen*, tombol navigasi dan *system bars* Android masih terlihat (tidak *immersive*).
 2. Dimensi video *preview* dan *player* dikunci paksa (*hardcode*) di rasio 16:9, sehingga video vertikal menjadi melebar dan cacat.
**Execution Plan (Timebox: Maksimal 1 Jam):**
 1. **Dinamisasi Aspect Ratio (VideoPreviewCard.kt):**
   * Tambahkan parameter videoAspectRatio: Float = 16f / 9f pada VideoPreviewCard.
   * Ganti *hardcode* 16f / 9f di VideoThumbnailPlaceholder agar menggunakan parameter aspectRatio tersebut.
   * Teruskan parameter videoAspectRatio ini saat memanggil RocatVideoPlayer.
 2. **Perbaikan Layar Penuh & Orientasi (RocatVideoPlayer.kt):**
   * Tambahkan parameter videoAspectRatio pada komponen RocatVideoPlayer.
   * Ganti *hardcode* rasio pada Box utama agar menggunakan ukuran dari videoAspectRatio.
   * Ubah logika orientasi pada LaunchedEffect(isFullScreen). Jika videoAspectRatio < 1f (video vertikal), set requestedOrientation ke ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT. Jika horizontal, set ke SCREEN_ORIENTATION_SENSOR_LANDSCAPE.
 3. **Perbaikan Immersive Mode di Dialog:**
   * Di dalam komponen FullScreenVideoDialog, pada blok LaunchedEffect(Unit), tambahkan perintah WindowCompat.setDecorFitsSystemWindows(window, false) tepat sebelum mengatur WindowInsetsControllerCompat. Ini wajib agar *system bars* benar-benar hilang.
**Constraints:**
 * Pastikan transisi masuk dan keluar dari *full screen mode* berjalan lancar (*smooth*) tanpa memicu video *restart* atau menimbulkan *stuttering* parah di UI.
