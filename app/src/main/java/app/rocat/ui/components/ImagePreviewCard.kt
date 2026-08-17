package app.rocat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.documentfile.provider.DocumentFile
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource

/**
 * Script-driven image preview card (Tahap 18.1). Shows the image loaded with Coil plus
 * a **save / download** button (when [allowDownload]) that streams the file into the
 * active scrape folder via [MediaDownloader] + [app.rocat.storage.StorageManager] and
 * confirms with a Toast.
 *
 * Tahap 31: tapping the preview opens a **full-screen immersive viewer** (edge-to-edge,
 * system bars hidden with swipe-to-reveal, soft top scrim) and a copy-URL action is
 * always available.
 *
 * [headers] (Tahap 24.1) are attached to the Coil [ImageRequest] and the download so
 * hotlink-protected hosts (which require a `Referer`) serve the image.
 */
@Composable
fun ImagePreviewCard(
    url: String,
    title: String = "",
    allowDownload: Boolean = true,
    headers: Map<String, String> = emptyMap(),
    folder: () -> DocumentFile?,
    successMessage: String,
    failureMessage: String,
    modifier: Modifier = Modifier,
) {
    val downloader = rememberMediaDownloaderState()
    val context = LocalContext.current
    var showFullScreen by remember { mutableStateOf(false) }
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

    ElevatedCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                )
                Spacer(Modifier.height(4.dp))
            }
            Box {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = title.ifBlank { "Script image preview" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .padding(8.dp)
                        .clickable { showFullScreen = true },
                )
                if (allowDownload) {
                    DownloadActionButton(
                        status = downloader.status,
                        onClick = {
                            downloader.start(
                                url = url,
                                folder = folder(),
                                fileName = fileNameFromUrl(url, fallback = "image"),
                                mimeType = imageMimeFor(url),
                                headers = headers,
                                successMessage = successMessage,
                                failureMessage = failureMessage,
                            )
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
                CopyIconButton(
                    text = url,
                    label = stringResource(StringKey.copyUrl),
                    message = stringResource(StringKey.urlCopied),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                )
            }
        }
    }

    if (showFullScreen) {
        FullScreenImageDialog(
            imageRequest = imageRequest,
            onExit = { showFullScreen = false },
        )
    }
}

/** A window-filling immersive dialog showing [imageRequest] edge-to-edge on black. */
@Composable
private fun FullScreenImageDialog(
    imageRequest: ImageRequest,
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
            // Same immersive handling as the full-screen video: hide system bars on the
            // dialog window (and the activity window as a fallback) with swipe-to-reveal.
            listOfNotNull(dialogWindow, activity?.window).forEach { window ->
                window.hideSystemBars()
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            )

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
                Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
            }
        }
    }
}

/** Picks a sensible MIME type for an image URL based on its file extension. */
internal fun imageMimeFor(url: String): String = when (url.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "jpeg", "jpg", "jfif" -> "image/jpeg"
    else -> "image/jpeg"
}

/** Returns the last path segment of [url] as a file name, falling back to [fallback]. */
internal fun fileNameFromUrl(url: String, fallback: String): String {
    val segment = url.substringBefore('?').substringBefore('#').substringAfterLast('/').trim()
    return segment.takeIf { it.isNotBlank() && !it.endsWith("/") && it.contains(".") } ?: fallback
}