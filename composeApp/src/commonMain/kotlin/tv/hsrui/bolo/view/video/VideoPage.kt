package tv.hsrui.bolo.view.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.player.VideoPlayer
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.player.VideoPlayerViewModel
import tv.hsrui.bolo.ui.common.player.PlayerPageLayout
import tv.hsrui.bolo.view.video.desc.VideoDescPage
import tv.hsrui.bolo.view.video.reply.VideoReplyPage

@Composable
fun VideoPage(
    uiState: VideoUiState.Success,
    modifier: Modifier = Modifier
) {
    val videoInfo = uiState.video
    val viewModel = viewModel(key = "player_${videoInfo.bvid}") {
        VideoPlayerViewModel(videoInfo.avid, videoInfo.cid)
    }
    val playerUiState by viewModel.uiState.collectAsState()
    val playerInfo by viewModel.controller.info.collectAsState()
    PlayerPageLayout(
        descContent = { VideoDescPage(videoInfo = videoInfo, modifier = Modifier.fillMaxSize()) },
        replyContent = { VideoReplyPage(videoInfo = videoInfo, modifier = Modifier.fillMaxSize()) },
        replyCount = videoInfo.stateCount.reply.toLong(),
        videoAspectRatio = playerInfo.video.aspectRatio.takeIf { playerUiState is VideoPlayerUiState.Success },
        modifier = modifier,
    ) { fullscreenState ->
        VideoPlayer(
            title = videoInfo.title,
            viewModel = viewModel,
            uiState = playerUiState,
            fullscreenState = fullscreenState,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
