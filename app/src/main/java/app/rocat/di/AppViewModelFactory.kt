package app.rocat.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import app.rocat.ui.import.ImportScriptViewModel
import app.rocat.ui.settings.SettingsViewModel
import app.rocat.ui.scripts.ScriptsViewModel

/**
 * The single factory that knows how to build every app ViewModel. ViewModels resolve
 * their dependencies through Injekt (default constructor params), so the reflection
 * based default factory — which only supports no-arg constructors — must never be
 * used. Screens pass this factory explicitly to `viewModel(...)`, mirroring how mihon
 * wires its `ViewModelFactory` into the composition.
 */
object AppViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when {
        modelClass.isAssignableFrom(ScriptsViewModel::class.java) -> ScriptsViewModel() as T
        modelClass.isAssignableFrom(ImportScriptViewModel::class.java) -> ImportScriptViewModel() as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel() as T
        else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
