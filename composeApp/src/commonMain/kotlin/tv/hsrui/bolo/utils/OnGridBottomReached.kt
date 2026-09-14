package tv.hsrui.bolo.utils

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember


@Composable
fun LazyGridState.OnGridBottomReached(
    buffer: Int = 8,
    isLoading: Boolean,
    staggeredGridState: LazyStaggeredGridState? = null,
    onLoadMore: () -> Unit
) {
    val shouldLoadMore by remember(this, staggeredGridState) {
        derivedStateOf {
            val totalItems = staggeredGridState?.layoutInfo?.totalItemsCount
                ?: layoutInfo.totalItemsCount
            val lastVisibleItemIndex = if (staggeredGridState != null) {
                staggeredGridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            } else {
                layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            }
            totalItems != 0 && lastVisibleItemIndex > totalItems - buffer
        }
    }

    LaunchedEffect(shouldLoadMore, this, staggeredGridState) {
        if (!isLoading && shouldLoadMore) {
            onLoadMore()
        }
    }
}