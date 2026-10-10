package tv.hsrui.bolo.ui.components.dialog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.model.Vid
import tv.hsrui.bolo.navigation.openMedia
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.media.ShowMediaCard
import tv.hsrui.bolo.ui.components.video.ShowVideoCard

@Composable
fun ShowVideoInfoDialog(vid: Vid, onDismissRequest: () -> Unit) {
    key(vid) {
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        val model: VideoInfoDialogViewModel = viewModel(viewModelStoreOwner = owner) {
            VideoInfoDialogViewModel(vid)
        }
        val uiState by model.uiState.collectAsStateWithLifecycle()
        LifecycleEventEffect(Lifecycle.Event.ON_STOP, onEvent = onDismissRequest)

        ShowInfoDialog(onConfirm = onDismissRequest, scrollableContent = true) {
            when (val state = uiState) {
                VideoInfoDialogUiState.Loading -> Box(
                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is VideoInfoDialogUiState.Video -> ShowVideoCard(
                    videoInfo = state.info,
                    modifier = Modifier.widthIn(max = 350.dp).fillMaxWidth(),
                )
                is VideoInfoDialogUiState.Media -> ShowMediaCard(
                    mediaInfo = state.info,
                    modifier = Modifier.widthIn(max = 275.dp).fillMaxWidth(),
                    onClick = { openMedia(seasonId = state.info.seasonId, episodeId = state.episodeId) },
                )
                is VideoInfoDialogUiState.Error -> ShowErrorContent(
                    message = state.message,
                    retry = model::load,
                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                )
            }
        }
    }
}
