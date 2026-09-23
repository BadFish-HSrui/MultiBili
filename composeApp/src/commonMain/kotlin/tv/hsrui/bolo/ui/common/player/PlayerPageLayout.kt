package tv.hsrui.bolo.ui.common.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import tv.hsrui.bolo.player.PlayerFullscreenState
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.utils.formatCountToString
import kotlin.math.roundToInt

private enum class PlayerInfoTab(val title: String) {
    Desc("简介"),
    Reply("评论")
}

private fun Modifier.playerBounds(
    bounds: IntRect
): Modifier = this
    .offset { bounds.topLeft }
    .layout { measurable, _ ->
        val width = bounds.width.coerceAtLeast(1)
        val height = bounds.height.coerceAtLeast(1)
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(width, height) {
            placeable.place(0, 0)
        }
    }

@Composable
fun PlayerPageLayout(
    fullscreenState: PlayerFullscreenState,
    descContent: @Composable () -> Unit,
    replyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    replyCount: Long? = null,
    videoAspectRatio: Float? = null,
    playerContent: @Composable () -> Unit,
) {
    val currentDescContent by rememberUpdatedState(descContent)
    val currentReplyContent by rememberUpdatedState(replyContent)
    val currentReplyCount by rememberUpdatedState(replyCount)
    val isFullscreen = fullscreenState.isFullscreen
    var rootOffsetInRoot by remember { mutableStateOf(IntOffset.Zero) }
    var playerBoundsInRoot by remember { mutableStateOf(IntRect.Zero) }

    val videoInfoBar = remember {
        movableContentOf {
            val tabs = PlayerInfoTab.entries
            val pagerState = rememberPagerState { tabs.size }
            val coroutineScope = rememberCoroutineScope()

            Column {
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.widthIn(max = 224.dp),
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = (pagerState.currentPage == index),
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            text = {
                                Text(tab.title + currentReplyCount?.takeIf { tab == PlayerInfoTab.Reply }
                                    ?.let { "(${it.formatCountToString()})" }.orEmpty())
                            },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                HorizontalDivider()

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1
                ) { page ->
                    when (tabs[page]) {
                        PlayerInfoTab.Desc -> currentDescContent()
                        PlayerInfoTab.Reply -> currentReplyContent()
                    }
                }
            }
        }
    }

    val playerBoundsTracker = Modifier
        .background(Color.Black)
        .onGloballyPositioned { coordinates ->
            val position = coordinates.positionInRoot()
            playerBoundsInRoot = IntRect(
                offset = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
                size = coordinates.size
            )
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInRoot()
                rootOffsetInRoot = IntOffset(position.x.roundToInt(), position.y.roundToInt())
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
        ) {
            if (isExpanded()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val infoWidth = (maxWidth * 0.3F).coerceIn(300.dp, 360.dp)
                    Row(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1F)
                                .fillMaxHeight()
                                .then(playerBoundsTracker)
                        )
                        Box(
                            Modifier
                                .width(infoWidth)
                                .fillMaxHeight()
                        ) { videoInfoBar() }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                videoAspectRatio?.takeIf { it.isFinite() && it > 0F }
                                    ?.coerceIn(4F / 3F, 16F / 9F) ?: (4F / 3F)
                            )
                            .then(playerBoundsTracker)
                    )
                    Box(
                        Modifier
                            .weight(1F)
                            .fillMaxWidth()
                    ) { videoInfoBar() }
                }
            }
        }

        if (isFullscreen || !playerBoundsInRoot.isEmpty) {
            val playerBoundsInPage = playerBoundsInRoot.translate(
                translateX = -rootOffsetInRoot.x,
                translateY = -rootOffsetInRoot.y
            )
            val playerModifier = if (isFullscreen) {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(2f)
            } else {
                Modifier
                    .playerBounds(playerBoundsInPage)
                    .zIndex(1f)
            }

            Box(modifier = playerModifier) {
                playerContent()
            }
        }
    }
}
