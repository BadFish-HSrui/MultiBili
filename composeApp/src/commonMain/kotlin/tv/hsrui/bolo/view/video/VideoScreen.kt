package tv.hsrui.bolo.view.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import tv.hsrui.bolo.player.session.BoloPlaybackSession
import tv.hsrui.bolo.ui.common.player.PlayerStatusBarOverlay
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton

@Composable
fun VideoScreen(
    request: VideoPlaybackRequest,
    viewModel: VideoViewModel = remember(request.key) {
        BoloPlaybackSession.obtain(request.key).getViewModel(request.key) { VideoViewModel(request = request) }
    },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is VideoUiState.Success -> {
                    VideoPage(uiState = state, videoViewModel = viewModel)
                }
                else -> Column(Modifier.fillMaxSize()) {
                    ShowTopBarWithNavigationButton(title = {})
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (state) {
                            is VideoUiState.Loading -> CircularProgressIndicator()
                            is VideoUiState.Error -> ShowErrorContent(
                                message = state.message,
                                retry = viewModel::loadPlayback,
                            )
                            VideoUiState.Empty -> Text("暂无可播放视频")
                            is VideoUiState.Success -> Unit
                        }
                    }
                }
            }
        }
        PlayerStatusBarOverlay()
    }
}
