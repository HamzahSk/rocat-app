# Task Log — Tahap 11.5: Hotfix & Refinement UI Playground

## Status
Selesai

## Ringkasan Perubahan
- **`PlaygroundViewModel.kt`:** Card "Run main" (Target URL) dihapus total → state `targetUrl`/`running`/`result` + fungsi `run()`/`onUrlChange()` dihilangkan; `testArgs` kini default `emptyList()` (kosong, tanpa default membutuhkan input); `testFunctionSuggestions` default kosong; **fungsi baru** `extractFunctionNames(scriptCode)` memindai kode skrip dengan Regex `function\s+([a-zA-Z_$][\w$]*)\s*\(` → daftar nama fungsi otomatis; saat skrip dimuat/dipilih (`select()` & init `subscribe`), suggestions = hasil ekstraksi dan `testFunction` diset ke fungsi pertama (default). Fungsi `removeArg` tetap bisa menghapus semua arg (kembali ke kosong).
- **`PlaygroundScreen.kt`:** Card "Run main" (Target URL) dan `ResultCard` dihapus — yang tersisa hanya *Test Function* dan *Log/Result*; bagian input Key/Value ("Inputs") tampil **hanya** jika `testArgs` tidak kosong (tombol "+ Add Input" tetap tampil); ikon hapus kini selalu aktif agar baris terakhir bisa dihapus kembali ke state kosong; text hasil eksekusi di dalam `CopyableResultCard`/log dibungkus `SelectionContainer { ... }` sehingga teks bisa diseleksi & disalin sebagian; konten display & kedua tombol copy memakai `ResultFormatter.prettyJson(content)` (WYSIWYG).
- **`ResultFormatter.kt`:** `prettyJson()` disempurnakan — `Json.parseToJsonElement(raw)` lalu re-encode dengan `Json { prettyPrint = true; prettyPrintIndent = "  " }`; jika bukan JSON valid → return `raw` (fallback aman).

## Verifikasi
- Contoh skrip "MythToons HTML Tester" (`main()` tanpa parameter, fungsi `main`/`testHtml`/`parseTest`) → regex terdeteksi `[main, testHtml, parseTest]` (uji mandiri kotlinc), bisa langsung di-run pada Function Selector tanpa form input kosong.
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- `./gradlew test` → BUILD SUCCESSFUL (semua unit test hijau).

## Tugas Selanjutnya (Next Steps)
- Uji manual emulator: pilih skrip yang dimuat, lihat Function Selector terisi otomatis dari daftar fungsi; jalankan `main()` tanpa argumen; highlight & copy sebagian teks hasil JSON pretty-print di Log card.