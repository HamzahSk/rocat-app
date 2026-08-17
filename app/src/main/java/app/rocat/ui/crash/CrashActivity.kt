package app.rocat.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rocat.ui.main.RoCatTheme
import kotlin.system.exitProcess

/**
 * Standalone crash-report screen. Deliberately kept outside [app.rocat.ui.navigation.RoCatApp]
 * and [app.rocat.ui.main.MainActivity] so it can still be shown even when the main UI is the
 * source of the crash. Receives the stack trace and log path via the launching Intent.
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace available."
        val logPath = intent.getStringExtra(EXTRA_LOG_PATH) ?: "Unknown"

        setContent {
            RoCatTheme {
                CrashLogScreen(
                    stackTrace = stackTrace,
                    logPath = logPath,
                    onExit = ::exitApp,
                )
            }
        }
    }

    private fun exitApp() {
        finishAffinity()
        exitProcess(0)
    }

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        const val EXTRA_LOG_PATH = "extra_log_path"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashLogScreen(
    stackTrace: String,
    logPath: String,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("RoCat Crash") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "RoCat hit an unexpected error.",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Log saved to:",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = logPath,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))

            Text("Stack trace", style = MaterialTheme.typography.titleSmall)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = stackTrace,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("RoCat crash log", stackTrace),
                        )
                        Toast.makeText(context, "Stack trace copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("Copy to Clipboard")
                }
                OutlinedButton(onClick = onExit) {
                    Text("Exit")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
