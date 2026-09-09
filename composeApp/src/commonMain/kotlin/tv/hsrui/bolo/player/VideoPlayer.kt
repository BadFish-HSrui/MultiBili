package tv.hsrui.bolo.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.player.base.BoloVideoPlayer
import tv.hsrui.bolo.player.controls.BoloPlayerControls
import tv.hsrui.bolo.player.danmaku.BoloDanmakuLayer
import tv.hsrui.bolo.player.subtitle.BoloSubtitleLayer
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.network.feature.video.VideoInfoData

@Composable
fun VideoPlayer(
    videoInfo: VideoInfoData,
    viewModel: VideoPlayerViewModel,
    uiState: VideoPlayerUiState,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {

    when (uiState) {
        is VideoPlayerUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is VideoPlayerUiState.Error -> {
            ShowErrorContent(
                message = uiState.message,
                retry = { }
            )
        }

        is VideoPlayerUiState.Success -> {
            Box(modifier.fillMaxSize()) {
                BoloVideoPlayer(controller = viewModel.controller)
                BoloDanmakuLayer(controller = viewModel.danmakuController)
                BoloSubtitleLayer(controller = viewModel.subtitleController)
                BoloPlayerControls(
                    videoInfo = videoInfo,
                    viewModel = viewModel,
                    isFullscreen = isFullscreen,
                    onFullscreenChange = onFullscreenChange
                )
            }
        }
    }
}
