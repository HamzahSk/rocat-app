package app.rocat.scripting.rhino

import app.rocat.scripting.api.FetchResult
import app.rocat.scripting.api.ScriptResult
import app.rocat.scripting.api.ScriptSettingsBridge
import app.rocat.scripting.api.ScriptUiBridge
import app.rocat.scripting.api.model.DefaultScriptEnvironment
import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Tahap 35 coverage: the per-script `RoCat.settings` API (typed snapshot, set/setTemp/
 * getTemp, history, onSettingsChanged, openSettings) and the new canvas components
 * (text/divider/checkbox/toggle/dropdown/number/colorpicker/textarea/autocomplete/
 * group/layout) dispatched both through `RoCatUI.*` and `RoCat.render(...)`.
 */
class RoCatTahap35Test {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()
    private val engine = RhinoScriptEngine(client)

    private fun script(source: String) = Script(
        id = "test35",
        name = "test35",
        source = source,
    )

    private fun uiEnvironment(ui: ScriptUiBridge) = DefaultScriptEnvironment(
        fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
        ui = ui,
    )

    // --- RoCat.settings ---

    private class FakeSettingsBridge : ScriptSettingsBridge {
        val stored = mutableMapOf(
            "username" to "admin",
            "enabled" to "true",
            "limit" to "10",
        )
        val temp = mutableMapOf<String, String>()
        val history = mutableListOf<String>()
        var changedCount = 0
        var openCount = 0

        override fun snapshot(): Map<String, String> = stored.toMap()

        override fun types(): Map<String, String> = mapOf(
            "username" to "string",
            "enabled" to "boolean",
            "limit" to "number",
        )

        override fun setValue(key: String, value: String) {
            stored[key] = value
            changedCount++
        }

        override fun setTemp(key: String, value: String) {
            temp[key] = value
        }

        override fun getTemp(key: String): String? = temp[key]

        override fun saveHistory(key: String, value: String) {
            history.add("$key=$value")
        }

        override fun clearHistory(key: String) {
            history.removeAll { it.startsWith("$key=") }
        }

        override fun openSettings() {
            openCount++
        }
    }

    @Test
    fun `rocat settings exposes typed snapshot values`() = runBlocking {
        val settings = FakeSettingsBridge()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            settings = settings,
        )
        val source = """
            function main() {
                var u = RoCat.settings.username;
                var e = RoCat.settings.enabled;
                var l = RoCat.settings.limit;
                return (typeof u) + ":" + u + "|" + (typeof e) + ":" + e + "|" + (typeof l) + ":" + (l === 10);
            }
        """.trimIndent()

        val result = engine.execute(script(source), env)

