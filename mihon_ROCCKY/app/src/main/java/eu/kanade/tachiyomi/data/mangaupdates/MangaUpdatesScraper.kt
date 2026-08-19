package eu.kanade.tachiyomi.data.mangaupdates

import eu.kanade.tachiyomi.network.NetworkHelper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tachiyomi.core.common.util.lang.withIOContext

class MangaUpdatesScraper(
    private val network: NetworkHelper,
) {

    companion object {
        private const val SEARCH_URL = "https://www.mangaupdates.com/series"
    }

    suspend fun search(query: String): List<MangaUpdatesSearchResult> = withIOContext {
        val url = "$SEARCH_URL?search=${query.encodeForUrl()}&perpage=10"
        val html = network.client.newCall(okhttp3.Request.Builder().url(url).build()).execute()
            .body.string()
        parseSearch(html)
    }

    suspend fun detail(url: String): MangaUpdatesDetailResult = withIOContext {
        val html = network.client.newCall(okhttp3.Request.Builder().url(url).build()).execute()
            .body.string()
        parseDetail(html)
    }

    private fun parseSearch(html: String): List<MangaUpdatesSearchResult> {
        val doc = Jsoup.parse(html)
        val results = mutableListOf<MangaUpdatesSearchResult>()

        for (card in doc.select(".col-12.col-lg-6.p-3.text")) {
            val title = card.select(".linked-name-module__9zptFq__name_underline").first()
                ?.text()?.trim() ?: ""
            val url = card.select("a[title=\"Click for Series Info\"]").first()
                ?.attr("href") ?: ""
            val image = card.select("img").first()?.attr("src")
            val adult = card.select(".series-box-module__K7yETa__adult").isNotEmpty()
            val genres = card.select(".textsmall .text-truncate").text()
                .trim()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val description = card.select(".mu-markdown-module___SC9hG__mu_markdown").text()
                .replace(Regex("\\s+"), " ")
                .trim()
            val infoText = card.select("> .row .series-box-module__K7yETa__mw_flex .text").last()
                ?.text()?.replace(Regex("\\s+"), " ")?.trim() ?: ""
            val year = Regex("\\d{4}").find(infoText)?.value
            val rating = card.select("b").first()?.text()?.trim()
            val slug = url.split("/").lastOrNull()
            val id = url.split("/").getOrNull(url.split("/").size - 2)

            results.add(
                MangaUpdatesSearchResult(
                    id = id,
                    slug = slug,
                    title = title,
                    url = url,
                    image = image,
                    adult = adult,
                    genres = genres,
                    description = description,
                    year = year,
                    rating = rating,
                ),
            )
        }

        return results
    }

    private fun parseDetail(html: String): MangaUpdatesDetailResult {
        val doc = Jsoup.parse(html)

        val jsonLdScript = doc.select("script[type=\"application/ld+json\"]").first()
        val jsonLd = jsonLdScript?.html()?.let { parseJsonLd(it) } ?: emptyMap()

        val authors = parseJsonLdArray(jsonLd, "author")
        val publishers = parseJsonLdArray(jsonLd, "publisher")

        var status: String? = null
        var type: String? = null
        var licensed: String? = null
        var scanlated: String? = null
        val associatedNames = mutableListOf<String>()
        val groups = mutableListOf<MULinkedItem>()
        val relatedSeries = mutableListOf<MULinkedItem>()
        val recommendations = mutableListOf<MULinkedItem>()

        for (infoBox in doc.select(".info-box-module__gIhiNW__sCat")) {
            val key = infoBox.text().trim()
            val valueBox = infoBox.nextElementSibling()

            when (key) {
                "Type" -> type = valueBox?.text()?.trim()
                "Status in Country of Origin" -> {
                    status = valueBox?.text()?.replace(Regex("\\s+"), " ")?.trim()
                }
                "Licensed (in English)" -> licensed = valueBox?.text()?.trim()
                "Completely Scanlated?" -> scanlated = valueBox?.text()?.trim()
                "Associated Names" -> {
                    valueBox?.select("div")?.forEach { div ->
                        associatedNames.add(div.text().trim())
                    }
                }
                "Groups Scanlating" -> {
                    valueBox?.select("a")?.forEach { a ->
                        groups.add(
                            MULinkedItem(
                                name = a.text().trim(),
                                url = a.attr("href"),
                            ),
                        )
                    }
                }
                "Related Series" -> {
                    valueBox?.select("a")?.forEach { a ->
                        relatedSeries.add(
                            MULinkedItem(
                                name = a.text().trim(),
                                url = a.attr("href"),
                            ),
                        )
                    }
                }
                "Recommendations" -> {
                    valueBox?.select("a")?.forEach { a ->
                        recommendations.add(
                            MULinkedItem(
                                name = a.text().trim(),
                                url = a.attr("href"),
                            ),
                        )
                    }
                }
            }
        }

        return MangaUpdatesDetailResult(
            id = jsonLd["identifier"] as? String,
            title = jsonLd["name"] as? String,
            alternativeTitles = (jsonLd["alternateName"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            cover = jsonLd["image"] as? String,
            url = jsonLd["url"] as? String,
            synopsis = jsonLd["description"] as? String,
            year = jsonLd["datePublished"] as? String,
            genres = (jsonLd["genre"] as? List<*>)?.map { it.toString() } ?: emptyList(),
            authors = authors.map { MULinkedItem(name = it.name, url = it.url) },
            publishers = publishers.map { MULinkedItem(name = it.name, url = it.url) },
            type = type,
            status = status,
            licensed = licensed,
            scanlated = scanlated,
            associatedNames = associatedNames,
            groups = groups,
            relatedSeries = relatedSeries,
            recommendations = recommendations,
        )
    }

    private data class JsonLdPerson(
        val name: String,
        val url: String?,
    )

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonLdArray(json: Map<String, Any?>, key: String): List<JsonLdPerson> {
        val value = json[key] ?: return emptyList()
        return when (value) {
            is List<*> -> value.mapNotNull { item ->
                when (item) {
                    is Map<*, *> -> JsonLdPerson(
                        name = item["name"] as? String ?: "",
                        url = item["url"] as? String,
                    )
                    else -> null
                }
            }
            is Map<*, *> -> listOf(
                JsonLdPerson(
                    name = value["name"] as? String ?: "",
                    url = value["url"] as? String,
                ),
            )
            else -> emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseJsonLd(jsonStr: String): Map<String, Any?> {
        return try {
            val element = kotlinx.serialization.json.Json
                .decodeFromString<kotlinx.serialization.json.JsonElement>(jsonStr)
            jsonElementToMap(element)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun jsonElementToMap(element: kotlinx.serialization.json.JsonElement): Map<String, Any?> {
        val obj = element as? kotlinx.serialization.json.JsonObject ?: return emptyMap()
        return obj.mapValues { (_, value) ->
            jsonElementToValue(value)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun jsonElementToValue(element: kotlinx.serialization.json.JsonElement): Any? {
        return when (element) {
            is kotlinx.serialization.json.JsonPrimitive -> element.content
            is kotlinx.serialization.json.JsonObject -> jsonElementToMap(element)
            is kotlinx.serialization.json.JsonArray -> element.map { jsonElementToValue(it) }
        }
    }

    private fun String.encodeForUrl(): String {
        return java.net.URLEncoder.encode(this, "UTF-8")
    }
}
