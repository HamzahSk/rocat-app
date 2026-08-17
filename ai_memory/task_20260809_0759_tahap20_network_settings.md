# Tahap 20 — Native Base64 Bridge, Custom User-Agent & Custom DNS (Network Settings)

**Tanggal:** 2026-08-09 · **Status:** SELESAI · **Build:** `rocat-app` `sh gradlew :app:assembleDebug` + `:app:assemblePreview` (R8) + `sh gradlew test` SUCCESS (rhino 22 test hijau: 20 RhinoScriptEngineTest + 2 AnichinScraperTest — termasuk assert decodeBase64 via bridge native).

## Tujuan
Tahap 20: (1) memindahkan logika `decodeBase64` dari scraper JS ke native Kotlin (Android `android.util.Base64`) lewat bridge `RoCatUI.decodeBase64`, dan (2) menambah pengaturan **Jaringan** di aplikasi: custom User-Agent dan DNS Over HTTPS (DoH) yang tersimpan di SharedPreferences (`SettingsRepository`) dan dipakai `NetworkHelper`/interceptor OkHttp.

## Perubahan

### Tahap 20.1 — Native Base64 Bridge
- `ScriptUiBridge` (scripting/api): fungsi baru `fun decodeBase64(input: String): String` dengan **default implementation** dependency-free (`java.util.Base64`, safe di JVM unit test & Android API26+) — pad otomatis bila `length % 4 != 0`, strip whitespace, return `""` bila gagal (tak pernah throw).
- `RhinoScriptEngine` `RoCatUiBridge`: `put("decodeBase64", ...)` + helper baru `runSafeValue(default){}` (versi nilai dari `runSafe`) supaya hasil decode dikembalikan ke JS sebagai string, bukan `undefined`.
- `ScriptCanvasViewModel.uiBridge`: override `decodeBase64` memakai **native Android** (`android.util.Base64.decode(padded, Base64.DEFAULT)` → String UTF-8, try-catch → `""`).
- `scrape_anichin.js`: `decodeBase64()` kini memanggil `RoCatUI.decodeBase64(s)` bila bridge ada, fallback `b64Decode` JS (tetap ada) bila tidak.
- Test `AnichinScraperTest`: `UiRecorder.decodeBase64` merekam pemanggilan (`super.decodeBase64`); 2 test assert bahwa decode iframe episode + `decodeBase64(enc)` probe **lewat bridge native**.

### Tahap 20.2/20.3 — Network Settings (UI + Data Store + OkHttp)
- `core/common/network/DnsProvider.kt`: `enum DnsMode { SYSTEM, CLOUDFLARE, GOOGLE, QUAD9, CUSTOM }` + `DnsProviders.dohUrl(mode, customUrl)` (Cloudflare `https://cloudflare-dns.com/dns-query`, Google `https://dns.google/dns-query`, Quad9 `https://dns.quad9.net/dns-query`, CUSTOM → URL pengguna).
- `core/common/network/DoHResolver.kt`: implementasi `okhttp3.Dns` kustom — GET JSON `?name=<host>&type=A` (Accept `application/dns-json`) ke endpoint terpilih; baca `Answer[].type` (1=A/28=AAAA) → `InetAddress.getByName(data)`; fallback otomatis ke `Dns.SYSTEM` bila request gagal/tak ada jawaban. Client DoH di-pin `.dns(Dns.SYSTEM)` (anti-rekursi).
- `NetworkHelper` dirombak: membaca **provider** `userAgentProvider()` dan `dnsConfigProvider()` (dari SettingsRepository), membangun OkHttp client **lazily** dengan fingerprint `"$ua|$mode|$customUrl"` — client hanya di-rebuild bila config berubah (tidak per-keystroke); `.client()` sekarang fungsi (bukan val) yang exposes `DoHResolver` di `.dns(...)`; `newScriptClient()` mewarisi config terbaru. `UserAgentInterceptor` & `CloudflareInterceptor` ikut snapshot UA saat build.
- `ScriptManager` (data): engine + environment **di-refresh lazily** saat fingerprint jaringan berubah → `engine()`, `environment()`, `createEnvironment(ui)` (bukan `val engine`/`environment` lagi); `ExecuteScript` di AppModule dijadikan factory.
- `SettingsRepository`: pref baru `user_agent`, `dns_mode`, `custom_dns_url` (SharedPreferences). `AppModule`: SettingsRepository dibuat **sebelum** NetworkHelper sehingga stack HTTP di-seed dari pref persisten.

