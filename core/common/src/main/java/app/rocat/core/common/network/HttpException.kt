package app.rocat.core.common.network

import java.io.IOException

/**
 * Thrown when an HTTP response is not successful (2xx).
 *
 * Mirrors `HttpException` from the mihon codebase so callers can react to
 * non-successful responses with a typed, catchable exception.
 */
class HttpException(val code: Int) : IOException("HTTP error $code")
