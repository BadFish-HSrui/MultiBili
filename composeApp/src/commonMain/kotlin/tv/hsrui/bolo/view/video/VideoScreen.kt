package tv.hsrui.bolo.view.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import tv.hsrui.bolo.model.Vid
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton

@Composable
fun  VideoScreen(
    vid: Vid,
    viewModel: VideoViewModel = viewModel(key = vid.key) {
        VideoViewModel(vid =  vid)
    },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is VideoUiState.Success -> {
                key(uiState) {
                    VideoPage(uiState = state)
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                ShowTopBarWithNavigationButton(title = {})
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (state) {
                        is VideoUiState.Loading -> CircularProgressIndicator()
                        is VideoUiState.Error -> ShowErrorContent(
                            message = state.message,
                            retry = { viewModel.loadVideoInfo() },
                        )
                        is VideoUiState.Success -> Unit
                    }
                }
            }
        }
    }
}