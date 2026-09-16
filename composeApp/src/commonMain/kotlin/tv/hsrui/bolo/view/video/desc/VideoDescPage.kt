package tv.hsrui.bolo.view.video.desc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.utils.isMedium
import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.bolo.view.video.VideoUiState
import tv.hsrui.bolo.ui.components.player.PlaybackCollectionGroup
import tv.hsrui.bolo.ui.components.player.PlaybackCollectionItem
import tv.hsrui.bolo.ui.components.player.ShowPlaybackCollectionSelector

@Composable
fun VideoDescPage(
    videoInfo: VideoInfoData,
    collectionState: VideoUiState.Success,
    onSectionSelected: (Long) -> Unit,
    onEpisodeSelected: (String) -> Unit,
    onDescendingChange: (Boolean) -> Unit,
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
        videoInfo.collection?.takeIf { it.seasonId > 0 }?.let { collection ->
            item(key = "collection") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (collectionState.isSwitchingEpisode) LinearProgressIndicator(Modifier.fillMaxWidth())
                    collectionState.episodeError?.let { message ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(message, modifier = Modifier.weight(1f), maxLines = 2)
                            TextButton(onClick = {
                                collectionState.switchingEpisodeKey?.let(onEpisodeSelected)
                            }) { Text("重试") }
                        }
                    }
                    ShowPlaybackCollectionSelector(
                        groups = collection.sections.map { PlaybackCollectionGroup(it.sectionId.toString(), it.title) },
                        selectedGroupId = collectionState.selectedSectionId?.toString().orEmpty(),
                        items = collectionState.selectedSection?.episodes.orEmpty().distinctBy { it.key }.map {
                            PlaybackCollectionItem(id = it.key, title = it.displayTitle, enabled = it.isAvailable)
                        },
                        playingItemId = collectionState.playingEpisodeKey,
                        isDescending = collectionState.isDescending,
                        onGroupSelected = { it.toLongOrNull()?.let(onSectionSelected) },
                        onItemSelected = onEpisodeSelected,
                        onDescendingChange = onDescendingChange,
                        onRetry = { collectionState.switchingEpisodeKey?.let(onEpisodeSelected) },
                        title = collection.title,
                    )
                }
            }
        }
        item(key = "recommendations_title") {
            Text("相关推荐", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(4.dp))
        }
        relatedVideosContent(uiState, viewModel, columns = columns)
    }
}
