package app.rocat.domain.script

import app.rocat.scripting.api.model.Script

/**
 * Use case to install/update a script from raw JS source. Metadata is parsed from the
 * userscript header block; an explicit [id] keeps the entry stable across re-installs.
 */
class UpsertScript(private val repository: ScriptRepository) {
    suspend fun await(
        id: String,
        name: String,
        source: String,
        description: String = "",
    ): Script {
        val metadata = ScriptMetadataParser.parse(source)
        val existing = repository.getScriptById(id)
        val script = Script(
            id = id,
            name = name.ifBlank { metadata.name.ifBlank { "Unnamed script" } },
            version = metadata.version,
            description = description.ifBlank { metadata.description },
            author = metadata.author,
            icon = metadata.icon,
            category = metadata.category,
            source = source,
            matches = metadata.matches,
            enabled = existing?.enabled ?: true,
            updatedAt = System.currentTimeMillis(),
        )
        repository.upsertScript(script)
        return script
    }
}
