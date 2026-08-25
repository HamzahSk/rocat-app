package app.rocat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

/** Vertical spacing applied both between and around grid rows/tiles. */
private val GridSpacing = 8.dp

/** Fixed tile height so the grid's total height can be measured for the parent column. */
private val GridTileHeight = 196.dp

/**
 * The mihon-style media grid produced by `RoCatUI.addGrid(...)`. Rendered as a
 * [LazyVerticalGrid] whose height is computed from the tile count and column count, so
 * it can live harmlessly inside an outer [androidx.compose.foundation.lazy.LazyColumn]:
 * each row is [GridTileHeight] tall, which lets the whole grid scroll with the page
 * instead of fighting the column for scroll events.
 */
@Composable
fun GridComponent(
    grid: ScriptUIComponent.Grid,
    onItemClick: (GridItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = grid.items
    if (items.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val adaptive = 132.dp
        val columns = maxOf(1, minOf(grid.columns.coerceIn(1, 8), (maxWidth / adaptive).toInt()))
        val rows = (items.size + columns - 1) / columns
        val totalHeight = GridTileHeight * rows + GridSpacing * (rows - 1) + 8.dp
        LazyVerticalGrid(
            columns = GridCells.Adaptive(adaptive),
            modifier = Modifier.fillMaxWidth().height(totalHeight),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(GridSpacing),
            verticalArrangement = Arrangement.spacedBy(GridSpacing),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(count = items.size, key = { index -> index }) { index ->
                val item = items[index]
                GridTile(item = item, height = GridTileHeight, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun GridTile(
    item: GridItem,
    height: Dp,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val imageRequest = remember(item.imageUrl, item.headers) {
        ImageRequest.Builder(context).data(item.imageUrl).apply {
            if (item.headers.isNotEmpty()) {
                httpHeaders(
                    NetworkHeaders.Builder().apply {
                        item.headers.forEach { (name, value) -> set(name, value) }
                    }.build(),
                )
            }
        }.build()
    }

    Card(onClick = onClick, modifier = Modifier.height(height)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (item.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        Icons.Outlined.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = item.title.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
