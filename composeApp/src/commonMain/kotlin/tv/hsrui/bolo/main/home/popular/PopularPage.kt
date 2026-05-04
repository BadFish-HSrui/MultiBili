package tv.hsrui.bolo.main.home.popular

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage

@Composable
fun PopularPage(
    modifier: Modifier = Modifier,
    popularViewModel: PopularViewModel = viewModel { PopularViewModel() }
) {
    val popularUiState by popularViewModel.uiState.collectAsState()
    VideosGridPage(
        uiState = popularUiState,
        viewModel = popularViewModel,
        modifier = modifier
    )
}