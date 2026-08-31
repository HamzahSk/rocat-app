
### Prompt Fase 43: Fix Headless WebView Rendering - Sinkronisasi dengan Browser UI

**Role & Objective**
Kamu adalah **Senior Android Engineer** untuk RoCat. Kita masuk ke **Tahap 43: Perbaikan Rendering Headless WebView**. Masalah: WebView di mode script (headless) tidak memuat halaman dengan sempurna seperti di mode browser UI, padahal menggunakan WebView yang sama. Elemen-elemen SPA (React/Vue) tidak ter-render dengan benar.

**Root Cause Analysis**
Perbedaan utama antara Browser UI dan Headless:

| Aspek | Browser UI (BrowserScreen.kt) | Headless (HeadlessWebViewManager.kt) |
|-------|-------------------------------|--------------------------------------|
| Context | Activity Context | Application Context |
| View Hierarchy | Ter-attach ke Compose UI | Tidak pernah di-attach |
| Window/Focus | Punya window & focus | Tidak punya window |
| Layout/Measure | Dilakukan oleh sistem | Manual via `measure()`/`layout()` |
| Resource Loading | Normal | Terbatas |

**Masalah Identifikasi:**
1. WebView dibuat dengan `appContext` (Application), bukan Activity Context
2. WebView tidak pernah di-attach ke view hierarchy
3. Tidak ada window/focus → renderer tidak mendapat prioritas
4. Layout/measure manual tidak cukup untuk SPA modern

**Execution Plan (Maks 1 Jam):**

### 1. Fix Context & Attachment (HeadlessWebViewManager.kt)

**Goal**: Buat headless WebView memiliki "perlakuan" yang sama dengan browser UI.

**Perubahan yang diperlukan:**

```kotlin
class HeadlessWebViewManager(
    private val appContext: Context,
    private val activityContext: Context? = null  // NEW
) {
    private var containerView: View? = null  // NEW
    private var windowManager: WindowManager? = null  // NEW

    private fun ensureWebView(): WebView? {
        webView?.let { return it }
        
        val latch = CountDownLatch(1)
        val ref = AtomicReference<WebView?>()
        
        onMain {
            try {
                // 1. Gunakan Activity Context jika tersedia
                val context = activityContext ?: appContext
                
                // 2. Buat container invisible
                val container = FrameLayout(context).apply {
                    visibility = View.GONE
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                    setBackgroundColor(Color.TRANSPARENT)
                }
                
                // 3. Buat WebView dengan settings IDENTIK
                val wv = WebView(context).apply {
                    WebViewUtil.setDefaultSettings(this)
                    
                    // Settings tambahan untuk SPA
                    settings.apply {
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportMultipleWindows(true)
                        javaScriptCanOpenWindowsAutomatically = true
                        // Media playback tanpa user gesture
                        mediaPlaybackRequiresUserGesture = false
                    }
                    
                    // Chrome client untuk console log
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            consoleMessage?.let { msg ->
                                Log.d("HeadlessWebView", 
                                    "${msg.messageLevel()}: ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})"
                                )
                            }
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }
                }
                
                // 4. ATTACH ke container!
                container.addView(wv)
                
                // 5. Tambahkan container ke window (invisible)
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val params = WindowManager.LayoutParams(
                    1, 1,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                )
                params.gravity = Gravity.TOP or Gravity.START
                wm.addView(container, params)
                
                // 6. Beri waktu untuk attach
                container.post {
                    wv.onWindowFocusChanged(true)
                    wv.requestFocus()
                }
                
                containerView = container
                windowManager = wm
                webView = wv
                ref.set(wv)
                
                Log.d("HeadlessWebView", "WebView attached to invisible container")
                
            } catch (e: Throwable) {
                Log.e("HeadlessWebView", "Failed to create WebView", e)
                ref.set(null)
            }
            latch.countDown()
        }
        
        if (!latch.await(5, TimeUnit.SECONDS)) return null
        return ref.get()
    }

    fun close() {
        onMain {
            // Hapus container dari window
            containerView?.let { view ->
                try {
                    windowManager?.removeView(view)
                } catch (_: Exception) {}
                try {
                    (view as? ViewGroup)?.removeAllViews()
                } catch (_: Exception) {}
            }
            
            // Destroy WebView
            webView?.let { wv ->
                try {
                    wv.stopLoading()
                    wv.removeAllViews()
                    wv.destroy()
                } catch (_: Throwable) {}
            }
            
            containerView = null
            windowManager = null
            webView = null
        }
    }
}
```

2. Update open() Method

Goal: Pastikan setiap navigasi mendapatkan layout yang proper.

```kotlin
fun open(url: String, timeoutMs: Long): Boolean {
    val wv = ensureWebView() ?: return false
    clearInterceptedResponses()
    
    val latch = CountDownLatch(1)
    onMain {
        try {
            // Siapkan view untuk render
            wv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    // Inject interceptor di awal
                    view?.evaluateJavascript(NETWORK_INTERCEPTOR_JS, null)
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Layout setelah page selesai
                    view?.post {
                        view.measure(
                            View.MeasureSpec.makeMeasureSpec(DEFAULT_VIEWPORT_WIDTH, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(DEFAULT_VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY)
                        )
                        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                        view.onWindowFocusChanged(true)
                    }
                    latch.countDown()
                }
                
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    Log.w("HeadlessWebView", "Load error: ${error?.description}")
                    latch.countDown()
                }
            }
            
            // Load URL
            wv.loadUrl(url)
            
        } catch (e: Throwable) {
            Log.e("HeadlessWebView", "Open failed", e)
            latch.countDown()
        }
    }
    
    return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
}
```

