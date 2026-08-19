package app.rocat.scripting

import app.rocat.core.common.util.JsonUtil
import app.rocat.data.db.AppDatabase
import app.rocat.data.db.ScriptInputHistoryEntity
import app.rocat.data.db.ScriptSettingEntity
import app.rocat.domain.script.ScriptMetadataParser
import app.rocat.domain.script.ScriptSetting
import app.rocat.scripting.api.ScriptSettingsBridge
import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns a script's editable configuration (Tahap 35):
 *
 * - **Persistence** — values live in Room (`script_settings`, keyed by script id) so
 *   they survive restarts and are shared between the settings page and the canvas.
 * - **Defaults** — declared `@settings` defaults win over the implicit
 *   [ScriptDefaultSettings] fallbacks; a script that declares nothing still gets
 *   well-known keys (`autoRun`, `timeout`, ...) from `RoCat.settings`.
 * - **Temp storage** — per-session `setTemp`/`getTemp` kept in memory.
 * - **History** — `saveHistory`/`history`/`clearHistory` feed the autocomplete input.
 * - **Export/Import** — JSON round-trip for sharing/backing up a script's config.
 *
 * The singleton is registered in [app.rocat.di.AppModule]; [bridgeFor] builds the
 * [ScriptSettingsBridge] handed to the Rhino engine for a specific script.
 */
class ScriptSettingsManager(
    private val database: AppDatabase,
) {
    private companion object { const val STORAGE_PREFIX = "__storage__:" }

    /** In-memory per-script session values (scriptId -> key -> value). */
    private val tempStore = ConcurrentHashMap<String, MutableMap<String, String>>()

    /** Emits a script id whenever `RoCat.openSettings()` is called by that script. */
    private val openSettingsRequests = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun settingsOpenRequests(): Flow<String> = openSettingsRequests

    /** Builds the engine-facing bridge for one script (metadata parsed from source). */
    fun bridgeFor(script: Script): ScriptSettingsBridge = ScriptBridge(script)

    // ---- Settings page / repository API (suspend, main-thread safe) ----

    suspend fun load(scriptId: String): Map<String, String> = withContext(Dispatchers.IO) {
        database.scriptSettingsDao().getAll(scriptId).associate { it.key to it.value }
    }

    suspend fun getValue(scriptId: String, key: String, declaredDefault: String): String =
        withContext(Dispatchers.IO) {
            database.scriptSettingsDao().get(scriptId, key)?.value ?: declaredDefault
        }

    /** Validates [value] against [type] + the declared constraints, then persists. */
    suspend fun setValue(scriptId: String, key: String, value: String, type: String, setting: ScriptSetting?) =
        withContext(Dispatchers.IO) {
            val validated = validate(key, value, type, setting)
            database.scriptSettingsDao().upsert(
                ScriptSettingEntity(scriptId = scriptId, key = key, value = validated, type = type),
            )
        }

    /** Removes every persisted value so declared/implicit defaults apply again. */
    suspend fun resetToDefault(scriptId: String) = withContext(Dispatchers.IO) {
        database.scriptSettingsDao().deleteAll(scriptId)
        tempStore.remove(scriptId)
    }

    /** Serialises the persisted values as `{ "scriptId": ..., "settings": { key: value } }`. */
    suspend fun exportSettings(scriptId: String): String = withContext(Dispatchers.IO) {
        val values = database.scriptSettingsDao().getAll(scriptId).associate { it.key to it.value }
        buildJsonObject {
            put("scriptId", scriptId)
            put("settings", JsonObject(values.mapValues { JsonPrimitive(it.value) }))
        }.toString()
    }

    /**
     * Imports `{ "settings": { ... } }` or a bare `{ key: value }` object. Values are
     * validated per declared type where possible. Returns false when the payload is
     * not usable JSON (never throws).
     */
    suspend fun importSettings(scriptId: String, json: String, settingTypes: Map<String, String> = emptyMap()): Boolean =
        withContext(Dispatchers.IO) {
            val element = try {
                Json.parseToJsonElement(json)
            } catch (_: Exception) {
                return@withContext false
            }
            val obj = (element as? JsonObject) ?: return@withContext false
            val settingsObj = (obj["settings"] as? JsonObject) ?: obj
            if (settingsObj.isEmpty()) return@withContext false

            for ((key, value) in settingsObj) {
                val raw = (value as? JsonPrimitive)?.contentOrNull ?: continue
                val type = settingTypes[key] ?: "string"
                val validated = validate(key, raw, type, null)
                database.scriptSettingsDao().upsert(
                    ScriptSettingEntity(scriptId = scriptId, key = key, value = validated, type = type),
                )
            }
            true
        }

    suspend fun history(scriptId: String, key: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        database.scriptInputHistoryDao().recent(scriptId, key, limit.coerceIn(1, 100))
    }

    suspend fun saveHistory(scriptId: String, key: String, value: String) = withContext(Dispatchers.IO) {
        if (key.isBlank() || value.isBlank()) return@withContext
        database.scriptInputHistoryDao().insert(
            ScriptInputHistoryEntity(scriptId = scriptId, key = key, value = value.trim()),
        )
    }

    suspend fun clearHistory(scriptId: String, key: String) = withContext(Dispatchers.IO) {
        database.scriptInputHistoryDao().clear(scriptId, key)
    }

    /** Removes the script's persisted config + history (used when a script is deleted). */
    suspend fun deleteAll(scriptId: String) = withContext(Dispatchers.IO) {
        database.scriptSettingsDao().deleteAll(scriptId)
        database.scriptInputHistoryDao().clearAll(scriptId)
        tempStore.remove(scriptId)
    }

    // ---- Engine-facing bridge (synchronous; hops to IO for Room calls) ----

    private inner class ScriptBridge(
        private val script: Script,
    ) : ScriptSettingsBridge {

        private val declared: List<ScriptSetting> =
            runCatching { ScriptMetadataParser.parse(script.source).settings }.getOrDefault(emptyList())

        private fun declaredBy(key: String): ScriptSetting? = declared.firstOrNull { it.key == key }

        override fun types(): Map<String, String> {
            val map = HashMap<String, String>()
            declared.forEach { map[it.key] = it.type.wire }
            ScriptDefaultSettings.defaults.forEach { (k, d) -> map.putIfAbsent(k, d.type) }
            return map
        }

        override fun snapshot(): Map<String, String> {
            val persisted = runBlockingIo { database.scriptSettingsDao().getAll(script.id) }
                .filterNot { it.key.startsWith(STORAGE_PREFIX) }
                .associate { it.key to it.value }
            val declaredDefaults = declared.associate { it.key to it.normalizedDefault }
            val implicit = ScriptDefaultSettings.defaults.mapValues { it.value.value }
            return implicit + declaredDefaults + persisted
        }

        override fun setValue(key: String, value: String) {
            val type = types()[key] ?: "string"
            val validated = validate(key, value, type, declaredBy(key))
            runBlockingIo { database.scriptSettingsDao().upsert(ScriptSettingEntity(script.id, key, validated, type)) }
        }

        override fun setTemp(key: String, value: String) {
            tempStore.computeIfAbsent(script.id) { ConcurrentHashMap() }[key] = value
        }

        override fun getTemp(key: String): String? = tempStore[script.id]?.get(key)

        override fun saveHistory(key: String, value: String) = runBlockingIo {
            if (key.isBlank() || value.isBlank()) return@runBlockingIo
            database.scriptInputHistoryDao().insert(
                ScriptInputHistoryEntity(scriptId = script.id, key = key, value = value.trim()),
            )
        }

        override fun history(key: String, limit: Int): List<String> = runBlockingIo {
            database.scriptInputHistoryDao().recent(script.id, key, limit.coerceIn(1, 100))
        }

        override fun clearHistory(key: String) = runBlockingIo {
            database.scriptInputHistoryDao().clear(script.id, key)
        }

        override fun openSettings() {
            openSettingsRequests.tryEmit(script.id)
        }

        override fun storageSet(key: String, value: String) = runBlockingIo {
            if (key.isBlank()) return@runBlockingIo
            database.scriptSettingsDao().upsert(ScriptSettingEntity(script.id, STORAGE_PREFIX + key, value, "storage"))
        }

        override fun storageGet(key: String): String? = runBlockingIo {
            database.scriptSettingsDao().get(script.id, STORAGE_PREFIX + key)?.value
        }

        override fun storageRemove(key: String) = runBlockingIo {
            database.scriptSettingsDao().delete(script.id, STORAGE_PREFIX + key)
        }

        override fun storageClear() = runBlockingIo {
            database.scriptSettingsDao().deleteStorage(script.id, STORAGE_PREFIX + "%")
        }
    }

    private fun <T> runBlockingIo(block: suspend () -> T): T = kotlinx.coroutines.runBlocking(Dispatchers.IO) { block() }

    /** Coerces a raw string value to the canonical form for [type], clamping to the
     *  declared min/max/options. Invalid input falls back to the declared default. */
    private fun validate(key: String, value: String, type: String, setting: ScriptSetting?): String {
        return when (type) {
            "boolean" -> if (value.trim().equals("true", ignoreCase = true) || value.trim() == "1") "true" else "false"
            "number" -> {
                val number = value.trim().toDoubleOrNull()
                if (number == null || !number.isFinite()) {
                    setting?.normalizedDefault?.ifBlank { "0" } ?: "0"
                } else {
                    val min = setting?.min
                    val max = setting?.max
                    var v: Double = number
                    if (min != null && v < min) v = min
                    if (max != null && v > max) v = max
                    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
                }
            }
            "select" -> {
                val options = setting?.options.orEmpty()
                if (options.isNotEmpty() && value !in options) options.first() else value
            }
            else -> value
        }
    }
}

