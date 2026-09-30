package tv.hsrui.bolo.ui.components.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    noContentPadding: Boolean = false,
    noContentSpacing: Boolean = false,
    topContent: @Composable (() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
    staggeredGridState: LazyStaggeredGridState? = null,
    columns: GridCells = GridCells.Adaptive(325.dp),
    howToShow: @Composable (T) -> Unit
) {
    val contentPadding: Dp
    val contentSpacing: Dp

    when (getNowWindowSize()) {
        AppWindowSize.EXPANDED -> {
            contentPadding = 16.dp
            contentSpacing = 12.dp
        }

        AppWindowSize.MEDIUM -> {
            contentPadding = 16.dp
            contentSpacing = 8.dp
        }

        AppWindowSize.COMPACT -> {
            contentPadding = 8.dp
            contentSpacing = 8.dp
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.widthIn(max = 1280.dp).fillMaxSize().align(Alignment.TopCenter)) {
            if (staggeredGridState != null) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(325.dp),
                    contentPadding = PaddingValues(if (noContentPadding) 0.dp else contentPadding),
                    verticalItemSpacing = if (noContentSpacing) 0.dp else contentSpacing,
                    horizontalArrangement = Arrangement.spacedBy(if (noContentSpacing) 0.dp else contentSpacing),
                    state = staggeredGridState
                ) {
                    if (topContent != null) {
                        item(key = "top") {
                            topContent()
                        }
                    }

                    items(items = cards, key = keySelector) { card ->
                        howToShow(card)
                    }

                    if (bottomContent != null) {
                        item(key = "bottom", span = StaggeredGridItemSpan.FullLine) {
                            bottomContent()
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = columns,
                    contentPadding = PaddingValues(if (noContentPadding) 0.dp else contentPadding),
                    verticalArrangement = Arrangement.spacedBy(if (noContentSpacing) 0.dp else contentSpacing),
                    horizontalArrangement = Arrangement.spacedBy(if (noContentSpacing) 0.dp else contentSpacing),
                    state = gridState,
                    modifier = Modifier
                ) {
                    if (topContent != null){
                        item(key = "top") {
                            topContent()
                        }
                    }

                    items(
                        items = cards,
                        key = keySelector
                    ) { card ->
                        howToShow(card)
                    }

                    if (bottomContent != null) {
                        item(key = "bottom", span = { GridItemSpan(maxLineSpan) }) {
                            bottomContent()
                        }
                    }
                }
            }
        }
    }
}