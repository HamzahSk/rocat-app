package app.rocat.domain.script

/**
 * Parsed `==UserScript==` metadata block, mirroring Tampermonkey/Greasemonkey headers
 * and mihon extension descriptors.
 */
data class ScriptMetadata(
    val name: String = "",
    val version: String = "0.0.0",
    val description: String = "",
    val author: String = "",
    val icon: String = "",
    /**
     * Optional grouping label read from `@category` (or legacy `@group`). Scripts without
     * one are grouped under a localized "Others" bucket in the UI.
     */
    val category: String = "",
    /** Merged `@match` + `@include` allow-list. */
    val matches: List<String> = emptyList(),
    /**
     * User-editable settings declared via `@settings` metadata tags (Tahap 35). Each
     * entry describes a typed, labeled control the app renders in the per-script
     * settings page and exposes to the script through `RoCat.settings`.
     */
    val settings: List<ScriptSetting> = emptyList(),
)
