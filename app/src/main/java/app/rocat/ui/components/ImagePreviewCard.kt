package app.rocat.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import coil3.compose.AsyncImage
import coil3.compose.rememberConstraintsSizeResolver
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.size.Scale

/**
 * Script-driven image preview card (Tahap 18.1). Shows the image loaded with Coil plus
 * a **save / download** button (when [allowDownload]) that streams the file into the
 * active scrape folder via [MediaDownloader] + [app.rocat.storage.StorageManager] and
 * confirms with a Toast.
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
    seamless: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val downloader = rememberMediaDownloaderState()
    val context = LocalContext.current
    val sizeResolver = rememberConstraintsSizeResolver()
    var fullScreen by remember { mutableStateOf(false) }
    var loadFailed by remember(url, headers) { mutableStateOf(false) }
    var isLoading by remember(url, headers) { mutableStateOf(true) } // Tambahkan state loading
    
    // Tambahkan 'headers' ke dalam remember
    val imageRequest = remember(url, headers) {
        ImageRequest.Builder(context).data(url).apply {
            if (headers.isNotEmpty()) {
                httpHeaders(
                    NetworkHeaders.Builder().apply {
                        headers.forEach { (name, value) -> set(name, value) }
                    }.build(),
                )
            }
        }
            // Avoid GPU texture limits for very tall webtoon pages. Coil performs the
            // bounded decode off the main thread using the measured Compose constraints.
            .allowHardware(false)
            .size(sizeResolver)
            .precision(Precision.INEXACT)
            .scale(Scale.FIT)
            .build()
    }

    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title.isNotBlank() && !seamless) {
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
                    contentScale = ContentScale.FillWidth,
                    onLoading = {
                        isLoading = true
                        loadFailed = false
                    },
                    onSuccess = { 
                        isLoading = false 
                    },
                    onError = { 
                        isLoading = false
                        loadFailed = true 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .then(sizeResolver)
                        .then(if (seamless) Modifier else Modifier.padding(8.dp))
                        .clickable { fullScreen = true },
                )
                
                // Tampilkan animasi muter-muter saat loading
                if (isLoading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                // Tampilkan teks error jika gagal
                if (loadFailed) {
                    Text(
                        text = "Gagal memuat gambar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                
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
            }
        }
    }
    if (seamless) {
        Box(modifier = modifier.fillMaxWidth()) { content() }
    } else {
        ElevatedCard(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) { content() }
    }
    if (fullScreen) {
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            val window = (LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            androidx.compose.runtime.LaunchedEffect(Unit) {
                window?.let { WindowCompat.getInsetsController(it, it.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clickable { fullScreen = false },
                )
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
