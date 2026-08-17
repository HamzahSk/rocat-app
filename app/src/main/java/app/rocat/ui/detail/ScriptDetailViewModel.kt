package app.rocat.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.rocat.core.common.injekt.Injekt
import app.rocat.core.viewmodel.StateViewModel
import app.rocat.domain.script.DeleteScript
import app.rocat.domain.script.GetScripts
import app.rocat.domain.script.SetScriptEnabled
import app.rocat.domain.script.UpsertScript
import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScriptDetailViewModel(
    private val scriptId: String,
    private val getScripts: GetScripts = Injekt.get(),
    private val setScriptEnabled: SetScriptEnabled = Injekt.get(),
    private val deleteScript: DeleteScript = Injekt.get(),
    private val upsertScript: UpsertScript = Injekt.get(),
) : StateViewModel<ScriptDetailViewModel.State>(State()) {

    data class State(
        val script: Script? = null,
        val showCode: Boolean = false,
    )

    val detailState: StateFlow<State> = getScripts.subscribe()
        .map { list -> State(script = list.firstOrNull { it.id == scriptId }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setEnabled(enabled: Boolean) {
        val id = detailState.value.script?.id ?: return
        viewModelScope.launch { setScriptEnabled.await(id, enabled) }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteScript.await(scriptId)
            onDeleted()
        }
    }

    fun toggleCode() = mutableState.update { it.copy(showCode = !it.showCode) }

    fun saveSource(newSource: String, onSaved: () -> Unit) {
        val script = detailState.value.script ?: return
        viewModelScope.launch {
            upsertScript.await(script.id, script.name, newSource, script.description)
            onSaved()
        }
    }

    /**
     * Builds a [ScriptDetailViewModel] for a specific [scriptId]. Because this
     * ViewModel takes a constructor argument, it cannot go through the default
     * reflection-based factory; the factory closes over the id instead.
     */
    class Factory(private val scriptId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            ScriptDetailViewModel(scriptId) as T
    }
}
