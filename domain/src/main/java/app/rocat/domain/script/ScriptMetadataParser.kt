package app.rocat.domain.script

/**
 * Parses Tampermonkey-style userscript metadata headers from raw JavaScript source.
 *
 * Supports the `==UserScript==` block (preferred) and falls back to scanning every
 * `// @tag value` line when no block is present, so loose scripts still get parsed.
 * Multi-line `@description`/`@author` continuations are joined with newlines.
 */
object ScriptMetadataParser {

    private val userScriptBlock = Regex("==UserScript==([\\s\\S]*?)==/UserScript==")
    private val tagLine = Regex("^\\s*//\\s*@(\\w+)\\s*(.*?)\\s*$")
    private val continuationLine = Regex("^\\s*//\\s+(?!@)(.*?)\\s*$")
    private val multiLineTags = setOf("description", "author")

    fun parse(source: String): ScriptMetadata {
        val block = userScriptBlock.find(source)?.groupValues?.getOrNull(1)
        val tags = linkedMapOf<String, MutableList<String>>()
        var currentTag: String? = null

        (block ?: source).lineSequence().forEach { raw ->
            val tagMatch = tagLine.find(raw)
            if (tagMatch != null) {
                val key = tagMatch.groupValues[1].lowercase()
                tags.getOrPut(key) { mutableListOf() }.add(tagMatch.groupValues[2].trim())
                currentTag = key
            } else if (currentTag in multiLineTags) {
                continuationLine.find(raw)?.let { m ->
                    val list = tags[currentTag]
                    if (list != null && list.isNotEmpty()) {
                        val continuation = m.groupValues[1].trim()
                        if (continuation.isNotEmpty()) {
                            val index = list.size - 1
                            list[index] = if (list[index].isEmpty()) continuation else "${list[index]}\n$continuation"
                        }
                    }
                }
            }
        }

        val matches = tags["match"].orEmpty() + tags["include"].orEmpty()

        return ScriptMetadata(
            name = tags["name"]?.firstOrNull()?.trim().orEmpty(),
            version = tags["version"]?.firstOrNull()?.trim() ?: "0.0.0",
            description = tags["description"]?.firstOrNull()?.trim().orEmpty(),
            author = tags["author"]?.firstOrNull()?.trim().orEmpty(),
            icon = (tags["icon"] ?: tags["iconurl"] ?: emptyList()).firstOrNull()?.trim().orEmpty(),
            category = (tags["category"] ?: tags["group"] ?: emptyList()).firstOrNull()?.trim().orEmpty(),
            matches = matches.map { it.trim() }.filter { it.isNotEmpty() },
        )
    }
}
