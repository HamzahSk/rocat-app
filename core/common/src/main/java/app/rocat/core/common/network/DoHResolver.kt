package app.rocat.core.common.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * An OkHttp [Dns] implementation that resolves hostnames over DNS-over-HTTPS (DoH),
 * Tahap 20.3. Replaces OkHttp's system `Dns.SYSTEM` when the user picks a non-system
 * resolver in the Network settings.
 *
 * Lookups issue a synchronous RFC-8484-style JSON GET (`?name=<host>&type=A`) against
 * the configured [endpoint] (Cloudflare / Google / Quad9 / custom). A records (type 1,
 * IPv4) and AAAA records (type 28, IPv6) from the JSON `Answer` section are returned.
 *
 * Failures never break a connect: when the DoH request itself fails, times out or
 * returns no usable answer, the lookup transparently falls back to the platform
 * resolver (`Dns.SYSTEM`). DoH traffic uses a dedicated tiny client pinned to
 * `Dns.SYSTEM` so the resolver never recurses into itself.
 */
class DoHResolver(
    private val endpoint: String,
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build(),
) : Dns {

    // Never resolve the DoH host itself through DoH (it would recurse forever).
    private val client: OkHttpClient = client.newBuilder()
        .dns(Dns.SYSTEM)
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val queryUrl = endpoint.toHttpUrl().newBuilder()
                .addQueryParameter("name", hostname)
                .addQueryParameter("type", "A")
                .build()
            val request = Request.Builder()
                .url(queryUrl)
                .header("Accept", "application/dns-json")
                .header("User-Agent", NetworkHelper.DEFAULT_USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    fallback(hostname)
                } else {
                    val answers = parseAnswers(response.body?.string().orEmpty())
                    answers.ifEmpty { fallback(hostname) }
                }
            }
        } catch (e: Exception) {
            fallback(hostname)
        }
    }

    /** Falls back to the classic system resolver. */
    private fun fallback(hostname: String): List<InetAddress> = try {
        Dns.SYSTEM.lookup(hostname)
    } catch (e: Exception) {
        emptyList()
    }

    /**
     * Parses the Google/Cloudflare JSON response `{"Answer":[{"type":1,"data":"1.2.3.4"},...]}`
     * and returns only A/AAAA address records that parse to [InetAddress]es.
     */
    private fun parseAnswers(body: String): List<InetAddress> {
        if (body.isBlank()) return emptyList()
        return try {
            val root = Json.parseToJsonElement(body).jsonObject
            val answers = root["Answer"] ?: return emptyList()
            answers.jsonArray.mapNotNull { element ->
                val obj = element.jsonObject
                val type = obj["type"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                val data = obj["data"]?.jsonPrimitive?.contentOrNull
                if (data == null || (type != 1 && type != 28)) {
                    null
                } else {
                    runCatching { InetAddress.getByName(data) }.getOrNull()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}