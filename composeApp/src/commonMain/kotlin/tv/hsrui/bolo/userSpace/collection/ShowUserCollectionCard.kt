package tv.hsrui.bolo.userSpace.collection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.userSpace.ShowUserVideoListCard
import tv.hsrui.network.feature.video.collection.VideoCollectionSummaryData

@Composable
fun ShowUserCollectionCard(
    collection: VideoCollectionSummaryData,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    canPlay: Boolean = true,
) {
    ShowUserVideoListCard(
        title = collection.title,
        coverUrl = collection.coverUrl,
        total = collection.total,
        coverDescription = "合集封面",
        playDescription = "播放合集第一个视频",
        onOpen = onOpen,
        onPlay = onPlay,
        modifier = modifier,
        isLoading = isLoading,
        canPlay = canPlay && collection.total > 0,
    )
}
