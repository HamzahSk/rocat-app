package app.rocat.media

import androidx.documentfile.provider.DocumentFile
import app.rocat.core.common.network.NetworkHelper
import app.rocat.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Locale

/**
 * Downloads a media file (image / video) from the network and persists it into the
 * active scrape folder via the SAF-backed [StorageManager] (Tahap 18).
 *
 * The download reuses the app's shared OkHttp client (`NetworkHelper.client()`) so
 * requests carry the browser-grade user-agent, the custom DoH DNS, the stealth headers
 * and the shared cookie jar — meaning authenticated media (e.g. scraped video behind a
 * login) downloads correctly.
 */class MediaDownloader(
    private val networkHelper: NetworkHelper,
    private val storageManager: StorageManager,
) {

    /**
     * Streams the resource at [url] into [folder] as [fileName] with MIME [mimeType].
     * [headers] (Tahap 24.1) are extra HTTP headers (e.g. a `Referer`) added to the
     * download request so hotlink-protected hosts serve the file. [onProgress] is
     * invoked with a 0..1 fraction as chunks arrive.
     *
     * @return the content [android.net.Uri] string of the written file, or null when the
     *   download failed / storage is unavailable.
     */
    suspend fun download(
        url: String,
        folder: DocumentFile?,
        fileName: String,
        mimeType: String = "application/octet-stream",
        headers: Map<String, String> = emptyMap(),
        onProgress: (Float) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        if (folder == null) {
            android.util.Log.w("RoCatMedia", "download: no folder (storage not configured?) url=$url")
            return@withContext null
        }
        val bytes = fetchBytes(url, headers, onProgress)
        if (bytes == null) {
            android.util.Log.w("RoCatMedia", "download: fetchBytes failed url=$url")
            return@withContext null
        }
        val saved = runCatching {
            storageManager.saveFileToScrapeFolder(
                folder = folder,
                fileName = fileName,
                mimeType = mimeType,
                content = bytes,
            )?.toString()
        }.getOrElse { error ->
            // Tahap 31.3: any throwable (SecurityException on revoked SAF grant,
            // IOException on disconnected USB drive, ...) is caught here and
            // surfaced as a log + Toast — no more silent force-close.
            android.util.Log.w("RoCatMedia", "download: saveFileToScrapeFolder threw", error)
            null
        }
        if (saved == null) android.util.Log.w("RoCatMedia", "download: save returned null url=$url")
        saved
    }

    /**
     * Convenience overload deriving the file name from the URL's last path segment so
     * callers do not need to guess a name (e.g. `video.mp4` from `https://.../clip.mp4`).
     */
    suspend fun downloadFromUrl(
        url: String,
        folder: DocumentFile?,
        mimeType: String = "application/octet-stream",
        headers: Map<String, String> = emptyMap(),
        onProgress: (Float) -> Unit = {},
    ): String? {
        val fileName = inferFileName(url, mimeType)
        return download(url, folder, fileName, mimeType, headers, onProgress)
    }

    /** Streams the whole body of [url] into memory, reporting download progress. */
    private fun fetchBytes(url: String, headers: Map<String, String>, onProgress: (Float) -> Unit): ByteArray? = runCatching {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> addHeader(name, value) }
        }.build()
        networkHelper.client().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val body = response.body ?: return@runCatching null
            val total = body.contentLength().coerceAtLeast(0L)
            val buffer = ByteArrayOutputStream()
            val sink = body.byteStream()
            val chunk = ByteArray(DEFAULT_CHUNK_SIZE)
            var received = 0L
            while (true) {
                val read = sink.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                received += read
                if (total > 0) onProgress(received.toFloat() / total.toFloat())
            }
            buffer.toByteArray()
        }
    }.getOrNull()

    /**
     * Best-effort file name for [url]: last non-empty path segment, with a MIME-based
     * extension fallback so downloads always have a sensible name.
     */
    private fun inferFileName(url: String, mimeType: String): String {
        val decoded = runCatching { URLDecoder.decode(url, Charsets.UTF_8.name()) }.getOrDefault(url)
        val segment = decoded.substringBefore('?').substringBefore('#')
            .substringAfterLast('/')
            .trim()
        val name = segment.takeIf { it.isNotBlank() && !it.endsWith("/") } ?: "media"
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension.isBlank() || extension.length > 6) {
            val fallback = mimeTypeToExtension(mimeType)
            return "$name$fallback"
        }
        return name
    }

    /** Maps a MIME type to a typical file extension (used when the URL carries none). */
    private fun mimeTypeToExtension(mimeType: String): String = when (mimeType.lowercase(Locale.ROOT)) {
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "video/mp4", "video/mp4v-es" -> ".mp4"
        "video/webm" -> ".webm"
        "video/quicktime" -> ".mov"
        "application/x-mpegurl", "application/vnd.apple.mpegurl" -> ".m3u8"
        "application/octet-stream" -> ".bin"
        else -> ".bin"
    }

    companion object {
        private const val DEFAULT_CHUNK_SIZE = 8 * 1024
    }
}
