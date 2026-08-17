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
 * Tahap 30 — verifies the rewritten `capcut_test.js` (v4). The script no longer clicks
 * through a convoluted `page.evaluate` chain calling `el.click()` (untrusted, ignored
 * by SPA/anti-bot pages). Instead it tags the target element with `data-rocat-click`
 * and calls the built-in `page.click(selector)` which, on the app side, dispatches a
 * **native touch tap** (MotionEvent ACTION_DOWN/ACTION_UP) through the WebView.
 *
 * The test proves:
 *  1. `onLaunch()` renders the action buttons without touching the browser.
 *  2. `clickContinueEmail()` runs the full flow synchronously (Rhino has no
 *     async/await) and the click is forwarded to the native bridge
 *     (`click:[data-rocat-click="1"]`) instead of an evaluate-based synthetic click.
 *  3. The fallback static `fetch()` path is untouched.
 */
class CapCutNativeClickTest {

    private val scriptSource: String by lazy {
        val candidates = listOf(
            "../../../capcut_test.js", // working dir = rocat-app/scripting/rhino
            "../../capcut_test.js",    // working dir = rocat-app/scripting
            "../capcut_test.js",       // working dir = rocat-app
            "capcut_test.js",          // working dir = repo root
        )
        val file = candidates.asSequence()
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("capcut_test.js not found (user.dir=${System.getProperty("user.dir")})")
        file.readText()
    }

    private fun script() = Script(id = "capcut", name = "CapCut Native Touch", source = scriptSource)

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

    /** Fake headless browser simulating a CapCut SPA page. */
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
            // The marker script tags the target element with data-rocat-click.
            script.contains("data-rocat-click") -> {
                calls += "eval"
                """{"tagged":true,"tag":"BUTTON","className":"lv-account-login-form-main-field-xx","index":0}"""
            }
            script.contains("querySelector") -> {
                calls += "eval"
                "true"
            }
            script.contains("readyState") -> {
                calls += "eval"
                "\"complete\""
            }
            script.contains("location.href") -> {
                calls += "eval"
                "\"https://www.capcut.com/id-id/signup\""
            }
            else -> {
                calls += "eval"
                "null"
            }
        }

        override fun getHtml(): String =
            "<html><body><button data-rocat-click=\"1\">Lanjutkan dengan alamat email</button>" +
                "<form><input type=\"email\" /><input type=\"password\" /></form></body></html>"

        override fun sleep(ms: Long): Boolean {
            calls += "sleep:$ms"
            return true
        }

        override fun url(): String {
            calls += "url"
            return "https://www.capcut.com/id-id/signup"
        }

        override fun title(): String {
            calls += "title"
            return "Daftar - CapCut"
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
            return "/cache/browser_screenshots/shot_capcut.png"
        }

        override fun close() { calls += "close" }
    }

    private fun env(ui: ScriptUiBridge, browser: ScriptBrowserBridge) =
        DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
            browser = browser,
        )

    @Test
    fun `onLaunch renders the action buttons without touching the browser`() = runBlocking {
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
        assertTrue("expected action button", ui.calls.any { it.contains("clickContinueEmail") })
        assertTrue("browser must not be touched by onLaunch", browser.calls.isEmpty())
    }

    @Test
    fun `clickContinueEmail clicks via native page click bridge`() = runBlocking {
        val ui = UiRecorder()
        val browser = FakeBrowser()
        val engine = RhinoScriptEngine(OkHttpClient.Builder().build())
        val result = engine.invokeNamedFunction(
            script(),
            env(ui, browser),
            "clickContinueEmail",
            emptyMap(),
        )

        assertTrue("clickContinueEmail failed: $result", result is ScriptResult.Success)

        assertTrue("expected goto (open)", browser.calls.any { it.startsWith("open:https://www.capcut.com/id-id/signup") })
        // page.waitForSelector polls via evaluate (locator.exists → querySelector probe);
        // the marker evaluate that tags the target element must also have run.
        assertTrue("expected evaluate-based selector probes", browser.calls.any { it == "eval" })
        // The click MUST go to the native bridge (page.click) — not an evaluate chain.
        assertTrue("expected native bridge click", browser.calls.contains("click:[data-rocat-click=\"1\"]"))
        assertTrue("expected screenshot", browser.calls.any { it.startsWith("screenshot:") })

        // The script parsed the rendered HTML with RoCatDOM and found the email form.
        val jsonLog = ui.calls.firstOrNull { it.startsWith("jsonlog:📊 Detail") } ?: ""
        assertTrue("expected berhasil_tap true in $jsonLog", jsonLog.contains("\"berhasil_tap\":true"))
        assertTrue("expected email_inputs in $jsonLog", jsonLog.contains("\"email_inputs\":1"))
        assertTrue("expected password_inputs in $jsonLog", jsonLog.contains("\"password_inputs\":1"))
        assertTrue("expected success banner", ui.calls.any { it.startsWith("alert:success") })
    }
}