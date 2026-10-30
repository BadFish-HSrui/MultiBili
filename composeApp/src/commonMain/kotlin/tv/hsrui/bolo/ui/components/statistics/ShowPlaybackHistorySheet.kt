package tv.hsrui.bolo.ui.components.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import tv.hsrui.bolo.model.Vid
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackHistorySort
import tv.hsrui.bolo.ui.components.dialog.ShowLiveRoomInfoDialog
import tv.hsrui.bolo.ui.components.dialog.ShowUserInfoDialog
import tv.hsrui.bolo.ui.components.dialog.ShowVideoInfoDialog
import tv.hsrui.bolo.ui.components.error.ShowErrorContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowPlaybackHistorySheet(type: StatisticsHistoryType, onDismissRequest: () -> Unit) {
    key(type) {
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        val model = viewModel(viewModelStoreOwner = owner) { PlaybackHistoryViewModel(type) }
        val state by model.uiState.collectAsStateWithLifecycle()
        val listState = key(state.sort, state.descending) { rememberLazyListState() }
        var selectedId by remember { mutableStateOf<Long?>(null) }
        DisposableEffect(model) {
            model.load()
            onDispose { model.cancelLoading() }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP, onEvent = onDismissRequest)
        LaunchedEffect(listState, state.items.size, state.isLoading, state.isLoadingMore, state.hasMore, state.loadMoreError) {
            if (!state.isLoading && !state.isLoadingMore && state.hasMore && state.loadMoreError == null && state.items.isNotEmpty()) {
                snapshotFlow {
                    val layout = listState.layoutInfo
                    layout.totalItemsCount > 0 &&
                        (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 6
                }.distinctUntilChanged().collect { nearBottom ->
                    if (nearBottom) model.loadMore()
                }
            }
        }

        selectedId?.let { id ->
            when (type) {
                StatisticsHistoryType.Videos -> ShowVideoInfoDialog(Vid.AVid(id), onDismissRequest = { selectedId = null })
                StatisticsHistoryType.Ups -> ShowUserInfoDialog(id, onDismissRequest = { selectedId = null })
                StatisticsHistoryType.LiveRooms -> ShowLiveRoomInfoDialog(id, onDismissRequest = { selectedId = null })
            }
        }
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)
                    .nestedScroll(statisticsSheetNestedScrollConnection),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SingleChoiceSegmentedButtonRow(
                    Modifier.widthIn(max = 400.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                ) {
                    PlaybackHistorySort.entries.forEachIndexed { index, sort ->
                        val descending = state.descendingBySort.getValue(sort)
                        SegmentedButton(
                            selected = state.sort == sort,
                            onClick = { model.selectSort(sort) },
                            shape = SegmentedButtonDefaults.itemShape(index, PlaybackHistorySort.entries.size),
                            icon = {},
                        ) {
                            Row(
                                // 补偿右侧箭头内部留白，使图文可见区域居中。
                                modifier = Modifier.offset(x = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(when (sort) {
                                    PlaybackHistorySort.Time -> "时间"
                                    PlaybackHistorySort.PlayCount -> "次数"
                                    PlaybackHistorySort.Duration -> "时长"
                                })
                                Icon(
                                    imageVector = if (descending) Icons.Rounded.ArrowDropDown else Icons.Rounded.ArrowDropUp,
                                    contentDescription = if (descending) "降序" else "升序",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
                if (state.hasUnsavedPlayback) {
                    Text("部分播放记录尚未保存，当前显示已保存的数据。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    shape = MaterialTheme.shapes.medium.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
                ) {
                    when {
                        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        state.error != null -> ShowErrorContent(message = state.error.orEmpty(), retry = model::load)
                        state.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = when (type) {
                                    StatisticsHistoryType.Videos -> "还没有看过的视频"
                                    StatisticsHistoryType.Ups -> "还没有看过的UP主"
                                    StatisticsHistoryType.LiveRooms -> "还没有看过的直播间"
                                },
                                textAlign = TextAlign.Center,
                            )
                        }
                        else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                StatisticsRankingRow(
                                    rank = null,
                                    title = item.title,
                                    subtitle = if (type == StatisticsHistoryType.LiveRooms) "${item.playCount} 次观看" else "${item.playCount} 次播放",
                                    totalPlayedMs = item.totalPlayedMs,
                                    modifier = Modifier.clickable(enabled = item.id > 0) { selectedId = item.id },
                                    lastViewedAtMs = item.lastViewedAtMs,
                                )
                            }
                            item(key = "footer") {
                                Column(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    when {
                                        state.isLoadingMore -> CircularProgressIndicator()
                                        state.loadMoreError != null -> {
                                            Text(state.loadMoreError.orEmpty(), color = MaterialTheme.colorScheme.error)
                                            TextButton(onClick = model::loadMore) { Text("重试") }
                                        }
                                        !state.hasMore -> Text("已显示全部记录", style = MaterialTheme.typography.bodySmall)
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
