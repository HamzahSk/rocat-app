package app.rocat.data.script

import app.rocat.core.common.network.GET
import app.rocat.core.common.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.net.ssl.SSLException
import java.io.IOException
import java.net.UnknownHostException

/**
 * Downloads a raw `.js` script from a URL. The URL is normalised first (whitespace
 * trimmed, `https://` injected when the user only typed a domain) and GitHub blob
 * URLs are transparently rewritten to their `raw.githubusercontent.com` equivalent.
 *
 * The response is validated to be plain text/JS (rejecting HTML error pages and other
 * non-script payloads) before it is handed to the importer for storage. Network
 * failures are rethrown as typed [IOException]s the UI can map to friendly messages.
 */
class ScriptSourceFetcher(
    private val client: OkHttpClient,
) {
    /**
     * Downloads and validates the script source, always on [Dispatchers.IO].
     *
     * @throws IllegalArgumentException when the URL is malformed or the payload is not
     *   a script (HTML error page / empty body).
     * @throws IOException on connectivity / TLS failures (`UnknownHostException`,
     *   `SSLException`, `SocketTimeoutException`, ...).
     */
    suspend fun fetchSource(url: String): String = withContext(Dispatchers.IO) {
        val effectiveUrl = normalizeUrl(url)
        val response = client.newCall(GET(effectiveUrl, cacheControl = null)).awaitSuccess()
        response.use { res ->
            val body = res.body?.string() ?: ""
            validateContentType(res.header("Content-Type"), effectiveUrl)
            validateBody(body, effectiveUrl)
            body
        }
    }

    /**
     * Trims the input and guarantees a usable absolute URL:
     *  - bare domains (`google.com/path.js`) get `https://` prepended,
     *  - `http://` is kept (cleartext is allowed by network_security_config),
     *  - GitHub `blob/` links are rewritten to their raw equivalent.
     */
    fun normalizeUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) throw IllegalArgumentException("Enter a script URL first")

        if (!url.contains("://")) {
            url = "https://$url"
        }
        return rewriteUrl(url)
    }

    private fun validateContentType(contentType: String?, url: String) {
        val mime = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
        if (mime.startsWith("text/html") || mime.contains("xml")) {
            throw IllegalArgumentException(
                "URL returned \"$mime\" instead of a script (expected plain text or JavaScript): $url",
            )
        }
    }

    private fun validateBody(body: String, url: String) {
        val trimmed = body.trimStart()
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
        ) {
            throw IllegalArgumentException(
                "URL returned an HTML page instead of a script. " +
                    "Use a raw/link-to-file URL (e.g. raw.githubusercontent.com): $url",
            )
        }
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("URL returned an empty response: $url")
        }
    }

    private fun rewriteUrl(url: String): String {
        val githubBlob = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$")
        return githubBlob.replace(url) { match ->
            val (owner, repo, path) = match.destructured
            "https://raw.githubusercontent.com/$owner/$repo/$path"
        }
    }
}
