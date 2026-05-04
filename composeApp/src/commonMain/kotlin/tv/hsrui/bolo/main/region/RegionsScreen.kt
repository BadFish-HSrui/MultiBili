package tv.hsrui.bolo.main.region

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tv.hsrui.network.feature.region.Region

@Composable
fun RegionsScreen(modifier: Modifier = Modifier) {
    val tabs = Region.entries
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()

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
            RegionFeedPage(tabs[page])
        }
    }
}