package tv.hsrui.bolo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import tv.hsrui.network.model.VideoCard

@Composable
fun ShowVerticalVideoGrid(videos: List<VideoCard>,modifier: Modifier = Modifier) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass

    val columns: Int
    val isWideCard: Boolean

    when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> {
            columns = 4
            isWideCard = true
        }

        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> {
            columns = 3
            isWideCard = true
        }

        else -> {
            columns = 2
            isWideCard = false
        }
    }
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    )
}