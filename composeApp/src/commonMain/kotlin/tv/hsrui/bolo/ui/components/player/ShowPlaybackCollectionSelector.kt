package tv.hsrui.bolo.ui.components.player

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.theme.BiliColor

data class PlaybackCollectionGroup(val id: String, val title: String)

data class PlaybackCollectionItem(
    val id: String,
    val title: String,
    val badge: String = "",
    val enabled: Boolean = true,
)

@Composable
fun ShowPlaybackCollectionSelector(
    groups: List<PlaybackCollectionGroup>,
    selectedGroupId: String,
    items: List<PlaybackCollectionItem>,
    playingItemId: String?,
    isDescending: Boolean,
    onGroupSelected: (String) -> Unit,
    onItemSelected: (String) -> Unit,
    onDescendingChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "选集",
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    val displayedItems = remember(items, isDescending) { if (isDescending) items.reversed() else items }
    val listState = rememberLazyListState()
    val groupState = rememberLazyListState()
    val playingIndex = displayedItems.indexOfFirst { it.id == playingItemId }
    LaunchedEffect(selectedGroupId, playingItemId, isDescending, displayedItems) {
        if (displayedItems.isNotEmpty()) listState.scrollToItem(playingIndex.coerceAtLeast(0))
    }
    LaunchedEffect(groups, selectedGroupId) {
        val index = groups.indexOfFirst { it.id == selectedGroupId }
        if (index >= 0) groupState.animateScrollToItem(index)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (!isLoading && errorMessage == null) {
                    Text(items.size.toString(), style = MaterialTheme.typography.labelMedium)
                }
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 24.dp) {
                    TextButton(
                        onClick = { onDescendingChange(!isDescending) },
                        modifier = Modifier.height(24.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SwapVert,
                            contentDescription = "切换为${if (isDescending) "正序" else "倒序"}",
                            modifier = Modifier.size(20.dp),
                        )
                        Text(if (isDescending) "倒序" else "正序")
                    }
                }
            }
            if (groups.size > 1) {
                LazyRow(
                    state = groupState,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        FilterChip(
                            selected = group.id == selectedGroupId,
                            onClick = { onGroupSelected(group.id) },
                            label = { Text(group.title) },
                        )
                    }
                }
            }
            when {
                isLoading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp))
                errorMessage != null -> Box(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    ShowErrorContent(message = errorMessage, retry = onRetry)
                }
                displayedItems.isEmpty() -> Text("暂无可选内容", Modifier.padding(16.dp))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(displayedItems, key = { it.id }) { item ->
                        val playing = item.id == playingItemId
                        val containerColor = if (playing) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Card(
                                onClick = { onItemSelected(item.id) },
                                enabled = item.enabled,
                                shape = MaterialTheme.shapes.small,
                                colors = CardDefaults.cardColors(containerColor = containerColor),
                                modifier = Modifier.fillMaxWidth().semantics { selected = playing },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (item.badge.isNotBlank()) {
                                        val isVipBadge = item.badge.contains("会员")
                                        val isPreviewBadge = item.badge.contains("预告")
                                        Surface(
                                            color = when {
                                                isVipBadge -> BiliColor.ThemeColor
                                                isPreviewBadge -> BiliColor.Blue
                                                else -> MaterialTheme.colorScheme.tertiaryContainer
                                            },
                                            contentColor = if (isVipBadge || isPreviewBadge) Color.White
                                            else MaterialTheme.colorScheme.onTertiaryContainer,
                                            shape = MaterialTheme.shapes.extraSmall,
                                        ) {
                                            Text(
                                                item.badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
