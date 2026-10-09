package tv.hsrui.bolo.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.player.base.BoloVideoPlayer
import tv.hsrui.bolo.player.controls.BoloPlayerControls
import tv.hsrui.bolo.player.danmaku.BoloDanmakuLayer
import tv.hsrui.bolo.player.settings.BoloPlayerSettings
import tv.hsrui.bolo.player.subtitle.BoloSubtitleLayer
import tv.hsrui.bolo.ui.components.error.ShowErrorContent

@Composable
fun VideoPlayer(
    title: String,
    viewModel: VideoPlayerViewModel,
    uiState: VideoPlayerUiState,
    fullscreenState: PlayerFullscreenState,
    modifier: Modifier = Modifier,
    onPreviousEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    episodeNavigationEnabled: Boolean = true,
) {
    val settings: BoloSettings = koinInject()
    val playerSettings: BoloPlayerSettings = koinInject()
    LaunchedEffect(viewModel, playerSettings.playback.mergeAudioChannelsEnabled) {
        viewModel.controller.setMergeAudioChannelsEnabled(playerSettings.playback.mergeAudioChannelsEnabled)
    }
    LaunchedEffect(
        viewModel, settings.playback.loudnessMode, settings.playback.dynamicLoudnessEnabled,
        settings.playback.dynamicLoudnessTargetLufs, settings.playback.dynamicLoudnessRangeLu,
        settings.playback.dynamicLoudnessTruePeakDbtp,
    ) {
        viewModel.controller.setLoudnessSettings(
            settings.playback.loudnessMode, settings.playback.dynamicLoudnessEnabled,
            settings.playback.dynamicLoudnessTargetLufs.toDouble(), settings.playback.dynamicLoudnessRangeLu.toDouble(),
            settings.playback.dynamicLoudnessTruePeakDbtp.toDouble(),
        )
    }
    LaunchedEffect(
        viewModel.subtitleController,
        settings.playback.subtitleAlwaysOn,
        settings.playback.subtitleSmartEnabled,
        settings.playback.subtitleAutoChineseOnly,
        settings.playback.subtitleAutoExcludeAi,
    ) {
        // 先同步过滤条件，避免启用自动字幕时短暂选中不符合条件的轨道。
        viewModel.subtitleController.autoChineseOnly = settings.playback.subtitleAutoChineseOnly
        viewModel.subtitleController.autoExcludeAi = settings.playback.subtitleAutoExcludeAi
        viewModel.subtitleController.smartEnabled = settings.playback.subtitleSmartEnabled
        viewModel.subtitleController.alwaysOn = settings.playback.subtitleAlwaysOn
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // 换集时保留输出宿主，加载反馈遮住旧画面，避免触发平台宿主的销毁与重建。
        BoloVideoPlayer(controller = viewModel.controller, modifier = Modifier.matchParentSize())
        when (uiState) {
            is VideoPlayerUiState.Loading -> Box(
                modifier = Modifier.matchParentSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            is VideoPlayerUiState.Error -> Surface(modifier = Modifier.matchParentSize()) {
                ShowErrorContent(
                    message = uiState.message,
                    retry = {
                        viewModel.switchMedia(viewModel.avid, viewModel.cid, viewModel.episodeId, forceReload = true,
                            seasonId = viewModel.seasonId, seasonType = viewModel.seasonType)
                    },
                )
            }
            is VideoPlayerUiState.Success -> {
                BoloDanmakuLayer(controller = viewModel.danmakuController)
                BoloSubtitleLayer(controller = viewModel.subtitleController)
                BoloPlayerControls(
                    title = title,
                    viewModel = viewModel,
                    fullscreenState = fullscreenState,
                    onPreviousEpisode = onPreviousEpisode,
                    onNextEpisode = onNextEpisode,
                    episodeNavigationEnabled = episodeNavigationEnabled,
                )
                if (uiState.videoSource.isPreview) {
                    Text(
                        text = "试看",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f)).padding(4.dp),
                    )
                }
            }
        }
        if (uiState !is VideoPlayerUiState.Success) {
            BoloPlayerControls(
                title = title,
                viewModel = viewModel,
                fullscreenState = fullscreenState,
                navigationOnly = true,
                navigationContentColor = if (uiState is VideoPlayerUiState.Error) {
                    MaterialTheme.colorScheme.onSurface
                } else Color.White,
            )
        }
    }
}
