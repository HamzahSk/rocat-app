package app.rocat.ui.canvas

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.rocat.core.common.injekt.Injekt
import app.rocat.core.viewmodel.StateViewModel
import app.rocat.data.script.ScriptManager
import app.rocat.domain.script.ExecuteScript
import app.rocat.domain.script.GetScripts
import app.rocat.scripting.ScriptSettingsManager
import app.rocat.scripting.api.ScriptResult
import app.rocat.scripting.api.ScriptBrowserBridge
import app.rocat.scripting.api.ScriptUiBridge
import app.rocat.scripting.api.baseUrlFromMatches
import app.rocat.scripting.api.effectiveMediaHeaders
import app.rocat.scripting.api.model.Script
import app.rocat.storage.StorageManager
import app.rocat.ui.components.ScriptUIComponent
import app.rocat.ui.components.fieldValue
import app.rocat.ui.components.parseBadgeGroup
import app.rocat.ui.components.parseComponents
import app.rocat.ui.components.parseGrid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *  - Tahap 35: rich controls (`checkbox`/`toggle`/`dropdown`/`number`/`colorpicker`/
 *    `textarea`/`autocomplete`) and flexible layouts (`layout` row/column/grid, `group`)
 *    are collected/updated recursively (also inside nested containers).
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
    private val settingsManager: ScriptSettingsManager = Injekt.get(),
) : StateViewModel<ScriptCanvasViewModel.State>(State()) {

    data class State(
        val script: Script? = null,
        val loaded: Boolean = false,
        val executing: Boolean = false,
        val executingFunction: String? = null,
        val output: String = "",
    )

    /** The ordered, script-driven list of components rendered by the canvas. */
    val uiComponents: SnapshotStateList<ScriptUIComponent> = mutableStateListOf()

    /** History suggestions (historyKey -> values) for autocomplete inputs. */
    val historyState: SnapshotStateMap<String, List<String>> = mutableStateMapOf()

    private val historyLoaded = mutableSetOf<String>()

    /** Incremented whenever the script calls `RoCat.openSettings()`; the screen observes
     *  this and navigates to the per-script settings page. */
    private val _openSettingsRequest = MutableStateFlow(0L)
    val openSettingsRequest: StateFlow<Long> = _openSettingsRequest.asStateFlow()

    /**
     * Monotonic session id. Incremented whenever a fresh render starts (a new `onLaunch()`
     * draw or a script source change) so queued bridge updates from an older render are
     * discarded on the main thread.
     */
    @Volatile
    private var uiSession: Long = 0

    /** Last source string that triggered a render; used to auto-redraw on edit. */
    private var lastSource: String? = null

    /** The per-script scrape folder inside `[MainDirectory]/Scrapes/`, created lazily. */
    @Volatile
    private var scrapeFolder: androidx.documentfile.provider.DocumentFile? = null

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
            uiComponents.add(ScriptUIComponent.Button(label, functionName))
        }
        override fun thumbnailPreview(url: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Image(url = url, headers = resolveHeaders(emptyMap(), url)))
        }
        override fun videoPreview(url: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Video(url, "", false, true, resolveHeaders(emptyMap(), url)))
        }
        override fun addImage(url: String, title: String, allowDownload: Boolean, headers: Map<String, String>) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Image(url, title, allowDownload, resolveHeaders(headers, url)))
        }
        override fun addImage(url: String, title: String, allowDownload: Boolean, headers: Map<String, String>, seamless: Boolean) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Image(url, title, allowDownload, resolveHeaders(headers, url), seamless))
        }
        override fun addVideo(url: String, title: String, isStreamHls: Boolean, allowDownload: Boolean, headers: Map<String, String>) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Video(url, title, isStreamHls, allowDownload, resolveHeaders(headers, url)))
        }
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

        // --- Tahap 35: flexible layouts & rich input controls ---
        override fun addText(content: String, style: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Text(content, style))
        }
        override fun addDivider(thickness: Int, color: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Divider(thickness, color))
        }
        override fun addCheckbox(id: String, label: String, checked: Boolean) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Checkbox(id, label, checked))
        }
        override fun addToggle(id: String, label: String, checked: Boolean) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Toggle(id, label, checked))
        }
        override fun addDropdown(id: String, options: List<String>, selected: String, label: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Dropdown(id, label, options, selected))
        }
        override fun addNumber(id: String, value: Double?, min: Double?, max: Double?, step: Double?, label: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Number(id, label, value, min, max, step))
        }
        override fun addColorPicker(id: String, color: String, label: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.ColorPicker(id, label, color))
        }
        override fun addTextArea(id: String, hint: String, rows: Int, value: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.TextArea(id, hint, value, rows))
        }
        override fun addAutocomplete(
            id: String,
            hint: String,
            suggestions: List<String>,
            historyKey: String,
            maxHistory: Int,
            showHistory: Boolean,
            showClearHistory: Boolean,
            value: String,
        ) = postUi(uiSession) {
            uiComponents.add(
                ScriptUIComponent.Autocomplete(id, hint, value, suggestions, historyKey, maxHistory, showHistory, showClearHistory),
            )
        }
        override fun addGroup(title: String, collapsed: Boolean, childrenJson: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Group(title, collapsed, parseComponents(childrenJson)))
        }
        override fun addLayout(
            layout: String,
            columns: Int,
            padding: Int,
            divider: Boolean,
            childrenJson: String,
            flex: Int?,
        ) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Layout(layout, columns, padding, divider, flex, parseComponents(childrenJson)))
        }
        override fun addLayoutOptions(layout: String, columns: Int, padding: Int, divider: Boolean, childrenJson: String, flex: Int?, margin: Int, spacing: Int, align: String) = postUi(uiSession) {
            uiComponents.add(ScriptUIComponent.Layout(layout, columns, padding, divider, flex, parseComponents(childrenJson), margin, spacing, align))
        }
    }

    /** Builds a fresh engine/environment pair per call. The per-script settings bridge
     *  is attached so scripts get `RoCat.settings` + history (Tahap 35). */
    private fun executeWith(script: Script): ExecuteScript =
        ExecuteScript(
            engine = scriptManager.engine(),
            environment = scriptManager.createEnvironment(
                ui = uiBridge,
                browser = browserBridge,
                settings = settingsManager.bridgeFor(script),
            ),
        )

    override fun onCleared() {
        super.onCleared()
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
        viewModelScope.launch {
            settingsManager.settingsOpenRequests().collect { requestedId ->
                if (requestedId == scriptId) _openSettingsRequest.value++
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
        historyState.clear()
        historyLoaded.clear()
        mutableState.update { it.copy(output = "") }

        viewModelScope.launch {
            val result = try {
                executeWith(script).invoke(script, ON_LAUNCH_FUNCTION)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                ScriptResult.Failure(e.message ?: e.javaClass.simpleName)
            }
            if (session != uiSession) return@launch
            if (result is ScriptResult.Failure) {
                val error = result.error
                if (error?.contains("no function") != true) {
                    mutableState.update {
                        it.copy(output = "onLaunch error: $error")
                    }
                }
            }
        }
    }

    // ---- Field value updates (recursive, also inside group/layout containers) ----

    /** Updates any text-ish field ([Input], [ScriptUIComponent.TextArea], autocomplete,
     *  dropdown, number, color picker) identified by [id]. */
    fun updateFieldValue(id: String, value: String) {
        replaceInTree(uiComponents, id) { field ->
            when (field) {
                is ScriptUIComponent.Input -> field.copy(value = value)
                is ScriptUIComponent.TextArea -> field.copy(value = value)
                is ScriptUIComponent.Autocomplete -> field.copy(value = value)
                is ScriptUIComponent.Dropdown -> field.copy(selected = value)
                is ScriptUIComponent.Number -> field.copy(value = value.toDoubleOrNull())
                is ScriptUIComponent.ColorPicker -> field.copy(color = value)
                else -> field
            }
        }
    }

    /** Updates the checked state of a checkbox/toggle identified by [id]. */
    fun updateChecked(id: String, checked: Boolean) {
        replaceInTree(uiComponents, id) { field ->
            when (field) {
                is ScriptUIComponent.Checkbox -> field.copy(checked = checked)
                is ScriptUIComponent.Toggle -> field.copy(checked = checked)
                else -> field
            }
        }
    }

    /** Steps a [ScriptUIComponent.Number] field by ±[step] (clamped to min/max). */
    fun stepNumber(id: String, delta: Double) {
        replaceInTree(uiComponents, id) { field ->
            if (field is ScriptUIComponent.Number) {
                val step = field.step ?: 1.0
                val current = field.value ?: (field.min ?: 0.0)
                var next = current + delta * step
                field.min?.let { if (next < it) next = it }
                field.max?.let { if (next > it) next = it }
                field.copy(value = next)
            } else {
                field
            }
        }
    }

    /** Depth-first replace of the field with [id] using [transform]. Returns true when
     *  found, so nested children only get copied along the matched path. */
    private fun replaceInTree(
        items: MutableList<ScriptUIComponent>,
        id: String,
        transform: (ScriptUIComponent.Field) -> ScriptUIComponent.Field,
    ): Boolean {
        for (i in items.indices) {
            val component = items[i]
            when {
                component is ScriptUIComponent.Field && component.id == id -> {
                    items[i] = transform(component)
                    return true
                }
                component is ScriptUIComponent.Group -> {
                    val children = component.children.toMutableList()
                    if (replaceInTree(children, id, transform)) {
                        items[i] = component.copy(children = children)
                        return true
                    }
                }
                component is ScriptUIComponent.Layout -> {
                    val children = component.children.toMutableList()
                    if (replaceInTree(children, id, transform)) {
                        items[i] = component.copy(children = children)
                        return true
                    }
                }
            }
        }
        return false
    }

    /** Collects every field value (recursively) as `id -> string value`. Blank text-ish
     *  fields are skipped (backward compatible); structured fields always contribute. */
    private fun collectFields(root: List<ScriptUIComponent>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        fun walk(items: List<ScriptUIComponent>) {
            items.forEach { component ->
                when (component) {
                    is ScriptUIComponent.Field -> component.fieldValue()?.let { result[component.id] = it }
                    is ScriptUIComponent.Group -> walk(component.children)
                    is ScriptUIComponent.Layout -> walk(component.children)
                    else -> Unit
                }
            }
        }
        walk(root)
        return result
    }

    // ---- History (autocomplete) support ----

    /** Loads (once) the history bucket [historyKey] so autocomplete inputs can suggest it. */
    fun loadHistory(historyKey: String) {
        if (historyKey.isBlank()) return
        if (!historyLoaded.add(historyKey)) return
        val script = state.value.script ?: return
        viewModelScope.launch {
            historyState[historyKey] = settingsManager.history(script.id, historyKey, 50)
        }
    }

    /** Clears the history bucket [historyKey] (autocomplete "clear history" action). */
    fun clearHistory(historyKey: String) {
        if (historyKey.isBlank()) return
        val script = state.value.script ?: return
        viewModelScope.launch {
            settingsManager.clearHistory(script.id, historyKey)
            historyState[historyKey] = emptyList()
        }
    }

    private fun saveAutocompleteHistory(script: Script) {
        fun walk(items: List<ScriptUIComponent>) {
            items.forEach { component ->
                when (component) {
                    is ScriptUIComponent.Autocomplete ->
                        if (component.historyKey.isNotBlank() && component.value.isNotBlank()) {
                            viewModelScope.launch {
                                settingsManager.saveHistory(script.id, component.historyKey, component.value)
                            }
                        }
                    is ScriptUIComponent.Group -> walk(component.children)
                    is ScriptUIComponent.Layout -> walk(component.children)
                    else -> Unit
                }
            }
        }
        walk(uiComponents.toList())
    }

    /**
     * Pressing a `RoCatUI.Button`: gathers every field value (recursively) into a
     * `Map<id, value>` and invokes the named JS function with that object as one argument.
     */
    fun onScriptButton(functionName: String) {
        val script = state.value.script ?: return
        val inputs = collectFields(uiComponents.toList())
        saveAutocompleteHistory(script)
        execute(script, functionName, inputs = inputs, args = emptyList())
    }

    /**
     * Tapping a tile of a `RoCatUI` grid: forwards the tile's raw JSON payload as a
     * string argument (`openDetail(itemJson)`) so the script can render its detail page.
     */
    fun onGridItemClick(functionName: String, payload: String) {
        val script = state.value.script ?: return
        execute(script, functionName, inputs = emptyMap(), args = listOf(payload))
    }

    /** Re-runs the script's `onLaunch()` to redraw the canvas from scratch. */
    fun rebuildCanvas() {
        state.value.script?.let { renderOnLaunch(it) }
    }

    private fun execute(
        script: Script,
        functionName: String,
        inputs: Map<String, String>,
        args: List<String>,
    ) {
        scrapeFolder()
        mutableState.update { it.copy(executing = true, executingFunction = functionName, output = "") }
        viewModelScope.launch {
            val result = try {
                if (args.isNotEmpty()) {
                    executeWith(script).invoke(script, functionName, args = args)
                } else {
                    executeWith(script).invoke(script, functionName, inputs = inputs)
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
            mutableState.update {
                it.copy(executing = false, executingFunction = null, output = message)
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
