package app.rocat.core.common.network

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

private const val DEFAULT_CACHE_CONTROL = "max-age=3600"

/** Builds a GET request with sensible defaults (mirrors mihon's `Requests.kt`). */
fun GET(url: String, headers: HeadersBuilder = HeadersBuilder(), cacheControl: String? = DEFAULT_CACHE_CONTROL): Request {
    return GET(url.toHttpUrl(), headers, cacheControl)
}

fun GET(url: HttpUrl, headers: HeadersBuilder = HeadersBuilder(), cacheControl: String? = DEFAULT_CACHE_CONTROL): Request {
    val builder = Request.Builder()
        .url(url)
        .headers(headers.build())
    if (cacheControl != null) {
        builder.header("Cache-Control", cacheControl)
    }
    return builder.build()
}

/** Builds a POST request with an optional JSON body. */
fun POST(
    url: String,
    headers: HeadersBuilder = HeadersBuilder(),
    body: RequestBody? = null,
    cacheControl: String? = DEFAULT_CACHE_CONTROL,
): Request {
    val builder = Request.Builder()
        .url(url)
        .headers(headers.build())
        .post(body ?: FormBody.Builder().build())
    if (cacheControl != null) {
        builder.header("Cache-Control", cacheControl)
    }
    return builder.build()
}

/** Small helper that accumulates headers the same way mihon's `HeadersBuilder` does. */
class HeadersBuilder {
    private val map = LinkedHashMap<String, String>()

    fun add(key: String, value: String): HeadersBuilder {
        map[key] = value
        return this
    }

    fun addAll(headers: Map<String, String>): HeadersBuilder {
        map.putAll(headers)
        return this
    }

    fun build(): okhttp3.Headers = okhttp3.Headers.Builder()
        .apply { map.forEach { (k, v) -> add(k, v) } }
        .build()
}

/** Convenience builder for a JSON request body. */
fun jsonBody(content: String): RequestBody = content.toRequestBody("application/json; charset=utf-8".toMediaType())

/** Builds an [HttpUrl] from a string, throwing a descriptive exception on failure. */
fun String.toHttpUrl(): HttpUrl {
    return this.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid URL: $this")
}
