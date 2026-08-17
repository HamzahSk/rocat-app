package app.rocat.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val prettyJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** Pretty-prints a JSON string (falls back to the raw input on any parse failure). */
internal fun prettyPrintJson(dataJson: String): String {
    val trimmed = dataJson.trim()
    if (trimmed.isEmpty()) return trimmed
    return try {
        prettyJson.encodeToString(JsonElement.serializer(), prettyJson.parseToJsonElement(trimmed))
    } catch (e: Exception) {
        dataJson
    }
}

/** Minimal JSON syntax highlighting: keys in [keyColor], strings in [valueColor],
 *  literals/numbers in [numberColor]. */
internal fun highlightJson(
    text: String,
    keyColor: Color,
    valueColor: Color,
    numberColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(text)
    Regex("\"([^\"]*)\"(\\s*:)").findAll(text).forEach { match ->
        addStyle(SpanStyle(color = keyColor), match.range.first, match.range.last + 1)
    }
    Regex(":\\s*(\"[^\"]*\")").findAll(text).forEach { match ->
        val range = match.groups[1]!!.range
        addStyle(SpanStyle(color = valueColor), range.first, range.last + 1)
    }
    Regex(":\\s*(-?\\d+\\.?\\d*|true|false|null)").findAll(text).forEach { match ->
        val range = match.groups[1]!!.range
        addStyle(
            SpanStyle(color = numberColor, fontWeight = FontWeight.SemiBold),
            range.first,
            range.last + 1,
        )
    }
}

/**
 * Script-driven JSON log viewer card (Tahap 22.2). Displays [dataJson] pretty-printed
 * in a monospaced, scrollable block with simple syntax highlighting. When [allowCopy]
 * a **Copy JSON** button copies the formatted JSON to the clipboard and confirms with a
 * Toast.
 */
@Composable
fun JsonLogCard(
    dataJson: String,
    title: String = "",
    allowCopy: Boolean = true,
    copyLabel: String,
    copiedMessage: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyColor = MaterialTheme.colorScheme.primary
    val valueColor = MaterialTheme.colorScheme.tertiary
    val numberColor = MaterialTheme.colorScheme.secondary

    val pretty = remember(dataJson) { prettyPrintJson(dataJson) }
    val highlighted = remember(pretty, keyColor, valueColor, numberColor) {
        highlightJson(pretty, keyColor, valueColor, numberColor)
    }

    ScriptCanvasCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
                )
            }
            Box {
                Text(
                    text = highlighted,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                )
                if (allowCopy && pretty.isNotBlank()) {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(pretty))
                            Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Text(
                            copyLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
    }
}
