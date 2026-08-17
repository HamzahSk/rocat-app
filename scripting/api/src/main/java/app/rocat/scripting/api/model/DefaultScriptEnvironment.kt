package app.rocat.scripting.api.model

import app.rocat.scripting.api.FetchResult
import app.rocat.scripting.api.ScriptBrowserBridge
import app.rocat.scripting.api.ScriptEnvironment
import app.rocat.scripting.api.ScriptUiBridge

/**
 * Default [ScriptEnvironment] provided to the engine, containing the app's current
 * network client. Implemented in the RApi module so the engine only depends on the API.
 */
class DefaultScriptEnvironment(
    private val fetchImpl: suspend (String, String, Map<String, String>, String?) -> FetchResult,
    override val document: Any? = null,
    override val ui: ScriptUiBridge? = null,
    override val browser: ScriptBrowserBridge? = null,
) : ScriptEnvironment {

    override suspend fun fetch(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
    ): FetchResult = fetchImpl(url, method, headers, body)
}