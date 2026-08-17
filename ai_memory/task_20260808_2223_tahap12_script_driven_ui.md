# TASK LOG — Tahap 12: Script-Driven UI & Media Previews (Mihon-Style)

**Status:** Selesai

**Ringkasan Perubahan:**
- `scripting/api` — `ScriptUiBridge` interface baru (addInput/addButton/thumbnailPreview/videoPreview/clear/log); `ScriptEnvironment.ui` (nullable, default null); `ScriptEngine.invokeNamedFunction()` baru (panggil fungsi JS dengan 1 argumen objek berisi `Map<id,value>`, return `undefined` → string kosong).
- `scripting/rhino` — global `RoCatUI` dipasang di scope hanya saat `environment.ui` non-null; `RoCatUiBridge` (ScriptableObject ber-fungsi, semua call dijaga `runSafe` supaya galat bridge tidak membunuh skrip); `invokeNamedFunction` diimplementasi.
- `data` — `ScriptManager.createEnvironment(ui)` untuk membangun environment ber-RoCatUI dengan client yang sama.
- `domain` — `ExecuteScript.invoke(..., inputs: Map)` memilih `invokeFunction`/`invokeNamedFunction`.
- `app` — `ScriptUIComponent` (sealed: Input/Button/Thumbnail/Video/LogText); `PlaygroundViewModel` direformasi: `SnapshotStateList<ScriptUIComponent>` (di-marshal via main thread + session token untuk buang render lama), `buildUI()` dipanggil otomatis saat load/switch script, tombol mengumpulkan semua input → Map → ke JS; `PlaygroundScreen` = LazyColumn script-driven (AsyncImage Coil, OutlinedTextField, tombol Play `Intent.ACTION_VIEW`, log area, console output copyable JSON). Logika Function Selector + Arg statis Tahap 11 DIHAPUS.
- Test: `RhinoScriptEngineTest` +2 (RoCatUI bridge build + void button handler), hijau.

**Next Steps:**
- Uji manual dengan skrip `Script-Driven UI Tester` (via.placeholder.com + w3schools mov_bbb.mp4).
- Persistence/export Playground hasil ke disk, atau dukungan `RoCatUI.select`/list fallback.

## Build & Test
- `./gradlew :app:assembleDebug` SUCCESS
- `./gradlew test` SUCCESS
- `./gradlew :scripting:rhino:test` SUCCESS (24 test hijau)