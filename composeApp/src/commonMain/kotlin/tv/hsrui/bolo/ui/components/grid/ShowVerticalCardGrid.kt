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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import tv.hsrui.bolo.utils.AppWindowSize
import tv.hsrui.bolo.utils.getNowWindowSize

@Composable
fun <T> ShowVerticalCardGrid(
    cards: List<T>,
    keySelector: (T) -> Any,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    gridCells: GridCells? = null,
    howToShow: @Composable (T) -> Unit
) {
    val minColumns: Int
    val contentPadding: Dp
    val contentSpacing: Dp

    when (getNowWindowSize()) {
        AppWindowSize.EXPANDED -> {
            minColumns = 4
            contentPadding = 16.dp
            contentSpacing = 12.dp
        }

        AppWindowSize.MEDIUM -> {
            minColumns = 3
            contentPadding = 16.dp
            contentSpacing = 8.dp
        }

        AppWindowSize.COMPACT -> {
            minColumns = 2
            contentPadding = 8.dp
            contentSpacing = 4.dp
        }
    }
    val defaultGridCells = remember(minColumns) {
        object : GridCells {
            override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
                val adaptiveSizes = with(GridCells.Adaptive(320.dp)) {
                    calculateCrossAxisCellSizes(availableSize, spacing)
                }
                val maxColumns = if (minColumns == 4) 6 else Int.MAX_VALUE
                val columns = adaptiveSizes.size.coerceIn(minColumns, maxColumns)
                // 宽度不足时优先满足布局列数下限，卡片均分实际可用宽度。
                return if (columns == adaptiveSizes.size) adaptiveSizes else with(GridCells.Fixed(columns)) {
                    calculateCrossAxisCellSizes(availableSize, spacing)
                }
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()){
        Box(modifier = Modifier.widthIn(max = 1920.dp).fillMaxSize().align(Alignment.TopCenter)) {
            LazyVerticalGrid(
                columns = gridCells ?: defaultGridCells,
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
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
