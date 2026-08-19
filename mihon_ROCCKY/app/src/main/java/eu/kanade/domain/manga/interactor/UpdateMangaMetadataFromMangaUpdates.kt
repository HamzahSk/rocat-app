package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.mangaupdates.MangaUpdatesDetailResult
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository

class UpdateMangaMetadataFromMangaUpdates(
    private val mangaRepository: MangaRepository,
    private val updateManga: UpdateManga,
    private val coverCache: CoverCache,
    private val networkHelper: NetworkHelper,
) {

    suspend fun await(manga: Manga, detail: MangaUpdatesDetailResult): Result<Unit> {
        return try {
            val update = mapToMangaUpdate(manga.id, detail)
            mangaRepository.update(update)

            val coverUrl = detail.cover
            if (!coverUrl.isNullOrBlank()) {
                downloadAndCacheCover(manga, coverUrl)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapToMangaUpdate(mangaId: Long, detail: MangaUpdatesDetailResult): MangaUpdate {
        val author = detail.authors.firstOrNull()?.name
        val artist = detail.authors.getOrNull(1)?.name ?: author
        val description = detail.synopsis?.ifBlank { null }
        val genre = detail.genres.ifEmpty { null }

        return MangaUpdate(
            id = mangaId,
            title = detail.title?.ifBlank { null },
            author = author,
            artist = artist,
            description = description,
            genre = genre,
            status = mapStatus(detail.status),
            thumbnailUrl = detail.cover?.ifBlank { null },
            initialized = true,
        )
    }

    private fun mapStatus(status: String?): Long {
        if (status == null) return SManga.UNKNOWN.toLong()
        return when (status.lowercase().replace(" ", "_")) {
            "ongoing" -> SManga.ONGOING.toLong()
            "completed" -> SManga.COMPLETED.toLong()
            "hiatus",
            "on_hiatus",
            -> SManga.ON_HIATUS.toLong()
            "cancelled" -> SManga.CANCELLED.toLong()
            "licensed_(in_english)" -> SManga.LICENSED.toLong()
            else -> SManga.UNKNOWN.toLong()
        }
    }

    private suspend fun downloadAndCacheCover(manga: Manga, coverUrl: String) {
        try {
            val request = okhttp3.Request.Builder()
                .url(coverUrl)
                .build()
            val response = networkHelper.client.newCall(request).execute()
            val body = response.body ?: return
            val inputStream = body.byteStream()
            coverCache.setCustomCoverToCache(manga, inputStream)
            inputStream.close()

            updateManga.awaitUpdateCoverLastModified(manga.id)
        } catch (_: Exception) {
        }
    }
}
