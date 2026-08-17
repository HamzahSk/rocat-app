package app.rocat.scripting.rhino

import app.rocat.scripting.api.FetchResult
import app.rocat.scripting.api.ScriptBrowserBridge
import app.rocat.scripting.api.ScriptResult
import app.rocat.scripting.api.model.DefaultScriptEnvironment
import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tahap 25 — verifies the general-purpose `RoCatBrowser` automation polyfill (a
 * Playwright/Puppeteer-like Browser / Page / Locator API layered on top of the
 * low-level `RoCatPage` native bridge):
 *
 *  1. `RoCatBrowser` is only registered when a [ScriptBrowserBridge] is present.
 *  2. `launch()` → `newPage()` → `goto()` round-trips to the native bridge and the
 *     page's `title()` / `url()` come back from the live DOM.
 *  3. Locator operations (`exists`, `fill`, `click`, `text`, `all`, `clickAll`,
 *     `waitFor`, `getAttribute`) drive `RoCatPage.evaluate` with serialised args.
 *  4. `evaluate(fn, args)` executes an inline function and returns native JS values.
 *  5. Cookies / screenshot / navigation / time-control commands forward correctly.
 *  6. Missing elements and timeouts surface as script-catchable errors / `{success:false}`.
 */
class RoCatBrowserAutomationTest {

    private val engine = RhinoScriptEngine(OkHttpClient.Builder().build())

    private fun script(source: String) = Script(id = "browser", name = "browser", source = source)

    /**
     * A tiny fake browser whose `evaluate` answers from canned JSON strings detected by
     * markers in the generated script — the wrapper always sends
     * `(function (…args…) {…})("…", …)` so the selector literal is searchable.
     */
    private class FakeBrowser : ScriptBrowserBridge {
        val calls = mutableListOf<String>()
        var currentUrl = "https://example.com/login"
        var readyState = "complete"
        var hasElement = true

        override fun open(url: String, timeoutMs: Long): Boolean {
            calls += "open:$url:$timeoutMs"
            currentUrl = url
            return true
        }

        override fun type(selector: String, text: String): Boolean {
            calls += "type:$selector:$text"
            return true
        }

        override fun click(selector: String): Boolean {
            calls += "click:$selector"
            return true
        }

        override fun waitForSelector(selector: String, timeoutMs: Long): Boolean {
            calls += "wait:$selector:$timeoutMs"
            return true
        }

        override fun evaluate(script: String): String {
            calls += "eval"
            return when {
                script.contains("#missing") ->
                    "{\"success\":false,\"error\":\"Element not found: #missing\"}"
                script.contains("document.title") -> "\"Fake Title\""
                script.contains("location.href") -> "\"$currentUrl\""
                script.contains("readyState") -> "\"$readyState\""
                script.contains("outerHTML") -> "\"<html><body>Rendered</body></html>\""
                script.contains("index: i") ->
                    "[{\"index\":0,\"success\":true},{\"index\":1,\"success\":true}]"
                script.contains("querySelectorAll") -> "[" +
                    "{\"text\":\"One\",\"html\":\"<li>One</li>\",\"attributes\":{}}," +
                    "{\"text\":\"Two\",\"html\":\"<li>Two</li>\",\"attributes\":{}}]"
                script.contains("getBoundingClientRect") ->
                    "{\"x\":0,\"y\":0,\"width\":100,\"height\":50,\"top\":0,\"right\":100,\"bottom\":50,\"left\":0}"
                script.contains("getAttribute") -> "\"item-1\""
                script.contains("scrollIntoView") -> "true"
                script.contains("textContent") -> "\"Hello World\""
                script.contains("sum") -> "{\"sum\":42}"
                script.contains("querySelector") -> if (hasElement) "true" else "false"
                else -> "true"
            }
        }

        override fun getHtml(): String {
            calls += "getHtml"
            return "<html><body>Rendered</body></html>"
        }

        override fun sleep(ms: Long): Boolean {
            calls += "sleep:$ms"
            return true
        }

        override fun url(): String {
            calls += "url"
            return currentUrl
        }

        override fun title(): String {
            calls += "title"
            return "Fake Title"
        }

        override fun goBack(): Boolean {
            calls += "back"
            return true
        }

        override fun goForward(): Boolean {
            calls += "forward"
            return true
        }

