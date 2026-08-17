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
     * and the app database are enabled, wide-viewport rendering is on, popups and zoom
     * are supported and third-party cookies are accepted. The User-Agent is pinned to
     * [NetworkHelper]'s so JS fetch / WebView rendering see the same identity.
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
