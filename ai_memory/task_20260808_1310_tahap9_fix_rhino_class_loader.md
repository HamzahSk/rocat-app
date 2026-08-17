# Task Log — Tahap 9: Fix Rhino Class Loader (optimizationLevel = -1) + Playground Error UI

## Status
Selesai

## Ringkasan Perubahan
- **`rocat-app/scripting/rhino/.../RhinoScriptEngine.kt`:**
  - `ScriptContextFactory.makeContext()` kini menyetel `optimizationLevel = -1` → Rhino berjalan murni mode interpretasi, tidak mengompilasi JS ke `.class` Java standar yang tak bisa dimuat classloader Android (fix `can't load this type of class file`).
  - Blok `try-catch` di `execute()` & `invokeFunction()` diperlebar: `CancellationException` (rethrow), lalu `EvaluatorException`, `StackOverflowError`, `Exception`, dan `Throwable` (guard terakhir anti force-close).
  - Helper baru `valueToString()`: interpreter mengembalikan `Double` untuk aritmatika bilangan bulat (`1+41` → `42.0`), kini dinormalisasi → `42` agar output bersih & test `returns last expression` tetap hijau.
- **`PlaygroundViewModel.kt`:** Hapus state `error`/`logError`. Semua kegagalan (validasi, `ScriptResult.Failure`, atau Throwable) ditulis ke `result` (Run) / `log` (Run Search/Detail) dengan prefiks `Error: `; eksekusi pindah ke `Dispatchers.IO` + `try-catch Throwable`.
- **`PlaygroundScreen.kt`:** Hapus teks merah melayang (`state.error` & `logError`); error kini tampil di area Result/log monospace berlatar abu-abu.

## Verifikasi
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- `:scripting:rhino:testDebugUnitTest` → 13/13 hijau; `:domain:testDebugUnitTest` hijau.

## Tugas Selanjutnya (Next Steps)
- Uji manual di emulator: tombol Run / Run Search / Run Detail pada skrip contoh; pastikan tidak ada pesan merah melayang dan output/error muncul di area Result/log.
