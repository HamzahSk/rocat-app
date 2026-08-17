package app.rocat.domain.script

import app.rocat.scripting.api.model.Script
import java.security.MessageDigest

/**
 * Imports a brand-new script from raw JS source. The id is derived from the script
 * name (or a content hash when unnamed) so re-importing the same script updates it
 * rather than duplicating it.
 */
class ImportScript(private val repository: ScriptRepository) {
    suspend fun await(
        source: String,
        explicitName: String? = null,
        explicitId: String? = null,
    ): Script {
        val metadata = ScriptMetadataParser.parse(source)
        val name = explicitName?.takeIf { it.isNotBlank() }
            ?: metadata.name.ifBlank { "Unnamed script" }
        val id = explicitId?.takeIf { it.isNotBlank() } ?: deriveId(name, source)
        val script = Script(
            id = id,
            name = name,
            version = metadata.version,
            description = metadata.description,
            author = metadata.author,
            icon = metadata.icon,
            category = metadata.category,
            source = source,
            matches = metadata.matches,
            enabled = true,
            updatedAt = System.currentTimeMillis(),
        )
        repository.upsertScript(script)
        return script
    }

    companion object {
        fun deriveId(name: String, source: String): String {
            val slug = name.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
            if (slug.isNotBlank()) return slug
            return "script-" + md5(source)
        }

        private fun md5(input: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
