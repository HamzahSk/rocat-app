package app.rocat.scripting.api

/**
 * Native bridge exposing a script's per-script settings (Tahap 35) to the engine as the
 * global `RoCat.settings` object plus history/temp helpers. The host app implements it
 * (typically on top of its persistence layer); a default no-op keeps plain executions
 * and unit-test recorders valid.
 *
 * Every method is safe to call from any thread. Values travel as strings:
 * booleans use `"true"`/`"false"`, numbers their decimal representation.
 */
interface ScriptSettingsBridge {

    /**
     * Current effective values for every setting the script can read. Keys that the
     * script declares but the user never customized must be returned with their
     * declared default; the app may also include implicit (un-declared) defaults so
     * `RoCat.settings.*` never returns `undefined` for well-known keys.
     */
    fun snapshot(): Map<String, String> = emptyMap()

    /**
     * The canonical `@settings` type (`boolean`/`number`/`string`/...) per key, used by
     * the engine to coerce the string snapshot into typed JS values. Keys not present
     * fall back to string coercion.
     */
    fun types(): Map<String, String> = emptyMap()

    /** Persists a single setting value (validated/coerced by the host). */
    fun setValue(key: String, value: String) = Unit

    /** Stores a temporary (per-session) value under [key]. */
    fun setTemp(key: String, value: String) = Unit

    /** Reads a temporary (per-session) value, or `null` when absent. */
    fun getTemp(key: String): String? = null

    /** Appends [value] to the script's input history bucket named [key]. */
    fun saveHistory(key: String, value: String) = Unit

    /** Returns the most recently used values (newest first) for history bucket [key]. */
    fun history(key: String, limit: Int): List<String> = emptyList()

    /** Clears the script's input history bucket named [key]. */
    fun clearHistory(key: String) = Unit

    /** Asks the host to navigate to this script's settings page. */
    fun openSettings() = Unit
}