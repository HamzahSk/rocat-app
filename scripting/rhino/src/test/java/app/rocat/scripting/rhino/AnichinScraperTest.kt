package app.rocat.scripting.rhino

import app.rocat.scripting.api.FetchResult
import app.rocat.scripting.api.ScriptResult
import app.rocat.scripting.api.ScriptUiBridge
import app.rocat.scripting.api.model.DefaultScriptEnvironment
import app.rocat.scripting.api.model.Script
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tahap 19 — drives the real `scrape_anichin.js` from the repo root through the full
 * RoCat canvas flow against canned Anichin HTML:
 *
 *   onLaunch -> doSearch -> openDetail -> openEpisode (HLS)
 *
 * Verifies the script parses search/detail/episode pages with `RoCatDOM`, decodes the
 * base64-encoded mirror iframes, selects the best VALID HLS variant from the (malformed)
 * master m3u8 and hands it to `RoCatUI.addVideo(..., isStreamHls = true)`.
 */
class AnichinScraperTest {

    private val scriptSource: String by lazy {
        val candidates = listOf(
            "../../../scrape_anichin.js", // working dir = rocat-app/scripting/rhino
            "../../scrape_anichin.js",    // working dir = rocat-app/scripting
            "../scrape_anichin.js",       // working dir = rocat-app
            "scrape_anichin.js",          // working dir = repo root
        )
        val file = candidates.asSequence()
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("scrape_anichin.js not found (user.dir=${System.getProperty("user.dir")})")
        file.readText()
    }

    private fun script() = Script(id = "anichin", name = "Anichin Scraper", source = scriptSource)

    private class UiRecorder : ScriptUiBridge {
        val calls = mutableListOf<String>()
        var decodeBase64Calls = 0
        override fun addInput(id: String, hint: String) { calls += "input:$id:$hint" }
        override fun addButton(label: String, functionName: String) { calls += "button:$label:$functionName" }
        override fun thumbnailPreview(url: String) { calls += "thumb:$url" }
        override fun videoPreview(url: String) { calls += "video:$url" }
        override fun addImage(url: String, title: String, allowDownload: Boolean, headers: Map<String, String>) {
            calls += "image:$url:$title:$allowDownload:${headers.toSortedMap()}"
        }
        override fun addVideo(url: String, title: String, isStreamHls: Boolean, allowDownload: Boolean, headers: Map<String, String>) {
            calls += "videoCard:$url:$title:$isStreamHls:$allowDownload:${headers.toSortedMap()}"
        }
        override fun clear() { calls += "clear" }
        override fun addGrid(columns: Int, itemsJsonString: String, onClickFunction: String, headers: Map<String, String>) {
            calls += "grid:$columns:$onClickFunction:$itemsJsonString:${headers.toSortedMap()}"
        }
        override fun log(text: String) { calls += "log:$text" }
        override fun saveFile(fileName: String, content: String, mimeType: String): String {
            calls += "save:$fileName:$mimeType"
            return fileName
        }
        override fun decodeBase64(input: String): String {
            decodeBase64Calls++
            // Record so the test can prove the script reached the native bridge.
            calls += "decodeBase64:$input"
            return super.decodeBase64(input)
        }
    }

    private val homeHtml = """
        <html><body>
          <div class="bixbox">
            <div class="listupd">
              <article class="bs"><div class="bsx">
                <a href="https://anichin.cafe/seri/perfect-world/">
                  <div class="limit"><img src="https://anichin.cafe/wp-content/uploads/2021/04/Perfect-World.webp"/></div>
                  <div class="tt"><h2>Perfect World</h2></div>
                </a>
              </div></article>
            </div>
          </div>
        </body></html>
    """.trimIndent()

