package tv.hsrui.bolo.main.home.following

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage

@Composable
fun FollowingVideosPage(
    modifier: Modifier = Modifier,
    followingVideosViewModel: FollowingVideosViewModel = viewModel { FollowingVideosViewModel() },
    reselectEvents: Flow<Unit>? = null,
){
    val followingVideosUiState by followingVideosViewModel.uiState.collectAsState()
    VideosGridPage(
        uiState = followingVideosUiState,
        viewModel = followingVideosViewModel,
        reselectEvents = reselectEvents,
        modifier = modifier
    )
}