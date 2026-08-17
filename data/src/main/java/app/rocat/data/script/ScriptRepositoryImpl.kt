package app.rocat.data.script

import app.rocat.core.common.network.NetworkHelper
import app.rocat.core.common.util.JsonUtil
import app.rocat.domain.script.ScriptRepository
import app.rocat.scripting.api.FetchResult
import app.rocat.scripting.api.ScriptBrowserBridge
import app.rocat.scripting.api.ScriptEngine
import app.rocat.scripting.api.ScriptEnvironment
import app.rocat.scripting.api.ScriptUiBridge
import app.rocat.scripting.api.model.DefaultScriptEnvironment
import app.rocat.scripting.api.model.Script
import app.rocat.scripting.api.network.scriptFetch
import app.rocat.scripting.rhino.RhinoScriptEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import java.io.File

/**
 * In-memory script store backed by a JSON file. Mirrors the repository-implementation
 * split: the [ScriptRepository] interface lives in `domain`, the impl in `data`.
 */
class ScriptRepositoryImpl(
    private val storageDir: File,
) : ScriptRepository {

    private val storeFile = File(storageDir, "scripts.json")

    // Own scope so initial load never runs on the caller (often the main) thread.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Starts empty; populated asynchronously in [init] so construction never blocks.
    private val _scripts = MutableStateFlow<List<Script>>(emptyList())
    override fun getAllScripts(): Flow<List<Script>> = _scripts.asStateFlow()

    private val mutex = Mutex()

    init {
        ioScope.launch {
            _scripts.value = load()
        }
    }

    override suspend fun getScriptById(id: String): Script? =
        _scripts.value.firstOrNull { it.id == id }

    override suspend fun upsertScript(script: Script) = mutex.withLock {
        val updated = _scripts.value
            .filterNot { it.id == script.id }
            .plus(script)
            .sortedBy { it.name }
        _scripts.value = updated
        save(updated)
    }

    override suspend fun deleteScript(id: String) = mutex.withLock {
        val updated = _scripts.value.filterNot { it.id == id }
        _scripts.value = updated
        save(updated)
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = mutex.withLock {
        val updated = _scripts.value.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        _scripts.value = updated
        save(updated)
    }

    private suspend fun load(): List<Script> = withContext(Dispatchers.IO) {
        try {
            if (!storeFile.exists()) {
                emptyList()
            } else {
                JsonUtil.json.decodeFromString<List<Script>>(storeFile.readText())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun save(scripts: List<Script>) {
        try {
            withContext(Dispatchers.IO) {
                storeFile.parentFile?.mkdirs()
                storeFile.writeText(JsonUtil.json.encodeToString(scripts))
            }
        } catch (e: Exception) {
            // Best-effort persistence; failures should not crash the app.
        }
    }
}

/**
 * Wires the [RhinoScriptEngine] together with a network-backed [ScriptEnvironment].
 * Every request made from script code goes through the app's OkHttp stack via the
 * shared [scriptFetch] helper (same interceptors/cookie jar as the rest of the app).
 *
 * The engine/environment are rebuilt lazily when the shared network configuration
 * (custom User-Agent / DoH DNS, Tahap 20) changes, so a scraper always picks up the
 * user's latest network settings without an app restart.
 */
class ScriptManager(
    networkHelper: NetworkHelper,
) {
    private val networkHelper: NetworkHelper = networkHelper

    /** Fingerprint of the network config the current [currentEngine] was built with. */
    private var lastFingerprint: String? = null

    @Volatile
    private var currentEngine: RhinoScriptEngine? = null

    @Volatile
    private var fetchImpl: (suspend (String, String, Map<String, String>, String?) -> FetchResult)? = null

    /** Recreates the engine + fetch bridge whenever the network config changed. */
    @Synchronized
    private fun refresh() {
        val fingerprint = networkHelper.fingerprint()
        if (currentEngine != null && lastFingerprint == fingerprint) return
        lastFingerprint = fingerprint
        val scriptClient: OkHttpClient = networkHelper.newScriptClient()
        currentEngine = RhinoScriptEngine(scriptClient)
        fetchImpl = { url: String, method: String, headers: Map<String, String>, body: String? ->
            scriptClient.scriptFetch(url, method, headers, body)
        }
    }

    /** The (config-fresh) script engine. */
    fun engine(): ScriptEngine {
        refresh()
        return currentEngine ?: error("ScriptManager not initialised")
    }

    /** The (config-fresh) plain environment, used for plain executions. */
    fun environment(): ScriptEnvironment = DefaultScriptEnvironment(
        fetchImpl = fetchImplOrThrow(),
    )

    /**
     * Builds a fresh environment wired to the same network stack but exposing [ui] as
     * the script's global `RoCatUI` object, letting a script drive a dynamic Compose
     * UI (used by the canvas/playground). When [browser] is provided it is exposed as
     * the script's global `RoCatPage` (Tahap 23: dual-mode scraping), letting a script
     * switch from static `fetch()` scraping to interactive headless-WebView automation.
     */
    fun createEnvironment(ui: ScriptUiBridge? = null, browser: ScriptBrowserBridge? = null): ScriptEnvironment =
        DefaultScriptEnvironment(
            fetchImpl = fetchImplOrThrow(),
            ui = ui,
            browser = browser,
        )

    private fun fetchImplOrThrow(): suspend (String, String, Map<String, String>, String?) -> FetchResult {
        refresh()
        return fetchImpl ?: error("ScriptManager not initialised")
    }
}