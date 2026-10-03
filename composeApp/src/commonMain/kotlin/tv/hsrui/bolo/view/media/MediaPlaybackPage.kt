package tv.hsrui.bolo.view.media

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import coil3.compose.AsyncImage
import tv.hsrui.bolo.navigation.openMedia
import tv.hsrui.bolo.player.PlayerFullscreenState
import tv.hsrui.bolo.player.VideoPlayer
import tv.hsrui.bolo.player.VideoPlayerUiState
import tv.hsrui.bolo.player.controls.BoloPlayerControls
import tv.hsrui.bolo.ui.common.player.PlayerPageLayout
import tv.hsrui.bolo.ui.common.reply.RepliesGridPage
import tv.hsrui.bolo.ui.common.reply.RepliesUiState
import tv.hsrui.bolo.ui.common.reply.RepliesViewModel
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.media.ShowMediaCard
import tv.hsrui.bolo.ui.components.media.actionsBar.MediaActionsBar
import tv.hsrui.bolo.ui.components.player.PlaybackCollectionGroup
import tv.hsrui.bolo.ui.components.player.PlaybackCollectionItem
import tv.hsrui.bolo.ui.components.player.ShowPlaybackCollectionSelector
import tv.hsrui.network.feature.media.MediaEpisode
import tv.hsrui.network.feature.media.MediaSeasonData
import tv.hsrui.network.feature.reply.ReplySectionType
import tv.hsrui.network.utils.formatCountToString

@Composable
fun MediaPlaybackPage(
    uiState: MediaPlaybackUiState.Success,
    viewModel: MediaPlaybackViewModel,
    fullscreenState: PlayerFullscreenState,
    isActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val episode = uiState.episode
    val playerViewModel = viewModel.playbackSession?.player
    val playerUiState = playerViewModel?.uiState?.collectAsState()?.value
    val playerInfo = playerViewModel?.controller?.info?.collectAsState()?.value
    var videoAspectRatio by rememberSaveable(episode?.episodeId) { mutableStateOf<Float?>(null) }
    SideEffect {
        if (playerUiState is VideoPlayerUiState.Success) {
            playerInfo?.video?.aspectRatio?.takeIf { it.isFinite() && it > 0F }?.let { videoAspectRatio = it }
        }
    }
    // 当前集评论随导航页面保留，切集或移出返回栈时释放。
    val replyOwner = viewModel.getDetailOwner(episode?.episodeId)
    val repliesViewModel = if (episode != null) {
        composeViewModel(viewModelStoreOwner = replyOwner) { RepliesViewModel(ReplySectionType.VideoReply(episode.avid)) }
    } else null
    val repliesUiState = repliesViewModel?.uiState?.collectAsState()?.value

    PlayerPageLayout(
        fullscreenState = fullscreenState,
        descContent = { MediaDescPage(uiState = uiState, viewModel = viewModel, modifier = Modifier.fillMaxSize()) },
        replyContent = { content ->
            key(episode?.episodeId) {
                if (repliesViewModel != null && repliesUiState != null) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides replyOwner) {
                        RepliesGridPage(
                            viewModel = repliesViewModel,
                            uiState = repliesUiState,
                            upMid = uiState.media.upMid,
                            isActive = isActive,
                            modifier = Modifier.fillMaxSize(),
                            content = content,
                        )
                    }
                } else {
                    content(
                        {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无剧集评论") }
                        },
                        {},
                    )
                }
            }
        },
        replyCount = (repliesUiState as? RepliesUiState.Success)?.totalReplyCount,
        videoAspectRatio = videoAspectRatio,
        modifier = modifier,
    ) {
        if (playerViewModel == null || playerUiState == null) return@PlayerPageLayout
        if (episode != null) {
            VideoPlayer(
                title = "${uiState.media.title} ${episode.displayTitle}",
                viewModel = playerViewModel,
                uiState = playerUiState,
                fullscreenState = fullscreenState,
                modifier = Modifier.fillMaxSize(),
                onPreviousEpisode = if (uiState.hasPreviousEpisode) viewModel::selectPreviousEpisode else null,
                onNextEpisode = if (uiState.hasNextEpisode) viewModel::selectNextEpisode else null,
                episodeNavigationEnabled = uiState.episodeNavigationEnabled,
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无可播放剧集", color = Color.White)
                BoloPlayerControls(
                    title = uiState.media.title,
                    viewModel = playerViewModel,
                    fullscreenState = fullscreenState,
                    navigationOnly = true,
                )
            }
        }
    }
}

