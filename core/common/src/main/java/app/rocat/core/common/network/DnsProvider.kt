package app.rocat.core.common.network

/**
 * The supported DNS-over-HTTPS (DoH) selection modes exposed in the Network settings
 * (Tahap 20.2). [SYSTEM] leaves the platform (system) resolver untouched; the other
 * values route OkHttp's DNS lookups through a DoH endpoint.
 */
enum class DnsMode {
    /** Use the platform/system resolver (no custom DNS). */
    SYSTEM,

    /** Cloudflare 1.1.1.1 (`https://cloudflare-dns.com/dns-query`). */
    CLOUDFLARE,

    /** Google Public DNS 8.8.8.8 (`https://dns.google/dns-query`). */
    GOOGLE,

    /** Quad9 9.9.9.9 (`https://dns.quad9.net/dns-query`). */
    QUAD9,

    /** A user-supplied DoH endpoint URL (see [DnsProviders.resolve]). */
    CUSTOM,
}

/** Maps a [DnsMode] to its DoH endpoint URL and keeps the well-known endpoints. */
object DnsProviders {
    const val CLOUDFLARE_DOH = "https://cloudflare-dns.com/dns-query"
    const val GOOGLE_DOH = "https://dns.google/dns-query"
    const val QUAD9_DOH = "https://dns.quad9.net/dns-query"

    /** Resolves the active DoH endpoint for [mode], or null when system DNS is used. */
    fun dohUrl(mode: DnsMode, customUrl: String?): String? = when (mode) {
        DnsMode.SYSTEM -> null
        DnsMode.CLOUDFLARE -> CLOUDFLARE_DOH
        DnsMode.GOOGLE -> GOOGLE_DOH
        DnsMode.QUAD9 -> QUAD9_DOH
        DnsMode.CUSTOM -> customUrl?.trim().takeIf { !it.isNullOrBlank() }
    }
}