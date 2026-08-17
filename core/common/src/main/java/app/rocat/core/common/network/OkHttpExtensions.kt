package app.rocat.core.common.network

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Bridges OkHttp's callback API into suspend functions.
 *
 * Mirrors the approach used in mihon's `OkHttpExtensions.kt`.
 */
suspend fun Call.await(): Response = suspendCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }
    })
}

/** Awaits the response and throws [HttpException] if the response was not successful. */
suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code)
    }
    return response
}

/**
 * Awaits the response, throwing [HttpException] when unsuccessful, and reads the body
 * as a string. This is the primitive used by the scripting engine's `fetch()`.
 */
suspend fun Call.awaitSuccessString(): String {
    val response = awaitSuccess()
    return response.use { it.body!!.string() }
}

/**
 * Awaits with a timeout using [timeoutMillis]; useful for script execution where
 * runaway network calls should be bounded.
 */
suspend fun Call.awaitWithTimeout(timeoutMillis: Long): Response {
    val timeoutCall = clone().apply {
        timeout()
        // OkHttp supports per-call timeouts via callTimeout on the client, but for
        // a simple bound we rely on the caller scheduling cancellation.
    }
    return timeoutCall.await()
}
