# Tahap 15 — Lokalisasi (i18n), Scoped Storage, Database & Pengaturan

**Tanggal:** 2026-08-09
**Status:** SELESAI
**Build:** `./gradlew :app:assembleDebug` SUCCESS, `./gradlew test` SUCCESS, `./gradlew :app:assemblePreview` (R8 minify) SUCCESS.

## Ringkasan
Menambahkan fondasi yang memisahkan concern aplikasi agar siap tahap berikutnya:
custom i18n (bukan `res/values`), akses direktori utama via Storage Access
Framework ala Mihon, Room database, serta halaman Settings lengkap.

### 15.1 — Lokalisasi Custom (package `app/rocat/i18n/`)
- `StringKey.kt`: enum berisi semua string UI (nav, scripts, import, playground,
  canvas, detail, settings, setup storage, scrapes).
- `AppLanguage.kt`: enum `ENGLISH("en")` / `INDONESIAN("id")`, label juga
  dilokalisasi via `labelKey`.
- `Strings.kt`: `open class Strings` (bukan data class supaya bisa diturunkan)
  dengan `operator get` fallback ke English bila key hilang; `EnglishStrings`
  (base) & `IndonesianStrings` sebagai object.
- `I18nProvider.kt`: `I18nProvider` reaktif — `StateFlow<AppLanguage>` +
  `StateFlow<Strings>` (stateIn Eagerly), `setLanguage()` persist + emit ulang.
  CompositionLocal `LocalStrings` + `LocalAppLanguage`, wrapper composable
  `I18nApp()`, helper `stringResource(key)` & `stringResource(key, vararg args)`
  (format `%1$s` untuk argumen).
- Seluruh hardcoded string utama UI dipindah ke sistem i18n (RoCatNav labels,
  Scripts/Detail/Import/Playground/Canvas screens). Toast pada callback non-
  composable memakai nilai string yang ditangkap saat komposisi (bukan
  `stringResource` di dalam lambda click/onFailure).

### 15.2 — Storage Access Framework & Manajemen Folder Scrape
- `SettingsRepository` (`app/rocat/settings/`) berbasis SharedPreferences:
  menyimpan `language` dan `storageUri` (tree URI).
- `StorageManager` (`app/rocat/storage/`): `takePersistablePermission()` memakai
  `takePersistableUriPermission` + menyimpan URI; `mainDocument()` via
  `DocumentFile.fromTreeUri`; `createScrapeFolder(name)` membuat
  `[Utama]/Scrapes/[name]/`; `mainDirectoryName()` untuk label UI;
  `clearCache()` membersihkan Coil memory+disk & `context.cacheDir` (IO).
- `StorageSetupScreen`: layar first-launch dengan tombol "Choose folder"
  (mirip alur Mihon) — seluruh UI diblokir sampai folder dipilih.
- `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())`
  dipakai di `StorageSetupScreen` dan di `SettingsScreen` (ubah direktori).
- `ScriptCanvasViewModel` kini menyuntik `StorageManager`; `scrapeFolder()`
  membuat/reuse `Scrapes/<scriptId>/` dan dipanggil di awal `execute()` sehingga
  setiap proses scrape punya folder hasil tersendiri.
- Dependensi baru: `androidx.documentfile:documentfile:1.0.1`.

### 15.3 — Room Database (SQLite)
- `libs.versions.toml`: `room = 2.6.1`, `ksp = 2.0.21-1.0.28`; libs
  `room-runtime/room-ktx/room-compiler`; plugin `ksp`. Root & app build file
  memakai plugin KSP (`alias(libs.plugins.ksp)`), app menambahkan
  `implementation(room-runtime/room-ktx)` + `ksp(room-compiler)`.
- Entitas: `CookieEntity` (id auto, name, value, domain, path, expiresAt,
  createdAt) & `HistoryEntity` (id auto, scriptId, title, url, timestamp).
- DAO: `CookieDao` (upsert/getAll/deleteAll), `HistoryDao` (insert/getAll/deleteAll).
- `AppDatabase` v1 (exportSchema=false); singleton dibangun di `AppModule`
  (`Room.databaseBuilder(app, ..., "rocat.db")`), DAO didaftarkan ke Injekt.

### 15.4 — Tab Pengaturan (Settings)
- Rute baru `Screen.Settings` + item ketiga di `NavigationBar` (ikon Settings).
- `SettingsScreen` (Compose): pemilih Bahasa (ExposedDropdown, langsung
  men-trigger i18n), kartu Storage (nama folder + tombol "Change storage
  directory" → OpenDocumentTree), dan aksi Data management (Clear cache,
  Clear cookies, Clear history) masing-masing dengan AlertDialog konfirmasi;
  hasil aksi ditampilkan via Toast.
- `SettingsViewModel`: `settingsState` (map atas `i18nProvider.language`),
  `setLanguage`, `onStoragePicked(uri)` (persist permission), `clearCache()`
  (Coil + cacheDir), `deleteCookies()`/`deleteHistory()` (Room deleteAll).
- `AppViewModelFactory` + `AppModule` didaftarkan `SettingsViewModel`.

## Catatan Teknis Penting
- **Strings harus `open class`**, bukan `data class`, karena `EnglishStrings`/
  `IndonesianStrings` diturunkan darinya; `staticCompositionLocalOf` perlu tipe
  eksplisit (`staticCompositionLocalOf<Strings>`).
- **`stringResource` tidak boleh dipanggil di dalam callback non-composable**
  (onClick/onFailure) — tangkap nilai string di body composable lalu pakai
  variabel tersebut.
- **Coil 3**: `ImageLoader.diskCache.clear()` bersifat `suspend` sehingga
  `clearCache()` dibuat suspend dan dijalankan di `Dispatchers.IO`.
- **KSP versi 2.0.21-1.0.28** cocok dengan Kotlin 2.0.21; KSP memproses Room
  (`:app:kspDebugKotlin` sukses) dan R8 preview tidak perlu rule tambahan.
- Room DAO/DB singleton di-inject lewat Injekt (`registrar.addSingleton`).
