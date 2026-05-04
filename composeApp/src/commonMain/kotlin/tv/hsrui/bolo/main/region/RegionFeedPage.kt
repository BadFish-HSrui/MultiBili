package tv.hsrui.bolo.main.region

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage
import tv.hsrui.network.feature.region.Regions

@Composable
fun RegionFeedPage(
    region: Regions,
    regionFeedViewModel: RegionFeedViewModel = viewModel { RegionFeedViewModel(region) },
    modifier: Modifier = Modifier
) {
    val regionFeedUiState by regionFeedViewModel.uiState.collectAsState()
    VideosGridPage(
        uiState = regionFeedUiState,
        viewModel = regionFeedViewModel,
        modifier = modifier,
    )
}