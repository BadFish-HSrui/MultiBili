package tv.hsrui.bolo.main.live

import androidx.compose.foundation.layout.Box
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
import tv.hsrui.bolo.main.live.area.LiveAreaPage
import tv.hsrui.bolo.main.live.recommend.LiveRecommendPage

@Composable
fun LiveScreen(
    modifier: Modifier = Modifier,
    reselectEvents: Flow<Unit>? = null,
) {
    val tabs = LiveTab.entries
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(LiveTab.Recommend)) { tabs.size }
    val scope = rememberCoroutineScope()
    val pageReselectEvents = remember(reselectEvents, pagerState) {
        listOf(LiveTab.Area, LiveTab.Recommend).associateWith {
            MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        }
    }
    LaunchedEffect(reselectEvents, pagerState) {
        reselectEvents?.collect {
            if (!pagerState.isScrollInProgress) {
                pageReselectEvents[tabs[pagerState.currentPage]]?.tryEmit(Unit)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage + 1) {
            Spacer(Modifier.weight(1f))
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.title) },
                    modifier = Modifier.height(36.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { tabs[it].name },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val isSelected = pagerState.currentPage == page && !pagerState.isScrollInProgress
            when (tabs[page]) {
                LiveTab.Area -> LiveAreaPage(
                    modifier = Modifier.fillMaxSize(),
                    reselectEvents = pageReselectEvents.getValue(LiveTab.Area).takeIf { isSelected },
                )
                LiveTab.Recommend -> LiveRecommendPage(
                    modifier = Modifier.fillMaxSize(),
                    reselectEvents = pageReselectEvents.getValue(LiveTab.Recommend).takeIf { isSelected },
                    isSelected = isSelected,
                )
                LiveTab.Following -> Box(Modifier.fillMaxSize())
            }
        }
    }
}
