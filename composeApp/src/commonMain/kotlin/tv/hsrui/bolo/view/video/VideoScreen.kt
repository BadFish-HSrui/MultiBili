package tv.hsrui.bolo.view.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import tv.hsrui.bolo.player.PlayerFullscreenEffect
import tv.hsrui.bolo.player.rememberPlayerFullscreenState
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
    val fullscreenState = rememberPlayerFullscreenState()
    val fullscreenBackState = rememberNavigationEventState(NavigationEventInfo.None)
    PlayerFullscreenEffect(fullscreenState)
    NavigationBackHandler(
        state = fullscreenBackState,
        isBackEnabled = fullscreenState.canExitFullscreen,
        onBackCompleted = fullscreenState::exitFullscreen,
    )

    Box(
        modifier.fillMaxSize().then(
            if (!fullscreenState.isDesktop && fullscreenState.isFullscreen) {
                // 全屏时仅消费顶部安全区，不让状态栏显隐推动页面及播放器子内容。
                Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            } else Modifier
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is VideoUiState.Success -> {
                    VideoPage(uiState = state, videoViewModel = viewModel, fullscreenState = fullscreenState)
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
        PlayerStatusBarOverlay(isFullscreen = fullscreenState.isFullscreen)
    }
}
