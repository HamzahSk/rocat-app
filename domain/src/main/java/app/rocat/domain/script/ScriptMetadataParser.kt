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
    /** `key: type: ...` — the trailing `.*` is non-greedy-safe for any extra colons. */
    private val settingLine = Regex("""^\s*(\w+)\s*:\s*(\w+)\s*:\s*(.*)$""")
    /** `name=value` pairs where the value runs until the next `, name=` boundary. */
    private val parameterTokens = Regex("""(\w+)\s*=\s*(.*?)(?=,\s*\w+\s*=|\s*$)""")

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
            settings = (tags["settings"].orEmpty()).mapNotNull { parseSetting(it) },
        )
    }

    /**
     * Parses a single `@settings key: type: param=value, ...` line. The first two
     * colon-separated segments are the key and the type; everything after the second
     * colon is a comma-separated list of `name=value` parameters. `options=a,b,c` is
     * greedy so commas inside the option list never split the value early.
     */
    private fun parseSetting(line: String): ScriptSetting? {
        val match = settingLine.find(line) ?: return null
        val key = match.groupValues[1].trim()
        val type = ScriptSettingType.fromWire(match.groupValues[2])
        if (key.isEmpty()) return null

        val params = linkedMapOf<String, String>()
        parameterTokens.findAll(match.groupValues[3]).forEach { m ->
            val name = m.groupValues[1].trim()
            if (name.isNotEmpty()) params[name] = m.groupValues[2].trim()
        }

        fun doubleParam(name: String): Double? = params[name]?.toDoubleOrNull()
        fun intParam(name: String): Int? = params[name]?.toIntOrNull()

        return ScriptSetting(
            key = key,
            type = type,
            defaultValue = params["default"].orEmpty(),
            label = params["label"].orEmpty(),
            placeholder = params["placeholder"].orEmpty(),
            maxLength = intParam("maxLength"),
            min = doubleParam("min"),
            max = doubleParam("max"),
            step = doubleParam("step"),
            options = params["options"]
                .orEmpty()
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            rows = intParam("rows"),
        )
    }
}
