package tv.hsrui.bolo.main.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

@Composable
fun MediaScreen(
    modifier: Modifier = Modifier,
    reselectEvents: Flow<Unit>? = null,
) {
    val tabs = MediaTab.entries
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(MediaTab.Bangumi)) { tabs.size }
    val scope = rememberCoroutineScope()

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
        SecondaryScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(tab.title) },
                    modifier = Modifier.height(36.dp),
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            key = { tabs[it].name },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val activeReselectEvents = pageReselectEvents[page].takeIf {
                pagerState.currentPage == page && !pagerState.isScrollInProgress
            }
            MediaPage(
                seasonType = tabs[page].seasonType,
                reselectEvents = activeReselectEvents,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
