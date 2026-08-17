package app.rocat.scripting.api.network

import app.rocat.scripting.api.FetchResult
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Performs an HTTP request on behalf of script code, re-using the app's OkHttp client
 * so every request flows through the same interceptors / cookie jar as the app itself.
 *
 * Never throws on network errors: failures are surfaced through [FetchResult.error] so
 * a hanging or crashing script cannot take the app down. Timeouts are bounded by the
 * per-call timeouts configured on the script client.
 */
suspend fun OkHttpClient.scriptFetch(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
    bodyMime: String = "application/json; charset=utf-8",
): FetchResult {
    return try {
        val request = buildScriptRequest(url, method, headers, body, bodyMime)
        val response = newCall(request).awaitCancellable()
        response.use { resp ->
            val responseBody = resp.body?.string() ?: ""
            FetchResult(
                status = resp.code,
                statusText = resp.message,
                headers = resp.headers.toMultimap().mapValues { (_, values) -> values.firstOrNull() ?: "" },
                body = responseBody,
            )
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        FetchResult(
            status = 0,
            headers = emptyMap(),
            body = "",
            error = e.message ?: e.javaClass.simpleName,
        )
    }
}

private fun buildScriptRequest(
    url: String,
    method: String,
    headers: Map<String, String>,
    body: String?,
    bodyMime: String,
): Request {
    val builder = Request.Builder().url(url)
    val headerBuilder = Headers.Builder()
    headers.forEach { (key, value) ->
        if (key.isNotBlank() && value.isNotBlank()) headerBuilder.add(key, value)
    }
    builder.headers(headerBuilder.build())

    val verb = method.uppercase()
    if (verb == "GET" || verb == "HEAD") {
        builder.method(verb, null)
    } else {
        val mime = bodyMime.toMediaType()
        val requestBody = body?.toRequestBody(mime) ?: "".toRequestBody(mime)
        builder.method(verb, requestBody)
    }
    return builder.build()
}

/**
 * Cancellable variant of OkHttp's callback API so a cancelled coroutine also cancels
 * the underlying HTTP call.
 */
private suspend fun Call.awaitCancellable(): Response = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
    continuation.invokeOnCancellation { cancel() }
}
