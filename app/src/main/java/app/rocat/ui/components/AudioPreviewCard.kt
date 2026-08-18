@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package app.rocat.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import app.rocat.core.common.network.NetworkHelper
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Script-driven inline audio player card (Tahap 22.2) built on Media3/ExoPlayer.
 * Compact controls: Play/Pause, a seekable progress bar with time labels, and (when
 * [allowDownload]) a **Download Audio** button that saves the file into the active
 * scrape folder via [MediaDownloader] + [app.rocat.storage.StorageManager].
 * [headers] (Tahap 24.1) are sent with the ExoPlayer data source requests.
 */
@Composable
fun AudioPreviewCard(
    url: String,
    title: String = "",
    allowDownload: Boolean = true,
    headers: Map<String, String> = emptyMap(),
    folder: () -> DocumentFile?,
    playLabel: String,
    pauseLabel: String,
    downloadLabel: String,
    successMessage: String,
    failureMessage: String,
    noStorageMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val downloader = rememberMediaDownloaderState()
    val context = LocalContext.current

    val player = remember(url, headers) {
        ExoPlayer.Builder(context).build().apply {
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(NetworkHelper.DEFAULT_USER_AGENT)
                .apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Poll progress while playing; duration is unknown until the player is ready.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val duration = player.duration
            if (duration > 0) durationMs = duration
            delay(500L)
        }
    }

    ElevatedCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) pauseLabel else playLabel,
                    )
                }
                Slider(
                    value = positionMs.coerceIn(0L, durationMs).toFloat(),
                    onValueChange = { player.seekTo(it.toLong()) },
                    enabled = durationMs > 0,
                    valueRange = 0f..durationMs.coerceAtLeast(1).toFloat(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }

            if (allowDownload) {
                OutlinedButton(
                    onClick = {
                        downloader.start(
                            url = url,
                            folder = folder(),
                            fileName = fileNameFromUrl(url, fallback = "audio"),
                            mimeType = audioMimeFor(url),
                            headers = headers,
                            successMessage = successMessage,
                            failureMessage = failureMessage,
                            noStorageMessage = noStorageMessage,
                        )
                    },
                ) {
                    if (downloader.status is DownloadStatus.Downloading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(downloadLabel)
                }
            }
        }
    }
}

/** Formats a millisecond duration as `m:ss` (or `h:mm:ss` for long tracks). */
internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0L) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/** Picks a sensible MIME type for an audio URL based on its file extension. */
internal fun audioMimeFor(url: String): String = when (url.substringAfterLast('.', "").lowercase()) {
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "ogg" -> "audio/ogg"
    "m4a" -> "audio/mp4"
    "aac" -> "audio/aac"
    "flac" -> "audio/flac"
    "opus" -> "audio/opus"
    else -> "audio/mpeg"
}
