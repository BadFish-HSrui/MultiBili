package tv.hsrui.bolo.view.video.desc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.utils.isMedium
import tv.hsrui.network.feature.video.VideoInfoData

@Composable
fun VideoDescPage(
    videoInfo: VideoInfoData,
    viewModel: RelatedViewModel = viewModel(key = "desc_${videoInfo.bvid}") { RelatedViewModel(videoInfo.avid) },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val columns = if (isMedium()) 2 else 1
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (columns == 2) modifier.fillMaxWidth() else modifier.widthIn(max = 512.dp)
    ) {
        item(key = "desc") { VideoDescContent(videoInfo) }
        item(key = "recommendations_title") {
            Text("相关推荐", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(4.dp))
        }
        relatedVideosContent(uiState, viewModel, columns = columns)
    }
}
