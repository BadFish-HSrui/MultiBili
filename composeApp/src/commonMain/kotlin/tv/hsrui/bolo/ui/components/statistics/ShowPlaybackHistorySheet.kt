package tv.hsrui.bolo.ui.components.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import tv.hsrui.bolo.model.Vid
import tv.hsrui.bolo.storage.appData.playbackStatistics.PlaybackHistoryType
import tv.hsrui.bolo.ui.components.dialog.ShowUserInfoDialog
import tv.hsrui.bolo.ui.components.dialog.ShowVideoInfoDialog
import tv.hsrui.bolo.ui.components.error.ShowErrorContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowPlaybackHistorySheet(type: PlaybackHistoryType, onDismissRequest: () -> Unit) {
    key(type) {
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        val model = viewModel(viewModelStoreOwner = owner) { PlaybackHistoryViewModel(type) }
        val state by model.uiState.collectAsStateWithLifecycle()
        val listState = rememberLazyListState()
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
                PlaybackHistoryType.Videos -> ShowVideoInfoDialog(Vid.AVid(id), onDismissRequest = { selectedId = null })
                PlaybackHistoryType.Ups -> ShowUserInfoDialog(id, onDismissRequest = { selectedId = null })
            }
        }
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded)),
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (type == PlaybackHistoryType.Videos) "看过的视频" else "看过的UP主",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (state.hasUnsavedPlayback) {
                    Text("部分播放记录尚未保存，当前显示已保存的数据。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                when {
                    state.isLoading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.error != null -> ShowErrorContent(
                        message = state.error.orEmpty(), retry = model::load, modifier = Modifier.weight(1f),
                    )
                    state.items.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(if (type == PlaybackHistoryType.Videos) "还没有看过的视频" else "还没有看过的UP主")
                    }
                    else -> Card(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(Modifier.fillMaxSize(), state = listState) {
                            itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                val title = item.title?.takeIf { it.isNotBlank() }
                                    ?: if (type == PlaybackHistoryType.Videos) "av${item.id}" else "UP ${item.id}"
                                StatisticsRankingRow(
                                    rank = index + 1,
                                    title = if (item.partTitle.isNullOrBlank()) title else "$title · ${item.partTitle}",
                                    subtitle = "${item.playCount} 次播放",
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
