package tv.hsrui.bolo.favorite.videos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.search.ShowSearchInputTopBar
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.network.feature.favorite.removeFavoriteVideo
import tv.hsrui.network.login.storage.LoginStorage

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoriteSearchScreen(
    mediaId: Long,
    modifier: Modifier = Modifier,
    viewModel: FavoriteSearchViewModel = viewModel(key = "favorite_search_$mediaId") { FavoriteSearchViewModel(mediaId) },
) {
    val state by viewModel.uiState.collectAsState()
    val navigator: Navigator = koinInject()
    val snackbarManager: SnackbarManager = koinInject()
    val loginStorage: LoginStorage = koinInject()
    val textFieldState = rememberTextFieldState()
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var hasFocused by rememberSaveable { mutableStateOf(false) }
    var displayedRevision by rememberSaveable { mutableStateOf(state.revision) }

    LaunchedEffect(Unit) {
        if (!hasFocused) {
            hasFocused = true
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(state.revision) {
        if (displayedRevision != state.revision) {
            displayedRevision = state.revision
            gridState.scrollToItem(0)
        }
    }
    LaunchedEffect(gridState, state.videos.size, state.isSearching, state.isLoadingMore, state.hasMore, state.loadMoreError) {
        if (!state.isSearching && !state.isLoadingMore && state.hasMore && state.loadMoreError == null) {
            if (state.videos.isEmpty()) {
                viewModel.loadMoreVideos()
                return@LaunchedEffect
            }
            snapshotFlow {
                val layout = gridState.layoutInfo
                layout.totalItemsCount > 0 &&
                    (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= layout.totalItemsCount - 8
            }.distinctUntilChanged().collect { atBottom ->
                if (atBottom) viewModel.loadMoreVideos()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ShowSearchInputTopBar(
                textFieldState = textFieldState,
                onSearch = {
                    val keyword = textFieldState.text.toString().trim()
                    if (keyword.isNotEmpty()) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        viewModel.search(keyword)
                    }
                },
                onBack = navigator::goBack,
                focusRequester = focusRequester,
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = viewModel::refreshVideos,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            when {
                state.isSearching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.error != null -> ShowErrorContent(
                    message = state.error.orEmpty(),
                    retry = viewModel::refreshVideos,
                )
                state.keyword.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("请输入关键词搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    if (state.videos.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (state.isLoadingMore) CircularProgressIndicator()
                            else if (state.loadMoreError != null) {
                                Text(state.loadMoreError.orEmpty())
                                TextButton(onClick = viewModel::loadMoreVideos) { Text("重试") }
                            } else if (!state.hasMore) {
                                Text("未找到相关视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        ShowHorizontalCardGrid(
                            cards = state.videos,
                            keySelector = { it.resourceKey },
                            gridState = gridState,
                            bottomContent = if (state.isLoadingMore || state.loadMoreError != null) {
                                {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        if (state.isLoadingMore) CircularProgressIndicator()
                                        state.loadMoreError?.let { message ->
                                            Text(message, style = MaterialTheme.typography.bodyMedium)
                                            TextButton(onClick = viewModel::loadMoreVideos) { Text("重试") }
                                        }
                                    }
                                }
                            } else null,
                        ) { video ->
                            val resultIdentity = state.accountMid
                            ShowFavoriteVideoCard(
                                videoInfo = video,
                                canManage = viewModel.canManageNow(),
                                onRemove = action@{
                                    if (!viewModel.canManageNow() || loginStorage.cookies.dedeUserID != resultIdentity) return@action
                                    val identity = resultIdentity
                                    try {
                                        val result = removeFavoriteVideo(mediaId, video)
                                        if (!loginStorage.isLoggedIn || loginStorage.cookies.dedeUserID != identity) return@action
                                        if (result.isSuccess) viewModel.removeItem(video.resourceKey)
                                        else snackbarManager.showMessage(result.message.ifBlank { "取消收藏失败" })
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        snackbarManager.showMessage(e.message ?: "其他网络错误")
                                    }
                                },
                            )
                        }
                    }
                    ShowGridFABMenu(
                        onBackToTop = {
                            if (state.videos.isNotEmpty()) scope.launch { gridState.animateScrollToItem(0) }
                        },
                        onRefresh = viewModel::refreshVideos,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }
}
