package app.rocat.scripting.api

/**
 * Callbacks invoked by a user script through the global `RoCatUI` object to build a
 * dynamic, script-driven Compose UI (the mihon-style extension tab). Implementations
 * typically queue new components into the ViewModel that owns the rendered list.
 *
 * MUST be safe to call from any thread: the Rhino engine evaluates scripts on a
 * background coroutine, so implementations are responsible for hopping back to the
 * main thread before touching Compose state.
 */
interface ScriptUiBridge {

    /** Renders an editable text field identified by [id] with the given [hint]. */
    fun addInput(id: String, hint: String)

    /**
     * Renders a button labelled [label] whose click re-invokes the script function
     * named [functionName], passing every input collected so far as a single object.
     */
    fun addButton(label: String, functionName: String)

    /** Renders the image at [url] (loaded with Coil). */
    fun thumbnailPreview(url: String)

    /** Renders a card/button that opens the video at [url] via `Intent.ACTION_VIEW`. */
    fun videoPreview(url: String)

    /**
     * Renders an image preview card (Tahap 18.1). [title] is shown above the image and
     * [allowDownload] toggles the "save to scrape folder" button on the card.
     *
     * [headers] (Tahap 24.1) are extra HTTP headers sent while loading the image with
     * Coil and while downloading it. Many image hosts block hotlinking without a
     * `Referer`; scripts can pass one explicitly. When the map does not contain a
     * `Referer` the engine fills it in automatically (origin of [url] or the script's
     * metadata `@match` base URL).
     */
    fun addImage(
        url: String,
        title: String = "",
        allowDownload: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    )

    /**
     * Renders a video preview card (Tahap 18.2/18.3) with an inline Media3 (ExoPlayer)
     * player and a download button. Set [isStreamHls] to `true` for `.m3u8` streams so
     * the player configures an HLS media source.
     *
     * [headers] (Tahap 24.1) are sent by ExoPlayer for the playlist and every media
     * segment (via `DefaultHttpDataSource.Factory.setDefaultRequestProperties`) and by
     * the downloader. HLS providers frequently return HTTP 403 without a `Referer`.
     */
    fun addVideo(
        url: String,
        title: String = "",
        isStreamHls: Boolean = false,
        allowDownload: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    )

    /** Clears every currently rendered component. */
    fun clear()

    /**
     * Renders a responsive grid of [columns] columns. [itemsJsonString] is a JSON array
     * of objects (each expected to carry at least a `title` and `image`). Tapping a tile
     * re-invokes the script function named [onClickFunction], passing the tapped item's
     * JSON payload as a string argument — the script can then "navigate" by calling
     * [clear] and redrawing the detail UI.
     */
    fun addGrid(
        columns: Int,
        itemsJsonString: String,
        onClickFunction: String,
        headers: Map<String, String> = emptyMap(),
    )

    /** Appends [text] to the script log area. */
    fun log(text: String)

    /**
     * Persists a scrape result. Implementations write [content] as a new file named
     * [fileName] inside the current scrape folder (e.g. via the StorageManager). This is
     * the Tahap 16.1 pipeline: scripts call `RoCatUI.save()` and the app genuinely writes
     * the bytes to device storage through the SAF content resolver.
     *
     * @return the content [android.net.Uri] string of the written file, or an empty
     *   string when the write failed.
     */
    fun saveFile(fileName: String, content: String, mimeType: String = "text/plain"): String

    // --- Tahap 22.2: expanded UI template cards ---

    /**
     * Renders a pretty-printed, syntax-highlighted JSON log card (Tahap 22.2).
     * [dataJson] can be a raw JSON string or (through the Rhino bridge) any
     * JSON-serialisable object/array — the bridge normalises it to a string. When
     * [allowCopy] is true the card exposes a "Copy JSON" button with a Toast.
     */
    fun addJsonLog(dataJson: String, title: String = "", allowCopy: Boolean = true) {
        // Default no-op keeps every existing implementation / unit-test recorder valid.
    }

    /**
     * Renders a rich-text HTML preview card (Tahap 22.2). The content is converted
     * with `android.text.Html.fromHtml` so bold/italic/links/lists render inline
     * without a heavy WebView. Tapping a link opens it in the system browser.
     */
    fun addHtmlPreview(htmlContent: String, title: String = "") {
        // Default no-op.
    }

