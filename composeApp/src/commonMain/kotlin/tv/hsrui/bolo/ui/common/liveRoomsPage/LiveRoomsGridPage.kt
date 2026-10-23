package tv.hsrui.bolo.ui.common.liveRoomsPage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowVerticalCardGrid
import tv.hsrui.bolo.ui.components.live.ShowLiveCard

@Composable
fun LiveRoomsGridPage(
    viewModel: LiveRoomsViewModel,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    reselectEvents: Flow<Unit>? = null,
    isSelected: Boolean = true,
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel, isSelected) {
        if (isSelected) viewModel.loadRooms()
    }
    LiveRoomsGridPage(
        uiState = uiState,
        onRefresh = viewModel::refreshRooms,
        onLoadMore = viewModel::loadMoreRooms,
        modifier = modifier,
        gridState = gridState,
        reselectEvents = reselectEvents,
        isSelected = isSelected,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveRoomsGridPage(
    uiState: LiveRoomsUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    reselectEvents: Flow<Unit>? = null,
    isSelected: Boolean = true,
) {
    val success = uiState as? LiveRoomsUiState.Success
    val scope = rememberCoroutineScope()
    val refresh: () -> Unit = {
        gridState.requestScrollToItem(0)
        onRefresh()
    }

    val currentState by rememberUpdatedState(uiState)
    val currentRefresh by rememberUpdatedState(refresh)

    LaunchedEffect(reselectEvents, gridState) {
        var scrollJob: Job? = null
        reselectEvents?.collect {
            if (scrollJob?.isActive == true) return@collect
            val current = currentState
            val currentSuccess = current as? LiveRoomsUiState.Success
            val isAtTop = gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
            if (currentSuccess?.rooms?.isNotEmpty() == true && !isAtTop) {
                scrollJob = launch { gridState.animateScrollToItem(0) }
            } else if (current !is LiveRoomsUiState.Loading &&
                currentSuccess?.isRefreshing != true && currentSuccess?.isLoadingMore != true
            ) {
                currentRefresh()
            }
        }
    }

    LaunchedEffect(isSelected, uiState, gridState) {
        val state = uiState as? LiveRoomsUiState.Success ?: return@LaunchedEffect
        if (!isSelected || state.isRefreshing || state.isLoadingMore || !state.hasMore ||
            state.refreshError != null || state.loadMoreError != null
        ) return@LaunchedEffect
        // 等待本页条目完成布局，再判断是否需要继续填充可见区域。
        snapshotFlow {
            val layout = gridState.layoutInfo
            layout.totalItemsCount > 0 && layout.totalItemsCount == state.rooms.size &&
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 8
        }.collect { reachedBottom ->
            if (reachedBottom) onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = success?.isRefreshing == true,
        onRefresh = refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val state = uiState) {
                    LiveRoomsUiState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    is LiveRoomsUiState.Error -> ShowErrorContent(
                        message = state.message,
                        retry = refresh,
                    )
                    is LiveRoomsUiState.Success -> if (state.rooms.isEmpty()) {
                        Text("暂无直播", modifier = Modifier.align(Alignment.Center))
                    } else {
                        ShowVerticalCardGrid(
                            cards = state.rooms,
                            keySelector = { it.roomId },
                            gridState = gridState,
                            modifier = Modifier.fillMaxSize(),
                        ) { room ->
                            ShowLiveCard(liveInfo = room, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                ShowGridFABMenu(
                    onBackToTop = { scope.launch { gridState.animateScrollToItem(0) } },
                    onRefresh = refresh,
                    enabled = uiState !is LiveRoomsUiState.Loading && success?.isRefreshing != true,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            if (success?.isLoadingMore == true) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            (success?.refreshError ?: success?.loadMoreError)?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = if (success?.refreshError != null) refresh else onLoadMore,
                    ) {
                        Text("重试")
                    }
                }
            }
        }
    }
}
