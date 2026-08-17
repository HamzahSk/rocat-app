package app.rocat.core.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base ViewModel exposing an immutable [StateFlow] of a [State], mirroring mihon's
 * `core/viewmodel/src/main/.../StateViewModel.kt`.
 */
abstract class StateViewModel<S>(initialState: S) : ViewModel() {
    protected val mutableState: MutableStateFlow<S> = MutableStateFlow(initialState)

    val state: StateFlow<S> = mutableState.asStateFlow()
}