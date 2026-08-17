# Tahap 17 — First-Launch Fix, Storage Skrip Fisik & UI Kategori Collapsible

- **Tanggal:** 2026-08-09
- **Status:** SELESAI
- **Build:** `./gradlew :app:assembleDebug` SUCCESS; `./gradlew test` SUCCESS (domain 6/6, termasuk test kategori baru).

## 17.1 — Perbaikan Bug First-Launch (Storage Setup Stuck)
- **Akar masalah:** `StorageManager.isConfigured` adalah `val Boolean` getter yang dibaca sekali oleh `RoCatApp()` → Compose tidak re-compose saat folder dipilih → UI tetap di `StorageSetupScreen` sampai app di-restart.
- **Solusi:**
  - `SettingsRepository.storageUri` → `StateFlow<Uri?>` (`MutableStateFlow` dibungkus `asStateFlow()`), setter baru `setStorageUri(value)` (update flow + SharedPreferences). `hasStorageDirectory` membaca `.value`.
  - `StorageManager.isConfigured` → `StateFlow<Boolean>` via `settings.storageUri.map { it != null }.stateIn(scope, SharingStarted.Eagerly, initial)`; `mainUri` → `settings.storageUri.value`; semua penulisan pindah ke `settings.setStorageUri(...)`.
  - `RoCatApp()`: `val storageConfigured by storageManager.isConfigured.collectAsState()` → begitu folder dipilih, `StorageSetupScreen` diganti `RoCatAppNav()` seketika.
  - `SettingsViewModel`: `storageManager.isConfigured.value` (2 tempat).

## 17.2 — Simpan Fisik Skrip ke Storage Saat Import
- `StorageManager` helper baru:
  - `createScriptFolder(scriptId)` → `[Utama]/Scripts/[id]/` (`SCRIPTS_DIR = "Scripts"`).
  - `saveFileToScriptFolder(scriptId, fileName, content, mimeType = "application/javascript")` → menulis via pipeline `DocumentFile.createFile` + `contentResolver.openOutputStream` yang sama dengan scrape (Tahap 16).
  - `deleteScriptFolder(scriptId)` → hapus folder fisik (dipanggil saat skrip dihapus).
- `ImportScriptViewModel`: injeksi `StorageManager`; `persistToStorage(script)` dipanggil setelah import sukses (URL & paste) — best-effort (`runCatching`) agar gagal tulis tak memblokir import.
- `ScriptsViewModel.delete(id)` → `deleteScript.await(id)` + `storageManager.deleteScriptFolder(id)`.

## 17.3 — Menu Aksi Tahan Lama (Long Press) pada Skrip
- `ScriptsScreen`:
  - `ScriptListItem` memakai `Modifier.combinedClickable(onClick, onLongClick)` (`@OptIn(ExperimentalFoundationApi::class)`).
  - Long-press → `ModalBottomSheet` (`@OptIn(ExperimentalMaterial3Api::class)`) menampilkan nama + kategori skrip, opsi **Edit** (buka `Screen.Detail` via `onEditScript`) dan **Hapus** (→ `AlertDialog` konfirmasi → `viewModel.delete(id)`).
- `RoCatNav`: route baru `onEditScript = { navigate(Screen.Detail(it)) }` ke `ScriptsScreen`.

## 17.4 — UI Kategori Skrip (Collapsible/Akordion)
- **Metadata:** `ScriptMetadataParser` baca `@category` (fallback `@group`) → `ScriptMetadata.category`; field `category` (default `""`) ditambahkan di `Script` (backward-compatible untuk decode `scripts.json` lama) dan diisi `ImportScript`/`UpsertScript`.
- **UI:** `ScriptsScreen` grup `state.scripts.groupBy { it.category }`; header kategori (tebal, count, ikon `ExpandLess`/`ExpandMore`) klik untuk lipat/buka; status per-kategori di `remember { mutableStateMapOf<String, Boolean>() }` (missing = expanded); kategori kosong diberi label i18n `StringKey.othersCategory` ("Others"/"Lainnya").
- **i18n baru:** `StringKey.edit`, `StringKey.othersCategory` (EN + ID).

## File yang Diubah
- `app/src/main/java/app/rocat/settings/SettingsRepository.kt`
- `app/src/main/java/app/rocat/storage/StorageManager.kt`
- `app/src/main/java/app/rocat/ui/navigation/RoCatNav.kt`
- `app/src/main/java/app/rocat/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/app/rocat/ui/import/ImportScriptViewModel.kt`
- `app/src/main/java/app/rocat/ui/scripts/ScriptsViewModel.kt`
- `app/src/main/java/app/rocat/ui/scripts/ScriptsScreen.kt`
- `app/src/main/java/app/rocat/i18n/StringKey.kt`, `app/src/main/java/app/rocat/i18n/Strings.kt`
- `domain/.../ScriptMetadata.kt`, `ScriptMetadataParser.kt`, `ImportScript.kt`, `UpsertScript.kt`
- `scripting/api/.../model/Script.kt`
- `domain/src/test/.../ScriptMetadataParserTest.kt` (test kategori baru)
