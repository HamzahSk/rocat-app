package app.rocat.scripting.rhino

import app.rocat.scripting.api.ScriptBrowserBridge
import app.rocat.scripting.api.ScriptResult
import app.rocat.scripting.api.model.DefaultScriptEnvironment
import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tahap 23 — verifies the headless-browser bridge (global `RoCatPage`) that backs the
 * dual-mode scraping engine:
 *
 *  1. `RoCatPage` is only registered when the environment carries a
 *     [ScriptBrowserBridge] (plain executions see `typeof RoCatPage === "undefined"`).
 *  2. `open`/`type`/`click`/`waitForSelector` forward their arguments and return the
 *     browser's verdict.
 *  3. `evaluate` parses the browser's JSON-encoded answer back into a native JS value
 *     (string / object / number) and maps `undefined` to `null`.
 *  4. A hybrid script can mix static `fetch()` scraping with `RoCatPage` automation in
 *     a single flow (Mode Statis + Mode Interaktif).
 */
class RoCatPageBridgeTest {

    private val engine = RhinoScriptEngine(OkHttpClient.Builder().build())

    /** An engine whose global `fetch()` always answers with [body]. */
    private fun cannedEngine(body: String): RhinoScriptEngine {
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("text/html; charset=utf-8".toMediaType()))
                .build()
        }
        return RhinoScriptEngine(OkHttpClient.Builder().addInterceptor(interceptor).build())
    }

    private fun script(source: String) = Script(id = "page", name = "page", source = source)

    /** A tiny fake browser whose `evaluate` answers from canned JSON strings. */
    private class FakeBrowser : ScriptBrowserBridge {
        val calls = mutableListOf<String>()

        override fun open(url: String, timeoutMs: Long): Boolean {
            calls += "open:$url:$timeoutMs"
            return true
        }

        override fun type(selector: String, text: String): Boolean {
            calls += "type:$selector:$text"
            return selector == "#user"
        }

        override fun click(selector: String): Boolean {
            calls += "click:$selector"
            return true
        }

        override fun waitForSelector(selector: String, timeoutMs: Long): Boolean {
            calls += "wait:$selector:$timeoutMs"
            return true
        }

        override fun evaluate(script: String): String = when {
            script.contains("document.title") -> "\"Fake Title\""
            script.contains("outerHTML") -> "\"<html><body>Rendered</body></html>\""
            script.contains("a: 1") || script.contains("a:1") -> "{\"a\":1}"
            script.contains("1 + 41") || script.contains("1+41") -> "42"
            script.contains("undefined") -> "undefined"
            script.contains("querySelector") -> "true"
            else -> "true"
        }

        override fun getHtml(): String {
            calls += "getHtml"
            return "<html><body>Rendered</body></html>"
        }

        override fun close() {
            calls += "close"
        }
    }

    @Test
    fun `rocatpage global is only registered when a browser bridge is present`() = runBlocking {
        val plainEnv = DefaultScriptEnvironment(fetchImpl = { _, _, _, _ ->
            app.rocat.scripting.api.FetchResult(200, emptyMap(), "")
        })

        val plainSource = """
            function main() { return typeof RoCatPage; }
        """.trimIndent()
        val plain = engine.execute(script(plainSource), plainEnv)
        assertEquals(ScriptResult.Success("undefined"), plain)

        val browser = FakeBrowser()
        val browserEnv = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> app.rocat.scripting.api.FetchResult(200, emptyMap(), "") },
            browser = browser,
        )
        val withBrowser = engine.execute(script(plainSource), browserEnv)
        assertEquals(ScriptResult.Success("object"), withBrowser)
    }

    @Test
    fun `rocatpage forwards calls and returns bridge verdicts`() = runBlocking {
        val browser = FakeBrowser()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> app.rocat.scripting.api.FetchResult(200, emptyMap(), "") },
            browser = browser,
        )
        val source = """
            function main() {
                var opened = RoCatPage.open("https://example.com/login", 20000);
                var typed  = RoCatPage.type("#user", "admin");
                var typedMissing = RoCatPage.type("#nope", "x");
                var clicked = RoCatPage.click("#login-btn");
                var waited  = RoCatPage.waitForSelector(".dashboard", 5000);
                var html    = RoCatPage.getHtml();
                RoCatPage.close();
                return JSON.stringify({
                    opened: opened, typed: typed, typedMissing: typedMissing,
                    clicked: clicked, waited: waited, hasHtml: html.length > 0
                });
            }
        """.trimIndent()

        val result = engine.execute(script(source), env)

        assertTrue("main failed: $result", result is ScriptResult.Success)
        val json = (result as ScriptResult.Success).value
        assertTrue("opened must be true", json.contains("\"opened\":true"))
        assertTrue("typed must be true", json.contains("\"typed\":true"))
        assertTrue("missing selector must be false", json.contains("\"typedMissing\":false"))
        assertTrue("clicked must be true", json.contains("\"clicked\":true"))
        assertTrue("waited must be true", json.contains("\"waited\":true"))
        assertTrue("html must be rendered", json.contains("\"hasHtml\":true"))

        assertEquals(
            listOf(
                "open:https://example.com/login:20000",
                "type:#user:admin",
                "type:#nope:x",
                "click:#login-btn",
                "wait:.dashboard:5000",
                "getHtml",
                "close",
            ),
            browser.calls,
        )
    }

    @Test
    fun `evaluate parses json answers back into native js values`() = runBlocking {
        val browser = FakeBrowser()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> app.rocat.scripting.api.FetchResult(200, emptyMap(), "") },
            browser = browser,
        )
        val source = """
            function main() {
                var title = RoCatPage.evaluate("document.title");
                var obj   = RoCatPage.evaluate("({a: 1})");
                var num   = RoCatPage.evaluate("1 + 41");
                var undef = RoCatPage.evaluate("undefined");
                return JSON.stringify({
                    title: title,
                    objA: obj.a,
                    num: num,
                    undefIsNull: undef === null
                });
            }
        """.trimIndent()

        val result = engine.execute(script(source), env)

        assertTrue("main failed: $result", result is ScriptResult.Success)
        val json = (result as ScriptResult.Success).value
        assertTrue("title must be a string, got $json", json.contains("\"title\":\"Fake Title\""))
        assertTrue("obj.a must be 1", json.contains("\"objA\":1"))
        assertTrue("num must be 42", json.contains("\"num\":42"))
        assertTrue("undefined must map to null", json.contains("\"undefIsNull\":true"))
    }

    @Test
    fun `hybrid script uses the static fetch path when no interaction is needed`() = runBlocking {
        val browser = FakeBrowser()
        val engine = cannedEngine("<html><body>static</body></html>")
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> app.rocat.scripting.api.FetchResult(200, emptyMap(), "<html><body>static</body></html>") },
            browser = browser,
        )
        val source = """
            function main(url) {
                // Mode Statis: cheap fetch + Jsoup DOM.
                var res = fetch(url, "GET", {}, null);
                var staticText = RoCatDOM.selectText(res.text(), "body");
                if (staticText.indexOf("login") !== -1) {
                    // Mode Interaktif: form yang butuh render hidup.
                    RoCatPage.open(url, 15000);
                    RoCatPage.type("#user", "admin");
                    RoCatPage.click("#submit");
                    RoCatPage.waitForSelector(".profile", 5000);
                    var title = RoCatPage.evaluate("document.title");
                    var html = RoCatPage.getHtml();
                    RoCatPage.close();
                    return "hybrid:" + title + ":" + html.length;
                }
                return "static:" + staticText;
            }
        """.trimIndent()

        val result = engine.execute(script(source), env, listOf("https://example.com/home"))

        assertTrue("main failed: $result", result is ScriptResult.Success)
        val value = (result as ScriptResult.Success).value
        // body is "static" (no "login") so the static path wins; verify the browser
        // was never touched on the static path.
        assertEquals("static:static", value)
        assertTrue("browser must not be used on the static path", browser.calls.isEmpty())
    }

    @Test
    fun `hybrid script switches to the headless browser for interactive pages`() = runBlocking {
        val browser = FakeBrowser()
        val engine = cannedEngine("<html><body>login form</body></html>")
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> app.rocat.scripting.api.FetchResult(200, emptyMap(), "<html><body>login form</body></html>") },
            browser = browser,
        )
        val source = """
            function main(url) {
                var res = fetch(url, "GET", {}, null);
                var staticText = RoCatDOM.selectText(res.text(), "body");
                if (staticText.indexOf("login") !== -1) {
                    RoCatPage.open(url, 15000);
                    RoCatPage.type("#user", "admin");
                    RoCatPage.click("#submit");
                    RoCatPage.waitForSelector(".profile", 5000);
                    var title = RoCatPage.evaluate("document.title");
                    var html = RoCatPage.getHtml();
                    RoCatPage.close();
                    return "hybrid:" + title + ":" + html.length;
                }
                return "static:" + staticText;
            }
        """.trimIndent()

        val result = engine.execute(script(source), env, listOf("https://example.com/login"))

        assertTrue("main failed: $result", result is ScriptResult.Success)
        val value = (result as ScriptResult.Success).value
        assertEquals("hybrid:Fake Title:34", value)
        assertEquals(
            listOf(
                "open:https://example.com/login:15000",
                "type:#user:admin",
                "click:#submit",
                "wait:.profile:5000",
                "getHtml",
                "close",
            ),
            browser.calls.filterNot { it.startsWith("eval:") },
        )
    }
}
