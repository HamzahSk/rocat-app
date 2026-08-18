# Tahap 35 - Sistem Pengaturan Skrip & Peningkatan Canvas UI

Tag: #Fase35-SettingsSystem

- **Metadata `@settings`** (`ScriptMetadataParser.parseSetting`): skrip mendeklarasikan
  pengaturan per-baris `// @settings key: type: default=..., label=..., placeholder=...,
  min=..., max=..., step=..., options=a,b,c, maxLength=..., rows=...`. Tipe didukung:
  `string|password|boolean|number|select|multiline|color|email` (`ScriptSettingType`);
  tipe tak dikenal fallback ke `string` dan baris malformed dilewati. `ScriptSetting`
  (domain) membawa default ternormalisasi (`normalizedDefault`): boolean → `"true"`/
  `"false"`, number → representasi desimal, label fallback ke key.
- **`RoCat.settings` (API skrip)** — `ScriptSettingsBridge` (no-op default di
  `scripting:api`) + wiring Rhino di `createScope`:
  - `RoCat.settings.<key>` ter-typed dari snapshot (boolean/number/string via
    `typedSettingsJson`/`coerceToJsonValue`), `get(k)`, `getAll()`, `set(k,v)` (persist +
    fire `onSettingsChanged`), `setTemp(k,v)`/`getTemp(k)` (sesi in-memory).
  - `RoCat.onSettingsChanged(fn)`, `RoCat.saveHistory(key,value)`,
    `RoCat.clearHistory(key)`, `RoCat.openSettings()` (minta host buka halaman
    pengaturan skrip — `ScriptCanvasViewModel` mendengar `settingsOpenRequests`).
  - Tanpa bridge (eksekusi polos) `typeof RoCat.settings === "undefined"`.
- **Persistence** — Room `AppDatabase` v2 (`MIGRATION_1_2`): tabel `script_settings`
  (PK `script_id,key`, kolom value+type+updated_at) dan `script_input_history`
  (index `script_id,key`, query newest-first `GROUP BY value`). DAO + entity baru
  didaftarkan di `AppModule`.
- **`ScriptSettingsManager`** (app, singleton): load/setValue(validasi clamp min/max/
  options)/resetToDefault/exportSettings/importSettings(validasi per tipe)/history/
  saveHistory/clearHistory/deleteAll + `bridgeFor(script)` (EngineBridge + fallback
  `ScriptDefaultSettings` — `autoRun`, `timeout`, `downloadPath`, dsb — di belakang
  deklarasi skrip; skrip menang).
- **Halaman pengaturan skrip** — `ScriptSettingsScreen` + `ScriptSettingsViewModel`
  (route `Screen.ScriptSettings(scriptId)` di `RoCatNav`, tombol ikon settings di
  `ScriptCanvasScreen`, `RoCat.openSettings()` → navigasi). Kontrol per tipe:
  boolean→Switch, select→ExposedDropdownMenu, color→preview+text, number→field
  min/max/step, password→PasswordVisualTransformation, email→keyboard email,
  multiline→textarea rows, string→text. Reset to default, Export JSON (dialog + Copy),
  Import JSON (dialog validasi). i18n EN/ID baru (`scriptSettings`, `resetToDefault`,
  `exportSettings`, `importSettings`, `settingsReset`, dsb; `options` juga ditambahkan
  untuk header Group kosong di canvas).
- **Komponen canvas baru** (`ScriptUIComponent` + `ScriptUiBridge` no-op default +
  bridge Rhino `RoCatUiBridge` + dispatch `RoCat.render` di `RoCatCoreWrapper`):
  `addText/addDivider/addCheckbox/addToggle/addDropdown/addNumber/addColorPicker/
  addTextArea/addAutocomplete/addGroup/addLayout`. Descriptor JSON via
  `parseComponents/parseComponent` (parses array/objek, rekursif ke children,
  helper `str/bool/int/double/list/children/flex`, toleran malformed).
- **`flex`** — properti `abstract val flex: Int?` di `ScriptUIComponent`; di dalam
  layout `row`/`grid` tiap anak diberi `Modifier.weight(flexWeight)` (default 1),
  `group` collapsible dengan ikon ExpandLess/ExpandMore, `layout` row/column/grid
  dengan `padding`, `divider`, dan `columns`.
- **Canvas engine** (`ScriptCanvasViewModel`): `updateFieldValue`/`updateChecked`/
  `stepNumber` sekarang rekursif depth-first (`replaceInTree`) sehingga kontrol di
  dalam `group`/`layout` ikut dikumpulkan (`collectFields`) dan diperbarui; autocomplete
  memuat history bucket sekali (`loadHistory`) dan `saveAutocompleteHistory` pada
  penekanan tombol.
- **Pembersihan** — `ScriptsViewModel.delete()` juga memanggil
  `settingsManager.deleteAll(id)` (config + history + temp).
- **Fix kompilasi**: regex `settingLine/parameterTokens` dipindah dari `companion object`
  di dalam `object` (invalid); `argStringArray` membaca id array JS sebagai `Int`
  (`value.get(id, value)`, bukan `get(id.toString(), ...)` yang mengembalikan
  `UniqueTag.NOT_FOUND`); `MenuAnchorType` adalah tipe top-level material3 (bukan nested
  di `ExposedDropdownMenuDefaults`); `MaterialTheme.colorScheme` dibaca di luar lambda
  `remember`; variabel `v` bertipe `Double` eksplisit di `validate`/`normalize`.

Verifikasi:

- `:app:assembleDebug` sukses (0 error).
- `gradlew test`: domain 11/11 hijau (termasuk 5 test `@settings` baru),
  rhino 67 test, 64 hijau — termasuk `RoCatTahap35Test` baru (8/8: snapshot ter-typed,
  get/getAll, set+onSettingsChanged, temp, history+openSettings, gating tanpa bridge,
  rich controls via RoCatUI, dispatch `RoCat.render` tipe baru); 3 gagal =
  `FixedTestscrapeScraperTest` pre-existing (fixture `fixed_testscrape.js` tidak ada
  di repo), bukan regression Fase 35.