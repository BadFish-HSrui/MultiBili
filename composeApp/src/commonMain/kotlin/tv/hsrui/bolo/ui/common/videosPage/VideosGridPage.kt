package tv.hsrui.bolo.ui.common.videosPage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.ui.components.ShowVerticalVideoGrid

@Composable
fun VideosGridPage(
    uiState: VideosUiState,
    viewModel: VideosViewModel,
    modifier: Modifier = Modifier
) {
    val videoGridState = rememberLazyGridState()

    videoGridState.OnBottomReached(buffer = 4, isLoading = viewModel.isLoading) {
        viewModel.loadMoreVideos()
    }

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

@Composable
fun LazyGridState.OnBottomReached(
    buffer: Int = 0,
    isLoading: Boolean,
    onLoadMore: () -> Unit
) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems != 0 && lastVisibleItemIndex >= totalItems - 1 - buffer
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (!isLoading) {
            onLoadMore()
        }
    }
}