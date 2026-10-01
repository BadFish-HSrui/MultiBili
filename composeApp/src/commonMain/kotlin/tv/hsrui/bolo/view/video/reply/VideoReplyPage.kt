package tv.hsrui.bolo.view.video.reply

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.common.reply.RepliesGridPage
import tv.hsrui.bolo.ui.common.reply.RepliesViewModel
import tv.hsrui.network.feature.reply.ReplySectionType
import tv.hsrui.network.feature.video.VideoInfoData

@Composable
fun VideoReplyPage(
    videoInfo: VideoInfoData,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    videoRepliesViewModel: RepliesViewModel = viewModel(key = "reply_${videoInfo.bvid}") {
        RepliesViewModel(ReplySectionType.VideoReply(videoInfo.avid))
    },
    content: @Composable (
        mainContent: @Composable () -> Unit,
        overlayContent: @Composable () -> Unit,
    ) -> Unit,
) {
    val videoRepliesUiState by videoRepliesViewModel.uiState.collectAsState()
    RepliesGridPage(
        viewModel = videoRepliesViewModel,
        uiState = videoRepliesUiState,
        upMid = videoInfo.upMid,
        isActive = isActive,
        modifier = modifier,
        content = content,
    )
}