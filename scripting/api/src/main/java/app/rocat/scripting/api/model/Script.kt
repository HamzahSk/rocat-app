package app.rocat.scripting.api.model

import kotlinx.serialization.Serializable

/**
 * A user-supplied custom script, conceptually similar to a Tampermonkey userscript
 * or a mihon extension descriptor.
 */
@Serializable
data class Script(
    val id: String,
    val name: String,
    val version: String = "0.0.0",
    val description: String = "",
    /** Script author from the userscript metadata block. */
    val author: String = "",
    /** Icon URL from the userscript metadata block. */
    val icon: String = "",
    /** Grouping label from `@category`/`@group`; blank scripts fall into "Others". */
    val category: String = "",
    /** The JavaScript source code. */
    val source: String,
    /** URL patterns (or host allow-list) this script is allowed to run against. */
    @Serializable(with = StringListSerializer::class)
    val matches: List<String> = emptyList(),
    /** Listed as installed/enabled by the user. */
    val enabled: Boolean = true,
    /** Epoch millis of the last install/update. */
    val updatedAt: Long = 0L,
)