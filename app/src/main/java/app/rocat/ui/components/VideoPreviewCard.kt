package app.rocat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile

/**
 * Script-driven video preview card (Tahap 18.2). Shows a 16:9 placeholder with the
 * video [title], a **Play Inline** button that swaps the placeholder for the in-app
 * [RocatVideoPlayer] (Media3 / ExoPlayer, supports HLS `.m3u8` streams), and a
 * **Download Video** button that saves the file into the active scrape folder via
 * [MediaDownloader] + [app.rocat.storage.StorageManager].
 *
 * **Tahap 31.3**: [noStorageMessage] is shown via Toast when [folder] is null so a
 * download attempt without a configured storage folder is no longer silent.
 */
@Composable
fun VideoPreviewCard(
    url: String,
    title: String = "",
    isStreamHls: Boolean = false,
    allowDownload: Boolean = true,
    headers: Map<String, String> = emptyMap(),
    folder: () -> DocumentFile?,
    playInlineLabel: String,
    closePlayerLabel: String,
    downloadLabel: String,
    successMessage: String,
    failureMessage: String,
    noStorageMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    var playing by remember { mutableStateOf(false) }
    val downloader = rememberMediaDownloaderState()

    ElevatedCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (playing) {
                RocatVideoPlayer(
                    url = url,
                    isHls = isStreamHls,
                    headers = headers,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                VideoThumbnailPlaceholder(title = title)
            }

            if (title.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }

            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                Button(onClick = { playing = !playing }, enabled = true) {
                    if (playing) {
                        Text(closePlayerLabel)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(playInlineLabel)
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (allowDownload) {
                    OutlinedButton(
                        onClick = {
                            downloader.start(
                                url = url,
                                folder = folder(),
                                fileName = fileNameFromUrl(url, fallback = "video"),
                                mimeType = videoMimeFor(url, isStreamHls),
                                headers = headers,
                                successMessage = successMessage,
                                failureMessage = failureMessage,
                                noStorageMessage = noStorageMessage,
                            )
                        },
                        enabled = downloader.status !is DownloadStatus.Downloading,
                    ) {
                        if (downloader.status is DownloadStatus.Downloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(downloadLabel)
                    }
                }
            }
        }
    }
}

/** The 16:9 poster area shown while the video is not playing. */
@Composable
private fun VideoThumbnailPlaceholder(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .padding(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
    }
}

/** Picks a MIME type for a video URL based on its extension / HLS flag. */
internal fun videoMimeFor(url: String, isStreamHls: Boolean): String = when {
    isStreamHls -> "application/x-mpegurl"
    url.substringAfterLast('.', "").lowercase() == "webm" -> "video/webm"
    url.substringAfterLast('.', "").lowercase() == "m3u8" -> "application/x-mpegurl"
    else -> "video/mp4"
}
