package app.rocat.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import app.rocat.core.common.injekt.Injekt
import app.rocat.settings.SettingsRepository
import app.rocat.settings.ThemeMode
import androidx.compose.ui.Modifier
import app.rocat.ui.navigation.RoCatApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Tahap 27.2: read the optional deep-link URL so `adb shell am start ... --es
        // app.rocat.EXTRA_URL "https://..."` opens the in-app browser straight on that
        // page (used by the emulator CI job to verify JS/SPA rendering without any
        // UI automation taps).
        val initialUrl = intent?.getStringExtra(EXTRA_URL)?.takeIf(String::isNotBlank)

        setContent {
            RoCatTheme {
                RoCatApp(initialUrl = initialUrl)
            }
        }
    }

    companion object {
        /** Intent extra carrying a URL to open directly in the in-app browser. */
        const val EXTRA_URL = "app.rocat.EXTRA_URL"
    }
}

@Composable
fun RoCatTheme(content: @Composable () -> Unit) {
    val settings = Injekt.get<SettingsRepository>()
    val themeMode by settings.themeMode.collectAsState()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = Color(0xFFB8C4FF),
            secondary = Color(0xFFFFB2C8),
            tertiary = Color(0xFF8ED8C4),
        )
        else -> lightColorScheme(
            primary = Color(0xFF4054A6),
            secondary = Color(0xFF9A405F),
            tertiary = Color(0xFF006B5B),
        )
    }
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
