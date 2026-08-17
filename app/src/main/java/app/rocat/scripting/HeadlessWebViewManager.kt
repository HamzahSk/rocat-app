package app.rocat.scripting

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
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

    /** Dispatches a real pointer/mouse click sequence on [selector]. */
    fun click(selector: String): Boolean {
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
