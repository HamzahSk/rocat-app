package app.rocat.scripting.rhino

import app.rocat.scripting.api.FetchResult
import app.rocat.scripting.api.ScriptEngine
import app.rocat.scripting.api.ScriptEnvironment
import app.rocat.scripting.api.ScriptResult
import app.rocat.scripting.api.ScriptUiBridge
import app.rocat.scripting.api.ScriptSettingsBridge
import app.rocat.scripting.api.model.Script
import app.rocat.scripting.api.network.scriptFetch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Function
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.json.JsonParser

/**
 * [ScriptEngine] backed by Mozilla Rhino (a pure-JVM JavaScript engine, chosen over
 * native QuickJS so the default build has no native dependencies).
 *
 * Scripts are executed in interpretive mode behind an instruction budget so infinite
 * loops are aborted, and every `fetch()` goes through the app's OkHttp stack with
 * per-call timeouts so a hanging script cannot take the app down.
 *
 * Rhino 1.7.15 does not support `async`/`await`, so `fetch()` returns a synchronous
 * Response-like object exposing `status`, `ok`, `headers`, `body`, `text()` and
 * `json()`.
 */
class RhinoScriptEngine(
    private val client: OkHttpClient,
    private val instructionBudget: Long = DEFAULT_INSTRUCTION_BUDGET,
) : ScriptEngine {

    override val name: String = "Rhino"

    private val contextFactory: ContextFactory = ScriptContextFactory(instructionBudget)

    override suspend fun execute(
        script: Script,
        environment: ScriptEnvironment,
        args: List<String>,
    ): ScriptResult = withContext(Dispatchers.IO) {
        try {
            ScriptResult.Success(evaluate(script, environment, args))
        } catch (e: CancellationException) {
            throw e
        } catch (e: EvaluatorException) {
            // Syntax/compile errors are reported back to the UI, never rethrown.
            ScriptResult.Failure("JS error: ${e.message}")
        } catch (e: StackOverflowError) {
            ScriptResult.Failure("Script caused a stack overflow (possible infinite recursion)")
        } catch (e: Exception) {
            ScriptResult.Failure(e.message ?: e.javaClass.simpleName)
        } catch (e: Throwable) {
            // Last-resort guard so a runtime error from JS never force-closes the app.
            ScriptResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    override suspend fun invokeFunction(
        script: Script,
        environment: ScriptEnvironment,
        functionName: String,
        args: List<String>,
    ): ScriptResult = withContext(Dispatchers.IO) {
        try {
            val cx = contextFactory.enterContext()
            try {
                cx.setLanguageVersion(Context.VERSION_ES6)
                val scope = createScope(cx, environment)
                cx.evaluateString(scope, script.source, script.name, 1, null)

                val fn = scope.get(functionName, scope)
                if (fn !is Function) {
                    ScriptResult.Failure("Script has no function named '$functionName'")
                } else {
                    val jsArgs = args.map { Context.javaToJS(it, scope) }.toTypedArray()
                    val result = fn.call(cx, scope, scope, jsArgs)
                    val json = NativeJSON.stringify(cx, scope, result, null, null)
                    ScriptResult.Success(json?.toString() ?: "null")
                }
            } finally {
                Context.exit()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: EvaluatorException) {
            ScriptResult.Failure("JS error: ${e.message}")
        } catch (e: StackOverflowError) {
            ScriptResult.Failure("Script caused a stack overflow (possible infinite recursion)")
        } catch (e: Exception) {
            ScriptResult.Failure(e.message ?: e.javaClass.simpleName)
        } catch (e: Throwable) {
            ScriptResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Script-driven entry point used by the playground's `RoCatUI` buttons. Evaluates
     * the script and invokes [functionName] passing the collected inputs as a single JS
     * object (keys = input ids). Undefined return values are mapped to an empty string
     * so a void handler renders as "returned nothing" instead of "null"/"undefined".
     */
    override suspend fun invokeNamedFunction(
        script: Script,
        environment: ScriptEnvironment,
        functionName: String,
        inputs: Map<String, String>,
    ): ScriptResult = withContext(Dispatchers.IO) {
        try {
            val cx = contextFactory.enterContext()
            try {
                cx.setLanguageVersion(Context.VERSION_ES6)
                val scope = createScope(cx, environment)
                cx.evaluateString(scope, script.source, script.name, 1, null)

                val fn = scope.get(functionName, scope)
                if (fn !is Function) {
                    ScriptResult.Failure("Script has no function named '$functionName'")
                } else {
                    val jsArgs = if (inputs.isEmpty()) {
                        emptyArray()
                    } else {
                        val objectArg = cx.newObject(scope)
                        inputs.forEach { (name, value) ->
                            ScriptableObject.putProperty(objectArg, name, Context.javaToJS(value, scope))
                        }
                        arrayOf<Any?>(objectArg)
                    }
                    val result = fn.call(cx, scope, scope, jsArgs)
                    val value = if (result === Undefined.instance) {
                        ""
                    } else {
                        NativeJSON.stringify(cx, scope, result, null, null)?.toString() ?: ""
                    }
                    ScriptResult.Success(value)
                }
            } finally {
                Context.exit()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: EvaluatorException) {
            ScriptResult.Failure("JS error: ${e.message}")
        } catch (e: StackOverflowError) {
            ScriptResult.Failure("Script caused a stack overflow (possible infinite recursion)")
        } catch (e: Exception) {
            ScriptResult.Failure(e.message ?: e.javaClass.simpleName)
        } catch (e: Throwable) {
            ScriptResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun evaluate(script: Script, environment: ScriptEnvironment, args: List<String>): String {
        val cx = contextFactory.enterContext()
        return try {
            cx.setLanguageVersion(Context.VERSION_ES6)
            val scope = createScope(cx, environment)

            val evaluated = cx.evaluateString(scope, script.source, script.name, 1, null)
            val mainFn = scope.get("main", scope)
            if (mainFn is Function) {
                val jsArgs = args.map { Context.javaToJS(it, scope) }.toTypedArray()
                val result = mainFn.call(cx, scope, scope, jsArgs)
                return valueToString(result)
            }
            valueToString(evaluated)
        } finally {
            Context.exit()
        }
    }
    private fun createScope(cx: Context, environment: ScriptEnvironment): Scriptable {
        val scope = cx.initStandardObjects()

        // Expose fetch(); internally it bridges to OkHttp (see runFetch).
        val fetchFn = BridgeFetch { url, method, headers, body ->
            runFetch(url, method, headers, body)
        }
        ScriptableObject.putProperty(scope, "fetch", fetchFn)

        // Expose the Jsoup-backed DOM bridge as the global `RoCatDOM` object.
        ScriptableObject.putProperty(scope, "RoCatDOM", RoCatDomBridge(cx, scope))

        // Expose the script-driven Compose UI bridge as the global `RoCatUI` object.
        val uiBridge = environment.ui
        if (uiBridge != null) {
            ScriptableObject.putProperty(scope, "RoCatUI", RoCatUiBridge(cx, scope, uiBridge))
        }

        if (environment.document != null) {
            val docWrapper = Context.javaToJS(environment.document, scope)
            ScriptableObject.putProperty(scope, "document", docWrapper)
        }

        // Tahap 23: expose the headless-browser bridge as the global `RoCatPage`.
        // Registered only when the host supplies a ScriptBrowserBridge so plain
        // executions (and unit tests without a browser) simply see `typeof RoCatPage
        // === "undefined"`.
        val browserBridge = environment.browser
        if (browserBridge != null) {
            ScriptableObject.putProperty(scope, "RoCatPage", RoCatPageBridge(cx, scope, browserBridge))
            // Tahap 25: inject the general-purpose `RoCatBrowser` polyfill (a
            // Playwright-like Browser/Page/Locator API) on top of the low-level
            // `RoCatPage` primitives. Present only when the browser bridge exists, so
            // plain executions see `typeof RoCatBrowser === "undefined"`.
            cx.evaluateString(scope, RO_CAT_BROWSER_WRAPPER_JS, "RoCatBrowser.js", 1, null)
        }

        // Tahap 22.1: auto-inject the universal core wrapper (`RoCat`) before user code.
        // It always exists — render()/safeParseJson()/fetchJson() degrade gracefully when
        // there is no RoCatUI bridge (e.g. plain executions).
        cx.evaluateString(scope, RO_CAT_CORE_WRAPPER_JS, "RoCatCore.js", 1, null)

        // Tahap 35: per-script settings bridge. When present the engine installs the
        // global `RoCat.settings` object (typed snapshot from the native bridge),
        // `RoCat.onSettingsChanged(fn)`, `RoCat.saveHistory/clearHistory(key, value)`
        // and `RoCat.openSettings()`. Absent in plain executions, mirroring
        // `typeof RoCat.settings === "undefined"` for scripts that don't use it.
        val settingsBridge = environment.settings
        if (settingsBridge != null) {
            ScriptableObject.putProperty(scope, "__rocatSettingsJson__", typedSettingsJson(settingsBridge))
            ScriptableObject.putProperty(scope, "__rocatSettingsBridge__", RoCatSettingsBridgeObject(cx, scope, settingsBridge))
            cx.evaluateString(scope, RO_CAT_SETTINGS_WRAPPER_JS, "RoCatSettings.js", 1, null)
        }
        return scope
    }

    /**
     * Serialises [ScriptSettingsBridge.snapshot] to a JS-value JSON object, coercing
     * each value to a boolean/number/string according to the bridge's declared types.
     * Scripts read `RoCat.settings.<key>` with the correct JS type (e.g. `=== true`,
     * numeric comparisons) instead of raw strings.
     */
    private fun typedSettingsJson(bridge: ScriptSettingsBridge): String {
        val types = bridge.types()
        val values = bridge.snapshot()
        val sb = StringBuilder("{")
        var first = true
        for ((key, raw) in values) {
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(escapeJsonString(key)).append("\":")
            sb.append(coerceToJsonValue(raw, types[key]))
        }
        sb.append('}')
        return sb.toString()
    }

    private fun coerceToJsonValue(raw: String, type: String?): String {
        if (type == null) return quoted(raw)
        return when (type.lowercase()) {
            "boolean" -> if (raw.trim().equals("true", ignoreCase = true) || raw.trim() == "1") "true" else "false"
            "number" -> raw.trim().toDoubleOrNull()?.let {
                if (it.isFinite()) { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } else "0"
            } ?: "0"
            else -> quoted(raw)
        }
    }

    private fun quoted(value: String): String = "\"" + escapeJsonString(value) + "\""

    private fun escapeJsonString(value: String): String = buildString {
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun runFetch(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
    ): FetchResult {
        val mime = headers["Content-Type"] ?: "application/json; charset=utf-8"
        return try {
            runBlocking { client.scriptFetch(url, method, headers, body, mime) }
        } catch (e: Exception) {
            FetchResult(status = 0, headers = emptyMap(), body = "", error = e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        /**
         * Interpreter instruction budget per script run. Generous enough for real
         * scraping loops but small enough to abort an infinite `while(true)`.
         */
        const val DEFAULT_INSTRUCTION_BUDGET: Long = 10_000_000L
    }
}

/**
 * Rhino [ContextFactory] whose contexts abort execution after [instructionBudget]
 * interpreted instructions. Used as a watchdog against runaway / infinite scripts.
 */
private class ScriptContextFactory(private val instructionBudget: Long) : ContextFactory() {
    override fun makeContext(): Context {
        return object : Context(this@ScriptContextFactory) {
            private var instructionCounter = 0L

            override fun observeInstructionCount(instructionCount: Int) {
                instructionCounter += instructionCount
                if (instructionCounter > instructionBudget) {
                    throw EvaluatorException("Script execution timed out (instruction budget exceeded)")
                }
            }
        }.apply {
            // Android's classloader cannot load standard JVM .class files produced by
            // Rhino's bytecode compiler, so force pure interpreter mode. Without this,
            // executing a script on Android throws "can't load this type of class file".
            optimizationLevel = -1
            setInstructionObserverThreshold(10_000)
        }
    }
}

private typealias FetchBridge = (String, String, Map<String, String>, String?) -> FetchResult

/**
 * A Rhino [BaseFunction] that performs an HTTP request through Kotlin and returns a
 * Response-like object: `{ status, statusText, ok, url, body, headers, text(), json() }`.
 *
 * Accepts either the positional form `fetch(url, "POST", headers, body)` or the
 * standard options form `fetch(url, { method, headers, body })`.
 */
private class BridgeFetch(
    private val bridge: FetchBridge,
) : BaseFunction() {

    override fun call(
        cx: Context,
        scope: Scriptable,
        thisObj: Scriptable,
        args: Array<out Any?>,
    ): Any? {
        val url = argStringOrNull(args, 0) ?: return null

        var method = "GET"
        var headers = emptyMap<String, String>()
        var body: String? = null

        when (val second = args.getOrNull(1)) {
            is String -> method = second
            is Scriptable -> {
                method = getStringProperty(second, "method") ?: method
                headers = getStringMap(second.get("headers", second))
                body = getStringProperty(second, "body")
            }
        }
        val third = args.getOrNull(2)
        if (third is Scriptable) {
            headers = getStringMap(third)
        }
        val fourth = args.getOrNull(3)
        if (fourth != null && fourth !== Undefined.instance) body = Context.toString(fourth)

        val result = bridge(url, method, headers, body)

        val obj = cx.newObject(scope)
        ScriptableObject.putProperty(obj, "status", result.status)
        ScriptableObject.putProperty(obj, "statusText", result.statusText)
        ScriptableObject.putProperty(obj, "ok", result.ok)
        ScriptableObject.putProperty(obj, "url", url)
        ScriptableObject.putProperty(obj, "body", result.body)
        ScriptableObject.putProperty(obj, "error", result.error)

        val headersJs = cx.newObject(scope)
        result.headers.forEach { (key, value) -> ScriptableObject.putProperty(headersJs, key, value) }
        ScriptableObject.putProperty(obj, "headers", headersJs)

        ScriptableObject.putProperty(obj, "text", SyncValueFn { result.body })
        ScriptableObject.putProperty(obj, "json", SyncValueFn {
            if (result.body.isBlank()) {
                throw throwJsError(cx, scope, "Empty response body cannot be parsed as JSON")
            }
            try {
                JsonParser(cx, scope).parseValue(result.body)
            } catch (e: Exception) {
                // Rhino's JsonParser throws a *checked* ParseException that the engine
                // cannot wrap into a JS-catchable error, so convert it to a real JS Error.
                throw throwJsError(cx, scope, "Invalid JSON response: ${e.message ?: e.javaClass.simpleName}")
            }
        })
        return obj
    }

    private fun getStringProperty(sb: Scriptable, name: String): String? {
        val value = sb.get(name, sb) ?: return null
        if (value is Undefined) return null
        return Context.toString(value)
    }

    private fun getStringMap(value: Any?): Map<String, String> {
        if (value !is Scriptable) return emptyMap()
        val map = LinkedHashMap<String, String>()
        for (id in value.ids) {
            val key = id.toString()
            val v = value.get(key, value)
            if (v != null && v !== Undefined.instance) map[key] = Context.toString(v)
        }
        return map
    }
}

/** A Rhino function that lazily produces (and returns) a single value. */
private class SyncValueFn(private val supplier: () -> Any?) : BaseFunction() {
    override fun call(
        cx: Context,
        scope: Scriptable,
        thisObj: Scriptable,
        args: Array<out Any?>,
    ): Any? = supplier()
}

/** Throws a plain, JS-catchable `Error` (unlike raw `EvaluatorException`s / checked
 *  Rhino parser exceptions which would escape a script's `try { } catch { }`). */
private fun throwJsError(cx: Context, scope: Scriptable, message: String): Nothing {
    val error = cx.newObject(scope, "Error", arrayOf<Any?>(message))
    throw JavaScriptException(error, null, 0)
}

/**
 * The `RoCatDOM` global: the Jsoup-backed DOM bridge exposed to user scripts.
 * Every function takes the raw HTML string plus a CSS selector and returns plain
 * JS values (strings, booleans, arrays of element objects) built via [cx] and
 * [scope]. Element wrappers expose a Cheerio-like surface (see [elementToJs]).
 */
private class RoCatDomBridge(
    private val cx: Context,
    private val scope: Scriptable,
) : ScriptableObject() {

    init {
        // Pre-create the function members so scripts can call RoCatDOM.selectText(...) etc.
        put("parse", this, Fn { args -> elementToJs(cx, scope, JsoupBridge.parse(argString(args, 0))) })
        put("select", this, Fn { args -> elementsToJs(cx, scope, JsoupBridge.select(argString(args, 0), argString(args, 1))) })
        put("selectText", this, Fn { args -> JsoupBridge.selectText(argString(args, 0), argString(args, 1)) })
        put("selectAttr", this, Fn { args -> JsoupBridge.selectAttr(argString(args, 0), argString(args, 1), argString(args, 2)) })
        put("selectHtml", this, Fn { args -> JsoupBridge.selectHtml(argString(args, 0), argString(args, 1)) })
        put("has", this, Fn { args -> JsoupBridge.has(argString(args, 0), argString(args, 1)) })
    }

    override fun getClassName(): String = "RoCatDomBridge"
}

/**
 * The `RoCatUI` global: the script-driven Compose UI bridge exposed to user scripts.
 * Every function forwards its (string) arguments to the [ScriptUiBridge] implementation
 * supplied by the host app, which is responsible for hopping back to the main thread
 * before updating the rendered UI. Failures inside the bridge never crash the script.
 */
private class RoCatUiBridge(
    private val cx: Context,
    private val scope: Scriptable,
    private val ui: ScriptUiBridge,
) : ScriptableObject() {

    init {
        put("addInput", this, Fn { args -> runSafe { ui.addInput(argString(args, 0), argString(args, 1)) } })
        put("addButton", this, Fn { args -> runSafe { ui.addButton(argString(args, 0), argString(args, 1)) } })
        put("thumbnailPreview", this, Fn { args -> runSafe { ui.thumbnailPreview(argString(args, 0)) } })
        put("videoPreview", this, Fn { args -> runSafe { ui.videoPreview(argString(args, 0)) } })
        put("addImage", this, Fn { args ->
            runSafe {
                ui.addImage(
                    url = argString(args, 0),
                    title = argString(args, 1),
                    allowDownload = argBoolean(args, 2, true),
                    headers = argHeaders(args, 3),
                )
            }
        })
        put("addVideo", this, Fn { args ->
            runSafe {
                ui.addVideo(
                    url = argString(args, 0),
                    title = argString(args, 1),
                    isStreamHls = argBoolean(args, 2),
                    allowDownload = argBoolean(args, 3, true),
                    headers = argHeaders(args, 4),
                )
            }
        })
        put("clear", this, Fn { runSafe { ui.clear() } })
        put("addGrid", this, Fn { args ->
            runSafe {
                ui.addGrid(argInt(args, 0), argString(args, 1), argString(args, 2), argHeaders(args, 3))
            }
        })
        put("log", this, Fn { args -> runSafe { ui.log(argString(args, 0)) } })
        put("save", this, Fn { args ->
            runSafe {
                ui.saveFile(
                    fileName = argString(args, 0),
                    content = argString(args, 1),
                    mimeType = argString(args, 2, "text/plain"),
                )
            }
        })
        // Tahap 20.1: native Base64 decode. Returns a plain JS string (empty on failure),
        // so scripts can decode iframe blobs without shipping a JS decoder.
        put("decodeBase64", this, Fn { args ->
            runSafeValue("") { ui.decodeBase64(argString(args, 0)) }
        })

        // --- Tahap 22.2: expanded UI template cards (all fault-tolerant) ---
        put("addJsonLog", this, Fn { args ->
            runSafe {
                ui.addJsonLog(
                    dataJson = argJson(args, 0),
                    title = argString(args, 1),
                    allowCopy = argBoolean(args, 2, true),
                )
            }
        })
        put("addHtmlPreview", this, Fn { args ->
            runSafe { ui.addHtmlPreview(argString(args, 0), argString(args, 1)) }
        })
        put("addAudio", this, Fn { args ->
            runSafe {
                ui.addAudio(
                    url = argString(args, 0),
                    title = argString(args, 1),
                    allowDownload = argBoolean(args, 2, true),
                    headers = argHeaders(args, 3),
                )
            }
        })
        put("addAlert", this, Fn { args ->
            runSafe { ui.addAlert(argString(args, 0), argString(args, 1, "info")) }
        })
        put("addBadgeGroup", this, Fn { args ->
            runSafe { ui.addBadgeGroup(argJson(args, 0)) }
        })

        // --- Tahap 35: flexible layouts & rich input controls ---
        put("addText", this, Fn { args ->
            runSafe { ui.addText(argString(args, 0), argString(args, 1, "body")) }
        })
        put("addDivider", this, Fn { args ->
            runSafe { ui.addDivider(argInt(args, 0, 1), argString(args, 1, "#cccccc")) }
        })
        put("addCheckbox", this, Fn { args ->
            runSafe { ui.addCheckbox(argString(args, 0), argString(args, 1), argBoolean(args, 2)) }
        })
        put("addToggle", this, Fn { args ->
            runSafe { ui.addToggle(argString(args, 0), argString(args, 1), argBoolean(args, 2)) }
        })
        put("addDropdown", this, Fn { args ->
            runSafe {
                ui.addDropdown(
                    argString(args, 0),
                    argStringArray(args, 1),
                    argString(args, 2),
                    argString(args, 3, ""),
                )
            }
        })
        put("addNumber", this, Fn { args ->
            runSafe {
                ui.addNumber(
                    argString(args, 0),
                    argDouble(args, 1),
                    argDouble(args, 2),
                    argDouble(args, 3),
                    argDouble(args, 4),
                    argString(args, 5, ""),
                )
            }
        })
        put("addColorPicker", this, Fn { args ->
            runSafe { ui.addColorPicker(argString(args, 0), argString(args, 1, "#000000"), argString(args, 2, "")) }
        })
        put("addTextArea", this, Fn { args ->
            runSafe { ui.addTextArea(argString(args, 0), argString(args, 1), argInt(args, 2, 3), argString(args, 3)) }
        })
        put("addAutocomplete", this, Fn { args ->
            runSafe {
                ui.addAutocomplete(
                    argString(args, 0),
                    argString(args, 1),
                    argStringArray(args, 2),
                    argString(args, 3),
                    argInt(args, 4, 20),
                    argBoolean(args, 5, true),
                    argBoolean(args, 6, true),
                    argString(args, 7),
                )
            }
        })
        put("addGroup", this, Fn { args ->
            runSafe { ui.addGroup(argString(args, 0), argBoolean(args, 1), argJson(args, 2)) }
        })
        put("addLayout", this, Fn { args ->
            runSafe {
                ui.addLayoutOptions(
                    argString(args, 0, "column"),
                    argInt(args, 1, 2),
                    argInt(args, 2, 0),
                    argBoolean(args, 3),
                    argJson(args, 4),
                    argInt(args, 5, 0).takeIf { it > 0 },
                    argInt(args, 6, 16),
                    argInt(args, 7, 8),
                    argString(args, 8, "start"),
                )
            }
        })
    }

    override fun getClassName(): String = "RoCatUiBridge"

    /** Reads the [index]-th JS argument as a JSON string, normalising objects/arrays
     *  (via [NativeJSON.stringify]) so the Kotlin bridge always receives a string. */
    private fun argJson(args: Array<out Any?>, index: Int, default: String = ""): String {
        val value = args.getOrNull(index) ?: return default
        if (value === Undefined.instance) return default
        return when (value) {
            is Scriptable -> NativeJSON.stringify(cx, scope, value, null, null)?.toString() ?: default
            else -> Context.toString(value)
        }
    }

    /**
     * Reads the [index]-th JS argument as a `Map<String, String>` of HTTP headers
     * (Tahap 24.1). Accepts a plain JS object (`{ "Referer": "https://…" }`), a
     * JSON string, or nothing (`null`/`undefined` → empty map). Non-string values are
     * coerced with `Context.toString`. Failures never throw.
     */
    private fun argHeaders(args: Array<out Any?>, index: Int): Map<String, String> {
        val json = argJson(args, index)
        if (json.isBlank()) return emptyMap()
        return try {
            val element = Json.parseToJsonElement(json)
            val obj = element as? JsonObject ?: return emptyMap()
            obj.mapNotNull { (name, value) ->
                val content = (value as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                name to content
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun runSafe(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // A UI bridge failure must never crash the script evaluation.
        }
    }

    /** Like [runSafe] but returns [default] when the bridge throws. */
    private fun <T> runSafeValue(default: T, block: () -> T): T =
        try {
            block()
        } catch (_: Throwable) {
            default
        }
}

/**
 * The auto-injected **settings bridge** (Tahap 35). `[RhinoScriptEngine]` evaluates
 * this into the scope *after* the core wrapper whenever the host supplies a
 * [ScriptSettingsBridge], exposing the ergonomic `RoCat.settings` object:
 *
 * - `RoCat.settings` — a typed snapshot of the script's persisted settings; direct
 *   property access (`RoCat.settings.username`), `get(key)`, `getAll()`.
 * - `RoCat.settings.set(key, value)` — persists a value and fires registered
 *   `onSettingsChanged` callbacks within the current execution.
 * - `RoCat.settings.setTemp(key, value)` / `getTemp(key)` — per-session storage.
 * - `RoCat.onSettingsChanged(fn)` — registers a callback for in-scope setting changes.
 * - `RoCat.saveHistory(key, value)` / `RoCat.clearHistory(key)` — input history.
 * - `RoCat.openSettings()` — asks the host to open this script's settings page.
 *
 * Written in ES5 (Rhino 1.7.15-safe). Every bridge call is guarded so a missing
 * bridge (plain executions) degrades gracefully.
 */
const val RO_CAT_SETTINGS_WRAPPER_JS: String = """
(function () {
    var base = {};
    try { base = JSON.parse(__rocatSettingsJson__ || "{}") || {}; } catch (e) { base = {}; }
    var callbacks = [];

    function bridge() {
        if (typeof __rocatSettingsBridge__ === "undefined") return null;
        return __rocatSettingsBridge__;
    }

    function fireChanged() {
        for (var i = 0; i < callbacks.length; i++) {
            try { callbacks[i](RoCat.settings); } catch (e) { /* listener errors are isolated */ }
        }
    }

    base.get = function (k) { return base[k]; };
    base.getAll = function () { return base; };
    base.set = function (k, v) {
        if (k === null || k === undefined) return;
        if (v !== undefined) base[k] = v;
        var b = bridge();
        if (b) { try { b.setValue(String(k), String(v)); } catch (e) {} }
        fireChanged();
    };
    base.setTemp = function (k, v) {
        var b = bridge();
        if (b) { try { b.setTemp(String(k), String(v)); } catch (e) {} }
    };
    base.getTemp = function (k) {
        var b = bridge();
        if (!b) return null;
        try { return b.getTemp(String(k)); } catch (e) { return null; }
    };

    RoCat.settings = base;
    RoCat.storage = {
        set: function (key, value) { var b = bridge(); if (b) { try { b.storageSet(String(key), String(value)); } catch (e) {} } },
        get: function (key) { var b = bridge(); if (!b) return null; try { return b.storageGet(String(key)); } catch (e) { return null; } },
        remove: function (key) { var b = bridge(); if (b) { try { b.storageRemove(String(key)); } catch (e) {} } },
        clear: function () { var b = bridge(); if (b) { try { b.storageClear(); } catch (e) {} } }
    };
    RoCat.onSettingsChanged = function (fn) {
        if (typeof fn === "function") callbacks.push(fn);
    };
    RoCat.saveHistory = function (key, value) {
        var b = bridge();
        if (b) { try { b.saveHistory(String(key), String(value)); } catch (e) {} }
    };
    RoCat.clearHistory = function (key) {
        var b = bridge();
        if (b) { try { b.clearHistory(String(key)); } catch (e) {} }
    };
    RoCat.openSettings = function () {
        var b = bridge();
        if (b) { try { b.openSettings(); } catch (e) {} }
    };
})();
"""

/**
 * The native `__rocatSettingsBridge__` object: a thin pass-through to the host's
 * [ScriptSettingsBridge] that scripts reach through the `RoCat.settings` wrapper.
 * Failures never throw back into JS (each call is wrapped in runSafe).
 */
private class RoCatSettingsBridgeObject(
    private val cx: Context,
    private val scope: Scriptable,
    private val bridge: ScriptSettingsBridge,
) : ScriptableObject() {

    init {
        put("setValue", this, Fn { args -> runSafe { bridge.setValue(argString(args, 0), argString(args, 1)) } })
        put("setTemp", this, Fn { args -> runSafe { bridge.setTemp(argString(args, 0), argString(args, 1)) } })
        put("getTemp", this, Fn { args -> runSafeValue(null) { bridge.getTemp(argString(args, 0)) } })
        put("saveHistory", this, Fn { args -> runSafe { bridge.saveHistory(argString(args, 0), argString(args, 1)) } })
        put("clearHistory", this, Fn { args -> runSafe { bridge.clearHistory(argString(args, 0)) } })
        put("storageSet", this, Fn { args -> runSafe { bridge.storageSet(argString(args, 0), argString(args, 1)) } })
        put("storageGet", this, Fn { args -> runSafeValue(null) { bridge.storageGet(argString(args, 0)) } })
        put("storageRemove", this, Fn { args -> runSafe { bridge.storageRemove(argString(args, 0)) } })
        put("storageClear", this, Fn { runSafe { bridge.storageClear() } })
        put("openSettings", this, Fn { runSafe { bridge.openSettings() } })
    }

    override fun getClassName(): String = "RoCatSettingsBridge"

    private fun runSafe(block: () -> Unit) {
        try {
            block()
        } catch (_: Throwable) {
            // A settings bridge failure must never crash the script evaluation.
        }
    }

    private fun <T> runSafeValue(default: T, block: () -> T): T =
        try {
            block()
        } catch (_: Throwable) {
            default
        }
}

/** Converts a [JsoupElement] into a plain JS object with a Cheerio-like API. */
private fun elementToJs(cx: Context, scope: Scriptable, el: JsoupElement): Scriptable {
    val obj = cx.newObject(scope)
    ScriptableObject.putProperty(obj, "text", el.text)
    ScriptableObject.putProperty(obj, "html", el.html)
    ScriptableObject.putProperty(obj, "innerHtml", el.innerHtml)

    val attrs = cx.newObject(scope)
    el.attrNames.forEach { name -> ScriptableObject.putProperty(attrs, name, el.attr(name)) }
    ScriptableObject.putProperty(obj, "attrs", attrs)

    ScriptableObject.putProperty(obj, "attr", Fn { args -> el.attr(argString(args, 0)) })
    ScriptableObject.putProperty(obj, "has", Fn { args -> el.has(argString(args, 0)) })
    ScriptableObject.putProperty(obj, "contains", Fn { args -> el.contains(argString(args, 0)) })
    ScriptableObject.putProperty(obj, "find", Fn { args -> elementsToJs(cx, scope, el.find(argString(args, 0))) })
    ScriptableObject.putProperty(obj, "textOf", Fn { args -> el.textOf(argString(args, 0)) })
    ScriptableObject.putProperty(obj, "attrOf", Fn { args -> el.attrOf(argString(args, 0), argString(args, 1)) })
    ScriptableObject.putProperty(obj, "textsOf", Fn { args -> stringsToJs(cx, scope, el.textsOf(argString(args, 0))) })
    ScriptableObject.putProperty(obj, "nextElement", Fn { args ->
        el.nextElement(argString(args, 0))?.let { elementToJs(cx, scope, it) }
    })
    return obj
}

/** Converts a list of [JsoupElement] into a JS array of element objects. */
private fun elementsToJs(cx: Context, scope: Scriptable, elements: List<JsoupElement>): Scriptable {
    val array = cx.newArray(scope, elements.size)
    elements.forEachIndexed { index, el -> array.put(index, array, elementToJs(cx, scope, el)) }
    return array
}

/** Converts a list of strings into a JS array of strings. */
private fun stringsToJs(cx: Context, scope: Scriptable, strings: List<String>): Scriptable {
    val array = cx.newArray(scope, strings.size)
    strings.forEachIndexed { index, value -> array.put(index, array, value) }
    return array
}

/** Reads the [index]-th JS argument as a String, or [default] when absent/undefined. */
private fun argString(args: Array<out Any?>, index: Int, default: String = ""): String {
    val value = args.getOrNull(index) ?: return default
    if (value === Undefined.instance) return default
    return Context.toString(value)
}

/** Reads the [index]-th JS argument as a String, or null when absent/undefined. */
private fun argStringOrNull(args: Array<out Any?>, index: Int): String? {
    val value = args.getOrNull(index) ?: return null
    if (value === Undefined.instance) return null
    return Context.toString(value)
}

/** Reads the [index]-th JS argument as an Int, or [default] when absent/undefined. */
private fun argInt(args: Array<out Any?>, index: Int, default: Int = 0): Int {
    val value = args.getOrNull(index) ?: return default
    if (value === Undefined.instance) return default
    return when (value) {
        is Number -> value.toInt()
        is CharSequence -> value.toString().trim().toIntOrNull() ?: default
        else -> runCatching { Context.toNumber(value).toInt() }.getOrDefault(default)
    }
}

/** Reads the [index]-th JS argument as a Double, or null when absent/undefined. */
private fun argDouble(args: Array<out Any?>, index: Int): Double? {
    val value = args.getOrNull(index) ?: return null
    if (value === Undefined.instance) return null
    return when (value) {
        is Number -> value.toDouble()
        is CharSequence -> value.toString().trim().toDoubleOrNull()
        else -> runCatching { Context.toNumber(value) }.getOrNull()
    }
}

/** Reads the [index]-th JS argument as a List<String> (JS array of primitives). */
private fun argStringArray(args: Array<out Any?>, index: Int): List<String> {
    val value = args.getOrNull(index) ?: return emptyList()
    if (value === Undefined.instance) return emptyList()
    if (value is Scriptable) {
        val result = mutableListOf<String>()
        for (id in value.ids) {
            // JS array ids are integer indices (Int); plain object ids are Strings.
            val item = when (id) {
                is Int -> value.get(id, value)
                else -> value.get(id.toString(), value)
            } ?: continue
            if (item === Undefined.instance) continue
            result += Context.toString(item).trim()
        }
        return result.filter { it.isNotEmpty() }
    }
    return Context.toString(value).split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Reads the [index]-th JS argument as a Boolean, or [default] when absent/undefined. */
private fun argBoolean(args: Array<out Any?>, index: Int, default: Boolean = false): Boolean {
    val value = args.getOrNull(index) ?: return default
    if (value === Undefined.instance) return default
    return when (value) {
        is Boolean -> value
        is CharSequence -> value.toString().trim().equals("true", ignoreCase = true)
        else -> runCatching { Context.toBoolean(value) }.getOrDefault(default)
    }
}

/**
 * Converts a Rhino-evaluated value to its display string. The interpreter represents
 * whole-number arithmetic as [Double] (e.g. `1 + 41` -> 42.0), so integral doubles
 * are normalized to their plain integer form to avoid a spurious `.0` in results.
 */
private fun valueToString(value: Any?): String = when (value) {
    null -> ""
    is Double -> if (value.isFinite() && value == Math.floor(value)) value.toLong().toString() else value.toString()
    is Float -> if (value.isFinite() && value == Math.floor(value.toDouble())) value.toLong().toString() else value.toString()
    else -> value.toString()
}

/** A Rhino function that receives its raw JS arguments for a Kotlin lambda. */
private class Fn(private val fn: (Array<out Any?>) -> Any?) : BaseFunction() {
    override fun call(
        cx: Context,
        scope: Scriptable,
        thisObj: Scriptable,
        args: Array<out Any?>,
    ): Any? = fn(args)
}
