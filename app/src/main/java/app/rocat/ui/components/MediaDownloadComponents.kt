package app.rocat.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import app.rocat.core.common.injekt.Injekt
import app.rocat.media.MediaDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Transient state of a single media download triggered from a preview card. */
sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Downloading(val progress: Float) : DownloadStatus
    data object Done : DownloadStatus
    data object Failed : DownloadStatus
}

/**
 * Per-card holder for download state (Tahap 18.1/18.2). Downloads run on
 * [MediaDownloader]'s IO dispatcher, report progress into [status] and finish with a
 * Toast whose message depends on whether the SAF write succeeded.
 */
@Stable
class MediaDownloaderState(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    var status by mutableStateOf<DownloadStatus>(DownloadStatus.Idle)
        private set

    fun start(
        url: String,
        folder: DocumentFile?,
        fileName: String,
        mimeType: String,
        headers: Map<String, String> = emptyMap(),
        successMessage: String,
        failureMessage: String,
        noStorageMessage: String? = null,
    ) {
        if (folder == null) {
        // Tahap 31.3: tell the user *why* the download failed when storage is not
        // configured yet (first-launch gate). Before this fix the failure was
        // indistinguishable from "network error", so users had no idea what to do.
            Toast.makeText(context, noStorageMessage ?: failureMessage, Toast.LENGTH_LONG).show()
            return
        }
        if (status is DownloadStatus.Downloading) return
        val downloader = Injekt.get<MediaDownloader>()
        scope.launch {
            status = DownloadStatus.Downloading(0f)
            val uri = downloader.download(url, folder, fileName, mimeType, headers) { progress ->
                status = DownloadStatus.Downloading(progress)
            }
            status = if (uri != null) DownloadStatus.Done else DownloadStatus.Failed
            Toast.makeText(
                context,
                if (uri != null) successMessage else failureMessage,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}

/** Creates a [MediaDownloaderState] remembered for the current composition. */
@Composable
fun rememberMediaDownloaderState(): MediaDownloaderState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { MediaDownloaderState(context, scope) }
}

/**
 * Icon button showing the download state: a download icon when idle/failed (tapping
 * retries), a spinner while downloading, and a checkmark once saved.
 */
@Composable
fun DownloadActionButton(
    status: DownloadStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = status !is DownloadStatus.Downloading,
        modifier = modifier.background(Color.Black.copy(alpha = 0.45f)),
    ) {
        when (status) {
            is DownloadStatus.Downloading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                progress = { status.progress.coerceIn(0f, 1f) },
                color = Color.White,
            )
            is DownloadStatus.Done -> Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color.White,
            )
            is DownloadStatus.Idle,
            is DownloadStatus.Failed,
            -> Icon(
                Icons.Filled.Download,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}
