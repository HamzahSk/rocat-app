package app.rocat.core.common.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Fills requests with the default browser headers a modern Chromium sends, but only
 * when the caller did not set its own value, so explicit per-request headers (e.g.
 * from a script) always win. Together with [app.rocat.core.common.network.UserAgentInterceptor]
 * this makes OkHttp look like a real browser to Cloudflare / anti-bot WAFs instead of
 * a minimal `fetch` client.
 */
class StealthHeadersInterceptor : Interceptor {

    private val defaultHeaders = listOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language" to "en-US,en;q=0.9",
        "Sec-CH-UA" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"141\", \"Google Chrome\";v=\"141\"",
        "Sec-CH-UA-Mobile" to "?1",
        "Sec-CH-UA-Platform" to "\"Android\"",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Sec-Fetch-User" to "?1",
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val builder = request.newBuilder()
        defaultHeaders.forEach { (name, value) ->
            if (request.header(name) == null) builder.header(name, value)
        }
        return chain.proceed(builder.build())
    }
}