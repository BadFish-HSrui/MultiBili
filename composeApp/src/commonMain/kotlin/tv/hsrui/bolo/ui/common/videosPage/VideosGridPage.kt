package tv.hsrui.bolo.ui.common.videosPage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.ui.components.ShowVerticalVideoGrid

@Composable
fun VideosGridPage(
    uiState: VideosUiState,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is VideosUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is VideosUiState.Error -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.message)
            }
        }

        is VideosUiState.Success -> {
            ShowVerticalVideoGrid(
                uiState.videos,
                modifier = modifier
            )
        }

    }
}