    private val searchHtml = """
        <html><body>
          <div class="bixbox">
            <div class="releases"><h1>Search 'perfect world'</h1></div>
            <div class="listupd">
              <article class="bs"><div class="bsx">
                <a href="https://anichin.cafe/seri/perfect-world/">
                  <div class="limit"><img src="https://anichin.cafe/wp-content/uploads/2021/04/Perfect-World.webp"/></div>
                  <div class="tt"><h2>Perfect World</h2></div>
                </a>
              </div></article>
              <article class="bs"><div class="bsx">
                <a href="https://anichin.cafe/seri/perfect-world-movie/">
                  <div class="limit"><img src="https://anichin.cafe/wp-content/uploads/2021/04/PW-Movie.webp"/></div>
                  <div class="tt"><h2>Perfect World Movie</h2></div>
                </a>
              </div></article>
            </div>
          </div>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><body>
          <div class="bixbox animefull">
            <div class="bigcontent">
              <div class="thumb"><img src="https://anichin.cafe/wp-content/uploads/2021/04/Perfect-World.webp"/></div>
            </div>
          </div>
          <h1 class="entry-title">Perfect World</h1>
          <div class="bixbox synp"><div class="entry-content"><p>Donghua Perfect World diadaptasi dari novel.</p></div></div>
          <div class="eplister">
            <ul>
              <li data-index="0"><a href="https://anichin.cafe/perfect-world-episode-281-subtitle-indonesia/">
                <div class="epl-num">281</div><div class="epl-date">August 6, 2026</div></a></li>
              <li data-index="1"><a href="https://anichin.cafe/perfect-world-episode-280-subtitle-indonesia/">
                <div class="epl-num">280</div><div class="epl-date">July 30, 2026</div></a></li>
            </ul>
          </div>
        </body></html>
    """.trimIndent()

