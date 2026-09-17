package tv.hsrui.bolo.userSpace.series

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tv.hsrui.bolo.navigation.openUserSeries
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.userSpace.ShowUserVideoListCard
import tv.hsrui.bolo.userSpace.UserSpaceSectionState
import tv.hsrui.bolo.userSpace.UserSpaceUiState
import tv.hsrui.bolo.utils.OnGridBottomReached

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserSeriesContent(
    mid: Long,
    state: UserSpaceUiState,
    gridState: LazyGridState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val cannotLoad = !isActive || state.isRefreshing || state.isLoadingMoreSeries ||
        !state.canLoadMoreSeries || state.seriesLoadMoreError != null
    gridState.OnGridBottomReached(buffer = 4, isLoading = cannotLoad, onLoadMore = onLoadMore)
    LaunchedEffect(cannotLoad, state.seriesPage) {
        val layout = gridState.layoutInfo
        if (!cannotLoad && layout.totalItemsCount > 0 && (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 4) {
            onLoadMore()
        }
    }
    Box(modifier.fillMaxSize()) {
        when (val section = state.series) {
            is UserSpaceSectionState.Success -> {
                ShowHorizontalCardGrid(
                    cards = section.data,
                    keySelector = { it.seriesId },
                    gridState = gridState,
                    bottomContent = {
                        if (state.isLoadingMoreSeries) CircularProgressIndicator(Modifier.padding(12.dp))
                        state.seriesLoadMoreError?.let { message ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                                Text(message, modifier = Modifier.weight(1f), maxLines = 2)
                                TextButton(onClick = onLoadMore, enabled = !state.isLoadingMoreSeries) { Text("重试") }
                            }
                        }
                    },
                ) { series ->
                    ShowUserVideoListCard(
                        title = series.title,
                        coverUrl = series.coverUrl,
                        total = series.total,
                        coverDescription = "系列封面",
                        playDescription = "开始播放系列",
                        onOpen = { openUserSeries(series.mid.takeIf { it > 0 } ?: mid, series.seriesId) },
                        onPlay = {},
                    )
                }
                ShowGridFABMenu(
                    onBackToTop = { scope.launch { gridState.animateScrollToItem(0) } },
                    onRefresh = onRefresh,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            else -> Box(
                modifier = Modifier.fillMaxSize().scrollable(rememberScrollableState { 0f }, Orientation.Vertical),
                contentAlignment = Alignment.Center,
            ) {
                if (section is UserSpaceSectionState.Error) ShowErrorContent(message = section.message, retry = onRefresh)
                else CircularProgressIndicator()
            }
        }
    }
}
