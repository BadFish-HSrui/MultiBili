package tv.hsrui.bolo.main.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import tv.hsrui.bolo.navigation.openMedia
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowVerticalCardGrid
import tv.hsrui.bolo.ui.components.media.ShowMediaCard
import tv.hsrui.bolo.utils.OnGridBottomReached
import tv.hsrui.bolo.utils.isMedium

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaPage(
    seasonType: Int,
    modifier: Modifier = Modifier,
    mediaViewModel: MediaViewModel = viewModel(key = "media:$seasonType") {
        MediaViewModel(seasonType = seasonType)
    },
) {
    val uiState by mediaViewModel.uiState.collectAsState()
    val filterUiState by mediaViewModel.filterUiState.collectAsState()
    val appliedFilters by mediaViewModel.appliedFilters.collectAsState()
    var showFilterDialog by rememberSaveable(seasonType) { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val minColumns = if (isMedium()) 3 else 2
    val mediaGridCells = remember(minColumns) {
        object : GridCells {
            override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
                val adaptiveSizes = with(GridCells.Adaptive(200.dp)) {
                    calculateCrossAxisCellSizes(availableSize, spacing)
                }
                // 空间不足时优先满足布局列数下限，卡片均分实际可用宽度。
                return if (adaptiveSizes.size >= minColumns) adaptiveSizes else with(GridCells.Fixed(minColumns)) {
                    calculateCrossAxisCellSizes(availableSize, spacing)
                }
            }
        }
    }
    val scope = rememberCoroutineScope()
    val success = uiState as? MediaUiState.Success

    LaunchedEffect(showFilterDialog) {
        if (showFilterDialog) mediaViewModel.loadMediaConditions()
    }

    // 新一批内容到达后重新检查，兼容大窗口仍处于触底区域的情况。
    key(success?.media?.size) {
        gridState.OnGridBottomReached(
            buffer = 8,
            isLoading = success == null || success.isLoadingMore || success.isRefreshing ||
                success.loadMoreError != null || !success.hasMore,
        ) {
            mediaViewModel.loadMoreMedia()
        }
    }

    PullToRefreshBox(
        isRefreshing = success?.isRefreshing == true,
        onRefresh = mediaViewModel::refreshMedia,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val state = uiState) {
                    MediaUiState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    is MediaUiState.Error -> ShowErrorContent(
                        message = state.message,
                        retry = mediaViewModel::refreshMedia,
                    )
                    is MediaUiState.Success -> if (state.media.isEmpty()) {
                        val title = MediaTab.entries.firstOrNull { it.seasonType == seasonType }?.title ?: "内容"
                        Text("暂无$title", modifier = Modifier.align(Alignment.Center))
                    } else {
                        ShowVerticalCardGrid(
                            cards = state.media,
                            keySelector = { it.seasonId },
                            gridState = gridState,
                            modifier = Modifier.fillMaxSize(),
                            gridCells = mediaGridCells,
                        ) { media ->
                            ShowMediaCard(
                                mediaInfo = media,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { openMedia(media.seasonId) },
                            )
                        }
                    }
                }
                ShowGridFABMenu(
                    onBackToTop = { scope.launch { gridState.animateScrollToItem(0) } },
                    onRefresh = mediaViewModel::refreshMedia,
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = { showFilterDialog = true },
                        text = { Text("筛选条件") },
                        icon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
                    )
                }
            }
            if (success?.isLoadingMore == true) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            success?.loadMoreError?.let { message ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = mediaViewModel::loadMoreMedia) {
                        Text("重试")
                    }
                }
            }
        }
    }

    if (showFilterDialog) {
        ShowMediaFilterDialog(
            uiState = filterUiState,
            selection = appliedFilters,
            onCancel = { showFilterDialog = false },
            onConfirm = { selection ->
                showFilterDialog = false
                mediaViewModel.applyMediaFilters(selection)
                gridState.requestScrollToItem(0)
            },
            onRetry = mediaViewModel::loadMediaConditions,
        )
    }
}
