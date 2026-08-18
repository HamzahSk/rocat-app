
**Role & Objective**
Kamu adalah **Senior Android Engineer**. Kita masuk ke **Tahap 32.2**. Pengguna melaporkan bahwa setelah menyalin pengaturan WebView dari sweb-master, halamannya **masih blank putih**, NAMUN aktivitas jaringan (*network*) menunjukkan bahwa elemen web/JS/CSS berhasil diunduh.
Ini berarti kegagalannya ada pada *rendering engine*, *Compose layout*, atau interseptor custom kita. Tugasmu adalah melakukan isolasi masalah dan debugging ekstrem.
**Execution Plan (Kerjakan Secara Bertahap):**
 1. **Isolasi Interseptor (Bypass Custom Network):**
   * Di RoCat, kita menggunakan AndroidCookieJar, CloudflareInterceptor, dan header kustom. Ada kemungkinan mekanisme ini merusak *request/response* internal WebView.
   * **Tugas:** Coba nonaktifkan sementara (bypass) kustomisasi shouldInterceptRequest atau interseptor OkHttp yang menyentuh WebView di BrowserScreen.kt. Buat WebView benar-benar berjalan "polosan" murni seperti aplikasi bawaan (mirip sweb-master yang polosan).
 2. **Perbaikan Layout Compose (AndroidView):**
   * Seringkali WebView terunduh tapi tidak digambar karena ukurannya 0.
   * **Tugas:** Di BrowserScreen.kt, pastikan AndroidView memiliki modifier = Modifier.fillMaxSize() dan di dalam factory block, *instance* WebView secara eksplisit diberikan parameter layout:
     ```kotlin
     layoutParams = ViewGroup.LayoutParams(
         ViewGroup.LayoutParams.MATCH_PARENT,
         ViewGroup.LayoutParams.MATCH_PARENT
     )
     
     ```
 3. **Aktifkan Inspektor Chrome (Wajib untuk SPA):**
   * Karena ini adalah aplikasi SPA (React), kita HARUS bisa melihat *Console* JavaScript-nya.
   * **Tugas:** Pastikan WebView.setWebContentsDebuggingEnabled(true) dijalankan secara statis.
   * Pastikan WebChromeClient meng-override onConsoleMessage dan mencetaknya ke Logcat dengan jelas (misal tag WebViewJS-Console), agar kita bisa melihat apakah React mengalami *Hydration Error* atau diblokir oleh CSP.
 4. **Verifikasi Hardware Acceleration & Konteks:**
   * Di AndroidManifest.xml sweb-master, periksa apakah ada tag khusus seperti android:hardwareAccelerated="true".
   * Pastikan WebView diinisialisasi menggunakan *Activity Context*, bukan *Application Context*.
**Memory and Constraints:**
 * Segera laporkan hasil ConsoleMessage dari JavaScript (apakah ada error merah dari React?).
 * Jangan merusak arsitektur BrowserViewModel yang sudah ada, cukup tambal bagian render AndroidView-nya.
 * Update 00_INDEX.md dengan penemuan terbaru terkait masalah rendering putih ini.
