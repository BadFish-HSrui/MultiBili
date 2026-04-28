package tv.hsrui.bolo.home.popular

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage
import tv.hsrui.bolo.ui.common.videosPage.VideosUiState
import tv.hsrui.bolo.ui.components.ShowVerticalVideoGrid

@Composable
fun PopularPage(
    popularViewModel: PopularViewModel = viewModel { PopularViewModel() },
    modifier: Modifier = Modifier
) {
    val popularUiState by popularViewModel.uiState.collectAsState()
    VideosGridPage(
        uiState = popularUiState,
        modifier = modifier
    )
}