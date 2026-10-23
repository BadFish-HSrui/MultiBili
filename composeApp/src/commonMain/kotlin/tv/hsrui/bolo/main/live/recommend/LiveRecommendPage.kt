package tv.hsrui.bolo.main.live.recommend

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import tv.hsrui.bolo.ui.common.liveRoomsPage.LiveRoomsGridPage

@Composable
fun LiveRecommendPage(
    modifier: Modifier = Modifier,
    liveRecommendViewModel: LiveRecommendViewModel = viewModel { LiveRecommendViewModel() },
    reselectEvents: Flow<Unit>? = null,
    isSelected: Boolean = true,
) {
    LiveRoomsGridPage(
        viewModel = liveRecommendViewModel,
        modifier = modifier,
        reselectEvents = reselectEvents,
        isSelected = isSelected,
    )
}
