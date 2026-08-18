package app.rocat.ui.scripts

import androidx.lifecycle.viewModelScope
import app.rocat.core.common.injekt.Injekt
import app.rocat.core.viewmodel.StateViewModel
import app.rocat.domain.script.DeleteScript
import app.rocat.domain.script.GetScripts
import app.rocat.domain.script.SetScriptEnabled
import app.rocat.scripting.ScriptSettingsManager
import app.rocat.scripting.api.model.Script
import app.rocat.storage.StorageManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScriptsViewModel(
    private val getScripts: GetScripts = Injekt.get(),
    private val setScriptEnabled: SetScriptEnabled = Injekt.get(),
    private val deleteScript: DeleteScript = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
    private val settingsManager: ScriptSettingsManager = Injekt.get(),
) : StateViewModel<ScriptsViewModel.State>(State()) {

    data class State(
        val scripts: List<Script> = emptyList(),
        val loading: Boolean = true,
    )

    val scriptsState: StateFlow<State> = getScripts.subscribe()
        .map { State(scripts = it, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        setScriptEnabled.await(id, enabled)
    }

    fun delete(id: String) = viewModelScope.launch {
        deleteScript.await(id)
        // Tahap 17.2: remove the physical `Scripts/[id]` folder too.
        storageManager.deleteScriptFolder(id)
        // Tahap 35: drop the persisted settings + input history for the deleted script.
        settingsManager.deleteAll(id)
    }
}
