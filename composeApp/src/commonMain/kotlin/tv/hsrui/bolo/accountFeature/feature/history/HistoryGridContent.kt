package tv.hsrui.bolo.accountFeature.feature.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.history.ShowHistoryCard
import tv.hsrui.bolo.utils.OnGridBottomReached
import tv.hsrui.network.feature.history.deleteHistory

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HistoryGridContent(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel { HistoryViewModel() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val historyGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val navigator: Navigator = koinInject()
    var refreshAfterSearch by rememberSaveable { mutableStateOf(false) }
    val currentRoute = navigator.backStack.lastOrNull()
    LaunchedEffect(currentRoute, refreshAfterSearch) {
        if (refreshAfterSearch && currentRoute == BoloRoute.AccountFeature.History) {
            refreshAfterSearch = false
            viewModel.refreshItems()
        }
    }

    historyGridState.OnGridBottomReached(buffer = 8, isLoading = viewModel.isLoading) {
        viewModel.loadMoreItems()
    }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = {
            viewModel.refreshItems()
        },
        modifier = modifier
    ) {
        when (uiState) {
            is HistoryUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is HistoryUiState.Error -> {
                ShowErrorContent(
                    message = (uiState as HistoryUiState.Error).message,
                    retry = { viewModel.refreshItems() }
                )
            }

            is HistoryUiState.Success -> {
                val snackbarManager: SnackbarManager = koinInject()
                Box {
                    ShowHorizontalCardGrid(
                        cards = (uiState as HistoryUiState.Success).items,
                        keySelector = { it.recordKey },
                        gridState = historyGridState
                    ) { item ->
                        ShowHistoryCard(
                            item = item,
                            onDelete = {
                                try {
                                    val result = deleteHistory(
                                        typeString = item.typeString,
                                        id = item.recordId
                                    )
                                    if (result.isSuccess) {
                                        viewModel.removeItem(item.recordKey)
                                    } else {
                                        snackbarManager.showMessage(result.message)
                                    }
                                } catch (e: Exception) {
                                    snackbarManager.showMessage(e.toString())
                                }
                            },
                            deleteDialogTitle = "删除历史记录",
                            modifier = Modifier.height(88.dp)
                        )
                    }
                    ShowGridFABMenu(
                        onBackToTop = { scope.launch { historyGridState.animateScrollToItem(0) } },
                        onRefresh = { viewModel.refreshItems() },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        FloatingActionButtonMenuItem(
                            onClick = {
                                refreshAfterSearch = true
                                navigator.navigateTo(BoloRoute.AccountFeature.HistorySearch)
                            },
                            text = { Text("搜索内容") },
                            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}