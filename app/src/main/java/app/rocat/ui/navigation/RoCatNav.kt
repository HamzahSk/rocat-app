package app.rocat.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import app.rocat.core.common.injekt.Injekt
import app.rocat.i18n.I18nApp
import app.rocat.i18n.I18nProvider
import app.rocat.i18n.StringKey
import app.rocat.i18n.stringResource
import app.rocat.storage.StorageManager
import app.rocat.ui.browser.BrowserScreen
import app.rocat.ui.canvas.ScriptCanvasScreen
import app.rocat.ui.detail.ScriptDetailScreen
import app.rocat.ui.import.ImportScriptScreen
import app.rocat.ui.scripts.ScriptsScreen
import app.rocat.ui.settings.SettingsScreen
import app.rocat.ui.settings.StorageSetupScreen

/** Lightweight, dependency-free navigation mirroring mihon's extension screens. */
sealed interface Screen {
    data object Scripts : Screen
    data class Detail(val scriptId: String) : Screen
    data object Import : Screen
    data object Browser : Screen
    /** The script-driven blank canvas (the script draws its own UI via `RoCatUI`). */
    data class Canvas(val scriptId: String) : Screen
    data object Settings : Screen
}

private const val KEY_SCRIPTS = "scripts"
private const val KEY_IMPORT = "import"
private const val KEY_BROWSER = "browser"
private const val KEY_SETTINGS = "settings"
private const val KEY_DETAIL_PREFIX = "detail:"
private const val KEY_CANVAS_PREFIX = "canvas:"

private fun encode(screen: Screen): String = when (screen) {
    is Screen.Scripts -> KEY_SCRIPTS
    is Screen.Import -> KEY_IMPORT
    is Screen.Browser -> KEY_BROWSER
    is Screen.Settings -> KEY_SETTINGS
    is Screen.Detail -> KEY_DETAIL_PREFIX + screen.scriptId
    is Screen.Canvas -> KEY_CANVAS_PREFIX + screen.scriptId
}

private fun decode(key: String): Screen = when {
    key == KEY_SCRIPTS -> Screen.Scripts
    key == KEY_IMPORT -> Screen.Import
    key == KEY_BROWSER -> Screen.Browser
    key == KEY_SETTINGS -> Screen.Settings
    key.startsWith(KEY_DETAIL_PREFIX) -> Screen.Detail(key.removePrefix(KEY_DETAIL_PREFIX))
    key.startsWith(KEY_CANVAS_PREFIX) -> Screen.Canvas(key.removePrefix(KEY_CANVAS_PREFIX))
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
        if (initialUrl != null) mutableStateListOf(KEY_BROWSER) else mutableStateListOf(KEY_SCRIPTS)
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
            if (current is Screen.Scripts || current is Screen.Browser || current is Screen.Settings) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    NavigationBarItem(
                        selected = current is Screen.Scripts,
                        onClick = { navigate(Screen.Scripts) },
                        icon = { Icon(Icons.Filled.Extension, contentDescription = null) },
                        label = { Text(stringResource(StringKey.scripts)) },
                        colors = roCatNavigationColors(),
                    )
                    NavigationBarItem(
                        selected = current is Screen.Browser,
                        onClick = { navigate(Screen.Browser) },
                        icon = { Icon(Icons.Filled.Public, contentDescription = null) },
                        label = { Text(stringResource(StringKey.browser)) },
                        colors = roCatNavigationColors(),
                    )
                    NavigationBarItem(
                        selected = current is Screen.Settings,
                        onClick = { navigate(Screen.Settings) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(StringKey.settings)) },
                        colors = roCatNavigationColors(),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (current) {
                is Screen.Scripts -> ScriptsScreen(
                    onOpenScript = { navigate(Screen.Canvas(it)) },
                    onEditScript = { navigate(Screen.Detail(it)) },
                    onImport = { navigate(Screen.Import) },
                )
                is Screen.Detail -> ScriptDetailScreen(scriptId = current.scriptId, onBack = ::goBack)
                is Screen.Import -> ImportScriptScreen(onBack = ::goBack)
                is Screen.Browser -> BrowserScreen(initialUrl = initialUrl)
                is Screen.Settings -> SettingsScreen()
                is Screen.Canvas -> ScriptCanvasScreen(scriptId = current.scriptId, onBack = ::goBack)
            }
        }
    }
}

@Composable
private fun roCatNavigationColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
