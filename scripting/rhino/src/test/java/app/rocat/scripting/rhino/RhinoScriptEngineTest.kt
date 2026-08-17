package app.rocat.scripting.rhino

import app.rocat.scripting.api.FetchResult
import app.rocat.scripting.api.ScriptResult
import app.rocat.scripting.api.ScriptUiBridge
import app.rocat.scripting.api.model.DefaultScriptEnvironment
import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RhinoScriptEngineTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()
    private val engine = RhinoScriptEngine(client)
    private val environment = DefaultScriptEnvironment(
        fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
    )

    private fun script(source: String) = Script(
        id = "test",
        name = "test",
        source = source,
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `executes main with arguments`() = runBlocking {
        val source = "function main(url) { return 'hello ' + url; }"
        val result = engine.execute(script(source), environment, listOf("world"))

        assertEquals(ScriptResult.Success("hello world"), result)
    }

    @Test
    fun `returns last expression when no main`() = runBlocking {
        val result = engine.execute(script("1 + 41"), environment)

        assertEquals(ScriptResult.Success("42"), result)
    }

    @Test
    fun `fetch returns text body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("hi there"))
        val source = """
            function main(u) {
                var r = fetch(u);
                return r.status + ':' + r.body;
            }
        """.trimIndent()

        val result = engine.execute(script(source), environment, listOf(server.url("/x").toString()))

        assertEquals(ScriptResult.Success("200:hi there"), result)
    }

    @Test
    fun `fetch sends custom headers`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val source = """
            function main(u) {
                var r = fetch(u, { method: 'GET', headers: { 'User-Agent': 'MyAgent', 'X-Test': '1' } });
                return r.status + ':' + r.body;
            }
        """.trimIndent()

        val result = engine.execute(script(source), environment, listOf(server.url("/h").toString()))

        assertEquals(ScriptResult.Success("200:ok"), result)
        val request = server.takeRequest()
        assertEquals("MyAgent", request.getHeader("User-Agent"))
        assertEquals("1", request.getHeader("X-Test"))
    }

    @Test
    fun `fetch parses json in js`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"name":"rocat","n":42}"""))
        val source = """
            function main(u) {
                var r = fetch(u);
                var o = r.json();
                return o.name + ':' + o.n;
            }
        """.trimIndent()

        val result = engine.execute(script(source), environment, listOf(server.url("/j").toString()))

        assertEquals(ScriptResult.Success("rocat:42"), result)
    }

    @Test
    fun `fetch exposes non-2xx status`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))
        val source = """
            function main(u) {
                var r = fetch(u);
                return r.status + ':' + r.ok + ':' + r.body;
            }
        """.trimIndent()

        val result = engine.execute(script(source), environment, listOf(server.url("/404").toString()))

        assertEquals(ScriptResult.Success("404:false:nope"), result)
    }

    @Test
    fun `fetch reports invalid url as error instead of crashing`() = runBlocking {
        val source = """
            function main() {
                var r = fetch('::bad::');
                return r.status + ':' + (r.error ? 'err' : 'noerr');
            }
        """.trimIndent()

        val result = engine.execute(script(source), environment)

        assertTrue(result is ScriptResult.Success)
        assertTrue((result as ScriptResult.Success).value.contains("err"))
    }

    @Test
    fun `watchdog stops infinite loop`() = runBlocking {
        val source = "function main() { while (true) {} }"

        val result = engine.execute(script(source), environment)

        assertTrue(result is ScriptResult.Failure)
        assertTrue((result as ScriptResult.Failure).error.contains("timed out"))
    }

    @Test
    fun `supports es6 arrow functions and template literals`() = runBlocking {
        val source = """
            var double = (x) => x * 2;
            function main() {
                return `result=${'$'}{double(21)}`;
            }
        """.trimIndent()

        val result = engine.execute(script(source), environment)

        assertEquals(ScriptResult.Success("result=42"), result)
    }

    // --- Tahap 8: Script Execution API & Native DOM Bridge (RoCatDOM) ---

    @Test
    fun `invokeFunction calls search and stringifies result`() = runBlocking {
        val html = """
            <html><body>
              <div class="col-12 col-lg-6 p-3 text">
                <a class="linked-name-module__9zptFq__name_underline"
                   href="/series/rccbc2h/turning" title="Click for Series Info">Turning</a>
                <img src="https://cdn.example/turning.jpg"/>
                <div class="mu-markdown-module___SC9hG__mu_markdown">A great manga</div>
                <div class="textsmall"><span class="text-truncate">Action, Adventure, Comedy</span></div>
              </div>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val source = """
            function search(q) {
                var u = serverUrl() + "search?q=" + encodeURIComponent(q);
                var r = fetch(u);
                return RoCatDOM.parse(r.text()).find(".col-12.col-lg-6.p-3.text").map(function (card) {
                    return {
                        title: card.textOf(".linked-name-module__9zptFq__name_underline"),
                        url: card.attrOf('a[title="Click for Series Info"]', "href"),
                        image: card.find("img").length > 0 ? card.find("img")[0].attr("src") : null,
                        genres: card.textOf(".textsmall .text-truncate").split(",").map(function (v) { return v.trim(); })
                    };
                });
            }
            function serverUrl() { return "SERVER_URL"; }
        """.trimIndent()
        val finalSource = source.replace("SERVER_URL", server.url("/").toString())

        val result = engine.invokeFunction(script(finalSource), environment, "search", listOf("turning"))

        assertTrue(result is ScriptResult.Success)
        val json = (result as ScriptResult.Success).value
        assertTrue("json=$json", json.contains("Turning"))
        assertTrue("json=$json", json.contains("/series/rccbc2h/turning"))
        assertTrue("json=$json", json.contains("Action"))
    }

    @Test
    fun `invokeFunction runs detail and parses json-ld via RoCatDOM`() = runBlocking {
        val html = """
            <html><head>
              <script type="application/ld+json">{"name":"Turning","image":"https://cdn.example/t.jpg","genre":["Action"]}</script>
            </head><body>
              <div class="info-box-module__gIhiNW__sCat">Type</div>
              <div class="info-box-module__gIhiNW__sContent">Manga</div>
              <div class="info-box-module__gIhiNW__sCat">Groups Scanlating</div>
              <div class="info-box-module__gIhiNW__sContent"><a href="/groups/abc">GroupA</a></div>
            </body></html>
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(html))

        val source = """
            function detail(url) {
                var r = fetch(url);
                var root = RoCatDOM.parse(r.text());
                var json = {};
                var ld = root.find('script[type="application/ld+json"]');
                if (ld.length > 0 && ld[0].innerHtml) json = JSON.parse(ld[0].innerHtml);
                var data = { title: json.name, cover: json.image };
                var keys = root.find(".info-box-module__gIhiNW__sCat");
                for (var i = 0; i < keys.length; i++) {
                    var box = keys[i].nextElement(".info-box-module__gIhiNW__sContent");
                    if (keys[i].text === "Type") data.type = box.text;
                    if (keys[i].text === "Groups Scanlating") {
                        data.groups = box.find("a").map(function (a) { return { name: a.text, url: a.attr("href") }; });
                    }
                }
                return data;
            }
        """.trimIndent()

        val result = engine.invokeFunction(script(source), environment, "detail", listOf(server.url("/d").toString()))

        assertTrue(result is ScriptResult.Success)
        val json = (result as ScriptResult.Success).value
        assertTrue("json=$json", json.contains("\"title\":\"Turning\""))
        assertTrue("json=$json", json.contains("https://cdn.example/t.jpg"))
        assertTrue("json=$json", json.contains("\"name\":\"GroupA\""))
    }

    @Test
    fun `rocatdom exposes selectText and selectAttr`() = runBlocking {
        val html = "<html><body><h1 class='title'>Hello World</h1><a href='/x'>link</a></body></html>"
        val source = """
            function main(h) {
                return RoCatDOM.selectText(h, ".title") + "|" + RoCatDOM.selectAttr(h, "a", "href");
            }
        """.trimIndent()

        val result = engine.execute(script(source), environment, listOf(html))

        assertEquals(ScriptResult.Success("Hello World|/x"), result)
    }

    @Test
    fun `invokeFunction reports missing function`() = runBlocking {
        val source = "function other() { return 1; }"

        val result = engine.invokeFunction(script(source), environment, "search", listOf("x"))

        assertTrue(result is ScriptResult.Failure)
        val error = (result as ScriptResult.Failure).error
        assertTrue("error=$error", error.contains("search"))
    }

    // --- Tahap 12: Script-Driven UI (RoCatUI bridge) ---

    private class RecordingUiBridge : ScriptUiBridge {
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
        override fun clear() { calls.add("clear") }
        override fun addGrid(columns: Int, itemsJsonString: String, onClickFunction: String, headers: Map<String, String>) {
            calls.add("grid:$columns:$onClickFunction:$itemsJsonString:${headers.toSortedMap()}")
        }
        override fun log(text: String) { calls.add("log:$text") }
        override fun saveFile(fileName: String, content: String, mimeType: String): String {
            calls.add("save:$fileName:$mimeType")
            return fileName
        }
    }

    @Test
    fun `rocatui builds a script-driven ui through the bridge`() = runBlocking {
        val ui = RecordingUiBridge()
        val uiEnvironment = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val source = """
            function buildUI() {
                RoCatUI.clear();
                RoCatUI.addInput("video_url", "Masukkan URL Video / Halaman");
                RoCatUI.addButton("Extract Video", "onExtractClick");
                RoCatUI.thumbnailPreview("https://example.com/thumb.jpg");
                RoCatUI.videoPreview("https://example.com/v.mp4");
                RoCatUI.log("ready");
            }
            function onExtractClick(inputs) {
                RoCatUI.log("got:" + inputs.video_url);
                return { found: inputs.video_url === "data://x", url: inputs.video_url };
            }
        """.trimIndent()

        val buildResult = engine.invokeNamedFunction(script(source), uiEnvironment, "buildUI", emptyMap())
        assertTrue(buildResult is ScriptResult.Success)

        assertEquals(
            listOf(
                "clear",
                "input:video_url:Masukkan URL Video / Halaman",
                "button:Extract Video:onExtractClick",
                "thumbnail:https://example.com/thumb.jpg",
                "video:https://example.com/v.mp4",
                "log:ready",
            ),
            ui.calls,
        )

        ui.calls.clear()
        val clickResult = engine.invokeNamedFunction(
            script(source),
            uiEnvironment,
            "onExtractClick",
            mapOf("video_url" to "data://x"),
        )
        assertTrue(clickResult is ScriptResult.Success)
        val json = (clickResult as ScriptResult.Success).value
        assertTrue("expected found=true, got=$json", json.contains("\"found\":true"))
        assertEquals(listOf("log:got:data://x"), ui.calls)
    }

    @Test
    fun `rocatui handles a void button handler without undefined output`() = runBlocking {
        val ui = RecordingUiBridge()
        val uiEnvironment = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val source = """
            function onExtractClick(inputs) {
                RoCatUI.videoPreview("https://example.com/" + inputs.video_url);
            }
        """.trimIndent()

        val result = engine.invokeNamedFunction(
            script(source),
            uiEnvironment,
            "onExtractClick",
            mapOf("video_url" to "clip.mp4"),
        )

        assertTrue(result is ScriptResult.Success)
        assertEquals("", (result as ScriptResult.Success).value)
        assertEquals(listOf("video:https://example.com/clip.mp4"), ui.calls)
    }

    @Test
    fun `rocatui forwards saveFile calls with a default mime type`() = runBlocking {
        val ui = RecordingUiBridge()
        val uiEnvironment = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val source = """
            function onSave() {
                var uri = RoCatUI.save("result.json", '{"ok": true}');
                return uri;
            }
        """.trimIndent()

        val result = engine.invokeNamedFunction(script(source), uiEnvironment, "onSave", emptyMap())

        assertTrue(result is ScriptResult.Success)
        assertEquals(listOf("save:result.json:text/plain"), ui.calls)
    }

    // --- Tahap 18: Media template cards (addImage / addVideo) ---

    @Test
    fun `rocatui renders an image card with title and download toggle`() = runBlocking {
        val ui = RecordingUiBridge()
        val uiEnvironment = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val source = """
            function buildUI() {
                RoCatUI.addImage("https://example.com/photo.jpg", "Sunset", true);
                RoCatUI.addImage("https://example.com/hidden.jpg", "Locked", false);
            }
        """.trimIndent()

        val result = engine.invokeNamedFunction(script(source), uiEnvironment, "buildUI", emptyMap())

        assertTrue(result is ScriptResult.Success)
        assertEquals(
            listOf(
                "image:https://example.com/photo.jpg:Sunset:true:{}",
                "image:https://example.com/hidden.jpg:Locked:false:{}",
            ),
            ui.calls,
        )
    }

    @Test
    fun `rocatui renders an hls video card with download toggle`() = runBlocking {
        val ui = RecordingUiBridge()
        val uiEnvironment = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val source = """
            function buildUI() {
                RoCatUI.addVideo("https://example.com/stream/master.m3u8", "Live", true, true);
                RoCatUI.addVideo("https://example.com/clip.mp4", "Clip");
            }
        """.trimIndent()

        val result = engine.invokeNamedFunction(script(source), uiEnvironment, "buildUI", emptyMap())

        assertTrue(result is ScriptResult.Success)
        assertEquals(
            listOf(
                "videoCard:https://example.com/stream/master.m3u8:Live:true:true:{}",
                "videoCard:https://example.com/clip.mp4:Clip:false:true:{}",
            ),
            ui.calls,
        )
    }

    // --- Tahap 13: Full Script-Driven Canvas & Grid System ---

    @Test
    fun `rocatui builds a 3-column grid via addGrid`() = runBlocking {
        val ui = RecordingUiBridge()
        val uiEnvironment = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val source = """
            function buildUI() {
                var results = [
                    { id: "1", title: "Manga A", image: "https://example.com/a.jpg" },
                    { id: "2", title: "Manga B", image: "https://example.com/b.jpg" }
                ];
                RoCatUI.addGrid(3, JSON.stringify(results), "openDetail");
            }
        """.trimIndent()

        val result = engine.invokeNamedFunction(script(source), uiEnvironment, "buildUI", emptyMap())

        assertTrue(result is ScriptResult.Success)
        assertEquals(1, ui.calls.size)
        val call = ui.calls.single()
        assertTrue("call=$call", call.startsWith("grid:3:openDetail:"))
        assertTrue("payload missing title", call.contains("Manga A"))
        assertTrue("payload missing custom id", call.contains("\"id\":\"2\""))
    }

    @Test
    fun `rocatui drives the search-to-detail canvas flow`() = runBlocking {
        val ui = RecordingUiBridge()
        val uiEnvironment = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val source = """
            function onLaunch() {
                RoCatUI.clear();
                RoCatUI.addInput("query", "Cari Manga...");
                RoCatUI.addButton("Search", "doSearch");
            }
            function doSearch(inputs) {
                var q = inputs.query;
                if (!q) { RoCatUI.log("Masukkan kata kunci!"); return; }
                RoCatUI.clear();
                RoCatUI.addButton("Back", "onLaunch");
                RoCatUI.log("Hasil pencarian untuk: " + q);
                var results = [
                    { id: "1", title: "Manga A", image: "https://via.placeholder.com/300/FF0000" },
                    { id: "2", title: "Manga B", image: "https://via.placeholder.com/300/00FF00" },
                    { id: "3", title: "Manga C", image: "https://via.placeholder.com/300/0000FF" },
                    { id: "4", title: "Manga D", image: "https://via.placeholder.com/300/FFFF00" }
                ];
                RoCatUI.addGrid(3, JSON.stringify(results), "openDetail");
            }
            function openDetail(itemRaw) {
                var item = JSON.parse(itemRaw);
                RoCatUI.clear();
                RoCatUI.addButton("Back to Search", "onLaunch");
                RoCatUI.thumbnailPreview(item.image);
                RoCatUI.log("Judul: " + item.title);
                RoCatUI.log("ID Manga: " + item.id);
                RoCatUI.addButton("Baca Chapter 1", "readChapter");
            }
            function readChapter() {
                RoCatUI.log("Membuka chapter...");
            }
        """.trimIndent()

        // 1) onLaunch draws the initial search form.
        val launch = engine.invokeNamedFunction(script(source), uiEnvironment, "onLaunch", emptyMap())
        assertTrue(launch is ScriptResult.Success)
        assertEquals(
            listOf(
                "clear",
                "input:query:Cari Manga...",
                "button:Search:doSearch",
            ),
            ui.calls,
        )

        // 2) doSearch redraws the canvas and publishes a 3-column grid.
        ui.calls.clear()
        val searchResult = engine.invokeNamedFunction(script(source), uiEnvironment, "doSearch", mapOf("query" to "opm"))
        assertTrue(searchResult is ScriptResult.Success)
        val gridCall = ui.calls.firstOrNull { it.startsWith("grid:3:") }
        assertNotNull("expected a grid call", gridCall)
        assertTrue("grid=$gridCall", gridCall!!.contains("Manga A"))
        assertTrue("grid=$gridCall", gridCall.contains("openDetail"))

        // 3) openDetail receives the raw item JSON string, parses it and redraws.
        ui.calls.clear()
        val itemJson = """{"id":"1","title":"Manga A","image":"https://via.placeholder.com/300/FF0000"}"""
        val detailResult = engine.invokeFunction(script(source), uiEnvironment, "openDetail", listOf(itemJson))
        assertTrue(detailResult is ScriptResult.Success)
        assertEquals(
            listOf(
                "clear",
                "button:Back to Search:onLaunch",
                "thumbnail:https://via.placeholder.com/300/FF0000",
                "log:Judul: Manga A",
                "log:ID Manga: 1",
                "button:Baca Chapter 1:readChapter",
            ),
            ui.calls,
        )
    }
}
