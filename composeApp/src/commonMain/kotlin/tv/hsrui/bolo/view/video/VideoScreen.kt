package tv.hsrui.bolo.view.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.components.error.ShowErrorContent

@Composable
fun VideoScreen(
    bvid: String,
    viewModel: VideoViewModel = viewModel(key = bvid) {
        VideoViewModel(bvid)
    },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface {
        when (uiState) {
            is VideoUiState.Loading -> {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is VideoUiState.Error -> {
                ShowErrorContent(
                    message = (uiState as VideoUiState.Error).message,
                    retry = { viewModel.loadVideoInfo() }
                )
            }

            is VideoUiState.Success -> {
                key(uiState) {
                    VideoPage(
                        uiState = uiState as VideoUiState.Success
                    )
                }
            }
        }
    }
}