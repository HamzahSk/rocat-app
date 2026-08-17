package app.rocat.ui.canvas

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.rocat.core.common.injekt.Injekt
import app.rocat.core.viewmodel.StateViewModel
import app.rocat.data.script.ScriptManager
import app.rocat.domain.script.ExecuteScript
import app.rocat.domain.script.GetScripts
import app.rocat.scripting.api.ScriptResult
import app.rocat.scripting.api.ScriptBrowserBridge
import app.rocat.scripting.api.ScriptUiBridge
import app.rocat.scripting.api.baseUrlFromMatches
import app.rocat.scripting.api.effectiveMediaHeaders
import app.rocat.scripting.api.model.Script
import app.rocat.storage.StorageManager
import app.rocat.ui.components.ScriptUIComponent
import app.rocat.ui.components.parseBadgeGroup
import app.rocat.ui.components.parseGrid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.update

/**
 * The engine behind the [ScriptCanvasScreen]: a per-script, mihon-like "extension tab".
 *
 * Unlike the removed shared Playground picker, this screen owns exactly one script. When it loads
 * (and again on every script source change) it clears the canvas and invokes the script's
 * `onLaunch()` function, letting the script draw its own initial UI through the global
 * `RoCatUI` object. From then on every tap/branch is script-driven:
 *
 *  - `RoCatUI.addInput`/`addButton` rebuild search forms; pressing a button forwards the
 *    collected inputs to the named JS function.
 *  - `RoCatUI.addGrid` renders a (mihon-style) media grid whose tiles call back into JS
 *    (JSON payload as a string) — the script then "navigates" by calling `RoCatUI.clear()`
 *    and redrawing, e.g. a Search list -> Manga Detail flow.
 *
 * Bridge callbacks are marshalled to the main thread and guarded by a session token so a
 * stale render can never wipe a newer one. Returns of `null`/`undefined` handlers are
 * flattened to empty so the console only shows real output/errors.
 */
