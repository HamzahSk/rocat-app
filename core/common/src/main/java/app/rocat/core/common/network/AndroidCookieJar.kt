package app.rocat.core.common.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * An OkHttp [CookieJar] backed by the WebView [CookieManager], mirroring mihon's
 * `AndroidCookieJar`.
 *
 * This is the single source of truth for cookies across OkHttp AND WebView: requests
 * made by the app (or by scripts through the OkHttp stack) and requests made by a
 * headless [android.webkit.WebView] share the exact same cookie store, so a
 * `cf_clearance` solved inside a WebView is immediately available to later OkHttp
 * calls, and vice versa.
 *
 * Thread safety: [CookieManager] lets callers run on any thread, but the underlying
 * storage is populated asynchronously. All reads/writes are serialized under a lock
 * and every write is flushed synchronously so a following request always sees the
 * cookie that was just set (e.g. the [CloudflareInterceptor] retries right after a
 * WebView has finished solving a challenge).
 */
class AndroidCookieJar : CookieJar {

    private val manager = CookieManager.getInstance()

    // Marshals access to the (async) cookie store so nothing is lost between threads.
    private val lock = Any()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        synchronized(lock) {
            cookies.forEach { manager.setCookie(urlString, it.toString()) }
            // Force the pending writes to disk/memory before returning.
            runCatching { manager.flush() }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = get(url)

    fun get(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val cookies = manager.getCookie(url.toString())
        if (!cookies.isNullOrEmpty()) {
            cookies.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
        } else {
            emptyList()
        }
    }

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1): Int = synchronized(lock) {
        val urlString = url.toString()
        val cookies = manager.getCookie(urlString) ?: return@synchronized 0

        fun List<String>.filterNames(): List<String> {
            return if (cookieNames != null) filter { it in cookieNames } else this
        }

        val removed = cookies.split(";")
            .mapNotNull { it.substringBefore("=").trim().takeIf(String::isNotEmpty) }
            .filterNames()
            .onEach { manager.setCookie(urlString, "$it=;Max-Age=$maxAge") }
        runCatching { manager.flush() }
        removed.count()
    }

    fun removeAll() {
        manager.removeAllCookies { runCatching { manager.flush() } }
    }
}