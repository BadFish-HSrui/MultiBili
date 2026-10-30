package tv.hsrui.bolo.search

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tv.hsrui.network.utils.formatToDateTime
import tv.hsrui.network.utils.formatToDurationText
import androidx.compose.ui.draw.alpha
import tv.hsrui.bolo.navigation.openLive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.navigation.openMedia
import tv.hsrui.bolo.navigation.openUserSpace
import tv.hsrui.bolo.ui.components.dropdownMenu.items.WatchLaterMenuItem
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.grid.ShowVerticalCardGrid
import tv.hsrui.bolo.ui.components.media.ShowMediaCard
import tv.hsrui.bolo.ui.components.user.ShowUserInfoLayout
import tv.hsrui.bolo.ui.components.video.ShowVideoCard
import tv.hsrui.bolo.utils.isMedium
import tv.hsrui.network.feature.search.SearchLiveType
import tv.hsrui.bolo.ui.components.live.ShowLiveCard
import tv.hsrui.network.feature.search.SearchCategory
import tv.hsrui.network.feature.search.SearchUserOrder
import tv.hsrui.network.feature.search.SearchVideoOrder
import tv.hsrui.network.utils.formatCountToString

@Composable
fun SearchResultsPage(
    keyword: String,
    category: SearchCategory,
    modifier: Modifier = Modifier,
    viewModel: SearchResultsViewModel = viewModel(key = "search:${category.name}:$keyword") {
        SearchResultsViewModel(keyword = keyword, category = category)
    },
) {
    val uiState by viewModel.uiState.collectAsState()
    val videoOrder by viewModel.videoOrder.collectAsState()
    val liveType by viewModel.liveType.collectAsState()
    val userOrder by viewModel.userOrder.collectAsState()
    val success = uiState as? SearchResultsUiState.Success
    val gridState = rememberLazyGridState()
    val minColumns = if (isMedium()) 3 else 2
    val mediaGridCells = remember(minColumns) {
        object : GridCells {
            override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
                val adaptiveSizes = with(GridCells.Adaptive(200.dp)) {
                    calculateCrossAxisCellSizes(availableSize, spacing)
                }
                return if (adaptiveSizes.size >= minColumns) adaptiveSizes else with(GridCells.Fixed(minColumns)) {
                    calculateCrossAxisCellSizes(availableSize, spacing)
                }
            }
        }
    }
    val nearBottom by remember(gridState) {
        derivedStateOf {
            val layout = gridState.layoutInfo
            layout.totalItemsCount > 0 &&
                (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 8
        }
    }

    // 每一页完成后重新判断，兼容大窗口、重复条目和过滤后为空的结果页。
    LaunchedEffect(nearBottom, success?.page, success?.isRefreshing) {
        val state = success ?: return@LaunchedEffect
        if ((nearBottom || !state.hasItems) && state.hasMore && !state.isRefreshing &&
            !state.isLoadingMore && state.loadMoreError == null
        ) {
            viewModel.loadMoreResults()
        }
    }

    Column(modifier.fillMaxSize()) {
        if (category == SearchCategory.Video || category == SearchCategory.User || category == SearchCategory.Live) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(32.dp).horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (category == SearchCategory.Video) SearchVideoOrder.entries.forEach { order ->
                        FilterChip(
                            selected = videoOrder == order,
                            onClick = {
                                if (videoOrder != order) {
                                    viewModel.applyVideoOrder(order)
                                    gridState.requestScrollToItem(0)
                                }
                            },
                            label = { Text(order.title, style = MaterialTheme.typography.labelMedium) },
                        )
                    } else if (category == SearchCategory.User) SearchUserOrder.entries.forEach { order ->
                        FilterChip(
                            selected = userOrder == order,
                            onClick = {
                                if (userOrder != order) {
                                    viewModel.applyUserOrder(order)
                                    gridState.requestScrollToItem(0)
                                }
                            },
                            label = { Text(order.title, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                    if (category == SearchCategory.Live) SearchLiveType.entries.forEach { type ->
                        FilterChip(
                            selected = liveType == type,
                            onClick = {
                                if (liveType != type) {
                                    viewModel.applyLiveType(type)
                                    gridState.requestScrollToItem(0)
                                }
                            },
                            label = { Text(type.title, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        PullToRefreshBox(
            isRefreshing = success?.isRefreshing == true,
            onRefresh = viewModel::refreshResults,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxSize()) {
                val hasItems = success?.hasItems == true
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().then(
                        if (!hasItems) Modifier.scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
                        else Modifier
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    when (val state = uiState) {
                        SearchResultsUiState.Loading -> CircularProgressIndicator()
                        is SearchResultsUiState.Error -> ShowErrorContent(
                            message = state.message,
                            retry = viewModel::refreshResults,
                        )
                        is SearchResultsUiState.Success -> when {
                            !hasItems -> Text("未找到相关${if (category == SearchCategory.Live) liveType.resultTitle else category.title}")
                            category == SearchCategory.Video -> ShowVerticalCardGrid(
                                cards = state.videos,
                                keySelector = { it.avid },
                                gridState = gridState,
                            ) { video ->
                                ShowVideoCard(videoInfo = video, modifier = Modifier.fillMaxSize()) { onDismiss ->
                                    WatchLaterMenuItem(avid = video.avid, onDismiss = onDismiss)
                                }
                            }
                            category == SearchCategory.Live && liveType.apiValue == SearchLiveType.AnchorRelated.apiValue -> ShowHorizontalCardGrid(
                                cards = state.anchors,
                                keySelector = { it.mid },
                                gridState = gridState,
                            ) { anchor ->
                                ShowUserInfoLayout(
                                    face = anchor.avatarUrl,
                                    name = anchor.name,
                                    sign = "",
                                    level = 0,
                                    levelString = "",
                                    isVip = false,
                                    vipTypeString = "",
                                    shape = CardDefaults.shape,
                                    showLevel = false,
                                    showStatistics = false,
                                    isLive = anchor.isLive,
                                    onClick = { openUserSpace(anchor.mid) },
                                    onUserClick = { openUserSpace(anchor.mid) },
                                    onAvatarClick = {
                                        if (anchor.isLive && anchor.roomId > 0) openLive(anchor.roomId)
                                        else openUserSpace(anchor.mid)
                                    },
                                    descriptionContent = {
                                        Text(
                                            text = listOfNotNull(
                                                anchor.areaName.takeIf { it.isNotBlank() },
                                                anchor.followerCount?.let { "${it.formatCountToString()}人关注" },
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            minLines = 1,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.alpha(0.75F),
                                        )
                                        Text(
                                            text = if (anchor.isLive) formatLiveStartText(anchor.liveStartTime, Clock.System.now()) else "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            minLines = 1,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.alpha(0.75F),
                                        )
                                    },
                                ) {}
                            }
                            category == SearchCategory.Live -> ShowVerticalCardGrid(
                                cards = state.rooms,
                                keySelector = { it.roomId },
                                gridState = gridState,
                            ) { room ->
                                ShowLiveCard(liveInfo = room, modifier = Modifier.fillMaxWidth())
                            }
                            category == SearchCategory.User -> ShowHorizontalCardGrid(
                                cards = state.users,
                                keySelector = { it.mid },
                                gridState = gridState,
                            ) { user ->
                                ShowUserInfoLayout(
                                    face = user.avatarUrl,
                                    name = user.name,
                                    sign = user.sign,
                                    level = user.level,
                                    levelString = user.levelString,
                                    isVip = false,
                                    vipTypeString = "",
                                    shape = CardDefaults.shape,
                                    signMinLines = 2,
                                    onClick = { openUserSpace(user.mid) },
                                    isLive = user.isLive,
                                    onAvatarClick = {
                                        val roomId = user.liveRoomId
                                        if (user.isLive && roomId != null) openLive(roomId)
                                        else openUserSpace(user.mid)
                                    },
                                ) {
                                    Text(
                                        text = "粉丝数: ${user.followerCount?.formatCountToString() ?: "--"}",
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "投稿数: ${user.videoCount?.formatCountToString() ?: "--"}",
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            else -> ShowVerticalCardGrid(
                                cards = state.media,
                                keySelector = { it.seasonId },
                                gridState = gridState,
                                gridCells = mediaGridCells,
                            ) { media ->
                                ShowMediaCard(
                                    mediaInfo = media,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { openMedia(media.seasonId) },
                                )
                            }
                        }
                    }
                }
                if (success?.isLoadingMore == true) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
                success?.loadMoreError?.let { message ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = message,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::loadMoreResults) { Text("重试") }
                    }
                }
            }
        }
    }
}


private fun formatLiveStartText(startTime: Long?, currentTime: Instant): String {
    if (startTime == null || startTime <= 0) return ""
    return try {
        val start = Instant.fromEpochSeconds(startTime)
        if (start > currentTime) return ""
        val elapsed = currentTime - start
        when {
            elapsed < 1.minutes -> "刚刚开播"
            elapsed < 14.days -> "${elapsed.inWholeSeconds.formatToDurationText()}前开播"
            else -> {
                val zone = TimeZone.currentSystemDefault()
                startTime.formatToDateTime(
                    year = start.toLocalDateTime(zone).year != currentTime.toLocalDateTime(zone).year,
                    hourMinute = false,
                ) + "开播"
            }
        }
    } catch (_: IllegalArgumentException) {
        ""
    }
}