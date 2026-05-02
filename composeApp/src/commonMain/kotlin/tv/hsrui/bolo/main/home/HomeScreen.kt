package tv.hsrui.bolo.main.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.main.home.popular.PopularPage
import tv.hsrui.bolo.main.home.recommend.RecommendPage

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val tabs = HomeTab.entries
    val pagerState = rememberPagerState { tabs.size }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1
        ) { page ->
            when(tabs[page]) {
                HomeTab.Popular -> PopularPage()
                HomeTab.Recommend -> RecommendPage()
            }
        }
    }
}