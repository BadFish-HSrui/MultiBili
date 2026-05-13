package tv.hsrui.bolo.ui.common.videosPage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.components.grid.ShowVerticalCardGrid
import tv.hsrui.bolo.ui.components.video.ShowVideoCard
import tv.hsrui.bolo.utils.OnGridBottomReached
import tv.hsrui.bolo.utils.setText

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
                var errorInfoButtonText by remember { mutableStateOf("复制错误信息") }
                val clipboard = LocalClipboard.current
                val scope = rememberCoroutineScope()

                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = {
                            scope.launch {
                                clipboard.setText(uiState.message)
                                errorInfoButtonText = "错误信息已复制"
                            }
                        }) {
                            Text(errorInfoButtonText)
                        }
                        Button(onClick = { viewModel.refreshVideos() }) {
                            Text("重试")
                        }
                    }
                }
            }

            is VideosUiState.Success -> {
                ShowVerticalCardGrid(
                    cards =  uiState.videos,
                    keySelector = { it.avid },
                    gridState = videoGridState,
                    modifier = modifier.fillMaxSize(),
                    needShowScrollToTopButton = true
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