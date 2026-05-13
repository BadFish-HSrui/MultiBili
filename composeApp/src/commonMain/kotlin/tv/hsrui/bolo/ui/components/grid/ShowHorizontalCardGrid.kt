package tv.hsrui.bolo.ui.components.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.utils.AppWindowSize
import tv.hsrui.bolo.utils.getNowWindowSize

@Composable
fun <T> ShowHorizontalCardGrid(
    cards: List<T>,
    keySelector: (T) -> Any,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    howToShow: @Composable (T) -> Unit
) {
    val windowSize = getNowWindowSize()

    val columns: Int
    val contentPadding: Dp
    val contentSpacing: Dp

    when (windowSize) {
        AppWindowSize.EXPANDED -> {
            columns = 2
            contentPadding = 16.dp
            contentSpacing = 12.dp
        }

        AppWindowSize.MEDIUM -> {
            columns = 1
            contentPadding = 16.dp
            contentSpacing = 8.dp
        }

        AppWindowSize.COMPACT -> {
            columns = 1
            contentPadding = 8.dp
            contentSpacing = 8.dp
        }
    }
    Box(modifier = modifier.widthIn(max = 1280.dp)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            content = {
                items(
                    items = cards,
                    key = keySelector
                ) { card ->
                    howToShow(card)
                }
            },
            contentPadding = PaddingValues(contentPadding),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            horizontalArrangement = Arrangement.spacedBy(contentSpacing),
            state = gridState,
            modifier = Modifier
        )
    }
}