package app.rocat.domain.script

/** Use case that removes a script from the store. */
class DeleteScript(private val repository: ScriptRepository) {
    suspend fun await(id: String) = repository.deleteScript(id)
}
