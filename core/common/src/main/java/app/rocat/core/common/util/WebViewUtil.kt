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
     * Applies the WebView configuration proven to run JavaScript in the reference
     * browser app **sweb-master** (Tahap 28 — ground truth for JS execution), kept
     * compatible with `DOCS_WEBVIEW.md` (Tahap 26) and mihon's `WebViewUtil`:
     * - JavaScript, DOM storage and the app database are enabled so SPA / heavy-JS pages
     *   never render blank. `allowUniversalAccessFromFileURLs` mirrors sweb-master's
     *   `createWebView()` — file:// frames may reach cross-origin resources the way the
     *   reference browser does (sweb's `setAppCacheEnabled(true)` is a no-op on modern
     *   Chromium where the API was removed; `cacheMode = LOAD_DEFAULT` covers it).
     * - `layoutAlgorithm = SINGLE_COLUMN` mirrors sweb-master's `createWebView()` and is
     *   kept for fidelity with the proven reference browser even though Chromium has
     *   deprecated the flag. Verification on https://www.capcut.com/id-id/signup (a
     *   dark-themed React SPA) during Tahap 28 found the page's SPA does not fully paint
     *   its theme in the emulator's Chrome-113 WebView in ANY configuration (bare,
     *   sweb-style, both UAs) — the page logs its own React hydration warnings
     *   (#418/#423/#425) plus a CSP inline-script refusal, which also affect the system
     *   browser. The page loads, the title hydrates ("Daftar - CapCut") and no app
     *   process crashes; the residual blank area is a page/engine-version issue, not a
     *   WebView-settings one.
     * - Mixed content is allowed in compatibility mode so http resources on https pages
     *   still load (§"Work with WebView on earlier versions" context).
     * - `setSupportMultipleWindows(true)` is set WITHOUT overriding
     *   `WebChromeClient.onCreateWindow`, the documented safest behavior that blocks
     *   `target="_blank"` popups while keeping the main frame rendering (§"Manage
     *   windows").
     * - Wide-viewport rendering, zoom and third-party cookies are supported; the browser
     *   presents the app's shared network identity ([NetworkHelper.DEFAULT_USER_AGENT], a
     *   modern Chrome UA exactly like sweb-master's hardcoded one) so OkHttp / scripts and
     *   the WebView see the same identity.
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
            // Tahap 28.2 (sweb-master `createWebView`): the reference browser enables the
            // application cache (`setAppCacheEnabled(true)`) so JS pages that persist
            // across navigations (SPA routing, offline assets) keep working. That API was
            // removed from `WebSettings` at compileSdk 33+ (Chromium dropped the app
            // cache), so `cacheMode = LOAD_DEFAULT` below already gives modern WebViews
            // the same HTTP-cache behavior — the sweb intent is preserved.
            // Tahap 28.2 (sweb-master `createWebView`): let file:// contexts access
            // cross-origin resources the way the proven reference browser allows.
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
            useWideViewPort = true
            loadWithOverviewMode = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgent
            // Tahap 27.4: let SPA/JS auto-play inline media without a prior tap so
            // media-heavy sites (CapCut, video editors, streaming) render their players.
            mediaPlaybackRequiresUserGesture = false

            // Handle popups properly (DOCS_WEBVIEW.md §"Manage windows"): enable
            // multi-window support but never override onCreateWindow so popups are
            // blocked and pages using target="_blank" don't hijack the browser.
            setSupportMultipleWindows(true)

            // Allow zooming
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        runCatching { CookieManager.getInstance().setAcceptCookie(true) }
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
