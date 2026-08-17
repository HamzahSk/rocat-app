package app.rocat.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp

/** Alert severity for [AlertBannerCard]; drives the icon + container colors. */
enum class AlertType(val key: String, val icon: ImageVector) {
    Info("info", Icons.Filled.Info),
    Warning("warning", Icons.Filled.Warning),
    Error("error", Icons.Filled.Error),
    Success("success", Icons.Filled.CheckCircle),
    ;

    companion object {
        /** Tolerant lookup — anything that is not a known type falls back to info. */
        fun from(raw: String?): AlertType =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: Info
    }
}

/**
 * Script-driven alert / banner card (Tahap 22.2). Renders [message] as an [ElevatedCard]
 * tinted by its [AlertType] ([type] is a string like `"info"`, `"warning"`, `"error"` or
 * `"success"`; unknown values fall back to info) with a matching leading icon.
 *
 * **Tahap 31.4**: long-pressing the banner copies [message] to the clipboard and
 * confirms with [copiedMessage] via Toast — a low-friction affordance for scripts
 * that publish useful debug text (e.g. "Scrape failed: 403 forbidden").
 */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlertBannerCard(
    message: String,
    type: String = "info",
    allowCopy: Boolean = true,
    copyLabel: String = "Copy",
    copiedMessage: String = "Copied to clipboard",
    modifier: Modifier = Modifier,
) {
    val alertType = remember(type) { AlertType.from(type) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val containerColor = when (alertType) {
        AlertType.Info -> MaterialTheme.colorScheme.secondaryContainer
        AlertType.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        AlertType.Error -> MaterialTheme.colorScheme.errorContainer
        AlertType.Success -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (alertType) {
        AlertType.Info -> MaterialTheme.colorScheme.onSecondaryContainer
        AlertType.Warning -> MaterialTheme.colorScheme.onTertiaryContainer
        AlertType.Error -> MaterialTheme.colorScheme.onErrorContainer
        AlertType.Success -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    ScriptCanvasCard(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .then(
                    if (allowCopy) Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(message))
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                        onLongClickLabel = copyLabel,
                    ) else Modifier,
                ),
        ) {
            Icon(
                imageVector = alertType.icon,
                contentDescription = null,
                tint = contentColor,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
