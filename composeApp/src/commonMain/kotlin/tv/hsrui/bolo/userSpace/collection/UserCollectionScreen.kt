package tv.hsrui.bolo.userSpace.collection

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
fun UserCollectionScreen(mid: Long, seasonId: Long, modifier: Modifier = Modifier) {
    val viewModel = viewModel(key = "UserCollection:$mid:$seasonId") { UserCollectionViewModel(mid, seasonId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ShowTopBarWithNavigationButton(title = {
                Text((state as? UserCollectionUiState.Success)?.collection?.title ?: "视频合集", maxLines = 1, overflow = TextOverflow.Ellipsis)
            })
        },
    ) { padding ->
        when (val current = state) {
            UserCollectionUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UserCollectionUiState.Error -> ShowErrorContent(current.message, retry = viewModel::loadCollection, modifier = Modifier.padding(padding))
            is UserCollectionUiState.Success -> UserCollectionContent(
                state = current,
                onRefresh = viewModel::loadCollection,
                onSectionSelected = viewModel::selectSection,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun UserCollectionContent(
    state: UserCollectionUiState.Success,
    onRefresh: () -> Unit,
    onSectionSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val collection = state.collection
    val section = collection.sections.firstOrNull { it.sectionId == state.selectedSectionId }
    val videos = section?.episodes.orEmpty().filter { it.isAvailable && it.bvid.isNotBlank() }
        .map { it.videoCard }.distinctBy { it.avid }
    val scrollStates = rememberSaveableStateHolder()
    PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (collection.description.isNotBlank() && collection.description.trim() != collection.title.trim()) {
                Text(
                    collection.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (collection.sections.size > 1) {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(collection.sections, key = { it.sectionId }) { group ->
                        FilterChip(
                            selected = group.sectionId == state.selectedSectionId,
                            onClick = { onSectionSelected(group.sectionId) },
                            label = { Text(group.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            state.refreshError?.let { message ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f), maxLines = 2)
                    TextButton(onClick = onRefresh) { Text("重试") }
                }
            }
            if (videos.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().scrollable(rememberScrollableState { 0f }, Orientation.Vertical),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(if (collection.sections.isEmpty()) "暂无合集视频" else "此分段暂无可播放视频")
                    TextButton(onClick = onRefresh, enabled = !state.isRefreshing) { Text("刷新") }
                }
            } else scrollStates.SaveableStateProvider(state.selectedSectionId ?: 0L) {
                VideosGridPage(
                    uiState = VideosUiState.Success(videos),
                    isLoading = true,
                    onRefresh = onRefresh,
                    onLoadMore = {},
                    emptyMessage = if (collection.sections.isEmpty()) "暂无合集视频" else "此分段暂无可播放视频",
                    videoGridState = rememberLazyGridState(),
                    enablePullToRefresh = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
