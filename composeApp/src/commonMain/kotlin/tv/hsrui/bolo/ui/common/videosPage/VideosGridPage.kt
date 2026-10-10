package tv.hsrui.bolo.ui.common.videosPage

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenuScope
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.components.dropdownMenu.items.WatchLaterMenuItem
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowVerticalCardGrid
import tv.hsrui.bolo.ui.components.video.ShowVideoCard
import tv.hsrui.bolo.utils.OnGridBottomReached

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideosGridPage(
    uiState: VideosUiState,
    viewModel: VideosViewModel,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null,
    reselectEvents: Flow<Unit>? = null,
    otherButton: @Composable FloatingActionButtonMenuScope.() -> Unit = {},
) {
    val videoGridState = rememberLazyGridState()
    VideosGridPage(
        uiState = uiState,
        isLoading = viewModel.isLoading,
        onRefresh = viewModel::refreshVideos,
        onLoadMore = viewModel::loadMoreVideos,
        modifier = modifier,
        emptyMessage = emptyMessage,
        videoGridState = videoGridState,
        reselectEvents = reselectEvents,
        reselectState = { viewModel.uiState.value to viewModel.isLoading },
        otherButton = otherButton,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideosGridPage(
    uiState: VideosUiState,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String? = null,
    videoGridState: LazyGridState = rememberLazyGridState(),
    enablePullToRefresh: Boolean = true,
    isRefreshing: Boolean = false,
    reselectEvents: Flow<Unit>? = null,
    reselectState: () -> Pair<VideosUiState, Boolean> = { uiState to isLoading },
    otherButton: @Composable FloatingActionButtonMenuScope.() -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val currentReselectState by rememberUpdatedState(reselectState)
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    LaunchedEffect(reselectEvents, videoGridState) {
        var scrollJob: Job? = null
        reselectEvents?.collect {
            if (scrollJob?.isActive == true) return@collect
            val (currentState, loading) = currentReselectState()
            val hasContent = currentState is VideosUiState.Success && currentState.videos.isNotEmpty()
            val isAtTop = videoGridState.firstVisibleItemIndex == 0 &&
                videoGridState.firstVisibleItemScrollOffset == 0
            if (hasContent && !isAtTop) {
                scrollJob = launch { videoGridState.animateScrollToItem(0) }
            } else if (currentState !is VideosUiState.Loading && !loading) {
                videoGridState.requestScrollToItem(0)
                currentOnRefresh()
            }
        }
    }

    videoGridState.OnGridBottomReached(buffer = 8, isLoading = isLoading) {
        onLoadMore()
    }

    LaunchedEffect(isLoading, uiState, enablePullToRefresh) {
        if (!enablePullToRefresh && !isLoading && uiState is VideosUiState.Success) {
            val layout = videoGridState.layoutInfo
            if (layout.totalItemsCount > 0 && (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 8) {
                onLoadMore()
            }
        }
    }
    val content: @Composable BoxScope.() -> Unit = {
        when (uiState) {
            is VideosUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is VideosUiState.Error -> {
                ShowErrorContent(
                    message = uiState.message,
                    retry = { onRefresh() }
                )
            }

            is VideosUiState.Success -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (uiState.videos.isEmpty() && emptyMessage != null) {
                        Text(
                            text = emptyMessage,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        ShowVerticalCardGrid(
                            cards = uiState.videos,
                            keySelector = { it.avid },
                            gridState = videoGridState,
                            modifier = Modifier.fillMaxSize()
                        ) { video ->
                            ShowVideoCard(
                                videoInfo = video,
                                modifier = Modifier.fillMaxSize()
                            ) { onDismiss ->
                                WatchLaterMenuItem(avid = video.avid, onDismiss = onDismiss)
                            }
                        }
                        ShowGridFABMenu(
                            onBackToTop = { scope.launch { videoGridState.animateScrollToItem(0) } },
                            onRefresh = { onRefresh() },
                            modifier = Modifier.align(Alignment.BottomEnd),
                            otherButton = otherButton,
                        )
                    }
                }
            }

        }
    }
    if (enablePullToRefresh) {
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize(), content = content)
    } else {
        Box(
            modifier = modifier.fillMaxSize().then(
                if (uiState !is VideosUiState.Success) {
                    Modifier.scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
                } else Modifier
            ),
            content = content,
        )
    }
}
