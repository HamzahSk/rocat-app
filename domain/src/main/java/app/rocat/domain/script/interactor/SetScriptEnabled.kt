package app.rocat.domain.script

/** Use case that toggles a script's active/enabled state. */
class SetScriptEnabled(private val repository: ScriptRepository) {
    suspend fun await(id: String, enabled: Boolean) = repository.setEnabled(id, enabled)
}
