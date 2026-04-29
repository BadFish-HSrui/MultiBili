package tv.hsrui.bolo.ui.common.videosPage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.ui.components.ShowVerticalVideoGrid
import tv.hsrui.bolo.utils.OnGridBottomReached

@Composable
fun VideosGridPage(
    uiState: VideosUiState,
    viewModel: VideosViewModel,
    modifier: Modifier = Modifier
) {
    val videoGridState = rememberLazyGridState()

    videoGridState.OnGridBottomReached(buffer = 4, isLoading = viewModel.isLoading) {
        viewModel.loadMoreVideos()
    }

    PullToRefreshBox(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = {
            viewModel.refreshVideos()
        }
    ){
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
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.message)
                }
            }

            is VideosUiState.Success -> {
                ShowVerticalVideoGrid(
                    uiState.videos,
                    gridState = videoGridState,
                    modifier = modifier
                )
            }

        }
    }
}