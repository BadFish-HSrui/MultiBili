package tv.hsrui.bolo.accountFeature.feature.watchLater

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.common.videosPage.VideosUiState
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.ui.components.video.ShowVerticalVideoCard
import tv.hsrui.bolo.utils.isExpanded

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WatchLaterScreen(
    modifier: Modifier = Modifier,
    isEntryFromList: Boolean = true,
    viewModel: WatchLaterViewModel = viewModel { WatchLaterViewModel() }
) {
    val uiState: VideosUiState by viewModel.uiState.collectAsState()
    val watchLaterGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isEntryFromList || !isExpanded()) {
                ShowTopBarWithNavigationButton(title = { Text("稍后再看") })
            }
        }
    ) { innerPadding ->

        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = {
                viewModel.refreshVideos()
            },
            modifier = Modifier.padding(top = innerPadding.calculateTopPadding())
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
                        message = (uiState as VideosUiState.Error).message,
                        retry = { viewModel.refreshVideos() }
                    )
                }

                is VideosUiState.Success -> {
                    Box {
                        ShowHorizontalCardGrid(
                            cards = (uiState as VideosUiState.Success).videos,
                            keySelector = { it.avid },
                            gridState = watchLaterGridState
                        ) { videoCard ->
                            ShowVerticalVideoCard(
                                videoInfo = videoCard,
                                modifier = Modifier.height(88.dp)
                            )
                        }
                        ShowGridFABMenu(
                            onBackToTop = { scope.launch { watchLaterGridState.animateScrollToItem(0) } },
                            onRefresh = { viewModel.refreshVideos() },
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
            }
        }
    }
}