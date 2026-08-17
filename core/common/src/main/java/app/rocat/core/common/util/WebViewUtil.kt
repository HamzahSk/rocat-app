package app.rocat.core.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import app.rocat.core.common.network.NetworkHelper

/**
 * WebView helpers mirroring mihon's `WebViewUtil`: browser-grade settings so pages
 * that rely on modern JS / storage behave, plus a User-Agent inferred the same way
 * mihon does (a Chrome-like UA on Android 10) which is kept consistent with the
 * User-Agent [NetworkHelper] sends on OkHttp requests.
 */
object WebViewUtil {

    /**
     * A standard desktop Chrome User-Agent (Windows x64), used by the in-app browser's
     * "Desktop mode" toggle (Tahap 25) so server-rendered / responsive sites that only
     * expose their full interface to non-mobile clients render completely.
     */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/141.0.0.0 Safari/537.36"

    /**
     * Returns a Chrome-like User-Agent string derived from the installed WebView,
     * normalised the same way mihon does (`; Android 10; K` + dropped `Version/x.`).
     * Falls back to the shared [NetworkHelper.DEFAULT_USER_AGENT] on any failure so
     * OkHttp and the WebView always agree.
     */
    fun getInferredUserAgent(context: Context): String = runCatching {
        getDefaultUserAgentString(WebView(context))
            .replace("; Android .*?\\)".toRegex(), "; Android 10; K)")
            .replace("Version/.* Chrome/".toRegex(), "Chrome/")
    }.getOrElse { NetworkHelper.DEFAULT_USER_AGENT }

    /**
     * Applies the modern WebView configuration used by mihon: JavaScript, DOM storage
     * and the app database are enabled, mixed content is allowed in compatibility mode
     * (so SPA / heavy-JS pages never render blank), wide-viewport rendering is on, popups
     * and zoom are supported and third-party cookies are accepted. The User-Agent is
     * pinned to [NetworkHelper]'s so JS fetch / WebView rendering see the same identity.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun setDefaultSettings(
        webView: WebView,
        userAgent: String = NetworkHelper.DEFAULT_USER_AGENT,
    ) {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgent

            // Handle popups properly
            setSupportMultipleWindows(true)

            // Allow zooming
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        runCatching { CookieManager.getInstance().acceptThirdPartyCookies(webView) }
    }

    /**
     * Toggles Desktop mode (Tahap 25.2) on an existing [WebView]: swaps the User-Agent
     * between a standard desktop Chrome agent and the mobile [mobileUserAgent] used by
     * the rest of the app, always keeping the wide-viewport flags on so pages re-layout
     * responsively after the page is reloaded.
     */
    fun applyDesktopMode(
        webView: WebView,
        desktop: Boolean,
        mobileUserAgent: String = NetworkHelper.DEFAULT_USER_AGENT,
    ) {
        with(webView.settings) {
            userAgentString = if (desktop) DESKTOP_USER_AGENT else mobileUserAgent
            useWideViewPort = true
            loadWithOverviewMode = true
        }
    }

    private fun getDefaultUserAgentString(webView: WebView): String {
        val originalUA: String = webView.settings.userAgentString
        // Next call to getUserAgentString() returns the default
        webView.settings.userAgentString = null
        val defaultUserAgentString = webView.settings.userAgentString
        // Revert to original UA string
        webView.settings.userAgentString = originalUA
        return defaultUserAgentString
    }
}
