package app.rocat.ui.downloads

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import app.rocat.core.common.injekt.Injekt
import app.rocat.downloads.DownloadItem
import app.rocat.downloads.DownloadRepository
import app.rocat.downloads.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(repository: DownloadRepository = remember { Injekt.get() }) {
    val downloads by repository.downloads.collectAsState()
    val context = LocalContext.current
    var deleteTarget by remember { mutableStateOf<DownloadItem?>(null) }

    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete download?") },
            text = { Text("Remove ${item.fileName} from the list and device storage?") },
            confirmButton = {
                TextButton(onClick = {
                    repository.remove(item.id, deleteFile = true)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Downloads")
                        Text(
                            text = "${downloads.count { it.state == DownloadState.COMPLETED }} saved • ${downloads.count { it.state == DownloadState.DOWNLOADING }} active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (downloads.any { it.state == DownloadState.COMPLETED }) {
                        TextButton(onClick = { repository.clearCompleted(deleteFiles = false) }) { Text("Clear") }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (downloads.isEmpty()) {
            EmptyDownloads(Modifier.fillMaxSize().padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(downloads, key = { it.id }) { item ->
                    DownloadCard(
                        item = item,
                        onOpen = {
                            val uri = item.contentUri?.let(Uri::parse) ?: return@DownloadCard
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, item.mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching { context.startActivity(intent) }.onFailure {
                                Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRetry = { repository.retry(item.id) },
                        onDelete = { deleteTarget = item },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloads(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Download,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(18.dp))
        Text("Your downloads live here", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "Files downloaded from webpages and scripts are saved to your selected storage folder and stay available after restart.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun DownloadCard(
    item: DownloadItem,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (item.state) {
                        DownloadState.DOWNLOADING, DownloadState.QUEUED -> CircularProgressIndicator(
                            progress = { item.progress ?: 0f },
                            modifier = Modifier.size(38.dp),
                        )
                        DownloadState.COMPLETED -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        DownloadState.FAILED -> Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = statusText(item, context),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.state == DownloadState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.state != DownloadState.DOWNLOADING && item.state != DownloadState.QUEUED) {
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }

            AnimatedVisibility(item.state == DownloadState.DOWNLOADING) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    val progress = item.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (item.state == DownloadState.COMPLETED || item.state == DownloadState.FAILED) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (item.state == DownloadState.FAILED) {
                        FilledTonalButton(onClick = onRetry) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry")
                        }
                    } else {
                        FilledTonalButton(onClick = onOpen) {
                            Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open")
                        }
                    }
                }
            }
        }
    }
}

private fun statusText(item: DownloadItem, context: android.content.Context): String = when (item.state) {
    DownloadState.QUEUED -> "Waiting to start • ${item.source}"
    DownloadState.DOWNLOADING -> {
        val received = Formatter.formatShortFileSize(context, item.bytesDownloaded)
        val total = item.totalBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
        val speed = Formatter.formatShortFileSize(context, item.bytesPerSecond) + "/s"
        listOfNotNull(if (total != null) "$received of $total" else received, speed).joinToString(" • ")
    }
    DownloadState.COMPLETED -> "${Formatter.formatShortFileSize(context, item.bytesDownloaded)} • ${item.source}"
    DownloadState.FAILED -> item.error ?: "Download failed"
}
