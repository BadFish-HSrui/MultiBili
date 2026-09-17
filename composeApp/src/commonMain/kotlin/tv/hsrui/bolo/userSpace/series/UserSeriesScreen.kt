package tv.hsrui.bolo.userSpace.series

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage
import tv.hsrui.bolo.ui.common.videosPage.VideosUiState
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton

@Composable
fun UserSeriesScreen(mid: Long, seriesId: Long, modifier: Modifier = Modifier) {
    val viewModel = viewModel(key = "UserSeries:$mid:$seriesId") { UserSeriesViewModel(mid, seriesId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ShowTopBarWithNavigationButton(title = {
                Text((state as? UserSeriesUiState.Success)?.series?.title ?: "视频系列", maxLines = 1, overflow = TextOverflow.Ellipsis)
            })
        },
    ) { padding ->
        when (val current = state) {
            UserSeriesUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UserSeriesUiState.Error -> ShowErrorContent(current.message, retry = viewModel::loadSeries, modifier = Modifier.padding(padding))
            is UserSeriesUiState.Success -> UserSeriesVideosContent(
                state = current,
                onRefresh = viewModel::loadSeries,
                onLoadMore = viewModel::loadMoreVideos,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun UserSeriesVideosContent(
    state: UserSeriesUiState.Success,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (state.series.description.isNotBlank()) {
                Text(
                    state.series.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            state.refreshError?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f), maxLines = 2)
                    TextButton(onClick = onRefresh, enabled = !state.isRefreshing) { Text("重试") }
                }
            }
            if (state.videos.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().scrollable(rememberScrollableState { 0f }, Orientation.Vertical),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("暂无系列视频")
                    TextButton(onClick = onRefresh, enabled = !state.isRefreshing) { Text("刷新") }
                }
            } else VideosGridPage(
                uiState = VideosUiState.Success(state.videos),
                isLoading = state.isRefreshing || state.isLoadingMore || !state.canLoadMore ||
                    state.loadMoreError != null || state.refreshError != null,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                emptyMessage = "暂无系列视频",
                videoGridState = gridState,
                enablePullToRefresh = false,
                modifier = Modifier.weight(1f),
            )
            if (state.isLoadingMore) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
            state.loadMoreError?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f), maxLines = 2)
                    TextButton(onClick = onLoadMore, enabled = !state.isLoadingMore && !state.isRefreshing) { Text("重试") }
                }
            }
        }
    }
}
