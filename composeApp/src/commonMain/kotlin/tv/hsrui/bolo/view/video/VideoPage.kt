package tv.hsrui.bolo.view.video

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import tv.hsrui.bolo.player.VideoPlayer
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.ui.common.player.PlayerPageLayout
import tv.hsrui.bolo.view.video.desc.VideoDescPage
import tv.hsrui.bolo.view.video.reply.VideoReplyPage

@Composable
fun VideoPage(
    uiState: VideoUiState.Success,
    videoViewModel: VideoViewModel,
    modifier: Modifier = Modifier
) {
    val videoInfo = uiState.video
    val viewModel = videoViewModel.playbackSession.player
    val playerUiState by viewModel.uiState.collectAsState()
    val playerInfo by viewModel.controller.info.collectAsState()
    // 简介操作、推荐和评论按稿件保留；同视频切 P 复用，换视频或离页时清理。
    val detailOwner = remember(videoInfo.avid) {
        object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
    }
    DisposableEffect(detailOwner) { onDispose { detailOwner.viewModelStore.clear() } }
    PlayerPageLayout(
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
        replyContent = {
            CompositionLocalProvider(LocalViewModelStoreOwner provides detailOwner) {
                key(videoInfo.avid) {
                    VideoReplyPage(videoInfo = videoInfo, modifier = Modifier.fillMaxSize())
                }
            }
        },
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
            onPreviousEpisode = if (uiState.hasPreviousEpisode) videoViewModel::selectPreviousEpisode else null,
            onNextEpisode = if (uiState.hasNextEpisode) videoViewModel::selectNextEpisode else null,
            episodeNavigationEnabled = uiState.episodeNavigationEnabled,
        )
    }
}
