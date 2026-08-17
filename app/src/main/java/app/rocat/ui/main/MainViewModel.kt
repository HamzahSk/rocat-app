package app.rocat.ui.main

import androidx.lifecycle.viewModelScope
import app.rocat.core.viewmodel.StateViewModel
import app.rocat.domain.script.GetScripts
import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val getScripts: GetScripts = app.rocat.core.common.injekt.Injekt.get(),
) : StateViewModel<MainViewModel.State>(State()) {

    data class State(
        val scripts: List<Script> = emptyList(),
        val loading: Boolean = true,
    )

    val scriptsState: StateFlow<State> = getScripts.subscribe()
        .map { State(scripts = it, loading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        // Trigger collection so data loads immediately.
        scriptsState
    }
}