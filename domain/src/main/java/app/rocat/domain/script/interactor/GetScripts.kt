package app.rocat.domain.script

import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.flow.Flow

/**
 * Use case that exposes the installed scripts to the presentation layer, mirroring
 * mihon's `Get...` interactors in `domain/.../interactor/`.
 */
class GetScripts(private val repository: ScriptRepository) {
    fun subscribe(): Flow<List<Script>> = repository.getAllScripts()
}