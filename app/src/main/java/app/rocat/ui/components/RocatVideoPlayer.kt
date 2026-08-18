@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package app.rocat.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
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
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (isFullScreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller?.show(WindowInsetsCompat.Type.systemBars())
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

/** A window-filling dialog rendering [player] in landscape with the system bars hidden. */
@Composable
private fun FullScreenVideoDialog(
    player: ExoPlayer,
    onExit: () -> Unit,
) {
    val view = LocalView.current
    val dialogWindow = remember(view) { (view.parent as? DialogWindowProvider)?.window }
    // Tahap 31.2: re-apply immersive on every frame for the first second so the dialog
    // never settles into a "flat" look if the activity-orientation change races the
    // dialog mount on slow devices.
    var immersiveFrames by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        LaunchedEffect(Unit) {
            val window = dialogWindow ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        SideEffect {
            // Belt-and-suspenders: the dialog window can change when the orientation
            // rotation kicks in (after requestedOrientation = SENSOR_LANDSCAPE). Re-hide
            // the system bars a few times so the immersive state sticks.
            if (immersiveFrames < 5) {
                dialogWindow?.let { window ->
                    WindowCompat.getInsetsController(window, window.decorView)
                        .hide(WindowInsetsCompat.Type.systemBars())
                }
                immersiveFrames++
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

/** Walks up the [ContextWrapper] chain to find the hosting [Activity]. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
