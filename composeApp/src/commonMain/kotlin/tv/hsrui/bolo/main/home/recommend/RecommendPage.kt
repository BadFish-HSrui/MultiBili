package tv.hsrui.bolo.main.home.recommend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage

@Composable
fun RecommendPage(
    modifier: Modifier = Modifier,
    recommendViewModel: RecommendViewModel = viewModel { RecommendViewModel() }
) {
    val recommendUiState by recommendViewModel.uiState.collectAsState()
    VideosGridPage(
        uiState = recommendUiState,
        viewModel = recommendViewModel,
        modifier = modifier
    )
}