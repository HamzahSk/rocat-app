package app.rocat.settings

import android.content.Context
import android.net.Uri
import app.rocat.core.common.network.DnsMode
import app.rocat.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thin persistence layer for app settings, backed by a private [android.content.SharedPreferences].
 * Stores the selected language, the main storage directory (as a SAF tree URI) and the
 * network settings (custom User-Agent + DoH DNS mode, Tahap 20). A dedicated repository
 * keeps each value in a single place so the i18n provider, the storage manager and the
 * network stack observe the same source of truth.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var language: AppLanguage
        get() = runCatching { AppLanguage.valueOf(prefs.getString(KEY_LANGUAGE, null) ?: "") }
            .getOrDefault(AppLanguage.ENGLISH)
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.name).apply()

    private val _storageUri = MutableStateFlow(
        prefs.getString(KEY_STORAGE_URI, null)?.let(Uri::parse),
    )

    /** The SAF tree URI of the main storage folder, or null until the user picks one. */
    val storageUri: StateFlow<Uri?> = _storageUri.asStateFlow()

    /**
     * Persists (or clears) the main storage directory. Updating the [StateFlow] lets the
     * first-launch gate (RoCatApp) and any storage observer recompose immediately without
     * a process restart.
     */
    fun setStorageUri(value: Uri?) {
        _storageUri.value = value
        val editor = prefs.edit()
        if (value == null) editor.remove(KEY_STORAGE_URI) else editor.putString(KEY_STORAGE_URI, value.toString())
        editor.apply()
    }

    val hasStorageDirectory: Boolean
        get() = _storageUri.value != null

    // ---- Tahap 20: Network settings (custom User-Agent + DoH DNS) ----

    /**
     * The user-defined User-Agent string, or "" when the default browser-grade agent
     * ([app.rocat.core.common.network.NetworkHelper.DEFAULT_USER_AGENT]) should be used.
     */
    var userAgent: String
        get() = prefs.getString(KEY_USER_AGENT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USER_AGENT, value).apply()

    /** Which DNS-over-HTTPS provider should be used ([DnsMode.SYSTEM] = platform default). */
    var dnsMode: DnsMode
        get() = runCatching { DnsMode.valueOf(prefs.getString(KEY_DNS_MODE, null) ?: "") }
            .getOrDefault(DnsMode.SYSTEM)
        set(value) = prefs.edit().putString(KEY_DNS_MODE, value.name).apply()

    /** The user-supplied DoH endpoint URL, relevant when [dnsMode] is [DnsMode.CUSTOM]. */
    var customDnsUrl: String
        get() = prefs.getString(KEY_CUSTOM_DNS_URL, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_CUSTOM_DNS_URL, value).apply()

    // ---- Tahap 25: In-app browser preferences ----

    /** Whether the in-app browser renders with a desktop User-Agent (Desktop mode). */
    var desktopMode: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_MODE, value).apply()

    companion object {
        private const val PREFS_NAME = "rocat_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_STORAGE_URI = "storage_uri"
        private const val KEY_USER_AGENT = "user_agent"
        private const val KEY_DNS_MODE = "dns_mode"
        private const val KEY_CUSTOM_DNS_URL = "custom_dns_url"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
    }
}
