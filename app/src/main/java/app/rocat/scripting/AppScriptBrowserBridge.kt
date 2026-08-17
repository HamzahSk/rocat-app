package app.rocat.scripting

import app.rocat.scripting.api.ScriptBrowserBridge

/**
 * App-side [ScriptBrowserBridge] (Tahap 23: dual-mode scraping engine). It wires the
 * script-facing `RoCatPage` global to the [HeadlessWebViewManager], making every call
 * synchronous for the script while the WebView itself runs on the Android main thread.
 *
 * Every method is wrapped in `runCatching` so a WebView failure (process death,
 * missing renderer, etc.) surfaces as `false`/`""` instead of an uncaught exception
 * that would kill the script run.
 */
class AppScriptBrowserBridge(private val manager: HeadlessWebViewManager) : ScriptBrowserBridge {

    override fun open(url: String, timeoutMs: Long): Boolean =
        runCatching { manager.open(url, timeoutMs) }.getOrDefault(false)

    override fun type(selector: String, text: String): Boolean =
        runCatching { manager.type(selector, text) }.getOrDefault(false)

    override fun click(selector: String): Boolean =
        runCatching { manager.click(selector) }.getOrDefault(false)

    override fun waitForSelector(selector: String, timeoutMs: Long): Boolean =
        runCatching { manager.waitForSelector(selector, timeoutMs) }.getOrDefault(false)

    override fun evaluate(script: String): String =
        runCatching { manager.evaluate(script) }.getOrElse { "null" }

    override fun getHtml(): String =
        runCatching { manager.getHtml() }.getOrDefault("")

    override fun close() {
        runCatching { manager.close() }
    }
}
