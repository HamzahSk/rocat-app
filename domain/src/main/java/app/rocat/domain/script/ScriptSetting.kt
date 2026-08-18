package app.rocat.domain.script

/**
 * A single user-editable setting declared by a script through a `@settings` metadata
 * tag (Tahap 35). Scripts describe *what* they want (a typed, labeled control) and the
 * app renders the matching widget and persists the value — no code editing required.
 *
 * Metadata syntax (one `@settings` line per setting):
 *
 * ```
 * @settings  key: type: default=..., label=..., placeholder=..., min=..., max=...,
 *            step=..., options=a,b,c, maxLength=..., rows=...
 * ```
 *
 * `type` is one of [ScriptSettingType]; every other parameter is optional. `options` is
 * a comma-separated list used by [ScriptSettingType.SELECT].
 */
data class ScriptSetting(
    val key: String,
    val type: ScriptSettingType,
    /** Raw string form of the declared default (parsed/validated per [type]). */
    val defaultValue: String = "",
    val label: String = "",
    val placeholder: String = "",
    val maxLength: Int? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val options: List<String> = emptyList(),
    val rows: Int? = null,
) {
    /** Human-readable label, falling back to the setting key. */
    val displayLabel: String get() = label.ifBlank { key }

    /**
     * The default value coerced to a canonical string form: booleans become
     * `"true"`/`"false"`, numbers their decimal representation and everything else the
     * raw string. Unknown/broken defaults fall back to an empty string.
     */
    val normalizedDefault: String
        get() = when (type) {
            ScriptSettingType.BOOLEAN -> defaultValue.trim().let {
                if (it.equals("true", ignoreCase = true) || it == "1") "true" else "false"
            }
            ScriptSettingType.NUMBER -> defaultValue.trim().toDoubleOrNull()?.let { n ->
                if (n.isFinite()) { if (n % 1.0 == 0.0) n.toLong().toString() else n.toString() } else ""
            } ?: ""
            else -> defaultValue.trim()
        }
}

/** The supported setting value types rendered by the settings page. */
enum class ScriptSettingType(val wire: String) {
    STRING("string"),
    PASSWORD("password"),
    BOOLEAN("boolean"),
    NUMBER("number"),
    SELECT("select"),
    MULTILINE("multiline"),
    COLOR("color"),
    EMAIL("email"),
    ;

    companion object {
        /** Resolves a `@settings` type token; unknown types fall back to [STRING]. */
        fun fromWire(token: String): ScriptSettingType =
            entries.firstOrNull { it.wire == token.trim().lowercase() } ?: STRING
    }
}