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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.player.PlayerFullscreenEffect
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.player.rememberPlayerFullscreenState
import tv.hsrui.bolo.ui.common.player.PlayerStatusBarOverlay
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton

@Composable
fun VideoScreen(
    request: VideoPlaybackRequest,
    isActive: Boolean = true,
    viewModel: VideoViewModel = composeViewModel(key = request.key) {
        VideoViewModel(request = request)
    },
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState.collectAsState().value
    val navigator = koinInject<Navigator>()
    LaunchedEffect(viewModel, isActive, uiState) {
        val redirect = uiState as? VideoUiState.RedirectToMedia ?: return@LaunchedEffect
        val expectedRoute = when (request) {
            is VideoPlaybackRequest.Single -> BoloRoute.View.Video(request.vid)
            is VideoPlaybackRequest.VideoList -> BoloRoute.View.VideoList(request)
        }
        if (isActive && navigator.backStack.lastOrNull() == expectedRoute && viewModel.uiState.value == redirect) {
            navigator.switchTo(redirect.route)
        }
    }
    LaunchedEffect(viewModel, isActive) {
        if (isActive && viewModel.uiState.value !is VideoUiState.RedirectToMedia) viewModel.bindPlayback()
    }
    val player = viewModel.playbackSession?.player
    val playerState = player?.uiState?.collectAsState()?.value
    val video = (uiState as? VideoUiState.Success)?.video
    val ready = (playerState as? VideoPlayerUiState.Success)?.takeIf {
        video != null && it.matches(video.avid, video.cid)
    }
    val playbackError = (playerState as? VideoPlayerUiState.Error)?.takeIf {
        video != null && player.avid == video.avid && player.cid == video.cid && player.episodeId == null
    }
    var hasShownPage by rememberSaveable { mutableStateOf(false) }
    var lastAspectRatio by rememberSaveable { mutableStateOf<Float?>(null) }
    val videoAspectRatio = if (ready != null) ready.videoAspectRatio ?: (4F / 3F) else lastAspectRatio
    SideEffect {
        if (ready != null) {
            hasShownPage = true
            lastAspectRatio = videoAspectRatio
        }
    }
    val fullscreenState = rememberPlayerFullscreenState()
    val fullscreenBackState = rememberNavigationEventState(NavigationEventInfo.None)
    val settings = koinInject<BoloSettings>()
    if (isActive) PlayerFullscreenEffect(fullscreenState, settings.playback.autoFullscreenOnRotateEnabled)
    NavigationBackHandler(
        state = fullscreenBackState,
        isBackEnabled = isActive && fullscreenState.shouldHandleFullscreenBack,
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
            val state = uiState
            if (state is VideoUiState.Success && (hasShownPage || ready != null)) {
                VideoPage(uiState = state, videoViewModel = viewModel, fullscreenState = fullscreenState,
                    videoAspectRatio = videoAspectRatio, isActive = isActive)
            } else {
                Column(Modifier.fillMaxSize()) {
                    ShowTopBarWithNavigationButton(
                        onBack = if (fullscreenState.isDesktop) fullscreenState::goBack else null,
                        title = {},
                    )
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (state) {
                            is VideoUiState.Loading, is VideoUiState.RedirectToMedia -> CircularProgressIndicator()
                            is VideoUiState.Error -> ShowErrorContent(
                                message = state.message,
                                retry = { viewModel.loadPlayback() },
                            )
                            VideoUiState.Empty -> Text("暂无可播放视频")
                            is VideoUiState.Success -> if (playbackError != null) {
                                ShowErrorContent(
                                    message = playbackError.message,
                                    retry = { player.switchMedia(state.video.avid, state.video.cid, forceReload = true) },
                                )
                            } else CircularProgressIndicator()
                        }
                    }
                }
            }
        }
        PlayerStatusBarOverlay(isFullscreen = fullscreenState.isFullscreen)
    }
}
