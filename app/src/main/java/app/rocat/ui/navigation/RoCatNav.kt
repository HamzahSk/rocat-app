package app.rocat.ui.navigation

import androidx.activity.compose.BackHandler
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import app.rocat.core.common.injekt.Injekt
import app.rocat.i18n.I18nApp
import app.rocat.i18n.I18nProvider
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource
import app.rocat.storage.StorageManager
import app.rocat.ui.browser.BrowserScreen
import app.rocat.ui.canvas.ScriptCanvasScreen
import app.rocat.ui.detail.ScriptDetailScreen
import app.rocat.ui.downloads.DownloadsScreen
import app.rocat.ui.home.HomeScreen
import app.rocat.ui.import.ImportScriptScreen
import app.rocat.ui.scripts.ScriptsScreen
import app.rocat.ui.settings.ScriptSettingsScreen
import app.rocat.ui.settings.SettingsScreen
import app.rocat.ui.settings.StorageSetupScreen

/** Lightweight, dependency-free navigation mirroring mihon's extension screens. */
sealed interface Screen {
    data object Home : Screen
    data object Scripts : Screen
    data class Detail(val scriptId: String) : Screen
    data class Import(val sourceUrl: String? = null) : Screen
    data class Browser(val url: String? = null) : Screen
    /** The script-driven blank canvas (the script draws its own UI via `RoCatUI`). */
    data class Canvas(val scriptId: String) : Screen
    /** Per-script settings page driven by `@settings` metadata (Tahap 35). */
    data class ScriptSettings(val scriptId: String) : Screen
    data object Settings : Screen
    data object Downloads : Screen
}

private const val KEY_HOME = "home"
private const val KEY_SCRIPTS = "scripts"
private const val KEY_IMPORT = "import"
private const val KEY_BROWSER = "browser"
private const val KEY_SETTINGS = "settings"
private const val KEY_DOWNLOADS = "downloads"
private const val KEY_DETAIL_PREFIX = "detail:"
private const val KEY_CANVAS_PREFIX = "canvas:"
private const val KEY_SCRIPT_SETTINGS_PREFIX = "script_settings:"

private fun encode(screen: Screen): String = when (screen) {
    is Screen.Home -> KEY_HOME
    is Screen.Scripts -> KEY_SCRIPTS
    is Screen.Import -> screen.sourceUrl?.let { "$KEY_IMPORT:${Uri.encode(it)}" } ?: KEY_IMPORT
    is Screen.Browser -> screen.url?.let { "$KEY_BROWSER:${Uri.encode(it)}" } ?: KEY_BROWSER
    is Screen.Settings -> KEY_SETTINGS
    is Screen.Downloads -> KEY_DOWNLOADS
    is Screen.Detail -> KEY_DETAIL_PREFIX + screen.scriptId
    is Screen.Canvas -> KEY_CANVAS_PREFIX + screen.scriptId
    is Screen.ScriptSettings -> KEY_SCRIPT_SETTINGS_PREFIX + screen.scriptId
}

private fun decode(key: String): Screen = when {
    key == KEY_HOME -> Screen.Home
    key == KEY_SCRIPTS -> Screen.Scripts
    key == KEY_IMPORT -> Screen.Import()
    key.startsWith("$KEY_IMPORT:") -> Screen.Import(Uri.decode(key.substringAfter(':')))
    key == KEY_BROWSER -> Screen.Browser()
    key.startsWith("$KEY_BROWSER:") -> Screen.Browser(Uri.decode(key.substringAfter(':')))
    key == KEY_SETTINGS -> Screen.Settings
    key == KEY_DOWNLOADS -> Screen.Downloads
    key.startsWith(KEY_DETAIL_PREFIX) -> Screen.Detail(key.removePrefix(KEY_DETAIL_PREFIX))
    key.startsWith(KEY_CANVAS_PREFIX) -> Screen.Canvas(key.removePrefix(KEY_CANVAS_PREFIX))
    key.startsWith(KEY_SCRIPT_SETTINGS_PREFIX) -> Screen.ScriptSettings(key.removePrefix(KEY_SCRIPT_SETTINGS_PREFIX))
    else -> Screen.Scripts
}