### Tahap 20.2 — UI Pengaturan Jaringan
- `SettingsScreen`: section kategori baru "Jaringan"/"Network" setelah Storage & sebelum Data Management: TextField User-Agent (kosong = default "Chrome 141", hint + label blank), dropdown `DnsModeRow` (System/Cloudflare/Google/Quad9/Custom), TextField DoH URL muncul hanya saat `CUSTOM`.
- `SettingsViewModel`: State bertambah `userAgent`, `dnsMode`, `customDnsUrl` (di-seed dari repo di `init`); `settingsState` kini `combine(i18nProvider.language, mutableState)` supaya perubahan jaringan ikut memancar ke UI; setter `setUserAgent/setDnsMode/setCustomDnsUrl` → tulis prefs + update state (rebuild client menunggu request berikutnya).
- i18n: `StringKey` baru `network/userAgent/userAgentHint/userAgentBlank/dnsSelection/dnsSystemDefault/dnsCloudflare/dnsGoogle/dnsQuad9/dnsCustom/customDnsUrl/customDnsUrlHint` + terjemahan EN/ID.

## Catatan Teknis Penting
- **Jangan letakkan `android.util.Base64` di default method interface** `ScriptUiBridge`: unit test JVM rhino yang memanggil `RoCatUI.decodeBase64` akan kena "Method decode not mocked". Default pakai `java.util.Base64` (aman di unit test & API26+), sedangkan override native `android.util.Base64` hanya di app (ScriptCanvasViewModel).
- **Bridge nilai**: `runSafe {}` mengembalikan `Unit`; fungsi bridge yang harus mengembalikan nilai (mis. decodeBase64) wajib pakai `runSafeValue(default){}` agar JS menerima string (bukan `undefined`).
- **`NetworkHelper.client` kini FUNGSI `client()`**, bukan `val`: pemanggil lama (`MediaDownloader`, `ScriptSourceFetcher`, `ScriptManager`) di-update; setiap request app memakai config UA/DNS terbaru (client di-rebuild lazily saat fingerprint berubah).
- **DoH kustom** tidak menambah dependensi `okhttp3-dnsoverhttps` (opsi "jika perlu") — implementasi `okhttp3.Dns` sendiri dengan JSON API; selalu `fallback Dns.SYSTEM` bila DoH gagal agar konektivitas tidak putus.
- **ScriptManager refresh**: `currentEngine`/`fetchImpl` disimpan, `engine()`/`createEnvironment()` memanggil `refresh()` (membandingkan `NetworkHelper.fingerprint()`); script yang dieksekusi di canvas memakai `scriptManager.engine()` + `createEnvironment(ui)` (uiExecuteScript diubah dari `by lazy` menjadi getter yang dibuat ulang setiap panggilan).
- **Dropdown pada `SettingsScreen`**: dropdown DNS memakai `ExposedDropdownMenuBox` + `DnsMode.entries` dan label lokal dari i18n — pola sama dengan `LanguageRow`.

## Verifikasi
- `cd rocat-app && sh gradlew :app:assembleDebug` → **BUILD SUCCESSFUL** (APK debug per-ABI).
- `sh gradlew :app:assemblePreview` → **BUILD SUCCESSFUL** (R8/minify, lintVitalPreview bersih).
- `sh gradlew test` → **BUILD SUCCESSFUL** (domain + data + scripting rhino; rhino 22 test hijau — `AnichinScraperTest` 2 test termasuk assert decode via bridge native).
- `node --check scrape_anichin.js` → **OK**.
- (Dev) verifikasi manual: ubah User-Agent/DNS di Settings → tersimpan & bertahan saat app direstart; jalankan skrip Anichin → stream HLS tetap jalan dengan `decodeBase64` native.