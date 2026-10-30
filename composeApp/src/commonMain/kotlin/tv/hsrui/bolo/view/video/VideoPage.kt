package tv.hsrui.bolo.view.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.bolo.navigation.openUserSpace
import tv.hsrui.bolo.player.PlayerFullscreenState
import tv.hsrui.bolo.player.VideoPlayer
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.ui.common.player.PlayerPageLayout
import tv.hsrui.bolo.userSpace.ShowUserUploadsSheet
import tv.hsrui.bolo.ui.components.video.ShowVerticalVideoCard
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
    val settings: BoloSettings = koinInject()
    var selectedUpMid by remember(videoInfo.avid) { mutableStateOf<Long?>(null) }
    LaunchedEffect(isActive, settings.general.upRecentUploadsSheetEnabled) {
        if (!isActive || !settings.general.upRecentUploadsSheetEnabled) selectedUpMid = null
    }
    val viewModel = videoViewModel.playbackSession?.player
    val playerState = viewModel?.uiState?.collectAsState()?.value
    val playerUiState = playerState.takeIf {
        viewModel?.avid == videoInfo.avid && viewModel.cid == videoInfo.cid &&
            viewModel.episodeId == null && (it !is VideoPlayerUiState.Success || it.matches(videoInfo.avid, videoInfo.cid))
    } ?: VideoPlayerUiState.Loading
    val recommendation = videoViewModel.pendingRecommendation
    // 稿件详情随导航页面保留；同视频切 P 复用，换稿件或移出返回栈时清理。
    val detailOwner = videoViewModel.getDetailOwner(videoInfo.avid)
    PlayerPageLayout(
        fullscreenState = fullscreenState,
        playerViewModel = viewModel,
        danmakuMediaKey = videoInfo.avid to videoInfo.cid,
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
                        onUpClick = { mid ->
                            if (isActive && mid > 0) {
                                if (settings.general.upRecentUploadsSheetEnabled) selectedUpMid = mid
                                else openUserSpace(mid)
                            }
                        },
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
            endedContent = if (recommendation == null) null else {
                {
                    Column(
                        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "即将播放：",
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        ShowVerticalVideoCard(
                            videoInfo = recommendation,
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            onClick = videoViewModel::playPendingRecommendation,
                        )
                        LinearProgressIndicator(
                            progress = { videoViewModel.recommendationCountdownProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White,
                        )
                    }
                }
            },
        )
    }
    if (isActive && settings.general.upRecentUploadsSheetEnabled) {
        selectedUpMid?.let { mid ->
            ShowUserUploadsSheet(mid = mid, onDismissRequest = { selectedUpMid = null })
        }
    }
}
