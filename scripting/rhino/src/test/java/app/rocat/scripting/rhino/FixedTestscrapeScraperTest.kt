package app.rocat.scripting.rhino

import app.rocat.scripting.api.FetchResult
import app.rocat.scripting.api.ScriptBrowserBridge
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
 * Tahap 23 — drives the rewritten `fixed_testscrape.js` (XVideos camera scraper
 * migrated from the old draft `testscrape.txt`) through the full RoCat canvas flow
 * against canned HTML on an OkHttp interceptor:
 *
 *   onLaunch -> doSearch -> openDetail -> openVideo (HLS best-variant)
 *
 * Verifies the script uses the Tahap-22 API: `RoCat.render([...])` descriptors,
 * `RoCat.safeParseJson` for grid payloads, `RoCatUI.addAlert` banners,
 * `RoCatUI.addBadgeGroup` genre chips and `RoCatUI.addJsonLog` debug cards, and
 * that malformed payloads never throw.
 */
class FixedTestscrapeScraperTest {

    private val scriptSource: String by lazy {
        val candidates = listOf(
            "../../../fixed_testscrape.js", // working dir = rocat-app/scripting/rhino
            "../../fixed_testscrape.js",    // working dir = rocat-app/scripting
            "../fixed_testscrape.js",       // working dir = rocat-app
            "fixed_testscrape.js",          // working dir = repo root
        )
        val file = candidates.asSequence()
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("fixed_testscrape.js not found (user.dir=${System.getProperty("user.dir")})")
        file.readText()
    }

    private fun script() = Script(id = "xv", name = "XVideos Scraper", source = scriptSource)

    private class UiRecorder : ScriptUiBridge {
        val calls = mutableListOf<String>()
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
        override fun addJsonLog(dataJson: String, title: String, allowCopy: Boolean) {
            calls += "jsonLog:$title:$allowCopy:$dataJson"
        }
        override fun addHtmlPreview(htmlContent: String, title: String) {
            calls += "html:$title:$htmlContent"
        }
        override fun addAudio(url: String, title: String, allowDownload: Boolean, headers: Map<String, String>) {
            calls += "audio:$url:$title:$allowDownload:${headers.toSortedMap()}"
        }
        override fun addAlert(message: String, type: String) {
            calls += "alert:$type:$message"
        }
        override fun addBadgeGroup(badgesJson: String) {
            calls += "badges:$badgesJson"
        }
    }

    private val homeHtml = """
        <html><body>
          <div id="content"><div class="mozaique">
            <div class="thumb-block">
              <div class="thumb-inside"><div class="thumb">
                <a href="/video1/sample-title/"><img src="https://cdn.xvideos.test/thumbs/sample.jpg"/></a>
              </div></div>
              <div class="thumb-under"><p class="title"><a href="/video1/sample-title/">Sample [4K] Title</a></p></div>
            </div>
            <div class="thumb-block">
              <div class="thumb-inside"><div class="thumb">
                <a href="/video2/another-clip/"><img src="https://cdn.xvideos.test/thumbs/another.jpg"/></a>
              </div></div>
              <div class="thumb-under"><p class="title"><a href="/video2/another-clip/">Another Clip</a></p></div>
            </div>
          </div></div>
        </body></html>
    """.trimIndent()

    private val searchHtml = """
        <html><body>
          <div id="content"><div class="mozaique">
            <div class="thumb-block">
              <div class="thumb-inside"><div class="thumb">
                <a href="/video1/sample-title/"><img src="https://cdn.xvideos.test/thumbs/sample.jpg"/></a>
              </div></div>
              <div class="thumb-under"><p class="title"><a href="/video1/sample-title/">Sample Title</a></p></div>
            </div>
            <div class="thumb-block">
              <div class="thumb-inside"><div class="thumb">
                <a href="/video3/sample-sequel/"><img src="https://cdn.xvideos.test/thumbs/sequel.jpg"/></a>
              </div></div>
              <div class="thumb-under"><p class="title"><a href="/video3/sample-sequel/">Sample [HD] Sequel</a></p></div>
            </div>
          </div></div>
        </body></html>
    """.trimIndent()

    private val detailHtml = """
        <html><head>
          <meta property="og:title" content="Sample Full Title">
          <meta property="og:image" content="https://cdn.xvideos.test/thumbs/cover.jpg">
        </head><body>
          <h2 class="page-title">Sample Full Title</h2>
          <ul class="main-uploader">
            <li class="main-uploader"><span class="name">UploaderJohn</span></li>
          </ul>
          <div class="video-metadata">
            <ul>
              <li><a class="is-keyword" href="/?k=4k">4K</a></li>
              <li><a class="is-keyword" href="/?k=hd">HD</a></li>
            </ul>
          </div>
          <script type="text/javascript">
            var html5player = function(){};
            html5player.setVideoUrlLow('https://cdn.xvideos.test/low.mp4');
            html5player.setVideoUrlHigh('https://cdn.xvideos.test/high.mp4');
            html5player.setVideoHLS('https://cdn.xvideos.test/hls/master.m3u8');
            html5player.setVolumeHLS(100);
          </script>
        </body></html>
    """.trimIndent()