    private val masterPlaylist = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-STREAM-INF:BANDWIDTH=676000,RESOLUTION=640x360
        https://cdn.example/360.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1052000,RESOLUTION=854x480
        https://cdn.example/480.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2137000,RESOLUTION=1280x720
        https://cdn.example/720.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=4096000,RESOLUTION=1920x1080
    """.trimIndent()

    /** Builds the `select.mirror` block like the live Anichin episode pages. */
    private fun episodeHtml(): String {
        fun b64(s: String) = java.util.Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
        val premium = """<iframe width="100%" height="100%" src="https://anichin.stream/?id=test123" frameborder="0" allowfullscreen></iframe>"""
        val okru = """<iframe width="100%" height="100%" src="https://ok.ru/videoembed/12345" frameborder="0" allowfullscreen></iframe>"""
        return """
            <html><body>
              <div class="video-content">
                <select class="mirror" name="mirror" onchange="loadMi(this);">
                  <option value="">Select Video Server</option>
                  <option value="${b64(premium)}" data-index="1">Premium 1</option>
                  <option value="${b64(okru)}" data-index="2">OK.ru</option>
                </select>
              </div>
            </body></html>
        """.trimIndent()
    }

    /**
     * The script's `fetch()` always goes through the OkHttp client (the bridge does not
     * read DefaultScriptEnvironment.fetchImpl), so we mock at the OkHttp layer: any
     * request to anichin.cafe / anichin.stream is short-circuited with canned HTML.
     */
    private fun anichinClient(): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val body = when {
                url.contains("anichin.stream/hls/") -> masterPlaylist
                url.contains("anichin.cafe") && url.contains("-episode-") -> episodeHtml()
                url.contains("anichin.cafe/seri/") -> detailHtml
                url.contains("anichin.cafe/page/1?s=") -> searchHtml
                else -> homeHtml
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("text/html; charset=utf-8".toMediaType()))
                .build()
        }
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @Test
    fun `script compiles and drives search-to-video canvas flow`() = runBlocking {
        val engine = RhinoScriptEngine(anichinClient())
        val ui = UiRecorder()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val s = script()

        // 1) onLaunch draws the search form and a home grid.
        val launch = engine.invokeNamedFunction(s, env, "onLaunch", emptyMap())
        assertTrue("onLaunch failed: $launch", launch is ScriptResult.Success)
        assertTrue(ui.calls.any { it.startsWith("input:query:") })
        assertTrue(ui.calls.any { it.startsWith("button:🔍 Cari:doSearch") })
        val homeGrid = ui.calls.firstOrNull { it.startsWith("grid:3:openDetail:") }
        assertNotNull("expected a home grid", homeGrid)
        assertTrue("home grid missing item", homeGrid!!.contains("Perfect World"))
        assertTrue("home grid must carry the raw url", homeGrid.contains("perfect-world"))

        // 2) doSearch publishes a 3-column grid of results.
        ui.calls.clear()
        val search = engine.invokeNamedFunction(s, env, "doSearch", mapOf("query" to "perfect"))
        assertTrue("doSearch failed: $search", search is ScriptResult.Success)
        val searchGrid = ui.calls.firstOrNull { it.startsWith("grid:3:openDetail:") }
        assertNotNull("expected a search grid", searchGrid)
        assertTrue("search grid missing title", searchGrid!!.contains("Perfect World Movie"))

        // 3) openDetail parses cover/synopsis + episode list.
        ui.calls.clear()
        val itemJson = """{"title":"Perfect World","image":"","url":"https://anichin.cafe/seri/perfect-world/"}"""
        val detail = engine.invokeFunction(s, env, "openDetail", listOf(itemJson))
        assertTrue("openDetail failed: $detail", detail is ScriptResult.Success)
        val imageCall = ui.calls.firstOrNull { it.startsWith("image:") }
        assertNotNull("expected an image card", imageCall)
        assertTrue(imageCall!!.startsWith("image:https://anichin.cafe/wp-content/uploads/2021/04/Perfect-World.webp:Perfect World:true"))
        val epGrid = ui.calls.firstOrNull { it.startsWith("grid:3:openEpisode:") }
        assertNotNull("expected an episode grid", epGrid)
        assertTrue("episode grid missing ep url", epGrid!!.contains("perfect-world-episode-281"))

        // 4) openEpisode decodes the mirror iframe, parses the master playlist and
        //    hands the best VALID HLS variant (720p, not the malformed 1080p) to addVideo.
        ui.calls.clear()
        val epJson = """{"title":"Perfect World - Ep 281","image":"","url":"https://anichin.cafe/perfect-world-episode-281-subtitle-indonesia/"}"""
        val episode = engine.invokeFunction(s, env, "openEpisode", listOf(epJson))
        assertTrue("openEpisode failed: $episode", episode is ScriptResult.Success)
        val videoCall = ui.calls.firstOrNull { it.startsWith("videoCard:") }
        assertNotNull("expected an HLS video card", videoCall)
        assertTrue(
            "expected best variant (720p), got $videoCall",
            videoCall!!.startsWith("videoCard:https://cdn.example/720.m3u8:"),
        )
        assertTrue("must pass isStreamHls=true + allowDownload=true", videoCall.endsWith(":true:true:{}"))
        assertTrue("title must include episode + server", videoCall.contains("Perfect World - Ep 281"))
        assertTrue("title must include server name", videoCall.contains("Premium 1"))
        // The non-HLS OK.ru mirror must only be listed in the log, never rendered as a card.
        assertTrue(ui.calls.none { it.contains("ok.ru/videoembed") && it.startsWith("videoCard:") })
        // Tahap 20.1: the iframe base64 must have been decoded through the native bridge.
        assertTrue("expected native decodeBase64 bridge calls", ui.decodeBase64Calls > 0)
        assertTrue(ui.calls.any { it.startsWith("decodeBase64:") })
    }

    @Test
    fun `decodeBase64 resolves through the native bridge and matches expected output`() = runBlocking {
        val engine = RhinoScriptEngine(anichinClient())
        val ui = UiRecorder()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val probe = scriptSource + "\n" + """
            function main() {
                var expected = '<iframe src="https://anichin.stream/?id=abc" frameborder="0"></iframe>';
                var enc = 'PGlmcmFtZSBzcmM9Imh0dHBzOi8vYW5pY2hpbi5zdHJlYW0vP2lkPWFiYyIgZnJhbWVib3JkZXI9IjAiPjwvaWZyYW1lPg==';
                var out = decodeBase64(enc);
                return out === expected ? 'ok' : 'mismatch:' + out;
            }
        """.trimIndent()

        val result = engine.execute(Script(id = "b64", name = "b64", source = probe), env)

        assertTrue("unexpected: $result", result is ScriptResult.Success)
        assertEquals("ok", (result as ScriptResult.Success).value)
        // The JS `decodeBase64` wrapper must have preferred the native bridge.
        assertTrue("expected the native bridge to be called", ui.decodeBase64Calls > 0)
    }
}
