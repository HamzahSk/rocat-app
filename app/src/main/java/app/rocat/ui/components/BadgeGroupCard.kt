package app.rocat.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource

/**
 * Script-driven badge / chip group card (Tahap 22.2). Renders [badges] (e.g. genres,
 * status, quality, rating) as a wrapping row of rounded chips styled by the theme.
 * Tahap 31: a copy icon in the corner copies all badges (comma-joined) to the clipboard.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeGroupCard(
    badges: List<String>,
    modifier: Modifier = Modifier,
) {
    if (badges.isEmpty()) return

    ScriptCanvasCard(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(end = 32.dp),
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
            CopyIconButton(
                text = badges.joinToString(", "),
                label = stringResource(StringKey.copyText),
                message = stringResource(StringKey.textCopied),
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