    /**
     * Renders an inline audio player card (Tahap 22.2) built on Media3/ExoPlayer with
     * Play/Pause, a seekable progress bar, and (when [allowDownload]) a "download to
     * scrape folder" button wired to the SAF pipeline.
     */
    fun addAudio(
        url: String,
        title: String = "",
        allowDownload: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ) {
        // Default no-op.
    }

    /**
     * Renders an alert/banner card (Tahap 22.2). [type] is one of `"info"`,
     * `"warning"`, `"error"` or `"success"` (anything else falls back to info).
     * Unknown / null types are tolerated by the bridge.
     */
    fun addAlert(message: String, type: String = "info") {
        // Default no-op.
    }

    /**
     * Renders a FlowRow of chips/badges (Tahap 22.2). [badgesJson] is a JSON array of
     * strings; the Rhino bridge also accepts a native JS array and serialises it.
     */
    fun addBadgeGroup(badgesJson: String) {
        // Default no-op.
    }

    /**
     * Native Base64 → UTF-8 decode (Tahap 20.1). Scripts call this through
     * `RoCatUI.decodeBase64(str)` instead of re-implementing a decoder in JavaScript,
     * so the heavy lifting happens in native code (Android's `android.util.Base64`)
     * and is much faster on large iframe blobs.
     *
     * The default implementation is a dependency-free `java.util.Base64` decoder that
     * is safe on both Android (API 26+) and plain JVM unit tests. The app overrides it
     * with `android.util.Base64.decode(input, Base64.DEFAULT)` for the true native path.
     *
     * Padding/format errors never throw: on failure an empty string is returned so a
     * script can skip the (unparseable) mirror instead of crashing.
     */
    fun decodeBase64(input: String): String {
        val cleaned = input.trim().filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return ""
        return try {
            val padded = if (cleaned.length % 4 != 0) {
                cleaned + "=".repeat(4 - (cleaned.length % 4))
            } else {
                cleaned
            }
            String(java.util.Base64.getDecoder().decode(padded), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    // --- Tahap 35: flexible canvas layouts & rich input controls ---

    /** Renders static [content] text with a named [style] (`heading`/`title`/`body`/`caption`). */
    fun addText(content: String, style: String = "body") {
        // Default no-op keeps existing implementations / test recorders valid.
    }

    /** Renders a horizontal separator line of [thickness] px in [color]. */
    fun addDivider(thickness: Int = 1, color: String = "#cccccc") {
        // Default no-op.
    }

    /** Renders a checkbox identified by [id] with [label]. */
    fun addCheckbox(id: String, label: String, checked: Boolean = false) {
        // Default no-op.
    }

    /** Renders an ON/OFF switch identified by [id] with [label]. */
    fun addToggle(id: String, label: String, checked: Boolean = false) {
        // Default no-op.
    }

    /** Renders a dropdown identified by [id] offering [options], initially [selected]. */
    fun addDropdown(id: String, options: List<String>, selected: String, label: String = "") {
        // Default no-op.
    }

    /** Renders a number field identified by [id] with optional [min]/[max]/[step]. */
    fun addNumber(id: String, value: Double?, min: Double?, max: Double?, step: Double?, label: String = "") {
        // Default no-op.
    }

    /** Renders a color picker identified by [id] starting at [color] (hex string). */
    fun addColorPicker(id: String, color: String, label: String = "") {
        // Default no-op.
    }

    /** Renders a multi-line text area identified by [id]. */
    fun addTextArea(id: String, hint: String, rows: Int = 3, value: String = "") {
        // Default no-op.
    }

    /**
     * Renders an autocomplete text field identified by [id]. [suggestions] are static
     * script-provided hints; a non-blank [historyKey] enables persisted per-script
     * history suggestions.
     */
    fun addAutocomplete(
        id: String,
        hint: String,
        suggestions: List<String>,
        historyKey: String,
        maxHistory: Int,
        showHistory: Boolean,
        showClearHistory: Boolean,
        value: String = "",
    ) {
        // Default no-op.
    }

    /** Renders a titled, collapsible [ScriptUIComponent.Group] from [childrenJson]. */
    fun addGroup(title: String, collapsed: Boolean, childrenJson: String) {
        // Default no-op.
    }

    /** Renders a flexible [ScriptUIComponent.Layout] (row/column/grid) from [childrenJson]. */
    fun addLayout(
        layout: String,
        columns: Int,
        padding: Int,
        divider: Boolean,
        childrenJson: String,
        flex: Int? = null,
    ) {
        // Default no-op.
    }
}