package app.rocat.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.rocat.core.common.injekt.InjektModule
import app.rocat.core.common.injekt.Registrar
import app.rocat.core.common.network.NetworkHelper
import app.rocat.data.db.AppDatabase
import app.rocat.data.script.ScriptManager
import app.rocat.data.script.ScriptRepositoryImpl
import app.rocat.data.script.ScriptSourceFetcher
import app.rocat.domain.script.DeleteScript
import app.rocat.domain.script.ExecuteScript
import app.rocat.domain.script.GetScripts
import app.rocat.domain.script.ImportScript
import app.rocat.domain.script.ScriptRepository
import app.rocat.domain.script.SetScriptEnabled
import app.rocat.domain.script.UpsertScript
import app.rocat.i18n.I18nProvider
import app.rocat.media.MediaDownloader
import app.rocat.settings.SettingsRepository
import app.rocat.scripting.HeadlessWebViewManager
import app.rocat.scripting.RoCatBrowserBridge
import app.rocat.scripting.ScriptSettingsManager
import app.rocat.scripting.api.ScriptBrowserBridge
import app.rocat.storage.StorageManager
import app.rocat.ui.browser.BrowserViewModel
import app.rocat.ui.import.ImportScriptViewModel
import app.rocat.ui.settings.SettingsViewModel
import app.rocat.ui.scripts.ScriptsViewModel

/**
 * Application-level dependency graph, mirroring mihon's `AppModule`/`PreferenceModule`.
 * Registered from [RoApp.onCreate].
 */
class AppModule(val app: Application) : InjektModule {

override fun registerInjectables(registrar: Registrar) {
        registrar.add(app)
        registrar.addSingleton(app as Context)

        // Tahap 15: Settings / storage / i18n. Created before NetworkHelper so the HTTP
        // stack can seed its User-Agent + DoH DNS from the persisted preferences.
        val settingsRepository = SettingsRepository(app)
        registrar.addSingleton(settingsRepository)

        // Tahap 20: the network stack reads UA + DNS settings lazily (fingerprint-based
        // rebuild) so changes are picked up on the next request without a restart.
        val networkHelper = NetworkHelper(
            app,
            userAgentProvider = {
                settingsRepository.userAgent.ifBlank { NetworkHelper.DEFAULT_USER_AGENT }
            },
            dnsConfigProvider = { settingsRepository.dnsMode to settingsRepository.customDnsUrl },
        )
        registrar.addSingleton(networkHelper)

        // Wires the Rhino engine + network-backed environment into a single manager.
        val scriptManager = ScriptManager(networkHelper)
        registrar.addSingleton(scriptManager)

        // Tahap 23/25: headless WebView backing the script globals `RoCatPage` (low-level)
        // and `RoCatBrowser` (general-purpose Playwright-like polyfill, Tahap 25).
        // Registered against the ScriptBrowserBridge interface so ScriptCanvasViewModel
        // can resolve it via Injekt and hand it to the engine.
        val headlessWebViewManager = HeadlessWebViewManager(app)
        registrar.addSingleton(headlessWebViewManager)
        registrar.addSingleton<ScriptBrowserBridge>(RoCatBrowserBridge(headlessWebViewManager))

        val scriptsDir = java.io.File(app.filesDir, "scripts")
        val scriptRepository: ScriptRepository = ScriptRepositoryImpl(scriptsDir)
        registrar.addSingleton(scriptRepository)
        registrar.addSingletonFactory { GetScripts(scriptRepository) }
        registrar.addSingletonFactory { UpsertScript(scriptRepository) }
        registrar.addSingletonFactory { ImportScript(scriptRepository) }
        registrar.addSingletonFactory { DeleteScript(scriptRepository) }
        registrar.addSingletonFactory { SetScriptEnabled(scriptRepository) }
        registrar.addSingletonFactory { ScriptSourceFetcher(networkHelper.client()) }
        registrar.addSingletonFactory {
            ExecuteScript(
                engine = scriptManager.engine(),
                environment = scriptManager.environment(),
            )
        }

        val storageManager = StorageManager(app, settingsRepository)
        registrar.addSingleton(storageManager)

        registrar.addSingleton(I18nProvider(settingsRepository))

        // Tahap 18.1: media downloader for ImagePreviewCard / VideoPreviewCard saves.
        registrar.addSingleton(MediaDownloader(networkHelper, storageManager))

        // Tahap 15.3: Room database singleton + DAOs.
        val database = Room.databaseBuilder(app, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        registrar.addSingleton(database)
        registrar.addSingleton(database.cookieDao())
        registrar.addSingleton(database.historyDao())
        registrar.addSingleton(database.scriptSettingsDao())
        registrar.addSingleton(database.scriptInputHistoryDao())

        // Tahap 35: per-script settings manager (persistence, defaults, history, export).
        registrar.addSingleton(ScriptSettingsManager(database))

        // ViewModels. Registered as Injekt factories so the Compose screens can build
        // them without the reflection-based default factory (which only supports
        // no-arg constructors and crashes for these constructor-injected ViewModels).
        registrar.addSingletonFactory { ScriptsViewModel() }
        registrar.addSingletonFactory { ImportScriptViewModel() }
        registrar.addSingletonFactory { SettingsViewModel() }
        registrar.addSingletonFactory { BrowserViewModel() }
        registrar.addSingleton(AppViewModelFactory)
    }

    companion object {
        private const val DATABASE_NAME = "rocat.db"
    }
}