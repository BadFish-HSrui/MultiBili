package tv.hsrui.bolo.main.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.PrimaryTabRow
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
import tv.hsrui.bolo.main.home.following.FollowingVideosPage
import tv.hsrui.bolo.main.home.popular.PopularPage
import tv.hsrui.bolo.main.home.recommend.RecommendPage

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    reselectEvents: Flow<Unit>? = null,
) {
    val tabs = HomeTab.entries
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(HomeTab.Recommend)) { tabs.size }
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

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage + 1) {
            Spacer(Modifier.weight(1F))
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = (pagerState.currentPage == index),
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.title) },
                    modifier = Modifier.height(36.dp)
                )
            }
            Spacer(Modifier.weight(1F))

        }

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1F)
                .fillMaxWidth()
        ) { page ->
            val activeReselectEvents = pageReselectEvents[page].takeIf {
                pagerState.currentPage == page && !pagerState.isScrollInProgress
            }
            when (tabs[page]) {
                HomeTab.Popular -> PopularPage(Modifier.fillMaxSize(), reselectEvents = activeReselectEvents)
                HomeTab.Recommend -> RecommendPage(Modifier.fillMaxSize(), reselectEvents = activeReselectEvents)
                HomeTab.Following -> FollowingVideosPage(Modifier.fillMaxSize(), reselectEvents = activeReselectEvents)
            }
        }
    }
}
