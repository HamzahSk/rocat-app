# TASK LOG — Tahap 14: Production Readiness (Build, Sign, Splits & Firebase)

**Status:** Selesai

**Tanggal:** 2026-08-09

## Ringkasan Perubahan
- **Signing config (Tahap 14.1)** — `app/build.gradle.kts`: logika Kotlin membaca `keystore.properties` dari root `rocat-app/` (`storeFile/storePassword/keyAlias/keyPassword`) via `rootProject.file()`. Blok `signingConfigs { create("release") }` hanya terisi bila keystore benar-benar ada (`hasReleaseSigning`). Jika tidak ada, `release` memakai fallback `signingConfigs.getByName("debug")` → build di CI/mesin tanpa keystore tidak pernah putus.
- **ABI splits (Tahap 14.2)** — `splits { abi { isEnable = true; reset(); include("armeabi-v7a","arm64-v8a","x86","x86_64"); isUniversalApk = true } }` → tiap build menghasilkan 4 APK per-arsitektur + 1 universal (terverifikasi di `outputs/apk/{debug,preview}`).
- **Build types (Tahap 14.3)** — `debug` (`applicationIdSuffix = ".debug"`), `release` (`isMinifyEnabled = true`, `isShrinkResources = true`, ProGuard optimize + `proguard-rules.pro`, signing release/debug-fallback), `preview` baru (`initWith(release)` + `applicationIdSuffix = ".preview"`) untuk uji rilis tanpa menimpa produksi. Build type `preview` ditambahkan juga ke 6 modul library (`core:common`, `core:viewmodel`, `domain`, `data`, `scripting:api`, `scripting:rhino`) dengan `initWith(getByName("release"))` karena variant matching AGP butuh build type yang sama di semua modul (tanpa ini `test`/`assemblePreview` gagal).
- **Firebase opsional (Tahap 14.4)** — `libs.versions.toml`: `firebase-bom = 34.17.0`, `google-services = 4.5.0`, `firebase-crashlytics-plugin = 3.0.7` + library `firebase-bom/analytics/crashlytics` + plugin `google-services` & `firebase.crashlytics`. Root `build.gradle.kts` mendeklarasikan kedua plugin `apply false` (hanya di-classpath). Di `app/build.gradle.kts`, plugin Firebase di-`apply(plugin = ...)` **hanya jika** `file("google-services.json").exists()`; dependency Firebase juga conditional. Tanpa file tsb, build 100% aman.
- **`proguard-rules.pro` (baru)** — aturan wajib Rhino: `-dontwarn java.beans.**`, `-dontwarn org.mozilla.javascript.**`, `-keep class org.mozilla.javascript.** { *; }`. Tanpa ini R8 gagal karena `JavaToJSONConverters` mereferensikan `java.beans.*` yang tidak ada di Android.

## Tugas Selanjutnya (Next Steps)
- (Opsional) Buat `keystore.properties` + `keystore.jks` lokal, lalu pastikan `./gradlew :app:assembleRelease` menandatangani dengan keystore asli.
- (Opsional) Tambah `google-services.json` + plugin Firebase untuk mengaktifkan Analytics/Crashlytics (build otomatis apply bila file ada).
- (Opsional) Set-up Firebase Crashlytics mapping file upload untuk release (symbol deobfuscation).

## Build & Test
- `./gradlew :app:assembleDebug` SUCCESS (verifikasi wajib) — menghasilkan split + universal APK
- `./gradlew test` SUCCESS (varian debug/release/preview, variant matching beres)
- `./gradlew :app:assemblePreview` SUCCESS (R8 minify + shrink resources + signing fallback debug)
