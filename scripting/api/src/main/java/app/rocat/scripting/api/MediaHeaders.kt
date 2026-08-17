package app.rocat.scripting.api

import java.net.URI

/**
 * Media header resolution helpers (Tahap 24.1).
 *
 * Many image hosts and HLS/M3U8 providers block requests that carry no `Referer`.
 * The scripting bridge therefore resolves the effective headers for every media URL:
 * headers explicitly supplied by the script win, and a missing `Referer` is filled in
 * automatically from the script metadata `@match` base URL or — as a last resort — the
 * media URL's own origin.
 */

/** Returns the origin (`scheme://host[:port]`) of [url], or null when it is not a valid URL. */
fun urlOrigin(url: String): String? = runCatching {
    val uri = URI(url)
    val scheme = uri.scheme ?: return@runCatching null
    if (scheme !in SUPPORTED_SCHEMES) return@runCatching null
    val host = uri.host?.trimStart('.')?.takeIf { it.isNotBlank() } ?: return@runCatching null
    val port = if (uri.port != -1) ":${uri.port}" else ""
    "$scheme://$host$port"
}.getOrNull()

/** Derives a usable base URL from a script metadata `@match`/`@include` pattern. */
fun baseUrlFromMatch(match: String): String? {
    val trimmed = match.trim()
    if (trimmed.isEmpty()) return null
    // Strip glob wildcards and anything after the host so `https://*.site.org/*`
    // yields `https://site.org`.
    val noWildcards = trimmed.replace("*", "").trimEnd('/', ' ')
    val origin = urlOrigin(noWildcards)
    if (origin != null) return origin
    // Wildcard subdomains leave a leading dot (`https://.example.org/…`) that
    // java.net.URI rejects; strip it and retry.
    return urlOrigin(noWildcards.replace("://.", "://"))
}

/** First usable base URL across the script's `@match`/`@include` allow-list. */
fun baseUrlFromMatches(matches: List<String>): String? = matches.asSequence()
    .mapNotNull { baseUrlFromMatch(it) }
    .firstOrNull()

/**
 * Builds the effective headers for a media request at [url].
 *
 * - Headers supplied by the script are kept as-is (a case-insensitive `Referer` is
 *   normalised to the canonical casing).
 * - When no `Referer` is present, one is auto-filled: prefer [scriptBaseUrl] (derived
 *   from the script metadata) and fall back to the media URL's own origin.
 */
fun effectiveMediaHeaders(
    url: String,
    scriptHeaders: Map<String, String> = emptyMap(),
    scriptBaseUrl: String? = null,
): Map<String, String> {
    val headers = LinkedHashMap<String, String>()
    scriptHeaders.forEach { (name, value) ->
        val key = if (name.equals(REFERER_HEADER, ignoreCase = true)) REFERER_HEADER else name
        headers[key] = value
    }
    if (!headers.containsKey(REFERER_HEADER)) {
        val referer = scriptBaseUrl?.let { urlOrigin(it) } ?: urlOrigin(url)
        if (referer != null) headers[REFERER_HEADER] = referer
    }
    return headers
}

/** Case-insensitive set of URL schemes accepted by [urlOrigin]. */
private val SUPPORTED_SCHEMES = setOf("http", "https")

/** Canonical HTTP header name for the auto-filled referer. */
internal const val REFERER_HEADER: String = "Referer"