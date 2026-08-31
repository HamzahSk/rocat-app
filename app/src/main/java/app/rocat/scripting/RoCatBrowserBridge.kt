package app.rocat.scripting

import app.rocat.scripting.api.ScriptBrowserBridge

/**
 * App-side [ScriptBrowserBridge] **general-purpose** implementation (Tahap 25). It wires
 * the low-level script globals (`RoCatPage` and, through the JS polyfill, `RoCatBrowser`)
 * to the [HeadlessWebViewManager], making every call **synchronous for the script** while
 * the hidden WebView itself runs on the Android main thread.
 *
 * It exposes the full Puppeteer/Playwright-like command set:
 *
 *  - **Page basics** — `open`, `type`, `click`, `waitForSelector`, `evaluate`, `getHtml`, `close`
 *  - **Time control** — `sleep`, `waitForLoad`
 *  - **Page info** — `url`, `title`
 *  - **Navigation** — `goBack`, `goForward`, `reload`, `stop`
 *  - **Capture** — `screenshot`
 *  - **Cookies** — `getCookies`, `setCookie`, `clearCookies`
 *
 * Cookie commands go through the WebView `CookieManager`, which is exactly the store the
 * OkHttp scraper shares via `AndroidCookieJar` — so a login performed here is immediately
 * usable by `fetch()` and vice versa.
 *
 * Every method is wrapped in `runCatching` so a WebView failure (process death, missing
 * renderer, etc.) surfaces as `false`/`""`/`"[]"` instead of an uncaught exception that
 * would kill the script run.
 */
class RoCatBrowserBridge(private val manager: HeadlessWebViewManager) : ScriptBrowserBridge {

    override fun open(url: String, timeoutMs: Long): Boolean =
        runCatching { manager.open(url, timeoutMs) }.getOrDefault(false)

    override fun type(selector: String, text: String): Boolean =
        runCatching { manager.type(selector, text) }.getOrDefault(false)

    override fun click(selector: String): Boolean =
        runCatching { manager.click(selector) }.getOrDefault(false)

    override fun waitForSelector(selector: String, timeoutMs: Long): Boolean =
        runCatching { manager.waitForSelector(selector, timeoutMs) }.getOrDefault(false)

    override fun interceptedResponse(urlPattern: String): String =
        runCatching { manager.interceptedResponse(urlPattern) }.getOrDefault("")

    override fun evaluate(script: String): String =
        runCatching { manager.evaluate(script) }.getOrElse { "null" }

    override fun getHtml(): String =
        runCatching { manager.getHtml() }.getOrDefault("")

    override fun close() {
        runCatching { manager.close() }
    }

    // --- Tahap 25: general-purpose commands ---

    override fun sleep(ms: Long): Boolean =
        runCatching { manager.sleep(ms) }.getOrDefault(false)

    override fun url(): String =
        runCatching { manager.url() }.getOrDefault("")

    override fun title(): String =
        runCatching { manager.title() }.getOrDefault("")

    override fun goBack(): Boolean =
        runCatching { manager.goBack() }.getOrDefault(false)

    override fun goForward(): Boolean =
        runCatching { manager.goForward() }.getOrDefault(false)

    override fun reload(): Boolean =
        runCatching { manager.reload() }.getOrDefault(false)

    override fun stop(): Boolean =
        runCatching { manager.stop() }.getOrDefault(false)

    override fun waitForLoad(state: String, timeoutMs: Long): Boolean =
        runCatching { manager.waitForLoad(state, timeoutMs) }.getOrDefault(false)

    override fun screenshot(path: String, quality: Int): String =
        runCatching { manager.screenshot(path, quality) }.getOrDefault("")

    // --- Tahap 29: scrolling (Puppeteer-like) ---

    override fun scrollTo(x: Int, y: Int): Boolean =
        runCatching { manager.scrollTo(x, y) }.getOrDefault(false)

    override fun scrollBottom(): Boolean =
        runCatching { manager.scrollBottom() }.getOrDefault(false)

    override fun getCookies(): String =
        runCatching { manager.getCookies() }.getOrDefault("[]")

    override fun setCookie(cookieJson: String): Boolean =
        runCatching { manager.setCookie(cookieJson) }.getOrDefault(false)

    override fun clearCookies(): Boolean =
        runCatching { manager.clearCookies() }.getOrDefault(false)
}
