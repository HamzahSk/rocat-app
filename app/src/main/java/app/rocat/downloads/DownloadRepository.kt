package app.rocat.downloads

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import app.rocat.core.common.network.NetworkHelper
import app.rocat.storage.StorageManager
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request

@Serializable
enum class DownloadState { QUEUED, DOWNLOADING, COMPLETED, FAILED }

@Serializable
data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val source: String,
    val state: DownloadState = DownloadState.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val contentUri: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
) {
    val progress: Float?
        get() = totalBytes.takeIf { it > 0L }?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }
}

/** Durable, observable download queue shared by the browser and script output cards. */
class DownloadRepository(
    context: Context,
    private val networkHelper: NetworkHelper,
    private val storageManager: StorageManager,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloads = MutableStateFlow(loadPersisted())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    init {
        val recovered = _downloads.value.map {
            if (it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED) {
                it.copy(state = DownloadState.FAILED, bytesPerSecond = 0L, error = "Download interrupted")
            } else it
        }
        if (recovered != _downloads.value) {
            _downloads.value = recovered
            persist()
        }
    }

    fun enqueue(
        url: String,
        fileName: String,
        mimeType: String,
        headers: Map<String, String> = emptyMap(),
        source: String = "Browser",
    ): String {
        val id = UUID.randomUUID().toString()
        add(DownloadItem(id, url, sanitizeFileName(fileName), mimeType, source))
        scope.launch { downloadExisting(id, storageManager.createDownloadsFolder(), headers) }
        return id
    }

    suspend fun downloadNow(
        url: String,
        folder: DocumentFile?,
        fileName: String,
        mimeType: String,
        headers: Map<String, String> = emptyMap(),
        source: String = "Script output",
        onProgress: (Float) -> Unit = {},
    ): String? {
        val id = UUID.randomUUID().toString()
        add(DownloadItem(id, url, sanitizeFileName(fileName), mimeType, source))
        return downloadExisting(id, folder, headers, onProgress)
    }

    fun retry(id: String) {
        if (_downloads.value.none { it.id == id }) return
        update(id) { it.copy(state = DownloadState.QUEUED, error = null, bytesDownloaded = 0L, bytesPerSecond = 0L) }
        persist()
        scope.launch { downloadExisting(id, storageManager.createDownloadsFolder(), emptyMap()) }
    }

    fun remove(id: String, deleteFile: Boolean) {
        val item = _downloads.value.firstOrNull { it.id == id } ?: return
        if (deleteFile) item.contentUri?.let(storageManager::deleteDocument)
        _downloads.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clearCompleted(deleteFiles: Boolean) {
        if (deleteFiles) {
            _downloads.value.filter { it.state == DownloadState.COMPLETED }
                .mapNotNull { it.contentUri }
                .forEach(storageManager::deleteDocument)
        }
        _downloads.update { list -> list.filterNot { it.state == DownloadState.COMPLETED } }
        persist()
    }

    private suspend fun downloadExisting(
        id: String,
        folder: DocumentFile?,
        headers: Map<String, String>,
        onProgress: (Float) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        val initial = _downloads.value.firstOrNull { it.id == id } ?: return@withContext null
        if (folder == null) {
            fail(id, "Choose a storage folder in Settings first")
            return@withContext null
        }
        val targetName = uniqueFileName(folder, initial.fileName)
        update(id) { it.copy(fileName = targetName) }
        update(id) { it.copy(state = DownloadState.DOWNLOADING, error = null) }

        val request = runCatching {
            Request.Builder().url(initial.url).apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
            }.build()
        }.getOrElse {
            fail(id, it.message ?: "Invalid download URL")
            return@withContext null
        }

        val result = runCatching {
            networkHelper.client().newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty response")
                val total = body.contentLength()
                var received = 0L
                var sampleBytes = 0L
                var sampleStarted = System.nanoTime()

                val uri = storageManager.saveStreamToScrapeFolder(folder, targetName, initial.mimeType) { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            received += read
                            sampleBytes += read
                            val now = System.nanoTime()
                            val elapsedMs = (now - sampleStarted) / 1_000_000L
                            if (elapsedMs >= 350L || received == total) {
                                val speed = if (elapsedMs > 0) sampleBytes * 1_000L / elapsedMs else 0L
                                update(id) {
                                    it.copy(
                                        state = DownloadState.DOWNLOADING,
                                        bytesDownloaded = received,
                                        totalBytes = total,
                                        bytesPerSecond = speed,
                                    )
                                }
                                if (total > 0L) onProgress(received.toFloat() / total.toFloat())
                                sampleStarted = now
                                sampleBytes = 0L
                            }
                        }
                    }
                } ?: error("Unable to write to storage")
                if (total > 0L) onProgress(1f)
                uri.toString() to received
            }
        }

        result.fold(
            onSuccess = { (uri, received) ->
                update(id) {
                    it.copy(
                        state = DownloadState.COMPLETED,
                        bytesDownloaded = received,
                        totalBytes = if (it.totalBytes > 0L) it.totalBytes else received,
                        bytesPerSecond = 0L,
                        contentUri = uri,
                        completedAt = System.currentTimeMillis(),
                    )
                }
                persist()
                uri
            },
            onFailure = {
                fail(id, it.message ?: "Download failed")
                null
            },
        )
    }

    private fun add(item: DownloadItem) {
        _downloads.update { listOf(item) + it }
        persist()
    }

    private fun fail(id: String, message: String) {
        update(id) { it.copy(state = DownloadState.FAILED, bytesPerSecond = 0L, error = message) }
        persist()
    }

    private fun update(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun persist() {
        prefs.edit().putString(
            KEY_ITEMS,
            json.encodeToString(ListSerializer(DownloadItem.serializer()), _downloads.value),
        ).apply()
    }

    private fun loadPersisted(): List<DownloadItem> = runCatching {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return@runCatching emptyList()
        json.decodeFromString(ListSerializer(DownloadItem.serializer()), raw)
    }.getOrDefault(emptyList())

    private fun sanitizeFileName(value: String): String =
        value.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").take(120).ifBlank { "download" }

    private fun uniqueFileName(folder: DocumentFile, requested: String): String {
        if (folder.findFile(requested) == null) return requested
        val dot = requested.lastIndexOf('.')
        val base = if (dot > 0) requested.substring(0, dot) else requested
        val extension = if (dot > 0) requested.substring(dot) else ""
        var index = 1
        while (folder.findFile("$base ($index)$extension") != null) index++
        return "$base ($index)$extension"
    }

    private companion object {
        const val PREFS = "rocat_downloads"
        const val KEY_ITEMS = "items"
    }
}
