package app.rocat.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * Shared "copy to clipboard" actions for the script canvas template cards (Tahap 31).
 * Copies [text] via the [LocalClipboardManager] and confirms with a [Toast] carrying
 * [message], so every card reports its own i18n feedback instead of a silent no-op.
 */

/** A compact icon-only copy button (e.g. top-right corner of a card). */
@Composable
fun CopyIconButton(
    text: String,
    label: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
    ) {
        Icon(
            Icons.Filled.ContentCopy,
            contentDescription = label,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** A labeled "Copy" [TextButton] (e.g. on log / console / JSON cards). */
@Composable
fun CopyTextButton(
    text: String,
    label: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    TextButton(
        onClick = {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
    ) {
        Icon(
            Icons.Filled.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}