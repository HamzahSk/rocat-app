package app.rocat.ui.components

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import app.rocat.core.common.network.NetworkHelper
import java.util.Locale

/**
 * High-performance in-app video player built on AndroidX Media3 (ExoPlayer) (Tahap 18.3).
 *
 * Plays standard progressive sources (MP4 / WebM) and HLS streams (`.m3u8`). Rendered
 * as an inline 16:9 [PlayerView] with the stock controls plus a **full screen** toggle:
 * entering full screen switches the activity to landscape, hides the system bars
 * (immersive mode) and shows the video in a dedicated full-screen dialog that also
 * handles Back / exit via [DialogWindowProvider]. The same [ExoPlayer] instance is
 * shared between inline and full-screen so playback never resets when toggling.
 *
 * [headers] (Tahap 24.1) are applied to every network request via
 * `DefaultHttpDataSource.Factory.setDefaultRequestProperties`, so a `Referer` (or any
 * custom header) is sent when fetching `.m3u8` playlists and `.ts` segments alike.
 */
@Composable
fun RocatVideoPlayer(
    url: String,
    isHls: Boolean = false,
    headers: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var isFullScreen by remember { mutableStateOf(false) }

    val exoPlayer = remember(url, headers) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = autoPlay
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(NetworkHelper.DEFAULT_USER_AGENT)
                .apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
            val mediaItem = MediaItem.fromUri(url)
            val mediaSource = if (isHls || isHlsUrl(url)) {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
            setMediaSource(mediaSource)
            prepare()
        }
    }

    // Release the player when this composable (or its URL) leaves composition.
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // Rotate + toggle the system bars whenever the full-screen flag changes.
    LaunchedEffect(isFullScreen) {
        val window = activity?.window
        if (isFullScreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window?.hideSystemBars()
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            window?.showSystemBars()
        }
    }

    Box(modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            update = { view ->
                // While the full-screen dialog owns the surface, detach the inline view.
                view.player = if (isFullScreen) null else exoPlayer
            },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = { isFullScreen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(
                Icons.Filled.Fullscreen,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }

    if (isFullScreen) {
        FullScreenVideoDialog(player = exoPlayer, onExit = { isFullScreen = false })
    }
}

/** A window-filling dialog rendering [player] in landscape with the system bars hidden.
 *  Tahap 31: bars are hidden on the dialog window *and* (fallback) the activity window so
 *  the playback area is truly edge-to-edge; a soft top gradient scrim keeps the exit
 *  control legible and lets the video blend with the screen instead of looking "flat". */
@Composable
private fun FullScreenVideoDialog(
    player: ExoPlayer,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        LaunchedEffect(Unit) {
            // Immersive mode on every window we can reach. Most devices expose the
            // dialog's own window here; if not, the hosting activity window is a safe
            // fallback so the system bars never linger over the video.
            listOfNotNull(dialogWindow, activity?.window).forEach { window ->
                window.hideSystemBars()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize(),
            )

            // Subtle top scrim so the exit button stays readable over bright video.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                        ),
                    ),
            )

            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    Icons.Filled.FullscreenExit,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
    }
}

/** True when [url] looks like an HLS stream (`.m3u8` playlist / `hls://` scheme). */
private fun isHlsUrl(url: String): Boolean {
    val lower = url.lowercase(Locale.ROOT)
    return lower.contains(".m3u8") || lower.startsWith("hls://")
}