class ScriptCanvasViewModel(
    private val scriptId: String,
    private val getScripts: GetScripts = Injekt.get(),
    private val scriptManager: ScriptManager = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
    private val browserBridge: ScriptBrowserBridge = Injekt.get(),
) : StateViewModel<ScriptCanvasViewModel.State>(State()) {

    data class State(
        val script: Script? = null,
        val loaded: Boolean = false,
        val executing: Boolean = false,
        val output: String = "",
    )

    /** The ordered, script-driven list of components rendered by the canvas. */
    val uiComponents: SnapshotStateList<ScriptUIComponent> = mutableStateListOf()

    /** Monotonic session id. Incremented whenever a fresh render starts (a new `onLaunch()`
     *  draw or a script source change) so queued bridge updates from an older render are
     *  discarded on the main thread. */
    @Volatile
    private var uiSession: Long = 0

    /** Per-script, per-button loading state (Tahap 31.1). Previously a single
     *  `state.executing` boolean drove every Button in the canvas — clicking button A
     *  animated the spinner on button B. The map is keyed by [ScriptUIComponent.Button.id]
     *  (assigned when the script publishes the button via `RoCatUI.addButton`) so each
     *  button only animates while its own handler is in flight. The map is also cleared
     *  on every fresh `onLaunch()` render via [renderOnLaunch]. */
    private val buttonLoading: MutableMap<String, Long> = mutableMapOf()

    /** Monotonic counter handed out as a unique `id` for every script-published button. */
    @Volatile
    private var buttonCounter: Long = 0
    /** The per-script scrape folder inside `[MainDirectory]/Scrapes/`, created lazily. */
    private var scrapeFolder: androidx.documentfile.provider.DocumentFile? = null
    /** Last source string that triggered a render; used to auto-redraw on edit. */
    private var lastSource: String? = null

    /**
     * Creates (or reuses) the scrape output folder for this script. Tahap 15.2: every
     * scrape writes into `[MainDirectory]/Scrapes/<scriptId>/` so results are isolated.
     */
    fun scrapeFolder(): androidx.documentfile.provider.DocumentFile? {
        val current = scrapeFolder ?: state.value.script?.let { storageManager.createScrapeFolder(it.id) }
        scrapeFolder = current
        return current
    }

    /**
     * Resolves the effective HTTP headers for a media URL (Tahap 24.1): headers supplied
     * by the script win; a missing `Referer` is auto-filled from the script metadata
     * `@match`/`@include` base URL (or the media URL's own origin as a last resort).
     */
    private fun resolveHeaders(headers: Map<String, String>, url: String): Map<String, String> =
        effectiveMediaHeaders(url, headers, scriptBaseUrl())

    /** Best-effort origin of the first `@match`/`@include` pattern of the running script. */
    private fun scriptBaseUrl(): String? =
        state.value.script?.matches?.let { baseUrlFromMatches(it) }



    private val uiBridge = object : ScriptUiBridge {
        override fun addInput(id: String, hint: String) = postUi(uiSession) { addOrReplaceInput(id, hint) }
        override fun addButton(label: String, functionName: String) = postUi(uiSession) {
            // Tahap 31.1: hand out a stable id so the canvas can isolate the loading
            // spinner per button. The id is reused when the script re-declares the same
            // (label, functionName) — this keeps the button visually stable across
            // re-renders (e.g. a "Search" button drawn on every onLaunch()).
            val id = buttonIdFor(label, functionName)
            uiComponents.add(ScriptUIComponent.Button(id, label, functionName))
        }
        override fun thumbnailPreview(url: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Image(url, "", true, resolveHeaders(emptyMap(), url)))
        }
        override fun videoPreview(url: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Video(url, "", false, true, resolveHeaders(emptyMap(), url)))
        }
        override fun addImage(url: String, title: String, allowDownload: Boolean, headers: Map<String, String>) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Image(url, title, allowDownload, resolveHeaders(headers, url)))
        }
        override fun addVideo(url: String, title: String, isStreamHls: Boolean, allowDownload: Boolean, headers: Map<String, String>) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Video(url, title, isStreamHls, allowDownload, resolveHeaders(headers, url)))
        }
        // Tahap 22.2: expanded UI template cards (JSON viewer / HTML preview / audio /
        // alert / badge group). All tolerant: bad payloads are simply not rendered.
        override fun addJsonLog(dataJson: String, title: String, allowCopy: Boolean) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.JsonLog(dataJson, title, allowCopy))
        }
        override fun addHtmlPreview(htmlContent: String, title: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.HtmlPreview(htmlContent, title))
        }
        override fun addAudio(url: String, title: String, allowDownload: Boolean, headers: Map<String, String>) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Audio(url, title, allowDownload, resolveHeaders(headers, url)))
        }
        override fun addAlert(message: String, type: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Alert(message, type))
        }
        override fun addBadgeGroup(badgesJson: String) = postUi(uiSession) {
            parseBadgeGroup(badgesJson)?.let { uiComponents.add(it) }
        }
        override fun clear() = postUi(uiSession) { uiComponents.clear() }
        override fun addGrid(columns: Int, itemsJsonString: String, onClickFunction: String, headers: Map<String, String>) = postUi(uiSession) {
            parseGrid(columns, itemsJsonString, onClickFunction, headers)?.let { grid ->
                uiComponents.add(
                    grid.copy(
                        items = grid.items.map { item ->
                            item.copy(headers = resolveHeaders(headers, item.imageUrl))
                        },
                    ),
                )
            }
        }
        override fun log(text: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.LogText(text))
        }
        override fun saveFile(fileName: String, content: String, mimeType: String): String {
            // Tahap 16.1: synchronously stream the bytes into the per-script scrape folder
            // via StorageManager, so files really land on device storage. Safe to block the
            // Rhino IO thread here; the actual write happens on the same dispatcher.
            val folder = scrapeFolder()
            return runBlocking {
                runCatching {
                    storageManager.saveFileToScrapeFolder(
                        folder = folder,
                        fileName = fileName,
                        mimeType = mimeType,
                        content = content.toByteArray(Charsets.UTF_8),
                    )
                }.getOrNull()?.toString().orEmpty()
            }
        }

        // Tahap 20.1: native Base64 → UTF-8. Scripts call RoCatUI.decodeBase64(str); this
        // uses the platform decoder (android.util.Base64) instead of the JS fallback. An
        // empty string is returned on padding/format errors so the script skips the mirror.
        override fun decodeBase64(input: String): String {
            val cleaned = input.trim().filterNot { it.isWhitespace() }
            if (cleaned.isEmpty()) return ""
            return try {
                val padded = if (cleaned.length % 4 != 0) {
                    cleaned + "=".repeat(4 - (cleaned.length % 4))
                } else {
                    cleaned
                }
                String(android.util.Base64.decode(padded, android.util.Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }
        }
    }

    /** The engine/environment pair used for every script-driven invocation. Built fresh
     *  per call: [ScriptManager] rebuilds the underlying engine when the user changes the
     *  network settings (custom User-Agent / DoH DNS, Tahap 20), so the scraper always
     *  uses the latest configuration. The [browserBridge] is attached so scripts can use
     *  the `RoCatPage` headless-WebView global (Tahap 23, dual-mode scraping). */
    private val uiExecuteScript: ExecuteScript
        get() = ExecuteScript(
            engine = scriptManager.engine(),
            environment = scriptManager.createEnvironment(uiBridge, browserBridge),
        )

    override fun onCleared() {
        super.onCleared()
        // Release the headless WebView so a finished canvas never leaks a live renderer.
        browserBridge.close()
    }

    init {
        viewModelScope.launch {
            getScripts.subscribe().collect { list ->
                val script = list.firstOrNull { it.id == scriptId }
                mutableState.update { it.copy(script = script, loaded = true) }
                if (script != null && script.source != lastSource) {
                    lastSource = script.source
                    renderOnLaunch(script)
                }
            }
        }
    }

    /**
     * Starts a fresh canvas render: clears the previous components and invokes the
     * script's `onLaunch()` function which repopulates the UI via `RoCatUI.*`.
     */
    private fun renderOnLaunch(script: Script) {
        uiSession++
        val session = uiSession
        postUi(session) { uiComponents.clear() }
        // Tahap 31.1: drop every per-button loading flag so the new render starts with a
        // clean slate — the old buttons (if any were still spinning) no longer exist.
        synchronized(buttonLoading) { buttonLoading.clear() }
        synchronized(buttonIds) { buttonIds.clear() }

        viewModelScope.launch {
            val result = try {
                uiExecuteScript.invoke(script, ON_LAUNCH_FUNCTION)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ScriptResult.Failure(e.message ?: e.javaClass.simpleName)
            }
            if (session != uiSession) return@launch
            if (result is ScriptResult.Failure) {
                val error = result.error
                // A missing onLaunch() is fine: such scripts are not canvas-driven.
                if (error?.contains("no function") != true) {
                    mutableState.update {
                        it.copy(output = "onLaunch error: $error")
                    }
                }
            }
        }
    }

    /** Updates a single input's value as the user types, keeping the item keyed by id. */
    fun updateInputValue(id: String, value: String) {
        val index = uiComponents.indexOfFirst { (it as? ScriptUIComponent.Input)?.id == id }
        if (index < 0) return
        val input = uiComponents[index] as ScriptUIComponent.Input
        if (input.value == value) return
        uiComponents[index] = input.copy(value = value)
    }

    /**
     * Pressing a `RoCatUI.Button`: gathers every non-blank input into a `Map<id, value>`
     * and invokes the named JS function with that object as a single argument. Tahap
     * 31.1: the spinner is bound to the *specific* [buttonId] the user tapped — other
     * buttons in the canvas stay clickable (with their normal labels) until the script
     * finishes its handler.
     */
    fun onScriptButton(buttonId: String, functionName: String) {
        val script = state.value.script ?: return
        val inputs = uiComponents
            .filterIsInstance<ScriptUIComponent.Input>()
            .filter { it.value.isNotBlank() }
            .associate { it.id to it.value.trim() }
        execute(script, functionName, buttonId, inputs = inputs, args = emptyList())
    }

    /**
     * Tapping a tile of a `RoCatUI` grid: forwards the tile's raw JSON payload as a
     * string argument (`openDetail(itemJson)`) so the script can render its detail page.
     * Tahap 31.1: grid taps still mark the whole canvas as executing (a single click is
     * driving the canvas redraw), but they no longer ripple to script-published buttons
     * because those read [isButtonLoading].
     */
    fun onGridItemClick(functionName: String, payload: String) {
        val script = state.value.script ?: return
        execute(script, functionName, null, inputs = emptyMap(), args = listOf(payload))
    }

    /** Returns `true` while the script handler for the given button is still in flight. */
    fun isButtonLoading(buttonId: String): Boolean = buttonLoading.containsKey(buttonId)
    /** Tahap 31.1: stable, reusable id for a `(label, functionName)` button pair so the
     *  same button published by `RoCatUI.addButton(...)` keeps its identity across
     *  re-renders (and therefore its own loading state). Each (functionName, label)
     *  pair maps to exactly one id for the lifetime of the canvas; multiple buttons
     *  with identical names get a counter suffix so they stay independent. */
    private val buttonIds: MutableMap<String, String> = mutableMapOf()
    private fun buttonIdFor(label: String, functionName: String): String =
        synchronized(buttonIds) {
            val key = "$functionName::$label"
            buttonIds.getOrPut(key) { "${++buttonCounter}::$key" }
        }


    /** Re-runs the script's `onLaunch()` to redraw the canvas from scratch. */
    fun rebuildCanvas() {
        state.value.script?.let { renderOnLaunch(it) }
    }
    /**
     * Tahap 31.1: runs a script function and tracks the per-button loading state when
     * [buttonId] is non-null. The button that triggered the call animates its own spinner
     * (via [isButtonLoading]) while every other button on the canvas stays enabled and
     * shows its label unchanged. Grid taps still toggle `state.executing` because a
     * single click is replacing the whole canvas.
     */
    private fun execute(
        script: Script,
        functionName: String,
        buttonId: String?,
        inputs: Map<String, String>,
        args: List<String>,
    ) {
        // Ensure the per-script scrape folder exists before the scrape writes anything.
        scrapeFolder()
        if (buttonId != null) {
            synchronized(buttonLoading) { buttonLoading[buttonId] = uiSession }
        } else {
            mutableState.update { it.copy(executing = true, output = "") }
        }
        viewModelScope.launch {
            val result = try {
                if (args.isNotEmpty()) {
                    uiExecuteScript.invoke(script, functionName, args = args)
                } else {
                    uiExecuteScript.invoke(script, functionName, inputs = inputs)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ScriptResult.Failure(e.message ?: e.javaClass.simpleName)
            }

            val message = when (result) {
                is ScriptResult.Success -> normalizeOutput(result.value)
                is ScriptResult.Failure -> "Error: ${result.error}"
            }
            if (buttonId != null) {
                synchronized(buttonLoading) { buttonLoading.remove(buttonId) }
            } else {
                mutableState.update { it.copy(executing = false, output = message) }
            }
        }
    }
    /** Remembers the id and refreshes its hint when the script re-declares the same id. */
    private fun addOrReplaceInput(id: String, hint: String) {
        val index = uiComponents.indexOfFirst {
            it is ScriptUIComponent.Input && (it as ScriptUIComponent.Input).id == id
        }
        if (index >= 0) {
            val current = uiComponents[index] as ScriptUIComponent.Input
            if (current.hint != hint) uiComponents[index] = current.copy(hint = hint)
        } else {
            uiComponents.add(ScriptUIComponent.Input(id, hint))
        }
    }

    /** Marshals a UI mutation to the main thread and drops it if it is a stale render. */
    private fun postUi(session: Long, block: () -> Unit) {
        viewModelScope.launch {
            if (session != uiSession) return@launch
            block()
        }
    }

    private companion object {
        const val ON_LAUNCH_FUNCTION = "onLaunch"

        /** Flattens `null`/`undefined`/blank handler returns so the console stays clean. */
        fun normalizeOutput(value: String): String = when {
            value.isBlank() || value == "null" || value == "undefined" -> ""
            else -> value
        }
    }

    /**
     * Builds a [ScriptCanvasViewModel] for a specific [scriptId]. Because this ViewModel
     * takes a constructor argument, it cannot go through the reflection-based default
     * factory; the factory closes over the id instead (mirrors ScriptDetailScreen).
     */
    class Factory(private val scriptId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            ScriptCanvasViewModel(scriptId) as T
    }
}