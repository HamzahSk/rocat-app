package app.rocat.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

/**
 * Script-driven image preview card (Tahap 18.1). Shows the image loaded with Coil plus
 * a **save / download** button (when [allowDownload]) that streams the file into the
 * active scrape folder via [MediaDownloader] + [app.rocat.storage.StorageManager] and
 * confirms with a Toast.
 *
 * [headers] (Tahap 24.1) are attached to the Coil [ImageRequest] and the download so
 * hotlink-protected hosts (which require a `Referer`) serve the image.
 *
 * **Tahap 31.2**: tapping the image opens an immersive [FullScreenImageDialog]
 * (WindowInsets hidden, edge-to-edge, real overlay) — replaces the previous "flat"
 * inline-only behaviour so users get a proper fullscreen viewer.
 *
 * **Tahap 31.3**: [noStorageMessage] is shown via Toast when [folder] is null (the
 * user has not picked a storage folder yet), so the failure is no longer silent.
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
    noStorageMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val downloader = rememberMediaDownloaderState()
    val context = LocalContext.current
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
    // Tahap 31.2: tapping the image opens an immersive full-screen viewer so the
    // preview is no longer "flat" against the rest of the canvas.
    var fullScreen by remember { mutableStateOf(false) }

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
                        .clickable { fullScreen = true },
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
                                noStorageMessage = noStorageMessage,
                            )
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
            }
        }
    }

    if (fullScreen) {
        FullScreenImageDialog(
            url = url,
            title = title,
            headers = headers,
            onDismiss = { fullScreen = false },
        )
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
