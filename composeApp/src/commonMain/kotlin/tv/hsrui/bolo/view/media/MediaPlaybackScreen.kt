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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.player.PlayerFullscreenEffect
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
            when (val state = uiState) {
                is MediaPlaybackUiState.Success -> MediaPlaybackPage(
                    uiState = state,
                    viewModel = viewModel,
                    fullscreenState = fullscreenState,
                    isActive = isActive,
                )
                else -> Column(Modifier.fillMaxSize()) {
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
                            is MediaPlaybackUiState.Success -> Unit
                        }
                    }
                }
            }
        }
        PlayerStatusBarOverlay(isFullscreen = fullscreenState.isFullscreen)
    }
}
