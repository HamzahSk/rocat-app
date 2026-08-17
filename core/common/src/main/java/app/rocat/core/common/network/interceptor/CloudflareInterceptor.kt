package app.rocat.core.common.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import app.rocat.core.common.network.AndroidCookieJar
import app.rocat.core.common.util.WebViewUtil
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * OkHttp [Interceptor] that transparently answers Cloudflare's JS challenge, mirroring
 * mihon's `CloudflareInterceptor`.
 *
 * When a response looks like a Cloudflare challenge (HTTP 403/503 behind a
 * `Server: cloudflare` header and a "Just a moment..." / challenge-error page), we
 * load the same URL in a hidden, headless [WebView] on the main thread. The real
 * Chromium engine (and its [android.webkit.CookieManager]) executes the Turnstile JS
 * challenge and, once solved, stores a `cf_clearance` cookie. Because OkHttp and the
 * WebView share the [AndroidCookieJar], the retried request automatically carries the
 * solved cookie and succeeds.
 *
 * The interceptor thread blocks for at most 30 seconds while the WebView works (OkHttp
 * interceptors are synchronous by design); afterwards the WebView is always destroyed
 * so no leaked [WebView] instances are left around.
 */
class CloudflareInterceptor(
    private val context: Context,
    private val cookieJar: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : Interceptor {

    private val defaultUserAgentProvider = defaultUserAgentProvider

    // All WebView work must happen on the main thread; posts from any thread are fine.
    private val mainExecutor = Handler(Looper.getMainLooper())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!shouldIntercept(response)) {
            return response
        }

        return try {
            // Consume the challenge body; the solved cookies are what we care about.
            response.close()
            // Drop any stale clearance token so the WebView has to solve a fresh one.
            cookieJar.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieJar.get(request.url).firstOrNull { it.name == "cf_clearance" }

            resolveWithWebView(request, oldCookie)

            // The retried request now carries cf_clearance from AndroidCookieJar.
            chain.proceed(request)
        } catch (e: CloudflareBypassException) {
            // A raw failure here would crash OkHttp's callback thread, so wrap it.
            throw IOException("Cloudflare challenge could not be bypassed", e)
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    /**
     * Returns true when [response] looks like a Cloudflare anti-bot challenge that a
     * real browser could answer but a plain HTTP client cannot.
     */
    private fun shouldIntercept(response: Response): Boolean {
        if (response.code !in ERROR_CODES) return false
        if (response.header("Server")?.lowercase(Locale.ENGLISH)?.contains("cloudflare") != true) return false

        return try {
            val document = Jsoup.parse(
                response.peekBody(Long.MAX_VALUE).string(),
                response.request.url.toString(),
            )
            // Solve only the real JS challenges (captcha / "Just a moment".), not geo blocks.
            document.getElementById("challenge-error-title") != null ||
                document.getElementById("challenge-error-text") != null ||
                (document.title()?.lowercase(Locale.ENGLISH)?.contains("just a moment") == true)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Runs the URL inside a verbose WebView until a (new) cf_clearance cookie lands in
     * [cookieJar] or [TIMEOUT_SECONDS] elapse, then always destroys the WebView.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)

        // Shared between the WebView (main) and interceptor (IO) threads.
        val state = ChallengeState()

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)
        val userAgent = originalRequest.header("User-Agent") ?: defaultUserAgentProvider()

        mainExecutor.post {
            val view = WebView(context).apply {
                WebViewUtil.setDefaultSettings(this, userAgent)
            }
            state.webview = view

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(page: WebView, url: String) {
                    fun isCloudFlareBypassed(): Boolean = cookieJar.get(origRequestUrl.toHttpUrl())
                        .firstOrNull { it.name == "cf_clearance" }
                        .let { it != null && it != oldCookie }

                    if (isCloudFlareBypassed()) {
                        state.cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == origRequestUrl && !state.challengeFound) {
                        // A page loaded without tripping the challenge: nothing left to solve.
                        latch.countDown()
                    }
                }

                override fun onReceivedHttpError(
                    page: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        if (errorResponse?.statusCode in ERROR_CODES) {
                            // "Just a moment..." page is served as an HTTP error: challenge found.
                            state.challengeFound = true
                        } else {
                            // An unrelated error: allow the latch to release without bypass.
                            latch.countDown()
                        }
                    }
                }
            }

            view.loadUrl(origRequestUrl, headers)
        }

        awaitFor30Seconds(latch)

        // Always tear the WebView down (headless, not attached to any window) so no
        // WebView instance leaks between challenge attempts.
        mainExecutor.post {
            state.webview?.run {
                stopLoading()
                runCatching { destroy() }
            }
            state.webview = null
        }

        if (!state.cloudflareBypassed) {
            throw CloudflareBypassException()
        }
    }

    private fun awaitFor30Seconds(latch: CountDownLatch) {
        latch.await(30, TimeUnit.SECONDS)
    }

    /**
     * Converts OkHttp headers into the plain map [WebView.loadUrl] accepts, dropping
     * forbidden headers; Chromium WebView throws `ERR_INVALID_ARGUMENT` on those.
     */
    private fun parseHeaders(headers: okhttp3.Headers): Map<String, String> {
        return headers
            .filter { (name, value) -> isRequestHeaderSafe(name, value) }
            .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
            .mapValues { (_, values) -> values.firstOrNull().orEmpty() }
    }
}

private val ERROR_CODES = listOf(403, 503)
private val COOKIE_NAMES = listOf("cf_clearance")

// Derived from Chromium's IsRequestHeaderSafe (services/network/public/cpp/header_util.cc).
private fun isRequestHeaderSafe(_name: String, _value: String): Boolean {
    val name = _name.lowercase(Locale.ENGLISH)
    val value = _value.lowercase(Locale.ENGLISH)
    if (name in unsafeHeaderNames || name.startsWith("proxy-")) return false
    if (name == "connection" && value == "upgrade") return false
    return true
}

private val unsafeHeaderNames = listOf(
    "content-length", "host", "trailer", "te", "upgrade", "cookie2",
    "keep-alive", "transfer-encoding", "set-cookie",
)

private class CloudflareBypassException : Exception()

/**
 * Cross-thread state for a single challenge attempt: written on the main (WebView)
 * thread, read on the interceptor (IO) thread after the latch releases.
 */
private class ChallengeState {
    @Volatile var webview: WebView? = null
    @Volatile var cloudflareBypassed: Boolean = false
    @Volatile var challengeFound: Boolean = false
}