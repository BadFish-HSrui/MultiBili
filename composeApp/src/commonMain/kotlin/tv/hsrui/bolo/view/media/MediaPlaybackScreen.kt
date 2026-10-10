package tv.hsrui.bolo.view.media

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
import tv.hsrui.bolo.player.PlayerFullscreenEffect
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.player.rememberPlayerFullscreenState
import tv.hsrui.bolo.ui.common.player.PlayerStatusBarOverlay
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton

@Composable
fun MediaPlaybackScreen(
    seasonId: Long = 0,
    modifier: Modifier = Modifier,
    episodeId: Long = 0,
    isActive: Boolean = true,
    viewModel: MediaPlaybackViewModel = composeViewModel(key = "media_${seasonId}_$episodeId") {
        MediaPlaybackViewModel(seasonId, episodeId)
    },
) {
    val uiState = viewModel.uiState.collectAsState().value
    LaunchedEffect(viewModel, isActive) {
        if (isActive) viewModel.bindPlayback()
    }
    val player = viewModel.playbackSession?.player
    val playerState = player?.uiState?.collectAsState()?.value
    val episode = (uiState as? MediaPlaybackUiState.Success)?.episode
    val ready = (playerState as? VideoPlayerUiState.Success)?.takeIf {
        episode != null && it.matches(episode.avid, episode.cid, episode.episodeId)
    }
    val playbackError = (playerState as? VideoPlayerUiState.Error)?.takeIf {
        episode != null && player.avid == episode.avid && player.cid == episode.cid && player.episodeId == episode.episodeId
    }
    var hasShownPage by rememberSaveable { mutableStateOf(false) }
    var lastAspectRatio by rememberSaveable { mutableStateOf<Float?>(null) }
    val videoAspectRatio = if (ready != null) ready.videoAspectRatio ?: (4F / 3F) else lastAspectRatio
    SideEffect {
        if (uiState is MediaPlaybackUiState.Success && episode == null) hasShownPage = true
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
            if (state is MediaPlaybackUiState.Success && (hasShownPage || ready != null || state.episode == null)) {
                MediaPlaybackPage(
                    uiState = state,
                    viewModel = viewModel,
                    fullscreenState = fullscreenState,
                    videoAspectRatio = videoAspectRatio,
                    isActive = isActive,
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    ShowTopBarWithNavigationButton(
                        onBack = if (fullscreenState.isDesktop) fullscreenState::goBack else null,
                        title = {},
                    )
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        when (state) {
                            MediaPlaybackUiState.Loading -> CircularProgressIndicator()
                            is MediaPlaybackUiState.Error -> ShowErrorContent(
                                message = state.message,
                                retry = { viewModel.loadMedia() },
                            )
                            is MediaPlaybackUiState.Success -> if (playbackError != null && episode != null) {
                                ShowErrorContent(
                                    message = playbackError.message,
                                    retry = {
                                        player.switchMedia(episode.avid, episode.cid, episode.episodeId, forceReload = true,
                                            seasonId = state.media.seasonId, seasonType = state.media.seasonType)
                                    },
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
