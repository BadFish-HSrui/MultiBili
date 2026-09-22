package tv.hsrui.bolo.accountFeature.feature.watchLater

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.utils.calculateWithoutBottom
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.accountFeature.feature.history.HistoryVideosUiState
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.dialog.ShowConfirmDialog
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.ui.components.video.ShowHistoryVideoCard
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.feature.watchLater.deleteAllViewedFromWatchLater
import tv.hsrui.network.feature.watchLater.deleteAllWatchLater
import tv.hsrui.network.feature.watchLater.deleteWatchLater

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WatchLaterScreen(
    modifier: Modifier = Modifier,
    isEntryFromList: Boolean = true,
    viewModel: WatchLaterViewModel = viewModel { WatchLaterViewModel() }
) {
    val uiState: HistoryVideosUiState by viewModel.uiState.collectAsState()
    val watchLaterGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val navigator: Navigator = koinInject()
    var refreshAfterSearch by rememberSaveable { mutableStateOf(false) }
    val currentRoute = navigator.backStack.lastOrNull()
    LaunchedEffect(currentRoute, refreshAfterSearch) {
        if (refreshAfterSearch && currentRoute == BoloRoute.AccountFeature.WatchLater) {
            refreshAfterSearch = false
            viewModel.refreshVideos()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isEntryFromList || !isExpanded()) {
                ShowTopBarWithNavigationButton(title = { Text("稍后再看") })
            }
        }
    ) { innerPadding ->

        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = {
                viewModel.refreshVideos()
            },
            modifier = Modifier.padding(innerPadding.calculateWithoutBottom())
        ) {
            when (uiState) {
                is HistoryVideosUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is HistoryVideosUiState.Error -> {
                    ShowErrorContent(
                        message = (uiState as HistoryVideosUiState.Error).message,
                        retry = { viewModel.refreshVideos() }
                    )
                }

                is HistoryVideosUiState.Success -> {
                    val snackbarManager: SnackbarManager = koinInject()
                    Box {
                        ShowHorizontalCardGrid(
                            cards = (uiState as HistoryVideosUiState.Success).videos,
                            keySelector = { it.avid },
                            gridState = watchLaterGridState
                        ) { video ->
                            ShowHistoryVideoCard(
                                videoInfo = video,
                                onDelete = {
                                    try {
                                        val result = deleteWatchLater(video.avid)
                                        if (result.isSuccess) {
                                            viewModel.removeItem(video.recordKey)
                                        } else {
                                            snackbarManager.showMessage(result.message)
                                        }
                                    } catch (e: Exception) {
                                        snackbarManager.showMessage(e.toString())
                                    }
                                },
                                deleteDialogTitle = "删除稍后再看",
                                modifier = Modifier.height(88.dp)
                            )
                        }
                        ShowGridFABMenu(
                            onBackToTop = { scope.launch { watchLaterGridState.animateScrollToItem(0) } },
                            onRefresh = { viewModel.refreshVideos() },
                            modifier = Modifier.align(Alignment.BottomEnd)
                        ) {
                            FloatingActionButtonMenuItem(
                                onClick = {
                                    refreshAfterSearch = true
                                    navigator.navigateTo(BoloRoute.AccountFeature.WatchLaterSearch)
                                },
                                text = { Text("搜索内容") },
                                icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            )
                            var showDeleteAllDialog by remember { mutableStateOf(false) }
                            var showDeleteViewedDialog by remember { mutableStateOf(false) }

                            FloatingActionButtonMenuItem(
                                onClick = { showDeleteAllDialog = true },
                                text = { Text("删除全部") },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteForever,
                                        contentDescription = null
                                    )
                                }
                            )
                            FloatingActionButtonMenuItem(
                                onClick = { showDeleteViewedDialog = true },
                                text = { Text("清除看完") },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoDelete,
                                        contentDescription = null
                                    )
                                }
                            )

                            if (showDeleteAllDialog) {
                                ShowConfirmDialog(
                                    title = { Text("删除所有稍后再看视频") },
                                    onCancel = { showDeleteAllDialog = false },
                                    onConfirm = {
                                        scope.launch {
                                            try {
                                                val result = deleteAllWatchLater()
                                                if (result.isSuccess) {
                                                    viewModel.refreshVideos()
                                                } else {
                                                    snackbarManager.showMessage(result.message)
                                                }
                                            } catch (e: Exception) {
                                                snackbarManager.showMessage(e.toString())
                                            } finally {
                                                showDeleteAllDialog = false
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteForever,
                                            contentDescription = null
                                        )
                                    },
                                    text = "*该操作无法撤销*"
                                )
                            }
                            if (showDeleteViewedDialog) {
                                ShowConfirmDialog(
                                    title = { Text("清除所有已看完视频") },
                                    onCancel = { showDeleteViewedDialog = false },
                                    onConfirm = {
                                        scope.launch {
                                            try {
                                                val result = deleteAllViewedFromWatchLater()
                                                if (result.isSuccess) {
                                                    viewModel.refreshVideos()
                                                } else {
                                                    snackbarManager.showMessage(result.message)
                                                }
                                            } catch (e: Exception) {
                                                snackbarManager.showMessage(e.toString())
                                            } finally {
                                                showDeleteViewedDialog = false
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteForever,
                                            contentDescription = null
                                        )
                                    },
                                    text = "*该操作无法撤销*"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
