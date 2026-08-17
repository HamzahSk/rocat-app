package app.rocat

import android.app.Application
import app.rocat.core.common.injekt.Injekt
import app.rocat.crash.CrashHandler
import app.rocat.di.AppModule
import logcat.LogcatLogger

class RoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        LogcatLogger.install()

        // Mirror mihon's App.onCreate() DI bootstrap.
        Injekt.importModule(AppModule(this))

        // Global crash handler: persist the stack trace to Android/data/<pkg>/files/
        // and surface a CrashActivity instead of force-closing silently.
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
    }
}