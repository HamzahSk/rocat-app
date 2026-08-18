package app.rocat.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.rocat.domain.script.ScriptSetting
import app.rocat.domain.script.ScriptSettingType
import app.rocat.i18n.LocalStrings
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource

/**
 * The per-script settings page (Tahap 35): renders every `@settings` declared by the
 * script as the matching control (boolean -> switch, number -> numeric field, select ->
 * dropdown, ...), persists edits live and offers reset-to-default plus JSON export/import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptSettingsScreen(
    scriptId: String,
    onBack: () -> Unit,
    viewModel: ScriptSettingsViewModel = viewModel(
        key = "script_settings_$scriptId",
        factory = remember(scriptId) { ScriptSettingsViewModel.Factory(scriptId) },
    ),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val strings = LocalStrings.current

    var importOpen by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    LaunchedEffect(viewModel.toastEvents) {
        viewModel.toastEvents.collect { key ->
            Toast.makeText(context, strings[key], Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.script?.name?.let { "${stringResource(StringKey.scriptSettings)} — $it" } ?: stringResource(StringKey.scriptSettings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(StringKey.back))
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

            state.settings.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(StringKey.noSettingsDeclared),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(stringResource(StringKey.settingsSummary), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(StringKey.settingsSummaryBody, state.settings.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.settings.forEach { setting ->
                    SettingRow(
                        setting = setting,
                        value = viewModel.currentValue(setting),
                        onValueChange = { viewModel.setValue(setting, it) },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    OutlinedButton(onClick = viewModel::resetToDefault, modifier = Modifier.weight(1f)) {
                        Text(stringResource(StringKey.resetToDefault))
                    }
                    OutlinedButton(onClick = viewModel::export, modifier = Modifier.weight(1f)) {
                        Text(stringResource(StringKey.exportSettings))
                    }
                    OutlinedButton(onClick = { importText = ""; importOpen = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(StringKey.importSettings))
                    }
                }
            }
        }
    }

    // Export dialog: shows the JSON with a copy button.
    state.exportJson?.let { json ->
        AlertDialog(
            onDismissRequest = viewModel::dismissExport,
            title = { Text(stringResource(StringKey.exportSettings)) },
            text = {
                Column {
                    Text(stringResource(StringKey.exportSettingsBody), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = json,
                        onValueChange = {},
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(json))
                        viewModel.dismissExport()
                    },
                ) {
                    Text(stringResource(StringKey.copyJson))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissExport) { Text(stringResource(StringKey.cancel)) }
            },
        )
    }

    // Import dialog: pasted JSON payload.
    if (importOpen) {
        AlertDialog(
            onDismissRequest = { importOpen = false },
            title = { Text(stringResource(StringKey.importSettings)) },
            text = {
                Column {
                    Text(stringResource(StringKey.importSettingsBody), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text("""{"settings": {"username": "admin"}}""") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.import(importText)
                        importOpen = false
                    },
                ) {
                    Text(stringResource(StringKey.importSettings))
                }
            },
            dismissButton = {
                TextButton(onClick = { importOpen = false }) { Text(stringResource(StringKey.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingRow(
    setting: ScriptSetting,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    setting.displayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    setting.type.wire,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (setting.placeholder.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    setting.placeholder,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            when (setting.type) {
                ScriptSettingType.BOOLEAN -> Switch(
                    checked = value == "true",
                    onCheckedChange = { onValueChange(it.toString()) },
                )

                ScriptSettingType.SELECT -> SettingDropdown(setting, value, onValueChange)

                ScriptSettingType.COLOR -> SettingColorPicker(setting, value, onValueChange)

                ScriptSettingType.NUMBER -> OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(setting.key) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        val range = listOfNotNull(
                            setting.min?.let { "min $it" },
                            setting.max?.let { "max $it" },
                            setting.step?.let { "step $it" },
                        ).joinToString(", ")
                        if (range.isNotEmpty()) Text(range)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                ScriptSettingType.PASSWORD -> OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(setting.key) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )

                ScriptSettingType.EMAIL -> OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(setting.key) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )

                ScriptSettingType.MULTILINE -> OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(setting.key) },
                    minLines = setting.rows?.coerceIn(2, 12) ?: 3,
                    maxLines = setting.rows?.coerceIn(2, 12) ?: 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                ScriptSettingType.STRING -> OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(setting.key) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingDropdown(
    setting: ScriptSetting,
    value: String,
    onValueChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(setting.key) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            setting.options.forEach { option ->
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
private fun SettingColorPicker(
    setting: ScriptSetting,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val fallbackColor = MaterialTheme.colorScheme.primary
    val previewColor = remember(value) {
        runCatching { Color(android.graphics.Color.parseColor(value)) }
            .getOrDefault(fallbackColor)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(previewColor, RoundedCornerShape(6.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(setting.key) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}