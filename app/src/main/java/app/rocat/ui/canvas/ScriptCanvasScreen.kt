package app.rocat.ui.canvas

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource
import app.rocat.ui.components.AlertBannerCard
import app.rocat.ui.components.AudioPreviewCard
import app.rocat.ui.components.BadgeGroupCard
import app.rocat.ui.components.GridComponent
import app.rocat.ui.components.HtmlPreviewCard
import app.rocat.ui.components.ImagePreviewCard
import app.rocat.ui.components.JsonLogCard
import app.rocat.ui.components.ScriptUIComponent
import app.rocat.ui.components.VideoPreviewCard

/**
 * The script "blank canvas" screen (mihon-like extension tab). Instead of a fixed
 * picker, the screen is empty except for a TopAppBar titled with the script's
 * metadata `@name`. The script owns the page: [ScriptCanvasViewModel] calls `onLaunch()`
 * automatically, and the script draws inputs, buttons, previews, a 3-column grid, or
 * redraws the whole page (Search -> Grid -> Detail) through `RoCatUI`.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScriptCanvasScreen(
    scriptId: String,
    onBack: () -> Unit,
    viewModel: ScriptCanvasViewModel = viewModel(
        key = "canvas_$scriptId",
        factory = remember(scriptId) { ScriptCanvasViewModel.Factory(scriptId) },
    ),
) {
    val state by viewModel.state.collectAsState()
    val components = viewModel.uiComponents

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.script?.name ?: stringResource(StringKey.script)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(StringKey.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::rebuildCanvas) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(StringKey.rebuildCanvas))
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            !state.loaded -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.script == null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(StringKey.scriptNotFound), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (components.isEmpty() && state.output.isEmpty() && !state.executing) {
                    item(key = "hint") {
                        AnimatedVisibility(
                            visible = components.isEmpty() && state.output.isEmpty() && !state.executing,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            CanvasEmptyHint(onRefresh = viewModel::rebuildCanvas)
                        }
                    }
                }

                itemsIndexed(components, key = { index, _ -> index }) { _, component ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .animateContentSize(),
                    ) {
                        when (component) {
                            is ScriptUIComponent.Input -> InputComponent(
                                component = component,
                                onValueChange = viewModel::updateInputValue,
                            )

                            is ScriptUIComponent.Button -> ButtonComponent(
                                label = component.label,
                                loading = viewModel.isButtonLoading(component.id),
                                onClick = {
                                    viewModel.onScriptButton(component.id, component.functionName)
                                },
                            )

                            is ScriptUIComponent.Image -> ImagePreviewCard(
                                url = component.url,
                                title = component.title,
                                allowDownload = component.allowDownload,
                                headers = component.headers,
                                folder = viewModel::scrapeFolder,
                                successMessage = stringResource(StringKey.imageSaved),
                                failureMessage = stringResource(StringKey.downloadFailed),
                                noStorageMessage = stringResource(StringKey.downloadFailedNoStorage),
                            )

                            is ScriptUIComponent.Video -> VideoPreviewCard(
                                url = component.url,
                                title = component.title,
                                isStreamHls = component.isStreamHls,
                                allowDownload = component.allowDownload,
                                headers = component.headers,
                                folder = viewModel::scrapeFolder,
                                playInlineLabel = stringResource(StringKey.playInline),
                                closePlayerLabel = stringResource(StringKey.closePlayer),
                                downloadLabel = stringResource(StringKey.downloadVideo),
                                successMessage = stringResource(StringKey.videoSaved),
                                failureMessage = stringResource(StringKey.downloadFailed),
                                noStorageMessage = stringResource(StringKey.downloadFailedNoStorage),
                            )

                            is ScriptUIComponent.LogText -> LogComponent(text = component.text)

                            is ScriptUIComponent.Grid -> GridComponent(
                                grid = component,
                                onItemClick = { item ->
                                    viewModel.onGridItemClick(component.onClickFunction, item.rawJsonPayload)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )

                            is ScriptUIComponent.JsonLog -> JsonLogCard(
                                dataJson = component.dataJson,
                                title = component.title,
                                allowCopy = component.allowCopy,
                                copyLabel = stringResource(StringKey.copyJson),
                                copiedMessage = stringResource(StringKey.jsonCopied),
                            )

                            is ScriptUIComponent.HtmlPreview -> HtmlPreviewCard(
                                htmlContent = component.htmlContent,
                                title = component.title,
                                copyLabel = stringResource(StringKey.copyHtml),
                                copiedMessage = stringResource(StringKey.htmlCopied),
                            )

                            is ScriptUIComponent.Audio -> AudioPreviewCard(
                                url = component.url,
                                title = component.title,
                                allowDownload = component.allowDownload,
                                headers = component.headers,
                                folder = viewModel::scrapeFolder,
                                playLabel = stringResource(StringKey.play),
                                pauseLabel = stringResource(StringKey.pause),
                                downloadLabel = stringResource(StringKey.downloadAudio),
                                successMessage = stringResource(StringKey.audioSaved),
                                failureMessage = stringResource(StringKey.downloadFailed),
                                noStorageMessage = stringResource(StringKey.downloadFailedNoStorage),
                            )

                            is ScriptUIComponent.Alert -> AlertBannerCard(
                                message = component.message,
                                type = component.type,
                                copyLabel = stringResource(StringKey.copyText),
                                copiedMessage = stringResource(StringKey.textCopied),
                            )

                            is ScriptUIComponent.BadgeGroup -> BadgeGroupCard(
                                badges = component.badges,
                                copyLabel = stringResource(StringKey.copyBadge),
                                copiedMessage = stringResource(StringKey.badgeCopied),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (state.executing || state.output.isNotEmpty()) {
                    item(key = "output") {
                        ConsoleOutput(
                            log = state.output,
                            executing = state.executing,
                            copyLabel = stringResource(StringKey.copyText),
                            copiedMessage = stringResource(StringKey.textCopied),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvasEmptyHint(onRefresh: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                stringResource(StringKey.blankCanvas),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(StringKey.blankCanvasBody),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRefresh) { Text(stringResource(StringKey.rerunOnLaunch)) }
        }
    }
}

@Composable
private fun InputComponent(
    component: ScriptUIComponent.Input,
    onValueChange: (String, String) -> Unit,
) {
    OutlinedTextField(
        value = component.value,
        onValueChange = { value -> onValueChange(component.id, value) },
        label = { Text(component.hint) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun ButtonComponent(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    // Tahap 31.1: a button only disables / shows a spinner when *its own* script
    // handler is in flight (`loading = viewModel.isButtonLoading(component.id)`).
    // Other buttons in the canvas stay fully clickable so the user is never locked
    // out of the script-driven flow.
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.width(16.dp).height(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable
private fun LogComponent(text: String) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
}

/** Console showing the return value (or error) of the last script invocation.
 *  Tahap 31.4: includes a **Copy** text button so the log / return value can be
 *  pasted elsewhere with one tap + Toast confirmation. */
@Composable
private fun ConsoleOutput(
    log: String,
    executing: Boolean,
    copyLabel: String,
    copiedMessage: String,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(StringKey.output),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (log.isNotBlank()) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(log))
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }) {
                        Text(
                            copyLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
            if (executing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(StringKey.running), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (log.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
