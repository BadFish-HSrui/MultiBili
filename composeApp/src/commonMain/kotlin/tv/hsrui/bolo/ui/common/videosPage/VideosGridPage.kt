package tv.hsrui.bolo.ui.common.videosPage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    modifier: Modifier = Modifier
) {
    val videoGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    videoGridState.OnGridBottomReached(buffer = 8, isLoading = viewModel.isLoading) {
        viewModel.loadMoreVideos()
    }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = {
            viewModel.refreshVideos()
        },
        modifier = modifier
            .fillMaxSize()
    ) {
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
                    retry = { viewModel.refreshVideos() }
                )
            }

            is VideosUiState.Success -> {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
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
                        onRefresh = { viewModel.refreshVideos() },
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }

        }
    }
}