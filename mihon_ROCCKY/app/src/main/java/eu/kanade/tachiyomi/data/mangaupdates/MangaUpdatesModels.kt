package eu.kanade.tachiyomi.data.mangaupdates

data class MangaUpdatesSearchResult(
    val id: String?,
    val slug: String?,
    val title: String,
    val url: String,
    val image: String?,
    val adult: Boolean,
    val genres: List<String>,
    val description: String,
    val year: String?,
    val rating: String?,
)

data class MangaUpdatesDetailResult(
    val id: String?,
    val title: String?,
    val alternativeTitles: List<String>,
    val cover: String?,
    val url: String?,
    val synopsis: String?,
    val year: String?,
    val genres: List<String>,
    val authors: List<MULinkedItem>,
    val publishers: List<MULinkedItem>,
    val type: String?,
    val status: String?,
    val licensed: String?,
    val scanlated: String?,
    val associatedNames: List<String>,
    val groups: List<MULinkedItem>,
    val relatedSeries: List<MULinkedItem>,
    val recommendations: List<MULinkedItem>,
)

data class MULinkedItem(
    val name: String,
    val url: String?,
)
