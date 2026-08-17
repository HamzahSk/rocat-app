package app.rocat.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import app.rocat.settings.SettingsRepository
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Owns the app's Storage Access Framework integration and the scrape folder layout.
 *
 * The user grants access to a single main directory via `ACTION_OPEN_DOCUMENT_TREE`; the
 * chosen URI is persisted (with a persistable grant) in [SettingsRepository]. Every scrape
 * writes into a dedicated sub-folder at `[MainDirectory]/Scrapes/[scrapeId]/` so results
 * stay organized and isolated per scrape run.
 */
class StorageManager(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    /** Small scope feeding the reactive `isConfigured` flow. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Whether the user has picked (and we hold a grant for) a main directory. Reactive so
     * the first-launch gate recomposes as soon as the folder is chosen (Tahap 17.1).
     */
    val isConfigured: StateFlow<Boolean> = settings.storageUri
        .map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, settings.storageUri.value != null)

    /** The persisted main directory URI, if any. */
    val mainUri: Uri?
        get() = settings.storageUri.value

    /**
     * Persists the read/write grant for [uri] returned by the folder picker. Mirrors how
     * mihon stores its "download location" tree URI. Returns false when the system refused
     * to keep the permission.
     */
    fun takePersistablePermission(uri: Uri): Boolean = runCatching {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        settings.setStorageUri(uri)
        true
    }.getOrDefault(false)

    /** Replaces the main directory with [uri] (called when the user changes it in Settings). */
    fun setMainDirectory(uri: Uri) {
        settings.setStorageUri(uri)
    }

    /** Clears the current main directory reference. */
    fun clearMainDirectory() {
        settings.setStorageUri(null)
    }

    /** The [DocumentFile] for the main directory, or null when not configured. */
    fun mainDocument(): DocumentFile? = mainUri?.let { DocumentFile.fromTreeUri(context, it) }

    /** Human-readable name of the main directory (e.g. "Downloads"), for the Settings UI. */
    fun mainDirectoryName(): String {
        val name = mainUri?.let { DocumentFile.fromTreeUri(context, it)?.name }
        return name?.takeIf { it.isNotBlank() } ?: mainUri?.lastPathSegment?.substringAfterLast(':') ?: ""
    }

    /**
     * Creates (or reuses) the per-scrape folder `[MainDirectory]/Scrapes/[name]/`. All files
     * belonging to one scrape run should be written under the returned [DocumentFile].
     */
    fun createScrapeFolder(name: String): DocumentFile? {
        val root = mainDocument() ?: return null
        val scrapes = root.findFile(SCRAPES_DIR) ?: root.createDirectory(SCRAPES_DIR) ?: return null
        return scrapes.findFile(name) ?: scrapes.createDirectory(name)
    }

    /**
     * Creates (or reuses) the per-script folder `[MainDirectory]/Scripts/[id]/`. Imported
     * scripts get a real, browsable `.js` file here (Tahap 17.2), complementing the
     * JSON-backed in-app store.
     */
    fun createScriptFolder(scriptId: String): DocumentFile? {
        val root = mainDocument() ?: return null
        val scripts = root.findFile(SCRIPTS_DIR) ?: root.createDirectory(SCRIPTS_DIR) ?: return null
        return scripts.findFile(scriptId) ?: scripts.createDirectory(scriptId)
    }

    /**
     * Writes a script's [content] as a physical file inside `[MainDirectory]/Scripts/[id]/`.
     * Reuses the same `DocumentFile.createFile` + `contentResolver.openOutputStream` pipeline
     * as the scrape writer so the source genuinely lands on device storage.
     *
     * @return the content [Uri] of the written file, or null when storage is not configured /
     *   the write failed.
     */
    suspend fun saveFileToScriptFolder(
        scriptId: String,
        fileName: String,
        content: String,
        mimeType: String = "application/javascript",
    ): Uri? = saveFileToScrapeFolder(
        folder = createScriptFolder(scriptId),
        fileName = fileName,
        mimeType = mimeType,
        content = content.toByteArray(Charsets.UTF_8),
    )

    /**
     * Deletes the physical per-script folder `[MainDirectory]/Scripts/[id]/` (used when a
     * script is removed from the list). Best-effort: silently ignores a missing folder.
     */
    fun deleteScriptFolder(scriptId: String) {
        val root = mainDocument() ?: return
        val scripts = root.findFile(SCRIPTS_DIR) ?: return
        runCatching { scripts.findFile(scriptId)?.delete() }
    }

    /**
     * Writes [content] as a new file named [fileName] inside the scrape [folder].
     *
     * Tahap 16.1: the old flow only created the sub-folder ([createScrapeFolder]) and never
     * wrote anything into it. This helper completes the pipeline: it (re)uses [folder] via
     * [DocumentFile.createFile] and streams the bytes through
     * `contentResolver.openOutputStream` so the file genuinely lands on device storage.
     *
     * Tahap 31 hardening: if [fileName] carries no extension a MIME-based one is appended
     * (so query-string scraped URLs still produce browsable files), the SAF write is fully
     * guarded, and every failure path is logged with its real cause.
     *
     * @return the content [Uri] of the written file, or null when storage is not
     *   configured / the write failed.
     */
    suspend fun saveFileToScrapeFolder(
        folder: DocumentFile?,
        fileName: String,
        mimeType: String,
        content: ByteArray,
    ): Uri? = withContext(Dispatchers.IO) {
        if (folder == null) {
            Log.w(TAG, "saveFileToScrapeFolder: folder is null (storage not configured)")
            return@withContext null
        }
        if (content.isEmpty()) {
            Log.w(TAG, "saveFileToScrapeFolder: refusing to write empty content for $fileName")
            return@withContext null
        }
        var safeName = sanitizeFileName(fileName)
        if (safeName.isBlank()) {
            Log.w(TAG, "saveFileToScrapeFolder: file name sanitized to blank")
            return@withContext null
        }
        // Append a MIME-based extension when the caller's name lacks one (Tahap 31).
        if (safeName.substringAfterLast('.', "").isBlank()) {
            safeName += extensionForMime(mimeType)
        }
        val target = folder.findFile(safeName) ?: folder.createFile(mimeType, safeName)
        if (target == null) {
            Log.e(TAG, "saveFileToScrapeFolder: createFile('$mimeType', '$safeName') returned null")
            return@withContext null
        }
        try {
            val stream = openOutputStream(target.uri)
            if (stream == null) {
                Log.e(TAG, "saveFileToScrapeFolder: openOutputStream returned null for ${target.uri}")
                return@withContext null
            }
            stream.use { out ->
                out.write(content)
                out.flush()
            }
            target.uri
        } catch (e: Throwable) {
            Log.e(TAG, "saveFileToScrapeFolder: write failed for '$safeName' -> ${target.uri}", e)
            null
        }
    }

    /** Convenience overload writing a UTF-8 [String] as plain text. */
    suspend fun saveFileToScrapeFolder(
        folder: DocumentFile?,
        fileName: String,
        content: String,
        mimeType: String = "text/plain",
    ): Uri? = saveFileToScrapeFolder(
        folder = folder,
        fileName = fileName,
        mimeType = mimeType,
        content = content.toByteArray(Charsets.UTF_8),
    )

    /** Opens (or creates) an [OutputStream] for [uri] through the SAF content provider. */
    private fun openOutputStream(uri: Uri): OutputStream? {
        val resolver = context.contentResolver
        val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
        return stream ?: runCatching { resolver.openOutputStream(uri) }.getOrNull()
    }

    /** Strips path separators / control chars so the name is a valid document file name. */
    private fun sanitizeFileName(name: String): String =
        name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").take(120)

    /** Maps a MIME type to a typical extension used when a file name has none (Tahap 31). */
    private fun extensionForMime(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "video/mp4", "video/mp4v-es" -> ".mp4"
        "video/webm" -> ".webm"
        "video/quicktime" -> ".mov"
        "audio/mpeg" -> ".mp3"
        "audio/wav" -> ".wav"
        "audio/ogg" -> ".ogg"
        "audio/mp4" -> ".m4a"
        "application/x-mpegurl", "application/vnd.apple.mpegurl" -> ".m3u8"
        "application/json" -> ".json"
        "text/plain" -> ".txt"
        "application/javascript" -> ".js"
        else -> ".bin"
    }

    /**
     * Empties the Coil image cache (memory + disk) and every file under `context.cacheDir`.
     * Runs on a background dispatcher because disk eviction can take a moment.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val imageLoader = runCatching { SingletonImageLoader.get(context) }.getOrNull()
        runCatching { imageLoader?.memoryCache?.clear() }
        runCatching { imageLoader?.diskCache?.clear() }
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
    }

    companion object {
        /** Top-level folder holding every scrape run. */
        const val SCRAPES_DIR = "Scrapes"

        /** Top-level folder holding every imported script's physical `.js` file. */
        const val SCRIPTS_DIR = "Scripts"

        private const val TAG = "StorageManager"
    }
}
