package tv.hsrui.bolo.view.video.desc

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.ui.common.videosPage.VideosUiState
import tv.hsrui.bolo.ui.components.dropdownMenu.items.WatchLaterMenuItem
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.video.ShowVerticalVideoCard

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.relatedVideosContent(
    uiState: VideosUiState,
    viewModel: RelatedViewModel,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        is VideosUiState.Loading -> item {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        is VideosUiState.Error -> item {
            ShowErrorContent(
                message = uiState.message,
                retry = { viewModel.loadVideos() }
            )
        }

        is VideosUiState.Success -> items(items = uiState.videos, key = { it.avid }) { videoCard ->
            ShowVerticalVideoCard(videoCard, Modifier.height(80.dp)) { onDismiss ->
                WatchLaterMenuItem(avid = videoCard.avid, onDismiss = onDismiss)
            }
        }
    }
}