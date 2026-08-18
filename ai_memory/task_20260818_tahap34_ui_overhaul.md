# Tahap 34 - UI Overhaul dan Bug Fixing Sistematis

Tag: #Fase34-UIOverhaul

- Tema global Material 3 memakai dynamic color Android 12+ dengan mode System/Light/Dark persisten.
- Layout storage Pengaturan diubah menjadi susunan vertikal responsif agar label lokasi tidak lagi terjepit per huruf; ditambah preview tema dan toggle remote debugging WebView.
- Tambah Skrip memakai tab URL/Sumber, tombol Paste, dan pemilih dokumen `.js`.
- Editor sumber memiliki gutter nomor baris, highlighting JavaScript dasar, undo/redo, dan formatter indentasi konservatif.
- Browser memasang `DownloadListener` dengan dialog konfirmasi dan `DownloadManager` ke `Downloads/RoCat`; Cookie dan User-Agent WebView diteruskan, notifikasi selesai dikelola sistem.
- Media downloader berubah dari buffer seluruh file ke streaming OkHttp langsung ke SAF untuk mencegah OOM dan mengembalikan URI `content://` yang valid.
- Fallback User-Agent jaringan diperbarui ke Chrome 142; WebView tetap memakai UA native agar konsisten dengan Client Hints.
- Screenshot headless tetap menghasilkan PNG unik di cache, mengembalikan absolute path, dan membatasi tinggi bitmap.

Verifikasi:

- `:app:compileDebugKotlin` sukses.
- `:app:assembleDebug` sukses.
- `:app:testDebugUnitTest` tidak memiliki source test.
- `gradlew test` menjalankan suite lintas modul tetapi berhenti pada 3 test
  `FixedTestscrapeScraperTest`, seluruhnya di setup baris 46 karena fixture
  `fixed_testscrape.js` tidak ada di repo; bukan kegagalan assertion Fase 34.
