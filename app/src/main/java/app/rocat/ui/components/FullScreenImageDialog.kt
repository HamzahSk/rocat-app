package app.rocat.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

/**
 * Immersive full-screen image preview dialog (Tahap 31.2). Replaces the previous
 * "flat" inline-card-zoom-out effect with a true overlay that:
 *
 *  - Fills the entire window (`Dialog(usePlatformDefaultWidth = false)` +
 *    `decorFitsSystemWindows = false`).
 *  - Hides the system bars (status + navigation) with the swipe-to-show behaviour so
 *    the picture fills the device edge-to-edge.
 *  - Restores the original orientation & system bar state when dismissed, even if
 *    the parent composition is recomposed or the activity is recreated.
 *
 * [headers] (Tahap 24.1) are forwarded to the Coil request so hotlink-protected
 * hosts render correctly in the fullscreen viewer too.
 */
@Composable
fun FullScreenImageDialog(
    url: String,
    title: String = "",
    headers: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val dialogWindow = remember(view) { (view.parent as? DialogWindowProvider)?.window }

    // Track whether we successfully requested immersive mode so we can restore it
    // exactly on dismiss (avoid leaving the bars hidden if the dialog is recreated).
    var immersiveApplied by remember { mutableStateOf(false) }

    val imageRequest = remember(url, headers) {
        ImageRequest.Builder(context).data(url).apply {
            if (headers.isNotEmpty()) {
                httpHeaders(
                    NetworkHeaders.Builder().apply {
                        headers.forEach { (name, value) -> set(name, value) }
                    }.build(),
                )
            }
        }.build()
    }

    LaunchedEffect(dialogWindow) {
        val window = dialogWindow ?: return@LaunchedEffect
        val controller: WindowInsetsControllerCompat =
            WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        immersiveApplied = true
    }

    DisposableEffect(Unit) {
        onDispose {
            if (immersiveApplied) {
                dialogWindow?.let { window ->
                    WindowCompat.getInsetsController(window, window.decorView)
                        .show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = title.ifBlank { "Fullscreen image preview" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            // Close button — TopEnd, transparent black backdrop so the icon stays
            // legible against any image colour (Tahap 31.2 anti-flat UI fix).
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close fullscreen",
                    tint = Color.White,
                )
            }

            // Title bottom-left, optional
            if (title.isNotBlank()) {
                androidx.compose.material3.Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Forward to MaterialTheme for places that need the spinner colour of the dialog —
 * kept here so future contributors don't have to import the MaterialTheme just to
 * reference its [MaterialTheme.colorScheme.primary].
 */
@Suppress("unused")
private val _spinnerColor: Color = Color.White
