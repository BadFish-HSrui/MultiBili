package tv.hsrui.bolo.main.region

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.boloSetting.BoloSettings
import tv.hsrui.network.feature.region.Region

@Composable
fun RegionsScreen(
    modifier: Modifier = Modifier,
    reselectEvents: Flow<Unit>? = null,
) {
    val settings: BoloSettings = koinInject()
    val tabs = settings.general.regionOrder
    val currentTabs by rememberUpdatedState(tabs)
    val pagerState = rememberPagerState { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    var showRegionOrder by rememberSaveable { mutableStateOf(false) }

    val pageReselectEvents = remember(reselectEvents, pagerState) {
        Region.entries.associateWith { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    }
    LaunchedEffect(reselectEvents, pagerState) {
        reselectEvents?.collect {
            if (!pagerState.isScrollInProgress) {
                pageReselectEvents.getValue(currentTabs[pagerState.currentPage]).tryEmit(Unit)
            }
        }
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxWidth().height(36.dp),
        ) {
            SecondaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier.fillMaxWidth(),
                containerColor = TabRowDefaults.secondaryContainerColor,
                edgePadding = 40.dp,
            ) {
                tabs.forEachIndexed { index, region ->
                    key(region.tid) {
                        Tab(
                            selected = (pagerState.currentPage == index),
                            onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(region.title) },
                            modifier = Modifier.height(36.dp),
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).size(32.dp),
                color = TabRowDefaults.secondaryContainerColor,
                contentColor = TabRowDefaults.secondaryContentColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                        IconButton(onClick = { showRegionOrder = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "分区顺序", modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        HorizontalPager(state = pagerState, beyondViewportPageCount = 1, key = { tabs[it].tid }) { page ->
            val region = tabs[page]
            val activeReselectEvents = pageReselectEvents.getValue(region).takeIf {
                pagerState.currentPage == page && !pagerState.isScrollInProgress
            }
            RegionFeedPage(region, reselectEvents = activeReselectEvents)
        }
    }

    if (showRegionOrder) {
        ShowRegionOrderSheet(
            regionOrder = tabs,
            onCancel = { showRegionOrder = false },
            onConfirm = { order ->
                val selectedRegion = tabs[pagerState.currentPage]
                if (order != tabs) {
                    settings.general.regionOrder = order
                    pagerState.requestScrollToPage(settings.general.regionOrder.indexOf(selectedRegion))
                }
                showRegionOrder = false
            },
        )
    }
}
