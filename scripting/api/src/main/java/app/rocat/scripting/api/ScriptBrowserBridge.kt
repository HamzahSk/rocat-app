package app.rocat.scripting.api

/**
 * Headless-browser bridge exposed to user scripts as the global `RoCatPage` object
 * (Tahap 23: dual-mode scraping engine). It mirrors the Puppeteer subset RoCat scripts
 * need to break form logins, anti-bot challenges and JS-generated player iframes that a
 * plain `fetch()` + Jsoup parse cannot reach.
 *
 * **Threading contract:** the Rhino engine evaluates scripts on a background coroutine,
 * so every method here is **synchronous and blocking**. Implementations marshal their
 * work onto the Android main thread (a WebView is main-thread bound) and park the
 * calling thread until a result is ready. Only the background script thread is blocked —
 * the main UI thread stays responsive.
 *
 * **Dual-mode guidance:** prefer `fetch()` + `RoCatDOM` (Mode Statis) for plain HTML
 * scraping — it is cheap, fast and battery friendly. Reach for [open]/[type]/[click]/
 * [waitForSelector]/[evaluate] only when the target genuinely needs a live browser
 * (JS-rendered DOM, form submission, anti-bot walls), because rendering a real page is
 * far heavier than an HTTP request.
 *
 * All failures are reported through return values (e.g. `false`, empty string), never
 * thrown, so a misbehaving page cannot crash the script.
 */
interface ScriptBrowserBridge {

    /**
     * Opens [url] in the hidden WebView and blocks until the page finished loading or
     * [timeoutMs] elapses.
     *
     * @return `true` when the page reached `onPageFinished` (or reported a load error)
     *   within the timeout; `false` on timeout / no browser available.
     */
    fun open(url: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean

    /**
     * Fills the first element matching [selector] with [text], dispatching real
     * `focus`/`input`/`change`/`blur` events so React/Vue-style frameworks pick the
     * value up.
     *
     * @return `true` when the element existed and was filled.
     */
    fun type(selector: String, text: String): Boolean

    /**
     * Dispatches a real pointer/mouse `click` sequence on the first element matching
     * [selector] (falls back to `element.click()`).
     *
     * @return `true` when the element existed and was clicked.
     */
    fun click(selector: String): Boolean

    /**
     * Polls the live DOM until an element matching [selector] exists or [timeoutMs]
     * elapses. Useful after a click that triggers an async navigation/re-render.
     *
     * @return `true` when the selector appeared within the timeout.
     */
    fun waitForSelector(selector: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean

    /**
     * Runs [script] inside the page's own JavaScript context and returns the value the
     * browser produced as a **JSON-encoded string** (WebView's `evaluateJavascript`
     * contract). The Rhino bridge parses that string back into a native JS value; when
     * it is not JSON (e.g. the literal `undefined`) the raw string is returned instead.
     *
     * Returns `"null"` when no page is open or the script could not run.
     */
    fun evaluate(script: String): String

    /** Returns the fully-rendered current HTML (`document.documentElement.outerHTML`). */
    fun getHtml(): String

    /** Releases the hidden WebView and frees its memory. No-op when no page is open. */
    fun close()

    // =====================================================================
    // Tahap 25 — General-purpose automation commands (Playwright-like subset)
    // ---------------------------------------------------------------------
    // Every new method below has a **default no-op implementation** so existing
    // bridges / test doubles written before Tahap 25 stay valid. The app-side
    // RoCatBrowserBridge overrides the ones the hidden WebView can really do.
    // =====================================================================

    /**
     * Pauses the calling (script) thread for [ms] milliseconds. Unlike every other
     * method this does **not** hop to the main thread — the script thread itself
     * sleeps, which is cheap and lets scripts implement `waitForTimeout`.
     *
     * @return `true` when the sleep ran to completion; `false` when interrupted.
     */
    fun sleep(ms: Long): Boolean = false

    /** Current page URL, or `""` when no page is open. */
    fun url(): String = ""

    /** Current page title, or `""` when no page is open. */
    fun title(): String = ""

    /** Navigates the WebView back one history entry. */
    fun goBack(): Boolean = false

    /** Navigates the WebView forward one history entry. */
    fun goForward(): Boolean = false

    /** Reloads the current page. */
    fun reload(): Boolean = false

    /** Stops the current page load. */
    fun stop(): Boolean = false

    /**
     * Polls `document.readyState` until it reaches the target state for [state]
     * (`"load"`/`"complete"` → `complete`, `"domcontentloaded"`/`"interactive"` →
     * `interactive`) or [timeoutMs] elapses.
     *
     * @return `true` when the state was reached within the timeout.
     */
    fun waitForLoad(state: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean = false

    /**
     * Captures a screenshot of the rendered page. The headless WebView is drawn into
     * a bitmap; the PNG is written to [path] when non-blank (a directory-absolute
     * path) or to a timestamped file inside the app cache otherwise.
     *
     * @return the absolute path of the written PNG file, or `""` on failure.
     */
    fun screenshot(path: String = "", quality: Int = 80): String = ""

    /**
     * Scrolls the live page to absolute viewport coordinates `(x, y)`. Used to move the
     * page around (or back to top) without triggering lazy-load; see [scrollBottom].
     *
     * @return `true` when the scroll command reached the page.
     */
    fun scrollTo(x: Int, y: Int): Boolean = false

    /**
     * Scrolls the live page to the bottom of the document — the standard way to trigger
     * *lazy-load* / infinite-scroll sites (newsfeeds, galleries, search results) so
     * content that only renders near the viewport gets injected into the DOM.
     *
     * @return `true` when the scroll command reached the page.
     */
    fun scrollBottom(): Boolean = false

    /**
     * Returns the cookies of the current page as a **JSON array string** — each
     * entry `{ "name", "value", "domain", "path", "url" }`. The WebView
     * `CookieManager` is the exact store shared with the OkHttp scraper via
     * `AndroidCookieJar`, so this is automatically synced with `fetch()`.
     */
    fun getCookies(): String = "[]"

    /**
     * Sets a cookie from its JSON representation: `{ "name", "value", "url"?, "domain"?,
     * "path"? }`. When [cookieJson] has no `url` the current page URL is used (a blank
     * page fails). Persists immediately (`CookieManager.flush()`).
     *
     * @return `true` when the cookie was accepted.
     */
    fun setCookie(cookieJson: String): Boolean = false

    /** Clears every WebView cookie (also wiped from the OkHttp jar it shares). */
    fun clearCookies(): Boolean = false

    companion object {
        /** Default per-call timeout used by `open` / `waitForSelector`. */
        const val DEFAULT_TIMEOUT_MS: Long = 15_000L
    }
}
