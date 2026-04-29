package tv.hsrui.bolo.utils

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember


@Composable
fun LazyGridState.OnGridBottomReached(
    buffer: Int = 0,
    isLoading: Boolean,
    onLoadMore: () -> Unit
) {
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems != 0 && lastVisibleItemIndex >= totalItems - 1 - buffer
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (!isLoading) {
            onLoadMore()
        }
    }
}