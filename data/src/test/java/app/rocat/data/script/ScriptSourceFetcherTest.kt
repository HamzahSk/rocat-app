package app.rocat.data.script

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ScriptSourceFetcherTest {

    private val fetcher = ScriptSourceFetcher(OkHttpClient())

    @Test
    fun `normalizeUrl trims whitespace and injects https scheme for bare domains`() {
        assertEquals(
            "https://example.com/script.user.js",
            fetcher.normalizeUrl("  example.com/script.user.js  "),
        )
        assertEquals(
            "https://google.com",
            fetcher.normalizeUrl("google.com"),
        )
    }

    @Test
    fun `normalizeUrl keeps an explicit scheme`() {
        assertEquals("http://example.com/a.js", fetcher.normalizeUrl("http://example.com/a.js"))
        assertEquals("https://example.com/a.js", fetcher.normalizeUrl("https://example.com/a.js"))
    }

    @Test
    fun `normalizeUrl rewrites github blob links to raw`() {
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/main/path/script.js",
            fetcher.normalizeUrl("https://github.com/owner/repo/blob/main/path/script.js"),
        )
    }

    @Test
    fun `normalizeUrl rejects empty input`() {
        assertThrows(IllegalArgumentException::class.java) { fetcher.normalizeUrl("   ") }
        assertThrows(IllegalArgumentException::class.java) { fetcher.normalizeUrl("") }
    }
}
