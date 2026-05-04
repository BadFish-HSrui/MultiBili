package tv.hsrui.bolo.main.region

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage
import tv.hsrui.network.feature.region.Region

@Composable
fun RegionFeedPage(
    region: Region,
    regionFeedViewModel: RegionFeedViewModel = viewModel(key = region.name) { RegionFeedViewModel(region) },
    modifier: Modifier = Modifier
) {
    val regionFeedUiState by regionFeedViewModel.uiState.collectAsState()
    VideosGridPage(
        uiState = regionFeedUiState,
        viewModel = regionFeedViewModel,
        modifier = modifier,
    )
}