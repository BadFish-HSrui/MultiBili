package tv.hsrui.bolo.main.live.area

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.common.liveRoomsPage.LiveRoomsGridPage
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton

@Composable
fun LiveAreaRoomsScreen(
    parentAreaId: Int,
    areaId: Int? = null,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val tabsViewModel = viewModel(key = "LiveAreaTabs:$parentAreaId") { LiveAreaTabsViewModel(parentAreaId) }
    val state by tabsViewModel.uiState.collectAsStateWithLifecycle()
    var selectedAreaId by rememberSaveable(parentAreaId, areaId) { mutableIntStateOf(areaId ?: 0) }
    val success = state as? LiveAreaTabsUiState.Success
    val selectedArea = success?.areas?.firstOrNull { it.id == selectedAreaId }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ShowTopBarWithNavigationButton(title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    selectedArea?.iconUrl?.takeIf { it.isNotBlank() }?.let { icon ->
                        AsyncImage(
                            model = icon,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Text(
                        text = selectedArea?.title ?: success?.parentTitle ?: "直播分区",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            })
        },
    ) { padding ->
        when (val current = state) {
            LiveAreaTabsUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is LiveAreaTabsUiState.Error -> ShowErrorContent(
                message = current.message,
                retry = tabsViewModel::refreshAreas,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is LiveAreaTabsUiState.Success -> LiveAreaRoomsContent(
                parentAreaId = parentAreaId,
                state = current,
                selectedAreaId = selectedAreaId,
                onAreaSelected = { selectedAreaId = it },
                isActive = isActive,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun LiveAreaRoomsContent(
    parentAreaId: Int,
    state: LiveAreaTabsUiState.Success,
    selectedAreaId: Int,
    onAreaSelected: (Int) -> Unit,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val areaIds = listOf(0) + state.areas.map { it.id }
    val pager = rememberPagerState(initialPage = areaIds.indexOf(selectedAreaId).coerceAtLeast(0)) { areaIds.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.currentPage) { onAreaSelected(areaIds[pager.currentPage]) }

    Column(modifier.fillMaxSize()) {
        SecondaryScrollableTabRow(selectedTabIndex = pager.currentPage) {
            areaIds.forEachIndexed { index, _ ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(if (index == 0) "全部" else state.areas[index - 1].title) },
                    modifier = Modifier.height(36.dp),
                )
            }
        }
        HorizontalPager(
            state = pager,
            key = { areaIds[it] },
            beyondViewportPageCount = 1,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val currentAreaId = areaIds[page]
            val roomsViewModel = viewModel(key = "LiveAreaRooms:$parentAreaId:$currentAreaId") {
                LiveAreaRoomsViewModel(parentAreaId, currentAreaId)
            }
            LiveRoomsGridPage(
                viewModel = roomsViewModel,
                gridState = rememberLazyGridState(),
                isSelected = isActive && pager.currentPage == page && !pager.isScrollInProgress,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
