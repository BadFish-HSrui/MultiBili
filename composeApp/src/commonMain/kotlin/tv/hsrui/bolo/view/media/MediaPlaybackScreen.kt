package tv.hsrui.bolo.view.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton

@Composable
fun MediaPlaybackScreen(
    seasonId: Long,
    modifier: Modifier = Modifier,
    viewModel: MediaPlaybackViewModel = viewModel(key = "media_$seasonId") { MediaPlaybackViewModel(seasonId) },
) {
    val uiState by viewModel.uiState.collectAsState()
    Surface(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is MediaPlaybackUiState.Success -> MediaPlaybackPage(uiState = state, viewModel = viewModel)
            else -> Column(Modifier.fillMaxSize()) {
                ShowTopBarWithNavigationButton(title = {})
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (state) {
                        MediaPlaybackUiState.Loading -> CircularProgressIndicator()
                        is MediaPlaybackUiState.Error -> ShowErrorContent(
                            message = state.message,
                            retry = viewModel::loadMedia,
                        )
                        is MediaPlaybackUiState.Success -> Unit
                    }
                }
            }
        }
    }
}
