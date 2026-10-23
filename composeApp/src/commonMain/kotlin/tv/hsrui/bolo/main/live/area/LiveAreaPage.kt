package tv.hsrui.bolo.main.live.area

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.live.ShowLiveAreaItem
import tv.hsrui.bolo.utils.AppWindowSize
import tv.hsrui.bolo.utils.getNowWindowSize
import tv.hsrui.network.liveFeature.area.LiveParentAreaData

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveAreaPage(
    modifier: Modifier = Modifier,
    liveAreaViewModel: LiveAreaViewModel = viewModel { LiveAreaViewModel() },
    reselectEvents: Flow<Unit>? = null,
) {
    val uiState by liveAreaViewModel.uiState.collectAsState()
    val success = uiState as? LiveAreaUiState.Success
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val onRefresh: () -> Unit = {
        gridState.requestScrollToItem(0)
        liveAreaViewModel.refreshAreas()
    }

    LaunchedEffect(reselectEvents, liveAreaViewModel, gridState) {
        var scrollJob: Job? = null
        reselectEvents?.collect {
            if (scrollJob?.isActive == true) return@collect
            val current = liveAreaViewModel.uiState.value
            val currentSuccess = current as? LiveAreaUiState.Success
            val isAtTop = gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
            if (currentSuccess?.parentAreas?.isNotEmpty() == true && !isAtTop) {
                scrollJob = launch { gridState.animateScrollToItem(0) }
            } else if (current !is LiveAreaUiState.Loading && currentSuccess?.isRefreshing != true) {
                onRefresh()
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = success?.isRefreshing == true,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            val hasContent = success?.parentAreas?.isNotEmpty() == true
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().then(
                    if (!hasContent) Modifier.scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
                    else Modifier
                ),
                contentAlignment = Alignment.Center,
            ) {
                when (val state = uiState) {
                    LiveAreaUiState.Loading -> CircularProgressIndicator()
                    is LiveAreaUiState.Error -> ShowErrorContent(message = state.message, retry = onRefresh)
                    is LiveAreaUiState.Success -> if (state.parentAreas.isEmpty()) {
                        Text("暂无分区")
                    } else {
                        LiveAreaGrid(
                            parentAreas = state.parentAreas,
                            gridState = gridState,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                ShowGridFABMenu(
                    onBackToTop = { scope.launch { gridState.animateScrollToItem(0) } },
                    onRefresh = onRefresh,
                    enabled = uiState !is LiveAreaUiState.Loading && success?.isRefreshing != true,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            success?.refreshError?.let { message ->
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
                    TextButton(onClick = onRefresh) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveAreaGrid(
    parentAreas: List<LiveParentAreaData>,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val navigator: Navigator = koinInject()
    val windowSize = getNowWindowSize()
    val columns = remember(windowSize) { createLiveAreaGridCells(windowSize) }
    val contentPadding = if (windowSize == AppWindowSize.COMPACT) 8.dp else 16.dp
    val contentSpacing = if (windowSize == AppWindowSize.EXPANDED) 12.dp else 8.dp
    var expandedParentIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier.widthIn(max = 1280.dp).fillMaxSize().align(Alignment.TopCenter),
        ) {
            val density = LocalDensity.current
            val columnCount = with(columns) {
                with(density) {
                    calculateCrossAxisCellSizes(
                        availableSize = (constraints.maxWidth - contentPadding.roundToPx() * 2).coerceAtLeast(0),
                        spacing = contentSpacing.roundToPx(),
                    ).size
                }
            }
            val collapsedLimit = columnCount * 4 - 1
            LazyVerticalGrid(
                columns = columns,
                state = gridState,
                contentPadding = PaddingValues(contentPadding),
                verticalArrangement = Arrangement.spacedBy(contentSpacing),
                horizontalArrangement = Arrangement.spacedBy(contentSpacing),
                modifier = Modifier.fillMaxSize(),
            ) {
                var itemIndex = 0
                parentAreas.forEach { parent ->
                    val hasMore = parent.areas.size > collapsedLimit
                    val expanded = hasMore && parent.id in expandedParentIds
                    val visibleAreas = if (expanded) parent.areas else parent.areas.take(collapsedLimit)
                    val parentStartIndex = itemIndex
                    itemIndex += 1 + visibleAreas.size + if (hasMore) 1 else 0
                    item(
                        key = "live-parent-${parent.id}",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "parent",
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { navigator.navigateTo(BoloRoute.Live.Area(parent.id)) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = parent.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = null, modifier = Modifier.size(24.dp))
                        }
                    }
                    items(
                        items = visibleAreas,
                        key = { "live-area-${parent.id}-${it.id}" },
                        contentType = { "area" },
                    ) { area ->
                        ShowLiveAreaItem(
                            title = area.title,
                            iconUrl = area.iconUrl,
                            onClick = { navigator.navigateTo(BoloRoute.Live.Area(parent.id, area.id)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (hasMore) {
                        item(key = "live-area-toggle-${parent.id}", contentType = "area-toggle") {
                            ShowLiveAreaItem(
                                title = if (expanded) "收起" else "查看更多",
                                icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                onClick = {
                                    if (parent.id in expandedParentIds) {
                                        scope.launch {
                                            try {
                                                gridState.animateScrollToItem(parentStartIndex)
                                            } finally {
                                                expandedParentIds = expandedParentIds - parent.id
                                            }
                                        }
                                    } else {
                                        expandedParentIds = expandedParentIds + parent.id
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun createLiveAreaGridCells(windowSize: AppWindowSize): GridCells {
    val minColumns = when (windowSize) {
        AppWindowSize.EXPANDED -> 4
        AppWindowSize.MEDIUM -> 3
        AppWindowSize.COMPACT -> 2
    }
    return object : GridCells {
        override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
            val adaptiveSizes = with(GridCells.Adaptive(300.dp)) {
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
