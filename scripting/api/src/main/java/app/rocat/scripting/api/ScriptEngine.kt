package app.rocat.scripting.api

import app.rocat.scripting.api.model.Script

/**
 * The execution context handed to a running script. Provides the JS code access to
 * the primitives it needs: an HTTP `fetch` and (optionally) a document to manipulate.
 */
interface ScriptEnvironment {
    /**
     * Executes an HTTP request from inside JavaScript. Implementations bridge into
     * the app's OkHttp stack via [app.rocat.core.common.network] primitives.
     */
    suspend fun fetch(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): FetchResult

    /** Optional DOM-ish object a script can manipulate; `null` when unavailable. */
    val document: Any?

    /**
     * Optional bridge exposed to the script as the global `RoCatUI`. When non-null the
     * engine installs the `RoCatUI.*` functions so scripts can drive a dynamic Compose
     * UI (inputs, buttons, image/video previews, logs). `null` for plain executions.
     */
    val ui: ScriptUiBridge?
        get() = null

    /**
     * Optional headless-browser bridge exposed to the script as the global `RoCatPage`
     * (Tahap 23: dual-mode scraping). When non-null the engine installs the
     * `RoCatPage.*` functions (`open`/`type`/`click`/`waitForSelector`/`evaluate`/
     * `getHtml`/`close`) so scripts can switch from static `fetch()` + `RoCatDOM`
     * scraping to interactive WebView automation in the same flow. `null` for plain
     * executions or when the host has no browser available.
     */
    val browser: ScriptBrowserBridge?
        get() = null
}

/**
 * Result of a [ScriptEnvironment.fetch] call, serializable so it can cross from the
 * JS evaluation context back into Kotlin world.
 *
 * Network failures never throw: they are reported through [error] with [status] set
 * to 0 so a misbehaving script cannot crash the app.
 */
data class FetchResult(
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
    val statusText: String = "",
    val error: String? = null,
) {
    val ok: Boolean get() = status in 200..299
}

/**
 * Contract for any JavaScript engine used to run user scripts. Kept intentionally
 * small so multiple engines (Rhino, QuickJS, J2V8) can be swapped in behind the same
 * interface - analogous to how mihon abstracts its extensions behind `SourceApi`.
 */
interface ScriptEngine {
    val name: String

    /**
     * Executes [script]'s `main` entry point with the given [environment].
     *
     * @param args arguments forwarded to the script's `main(...)` function, e.g. the
     *   target URL supplied by the playground.
     * @return the value the script returns (typically the JSON-serialisable result of
     *   its work, e.g. an array of scraped items).
     */
    suspend fun execute(
        script: Script,
        environment: ScriptEnvironment,
        args: List<String> = emptyList(),
    ): ScriptResult

    /**
     * Evaluates [script] to register its functions, then invokes the function named
     * [functionName] with [args] (passed as JS strings).
     *
     * Used by the playground's "Test Execution" section to call a specific entry
     * point (e.g. `search(query)` or `detail(url)`) instead of the generic
     * `main(...)` entry point.
     *
     * @return the function's return value serialized to JSON, or a [ScriptResult.Failure]
     *   if the script does not compile, the function is missing, or it throws.
     */
    suspend fun invokeFunction(
        script: Script,
        environment: ScriptEnvironment,
        functionName: String,
        args: List<String> = emptyList(),
    ): ScriptResult

    /**
     * Evaluates [script] to register its functions, then invokes the function named
     * [functionName] passing the collected inputs as a single JS object argument. This
     * is the entry point used by script-driven buttons: the UI gathers every input
     * value into [inputs] (`id -> value`) and the target function receives them keyed by
     * id (e.g. `function onExtractClick(inputs) { inputs.video_url }`).
     *
     * @return the function's return value serialized to JSON (empty for a `void`
     *   function), or a [ScriptResult.Failure] if the script does not compile, the
     *   function is missing, or it throws.
     */
    suspend fun invokeNamedFunction(
        script: Script,
        environment: ScriptEnvironment,
        functionName: String,
        inputs: Map<String, String>,
    ): ScriptResult
}

/**
 * Outcome of a script execution.
 */
sealed interface ScriptResult {
    data class Success(val value: String) : ScriptResult
    data class Failure(val error: String) : ScriptResult
}