package app.rocat.domain.script

import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.flow.Flow

/**
 * Interface for persisting and loading user scripts. Implemented in the `data` layer
 * (mirroring how mihon's repository interfaces are implemented in `tachiyomi.data`).
 */
interface ScriptRepository {
    /** Observes all installed scripts as a [Flow]. */
    fun getAllScripts(): Flow<List<Script>>

    /** Loads a single script by id, or `null`. */
    suspend fun getScriptById(id: String): Script?

    /** Installs or updates a script. */
    suspend fun upsertScript(script: Script)

    /** Removes a script. */
    suspend fun deleteScript(id: String)

    /** Toggles a script's enabled state. */
    suspend fun setEnabled(id: String, enabled: Boolean)
}