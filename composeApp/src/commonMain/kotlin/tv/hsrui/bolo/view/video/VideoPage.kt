package tv.hsrui.bolo.view.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import tv.hsrui.bolo.player.PlayerFullscreenState
import tv.hsrui.bolo.player.VideoPlayer
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.ui.common.player.PlayerPageLayout
import tv.hsrui.bolo.view.video.desc.VideoDescPage
import tv.hsrui.bolo.view.video.reply.VideoReplyPage

@Composable
fun VideoPage(
    uiState: VideoUiState.Success,
    videoViewModel: VideoViewModel,
    fullscreenState: PlayerFullscreenState,
    videoAspectRatio: Float?,
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val videoInfo = uiState.video
    val viewModel = videoViewModel.playbackSession?.player
    val playerState = viewModel?.uiState?.collectAsState()?.value
    val playerUiState = playerState.takeIf {
        viewModel?.avid == videoInfo.avid && viewModel.cid == videoInfo.cid &&
            viewModel.episodeId == null && (it !is VideoPlayerUiState.Success || it.matches(videoInfo.avid, videoInfo.cid))
    } ?: VideoPlayerUiState.Loading
    // 稿件详情随导航页面保留；同视频切 P 复用，换稿件或移出返回栈时清理。
    val detailOwner = videoViewModel.getDetailOwner(videoInfo.avid)
    PlayerPageLayout(
        fullscreenState = fullscreenState,
        descContent = {
            CompositionLocalProvider(LocalViewModelStoreOwner provides detailOwner) {
                key(videoInfo.avid) {
                    VideoDescPage(
                        videoInfo = videoInfo,
                        collectionState = uiState,
                        onSectionSelected = videoViewModel::selectSection,
                        onEpisodeSelected = videoViewModel::selectCollectionEpisode,
                        onPartSelected = videoViewModel::selectVideoPart,
                        onDescendingChange = videoViewModel::setDescending,
                        onListVideoSelected = videoViewModel::selectListVideo,
                        onLoadMoreListVideos = videoViewModel::loadMoreListVideos,
                        onRetryList = { videoViewModel.loadVideoList(uiState.videoList?.pendingDescending ?: false) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        },
        replyContent = { content ->
            CompositionLocalProvider(LocalViewModelStoreOwner provides detailOwner) {
                key(videoInfo.avid) {
                    VideoReplyPage(
                        videoInfo = videoInfo,
                        isActive = isActive,
                        modifier = Modifier.fillMaxSize(),
                        content = content,
                    )
                }
            }
        },
        replyCount = videoInfo.stateCount.reply.toLong(),
        videoAspectRatio = videoAspectRatio,
        modifier = modifier,
    ) {
        if (viewModel != null) VideoPlayer(
            title = videoInfo.title,
            viewModel = viewModel,
            uiState = playerUiState,
            fullscreenState = fullscreenState,
            modifier = Modifier.fillMaxSize(),
            onPreviousEpisode = if (uiState.hasPreviousEpisode) videoViewModel::selectPreviousEpisode else null,
            onNextEpisode = if (uiState.hasNextEpisode) videoViewModel::selectNextEpisode else null,
            episodeNavigationEnabled = uiState.episodeNavigationEnabled,
        )
    }
}
