package app.rocat.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.rocat.core.common.network.DnsMode
import app.rocat.di.AppViewModelFactory
import app.rocat.i18n.AppLanguage
import app.rocat.i18n.LocalStrings
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource
import app.rocat.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.settingsState.collectAsState()
    val strings = LocalStrings.current
    val context = LocalContext.current

    var confirm by remember { mutableStateOf<StringKey?>(null) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> viewModel.onStoragePicked(uri) }

    val message = state.message
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, strings[it], Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    confirm?.let { key ->
        ConfirmDialog(
            title = key,
            onConfirm = {
                when (key) {
                    StringKey.clearCache -> viewModel.clearCache()
                    StringKey.clearCookies -> viewModel.deleteCookies()
                    StringKey.clearHistory -> viewModel.deleteHistory()
                    else -> Unit
                }
                confirm = null
            },
            onDismiss = { confirm = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(StringKey.settingsTitle)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(strings[StringKey.appearance])
            ThemePreviewRow(
                selected = state.themeMode,
                onSelect = viewModel::setThemeMode,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(strings[StringKey.language])
            ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    LanguageRow(
                        languages = AppLanguage.entries,
                        selected = state.language,
                        onSelect = viewModel::setLanguage,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(strings[StringKey.storage])
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(strings[StringKey.storageStatus], style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = state.storageName.ifBlank {
                                strings[if (state.storageConfigured) StringKey.storageConfigured else StringKey.storageNotConfigured]
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        }
                    }
                    OutlinedButton(
                        onClick = { folderLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Text(strings[StringKey.changeStorage])
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(strings[StringKey.network])
            NetworkSettingsSection(
                userAgent = state.userAgent,
                onUserAgentChange = viewModel::setUserAgent,
                dnsMode = state.dnsMode,
                onDnsModeChange = viewModel::setDnsMode,
                customDnsUrl = state.customDnsUrl,
                onCustomDnsUrlChange = viewModel::setCustomDnsUrl,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(strings[StringKey.developerOptions])
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                ListItem(
                    headlineContent = { Text(strings[StringKey.webViewDebugging]) },
                    supportingContent = { Text(strings[StringKey.webViewDebuggingBody]) },
                    leadingContent = { Icon(Icons.Filled.Palette, contentDescription = null) },
                    trailingContent = {
                        Switch(checked = state.webViewDebugging, onCheckedChange = viewModel::setWebViewDebugging)
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(strings[StringKey.dataManagement])
            SettingsActionRow(
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                title = strings[StringKey.clearCache],
                subtitle = strings[StringKey.clearCacheConfirm],
                enabled = !state.busy,
                onClick = { confirm = StringKey.clearCache },
            )
            SettingsActionRow(
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                title = strings[StringKey.clearCookies],
                subtitle = strings[StringKey.clearCookiesConfirm],
                enabled = !state.busy,
                onClick = { confirm = StringKey.clearCookies },
            )
            SettingsActionRow(
                icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                title = strings[StringKey.clearHistory],
                subtitle = strings[StringKey.clearHistoryConfirm],
                enabled = !state.busy,
                onClick = { confirm = StringKey.clearHistory },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageRow(
    languages: List<AppLanguage>,
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = LocalStrings.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = strings.languageLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(strings[StringKey.language]) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.forEach { language ->
                DropdownMenuItem(
                    text = { Text(strings.languageLabel(language)) },
                    onClick = {
                        onSelect(language)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val strings = LocalStrings.current
    Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        listOf(
            Triple(ThemeMode.SYSTEM, StringKey.themeSystem, Icons.Filled.SettingsBrightness),
            Triple(ThemeMode.LIGHT, StringKey.themeLight, Icons.Filled.WbSunny),
            Triple(ThemeMode.DARK, StringKey.themeDark, Icons.Filled.DarkMode),
        ).forEach { (mode, label, icon) ->
            val active = selected == mode
            Card(
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f).height(88.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = androidx.compose.material3.CardDefaults.cardElevation(
                    defaultElevation = if (active) 4.dp else 1.dp,
                ),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(icon, contentDescription = null)
                    Text(strings[label], style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/**
 * Tahap 20.2: the "Network"-category settings. Custom User-Agent (blank = default) plus
 * a DNS-over-HTTPS provider dropdown. When [DnsMode.CUSTOM] is selected an extra field
 * appears for the DoH endpoint URL.
 */
@Composable
private fun NetworkSettingsSection(
    userAgent: String,
    onUserAgentChange: (String) -> Unit,
    dnsMode: DnsMode,
    onDnsModeChange: (DnsMode) -> Unit,
    customDnsUrl: String,
    onCustomDnsUrlChange: (String) -> Unit,
) {
    val strings = LocalStrings.current

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(strings[StringKey.userAgent], style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = userAgent,
                onValueChange = onUserAgentChange,
                label = { Text(strings[StringKey.userAgent]) },
                supportingText = { Text(strings[StringKey.userAgentHint]) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (userAgent.isBlank()) {
                Text(
                    text = strings[StringKey.userAgentBlank],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(strings[StringKey.dnsSelection], style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            DnsModeRow(selected = dnsMode, onSelect = onDnsModeChange)
            if (dnsMode == DnsMode.CUSTOM) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customDnsUrl,
                    onValueChange = onCustomDnsUrlChange,
                    label = { Text(strings[StringKey.customDnsUrl]) },
                    placeholder = { Text(strings[StringKey.customDnsUrlHint]) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Dropdown of the available [DnsMode] options with localized labels. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DnsModeRow(
    selected: DnsMode,
    onSelect: (DnsMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = LocalStrings.current

    fun labelOf(mode: DnsMode): String = when (mode) {
        DnsMode.SYSTEM -> strings[StringKey.dnsSystemDefault]
        DnsMode.CLOUDFLARE -> strings[StringKey.dnsCloudflare]
        DnsMode.GOOGLE -> strings[StringKey.dnsGoogle]
        DnsMode.QUAD9 -> strings[StringKey.dnsQuad9]
        DnsMode.CUSTOM -> strings[StringKey.dnsCustom]
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = labelOf(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(strings[StringKey.dnsSelection]) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DnsMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(labelOf(mode)) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            icon()
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: StringKey,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val body = when (title) {
        StringKey.clearCache -> StringKey.clearCacheConfirm
        StringKey.clearCookies -> StringKey.clearCookiesConfirm
        StringKey.clearHistory -> StringKey.clearHistoryConfirm
        else -> StringKey.clearCacheConfirm
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings[title]) },
        text = { Text(strings[body]) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings[StringKey.delete], color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings[StringKey.cancelDelete])
            }
        },
    )
}