/**
 * Recommended implicit settings every script gets (Tahap 35 §3). These act as a
 * documented fallback layer behind the script's own `@settings` declarations, so
 * `RoCat.settings.*` never returns `undefined` for well-known keys even in scripts
 * that don't declare them. Scripts can override any of these via `@settings`.
 */
object ScriptDefaultSettings {
    data class Default(val value: String, val type: String)

    val defaults: Map<String, Default> = mapOf(
        "autoRun" to Default("true", "boolean"),
        "debugMode" to Default("false", "boolean"),
        "cacheEnabled" to Default("true", "boolean"),
        "clearCookies" to Default("false", "boolean"),
        "clearHistory" to Default("false", "boolean"),
        "clearCache" to Default("false", "boolean"),
        "incognitoMode" to Default("false", "boolean"),
        "autoDownload" to Default("false", "boolean"),
        "downloadPath" to Default("", "string"),
        "maxConcurrentDownloads" to Default("3", "number"),
        "preferredQuality" to Default("auto", "string"),
        "timeout" to Default("30000", "number"),
        "maxRetries" to Default("3", "number"),
        "retryDelay" to Default("1000", "number"),
        "userAgent" to Default("", "string"),
        "followRedirects" to Default("true", "boolean"),
        "showNotifications" to Default("true", "boolean"),
        "compactMode" to Default("false", "boolean"),
        "theme" to Default("system", "string"),
    )
}
