package app.rocat.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val colors = when {
        dark -> darkColorScheme(
            primary = Color(0xFFAFC6FF),
            onPrimary = Color(0xFF002E69),
            primaryContainer = Color(0xFF164789),
            secondary = Color(0xFFC8BFFF),
            tertiary = Color(0xFF82D7C5),
            background = Color(0xFF0E1116),
            surface = Color(0xFF0E1116),
            surfaceContainer = Color(0xFF191D24),
            surfaceContainerHigh = Color(0xFF222730),
        )
        else -> lightColorScheme(
            primary = Color(0xFF2458A6),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD8E4FF),
            secondary = Color(0xFF65558F),
            tertiary = Color(0xFF006B5F),
            background = Color(0xFFF7F9FC),
            surface = Color(0xFFF7F9FC),
            surfaceContainer = Color(0xFFEEF1F6),
            surfaceContainerHigh = Color(0xFFE5E9F0),
        )
    }
    val typography = Typography(
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(30.dp),
    )
    MaterialTheme(colorScheme = colors, typography = typography, shapes = shapes) {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
