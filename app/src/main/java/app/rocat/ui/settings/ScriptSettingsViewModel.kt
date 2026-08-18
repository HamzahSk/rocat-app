package app.rocat.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.rocat.core.common.injekt.Injekt
import app.rocat.core.viewmodel.StateViewModel
import app.rocat.domain.script.GetScripts
import app.rocat.domain.script.ScriptMetadataParser
import app.rocat.domain.script.ScriptSetting
import app.rocat.domain.script.ScriptSettingType
import app.rocat.i18n.StringKey
import app.rocat.scripting.ScriptSettingsManager
import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Engine for the per-script settings page (Tahap 35). Loads the script's declared
 * `@settings` metadata plus the persisted values, renders a typed form and persists
 * edits immediately. Also handles reset-to-default and JSON export/import.
 */
class ScriptSettingsViewModel(
    private val scriptId: String,
    private val getScripts: GetScripts = Injekt.get(),
    private val settingsManager: ScriptSettingsManager = Injekt.get(),
) : StateViewModel<ScriptSettingsViewModel.State>(State()) {

    data class State(
        val script: Script? = null,
        val settings: List<ScriptSetting> = emptyList(),
        val values: Map<String, String> = emptyMap(),
        val loaded: Boolean = false,
        val exportJson: String? = null,
    )

    /** One-shot UI feedback events (reset/import outcomes) resolved by the screen. */
    private val _toastEvents = MutableSharedFlow<StringKey>(extraBufferCapacity = 4)
    val toastEvents: SharedFlow<StringKey> = _toastEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            getScripts.subscribe().collect { list ->
                val script = list.firstOrNull { it.id == scriptId }
                val declared = script?.let { ScriptMetadataParser.parse(it.source).settings }.orEmpty()
                val persisted = if (script != null) settingsManager.load(script.id) else emptyMap()
                val values = declared.associate { it.key to it.normalizedDefault } + persisted
                mutableState.update {
                    it.copy(script = script, settings = declared, values = values, loaded = true)
                }
            }
        }
    }

    fun currentValue(setting: ScriptSetting): String =
        state.value.values[setting.key] ?: setting.normalizedDefault

    /** Validates + persists a single edit and keeps the local map in sync. */
    fun setValue(setting: ScriptSetting, raw: String) {
        val normalized = normalize(setting, raw)
        mutableState.update { it.copy(values = it.values + (setting.key to normalized)) }
        val script = state.value.script ?: return
        viewModelScope.launch {
            settingsManager.setValue(script.id, setting.key, normalized, setting.type.wire, setting)
        }
    }

    /** Restores every declared default (persisted rows are deleted). */
    fun resetToDefault() {
        val script = state.value.script ?: return
        viewModelScope.launch {
            settingsManager.resetToDefault(script.id)
            val defaults = state.value.settings.associate { it.key to it.normalizedDefault }
            mutableState.update { it.copy(values = defaults) }
            _toastEvents.emit(StringKey.settingsReset)
        }
    }

    /** Serialises the current persisted values to JSON for sharing/backup. */
    fun export() {
        val script = state.value.script ?: return
        viewModelScope.launch {
            mutableState.update { it.copy(exportJson = settingsManager.exportSettings(script.id)) }
        }
    }

    fun dismissExport() {
        mutableState.update { it.copy(exportJson = null) }
    }

    /** Imports a settings JSON payload (validated per declared type). */
    fun import(json: String) {
        val script = state.value.script ?: return
        viewModelScope.launch {
            val types = state.value.settings.associate { it.key to it.type.wire }
            val ok = settingsManager.importSettings(script.id, json, types)
            val persisted = settingsManager.load(script.id)
            mutableState.update { it.copy(values = persisted) }
            _toastEvents.emit(if (ok) StringKey.settingsImported else StringKey.settingsImportFailed)
        }
    }

    private fun normalize(setting: ScriptSetting, raw: String): String = when (setting.type) {
        ScriptSettingType.BOOLEAN -> if (raw.trim().equals("true", ignoreCase = true) || raw.trim() == "1") "true" else "false"
        ScriptSettingType.NUMBER -> {
            val parsed = raw.trim().toDoubleOrNull()
            if (parsed == null || !parsed.isFinite()) {
                setting.normalizedDefault.ifBlank { "0" }
            } else {
                val min = setting.min
                val max = setting.max
                var v: Double = parsed
                if (min != null && v < min) v = min
                if (max != null && v > max) v = max
                if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
            }
        }
        ScriptSettingType.SELECT -> {
            if (setting.options.isNotEmpty() && raw !in setting.options) setting.options.first() else raw
        }
        else -> raw
    }

    /**
     * Builds a [ScriptSettingsViewModel] for a specific [scriptId] (constructor arg, so
     * it needs a custom factory).
     */
    class Factory(private val scriptId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            ScriptSettingsViewModel(scriptId) as T
    }
}