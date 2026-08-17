# Tahap 12 — Media Renderer & Smart Playground Inputs

- **Status:** Selesai
- **Tanggal:** 2026-08-08

## Ringkasan Perubahan
1. **PlaygroundViewModel.kt** — Argumen `TestArg(label,value)` dihapus → `testArgs: List<String>` (value-only). Default state = 1 baris input kosong (`List(1){""}`), di-reset saat ganti skrip. `extractFunctionNames` pakai Regex `function\s+(?!_)([a-zA-Z$][\w$]*)\s*\(` (negative lookahead) → fungsi `_underscore` dianggap PRIVATE, tidak tampil di dropdown. `runFunction()` meneruskan nilai non-blank berurutan ke `ExecuteScript.invoke()`.
2. **PlaygroundScreen.kt** — Kolom "Key" dihapus; tiap input = 1 `OutlinedTextField` full-width label "Argument Value (e.g. URL)" + tombol hapus. Dropdown hanya menampilkan Public Functions.
3. **Media Preview (Coil)** — `MediaPreviewRenderer` baru: parse JSON output, jika `"media_type":"image"` + `"media_url"` → `AsyncImage` (Coil 3, `ContentScale.Fit`, `heightIn(max=300.dp)`); jika `"video"` → tombol "Play Video" (`Intent.ACTION_VIEW`, MIME `video/*`, fallback Toast). Dipasang di atas log JSON di `CopyableResultCard`.
4. **Dependensi** — `coil = "3.0.4"` ditambah di `rocat-app/gradle/libs.versions.toml` (`coil-compose`, `coil-network-okhttp`) + `app/build.gradle.kts`.
5. **Verifikasi** — Script uji (getImage/getVideo + helper `_getResolution`) terdeteksi benar: hanya getImage/getVideo muncul. Build `./gradlew :app:assembleDebug` SUCCESS, `./gradlew test` hijau.

## Tugas Selanjutnya (Next Steps)
- (Opsional) Preview video player in-app (ExoPlayer) menggantikan fallback Intent.
- Dukungan media URL dari properti bertingkat / multiple media items di satu output.
