# Task Log — Tahap 10: Stealth Browser Networking & Cloudflare Bypass

## Status
Selesai

## Ringkasan Perubahan
- **`rocat-app/core/common/.../network/AndroidCookieJar.kt` (BARU):** OkHttp `CookieJar` berbasis `android.webkit.CookieManager` (jala mihon `AndroidCookieJar`). OkHttp + WebView berbagi cookie store persisten yang sama; semua akses diserialkan lock + `flush()` sinkron agar cookie yang baru diset langsung terlihat di request berikutnya; ada `get()/remove()/removeAll()` untuk cookie `cf_clearance`.
- **`.../network/interceptor/CloudflareInterceptor.kt` (BARU):** Deteksi challenge CF (HTTP 403/503 + header `Server: cloudflare` + `<title>Just a moment...` / elemen `challenge-error-*`), lalu headless WebView di main thread (via `Handler(mainLooper)`) menjalankan JS Turnstile; flag `@Volatile` dalam `ChallengeState`, latch sampai 30 detik, cek `cf_clearance` baru di `AndroidCookieJar`, retry `chain.proceed(request)`, dan WebView SELALU `destroy()` (anti-memory-leak).
- **`.../network/interceptor/StealthHeadersInterceptor.kt` (BARU):** Default header browser modern (`Accept-Language`, `Sec-CH-UA*`, `Sec-Fetch-Dest/Mode/Site/User`) diisi hanya jika belum ada; UA tetap lewat `UserAgentInterceptor`.
- **`NetworkHelper.kt`:** Konstruktor kini `Context`; `cookieJar` di-`cookieJar()`, `baseClient` + stealth headers, `client` menambah `CloudflareInterceptor`, `newScriptClient()` mewarisi seluruh stack (cookie jar + CF) dengan timeout agresif.
- **`AppModule.kt`:** `NetworkHelper(app)` (ganti `app.cacheDir`).

## Verifikasi
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- `:scripting:rhino:testDebugUnitTest` + `:domain:testDebugUnitTest` hijau. API `fetch` JS tidak berubah — cookie jar & interceptor bekerja transparan.

## Tugas Selanjutnya (Next Steps)
- Uji manual emulator pada situs ber-Cloudflare: pastikan request pertama 403/503 lalu bypass dan sukses membawa `cf_clearance`.