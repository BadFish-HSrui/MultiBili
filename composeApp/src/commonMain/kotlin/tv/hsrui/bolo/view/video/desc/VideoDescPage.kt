package tv.hsrui.bolo.view.video.desc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.network.feature.video.VideoInfoData

@Composable
fun VideoDescPage(
    videoInfo: VideoInfoData,
    viewModel: RelatedViewModel = viewModel(key = videoInfo.bvid) { RelatedViewModel(videoInfo.avid) },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.widthIn(max = 512.dp)
    ) {
        item(key = "desc") { VideoDescContent(videoInfo) }
        relatedVideosContent(uiState, viewModel)
    }
}