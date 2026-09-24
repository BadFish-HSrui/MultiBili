package tv.hsrui.bolo.main.region

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.region.Region

@Composable
fun RegionsScreen(
    modifier: Modifier = Modifier,
    reselectEvents: Flow<Unit>? = null,
) {
    val tabs = Region.entries
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    val pageReselectEvents = remember(reselectEvents, pagerState) {
        List(tabs.size) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    }
    LaunchedEffect(reselectEvents, pagerState) {
        reselectEvents?.collect {
            if (!pagerState.isScrollInProgress) {
                pageReselectEvents[pagerState.currentPage].tryEmit(Unit)
            }
        }
    }

    Column(modifier = modifier) {
        SecondaryScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, region ->
                Tab(
                    selected = (pagerState.currentPage == index),
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(region.title) },
                    modifier = Modifier.height(36.dp)
                )
            }
        }

        HorizontalPager(state = pagerState, beyondViewportPageCount = 1) { page ->
            val activeReselectEvents = pageReselectEvents[page].takeIf {
                pagerState.currentPage == page && !pagerState.isScrollInProgress
            }
            RegionFeedPage(tabs[page], reselectEvents = activeReselectEvents)
        }
    }
}