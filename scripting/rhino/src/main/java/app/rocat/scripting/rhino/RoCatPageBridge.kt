package app.rocat.scripting.rhino

import app.rocat.scripting.api.ScriptBrowserBridge
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.json.JsonParser

/**
 * The `RoCatPage` global (Tahap 23: dual-mode scraping engine): a Puppeteer-like
 * headless page driven by a hidden Android WebView. It exposes a **synchronous** API so
 * a script can mix static `fetch()` + `RoCatDOM` scraping with interactive automation
 * in a single execution flow:
 *
 *  - `RoCatPage.open(url, timeoutMs)`                       -> open a URL, wait for load
 *  - `RoCatPage.type(selector, text)`                       -> fill an input (React/Vue-friendly)
 *  - `RoCatPage.click(selector)`                            -> click an element
 *  - `RoCatPage.waitForSelector(selector, timeoutMs)`       -> wait until a selector exists
 *  - `RoCatPage.evaluate(js)`                               -> run JS in the live page, return value
 *  - `RoCatPage.getHtml()`                                  -> current rendered HTML
 *  - `RoCatPage.close()`                                    -> release the hidden WebView
 *
 *  Tahap 25 general-purpose primitives (used by the `RoCatBrowser` polyfill):
 *  - `RoCatPage.sleep(ms)` / `waitForLoad(state, timeoutMs)` -> time control
 *  - `RoCatPage.url()` / `title()`                           -> page info
 *  - `RoCatPage.goBack()` / `goForward()` / `reload()` / `stop()` -> navigation
 *  - `RoCatPage.screenshot(path, quality)`                   -> PNG capture, returns file path
 *  - `RoCatPage.getCookies()` / `setCookie(json)` / `clearCookies()` -> cookie sync
 *
 * Every call is delegated to the [ScriptBrowserBridge] supplied by the host app. The
 * engine executes scripts on a background thread, so the bridge blocks that thread until
 * the main-thread WebView answers (the main UI thread is never blocked).
 */
internal class RoCatPageBridge(
    private val cx: Context,
    private val scope: Scriptable,
    private val browser: ScriptBrowserBridge,
) : ScriptableObject() {

    init {
        put("open", this, PageFn { args ->
            browser.open(pageArgString(args, 0), pageArgLong(args, 1, ScriptBrowserBridge.DEFAULT_TIMEOUT_MS))
        })
        put("type", this, PageFn { args ->
            browser.type(pageArgString(args, 0), pageArgString(args, 1))
        })
        put("click", this, PageFn { args ->
            browser.click(pageArgString(args, 0))
        })
        put("waitForSelector", this, PageFn { args ->
            browser.waitForSelector(pageArgString(args, 0), pageArgLong(args, 1, ScriptBrowserBridge.DEFAULT_TIMEOUT_MS))
        })
        put("evaluate", this, PageFn { args ->
            evaluateResult(browser.evaluate(pageArgString(args, 0)))
        })
        put("getHtml", this, PageFn { browser.getHtml() })
        put("close", this, PageFn { browser.close() })

        // --- Tahap 25: general-purpose automation commands (Playwright-like subset) ---
        // These are the low-level primitives the high-level `RoCatBrowser` JS polyfill
        // (RoCatBrowserWrapper.kt) is built on top of.
        put("sleep", this, PageFn { args ->
            browser.sleep(pageArgLong(args, 0, 0))
        })
        put("url", this, PageFn { browser.url() })
        put("title", this, PageFn { browser.title() })
        put("goBack", this, PageFn { browser.goBack() })
        put("goForward", this, PageFn { browser.goForward() })
        put("reload", this, PageFn { browser.reload() })
        put("stop", this, PageFn { browser.stop() })
        put("waitForLoad", this, PageFn { args ->
            browser.waitForLoad(pageArgString(args, 0, "load"), pageArgLong(args, 1, ScriptBrowserBridge.DEFAULT_TIMEOUT_MS))
        })
        put("screenshot", this, PageFn { args ->
            browser.screenshot(pageArgString(args, 0), pageArgInt(args, 1, 80))
        })
        put("getCookies", this, PageFn { browser.getCookies() })
        put("setCookie", this, PageFn { args ->
            browser.setCookie(pageArgString(args, 0))
        })
        put("clearCookies", this, PageFn { browser.clearCookies() })
    }

    override fun getClassName(): String = "RoCatPageBridge"

    /**
     * The app bridge returns WebView's raw `evaluateJavascript` value — a JSON-encoded
     * string. Parse it back into a native JS value; when it is not valid JSON (e.g. the
     * literal `undefined`, or an internal error message) fall back to the raw string so
     * a script can still read it.
     */
    private fun evaluateResult(raw: String): Any? {
        val trimmed = raw.trim()
        if (trimmed == "undefined" || trimmed == "null") return null
        return try {
            JsonParser(cx, scope).parseValue(trimmed)
        } catch (e: Exception) {
            trimmed
        }
    }
}

/** A Rhino function that receives its raw JS arguments for a Kotlin lambda. */
private class PageFn(private val fn: (Array<out Any?>) -> Any?) : BaseFunction() {
    override fun call(
        cx: Context,
        scope: Scriptable,
        thisObj: Scriptable,
        args: Array<out Any?>,
    ): Any? = fn(args)
}

/** Reads the [index]-th JS argument as a String, or [default] when absent/undefined. */
private fun pageArgString(args: Array<out Any?>, index: Int, default: String = ""): String {
    val value = args.getOrNull(index) ?: return default
    if (value === Undefined.instance) return default
    return Context.toString(value)
}

/** Reads the [index]-th JS argument as a Long, or [default] when absent/undefined. */
private fun pageArgLong(args: Array<out Any?>, index: Int, default: Long): Long {
    val value = args.getOrNull(index) ?: return default
    if (value === Undefined.instance) return default
    return when (value) {
        is Number -> value.toLong()
        is CharSequence -> value.toString().trim().toLongOrNull() ?: default
        else -> runCatching { Context.toNumber(value).toLong() }.getOrDefault(default)
    }
}

/** Reads the [index]-th JS argument as an Int, or [default] when absent/undefined. */
private fun pageArgInt(args: Array<out Any?>, index: Int, default: Int): Int {
    val value = args.getOrNull(index) ?: return default
    if (value === Undefined.instance) return default
    return when (value) {
        is Number -> value.toInt()
        is CharSequence -> value.toString().trim().toIntOrNull() ?: default
        else -> runCatching { Context.toNumber(value).toInt() }.getOrDefault(default)
    }
}
