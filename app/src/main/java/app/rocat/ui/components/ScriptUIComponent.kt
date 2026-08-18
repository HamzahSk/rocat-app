package app.rocat.ui.components

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A single renderable unit produced by a script through the global `RoCatUI` object.
 * The canvas screen renders each [ScriptUIComponent] in the order it was added while
 * the script drives the UI (mihon-style extension tab).
 */
sealed class ScriptUIComponent {

    /** An editable text field keyed by a script-chosen [id]; its current value is
     *  collected and forwarded back to the script when a button is pressed. */
    data class Input(
        val id: String,
        val hint: String,
        val value: String = "",
    ) : ScriptUIComponent()

    /** A button that re-invokes the script function named [functionName]. */
    data class Button(
        val label: String,
        val functionName: String,
    ) : ScriptUIComponent()

    /** An image preview rendered with Coil (Tahap 18.1: optional title + download). */
    data class Image(
        val url: String,
        val title: String = "",
        val allowDownload: Boolean = true,
        val headers: Map<String, String> = emptyMap(),
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
    ) : ScriptUIComponent()

    /** A single line appended to the script's log area. */
    data class LogText(
        val text: String,
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
    ) : ScriptUIComponent()

    /** A pretty-printed, syntax-highlighted JSON log card (Tahap 22.2). [dataJson]
     *  is the raw JSON string handed over by the bridge; [allowCopy] toggles the
     *  "Copy JSON" button (with a Toast confirmation). */
    data class JsonLog(
        val dataJson: String,
        val title: String = "",
        val allowCopy: Boolean = true,
    ) : ScriptUIComponent()

    /** A rich-text HTML preview card rendered from [htmlContent] (Tahap 22.2). */
    data class HtmlPreview(
        val htmlContent: String,
        val title: String = "",
    ) : ScriptUIComponent()

    /** An inline audio player card (Tahap 22.2) with Play/Pause, a seek bar and an
     *  optional "download to scrape folder" action. [headers] (Tahap 24.1) are sent by
     *  the ExoPlayer data source when fetching the audio stream. */
    data class Audio(
        val url: String,
        val title: String = "",
        val allowDownload: Boolean = true,
        val headers: Map<String, String> = emptyMap(),
    ) : ScriptUIComponent()

    /** An alert/banner card (Tahap 22.2). [type] is one of `info`/`warning`/`error`/
     *  `success`; unknown values fall back to info. */
    data class Alert(
        val message: String,
        val type: String = "info",
    ) : ScriptUIComponent()

    /** A FlowRow of chips/badges (Tahap 22.2), e.g. genres or episode status. */
    data class BadgeGroup(
        val badges: List<String>,
    ) : ScriptUIComponent()
}

/**
 * A single tile inside a [ScriptUIComponent.Grid]. [title] and [imageUrl] are the two
 * shared fields every grid item is expected to carry; [rawJsonPayload] keeps the full
 * original JSON object (including any extra custom fields) to hand back to the script.
 * [headers] (Tahap 24.1) are the resolved headers used when Coil loads [imageUrl].
 */
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