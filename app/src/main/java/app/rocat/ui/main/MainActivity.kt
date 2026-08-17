package app.rocat.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}
