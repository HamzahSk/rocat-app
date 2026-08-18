# Tahap 33 - Sinkronisasi Klik SPA dan Screenshot Full-Page

**Status:** Selesai

**Tanggal:** 2026-08-18

## Perubahan

- `HeadlessWebViewManager.screenshot()` membaca tinggi dokumen dari DOM, mengonversinya
  ke pixel view, lalu me-layout WebView sementara ke tinggi dinamis. Tinggi bitmap
  dibatasi 5000 px untuk mencegah OOM; ukuran dan scroll lama selalu dipulihkan.
- `WebView.enableSlowWholeDocumentDraw()` diaktifkan best-effort sebelum pembuatan
  WebView untuk mencegah tile di luar viewport tergambar putih.
- Koordinat klik kini eksplisit memakai ruang dokumen: `rect + window.scrollX/Y`.
  Setelah `scrollIntoView` stabil, titik tap dihitung kembali ke ruang viewport dengan
  mengurangi posisi scroll, lalu dikonversi dari CSS pixel ke view pixel.
- Compositor dipaksa menggambar frame sebelum tap dan dua kali setelah tap, disertai
  settle 250 ms agar state React/Vue tersedia bagi baris skrip berikutnya.
- `capcut_test.js` v10 membersihkan marker klik lama dan memakai
  `page.waitForSelector(...)` untuk menunggu form email setelah klik.
- `DOCS_SCRIPTING.md` menambahkan bagian screenshot full-page dan pola tunggu SPA.

## Kompatibilitas

API `RoCatPage`, facade `page`, dan polyfill lama tidak berubah. Semua jalur render,
alokasi bitmap, restore layout, serta dispatch input tetap best-effort dan anti-crash.
