# Task 2026-08-08 03:13 — Tahap 4: Stabilisasi Crash & Penyempurnaan Script

**Status:** Selesai

## Ringkasan Perubahan
**Akar masalah Force Close:** `ScriptsScreen`, `ImportScriptScreen`, `PlaygroundScreen` memakai `viewModel()` tanpa factory. ViewModel ber-constructor default tidak punya no-arg constructor public → default factory melempar `NoSuchMethodException`/`Cannot create an instance of class ...ViewModel` → crash saat app pertama dibuka.

1. **DI (Tahap 4.1):** Baru `AppViewModelFactory.kt` (object, resolve Scripts/Import/Playground VM). `AppModule.kt` mendaftarkan keempat ViewModel via `addSingletonFactory` + factory. `ScriptDetailViewModel` dapat nested `Factory(scriptId)` (harus nested di class, bukan companion — koreksi compile). Keempat screen kini memakai `viewModel(factory=...)` eksplisit.
2. **Repository (Tahap 4.1):** `ScriptRepositoryImpl` memindahkan `load()`/`save()` ke `Dispatchers.IO` + `CoroutineScope(Dispatchers.IO)`; load inisial asinkron di `init` (tidak blokir main thread).
3. **MainActivity/Nav:** sudah benar (`RoCatApp()`, backstack selalu berisi route non-null, `decode` fallback `Scripts`).
4. **Tahap 4.2:** `ScriptSourceFetcher` validasi respons import URL: tolak Content-Type `text/html`/`xml` & body HTML/empty → `IllegalArgumentException` jelas. `RhinoScriptEngine.execute` menangkap `EvaluatorException` (syntax error → "JS error: ...") dan `StackOverflowError` agar tidak crash; URL Playground sudah diteruskan ke `main(url)` via `args`.

**Verifikasi:** `./gradlew :app:assembleDebug` SUCCESS; unit test domain + rhino SUCCESS.

## Tugas Selanjutnya (Next Steps)
- (Opsional) Uji manual runtime di emulator: impor script via URL & Playground run.
- (Opsional) Tambah unit test validasi `ScriptSourceFetcher` (HTML rejection) dan repository async-load.
