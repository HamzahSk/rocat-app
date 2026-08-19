package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.manga.SourceRecommendation

@Composable
fun MangaRecommendationsRow(
    recommendations: List<SourceRecommendation>,
    isLoading: Boolean,
    onRecommendationClicked: (SourceRecommendation) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Jangan tampilkan apa-apa jika tidak ada rekomendasi dan tidak sedang loading
    if (recommendations.isEmpty() && !isLoading) return

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "Suggestions",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (isLoading) {
            // Tampilkan indikator loading sederhana saat data sedang di-fetch
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(recommendations) { rec ->
                    Column(
                        modifier = Modifier
                            .width(104.dp)
                            .clip(RoundedCornerShape(6.dp)) // Opsional: Biar efek ripple kliknya rapi mengikuti bentuk
                            .clickable { onRecommendationClicked(rec) },
                    ) {
                        AsyncImage(
                            model = rec.thumbnailUrl,
                            contentDescription = rec.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f) // Rasio standar cover manga
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = rec.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
