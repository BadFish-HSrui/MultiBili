package tv.hsrui.bolo.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.feature.search.SearchTrendingItemData

@Composable
fun ShowSearchTrending(
    state: SearchTrendingUiState,
    compact: Boolean,
    onClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowSpacing = when {
        compact -> 0.dp
        isExpanded() -> 16.dp
        else -> 8.dp
    }
    Column(modifier = modifier) {
        Box(Modifier.height(if (compact) 48.dp else 64.dp), contentAlignment = Alignment.CenterStart) {
            Text("热搜", style = MaterialTheme.typography.titleMedium)
        }
        when (state) {
            SearchTrendingUiState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
            is SearchTrendingUiState.Error -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.message, modifier = Modifier.weight(1f))
                TextButton(onClick = onRetry) { Text("重试") }
            }
            is SearchTrendingUiState.Success -> {
                val items = if (compact) state.items.take(10) else state.items
                if (items.isEmpty()) {
                    Text("暂无热搜", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(rowSpacing)) {
                        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                rowItems.forEachIndexed { columnIndex, item ->
                                    ShowSearchTrendingItem(
                                        item = item,
                                        number = if (compact) null else rowIndex * 2 + columnIndex + 1,
                                        onClick = { onClick(item.keyword) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShowSearchTrendingItem(
    item: SearchTrendingItemData,
    number: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
        ListItem(
            onClick = onClick,
            modifier = modifier.height(32.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (number != null) {
                    Text(
                        text = number.toString(),
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(32.dp).alpha(0.75f),
                    )
                }
                Text(
                    text = item.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.iconUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 2.dp).height(14.dp),
                    )
                }
            }
        }
    }
}
