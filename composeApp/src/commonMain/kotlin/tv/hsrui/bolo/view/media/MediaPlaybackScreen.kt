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
import androidx.compose.runtime.remember
import tv.hsrui.bolo.player.session.BoloPlaybackSession
import tv.hsrui.bolo.ui.common.player.PlayerStatusBarOverlay
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton

@Composable
fun MediaPlaybackScreen(
    seasonId: Long = 0,
    modifier: Modifier = Modifier,
    episodeId: Long = 0,
    viewModel: MediaPlaybackViewModel = remember(seasonId, episodeId) {
        BoloPlaybackSession.obtain("media_${seasonId}_$episodeId").getViewModel("media_${seasonId}_$episodeId") {
            MediaPlaybackViewModel(seasonId, episodeId)
        }
    },
) {
    val uiState by viewModel.uiState.collectAsState()
    Box(modifier.fillMaxSize()) {
        Surface(modifier = Modifier.fillMaxSize()) {
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
        PlayerStatusBarOverlay()
    }
}
