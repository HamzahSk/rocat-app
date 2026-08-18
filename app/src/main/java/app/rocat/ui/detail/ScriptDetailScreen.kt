package app.rocat.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource
import app.rocat.scripting.api.model.Script

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptDetailScreen(
    scriptId: String,
    onBack: () -> Unit,
    viewModel: ScriptDetailViewModel = viewModel(
        key = "detail_$scriptId",
        factory = remember(scriptId) { ScriptDetailViewModel.Factory(scriptId) },
    ),
) {
    val state by viewModel.detailState.collectAsState()
    val script = state.script

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog && script != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(StringKey.deleteScriptTitle)) },
            text = { Text(stringResource(StringKey.deleteScriptBody, script.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(onBack)
                    },
                ) {
                    Text(stringResource(StringKey.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(StringKey.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(script?.name ?: stringResource(StringKey.script)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(StringKey.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(StringKey.delete))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (script == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(StringKey.scriptNotFound), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
        ) {
            MetadataCard(script, viewModel::setEnabled)
            MatchesCard(script)
            CodeSection(script, viewModel)
        }
    }
}

@Composable
private fun MetadataCard(script: Script, onToggle: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(script.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${stringResource(StringKey.version)} ${script.version}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(6.dp))
                    StatusChip(enabled = script.enabled)
                }
                Switch(checked = script.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(12.dp))
            DetailRow(stringResource(StringKey.description), script.description.ifBlank { "—" })
            DetailRow(stringResource(StringKey.author), script.author.ifBlank { "—" })
            if (script.icon.isNotBlank()) DetailRow(stringResource(StringKey.icon), script.icon)
            DetailRow(stringResource(StringKey.id), script.id)
        }
    }
}

@Composable
private fun StatusChip(enabled: Boolean) {
    val bg = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = stringResource(if (enabled) StringKey.active else StringKey.inactive),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MatchesCard(script: Script) {
    if (script.matches.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(StringKey.matches), style = MaterialTheme.typography.titleSmall)
            script.matches.forEach { match ->
                Text(
                    text = match,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun CodeSection(script: Script, viewModel: ScriptDetailViewModel) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(script.source) }
    var undo by remember { mutableStateOf(emptyList<String>()) }
    var redo by remember { mutableStateOf(emptyList<String>()) }
    val keywordColor = MaterialTheme.colorScheme.primary
    val stringColor = MaterialTheme.colorScheme.tertiary
    val commentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(StringKey.source), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (!editing) {
                    IconButton(onClick = {
                        draft = script.source
                        editing = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(StringKey.editSource))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()

            if (editing) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (undo.isNotEmpty()) { redo = redo + draft; draft = undo.last(); undo = undo.dropLast(1) } }, enabled = undo.isNotEmpty()) {
                        Icon(Icons.Filled.Undo, contentDescription = stringResource(StringKey.undo))
                    }
                    IconButton(onClick = { if (redo.isNotEmpty()) { undo = undo + draft; draft = redo.last(); redo = redo.dropLast(1) } }, enabled = redo.isNotEmpty()) {
                        Icon(Icons.Filled.Redo, contentDescription = stringResource(StringKey.redo))
                    }
                    OutlinedButton(onClick = { undo = undo + draft; draft = formatJavaScript(draft); redo = emptyList() }) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                        Spacer(Modifier.width(6.dp)); Text(stringResource(StringKey.format))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        text = (1..draft.lineSequence().count()).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 16.dp, end = 8.dp),
                    )
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { value ->
                            if (value != draft) undo = (undo + draft).takeLast(100)
                            draft = value
                            redo = emptyList()
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 280.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        visualTransformation = remember(keywordColor, stringColor, commentColor) {
                            JavaScriptHighlighting(keywordColor, stringColor, commentColor)
                        },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.saveSource(draft) { editing = false } }) {
                        Text(stringResource(StringKey.save))
                    }
                    OutlinedButton(onClick = { editing = false; draft = script.source }) {
                        Text(stringResource(StringKey.cancel))
                    }
                }
            } else {
                Text(
                    text = script.source,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

private class JavaScriptHighlighting(
    private val keywordColor: Color,
    private val stringColor: Color,
    private val commentColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        Regex("\\b(function|var|let|const|if|else|for|while|return|new|try|catch|throw|true|false|null|undefined)\\b")
            .findAll(text.text).forEach { builder.addStyle(SpanStyle(color = keywordColor), it.range.first, it.range.last + 1) }
        Regex("(['\"])(?:\\\\.|(?!\\1).)*\\1").findAll(text.text)
            .forEach { builder.addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1) }
        Regex("//.*|/\\*[\\s\\S]*?\\*/").findAll(text.text)
            .forEach { builder.addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1) }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private fun formatJavaScript(source: String): String {
    var indent = 0
    return source.lines().joinToString("\n") { raw ->
        val line = raw.trim()
        if (line.startsWith("}")) indent = (indent - 1).coerceAtLeast(0)
        val formatted = "    ".repeat(indent) + line
        val opens = line.count { it == '{' }
        val closes = line.count { it == '}' } - if (line.startsWith("}")) 1 else 0
        indent = (indent + opens - closes).coerceAtLeast(0)
        formatted
    }.trimEnd()
}
