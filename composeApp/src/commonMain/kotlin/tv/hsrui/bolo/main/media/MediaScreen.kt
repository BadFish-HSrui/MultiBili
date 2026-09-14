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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun MediaScreen(modifier: Modifier = Modifier) {
    val tabs = MediaTab.entries
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(MediaTab.Bangumi)) { tabs.size }
    val scope = rememberCoroutineScope()

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
            MediaPage(
                seasonType = tabs[page].seasonType,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
