# Task Log — Tahap 5: Global Crash Handler

- **Status:** Selesai
- **Ringkasan Perubahan:**
  - `app/src/main/java/app/rocat/crash/CrashLogStore.kt` (baru): utilitas menulis report stack trace (Throwable + cause chain + device info) ke `context.getExternalFilesDir(null)/crash_logs/crash_log_<timestamp>.txt` (terletak di `Android/data/app.rocat/files/`, tanpa runtime permission).
  - `app/src/main/java/app/rocat/crash/CrashHandler.kt` (baru): `Thread.UncaughtExceptionHandler` — simpan log, kirim `Intent` berisi stack trace + path ke `CrashActivity` (flag `NEW_TASK|CLEAR_TASK`), fallback ke default handler jika gagal, lalu `Process.killProcess`.
  - `app/src/main/java/app/rocat/RoApp.kt`: pasang `Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))` setelah DI bootstrap.
  - `app/src/main/java/app/rocat/ui/crash/CrashActivity.kt` (baru): Activity Compose terpisah (bukan bagian RoCatNav/MainActivity) — tampilkan stack trace scrollable (monospace), tombol "Copy to Clipboard" (ClipboardManager + Toast), info path log, tombol Exit.
  - `app/src/main/AndroidManifest.xml`: daftarkan `.ui.crash.CrashActivity` (`exported=false`, `excludeFromRecents=true`).
- **Verifikasi:** `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- **Tugas Selanjutnya:** Verifikasi di perangkat/emulator bahwa saat FC terjadi CrashActivity tampil & file log terbaca di Android/data. Jika crash terjadi di main thread dan activity tak sempat render (race dengan killProcess), opsi lanjutan: jalankan CrashActivity di process terpisah (`android:process=":crash"`) dan guard DI bootstrap RoApp berdasarkan process name.