        assertEquals(ScriptResult.Success("string:admin|boolean:true|number:true"), result)
    }

    @Test
    fun `rocat settings get and getAll work`() = runBlocking {
        val settings = FakeSettingsBridge()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            settings = settings,
        )
        val source = """
            function main() {
                var all = RoCat.settings.getAll();
                return RoCat.settings.get("username") + "|" +
                    (RoCat.settings.get("missing") === undefined) + "|" +
                    (all.enabled === true) + "|" + (all.limit === 10);
            }
        """.trimIndent()

        val result = engine.execute(script(source), env)

        assertTrue(result is ScriptResult.Success)
        assertEquals("admin|true|true|true", (result as ScriptResult.Success).value)
    }

    @Test
    fun `rocat settings set persists and fires onSettingsChanged`() = runBlocking {
        val settings = FakeSettingsBridge()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            settings = settings,
        )
        val source = """
            var notified = 0;
            RoCat.onSettingsChanged(function (s) { notified++; });
            function main() {
                RoCat.settings.set("limit", "25");
                var within = RoCat.settings.limit;
                return "notified:" + notified + "|within:" + within;
            }
        """.trimIndent()

        val result = engine.execute(script(source), env)

        assertTrue(result is ScriptResult.Success)
        assertEquals("notified:1|within:25", (result as ScriptResult.Success).value)
        assertEquals("25", settings.stored["limit"])
    }

    @Test
    fun `rocat settings temp storage roundtrips`() = runBlocking {
        val settings = FakeSettingsBridge()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            settings = settings,
        )
        val source = """
            function main() {
                RoCat.settings.setTemp("scratch", "abc");
                return RoCat.settings.getTemp("scratch") + "|" + (RoCat.settings.getTemp("missing") === null);
            }
        """.trimIndent()

        val result = engine.execute(script(source), env)

        assertTrue(result is ScriptResult.Success)
        assertEquals("abc|true", (result as ScriptResult.Success).value)
    }

    @Test
    fun `rocat history helpers forward to the bridge`() = runBlocking {
        val settings = FakeSettingsBridge()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            settings = settings,
        )
        val source = """
            function main() {
                RoCat.saveHistory("query", "one piece");
                RoCat.saveHistory("query", "naruto");
                RoCat.clearHistory("query");
                RoCat.saveHistory("query", "post-clear");
                RoCat.openSettings();
                return "ok";
            }
        """.trimIndent()

        val result = engine.execute(script(source), env)

        assertTrue(result is ScriptResult.Success)
        assertEquals(listOf("query=post-clear"), settings.history)
        assertEquals(1, settings.openCount)
    }

    @Test
    fun `rocat settings is undefined without a settings bridge`() = runBlocking {
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
        )
        val source = "function main() { return typeof RoCat.settings; }"

        val result = engine.execute(script(source), env)

        assertTrue(result is ScriptResult.Success)
        assertEquals("undefined", (result as ScriptResult.Success).value)
    }

    // --- New canvas components ---

    private class Tahap35UiBridge : ScriptUiBridge {
        val calls = mutableListOf<String>()
        override fun addInput(id: String, hint: String) { calls.add("input:$id:$hint") }
        override fun addButton(label: String, functionName: String) { calls.add("button:$label:$functionName") }
        override fun thumbnailPreview(url: String) { calls.add("thumbnail:$url") }
        override fun videoPreview(url: String) { calls.add("video:$url") }
        override fun addImage(url: String, title: String, allowDownload: Boolean, headers: Map<String, String>) {
            calls.add("image:$url:$title:$allowDownload:${headers.toSortedMap()}")
        }
        override fun addVideo(url: String, title: String, isStreamHls: Boolean, allowDownload: Boolean, headers: Map<String, String>) {
            calls.add("videoCard:$url:$title:$isStreamHls:$allowDownload:${headers.toSortedMap()}")
        }
        override fun addGrid(columns: Int, itemsJsonString: String, onClickFunction: String, headers: Map<String, String>) {
            calls.add("grid:$columns:$onClickFunction:$itemsJsonString:${headers.toSortedMap()}")
        }
        override fun log(text: String) { calls.add("log:$text") }
        override fun saveFile(fileName: String, content: String, mimeType: String): String {
            calls.add("save:$fileName:$mimeType")
            return fileName
        }
        override fun clear() { calls.add("clear") }

        override fun addText(content: String, style: String) { calls.add("text:$content:$style") }
        override fun addDivider(thickness: Int, color: String) { calls.add("divider:$thickness:$color") }
        override fun addCheckbox(id: String, label: String, checked: Boolean) { calls.add("checkbox:$id:$label:$checked") }
        override fun addToggle(id: String, label: String, checked: Boolean) { calls.add("toggle:$id:$label:$checked") }
        override fun addDropdown(id: String, options: List<String>, selected: String, label: String) {
            calls.add("dropdown:$id:${options.joinToString(",")}:$selected:$label")
        }
        override fun addNumber(id: String, value: Double?, min: Double?, max: Double?, step: Double?, label: String) {
            calls.add("number:$id:$value:$min:$max:$step:$label")
        }
        override fun addColorPicker(id: String, color: String, label: String) { calls.add("color:$id:$color:$label") }
        override fun addTextArea(id: String, hint: String, rows: Int, value: String) {
            calls.add("textarea:$id:$hint:$rows:$value")
        }
        override fun addAutocomplete(
            id: String,
            hint: String,
            suggestions: List<String>,
            historyKey: String,
            maxHistory: Int,
            showHistory: Boolean,
            showClearHistory: Boolean,
            value: String,
        ) {
            calls.add("autocomplete:$id:$hint:${suggestions.joinToString(",")}:$historyKey:$maxHistory:$showHistory:$showClearHistory:$value")
        }
        override fun addGroup(title: String, collapsed: Boolean, childrenJson: String) {
            calls.add("group:$title:$collapsed:$childrenJson")
        }
        override fun addLayout(
            layout: String,
            columns: Int,
            padding: Int,
            divider: Boolean,
            childrenJson: String,
            flex: Int?,
        ) {
            calls.add("layout:$layout:$columns:$padding:$divider:${flex ?: "null"}:$childrenJson")
        }
    }

    @Test
    fun `rocatui exposes rich controls and layouts`() = runBlocking {
        val ui = Tahap35UiBridge()
        val source = """
            function buildUI() {
                RoCatUI.addText("Hello", "heading");
                RoCatUI.addDivider(2, "#333333");
                RoCatUI.addCheckbox("c1", "Option A", true);
                RoCatUI.addToggle("t1", "ON", false);
                RoCatUI.addDropdown("d1", ["a", "b"], "b", "Pick");
                RoCatUI.addNumber("n1", 5, 1, 10, 0.5, "Count");
                RoCatUI.addColorPicker("cp1", "#ff0000", "Accent");
                RoCatUI.addTextArea("ta1", "Notes", 4, "hello");
                RoCatUI.addAutocomplete("ac1", "Search", ["one", "two"], "hist", 10, true, true, "one");
                RoCatUI.addGroup("Advanced", false, JSON.stringify([{ type: "input", id: "nested", hint: "N" }]));
                RoCatUI.addLayout("row", 0, 8, true, JSON.stringify([{ type: "text", content: "a" }]), 2);
            }
        """.trimIndent()

        val result = engine.invokeNamedFunction(script(source), uiEnvironment(ui), "buildUI", emptyMap())

        assertTrue(result is ScriptResult.Success)
        assertEquals(
            listOf(
                "text:Hello:heading",
                "divider:2:#333333",
                "checkbox:c1:Option A:true",
                "toggle:t1:ON:false",
                "dropdown:d1:a,b:b:Pick",
                "number:n1:5.0:1.0:10.0:0.5:Count",
                "color:cp1:#ff0000:Accent",
                "textarea:ta1:Notes:4:hello",
                "autocomplete:ac1:Search:one,two:hist:10:true:true:one",
                "group:Advanced:false:[{\"type\":\"input\",\"id\":\"nested\",\"hint\":\"N\"}]",
                "layout:row:0:8:true:2:[{\"type\":\"text\",\"content\":\"a\"}]",
            ),
            ui.calls,
        )
    }

    @Test
    fun `rocat render dispatches the tahap 35 descriptor types`() = runBlocking {
        val ui = Tahap35UiBridge()
        val source = """
            function buildUI() {
                RoCat.render([
                    { type: "text", content: "Section", style: "title" },
                    { type: "divider" },
                    { type: "checkbox", id: "x", label: "Check", default: true },
                    { type: "dropdown", id: "dd", options: ["x", "y"], selected: "y" },
                    { type: "number", id: "num", default: 7, min: 0, max: 9 },
                    { type: "autocomplete", id: "ac", suggestions: "a,b", historyKey: "hk" }
                ]);
                RoCat.render({ type: "group", title: "G", children: [{ type: "toggle", id: "tg", label: "T" }] });
            }
        """.trimIndent()

        val result = engine.invokeNamedFunction(script(source), uiEnvironment(ui), "buildUI", emptyMap())

        assertTrue(result is ScriptResult.Success)
        assertEquals(
            listOf(
                "text:Section:title",
                "divider:1:#cccccc",
                "checkbox:x:Check:true",
                "dropdown:dd:x,y:y:",
                "number:num:7.0:0.0:9.0:null:",
                "autocomplete:ac::a,b:hk:20:true:true:",
                "group:G:false:[{\"type\":\"toggle\",\"id\":\"tg\",\"label\":\"T\"}]",
            ),
            ui.calls,
        )
    }
}