        override fun reload(): Boolean {
            calls += "reload"
            return true
        }

        override fun waitForLoad(state: String, timeoutMs: Long): Boolean {
            calls += "waitForLoad:$state:$timeoutMs"
            return true
        }

        override fun screenshot(path: String, quality: Int): String {
            calls += "screenshot:$path:$quality"
            return "/cache/browser_screenshots/shot_1.png"
        }

        override fun getCookies(): String {
            calls += "getCookies"
            return "[]"
        }

        override fun setCookie(cookieJson: String): Boolean {
            calls += "setCookie:$cookieJson"
            return true
        }

        override fun clearCookies(): Boolean {
            calls += "clearCookies"
            return true
        }

        override fun close() {
            calls += "close"
        }
    }

    private fun env(browser: ScriptBrowserBridge) = DefaultScriptEnvironment(
        fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
        browser = browser,
    )

    @Test
    fun `rocatorbrowser global is only registered when a browser bridge is present`() = runBlocking {
        val plainSource = "function main() { return typeof RoCatBrowser; }"

        val plainEnv = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
        )
        val plain = engine.execute(script(plainSource), plainEnv)
        assertEquals(ScriptResult.Success("undefined"), plain)

        val withBrowser = engine.execute(script(plainSource), env(FakeBrowser()))
        assertEquals(ScriptResult.Success("object"), withBrowser)
    }

    @Test
    fun `launch newPage goto returns title url and locator verdicts`() = runBlocking {
        val browser = FakeBrowser()
        val source = """
            function main() {
                var b = RoCatBrowser.launch({ headless: true });
                var page = b.newPage();
                page.goto("https://example.com/login", {
                    waitUntil: "domcontentloaded",
                    timeout: 20000
                });
                var title = page.title();
                var url = page.url();
                var loc = page.locator("#user");
                var exists = loc.exists();
                var filled = loc.fill("admin").success;
                var clicked = loc.click().success;
                var txt = page.text(".msg");
                b.close();
                return JSON.stringify({
                    title: title, url: url, exists: exists,
                    filled: filled, clicked: clicked, txt: txt
                });
            }
        """.trimIndent()

        val result = engine.execute(script(source), env(browser))

        assertTrue("main failed: $result", result is ScriptResult.Success)
        val json = (result as ScriptResult.Success).value
        assertTrue("title missing in $json", json.contains("\"title\":\"Fake Title\""))
        assertTrue("url missing in $json", json.contains("\"url\":\"https://example.com/login\""))
        assertTrue("exists must be true", json.contains("\"exists\":true"))
        assertTrue("fill must succeed", json.contains("\"filled\":true"))
        assertTrue("click must succeed", json.contains("\"clicked\":true"))
        assertTrue("text must read DOM", json.contains("\"txt\":\"Hello World\""))

        assertTrue("open call missing", browser.calls.contains("open:https://example.com/login:20000"))
        assertTrue("close call missing", browser.calls.contains("close"))
    }

    @Test
    fun `evaluate with function and args returns native js values`() = runBlocking {
        val browser = FakeBrowser()
        val source = """
            function main() {
                var page = RoCatBrowser.getInstance().page();
                var result = page.evaluate(function (a, b) { return { sum: a + b }; }, [1, 41]);
                var content = page.content();
                return JSON.stringify({ sum: result.sum, contentLen: content.length });
            }
        """.trimIndent()

        val result = engine.execute(script(source), env(browser))

        assertTrue("main failed: $result", result is ScriptResult.Success)
        val json = (result as ScriptResult.Success).value
        assertTrue("sum must be 42 in $json", json.contains("\"sum\":42"))
        assertTrue("content must be rendered", json.contains("\"contentLen\":34"))
    }

    @Test
    fun `locator all clickAll and getBoundingRect return structured data`() = runBlocking {
        val browser = FakeBrowser()
        val source = """
            function main() {
                var page = RoCatBrowser.launch({}).newPage();
                var items = page.locator("ul li").all();
                var clicked = page.locator("button.x").clickAll();
                var rect = page.locator("#hero").getBoundingRect();
                var attr = page.locator("a[href]").getAttribute("data-id");
                var scrolled = page.locator("#hero").scrollIntoView();
                return JSON.stringify({
                    count: items.length,
                    first: items[0].text,
                    clickedCount: clicked.length,
                    width: rect.width,
                    attr: attr,
                    scrolled: scrolled
                });
            }
        """.trimIndent()

        val result = engine.execute(script(source), env(browser))

        assertTrue("main failed: $result", result is ScriptResult.Success)
        val json = (result as ScriptResult.Success).value
        assertTrue("count missing in $json", json.contains("\"count\":2"))
        assertTrue("first text missing", json.contains("\"first\":\"One\""))
        assertTrue("clickedCount missing", json.contains("\"clickedCount\":2"))
        assertTrue("rect width missing", json.contains("\"width\":100"))
        assertTrue("attr missing", json.contains("\"attr\":\"item-1\""))
        assertTrue("scrolled missing", json.contains("\"scrolled\":true"))
    }

    @Test
    fun `navigation cookies screenshot and time control forward to the bridge`() = runBlocking {
        val browser = FakeBrowser()
        val source = """
            function main() {
                var b = RoCatBrowser.launch({});
                var page = b.newPage();
                page.goto("https://example.com", { waitUntil: "load", timeout: 15000 });
                var waited = page.waitForSelector(".profile", 5000);
                page.waitForTimeout(200);
                var cookieLen = page.cookies().length;
                var setOk = page.setCookie({ name: "session", value: "abc", url: "https://example.com" });
                var cleared = page.clearCookies();
                var back = page.goBack();
                var forward = page.goForward();
                var reloaded = page.reload();
                var shot = page.screenshot({ quality: 90 });
                b.close();
                return JSON.stringify({
                    waited: waited, cookieLen: cookieLen, setOk: setOk, cleared: cleared,
                    back: back, forward: forward, reloaded: reloaded, shotLen: shot.length
                });
            }
        """.trimIndent()

        val result = engine.execute(script(source), env(browser))

        assertTrue("main failed: $result", result is ScriptResult.Success)
        val json = (result as ScriptResult.Success).value
        assertTrue("waited must be true", json.contains("\"waited\":true"))
        assertTrue("cookieLen must be 0", json.contains("\"cookieLen\":0"))
        assertTrue("setCookie must be true", json.contains("\"setOk\":true"))
        assertTrue("clearCookies must be true", json.contains("\"cleared\":true"))
        assertTrue("back must be true", json.contains("\"back\":true"))
        assertTrue("forward must be true", json.contains("\"forward\":true"))
        assertTrue("reload must be true", json.contains("\"reloaded\":true"))
        assertTrue("screenshot must return a path", json.contains("\"shotLen\":37"))

        assertTrue("sleep call missing", browser.calls.any { it.startsWith("sleep:") })
        assertTrue("screenshot call missing", browser.calls.any { it.startsWith("screenshot:") })
        assertTrue("setCookie call missing", browser.calls.any { it.startsWith("setCookie:") })
        assertTrue("clearCookies call missing", browser.calls.contains("clearCookies"))
    }

    @Test
    fun `missing element surfaces as success false without crashing`() = runBlocking {
        val browser = FakeBrowser()
        val source = """
            function main() {
                var page = RoCatBrowser.launch({}).newPage();
                var res = page.fill("#missing", "x");
                return JSON.stringify({ ok: res.success === false, hasError: res.error.length > 0 });
            }
        """.trimIndent()

        val result = engine.execute(script(source), env(browser))

        assertTrue("main failed: $result", result is ScriptResult.Success)
        val json = (result as ScriptResult.Success).value
        assertTrue("missing element must be a graceful failure", json.contains("\"ok\":true"))
        assertTrue("error message must be present", json.contains("\"hasError\":true"))
    }

    @Test
    fun `waitForSelector throws a script-catchable error on timeout`() = runBlocking {
        val browser = FakeBrowser().apply { hasElement = false }
        val source = """
            function main() {
                var page = RoCatBrowser.launch({}).newPage();
                var outcome = "no-throw";
                try {
                    page.waitForSelector("#ghost", 50);
                } catch (e) {
                    outcome = "threw:" + e.message;
                }
                return JSON.stringify({ outcome: outcome });
            }
        """.trimIndent()

        val result = engine.execute(script(source), env(browser))

        assertTrue("main failed: $result", result is ScriptResult.Success)
        val json = (result as ScriptResult.Success).value
        assertTrue("must throw a timeout error in $json", json.contains("threw:Timeout waiting for selector: #ghost"))
    }
}