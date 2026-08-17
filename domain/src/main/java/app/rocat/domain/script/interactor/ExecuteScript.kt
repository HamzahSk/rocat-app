package app.rocat.domain.script

import app.rocat.scripting.api.ScriptEngine
import app.rocat.scripting.api.ScriptEnvironment
import app.rocat.scripting.api.ScriptResult
import app.rocat.scripting.api.model.Script

/**
 * Use case that runs a script through the shared [ScriptEngine]. This is where the
 * engine and environment get wired together, keeping the presentation layer simple.
 */
class ExecuteScript(
    private val engine: ScriptEngine,
    private val environment: ScriptEnvironment,
) {
    suspend fun await(script: Script, args: List<String> = emptyList()): ScriptResult =
        engine.execute(script, environment, args)

    /**
     * Runs a named function inside the script (e.g. `search(query)` or
     * `detail(url)`) and returns its value serialized to JSON. Used by the
     * playground's function picker.
     */
    suspend fun invoke(
        script: Script,
        functionName: String,
        args: List<String> = emptyList(),
        inputs: Map<String, String> = emptyMap(),
    ): ScriptResult =
        if (inputs.isEmpty()) {
            engine.invokeFunction(script, environment, functionName, args)
        } else {
            engine.invokeNamedFunction(script, environment, functionName, inputs)
        }
}