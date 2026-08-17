package app.rocat.scripting.rhino

import app.rocat.scripting.api.FetchResult
import app.rocat.scripting.api.ScriptBrowserBridge
import app.rocat.scripting.api.ScriptResult
import app.rocat.scripting.api.ScriptUiBridge
import app.rocat.scripting.api.model.DefaultScriptEnvironment
import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tahap 29 — verifies the `test_browserless.js` demo end-to-end. The script exercises
 * the Puppeteer-like `page` global (goto / type / click / waitForSelector / scrollBottom
 * / evaluate / content / screenshot) driven by a fake [ScriptBrowserBridge], proving:
 *
 *  1. `onLaunch()` renders the two demo buttons without touching the browser.
 *  2. `runDemo()` runs the full browserless flow **synchronously** (Rhino has no
 *     async/await) and returns structured results via `RoCatUI.addJsonLog`.
 *  3. `runStatic()` still works with plain `fetch()` + `RoCatDOM` — backward
 *     compatibility is untouched by the new browserless engine.
 */
class TestBrowserlessScraperTest {

    private val scriptSource: String by lazy {
        val candidates = listOf(
            "../../../test_browserless.js", // working dir = rocat-app/scripting/rhino
            "../../test_browserless.js",    // working dir = rocat-app/scripting
            "../test_browserless.js",       // working dir = rocat-app
            "test_browserless.js",          // working dir = repo root
        )
        val file = candidates.asSequence()
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("test_browserless.js not found (user.dir=${System.getProperty("user.dir")})")
        file.readText()
    }

    private fun script() = Script(id = "browserless", name = "Test Browserless", source = scriptSource)

    private class UiRecorder : ScriptUiBridge {
        val calls = mutableListOf<String>()
        override fun addInput(id: String, hint: String) { calls += "input:$id:$hint" }
        override fun addButton(label: String, functionName: String) { calls += "button:$label:$functionName" }
        override fun thumbnailPreview(url: String) { calls += "thumb:$url" }
        override fun videoPreview(url: String) { calls += "video:$url" }
        override fun addImage(url: String, title: String, allowDownload: Boolean, headers: Map<String, String>) { calls += "image:$url:$title:$allowDownload" }
        override fun addVideo(url: String, title: String, isStreamHls: Boolean, allowDownload: Boolean, headers: Map<String, String>) { calls += "video:$url:$title:$isStreamHls:$allowDownload" }
        override fun addJsonLog(dataJson: String, title: String, allowCopy: Boolean) { calls += "jsonlog:$title:$allowCopy:$dataJson" }
        override fun addHtmlPreview(htmlContent: String, title: String) { calls += "html:$title:$htmlContent" }
        override fun addAudio(url: String, title: String, allowDownload: Boolean, headers: Map<String, String>) { calls += "audio:$url:$title:$allowDownload" }
        override fun addAlert(message: String, type: String) { calls += "alert:$type:$message" }
        override fun addBadgeGroup(badgesJson: String) { calls += "badges:$badgesJson" }
        override fun clear() { calls += "clear" }
        override fun addGrid(columns: Int, itemsJsonString: String, onClickFunction: String, headers: Map<String, String>) { calls += "grid:$columns:$onClickFunction:$itemsJsonString" }
        override fun log(text: String) { calls += "log:$text" }
        override fun saveFile(fileName: String, content: String, mimeType: String): String {
            calls += "save:$fileName:$mimeType"
            return "content://rocat/test/$fileName"
        }
        override fun decodeBase64(input: String): String {
            calls += "b64:$input"
            return ""
        }
    }

    /** Fake headless browser that answers page.* commands from canned markers. */
    private class FakeBrowser : ScriptBrowserBridge {
        val calls = mutableListOf<String>()
        var opened = false

        override fun open(url: String, timeoutMs: Long): Boolean {
            calls += "open:$url:$timeoutMs"
            opened = true
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

        override fun evaluate(script: String): String = when {
            // page.evaluate(function(){ return { title, cards, scrollY, ready } })
            script.contains("scrollY") -> {
                calls += "eval"
                """{"title":"Dashboard","cards":3,"scrollY":600,"ready":"complete"}"""
            }
            script.contains("querySelector") -> {
                calls += "eval"
                "true"
            }
            script.contains("readyState") -> {
                calls += "eval"
                "\"complete\""
            }
            script.contains("document.title") -> {
                calls += "eval"
                "\"Dashboard\""
            }
            else -> {
                calls += "eval"
                "null"
            }
        }

        override fun getHtml(): String =
            "<html><body><div class=\"dashboard\"><span class=\"user-name\">Budi</span>" +
                "<div class=\"card\">A</div><div class=\"card\">B</div><div class=\"card\">C</div></div></body></html>"

        override fun sleep(ms: Long): Boolean {
            calls += "sleep:$ms"
            return true
        }

        override fun url(): String {
            calls += "url"
            return "https://example.com/login"
        }

        override fun title(): String {
            calls += "title"
            return "Dashboard"
        }

        override fun scrollTo(x: Int, y: Int): Boolean {
            calls += "scrollTo:$x:$y"
            return true
        }

        override fun scrollBottom(): Boolean {
            calls += "scrollBottom"
            return true
        }

        override fun screenshot(path: String, quality: Int): String {
            calls += "screenshot:$path:$quality"
            return "/cache/browser_screenshots/shot_demo.png"
        }

        override fun close() { calls += "close" }
    }

