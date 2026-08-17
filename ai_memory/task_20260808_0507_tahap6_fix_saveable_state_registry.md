# Task Log — Tahap 6: Fix Crash SaveableStateRegistry

- **Status:** Selesai
- **Ringkasan Perubahan:**
  - Akar masalah (dari crash log `crash_log_20260808_120114.txt`): `RoCatNav.kt` line 54 memakai `rememberSaveable { mutableStateListOf(KEY_SCRIPTS) }`. Nilai berupa `SnapshotStateList<String>` yang TIDAK bisa disimpan oleh `SaveableStateRegistry` default (hanya mendukung tipe yang bisa ditaruh di Bundle) → `IllegalArgumentException` saat inisialisasi Compose → FC.
  - Perbaikan di `app/src/main/java/app/rocat/ui/navigation/RoCatNav.kt`: berikan `saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })` ke `rememberSaveable`. Impor baru: `androidx.compose.runtime.saveable.listSaver`, `androidx.compose.runtime.toMutableStateList`. Back stack navigasi tetap bisa *survive* config change/process death (disimpan sebagai `List<String>` yang Bundle-compatible).
- **Verifikasi:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- **Tugas Selanjutnya:** Verifikasi di perangkat/emulator bahwa UI (list scripts / playground) berhasil dirender tanpa FC setelah fix ini; jalankan unit test domain + rhino untuk regresi.
