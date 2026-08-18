package app.rocat.ui.import

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import app.rocat.di.AppViewModelFactory
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScriptScreen(
    onBack: () -> Unit,
    viewModel: ImportScriptViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val source = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!source.isNullOrBlank()) {
                viewModel.onSourceChange(source)
                selectedTab = 1
            } else {
                Toast.makeText(context, "Unable to read script file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pasteInto(update: (String) -> Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        if (text.isNotBlank()) update(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(StringKey.addScriptTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(StringKey.back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()),
        ) {
            val message = state.message
            if (message != null) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val error = state.error
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            PrimaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(StringKey.importFromUrl)) },
                    icon = { Icon(Icons.Filled.Link, contentDescription = null) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(StringKey.pasteSource)) },
                    icon = { Icon(Icons.Filled.Code, contentDescription = null) },
                )
            }

            ElevatedCard(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    if (selectedTab == 0) {
                        Text(stringResource(StringKey.importFromUrlBody), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = state.url,
                            onValueChange = viewModel::onUrlChange,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            label = { Text(stringResource(StringKey.scriptUrl)) },
                            trailingIcon = {
                                IconButton(onClick = { pasteInto(viewModel::onUrlChange) }) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(StringKey.paste))
                                }
                            },
                            singleLine = true,
                        )
                        Button(onClick = { viewModel.importFromUrl { onBack() } }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            if (state.busy) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            else Text(stringResource(StringKey.fetchImport))
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            FilledTonalButton(onClick = { pasteInto(viewModel::onSourceChange) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = null)
                                Spacer(Modifier.width(8.dp)); Text(stringResource(StringKey.paste))
                            }
                            OutlinedButton(onClick = { fileLauncher.launch(arrayOf("application/javascript", "text/javascript", "text/plain")) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(8.dp)); Text(stringResource(StringKey.chooseFile))
                            }
                        }
                        OutlinedTextField(
                            value = state.source,
                            onValueChange = viewModel::onSourceChange,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 260.dp),
                            label = { Text(stringResource(StringKey.scriptSource)) },
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            OutlinedButton(onClick = viewModel::loadCanvasExample, modifier = Modifier.weight(1f)) { Text(stringResource(StringKey.canvasDemo)) }
                            OutlinedButton(onClick = viewModel::loadExample, modifier = Modifier.weight(1f)) { Text(stringResource(StringKey.loadExample)) }
                        }
                        Button(onClick = { viewModel.importFromSource { onBack() } }, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(StringKey.importSource)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
