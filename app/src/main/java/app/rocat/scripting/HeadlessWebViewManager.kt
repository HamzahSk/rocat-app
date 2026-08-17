package app.rocat.scripting

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import app.rocat.core.common.util.WebViewUtil
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

/**
 * Manages a single **headless** [WebView] (Tahap 23: dual-mode scraping engine) that
 * backs the script-facing `RoCatPage` global.
 *
 * WebView is Android main-thread bound, while the Rhino engine runs scripts on a
 * background thread. Every public method therefore marshals its work onto the main
 * looper via [Handler] and blocks the calling thread with a [CountDownLatch] until a
 * result is ready. The main UI thread is never blocked — only the script thread parks.
 *
 * The WebView is created lazily on first use and torn down by [close]; the instance is
 * never attached to a view hierarchy, so nothing leaks into the UI.
 */
class HeadlessWebViewManager(private val appContext: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var webView: WebView? = null

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /** Creates the hidden WebView on the main thread if it does not exist yet. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView? {
        webView?.let { return it }
        val latch = CountDownLatch(1)
        val ref = AtomicReference<WebView?>()
        onMain {
            try {
                val wv = WebView(appContext)
                WebViewUtil.setDefaultSettings(wv)
                wv.setBackgroundColor(Color.TRANSPARENT)
                wv.webViewClient = WebViewClient()
                webView = wv
                ref.set(wv)
            } catch (_: Throwable) {
                ref.set(null)
            }
            latch.countDown()
        }
        if (!latch.await(5, TimeUnit.SECONDS)) return null
        return ref.get()
    }

    /**
     * Opens [url] and blocks until `onPageFinished` (or a load error) fires or
     * [timeoutMs] elapses.
     */
    fun open(url: String, timeoutMs: Long): Boolean {
        val wv = ensureWebView() ?: return false
        val latch = CountDownLatch(1)
        onMain {
            try {
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) = latch.countDown()
                }
                wv.loadUrl(url)
            } catch (_: Throwable) {
                latch.countDown()
            }
        }
        return latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    /** Fills the element matching [selector] with [text] (React/Vue-friendly events). */
    fun type(selector: String, text: String): Boolean {
        val js = """
            (function() {
                var el = document.querySelector(${jsQuote(selector)});
                if (!el) return false;
                try { el.focus(); } catch (e) {}
                el.value = ${jsQuote(text)};
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                try { el.dispatchEvent(new Event('blur', { bubbles: true })); } catch (e) {}
                return true;
            })()
        """.trimIndent()
        return evaluateJs(js, DEFAULT_EVAL_TIMEOUT_MS) == "true"
    }

    /**
     * Clicks [selector] with a **native touch sequence** (Tahap 30).
     *
     * A plain JS `element.click()` / `dispatchEvent` produces *untrusted* DOM events
     * (`isTrusted === false`) that modern SPA frameworks (React/Vue) and anti-bot
     * gateways (CapCut signup, hCaptcha, Cloudflare Turnstile, ...) routinely ignore,
     * so the page never reacts. To be treated as a genuine screen interaction the click
     * is dispatched as a real [MotionEvent] pair (`ACTION_DOWN` → `ACTION_UP`) at the
     * element's on-screen center through `WebView.dispatchTouchEvent`, which makes the
     * page observe trusted `touchstart/touchend → pointerdown/pointerup →
     * mousedown/mouseup → click` events exactly like a human tap.
     *
     * The WebView is first laid out to the default viewport (same as [screenshot]) so
     * the element's `getBoundingClientRect` coordinates map onto a real coordinate
     * space, and the element is scrolled into view when it sits outside the viewport.
     * DOM coordinates are **CSS pixels** while a [MotionEvent] lives in the view's
     * physical-pixel space, so the bounding box center is multiplied by the live
     * layout-viewport ratio (`viewWidth / window.innerWidth`) before dispatch —
     * without it a tap misses on any page whose density / viewport differs from 1:1.
     * When the element cannot be located (or the native dispatch is rejected) the
     * previous JS event-sequence fallback is used, so the method never crashes and
     * never reports a false negative.
     */
    fun click(selector: String): Boolean {
        val wv = webView ?: ensureWebView() ?: return false
        measureIfNeeded(wv)
        prepareForInteraction(wv)
        val scale = viewportScale(wv) ?: return clickViaJs(selector)
        val bounds = elementBounds(wv, selector)
        if (bounds != null) {
            val cx = ((bounds[0] + bounds[2] / 2f) * scale[0]).roundToInt()
            val cy = ((bounds[1] + bounds[3] / 2f) * scale[1]).roundToInt()
            if (dispatchNativeTap(wv, cx, cy)) return true
        }
        return clickViaJs(selector)
    }

    /**
     * Ensures the (never-attached) WebView has a real width/height so element
     * coordinates and touch dispatch work in a genuine coordinate space. Uses the
     * same default viewport as [screenshot]; no-op once the view is laid out.
     */
    private fun measureIfNeeded(wv: WebView) {
        if (wv.width > 0 && wv.height > 0) return
        val latch = CountDownLatch(1)
        onMain {
            try {
                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(DEFAULT_VIEWPORT_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(DEFAULT_VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY),
                )
                wv.layout(0, 0, wv.measuredWidth, wv.measuredHeight)
                // Kick the compositor: a detached WebView has no window/choreographer to
                // schedule frames on its own, so force one real frame here (same trick as
                // screenshot()). This makes the renderer apply the fresh 1366×768 size and
                // produce up-to-date hit-test geometry before any touch is dispatched.
                try {
                    val scratch = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                    wv.draw(Canvas(scratch))
                    scratch.recycle()
                } catch (_: Throwable) {
                    // Best-effort; the viewport-scale poll in viewportScale() still settles.
                }
            } catch (_: Throwable) {
                // Layout is best-effort; touch fallback still applies below.
            }
            latch.countDown()
        }
        try {
            latch.await(1, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Gives the (never-attached) WebView a real "active view" state. A detached WebView
     * has no window / attach info, so Android treats it as non-focusable and without
     * window focus; some WebView input paths (focus steering, touch-mode handling,
     * selection) behave differently for such views and can swallow or reject a synthetic
     * [MotionEvent]. We explicitly enable, focus and "window-focus" the view before a tap.
     * Every step is best-effort and wrapped — a headless edge case must never crash.
     */
    private fun prepareForInteraction(wv: WebView) {
        val latch = CountDownLatch(1)
        onMain {
            try {
                if (!wv.isEnabled) wv.isEnabled = true
                wv.isFocusable = true
                wv.isFocusableInTouchMode = true
                wv.isClickable = true
                wv.setLongClickable(false)
                wv.onWindowFocusChanged(true)
                wv.requestFocus(View.FOCUS_DOWN)
                wv.requestFocusFromTouch()
            } catch (_: Throwable) {
                // Best-effort focus hints — native dispatch still applies below.
            } finally {
                latch.countDown()
            }
        }
        try {
            latch.await(DEFAULT_EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Returns the CSS-pixel → view-pixel scale factors `[scaleX, scaleY]` for the live
     * layout viewport, or `null` when the page reports no viewport yet.
     *
     * `getBoundingClientRect()` / `window.innerWidth` are **CSS pixels**, but a
     * [MotionEvent] dispatched to the WebView is in the view's own coordinate space
     * (physical pixels). The WebView maps its layout viewport onto its measured box, so
     * the conversion is exactly `viewWidth / window.innerWidth` (and likewise height).
     * Tahap 30 v1 ignored this ratio and tapped raw CSS coordinates — that only lands on
     * the element when the viewport happens to be 1:1, and misses on every hi-dpi
     * device (`device-width` < view px), pages without a viewport meta (980px default)
     * or any page that zooms. This also polls `window.innerWidth` until it stabilises so
     * the scale is computed after the renderer picks up the freshly-measured viewport
     * size, not from a stale one.
     */
    private fun viewportScale(wv: WebView): FloatArray? {
        var innerW = 0f
        var innerH = 0f
        for (attempt in 0 until VIEWPORT_SETTLE_TRIES) {
            val raw = evaluateJs(
                "JSON.stringify([window.innerWidth || 0, window.innerHeight || 0])",
                DEFAULT_EVAL_TIMEOUT_MS,
            )
            val dims = parseFloatArray(raw)
            if (dims != null && dims[0] > 0f && dims[1] > 0f) {
                if (innerW > 0f && dims[0] == innerW && dims[1] == innerH) {
                    innerW = dims[0]
                    innerH = dims[1]
                    break
                }
                innerW = dims[0]
                innerH = dims[1]
            }
            if (attempt < VIEWPORT_SETTLE_TRIES - 1) sleep(POLL_INTERVAL_MS)
        }
        if (innerW <= 0f || innerH <= 0f) return null
        return floatArrayOf(
            (wv.width.toFloat() / innerW).coerceIn(MIN_TAP_SCALE, MAX_TAP_SCALE),
            (wv.height.toFloat() / innerH).coerceIn(MIN_TAP_SCALE, MAX_TAP_SCALE),
        )
    }

    /** Parses a JSON float array string (`[a, b, c, ...]`) or null on any failure. */
    private fun parseFloatArray(raw: String?): FloatArray? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "undefined") return null
        return try {
            val arr = JSONArray(trimmed)
            FloatArray(arr.length()) { i -> arr.getDouble(i).toFloat() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns the live bounding box of [selector] as `[left, top, width, height]`,
     * after scrolling the element into the viewport center. `null` when the element
     * does not exist or has no renderable size (hidden, `display:none`, detached).
     */
    private fun elementBounds(wv: WebView, selector: String): FloatArray? {
        val js = """
            (function() {
                var el = document.querySelector(${jsQuote(selector)});
                if (!el) return null;
                try { el.scrollIntoView({ block: 'center', inline: 'center' }); }
                catch (e) { try { el.scrollIntoView(); } catch (e2) {} }
                var r = el.getBoundingClientRect();
                if (!r || r.width <= 0 || r.height <= 0) return null;
                return [r.left, r.top, r.width, r.height];
            })()
        """.trimIndent()
        val raw = evaluateJs(js, DEFAULT_EVAL_TIMEOUT_MS) ?: return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "undefined") return null
        return try {
            val arr = JSONArray(trimmed)
            floatArrayOf(
                arr.getDouble(0).toFloat(),
                arr.getDouble(1).toFloat(),
                arr.getDouble(2).toFloat(),
                arr.getDouble(3).toFloat(),
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Dispatches a trusted screen tap at `(x, y)` through the Android input pipeline:
     * `ACTION_DOWN` → short gap → `ACTION_UP`, each as a touchscreen-sourced
     * [MotionEvent]. This is what makes the page see `isTrusted: true` events instead
     * of the synthetic (untrusted) DOM events a JS `el.click()` produces.
     */
    private fun dispatchNativeTap(wv: WebView, x: Int, y: Int): Boolean {
        // Let the page settle any async layout/scroll triggered by the preceding
        // scrollIntoView + viewport resize before the finger goes down, so the
        // renderer's hit-test sees the same geometry getBoundingClientRect reported.
        // This sleep is on the script thread only — the main/UI thread stays free.
        try {
            Thread.sleep(TAP_SETTLE_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }

        val downTime = SystemClock.uptimeMillis()

        val downLatch = CountDownLatch(1)
        val downRef = AtomicReference(false)
        onMain {
            try {
                val down = MotionEvent.obtain(
                    downTime, downTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0,
                ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                val handled = wv.dispatchTouchEvent(down)
                down.recycle()
                downRef.set(handled)
            } catch (_: Throwable) {
                downRef.set(false)
            } finally {
                downLatch.countDown()
            }
        }
        if (!awaitLatch(downLatch)) return false

        // A realistic tap has a few ms between finger-down and finger-up; this also
        // lets the page start its press/ripple handling before the release arrives.
        try {
            Thread.sleep(TAP_GAP_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }

        val upLatch = CountDownLatch(1)
        val upRef = AtomicReference(false)
        onMain {
            try {
                val up = MotionEvent.obtain(
                    downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x.toFloat(), y.toFloat(), 0,
                ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                val handled = wv.dispatchTouchEvent(up)
                up.recycle()
                upRef.set(handled)
            } catch (_: Throwable) {
                upRef.set(false)
            } finally {
                upLatch.countDown()
            }
        }
        if (!awaitLatch(upLatch)) return downRef.get()
        return downRef.get() || upRef.get()
    }

    /** Blocks the calling (script) thread on [latch] until it opens or the timeout hits. */
    private fun awaitLatch(latch: CountDownLatch): Boolean = try {
        latch.await(DEFAULT_EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        false
    }

    /** Fallback: the classic synthetic pointer/mouse click sequence, straight to JS. */
    private fun clickViaJs(selector: String): Boolean {
        val js = """
            (function() {
                var el = document.querySelector(${jsQuote(selector)});
                if (!el) return false;
                var opts = { bubbles: true, cancelable: true, view: window };
                try {
                    el.dispatchEvent(new MouseEvent('pointerdown', opts));
                    el.dispatchEvent(new MouseEvent('mousedown', opts));
                    el.dispatchEvent(new MouseEvent('pointerup', opts));
                    el.dispatchEvent(new MouseEvent('mouseup', opts));
                    el.dispatchEvent(new MouseEvent('click', opts));
                } catch (e) {
                    el.click();
                }
                return true;
            })()
        """.trimIndent()
        return evaluateJs(js, DEFAULT_EVAL_TIMEOUT_MS) == "true"
    }

    /** Polls the live DOM until [selector] exists or [timeoutMs] elapses. */
    fun waitForSelector(selector: String, timeoutMs: Long): Boolean {
        val probe = "(document.querySelector(${jsQuote(selector)}) !== null)"
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (evaluateJs(probe, DEFAULT_EVAL_TIMEOUT_MS) == "true") return true
            if (System.currentTimeMillis() >= deadline) return false
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return false
            }
        }
    }

    /** Runs [script] in the live page and returns the raw JSON-encoded result. */
    fun evaluate(script: String): String =
        evaluateJs(script, DEFAULT_EVAL_TIMEOUT_MS) ?: "null"

    /** Returns the current fully-rendered HTML (JSON-decoded). */
    fun getHtml(): String {
        val raw = evaluateJs("document.documentElement.outerHTML", DEFAULT_EVAL_TIMEOUT_MS) ?: return ""
        return unquoteJson(raw)
    }

    /** Releases the hidden WebView and frees its memory (safe to call repeatedly). */
    fun close() {
        onMain {
            val wv = webView
            webView = null
            wv?.let { view ->
                try {
                    view.stopLoading()
                    view.removeAllViews()
                    view.destroy()
                } catch (_: Throwable) {
                    // Already destroyed — nothing else to free.
                }
            }
        }
    }

    // =====================================================================
    // Tahap 25 — General-purpose automation commands (Playwright-like subset)
    // =====================================================================

    /** Pauses the calling (script) thread for [ms] ms — no main-thread hop needed. */
    fun sleep(ms: Long): Boolean = try {
        Thread.sleep(ms.coerceAtLeast(0))
        true
    } catch (_: InterruptedException) {
        false
    }

    /** Current page URL (via the live DOM), or `""` when no page is open. */
    fun url(): String {
        val raw = evaluateJs("location.href", DEFAULT_EVAL_TIMEOUT_MS) ?: return ""
        return unquoteJson(raw)
    }

    /** Current page title (via the live DOM), or `""` when no page is open. */
    fun title(): String {
        val raw = evaluateJs("document.title", DEFAULT_EVAL_TIMEOUT_MS) ?: return ""
        return unquoteJson(raw)
    }

    /** Navigates the WebView back one history entry. */
    fun goBack(): Boolean = navigate { it.goBack() }

    /** Navigates the WebView forward one history entry. */
    fun goForward(): Boolean = navigate { it.goForward() }

    /** Reloads the current page. */
    fun reload(): Boolean = navigate { it.reload() }

    /** Stops the current page load. */
    fun stop(): Boolean = navigate { it.stopLoading() }

    /**
     * Polls `document.readyState` until it reaches the target for [state] or
     * [timeoutMs] elapses. `"load"`/`"complete"` target `complete`;
     * `"domcontentloaded"`/`"interactive"` target `interactive`.
     */
    fun waitForLoad(state: String, timeoutMs: Long): Boolean {
        val target = when (state.trim().lowercase()) {
            "domcontentloaded", "interactive" -> "interactive"
            else -> "complete"
        }
        val probe = "(document.readyState === ${jsQuote(target)})"
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (evaluateJs(probe, DEFAULT_EVAL_TIMEOUT_MS) == "true") return true
            if (System.currentTimeMillis() >= deadline) return false
            try {
                Thread.sleep(POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                return false
            }
        }
    }

    /**
     * Draws the rendered page into a bitmap and writes it as a PNG to [path] (when
     * absolute / non-blank) or a timestamped file under the app cache. Returns the
     * absolute path of the written file, or `""` on any failure.
     */
    fun screenshot(path: String, quality: Int): String {
        val wv = webView ?: return ""
        val latch = CountDownLatch(1)
        val ref = AtomicReference<Bitmap?>()
        onMain {
            try {
                if (wv.width <= 0 || wv.height <= 0) {
                    // A never-attached WebView has zero size — give it the default
                    // viewport so drawing produces a real, full-page image.
                    wv.measure(
                        View.MeasureSpec.makeMeasureSpec(DEFAULT_VIEWPORT_WIDTH, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(DEFAULT_VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY),
                    )
                    wv.layout(0, 0, wv.measuredWidth, wv.measuredHeight)
                }
                val bitmap = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                wv.draw(Canvas(bitmap))
                ref.set(bitmap)
            } catch (_: Throwable) {
                ref.set(null)
            }
            latch.countDown()
        }
        if (!latch.await(DEFAULT_EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return ""
        val bitmap = ref.get() ?: return ""
        return try {
            val dir = File(appContext.cacheDir, SCREENSHOT_DIR).apply { mkdirs() }
            val file = if (path.isNotBlank()) {
                File(path).apply { parentFile?.mkdirs() }
            } else {
                File(dir, "shot_${System.currentTimeMillis()}.png")
            }
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, quality, out) }
            bitmap.recycle()
            file.absolutePath
        } catch (_: Throwable) {
            ""
        }
    }

    // =====================================================================
    // Tahap 29 — Scrolling (Puppeteer-like): scrollTo / scrollBottom
    // =====================================================================

    /** Scrolls the live page to the absolute viewport coordinates `(x, y)`. */
    fun scrollTo(x: Int, y: Int): Boolean {
        val js = "window.scrollTo($x, $y); true"
        return evaluateJs(js, DEFAULT_EVAL_TIMEOUT_MS) == "true"
    }

    /** Scrolls the live page to the bottom, triggering lazy-load / infinite scroll. */
    fun scrollBottom(): Boolean {
        val js = """
            (function() {
                var max = document.documentElement.scrollHeight - window.innerHeight;
                if (max > 0) { window.scrollTo(0, max); } else { window.scrollTo(0, document.body.scrollHeight); }
                return true;
            })()
        """.trimIndent()
        return evaluateJs(js, DEFAULT_EVAL_TIMEOUT_MS) == "true"
    }

    /** Cookies of the current page as a JSON array string, synced with OkHttp's jar. */
    fun getCookies(): String {
        val pageUrl = url()
        if (pageUrl.isEmpty()) return "[]"
        val cookieHeader = CookieManager.getInstance().getCookie(pageUrl) ?: return "[]"
        val host = runCatching { java.net.URI(pageUrl).host }.getOrNull() ?: ""
        val array = JSONArray()
        cookieHeader.split(";").forEach { part ->
            val separator = part.indexOf('=')
            if (separator > 0) {
                val name = part.substring(0, separator).trim()
                val value = part.substring(separator + 1).trim()
                val entry = JSONObject()
                    .put("name", name)
                    .put("value", value)
                    .put("domain", host)
                    .put("path", "/")
                    .put("url", pageUrl)
                array.put(entry)
            }
        }
        return array.toString()
    }

    /**
     * Sets a cookie from its JSON representation. Accepts either a full object
     * `{ "name", "value", "url"?, "domain"?, "path"? }` or a raw `"name=value"` string.
     * Without an explicit [JSONObject.url] the current page URL is used.
     */
    fun setCookie(cookieJson: String): Boolean {
        if (cookieJson.isBlank()) return false
        val cookie = try {
            val obj = JSONObject(cookieJson)
            val name = obj.optString("name")
            val value = obj.optString("value")
            if (name.isBlank()) return false
            val url = obj.optString("url").ifBlank { url() }
            val cookieString = buildString {
                append(name).append('=').append(value)
                append("; path=").append(obj.optString("path", "/"))
                val domain = obj.optString("domain")
                if (domain.isNotBlank()) append("; domain=").append(domain)
            }
            url to cookieString
        } catch (_: Exception) {
            // Raw "name=value" form falls back to the current page.
            val url = url()
            if (url.isBlank()) return false
            url to cookieJson
        }
        if (cookie.first.isBlank()) return false
        return try {
            CookieManager.getInstance().setCookie(cookie.first, cookie.second)
            CookieManager.getInstance().flush()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Clears every WebView cookie (shared with OkHttp via AndroidCookieJar). */
    fun clearCookies(): Boolean = try {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        true
    } catch (_: Throwable) {
        false
    }

    /** Runs [action] (navigation) on the main thread and returns whether it applied. */
    private fun navigate(action: (WebView) -> Unit): Boolean {
        val wv = webView ?: return false
        val latch = CountDownLatch(1)
        val ref = AtomicReference(false)
        onMain {
            try {
                action(wv)
                ref.set(true)
            } catch (_: Throwable) {
                ref.set(false)
            }
            latch.countDown()
        }
        if (!latch.await(DEFAULT_EVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return false
        return ref.get()
    }

    /** Runs [js] on the main thread and returns the callback value, or null on failure. */
    private fun evaluateJs(js: String, timeoutMs: Long): String? {
        val wv = webView ?: ensureWebView() ?: return null
        val latch = CountDownLatch(1)
        val ref = AtomicReference<String?>()
        onMain {
            try {
                wv.evaluateJavascript(js) { value ->
                    ref.set(value)
                    latch.countDown()
                }
            } catch (_: Throwable) {
                ref.set(null)
                latch.countDown()
            }
        }
        if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) return null
        return ref.get()
    }

    /** Decodes WebView's JSON-encoded callback string back into a plain string. */
    private fun unquoteJson(value: String): String = try {
        JSONTokener(value).nextValue() as? String ?: value
    } catch (_: Exception) {
        value
    }

    private companion object {
        const val DEFAULT_EVAL_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 150L
        const val SCREENSHOT_DIR = "browser_screenshots"
        const val DEFAULT_VIEWPORT_WIDTH = 1366
        const val DEFAULT_VIEWPORT_HEIGHT = 768
        const val TAP_GAP_MS = 80L
        const val TAP_SETTLE_MS = 120L
        const val VIEWPORT_SETTLE_TRIES = 3
        const val MIN_TAP_SCALE = 0.1f
        const val MAX_TAP_SCALE = 10f
    }
}

/** Quotes a Kotlin string for safe injection into a JS string literal. */
private fun jsQuote(value: String): String =
    "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t") + "\""
