package app.rocat

import android.app.Application
import android.content.Context
import android.webkit.WebView
import app.rocat.core.common.injekt.Injekt
import app.rocat.core.common.network.NetworkHelper
import app.rocat.crash.CrashHandler
import app.rocat.di.AppModule
import app.rocat.settings.SettingsRepository
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import logcat.LogcatLogger
import okio.Path.Companion.toPath

class RoApp : Application(), SingletonImageLoader.Factory {
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

    /** Use the same browser-grade OkHttp stack as downloads and script fetches. */
    override fun newImageLoader(context: Context): ImageLoader {
        val client = lazy { Injekt.get<NetworkHelper>().client() }
        return ImageLoader.Builder(this)
            // Keep large reader pages bounded on low-memory devices. Software bitmaps are
            // requested per image below so oversized webtoon pages never become GL textures.
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this@RoApp, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache").absolutePath.toPath())
                    .maxSizeBytes(100L * 1024L * 1024L)
                    .build()
            }
            .components {
                add(OkHttpNetworkFetcherFactory(client::value))
            }
            .build()
    }
}
