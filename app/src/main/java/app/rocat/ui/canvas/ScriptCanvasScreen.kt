package app.rocat.ui.canvas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.rocat.ui.components.flexWeight
import androidx.documentfile.provider.DocumentFile

/**
 * The script "blank canvas" screen (mihon-like extension tab). Instead of a fixed
 * picker, the screen is empty except for a TopAppBar titled with the script's
 * metadata `@name`. The script owns the page: [ScriptCanvasViewModel] calls `onLaunch()`
 * automatically, and the script draws inputs, buttons, previews, a 3-column grid, or
 * redraws the whole page (Search -> Grid -> Detail) through `RoCatUI`.
 *
 * Tahap 35: the canvas also renders flexible layouts (`layout` row/column/grid,
 * `group`), static text/dividers, and rich controls (checkbox/toggle/dropdown/number/
 * color picker/textarea/autocomplete with persisted history). [onOpenSettings] opens
 * the per-script settings page (also reachable from `RoCat.openSettings()`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScriptCanvasScreen(
    scriptId: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ScriptCanvasViewModel = viewModel(
        key = "canvas_$scriptId",
        factory = remember(scriptId) { ScriptCanvasViewModel.Factory(scriptId) },
    ),
) {
    val state by viewModel.state.collectAsState()
    val components = viewModel.uiComponents

    LaunchedEffect(viewModel.openSettingsRequest.value) {
        if (viewModel.openSettingsRequest.value > 0) onOpenSettings()
    }

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
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(StringKey.scriptSettings))
                    }
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
                        RenderComponent(
                            component = component,
                            executing = state.executing,
                            executingFunction = state.executingFunction,
                            folder = viewModel::scrapeFolder,
                            onFieldValue = viewModel::updateFieldValue,
                            onChecked = viewModel::updateChecked,
                            onNumberStep = viewModel::stepNumber,
                            onButton = viewModel::onScriptButton,
                            onGridItem = viewModel::onGridItemClick,
                            onLoadHistory = viewModel::loadHistory,
                            onClearHistory = viewModel::clearHistory,
                            historyFor = { viewModel.historyState[it] },
                        )
                        if (component !is ScriptUIComponent.Image || !component.seamless) {
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                if (state.executing || state.output.isNotEmpty()) {
                    item(key = "output") {
                        ConsoleOutput(log = state.output, executing = state.executing)
                    }
                }
            }
        }
    }
}

/** Renders any [ScriptUIComponent], recursing into group/layout containers. */
@Composable
private fun RenderComponent(
    component: ScriptUIComponent,
    executing: Boolean,
    executingFunction: String?,
    folder: () -> DocumentFile?,
    onFieldValue: (String, String) -> Unit,
    onChecked: (String, Boolean) -> Unit,
    onNumberStep: (String, Double) -> Unit,
    onButton: (String) -> Unit,
    onGridItem: (String, String) -> Unit,
    onLoadHistory: (String) -> Unit,
    onClearHistory: (String) -> Unit,
    historyFor: (String) -> List<String>?,
) {
    when (component) {
        is ScriptUIComponent.Input -> InputComponent(
            component = component,
            onValueChange = onFieldValue,
        )

        is ScriptUIComponent.Button -> ButtonComponent(
            label = component.label,
            enabled = !executing || executingFunction == component.functionName,
            loading = executing && executingFunction == component.functionName,
            onClick = { onButton(component.functionName) },
        )

        is ScriptUIComponent.Image -> ImagePreviewCard(
            url = component.url,
            title = component.title,
            allowDownload = component.allowDownload,
            headers = component.headers,
            seamless = component.seamless,
            folder = folder,
            successMessage = stringResource(StringKey.imageSaved),
            failureMessage = stringResource(StringKey.downloadFailed),
        )

        is ScriptUIComponent.Video -> VideoPreviewCard(
            url = component.url,
            title = component.title,
            isStreamHls = component.isStreamHls,
            allowDownload = component.allowDownload,
            headers = component.headers,
            folder = folder,
            playInlineLabel = stringResource(StringKey.playInline),
            closePlayerLabel = stringResource(StringKey.closePlayer),
            downloadLabel = stringResource(StringKey.downloadVideo),
            successMessage = stringResource(StringKey.videoSaved),
            failureMessage = stringResource(StringKey.downloadFailed),
        )

        is ScriptUIComponent.LogText -> LogComponent(text = component.text)

        is ScriptUIComponent.Grid -> GridComponent(
            grid = component,
            onItemClick = { item ->
                onGridItem(component.onClickFunction, item.rawJsonPayload)
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
        )

        is ScriptUIComponent.Audio -> AudioPreviewCard(
            url = component.url,
            title = component.title,
            allowDownload = component.allowDownload,
            headers = component.headers,
            folder = folder,
            playLabel = stringResource(StringKey.play),
            pauseLabel = stringResource(StringKey.pause),
            downloadLabel = stringResource(StringKey.downloadAudio),
            successMessage = stringResource(StringKey.audioSaved),
            failureMessage = stringResource(StringKey.downloadFailed),
        )

        is ScriptUIComponent.Alert -> AlertBannerCard(
            message = component.message,
            type = component.type,
        )

        is ScriptUIComponent.BadgeGroup -> BadgeGroupCard(badges = component.badges)

        is ScriptUIComponent.Text -> StaticTextComponent(component)
        is ScriptUIComponent.Divider -> DividerComponent(component)

        is ScriptUIComponent.Checkbox -> CheckboxComponent(
            component = component,
            onCheckedChange = { onChecked(component.id, it) },
        )

        is ScriptUIComponent.Toggle -> ToggleComponent(
            component = component,
            onCheckedChange = { onChecked(component.id, it) },
        )

        is ScriptUIComponent.Dropdown -> DropdownComponent(
            component = component,
            onValueChange = { onFieldValue(component.id, it) },
        )

        is ScriptUIComponent.Number -> NumberComponent(
            component = component,
            onValueChange = { onFieldValue(component.id, it) },
            onStep = { delta -> onNumberStep(component.id, delta) },
        )

        is ScriptUIComponent.ColorPicker -> ColorPickerComponent(
            component = component,
            onValueChange = { onFieldValue(component.id, it) },
        )

        is ScriptUIComponent.TextArea -> TextAreaComponent(
            component = component,
            onValueChange = { onFieldValue(component.id, it) },
        )

        is ScriptUIComponent.Autocomplete -> AutocompleteComponent(
            component = component,
            onValueChange = { onFieldValue(component.id, it) },
            onLoadHistory = onLoadHistory,
            onClearHistory = onClearHistory,
            historyFor = historyFor,
        )

        is ScriptUIComponent.Group -> GroupComponent(
            component = component,
            executing = executing,
            executingFunction = executingFunction,
            folder = folder,
            onFieldValue = onFieldValue,
            onChecked = onChecked,
            onNumberStep = onNumberStep,
            onButton = onButton,
            onGridItem = onGridItem,
            onLoadHistory = onLoadHistory,
            onClearHistory = onClearHistory,
            historyFor = historyFor,
        )

        is ScriptUIComponent.Layout -> LayoutComponent(
            component = component,
            executing = executing,
            executingFunction = executingFunction,
            folder = folder,
            onFieldValue = onFieldValue,
            onChecked = onChecked,
            onNumberStep = onNumberStep,
            onButton = onButton,
            onGridItem = onGridItem,
            onLoadHistory = onLoadHistory,
            onClearHistory = onClearHistory,
            historyFor = historyFor,
        )
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
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(onClick = onClick, enabled = enabled) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(label, maxLines = 1)
        }
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

// ---- Tahap 35: static pieces ----

@Composable
private fun StaticTextComponent(component: ScriptUIComponent.Text) {
    val style = when (component.style) {
        "heading" -> MaterialTheme.typography.headlineSmall
        "title" -> MaterialTheme.typography.titleMedium
        "caption" -> MaterialTheme.typography.labelMedium
        else -> MaterialTheme.typography.bodyMedium
    }
    Text(
        text = component.content,
        style = style,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun DividerComponent(component: ScriptUIComponent.Divider) {
    val fallbackColor = MaterialTheme.colorScheme.outlineVariant
    val color = remember(component.color) {
        runCatching { Color(android.graphics.Color.parseColor(component.color)) }
            .getOrDefault(fallbackColor)
    }
    HorizontalDivider(
        thickness = component.thickness.coerceIn(1, 8).dp,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

// ---- Tahap 35: rich controls ----

@Composable
private fun CheckboxComponent(
    component: ScriptUIComponent.Checkbox,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Checkbox(checked = component.checked, onCheckedChange = onCheckedChange)
        Text(component.label.ifBlank { component.id }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ToggleComponent(
    component: ScriptUIComponent.Toggle,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(component.label.ifBlank { component.id }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = component.checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownComponent(
    component: ScriptUIComponent.Dropdown,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = component.selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(component.label.ifBlank { component.id }) },
trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            component.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun NumberComponent(
    component: ScriptUIComponent.Number,
    onValueChange: (String) -> Unit,
    onStep: (Double) -> Unit,
) {
    val display = component.value?.let {
        if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
    } ?: ""
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = display,
            onValueChange = onValueChange,
            label = { Text(component.label.ifBlank { component.id }) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { onStep(-1.0) }, modifier = Modifier.height(48.dp)) { Text("−") }
        OutlinedButton(onClick = { onStep(1.0) }, modifier = Modifier.height(48.dp)) { Text("+") }
    }
}

@Composable
private fun ColorPickerComponent(
    component: ScriptUIComponent.ColorPicker,
    onValueChange: (String) -> Unit,
) {
    val fallbackColor = MaterialTheme.colorScheme.primary
    val previewColor = remember(component.color) {
        runCatching { Color(android.graphics.Color.parseColor(component.color)) }
            .getOrDefault(fallbackColor)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(previewColor, RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
        )
        OutlinedTextField(
            value = component.color,
            onValueChange = onValueChange,
            label = { Text(component.label.ifBlank { component.id }) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TextAreaComponent(
    component: ScriptUIComponent.TextArea,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = component.value,
        onValueChange = { onValueChange(it) },
        label = { Text(component.hint) },
        minLines = component.rows.coerceIn(2, 12),
        maxLines = component.rows.coerceIn(2, 12),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun AutocompleteComponent(
    component: ScriptUIComponent.Autocomplete,
    onValueChange: (String) -> Unit,
    onLoadHistory: (String) -> Unit,
    onClearHistory: (String) -> Unit,
    historyFor: (String) -> List<String>?,
) {
    LaunchedEffect(component.historyKey) { if (component.historyKey.isNotBlank()) onLoadHistory(component.historyKey) }
    val history = component.historyKey.let { key -> if (key.isNotBlank()) historyFor(key) else null }
    val suggestions = remember(component.suggestions, history) {
        (component.suggestions + (history ?: emptyList())).distinct()
    }
    val filtered = remember(component.value, suggestions) {
        suggestions.filter { it.contains(component.value, ignoreCase = true) }
    }
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = component.value,
            onValueChange = { value ->
                onValueChange(value)
                if (component.showHistory) expanded = true
            },
            label = { Text(component.hint) },
            singleLine = true,
            trailingIcon = {
                if (component.value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(StringKey.clearText),
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (expanded && filtered.isNotEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    filtered.take(component.maxHistory.coerceIn(1, 100)).forEach { suggestion ->
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(suggestion)
                                    expanded = false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        if (component.showClearHistory && component.historyKey.isNotBlank() && !history.isNullOrEmpty()) {
            TextButton(onClick = { onClearHistory(component.historyKey) }) {
                Text(stringResource(StringKey.clearHistory))
            }
        }
    }
}

// ---- Tahap 35: group & layout containers ----

@Composable
private fun GroupComponent(
    component: ScriptUIComponent.Group,
    executing: Boolean,
    executingFunction: String?,
    folder: () -> DocumentFile?,
    onFieldValue: (String, String) -> Unit,
    onChecked: (String, Boolean) -> Unit,
    onNumberStep: (String, Double) -> Unit,
    onButton: (String) -> Unit,
    onGridItem: (String, String) -> Unit,
    onLoadHistory: (String) -> Unit,
    onClearHistory: (String) -> Unit,
    historyFor: (String) -> List<String>?,
) {
    var collapsed by remember { mutableStateOf(component.collapsed) }
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { collapsed = !collapsed }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                component.title.ifBlank { stringResource(StringKey.options) },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }
        if (!collapsed) {
            HorizontalDivider()
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                component.children.forEach { child ->
                    RenderComponent(
                        component = child,
                        executing = executing,
                        executingFunction = executingFunction,
                        folder = folder,
                        onFieldValue = onFieldValue,
                        onChecked = onChecked,
                        onNumberStep = onNumberStep,
                        onButton = onButton,
                        onGridItem = onGridItem,
                        onLoadHistory = onLoadHistory,
                        onClearHistory = onClearHistory,
                        historyFor = historyFor,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun LayoutComponent(
    component: ScriptUIComponent.Layout,
    executing: Boolean,
    executingFunction: String?,
    folder: () -> DocumentFile?,
    onFieldValue: (String, String) -> Unit,
    onChecked: (String, Boolean) -> Unit,
    onNumberStep: (String, Double) -> Unit,
    onButton: (String) -> Unit,
    onGridItem: (String, String) -> Unit,
    onLoadHistory: (String) -> Unit,
    onClearHistory: (String) -> Unit,
    historyFor: (String) -> List<String>?,
) {
    val pad = component.padding.coerceIn(0, 32)
    val margin = component.margin.coerceIn(0, 32)
    val spacing = component.spacing.coerceIn(0, 32)
    val alignment = when (component.align) { "center" -> Alignment.CenterVertically; "end" -> Alignment.Bottom; else -> Alignment.Top }
    when (component.layout) {
        "row" -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = margin.dp),
                verticalAlignment = alignment,
                horizontalArrangement = Arrangement.spacedBy(spacing.dp),
            ) {
                component.children.forEachIndexed { index, child ->
                    if (index > 0 && component.divider) Spacer(Modifier.width(spacing.dp))
                    Box(modifier = Modifier.weight(child.flexWeight.toFloat())) {
                        RenderComponent(
                            component = child,
                            executing = executing,
                            executingFunction = executingFunction,
                            folder = folder,
                            onFieldValue = onFieldValue,
                            onChecked = onChecked,
                            onNumberStep = onNumberStep,
                            onButton = onButton,
                            onGridItem = onGridItem,
                            onLoadHistory = onLoadHistory,
                            onClearHistory = onClearHistory,
                            historyFor = historyFor,
                        )
                    }
                }
            }
        }

        "grid" -> {
            val columns = component.columns.coerceIn(1, 8)
            val chunks = component.children.chunked(columns)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = margin.dp), verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
                chunks.forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = alignment,
                        horizontalArrangement = Arrangement.spacedBy(spacing.dp),
                    ) {
                        chunk.forEachIndexed { index, child ->
                            if (index > 0 && component.divider) Spacer(Modifier.width(spacing.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                RenderComponent(
                                    component = child,
                                    executing = executing,
                                    executingFunction = executingFunction,
                                    folder = folder,
                                    onFieldValue = onFieldValue,
                                    onChecked = onChecked,
                                    onNumberStep = onNumberStep,
                                    onButton = onButton,
                                    onGridItem = onGridItem,
                                    onLoadHistory = onLoadHistory,
                                    onClearHistory = onClearHistory,
                                    historyFor = historyFor,
                                )
                            }
                        }
                        repeat(columns - chunk.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        else -> {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = margin.dp).padding(pad.dp), verticalArrangement = Arrangement.spacedBy(spacing.dp)) {
                component.children.forEachIndexed { index, child ->
                    if (index > 0 && component.divider) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    RenderComponent(
                        component = child,
                        executing = executing,
                        executingFunction = executingFunction,
                        folder = folder,
                        onFieldValue = onFieldValue,
                        onChecked = onChecked,
                        onNumberStep = onNumberStep,
                        onButton = onButton,
                        onGridItem = onGridItem,
                        onLoadHistory = onLoadHistory,
                        onClearHistory = onClearHistory,
                        historyFor = historyFor,
                    )
                }
            }
        }
    }
}

/** Console showing the return value (or error) of the last script invocation. */
@Composable
private fun ConsoleOutput(log: String, executing: Boolean) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(StringKey.output),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
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
