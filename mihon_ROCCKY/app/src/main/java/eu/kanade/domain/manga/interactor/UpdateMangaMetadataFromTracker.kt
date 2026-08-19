package eu.kanade.domain.manga.interactor

import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.SManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.repository.MangaRepository
import java.io.InputStream

class UpdateMangaMetadataFromTracker(
    private val mangaRepository: MangaRepository,
    private val updateManga: UpdateManga,
    private val coverCache: CoverCache,
    private val networkHelper: NetworkHelper,
) {

    suspend fun await(manga: Manga, trackSearch: TrackSearch): Result<Unit> {
        return try {
            val update = mapToMangaUpdate(manga.id, trackSearch)
            mangaRepository.update(update)

            if (trackSearch.cover_url.isNotBlank()) {
                downloadAndCacheCover(manga, trackSearch.cover_url)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mapToMangaUpdate(mangaId: Long, trackSearch: TrackSearch): MangaUpdate {
        return MangaUpdate(
            id = mangaId,
            title = trackSearch.title.ifBlank { null },
            author = trackSearch.authors.firstOrNull(),
            artist = trackSearch.artists.firstOrNull(),
            description = trackSearch.summary.ifBlank { null },
            status = mapPublishingStatus(trackSearch.publishing_status),
            thumbnailUrl = trackSearch.cover_url.ifBlank { null },
            initialized = true,
        )
    }

    private fun mapPublishingStatus(publishingStatus: String): Long {
        return when (publishingStatus.lowercase().replace(" ", "_")) {
            "finished", "completed" -> SManga.COMPLETED.toLong()
            "releasing", "currently_publishing", "ongoing" -> SManga.ONGOING.toLong()
            "not_yet_published", "not_yet_released" -> SManga.UNKNOWN.toLong()
            "cancelled" -> SManga.LICENSED.toLong()
            "hiatus" -> SManga.ONGOING.toLong()
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
            val inputStream: InputStream = body.byteStream()
            coverCache.setCustomCoverToCache(manga, inputStream)
            inputStream.close()

            updateManga.awaitUpdateCoverLastModified(manga.id)
        } catch (_: Exception) {
        }
    }
}
