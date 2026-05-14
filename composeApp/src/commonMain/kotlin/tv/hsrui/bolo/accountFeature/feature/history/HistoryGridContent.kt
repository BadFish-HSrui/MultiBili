package tv.hsrui.bolo.accountFeature.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.video.ShowHistoryVideoCard
import tv.hsrui.bolo.utils.OnGridBottomReached

@Composable
fun HistoryGridContent(
    modifier: Modifier = Modifier,
    viewModel: HistoryVideosViewModel = viewModel { HistoryVideosViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val historyGridState = rememberLazyGridState()

    historyGridState.OnGridBottomReached(buffer = 8, isLoading = viewModel.isLoading) {
        viewModel.loadMoreVideos()
    }

    PullToRefreshBox(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = {
            viewModel.refreshVideos()
        },
        modifier = modifier
    ) {
        when (uiState) {
            is HistoryVideosUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is HistoryVideosUiState.Error -> {
                ShowErrorContent(
                    message = (uiState as HistoryVideosUiState.Error).message,
                    retry = { viewModel.refreshVideos() }
                )
            }

            is HistoryVideosUiState.Success -> {
                ShowHorizontalCardGrid(
                    cards = (uiState as HistoryVideosUiState.Success).videos,
                    keySelector = { it.avid },
                    gridState = historyGridState
                ) { video ->
                    ShowHistoryVideoCard(
                        videoInfo = video,
                        modifier = Modifier.height(96.dp)
                    )
                }
            }
        }
    }
}