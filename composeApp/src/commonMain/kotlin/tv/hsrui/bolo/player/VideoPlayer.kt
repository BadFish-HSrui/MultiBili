package tv.hsrui.bolo.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
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
    val settings: BoloSettings = koinInject()
    LaunchedEffect(
        viewModel.subtitleController,
        settings.subtitleAlwaysOn,
        settings.subtitleAutoChineseOnly,
        settings.subtitleAutoExcludeAi,
    ) {
        // 先同步过滤条件，避免启用自动字幕时短暂选中不符合条件的轨道。
        viewModel.subtitleController.autoChineseOnly = settings.subtitleAutoChineseOnly
        viewModel.subtitleController.autoExcludeAi = settings.subtitleAutoExcludeAi
        viewModel.subtitleController.alwaysOn = settings.subtitleAlwaysOn
    }

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
