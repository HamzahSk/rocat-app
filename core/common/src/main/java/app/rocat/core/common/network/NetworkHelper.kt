package app.rocat.core.common.network

import android.content.Context
import app.rocat.core.common.network.interceptor.CloudflareInterceptor
import app.rocat.core.common.network.interceptor.StealthHeadersInterceptor
import okhttp3.Cache
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Central HTTP client holder, mirroring mihon's `NetworkHelper`.
 *
 * Owns a single lazily-built [OkHttpClient] (plus a per-request disposable client used
 * by the scripting engine so that scripts cannot leak connections into the main pool).
 *
 * The client is hardened against the common `CertPathValidatorException: Trust anchor
 * for certification path not found` failure mode seen when a site presents a chain
 * rooted in a user-installed / corporate CA:
 *  - [network_security_config.xml] already trusts system + user anchors, and
 *  - here we explicitly configure a browser-grade TLS [ConnectionSpec] set and
 *    disable hostname/SSL redirect blocking so 301 → https jumps keep working.
 *
 * Stealth networking (Tahap 10): every client shares [AndroidCookieJar] so OkHttp and
 * the WebView see identical, persistent cookies, requests carry browser-grade defaults
 * via [UserAgentInterceptor] + [StealthHeadersInterceptor], and [CloudflareInterceptor]
 * transparently solves Cloudflare's JS challenge with a headless WebView.
 *
 * Network settings (Tahap 20): the effective User-Agent and DNS-over-HTTPS mode come
 * from [userAgentProvider] / [dnsConfigProvider] (backed by SharedPreferences). Clients
 * are rebuilt transparently whenever that fingerprint changes, so changing the UA or
 * choosing a custom DoH provider takes effect on the next request without a restart —
 * rebuilding happens lazily on [client] / [newScriptClient], not per keystroke.
 */
class NetworkHelper(
    private val context: Context,
    private val userAgentProvider: () -> String = { DEFAULT_USER_AGENT },
    private val dnsConfigProvider: () -> Pair<DnsMode, String> = { DnsMode.SYSTEM to "" },
) {

    /** Single cookie store shared by OkHttp and the WebView (see [AndroidCookieJar]). */
    val cookieJar = AndroidCookieJar()

    private val configLock = Any()
    private var cachedClient: OkHttpClient? = null
    private var cachedFingerprint: String? = null

    /** The effective User-Agent, derived from [userAgentProvider] with a safe fallback. */
    private fun effectiveUserAgent(): String = userAgentProvider().ifBlank { DEFAULT_USER_AGENT }

    /**
     * Rebuilds the shared client only when the UA/DNS configuration actually changed
     * (fingerprint compare), returning the cached instance otherwise.
     */
    fun client(): OkHttpClient = synchronized(configLock) {
        val userAgent = effectiveUserAgent()
        val (mode, customUrl) = dnsConfigProvider()
        val fingerprint = "$userAgent|$mode|$customUrl"
        val cached = cachedClient
        if (cached != null && cachedFingerprint == fingerprint) {
            cached
        } else {
            cachedFingerprint = fingerprint
            buildClient(userAgent, mode, customUrl).also { cachedClient = it }
        }
    }

    /**
     * A short-lived client with aggressive timeouts, used by script executions so a
     * misbehaving script cannot hang the app or poison the shared connection pool. It
     * still carries the cookie jar, custom DNS and Cloudflare interceptor of the main
     * client, and is rebuilt automatically when the network settings change.
     */
    fun newScriptClient(): OkHttpClient = client().newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Stable description of the active network configuration (used for change detection). */
    fun fingerprint(): String {
        val userAgent = effectiveUserAgent()
        val (mode, customUrl) = dnsConfigProvider()
        return "$userAgent|$mode|$customUrl"
    }

    /**
     * Builds a hardened client for the given UA / DNS settings. Wilding TLS 1.2/1.3
     * via the browser-grade [ConnectionSpec] set, and routes DNS through [DoHResolver]
     * when a non-system [DnsMode] is selected.
     */
    private fun buildClient(userAgent: String, dnsMode: DnsMode, customDnsUrl: String): OkHttpClient {
        val dns: Dns = DnsProviders.dohUrl(dnsMode, customDnsUrl)?.let { DoHResolver(it) } ?: Dns.SYSTEM
        val base = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cache(Cache(File(context.cacheDir, "network_cache"), 5L * 1024 * 1024))
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionSpecs(
                listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS,
                    ConnectionSpec.CLEARTEXT,
                ),
            )
            .dns(dns)
            .addInterceptor(UserAgentInterceptor(userAgent))
            .addInterceptor(StealthHeadersInterceptor())
            .build()
        return base.newBuilder()
            .addInterceptor(CloudflareInterceptor(context, cookieJar) { userAgent })
            .build()
    }

    companion object {
        /**
         * Browser-grade default User-Agent, matching mihon's `default_user_agent`
         * preference so servers/WAFs treat requests as coming from a real browser.
         */
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * Adds a standard [User-Agent] to every request that does not already set one, using
 * the current user-configured agent, mirroring mihon's `UserAgentInterceptor`
 * (explicit per-request UAs win).
 */
class UserAgentInterceptor(
    private val userAgent: String,
) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        return if (request.header("User-Agent").isNullOrEmpty()) {
            chain.proceed(
                request.newBuilder()
                    .removeHeader("User-Agent")
                    .addHeader("User-Agent", userAgent)
                    .build(),
            )
        } else {
            chain.proceed(request)
        }
    }
}