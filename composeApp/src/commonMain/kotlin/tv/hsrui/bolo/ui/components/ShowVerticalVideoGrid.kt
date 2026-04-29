package tv.hsrui.bolo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.utils.AppWindowSize
import tv.hsrui.bolo.utils.ShowGridScrollToTopButton
import tv.hsrui.bolo.utils.getNowWindowSize
import tv.hsrui.network.model.VideoCard

@Composable
fun ShowVerticalVideoGrid(
    videos: List<VideoCard>,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    needShowScrollToTopButton: Boolean = false
) {
    val windowSize = getNowWindowSize()

    val columns: Int
    val isWideCard: Boolean
    val contentSpacing: Dp

    when (windowSize) {
        AppWindowSize.EXPANDED -> {
            columns = 4
            isWideCard = true
            contentSpacing = 12.dp
        }

        AppWindowSize.MEDIUM -> {
            columns = 3
            isWideCard = true
            contentSpacing = 8.dp
        }

        AppWindowSize.COMPACT -> {
            columns = 2
            isWideCard = false
            contentSpacing = 4.dp
        }
    }
    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            content = {
                items(
                    items = videos,
                    key = { it.avid }
                ) { video ->
                    ShowVideoCard(
                        video,
                        isWide = isWideCard
                    )
                }
            },
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            horizontalArrangement = Arrangement.spacedBy(contentSpacing),
            state = gridState,
            modifier = Modifier
        )
        if (needShowScrollToTopButton) {
            gridState.ShowGridScrollToTopButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(48.dp)
            )
        }
    }
}