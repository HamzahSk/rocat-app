# Task Log — Tahap 7: Network, SSL & Script Loader

- **Status:** Selesai
- **Ringkasan Perubahan:**
  - **network_security_config.xml** (baru, `rocat-app/app/src/main/res/xml/`): `cleartextTrafficPermitted="true"` + trust-anchors `system` & `user` (mirror Mihon) → mengatasi `CertPathValidatorException: Trust anchor ... not found` untuk CA user/proxy. Direferensikan di `AndroidManifest.xml` via `android:networkSecurityConfig`.
  - **NetworkHelper** (`core/common/network/NetworkHelper.kt`): default UA browser-grade ala Mihon (`Chrome/141.0.0.0`), `connectionSpecs` = `MODERN_TLS + COMPATIBLE_TLS + CLEARTEXT`, `followRedirects(true)` & `followSslRedirects(true)`, `writeTimeout(30s)`; `UserAgentInterceptor` kini publik & tidak menimpa UA eksplisit per-request (mirror Mihon).
  - **ScriptSourceFetcher**: `normalizeUrl()` (trim + injeksi `https://` utk domain telanjang + rewrite GitHub `blob/` → `raw.`), semua kerja network di `Dispatchers.IO`; error dilempar sebagai `IllegalArgumentException`/`IOException` bertipe.
  - **ImportScriptViewModel**: `friendlyMessage(e)` memetakan `UnknownHostException`/`SocketTimeoutException`/`SSLException`/`IOException` ke pesan ramah; `EXAMPLE_SCRIPT` diperkaya (header `==UserScript==` lengkap + `@grant none`), `fetch(url,"GET",{},null)` kompatibel Rhino.
  - **WebViewUtil** (baru, `core/common/util/WebViewUtil.kt`): `javaScriptEnabled`, `domStorageEnabled`, `databaseEnabled`, wide-viewport, popup/zoom, third-party cookies; `getInferredUserAgent()` disinkronkan dgn `NetworkHelper.DEFAULT_USER_AGENT`.
  - **Unit test** baru: `ScriptSourceFetcherTest` (normalisasi URL, rewrite GitHub, reject empty) + deps `junit`/`okhttp` di `data/build.gradle.kts`.
- **Verifikasi:** `./gradlew :app:assembleDebug` BUILD SUCCESSFUL; `./gradlew test` (domain, rhino, data) semua hijau.
- **Tugas Selanjutnya:** Uji fetch nyata ke `https://google.com` & raw script di perangkat (verifikasi handshake TLS + UA diterima server); validasi WebViewUtil saat dipakai engine fallback/rendering.
