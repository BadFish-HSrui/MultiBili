package tv.hsrui.bolo.ui.components.player

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.scrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.theme.BiliColor

data class PlaybackCollectionGroup(val id: String, val title: String)

data class PlaybackCollectionPart(val id: String, val title: String, val duration: String = "")

data class PlaybackCollectionItem(
    val id: String,
    val title: String,
    val badge: String = "",
    val enabled: Boolean = true,
    val parts: List<PlaybackCollectionPart> = emptyList(),
    val duration: String = "",
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
    playingPartId: String? = null,
    onPartSelected: (String) -> Unit = {},
    selectedItemContentColor: Color = Color.Unspecified,
    serverOrdered: Boolean = false,
    totalCount: Int = items.size,
    scrollRevision: Int = 0,
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    isLoadingPrevious: Boolean = false,
    isLoadingNext: Boolean = false,
    previousError: String? = null,
    nextError: String? = null,
    onLoadMore: (Boolean) -> Unit = {},
) {
    val displayedItems = remember(items, isDescending, serverOrdered) { if (isDescending && !serverOrdered) items.reversed() else items }
    val listState = rememberLazyListState()
    val groupState = rememberLazyListState()
    val playingIndex = displayedItems.indexOfFirst { it.id == playingItemId }
    val scrollItemsKey = if (serverOrdered) null else displayedItems
    val playingRowIndex = playingIndex.coerceAtLeast(0) + if (serverOrdered) 1 else 0
    val currentPlayingRowIndex by rememberUpdatedState(playingRowIndex)
    var partsExpanded by rememberSaveable(playingItemId) { mutableStateOf(true) }
    val partBounds = remember(selectedGroupId, playingItemId, scrollItemsKey, scrollRevision) { mutableStateMapOf<String, Pair<Int, Int>>() }
    LaunchedEffect(selectedGroupId, playingItemId, playingPartId, isDescending, scrollItemsKey, scrollRevision, isLoading, errorMessage) {
        if (isLoading || errorMessage != null) return@LaunchedEffect
        if (displayedItems.isNotEmpty()) listState.scrollToItem(playingRowIndex)
    }
    LaunchedEffect(selectedGroupId, playingItemId, playingPartId, isDescending, scrollItemsKey, scrollRevision, isLoading, errorMessage, partsExpanded) {
        if (isLoading || errorMessage != null) return@LaunchedEffect
        if (partsExpanded && displayedItems.getOrNull(playingIndex)?.parts?.any { it.id == playingPartId } == true) {
            // 展开动画结束后再定位 P 行；收起不重置列表位置，避免卡片向下跳动。
            delay(200)
            val (top, height) = snapshotFlow { partBounds[playingPartId] }.filterNotNull().first()
            val layout = listState.layoutInfo
            val visibleHeight = layout.viewportEndOffset - layout.viewportStartOffset - layout.afterContentPadding
            // 一张视频卡片可能高于整个视口；按内部 P 行定位，仍只滚动外层选集列表。
            listState.scrollToItem(currentPlayingRowIndex, (top + height - visibleHeight).coerceAtLeast(0))
        }
    }
    LaunchedEffect(serverOrdered, items.size, scrollRevision, hasPrevious, hasNext,
        isLoadingPrevious, isLoadingNext, previousError, nextError) {
        if (!serverOrdered) return@LaunchedEffect
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            (visible.firstOrNull()?.index ?: Int.MAX_VALUE) to (visible.lastOrNull()?.index ?: -1)
        }.collect { (first, last) ->
            if (first <= 2 && hasPrevious && !isLoadingPrevious && previousError == null) onLoadMore(true)
            if (last >= displayedItems.size - 2 && last >= 0 && hasNext && !isLoadingNext && nextError == null) onLoadMore(false)
        }
    }
    LaunchedEffect(groups, selectedGroupId) {
        val index = groups.indexOfFirst { it.id == selectedGroupId }
        if (index >= 0) groupState.animateScrollToItem(index)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(top = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (!isLoading && errorMessage == null) {
                    Text(totalCount.toString(), style = MaterialTheme.typography.labelMedium)
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
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).scrollbar(
                        state = listState.scrollIndicatorState,
                        orientation = Orientation.Vertical,
                        isFadeEnabled = false,
                        crossAxisTrackInset = 2.dp,
                    ),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (serverOrdered) item(key = "list_previous") {
                        if (isLoadingPrevious) LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp))
                        previousError?.let { message ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(message, modifier = Modifier.weight(1f), maxLines = 2)
                                TextButton(onClick = { onLoadMore(true) }) { Text("重试") }
                            }
                        }
                    }
                    items(displayedItems, key = { it.id }) { item ->
                        val playing = item.id == playingItemId
                        val hasParts = playing && item.parts.isNotEmpty()
                        val containerColor = if (playing) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Card(
                                onClick = { onItemSelected(item.id) },
                                enabled = item.enabled,
                                shape = MaterialTheme.shapes.small,
                                colors = CardDefaults.cardColors(containerColor = containerColor),
                                modifier = Modifier.fillMaxWidth()
                                    .animateContentSize(animationSpec = tween(200), alignment = Alignment.TopStart)
                                    .semantics { selected = playing },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(
                                        start = 8.dp,
                                        end = if (hasParts || item.duration.isNotBlank()) 0.dp else 8.dp,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (playing) selectedItemContentColor else Color.Unspecified,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
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
                                            modifier = Modifier.padding(start = 8.dp),
                                        ) {
                                            Text(
                                                item.badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                    if (hasParts) {
                                        IconButton(
                                            onClick = { partsExpanded = !partsExpanded },
                                            modifier = Modifier.padding(horizontal = 8.dp).size(24.dp),
                                        ) {
                                            Icon(
                                                if (partsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                contentDescription = if (partsExpanded) "收起分P" else "展开分P",
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    } else if (item.duration.isNotBlank()) {
                                        Text(
                                            item.duration,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 8.dp).alpha(0.75f),
                                        )
                                    }
                                }
                                if (hasParts && partsExpanded) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                    item.parts.forEach { part ->
                                        val playingPart = part.id == playingPartId
                                        Box(
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable(enabled = item.enabled, role = Role.Button) { onPartSelected(part.id) }
                                                .semantics { selected = playingPart }
                                                .onGloballyPositioned { coordinates ->
                                                    partBounds[part.id] = coordinates.positionInParent().y.roundToInt() to coordinates.size.height
                                                },
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    part.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (playingPart) BiliColor.Blue else MaterialTheme.colorScheme.onSecondaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f).padding(
                                                        start = 12.dp,
                                                        end = if (part.duration.isBlank()) 8.dp else 0.dp,
                                                        top = 4.dp,
                                                        bottom = 4.dp,
                                                    ),
                                                )
                                                if (part.duration.isNotBlank()) {
                                                    Text(
                                                        part.duration,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        maxLines = 1,
                                                        modifier = Modifier.padding(horizontal = 8.dp).alpha(0.75f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (serverOrdered) item(key = "list_next") {
                        if (isLoadingNext) LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp))
                        nextError?.let { message ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(message, modifier = Modifier.weight(1f), maxLines = 2)
                                TextButton(onClick = { onLoadMore(false) }) { Text("重试") }
                            }
                        }
                    }
                }
            }
        }
    }
}
