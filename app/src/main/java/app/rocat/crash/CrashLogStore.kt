package app.rocat.crash

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists crash reports as plain-text files under
 * `Android/data/<package>/files/crash_logs/` (i.e. `context.getExternalFilesDir(null)`),
 * which is directly accessible without any runtime permission.
 */
object CrashLogStore {

    const val DIR_NAME = "crash_logs"
    private const val FILE_PREFIX = "crash_log_"
    private const val FILE_EXT = ".txt"

    fun crashLogDir(context: Context): File =
        File(context.getExternalFilesDir(null), DIR_NAME).apply { mkdirs() }

    fun saveCrash(context: Context, throwable: Throwable): File {
        val file = File(crashLogDir(context), "$FILE_PREFIX${timestamp()}$FILE_EXT")
        file.writeText(buildReport(throwable))
        return file
    }

    fun buildReport(throwable: Throwable): String = buildString {
        appendLine("===== RoCat Crash Report =====")
        appendLine("Timestamp: ${Date()}")
        appendLine("Thread:    ${Thread.currentThread().name}")
        appendLine("Device:    ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
        appendLine()
        appendThrowable(this, throwable, 0)
    }

    private fun appendThrowable(sb: StringBuilder, throwable: Throwable, depth: Int) {
        if (depth > 5) return
        val indent = "  ".repeat(depth)
        sb.append(indent).append(throwable.javaClass.name)
            .append(if (throwable.message.isNullOrEmpty()) "" else ": ${throwable.message}")
            .append('\n')
        throwable.stackTrace.forEach { element ->
            sb.append(indent).append("    at ").append(element).append('\n')
        }
        throwable.cause?.let { cause ->
            sb.append(indent).append("Caused by:").append('\n')
            appendThrowable(sb, cause, depth + 1)
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
