package app.rocat.ui.components

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * A single renderable unit produced by a script through the global `RoCatUI` object.
 * The canvas screen renders each [ScriptUIComponent] in the order it was added while
 * the script drives the UI (mihon-style extension tab).
 *
 * Tahap 35 adds flexible layout (`Layout`, `Group`), static pieces (`Text`, `Divider`)
 * and a rich set of input controls ([Field] subclasses: checkbox, toggle, dropdown,
 * number, color picker, textarea, autocomplete) alongside the classic [Input].
 * Every component carries an optional [flex] weight (1/2/3...) used when it sits inside
 * a `row`/`grid` layout so scripts can control how much horizontal space it takes.
 */
sealed class ScriptUIComponent {

    /** Optional layout weight (1/2/3...) when this component sits inside a `row`/`grid`
     *  layout; `null` means the default weight (1) is used. */
    abstract val flex: Int?

    /** An editable text field keyed by a script-chosen [id]; its current value is
     *  collected and forwarded back to the script when a button is pressed. */
    data class Input(
        override val id: String,
        val hint: String,
        val value: String = "",
        override val flex: Int? = null,
    ) : Field()

    /** A button that re-invokes the script function named [functionName]. */
    data class Button(
        val label: String,
        val functionName: String,
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /** An image preview rendered with Coil (Tahap 18.1: optional title + download). */
    data class Image(
        val url: String,
        val title: String = "",
        val allowDownload: Boolean = true,
        val headers: Map<String, String> = emptyMap(),
        val seamless: Boolean = false,
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /**
     * A video preview card (Tahap 18.2/18.3): plays [url] inline with the in-app
     * Media3 player (HLS when [isStreamHls]) and can download the file.
     */
    data class Video(
        val url: String,
        val title: String = "",
        val isStreamHls: Boolean = false,
        val allowDownload: Boolean = true,
        val headers: Map<String, String> = emptyMap(),
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /** A single line appended to the script's log area. */
    data class LogText(
        val text: String,
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /**
     * A responsive media grid (mihon-style search results). Tapping a tile re-invokes
     * the script's [onClickFunction] passing the item's raw JSON payload as a string.
     * [headers] (Tahap 24.1) are sent when loading every tile thumbnail with Coil so
     * hotlink-protected covers load correctly.
     */
    data class Grid(
        val columns: Int,
        val items: List<GridItem>,
        val onClickFunction: String,
        val headers: Map<String, String> = emptyMap(),
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /** A pretty-printed, syntax-highlighted JSON log card (Tahap 22.2). [dataJson]
     *  is the raw JSON string handed over by the bridge; [allowCopy] toggles the
     *  "Copy JSON" button (with a Toast confirmation). */
    data class JsonLog(
        val dataJson: String,
        val title: String = "",
        val allowCopy: Boolean = true,
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /** A rich-text HTML preview card rendered from [htmlContent] (Tahap 22.2). */
    data class HtmlPreview(
        val htmlContent: String,
        val title: String = "",
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /** An inline audio player card (Tahap 22.2) with Play/Pause, a seek bar and an
     *  optional "download to scrape folder" action. [headers] (Tahap 24.1) are sent by
     *  the ExoPlayer data source when fetching the audio stream. */
    data class Audio(
        val url: String,
        val title: String = "",
        val allowDownload: Boolean = true,
        val headers: Map<String, String> = emptyMap(),
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /** An alert/banner card (Tahap 22.2). [type] is one of `info`/`warning`/`error`/
     *  `success`; unknown values fall back to info. */
    data class Alert(
        val message: String,
        val type: String = "info",
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /** A FlowRow of chips/badges (Tahap 22.2), e.g. genres or episode status. */
    data class BadgeGroup(
        val badges: List<String>,
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /** Static text (Tahap 35). [style] is `heading`/`title`/`body`/`caption`. */
    data class Text(
        val content: String,
        val style: String = "body",
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /** A horizontal separator line (Tahap 35). */
    data class Divider(
        val thickness: Int = 1,
        val color: String = "#cccccc",
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /** Base for every user-editable control rendered by the canvas. Values are
     *  collected by id and forwarded to the script when a button is pressed. */
    sealed class Field : ScriptUIComponent() {
        abstract val id: String
    }

    /** A checkbox with a label (Tahap 35). */
    data class Checkbox(
        override val id: String,
        val label: String = "",
        val checked: Boolean = false,
        override val flex: Int? = null,
    ) : Field()

    /** An ON/OFF switch (Tahap 35). */
    data class Toggle(
        override val id: String,
        val label: String = "",
        val checked: Boolean = false,
        override val flex: Int? = null,
    ) : Field()

    /** A dropdown of [options] (Tahap 35). */
    data class Dropdown(
        override val id: String,
        val label: String = "",
        val options: List<String> = emptyList(),
        val selected: String = "",
        override val flex: Int? = null,
    ) : Field()

    /** A number field with min/max/step (Tahap 35). */
    data class Number(
        override val id: String,
        val label: String = "",
        val value: Double? = null,
        val min: Double? = null,
        val max: Double? = null,
        val step: Double? = null,
        override val flex: Int? = null,
    ) : Field()

    /** A color picker (hex string, Tahap 35). */
    data class ColorPicker(
        override val id: String,
        val label: String = "",
        val color: String = "#000000",
        override val flex: Int? = null,
    ) : Field()

    /** A multi-line text area (Tahap 35). */
    data class TextArea(
        override val id: String,
        val hint: String = "",
        val value: String = "",
        val rows: Int = 3,
        override val flex: Int? = null,
    ) : Field()

    /**
     * A text field with suggestions (Tahap 35). When [historyKey] is set the canvas
     * saves/loads typed values from the script's input-history bucket, so users get
     * autocomplete from their own previous queries.
     */
    data class Autocomplete(
        override val id: String,
        val hint: String = "",
        val value: String = "",
        val suggestions: List<String> = emptyList(),
        val historyKey: String = "",
        val maxHistory: Int = 20,
        val showHistory: Boolean = true,
        val showClearHistory: Boolean = true,
        override val flex: Int? = null,
    ) : Field()

    /** A titled, collapsible group of child components (Tahap 35). */
    data class Group(
        val title: String = "",
        val collapsed: Boolean = false,
        val children: List<ScriptUIComponent> = emptyList(),
        override val flex: Int? = null,
    ) : ScriptUIComponent()

    /**
     * A flexible layout container (Tahap 35). [layout] is `row`, `column` or `grid`;
     * [columns] applies to grids, [padding] (px) pads the container, [divider] draws a
     * separator between children and [flex] gives the container a weight inside its
     * parent row/grid.
     */
    data class Layout(
        val layout: String = "column",
        val columns: Int = 2,
        val padding: Int = 0,
        val divider: Boolean = false,
        override val flex: Int? = null,
        val children: List<ScriptUIComponent> = emptyList(),
        val margin: Int = 16,
        val spacing: Int = 8,
        val align: String = "start",
    ) : ScriptUIComponent()
}

/** The canonical string value of a field, or null when it has no usable value yet. */
fun ScriptUIComponent.Field.fieldValue(): String? = when (this) {
    is ScriptUIComponent.Input -> value.ifBlank { null }
    is ScriptUIComponent.Checkbox -> checked.toString()
    is ScriptUIComponent.Toggle -> checked.toString()
    is ScriptUIComponent.Dropdown -> selected.ifBlank { null }
    is ScriptUIComponent.Number -> value?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
    is ScriptUIComponent.ColorPicker -> color
    is ScriptUIComponent.TextArea -> value.ifBlank { null }
    is ScriptUIComponent.Autocomplete -> value.ifBlank { null }
}

/** The flex weight of a component, defaulting to 1 inside row/grid layouts. */
val ScriptUIComponent.flexWeight: Int
    get() = when (this) {
        is ScriptUIComponent.Input -> flex
        is ScriptUIComponent.Button -> flex
        is ScriptUIComponent.Image -> flex
        is ScriptUIComponent.Video -> flex
        is ScriptUIComponent.LogText -> flex
        is ScriptUIComponent.Grid -> flex
        is ScriptUIComponent.JsonLog -> flex
        is ScriptUIComponent.HtmlPreview -> flex
        is ScriptUIComponent.Audio -> flex
        is ScriptUIComponent.Alert -> flex
        is ScriptUIComponent.BadgeGroup -> flex
        is ScriptUIComponent.Text -> flex
        is ScriptUIComponent.Divider -> flex
        is ScriptUIComponent.Checkbox -> flex
        is ScriptUIComponent.Toggle -> flex
        is ScriptUIComponent.Dropdown -> flex
        is ScriptUIComponent.Number -> flex
        is ScriptUIComponent.ColorPicker -> flex
        is ScriptUIComponent.TextArea -> flex
        is ScriptUIComponent.Autocomplete -> flex
        is ScriptUIComponent.Group -> flex
        is ScriptUIComponent.Layout -> flex
    } ?: 1

/** A single tile inside a [ScriptUIComponent.Grid]. [title] and [imageUrl] are the two
 *  shared fields every grid item is expected to carry; [rawJsonPayload] keeps the full
 *  original JSON object (including any extra custom fields) to hand back to the script.
 *  [headers] (Tahap 24.1) are the resolved headers used when Coil loads [imageUrl]. */
data class GridItem(
    val title: String,
    val imageUrl: String,
    val rawJsonPayload: String,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Best-effort parser for the JSON array passed to `RoCatUI.addGrid(...)`. Produces a
 * [ScriptUIComponent.Grid] (or null when the payload is not a usable JSON array) whose
 * tiles keep their original JSON so clicking can forward the exact object back to JS.
 */
fun parseGrid(
    columns: Int,
    itemsJson: String,
    onClickFunction: String,
    headers: Map<String, String> = emptyMap(),
): ScriptUIComponent.Grid? {
    val elements = try {
        Json.parseToJsonElement(itemsJson) as? JsonArray ?: return null
    } catch (e: Exception) {
        return null
    }
    val items = elements.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        GridItem(
            title = (obj["title"] as? JsonPrimitive)?.content.orEmpty(),
            imageUrl = (obj["image"] as? JsonPrimitive)?.content?.trim().orEmpty(),
            rawJsonPayload = element.toString(),
            headers = headers,
        )
    }
    if (items.isEmpty()) return null
    return ScriptUIComponent.Grid(columns.coerceAtLeast(1), items, onClickFunction, headers)
}

/**
 * Best-effort parser for the badges JSON array passed to `RoCatUI.addBadgeGroup(...)`.
 * Returns a [ScriptUIComponent.BadgeGroup] (or null when the payload is not a usable
 * JSON array of non-blank strings).
 */
fun parseBadgeGroup(badgesJson: String): ScriptUIComponent.BadgeGroup? {
    val elements = try {
        Json.parseToJsonElement(badgesJson) as? JsonArray ?: return null
    } catch (e: Exception) {
        return null
    }
    val badges = elements.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }
        .filter { it.isNotEmpty() }
    if (badges.isEmpty()) return null
    return ScriptUIComponent.BadgeGroup(badges)
}

/**
 * Tahap 35: parses a JSON descriptor array (as passed to `RoCatUI.addLayout` /
 * `RoCatUI.addGroup`) into native [ScriptUIComponent]s, recursing into nested
 * layout/group children. Unknown or malformed entries are skipped so a bad descriptor
 * never breaks a script or the canvas.
 */
fun parseComponents(json: String): List<ScriptUIComponent> {
    if (json.isBlank()) return emptyList()
    val element = try {
        Json.parseToJsonElement(json)
    } catch (_: Exception) {
        return emptyList()
    }
    return when (element) {
        is JsonArray -> element.mapNotNull { parseComponent(it) }
        else -> listOfNotNull(parseComponent(element))
    }
}

/** Parses a single component descriptor object; returns null when unusable. */
fun parseComponent(element: JsonElement): ScriptUIComponent? {
    val obj = element as? JsonObject ?: return null
    val type = (obj["type"] as? JsonPrimitive)?.content ?: return null
    fun str(key: String, default: String = ""): String = (obj[key] as? JsonPrimitive)?.contentOrNull ?: default
    fun bool(key: String, default: Boolean = false): Boolean = when (val v = obj[key] as? JsonPrimitive) {
        null -> default
        else -> v.contentOrNull?.trim().let { it == "true" || it == "1" } ?: default
    }
    fun int(key: String, default: Int): Int = (obj[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: default
    fun double(key: String): Double? = (obj[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
    fun children(): List<ScriptUIComponent> = when (val c = obj["children"]) {
        is JsonArray -> c.mapNotNull { parseComponent(it) }
        else -> emptyList()
    }
    fun list(key: String): List<String> = when (val v = obj[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        is JsonPrimitive -> v.contentOrNull.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }
    val flex: Int? = int("flex", 0).takeIf { it > 0 }

    return when (type) {
        "text" -> ScriptUIComponent.Text(str("content").ifBlank { str("text") }, str("style", "body"), flex)
        "divider" -> ScriptUIComponent.Divider(int("thickness", 1), str("color", "#cccccc"), flex)
        "input" -> ScriptUIComponent.Input(str("id"), str("hint"), flex = flex)
        "checkbox" -> ScriptUIComponent.Checkbox(str("id"), str("label", str("id")), bool("default") || bool("checked"), flex)
        "toggle" -> ScriptUIComponent.Toggle(str("id"), str("label", str("id")), bool("default") || bool("checked"), flex)
        "dropdown" -> ScriptUIComponent.Dropdown(
            str("id"),
            str("label", str("id")),
            list("options"),
            str("default", str("selected")),
            flex,
        )
        "number" -> ScriptUIComponent.Number(
            str("id"),
            str("label", str("id")),
            double("default") ?: double("value"),
            double("min"),
            double("max"),
            double("step"),
            flex,
        )
        "colorpicker" -> ScriptUIComponent.ColorPicker(str("id"), str("label", str("id")), str("default", "#000000"), flex)
        "textarea" -> ScriptUIComponent.TextArea(str("id"), str("hint"), str("default"), int("rows", 3), flex)
        "autocomplete" -> ScriptUIComponent.Autocomplete(
            str("id"),
            str("hint"),
            str("default"),
            list("suggestions"),
            str("historyKey"),
            int("maxHistory", 20),
            bool("showHistory", true),
            bool("showClearHistory", true),
            flex,
        )
        "group" -> ScriptUIComponent.Group(str("title"), bool("collapsed"), children(), flex)
        "layout" -> ScriptUIComponent.Layout(
            str("layout", "column"),
            int("columns", 2),
            int("padding", 0),
            bool("divider"),
            flex,
            children(),
            int("margin", 16),
            int("spacing", 8),
            str("align", "start"),
        )
        "button" -> ScriptUIComponent.Button(str("label"), str("fn").ifBlank { str("function") }.ifBlank { str("onClick") }, flex)
        "alert" -> ScriptUIComponent.Alert(str("message").ifBlank { str("text") }, str("level", "info"), flex)
        "badges" -> parseBadgeGroup(JsonArray(list("badges").map { JsonPrimitive(it) }).toString())
        "grid" -> parseGrid(
            int("columns", 3),
            (obj["items"] as? JsonArray)?.toString() ?: str("items"),
            str("onClick").ifBlank { str("fn") },
        )
        "log" -> ScriptUIComponent.LogText(str("text").ifBlank { str("message") }, flex)
        "json" -> {
            val data = obj["data"] ?: obj["json"] ?: JsonPrimitive("")
            ScriptUIComponent.JsonLog(
                if (data is JsonPrimitive) data.contentOrNull.orEmpty() else data.toString(),
                str("title"),
                bool("copy", true),
                flex,
            )
        }
        "html" -> ScriptUIComponent.HtmlPreview(str("html").ifBlank { str("content") }, str("title"), flex)
        "audio" -> ScriptUIComponent.Audio(str("url"), str("title"), bool("download", true), emptyMap(), flex)
        "video" -> ScriptUIComponent.Video(str("url"), str("title"), bool("hls"), bool("download", true), emptyMap(), flex)
        "image" -> ScriptUIComponent.Image(
            url = str("url").ifBlank { str("src") },
            title = str("title"),
            allowDownload = bool("download", true),
            seamless = bool("seamless"),
            flex = flex,
        )
        else -> null
    }
}