@Composable
private fun MediaDescPage(
    uiState: MediaPlaybackUiState.Success,
    viewModel: MediaPlaybackViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        item(key = "desc") { MediaDescContent(media = uiState.media, episode = uiState.episode) }
        item(key = "episodes") {
            ShowPlaybackCollectionSelector(
                groups = uiState.seasons.map { PlaybackCollectionGroup(it.seasonId.toString(), it.title) },
                selectedGroupId = uiState.selectedSeasonId.toString(),
                items = uiState.browsedSeason?.episodes.orEmpty().map {
                    PlaybackCollectionItem(it.episodeId.toString(), it.displayTitle, it.badge, it.isAvailable)
                },
                playingItemId = uiState.episode?.episodeId?.toString(),
                isDescending = uiState.isDescending,
                onGroupSelected = { it.toLongOrNull()?.let(viewModel::loadSeason) },
                onItemSelected = { it.toLongOrNull()?.let(viewModel::selectEpisode) },
                onDescendingChange = viewModel::setDescending,
                isLoading = uiState.seasonLoading,
                errorMessage = uiState.seasonError,
                onRetry = { viewModel.loadSeason(uiState.selectedSeasonId) },
            )
        }
        item(key = "recommendations_title") {
            Text("相关推荐", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(4.dp))
        }
        when {
            uiState.recommendationsLoading -> item(key = "recommendations_loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            uiState.recommendationsError != null -> item(key = "recommendations_error") {
                ShowErrorContent(
                    message = uiState.recommendationsError,
                    retry = { viewModel.loadRecommendations(uiState.media.seasonId) },
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                )
            }
            uiState.recommendations.isEmpty() -> item(key = "recommendations_empty") { Text("暂无相关推荐") }
            else -> items(uiState.recommendations, key = { "recommendation_${it.seasonId}" }) { media ->
                ShowMediaCard(
                    mediaInfo = media,
                    modifier = Modifier.height(80.dp),
                    horizontal = true,
                    onClick = { openMedia(media.seasonId) },
                )
            }
        }
    }
}

@Composable
private fun MediaDescContent(media: MediaSeasonData, episode: MediaEpisode?) {
    var expanded by rememberSaveable(media.seasonId) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val coverHeight = if (media.coverUrl.isNotBlank()) (maxWidth / 2f).coerceAtMost(200.dp) else 0.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (media.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = media.coverUrl,
                            contentDescription = "${media.title}封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.height(coverHeight)
                                .aspectRatio(2f / 3f, matchHeightConstraintsFirst = true)
                                .clip(MaterialTheme.shapes.small),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f).heightIn(min = coverHeight),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(media.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            if (media.scoreText.isNotBlank() || media.progressText.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (media.scoreText.isNotBlank()) {
                                        Text(media.scoreText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                    if (media.progressText.isNotBlank()) {
                                        Text(
                                            media.progressText,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                            Text(
                                "${media.viewCount.formatCountToString()}播放 · ${media.danmakuCount.formatCountToString()}弹幕",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (episode != null) {
                            MediaActionsBar(
                                episode = episode,
                                media = media,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("简介", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 24.dp) {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "收起简介" else "展开简介",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Text(
                media.description.ifBlank { "暂无简介" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
