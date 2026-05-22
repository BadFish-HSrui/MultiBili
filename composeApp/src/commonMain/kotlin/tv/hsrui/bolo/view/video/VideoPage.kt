package tv.hsrui.bolo.view.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.launch
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.bolo.view.video.desc.VideoDescPage

private enum class VideoInfoTab(val title: String) {
    Desc("简介"),
    Reply("评论")
}

@Composable
fun VideoPage(
    uiState: VideoUiState.Success,
    modifier: Modifier = Modifier
) {
    val videoInfo = uiState.video

    val videoPlayer = remember {
        movableContentOf {
            Box(modifier = Modifier.aspectRatio(uiState.video.dimension.width.toFloat() / uiState.video.dimension.height.toFloat()))
        }
    }
    val videoInfoBar = remember {
        movableContentOf {
            val tabs = VideoInfoTab.entries
            val pagerState = rememberPagerState { tabs.size }
            val coroutineScope = rememberCoroutineScope()

            Column {
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier.padding(start = 32.dp).width(128.dp),
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = (pagerState.currentPage == index),
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        index
                                    )
                                }
                            },
                            text = { Text(tab.title) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }

                HorizontalDivider()

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (tabs[page]) {
                        VideoInfoTab.Desc -> {
                            VideoDescPage(
                                videoInfo = videoInfo,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        VideoInfoTab.Reply -> {
                            /*TODO*/
                        }
                    }
                }
            }
        }
    }

    if (isExpanded()) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val infoWidth = min((maxWidth * 0.3F), 360.dp)
            Row {
                Box(
                    modifier = Modifier.weight(1F).fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) { videoPlayer() }
                Box(Modifier.width(infoWidth)) { videoInfoBar() }
            }
        }
    } else {
        Column(modifier) {
            videoPlayer()
            videoInfoBar()
        }
    }
}