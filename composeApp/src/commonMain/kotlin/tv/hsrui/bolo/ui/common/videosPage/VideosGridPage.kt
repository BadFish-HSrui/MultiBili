package tv.hsrui.bolo.ui.common.videosPage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowVerticalCardGrid
import tv.hsrui.bolo.ui.components.video.ShowVideoCard
import tv.hsrui.bolo.utils.OnGridBottomReached

@Composable
fun VideosGridPage(
    uiState: VideosUiState,
    viewModel: VideosViewModel,
    modifier: Modifier = Modifier
) {
    val videoGridState = rememberLazyGridState()

    videoGridState.OnGridBottomReached(buffer = 8, isLoading = viewModel.isLoading) {
        viewModel.loadMoreVideos()
    }

    PullToRefreshBox(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = {
            viewModel.refreshVideos()
        }
    ) {
        when (uiState) {
            is VideosUiState.Loading -> {
                Box(
                    modifier = modifier.fillMaxSize(),
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
                ShowVerticalCardGrid(
                    cards =  uiState.videos,
                    keySelector = { it.avid },
                    gridState = videoGridState
                ) { video ->
                    ShowVideoCard(
                        videoInfo = video,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

        }
    }
}