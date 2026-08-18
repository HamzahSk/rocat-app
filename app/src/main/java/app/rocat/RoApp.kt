package app.rocat

import android.app.Application
import android.webkit.WebView
import app.rocat.core.common.injekt.Injekt
import app.rocat.crash.CrashHandler
import app.rocat.di.AppModule
import app.rocat.settings.SettingsRepository
import logcat.LogcatLogger

class RoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        LogcatLogger.install()

        // Mirror mihon's App.onCreate() DI bootstrap.
        Injekt.importModule(AppModule(this))

        // Tahap 27.4: enable chrome://inspect + richer WebView logs on debug builds so
        // the emulator CI job / developer can diagnose blank-render issues remotely.
        val settings = Injekt.get<SettingsRepository>()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || settings.webViewDebugging)

        // Global crash handler: persist the stack trace to Android/data/<pkg>/files/
        // and surface a CrashActivity instead of force-closing silently.
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }
}
