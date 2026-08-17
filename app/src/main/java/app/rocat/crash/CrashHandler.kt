package app.rocat.crash

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import app.rocat.ui.crash.CrashActivity

/**
 * Default uncaught exception handler installed from [app.rocat.RoApp].
 *
 * Instead of force-closing silently, it:
 *  1. writes the stack trace to `Android/data/<package>/files/crash_logs/`,
 *  2. launches [CrashActivity] (its own screen, never part of RoCatNav) so the
 *     trace can be reviewed and copied,
 *  3. then kills the main process as a last resort.
 */
class CrashHandler(private val app: Application) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val logFile = CrashLogStore.saveCrash(app, throwable)
            Log.e(TAG, "Uncaught exception captured, log: ${logFile.absolutePath}", throwable)

            val intent = Intent(app, CrashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(CrashActivity.EXTRA_STACK_TRACE, throwable.stackTraceToString())
                putExtra(CrashActivity.EXTRA_LOG_PATH, logFile.absolutePath)
            }
            app.startActivity(intent)
        } catch (failure: Throwable) {
            // Never recurse: if we cannot handle it, let the platform default run.
            Log.e(TAG, "Crash handler failed, falling back to default handler", failure)
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            Process.killProcess(Process.myPid())
        }
    }

    companion object {
        private const val TAG = "RoCatCrashHandler"
    }
}