@Composable
fun RoCatApp(initialUrl: String? = null) {
    // Tahap 15.1: reactive custom i18n provider.
    val i18nProvider: I18nProvider = remember { Injekt.get() }
    val strings by i18nProvider.strings.collectAsState()
    val language by i18nProvider.language.collectAsState()

    // Tahap 15.2: storage access — gate the whole UI until the main folder is chosen.
    val storageManager: StorageManager = remember { Injekt.get() }

    // Tahap 17.1: `isConfigured` is a StateFlow now, so choosing the folder in
    // StorageSetupScreen recomposes this composable and swaps to the main nav instantly
    // (no app restart required).
    val storageConfigured by storageManager.isConfigured.collectAsState()

    // Tahap 27.2: a deep-link URL (from `app.rocat.EXTRA_URL`) skips the first-launch
    // storage gate — the in-app browser works without a storage directory, so an
    // automated test / external launcher can jump straight to the browser tab.
    if (!storageConfigured && initialUrl == null) {
        I18nApp(strings = strings, language = language) {
            StorageSetupScreen(
                onFolderPicked = { uri -> storageManager.takePersistablePermission(uri) },
                onConfigured = {},
            )
        }
        return
    }

    I18nApp(strings = strings, language = language) {
        RoCatAppNav(initialUrl)
    }
}

@Composable
private fun RoCatAppNav(initialUrl: String? = null) {
    val backStack = rememberSaveable(
        saver = listSaver(
            save = { it.toList() },
            restore = { it.toMutableStateList() },
        ),
    ) {
        // Tahap 27.2: when the activity was launched with a target URL, land directly
        // on the browser tab instead of the scripts list.
        if (initialUrl != null) mutableStateListOf(encode(Screen.Browser(initialUrl))) else mutableStateListOf(KEY_HOME)
    }
    val current = decode(backStack.last())

    fun goBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
    }

    fun navigate(screen: Screen) {
        val key = encode(screen)
        // Tab-like behaviour: jumping to an existing destination pops the stack to it.
        val index = backStack.indexOf(key)
        if (index >= 0) {
            while (backStack.size > index + 1) backStack.removeAt(backStack.size - 1)
        } else {
            backStack.add(key)
        }
    }

    BackHandler(enabled = backStack.size > 1) { goBack() }

    Scaffold(
        bottomBar = {
            if (current is Screen.Home || current is Screen.Scripts || current is Screen.Browser || current is Screen.Downloads || current is Screen.Settings) {
                NavigationBar(tonalElevation = androidx.compose.ui.unit.Dp.Hairline) {
                    NavigationBarItem(
                        selected = current is Screen.Home,
                        onClick = { navigate(Screen.Home) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = current is Screen.Browser,
                        onClick = { navigate(Screen.Browser()) },
                        icon = { Icon(Icons.Filled.Public, contentDescription = null) },
                        label = { Text(stringResource(StringKey.browser)) },
                    )
                    NavigationBarItem(
                        selected = current is Screen.Scripts,
                        onClick = { navigate(Screen.Scripts) },
                        icon = { Icon(Icons.Filled.Extension, contentDescription = null) },
                        label = { Text(stringResource(StringKey.scripts)) },
                    )
                    NavigationBarItem(
                        selected = current is Screen.Downloads,
                        onClick = { navigate(Screen.Downloads) },
                        icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        label = { Text("Downloads") },
                    )
                    NavigationBarItem(
                        selected = current is Screen.Settings,
                        onClick = { navigate(Screen.Settings) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(StringKey.settings)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "appNavigation",
            ) { destination -> when (destination) {
                is Screen.Home -> HomeScreen(
                    onBrowse = { navigate(Screen.Browser(it)) },
                    onScripts = { navigate(Screen.Scripts) },
                    onDownloads = { navigate(Screen.Downloads) },
                )
                is Screen.Scripts -> ScriptsScreen(
                    onOpenScript = { navigate(Screen.Canvas(it)) },
                    onEditScript = { navigate(Screen.Detail(it)) },
                    onImport = { navigate(Screen.Import()) },
                )
                is Screen.Detail -> ScriptDetailScreen(scriptId = destination.scriptId, onBack = ::goBack)
                is Screen.Import -> ImportScriptScreen(onBack = ::goBack, initialUrl = destination.sourceUrl)
                is Screen.Browser -> BrowserScreen(
                    initialUrl = destination.url,
                    onOpenScripts = { navigate(Screen.Scripts) },
                    onInstallScript = { navigate(Screen.Import(it)) },
                    onOpenDownloads = { navigate(Screen.Downloads) },
                )
                is Screen.Downloads -> DownloadsScreen()
                is Screen.Settings -> SettingsScreen()
                is Screen.Canvas -> ScriptCanvasScreen(
                    scriptId = destination.scriptId,
                    onBack = ::goBack,
                    onOpenSettings = { navigate(Screen.ScriptSettings(destination.scriptId)) },
                )
                is Screen.ScriptSettings -> ScriptSettingsScreen(scriptId = destination.scriptId, onBack = ::goBack)
            } }
        }
    }
}