    private val masterPlaylist = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-STREAM-INF:BANDWIDTH=676000,RESOLUTION=640x360
        https://cdn.xvideos.test/hls/360.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=1052000,RESOLUTION=854x480
        https://cdn.xvideos.test/hls/480.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2137000,RESOLUTION=1280x720
        https://cdn.xvideos.test/hls/720.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=4096000,RESOLUTION=1920x1080
    """.trimIndent()

    /** The script's `fetch()` always goes through OkHttp, so mock at the client layer. */
    private fun xvClient(): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val body = when {
                url.contains("/hls/") || url.endsWith(".m3u8") -> masterPlaylist
                url.contains("/video") -> detailHtml
                url.contains("?k=") -> searchHtml
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
    fun `script drives search-to-video canvas flow with tahap22 api`() = runBlocking {
        val engine = RhinoScriptEngine(xvClient())
        val ui = UiRecorder()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val s = script()

        // 1) onLaunch draws the search form + a home grid using RoCat.render + addAlert.
        val launch = engine.invokeNamedFunction(s, env, "onLaunch", emptyMap())
        assertTrue("onLaunch failed: $launch", launch is ScriptResult.Success)
        assertTrue(ui.calls.any { it.startsWith("input:query:") })
        assertTrue(ui.calls.any { it.startsWith("button:🔍 Cari:doSearch") })
        val homeGrid = ui.calls.firstOrNull { it.startsWith("grid:3:openDetail:") }
        assertNotNull("expected a home grid", homeGrid)
        assertTrue("home grid missing item", homeGrid!!.contains("Sample Title"))
        assertTrue("home grid must strip [tag] brackets", !homeGrid.contains("[4K]"))
        assertTrue("home grid must carry url", homeGrid.contains("xvideos.com/video1"))

        // 2) doSearch publishes a 3-column grid of results.
        ui.calls.clear()
        val search = engine.invokeNamedFunction(s, env, "doSearch", mapOf("query" to "sample"))
        assertTrue("doSearch failed: $search", search is ScriptResult.Success)
        val searchGrid = ui.calls.firstOrNull { it.startsWith("grid:3:openDetail:") }
        assertNotNull("expected a search grid", searchGrid)
        assertTrue("search grid missing item", searchGrid!!.contains("Sample Sequel"))

        // 3) openDetail parses cover/title + genre badges + promotes to video.
        ui.calls.clear()
        val itemJson = """{"title":"Sample Title","image":"","url":"https://www.xvideos.com/video1/sample-title/"}"""
        val detail = engine.invokeFunction(s, env, "openDetail", listOf(itemJson))
        assertTrue("openDetail failed: $detail", detail is ScriptResult.Success)
        val imageCall = ui.calls.firstOrNull { it.startsWith("image:") }
        assertNotNull("expected an image card with og:image cover", imageCall)
        assertTrue(imageCall!!.startsWith("image:https://cdn.xvideos.test/thumbs/cover.jpg:Sample Full Title:true"))
        val badge = ui.calls.firstOrNull { it.startsWith("badges:") }
        assertNotNull("expected a badge group for genres", badge)
        assertTrue("badges must carry keywords", badge!!.contains("4K") && badge.contains("HD"))
        assertTrue("must still expose the Play button", ui.calls.any { it.startsWith("button:▶️ Putar Video") })

        // 4) openVideo extracts html5player URLs, picks best VALID HLS variant and
        //    hands it to addVideo(..., isStreamHls=true, allowDownload=true).
        ui.calls.clear()
        val videoJson = """{"url":"https://www.xvideos.com/video1/sample-title/","title":"Sample Full Title"}"""
        val video = engine.invokeFunction(s, env, "openVideo", listOf(videoJson))
        assertTrue("openVideo failed: $video", video is ScriptResult.Success)
        val videoCall = ui.calls.firstOrNull { it.startsWith("videoCard:") }
        assertNotNull("expected an HLS video card", videoCall)
        assertTrue(
            "expected best VALID variant (720p), got $videoCall",
            videoCall!!.startsWith("videoCard:https://cdn.xvideos.test/hls/720.m3u8:"),
        )
        assertTrue("must pass isStreamHls=true + allowDownload=true", videoCall.endsWith(":true:true:{}"))
        // Debug JSON card must list every source the script extracted.
        val jsonLog = ui.calls.firstOrNull { it.startsWith("jsonLog:") }
        assertNotNull("expected a jsonLog debug card", jsonLog)
        assertTrue("jsonLog must list sources", jsonLog!!.contains("\"quality\"") && jsonLog.contains("HLS"))
    }

    @Test
    fun `malformed payloads are handled without throwing`() = runBlocking {
        val engine = RhinoScriptEngine(xvClient())
        val ui = UiRecorder()
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
        )
        val s = script()

        val badPayload = "not-json-{{"
        val detail = engine.invokeFunction(s, env, "openDetail", listOf(badPayload))
        assertTrue("openDetail with bad payload must not fail: $detail", detail is ScriptResult.Success)
        val badVideo = engine.invokeFunction(s, env, "openVideo", listOf(badPayload))
        assertTrue("openVideo with bad payload must not fail: $badVideo", badVideo is ScriptResult.Success)
        // The bridge is told why via an error alert rather than a raw log.
        assertTrue(ui.calls.any { it.startsWith("alert:error:") })

        // Empty query must show a warning alert, not crash.
        ui.calls.clear()
        val empty = engine.invokeNamedFunction(s, env, "doSearch", mapOf("query" to "   "))
        assertTrue("doSearch with empty query must not fail: $empty", empty is ScriptResult.Success)
        assertTrue(ui.calls.any { it.startsWith("alert:warning:") })
        assertEquals("no grid may be rendered for an empty search", true, ui.calls.none { it.startsWith("grid:") })
    }

    @Test
    fun `openVideo falls back to headless RoCatPage when html5player is js generated`() = runBlocking {
        // A detail page whose player is injected by JavaScript — invisible to fetch()+Jsoup.
        val noPlayerHtml = """
            <html><head>
              <meta property="og:title" content="JS Player Clip">
            </head><body>
              <h2 class="page-title">JS Player Clip</h2>
              <div id="player"></div>
              <script type="text/javascript">// player di-generate via JS</script>
            </body></html>
        """.trimIndent()
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(noPlayerHtml.toResponseBody("text/html; charset=utf-8".toMediaType()))
                .build()
        }
        val engine = RhinoScriptEngine(OkHttpClient.Builder().addInterceptor(interceptor).build())
        val ui = UiRecorder()
        val browser = object : ScriptBrowserBridge {
            var closed = false
            override fun open(url: String, timeoutMs: Long): Boolean {
                assertTrue("must open the video page", url.contains("xvideos.com/video9"))
                return true
            }
            override fun type(selector: String, text: String): Boolean = true
            override fun click(selector: String): Boolean = true
            override fun waitForSelector(selector: String, timeoutMs: Long): Boolean = true
            override fun evaluate(script: String): String = when {
                script.contains("setVideoUrlLow") ->
                    """{"low":"https://cdn.xvideos.test/low.mp4","high":"https://cdn.xvideos.test/high.mp4","hls":"https://cdn.xvideos.test/hls/master.m3u8"}"""
                else -> "null"
            }
            override fun getHtml(): String = noPlayerHtml
            override fun close() { closed = true }
        }
        val env = DefaultScriptEnvironment(
            fetchImpl = { _, _, _, _ -> FetchResult(200, emptyMap(), "") },
            ui = ui,
            browser = browser,
        )
        val s = script()
        val videoJson = """{"url":"https://www.xvideos.com/video9/js-player/","title":"JS Player Clip"}"""
        val video = engine.invokeFunction(s, env, "openVideo", listOf(videoJson))
        assertTrue("openVideo failed: $video", video is ScriptResult.Success)

        // The script must have gone interactive (warning banner) and extracted via JS.
        assertTrue(
            "expected an interactive-mode banner, got ${ui.calls.filter { it.startsWith("alert:") }}",
            ui.calls.any { it.startsWith("alert:warning:") && it.contains("WebView") },
        )
        assertTrue(
            "expected a headless-result jsonLog card",
            ui.calls.any { it.startsWith("jsonLog:") && it.contains("Mode Interaktif") },
        )

        // HLS master came from the headless extraction (static HTML had no player).
        val videoCall = ui.calls.firstOrNull { it.startsWith("videoCard:") }
        assertNotNull("expected an HLS video card via headless extraction", videoCall)
        assertTrue(
            "expected headless master.m3u8, got $videoCall",
            videoCall!!.startsWith("videoCard:https://cdn.xvideos.test/hls/master.m3u8:"),
        )
        assertTrue("must pass isStreamHls=true + allowDownload=true", videoCall.endsWith(":true:true:{}"))
        assertTrue("the headless WebView must be released", browser.closed)
    }
}