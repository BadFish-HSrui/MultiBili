package tv.hsrui.bolo.ui.components.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
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
import tv.hsrui.bolo.utils.getNowWindowSize

@Composable
fun <T> ShowVerticalCardGrid(
    cards: List<T>,
    keySelector: (T) -> Any,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    howToShow: @Composable (T) -> Unit
) {
    val columns: Int
    val contentPadding: Dp
    val contentSpacing: Dp

    when (getNowWindowSize()) {
        AppWindowSize.EXPANDED -> {
            columns = 4
            contentPadding = 16.dp
            contentSpacing = 12.dp
        }

        AppWindowSize.MEDIUM -> {
            columns = 3
            contentPadding = 16.dp
            contentSpacing = 8.dp
        }

        AppWindowSize.COMPACT -> {
            columns = 2
            contentPadding = 8.dp
            contentSpacing = 4.dp
        }
    }
    Box(modifier = modifier.fillMaxSize()){
        Box(modifier = Modifier.widthIn(max = 1920.dp).fillMaxSize().align(Alignment.TopCenter)) {
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
}