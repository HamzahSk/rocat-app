
### Prompt Fase 37: Fix Rendering Gambar Komik & Perbaikan Immersive UI (Mihon Style)
**Role & Objective**
Kamu adalah **Senior Android Engineer & UI/UX Designer** untuk aplikasi RoCat. Kita sekarang masuk ke **Tahap 37: Fix Rendering Gambar Komik & Perbaikan Immersive UI**.
Berdasarkan lampiran *screenshot* hasil tes pengguna pada skrip CGBUM Reader, terdapat *bug* visual yang parah:
 1. **Gambar Tidak Muncul (Gepeng):** Di bawah alert "Memuat 18 halaman", gambar komik sama sekali tidak muncul dan hanya berupa deretan kotak tipis (placeholder) yang kosong.
 2. **UI Kaku & Tombol Raksasa:** Tombol navigasi ("Home", "Detail") merentang *full-width* dengan ukuran yang sangat besar. Untuk aplikasi *comic reader* (seperti Mihon), ini membuang ruang layar dan tidak ergonomis.
Tugasmu adalah memperbaiki *layout* Compose dan logika *image loading* agar pengalaman membaca komik mulus, menyambung (*seamless*), dan gambar benar-benar termuat.
**Execution Plan (Kerjakan Secara Bertahap):**
 1. **Investigasi & Fix Bug Gambar (Coil Image Loading & Layout):**
   * Buka ImagePreviewCard.kt (atau komponen yang merender ScriptUIComponent.Image).
   * **Fix Layout:** Pastikan AsyncImage atau modul Coil menggunakan Modifier.fillMaxWidth().wrapContentHeight() dipadukan dengan contentScale = ContentScale.FillWidth. Jika gambar gepeng sebelum diload, beri minHeight sementara atau *placeholder* yang layak.
   * **Fitur Seamless (Webtoon Mode):** Di Mihon, gambar mode Webtoon itu menempel *edge-to-edge* (tanpa *gap*, tanpa *rounded corners*, tanpa *padding* luar). Tambahkan parameter opsional pada bridge RoCatUI.addImage(url, title, download, headers, seamless) di mana jika seamless = true, gambar dirender murni tanpa dibungkus ScriptCanvasCard (tanpa elevasi, *corner radius* 0, dan *padding* 0).
 2. **Validasi Anti-Hotlink (Header Referer):**
   * Banyak web komik menerapkan perlindungan *anti-hotlink*. Pastikan Coil NetworkHeaders di ImagePreviewCard benar-benar menerima dan memuat *headers* (terutama Referer) yang dikirimkan oleh skrip.
   * Di dalam file template skrip atau CGBUM, pastikan fungsi yang memanggil addImage sudah meneruskan headers: { "Referer": "URL_WEB_ASAL" }.
 3. **Perbaikan Proporsi UI & Grid:**
   * Di *screenshot*, tombol memanjang 100% karena berada di akar LazyColumn. Pastikan skrip komik memanfaatkan fitur layout: "row" dari Fase 35 dengan flex: 1 agar tombol "Home" dan "Detail" bisa sejajar (kiri-kanan) dan tidak raksasa.
   * Perbaiki jarak (*spacing*) antar komponen UI agar lebih padat dan profesional.
 4. **Kompilasi & Pembaruan Memori:**
   * Pastikan kompilasi sukses dengan ./gradlew assembleDebug.
   * **BACA ATURAN MEMORI:** Tambahkan catatan ringkas namun teknis di ai_memory/00_INDEX.md pada bagian atas tentang penyelesaian *bug* *image bounding* Coil dan tambahan mode *seamless* di Tahap 37.
**Memory and Constraints (CRITICAL):**
 * Jangan merusak fungsi skrip *scraper* video lama. Parameter seamless pada gambar harus bersifat *backward-compatible* (default false).
 * Tangani gambar gagal muat (error state di Coil) dengan memberikan *fallback icon* atau teks "Gagal memuat gambar" agar pengguna tahu jika gambarnya terkena 403 Forbidden.
