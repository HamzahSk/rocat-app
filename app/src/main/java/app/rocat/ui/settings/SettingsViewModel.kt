package app.rocat.ui.settings

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.viewModelScope
import app.rocat.core.common.injekt.Injekt
import app.rocat.core.common.network.DnsMode
import app.rocat.core.viewmodel.StateViewModel
import app.rocat.data.db.CookieDao
import app.rocat.data.db.HistoryDao
import app.rocat.i18n.AppLanguage
import app.rocat.i18n.I18nProvider
import app.rocat.i18n.StringKey
import app.rocat.settings.SettingsRepository
import app.rocat.storage.StorageManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Settings screen. Coordinates the custom i18n provider, the SAF storage
 * manager, the Room DAOs for the three destructive actions (cache/cookies/history) and
 * the network preferences (custom User-Agent + DoH DNS, Tahap 20). Every action updates
 * [State.message] so the UI can surface a transient confirmation.
 */
class SettingsViewModel(
    private val settings: SettingsRepository = Injekt.get(),
    private val storageManager: StorageManager = Injekt.get(),
    private val i18nProvider: I18nProvider = Injekt.get(),
    private val cookieDao: CookieDao = Injekt.get(),
    private val historyDao: HistoryDao = Injekt.get(),
    private val appContext: Context = Injekt.get(),
) : StateViewModel<SettingsViewModel.State>(State()) {

    data class State(
        val language: AppLanguage = AppLanguage.ENGLISH,
        val storageConfigured: Boolean = false,
        val storageName: String = "",
        val busy: Boolean = false,
        val message: StringKey? = null,
        val userAgent: String = "",
        val dnsMode: DnsMode = DnsMode.SYSTEM,
        val customDnsUrl: String = "",
    )

    val settingsState: StateFlow<State> = combine(
        i18nProvider.language,
        mutableState,
    ) { language, current ->
        current.copy(
            language = language,
            storageConfigured = storageManager.isConfigured.value,
            storageName = storageManager.mainDirectoryName(),
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        // Seed the network fields from the persisted preferences once.
        mutableState.value = mutableState.value.copy(
            userAgent = settings.userAgent,
            dnsMode = settings.dnsMode,
            customDnsUrl = settings.customDnsUrl,
        )
    }

    fun setLanguage(language: AppLanguage) {
        i18nProvider.setLanguage(language)
    }

    // ---- Tahap 20: Network settings ----
    // Persisting happens on every keystroke (cheap SharedPreferences write); the
    // NetworkHelper only *rebuilds* its client lazily on the next request, keyed by a
    // fingerprint, so editing the field here never rebuilds anything per keystroke.

    fun setUserAgent(value: String) {
        settings.userAgent = value
        mutableState.value = mutableState.value.copy(userAgent = value)
    }

    fun setDnsMode(mode: DnsMode) {
        settings.dnsMode = mode
        mutableState.value = mutableState.value.copy(dnsMode = mode)
    }

    fun setCustomDnsUrl(value: String) {
        settings.customDnsUrl = value
        mutableState.value = mutableState.value.copy(customDnsUrl = value)
    }

    /** Callback of the `OpenDocumentTree` launcher in the Settings screen. */
    fun onStoragePicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val persisted = storageManager.takePersistablePermission(uri)
            mutableState.value = mutableState.value.copy(
                storageConfigured = storageManager.isConfigured.value,
                storageName = storageManager.mainDirectoryName(),
                message = if (persisted) StringKey.storageChanged else StringKey.storagePermissionDenied,
            )
        }
    }

    fun clearCache() = mutateOnResult(StringKey.cacheCleared, StringKey.failure) {
        storageManager.clearCache()
        // Tahap 16.2: wipe the real WebView cache too (the Coil cache + cacheDir cleanup
        // alone left stale page/asset data behind). Must run on the main thread.
        runCatching { WebView(appContext).clearCache(true) }
    }

    fun deleteCookies() = mutateOnResult(StringKey.cookiesCleared, StringKey.failure) {
        cookieDao.deleteAll()
        // Tahap 16.2: purge the WebView CookieManager (which AndroidCookieJar shares with
        // OkHttp + scripts), not just the Room table, so sessions are really logged out.
        runCatching {
            val manager = CookieManager.getInstance()
            manager.removeAllCookies(null)
            manager.flush()
        }
    }

    fun deleteHistory() = mutateOnResult(StringKey.historyCleared, StringKey.failure) {
        historyDao.deleteAll()
    }

    fun consumeMessage() {
        mutableState.value = mutableState.value.copy(message = null)
    }

    private fun mutateOnResult(success: StringKey, failure: StringKey, block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true)
            val message = try {
                block()
                success
            } catch (e: Exception) {
                failure
            }
            mutableState.value = mutableState.value.copy(busy = false, message = message)
        }
    }
}