3. Tambahkan onWindowFocusChanged di Setiap Interaksi

```kotlin
private fun prepareForInteraction(wv: WebView) {
    val latch = CountDownLatch(1)
    onMain {
        try {
            wv.onWindowFocusChanged(true)
            wv.requestFocus()
            wv.requestFocusFromTouch()
            // Layout ulang untuk memastikan ukuran
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(wv.width.takeIf { it > 0 } ?: DEFAULT_VIEWPORT_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(wv.height.takeIf { it > 0 } ?: DEFAULT_VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY)
            )
        } catch (_: Throwable) {}
        latch.countDown()
    }
    try {
        latch.await(DEFAULT_EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    }
}
```

4. Update Dependency Injection (AppModule.kt)

Goal: Kirim Activity Context ke HeadlessWebViewManager saat tersedia.

```kotlin
// app/src/main/java/app/rocat/di/AppModule.kt

@Provides
@Singleton
fun provideHeadlessWebViewManager(
    app: Application,
    // Optional: Activity context jika tersedia
): HeadlessWebViewManager {
    // Coba dapatkan Activity dari Injekt atau Context
    val activityContext = try {
        // Jika ada activity context yang tersedia
        Injekt.get<Activity?>(named("currentActivity"))
    } catch (_: Exception) {
        null
    }
    return HeadlessWebViewManager(app, activityContext)
}
```

5. Update ScriptCanvasViewModel

Goal: Kirim Activity Context dari UI ke headless manager.

```kotlin
// ScriptCanvasViewModel.kt
class ScriptCanvasViewModel(
    private val scriptId: String,
    private val appContext: Context,
    private val activityContext: Context? = null,  // NEW
) : ViewModel() {
    
    private val headlessManager = HeadlessWebViewManager(appContext, activityContext)
    // ...
}
```

6. Tambahkan Debug Logging

Goal: Pantau perbedaan antara mode.

```kotlin
// Tambahkan di HeadlessWebViewManager
fun getDebugInfo(): String {
    val wv = webView ?: return "WebView: null"
    val settings = wv.settings
    return """
        WebView Debug Info:
        - Width: ${wv.width}, Height: ${wv.height}
        - Is Attached: ${wv.isAttachedToWindow}
        - Has Window Focus: ${wv.hasWindowFocus()}
        - Is Focusable: ${wv.isFocusable}
        - JS Enabled: ${settings.javaScriptEnabled}
        - DOM Storage: ${settings.domStorageEnabled}
        - UserAgent: ${settings.userAgentString}
        - Load With Overview: ${settings.loadWithOverviewMode}
        - Use Wide Viewport: ${settings.useWideViewPort}
    """.trimIndent()
}
```

7. Tambahkan Retry Mechanism untuk SPA

Goal: Tunggu SPA mount.

```kotlin
fun waitForRender(timeoutMs: Long = 10000): Boolean {
    val js = """
        (function() {
            try {
                var body = document.body;
                if (!body) return false;
                var html = body.innerHTML;
                // Cek apakah ada konten yang berarti
                if (html.length < 100) return false;
                // Cek apakah React/Vue sudah mount
                var hasReact = !!window.__REACT_DEVTOOLS_GLOBAL_HOOK__;
                var hasVue = !!window.__VUE__;
                return true;
            } catch(e) {
                return false;
            }
        })()
    """.trimIndent()
    
    val deadline = System.currentTimeMillis() + timeoutMs
    while (true) {
        if (evaluateJs(js, 1000) == "true") return true
        if (System.currentTimeMillis() >= deadline) return false
        Thread.sleep(200)
    }
}
```

Mandatory Tasks (WAJIB):

· Update HeadlessWebViewManager.kt dengan attach ke window invisible
· Update dependency injection untuk kirim Activity Context
· Tambahkan waitForRender() method
· Update ScriptCanvasViewModel untuk terima Activity Context
· Pastikan build success: bash ./gradlew assembleDebug
· Buat task file: ai_memory/task_YYYYMMDD_HHMM_Fase_43.md
· Update ai_memory/00_INDEX.md

Testing:

1. Buka web SPA (CapCut/SaveKit) di mode browser → harus normal
2. Buka web SPA yang sama di mode script → harus sama normalnya
3. Cek logcat untuk perbandingan debug info

Constraints:

· Jangan merusak fungsi browser UI yang sudah berjalan
· Pastikan tidak ada memory leak (container di-remove di close)
· Harus backward compatible dengan script existing
· Batasi resource: container invisible hanya 1x1 pixel

Expected Outcome:
Headless WebView sekarang punya "perlakuan" yang sama dengan browser UI, sehingga SPA modern bisa render dengan sempurna di mode script.