    private fun env(ui: ScriptUiBridge, browser: ScriptBrowserBridge, body: String = "") =
        DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), body) },
            ui = ui,
            browser = browser,
        )

    @Test
    fun `onLaunch renders demo buttons without touching the browser`() = runBlocking {
        val ui = UiRecorder()
        val browser = FakeBrowser()
        val engine = RhinoScriptEngine(OkHttpClient.Builder().build())
        val result = engine.invokeNamedFunction(
            script(),
            env(ui, browser),
            "onLaunch",
            emptyMap(),
        )
        assertTrue("onLaunch failed: $result", result is ScriptResult.Success)
        assertTrue("expected clear", ui.calls.any { it == "clear" })
        assertTrue("expected browserless button", ui.calls.any { it.contains("runDemo") })
        assertTrue("expected static button", ui.calls.any { it.contains("runStatic") })
        assertTrue("browser must not be touched by onLaunch", browser.calls.isEmpty())
    }

    @Test
    fun `runDemo drives the full browserless flow synchronously`() = runBlocking {
        val ui = UiRecorder()
        val browser = FakeBrowser()
        val engine = RhinoScriptEngine(OkHttpClient.Builder().build())
        val result = engine.invokeNamedFunction(
            script(),
            env(ui, browser),
            "runDemo",
            emptyMap(),
        )

        assertTrue("runDemo failed: $result", result is ScriptResult.Success)

        // The whole Puppeteer-like flow ran synchronously on the Rhino thread.
        assertTrue("expected goto (open)", browser.calls.any { it.startsWith("open:https://example.com/login") })
        assertTrue("expected scrollTo", browser.calls.any { it == "scrollTo:0:200" })
        assertTrue("expected scrollBottom (lazy-load)", browser.calls.any { it == "scrollBottom" })
        assertTrue("expected sleep/waitForTimeout", browser.calls.any { it.startsWith("sleep:") })
        assertTrue("expected screenshot", browser.calls.any { it.startsWith("screenshot:") })
        assertTrue("expected close (WebView released)", browser.calls.contains("close"))

        // page.type() / page.click() are driven through evaluate (locator.type / locator.click
        // dispatch real pointer+mouse events inside the page's own JS context).
        assertTrue("expected evaluate-based typing+clicking", browser.calls.count { it == "eval" } >= 4)

        // The script parsed the rendered HTML with RoCatDOM and found the user name.
        val jsonLog = ui.calls.firstOrNull { it.startsWith("jsonlog:Hasil Browserless") } ?: ""
        assertTrue("expected typedUser success in $jsonLog", jsonLog.contains("\"typedUser\":true"))
        assertTrue("expected parsed name 'Budi' in $jsonLog", jsonLog.contains("Budi"))
        assertTrue("expected contentLength in $jsonLog", jsonLog.contains("contentLength"))
        assertTrue("expected a success banner", ui.calls.any { it.startsWith("alert:success") })
    }

    @Test
    fun `runStatic keeps fetch and RoCatDOM fully working`() = runBlocking {
        val ui = UiRecorder()
        val browser = FakeBrowser()
        val staticHtml = "<html><body><h1>Example Domain</h1></body></html>"
        val engine = RhinoScriptEngine(OkHttpClient.Builder().build())
        val result = engine.invokeNamedFunction(
            script(),
            env(ui, browser, staticHtml),
            "runStatic",
            emptyMap(),
        )

        assertTrue("runStatic failed: $result", result is ScriptResult.Success)
        val jsonLog = ui.calls.firstOrNull { it.startsWith("jsonlog:Hasil Mode Statis") } ?: ""
        assertTrue("expected status 200 in $jsonLog", jsonLog.contains("\"status\":200"))
        assertTrue("expected h1 parsed via RoCatDOM in $jsonLog", jsonLog.contains("Example Domain"))
        // Static mode must never create a headless WebView.
        assertTrue("browser must not be touched in static mode, got ${browser.calls}", browser.calls.isEmpty())
    }
}
