package app.rocat.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Script-driven badge / chip group card (Tahap 22.2). Renders [badges] (e.g. genres,
 * status, quality, rating) as a wrapping row of rounded chips styled by the theme.
 *
 * **Tahap 31.4**: when [allowCopy] is true a small **Copy** button sits in the header,
 * mirroring the JSON log / HTML preview pattern — copies the comma-joined badge
 * labels to the clipboard and confirms with [copiedMessage] via Toast.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeGroupCard(
    badges: List<String>,
    allowCopy: Boolean = true,
    copyLabel: String = "Copy",
    copiedMessage: String = "Copied to clipboard",
    modifier: Modifier = Modifier,
) {
    if (badges.isEmpty()) return

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    ScriptCanvasCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            if (allowCopy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(badges.joinToString(", ")))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }) {
                        Text(
                            copyLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                badges.forEach { badge ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}
