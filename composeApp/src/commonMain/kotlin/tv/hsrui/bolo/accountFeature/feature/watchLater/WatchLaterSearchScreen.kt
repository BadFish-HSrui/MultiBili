package tv.hsrui.bolo.accountFeature.feature.watchLater

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.search.ShowSearchInputTopBar
import tv.hsrui.bolo.ui.common.snackbar.SnackbarManager
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.ui.components.video.ShowHistoryVideoCard

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WatchLaterSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: WatchLaterSearchViewModel = viewModel { WatchLaterSearchViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator: Navigator = koinInject()
    val textFieldState = rememberTextFieldState()
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
    val snackbarManager: SnackbarManager = koinInject()
    val scope = rememberCoroutineScope()

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
                state.error != null -> ShowErrorContent(state.error.orEmpty(), retry = viewModel::refreshVideos)
                state.keyword.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("请输入关键词搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    if (state.videos.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("未找到相关视频", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        ShowHorizontalCardGrid(
                            cards = state.videos,
                            keySelector = { it.recordKey },
                            gridState = gridState,
                        ) { video ->
                            val accountMid = state.accountMid
                            ShowHistoryVideoCard(
                                videoInfo = video,
                                onDelete = {
                                    viewModel.deleteVideo(video.avid, accountMid)?.let { snackbarManager.showMessage(it) }
                                },
                                deleteDialogTitle = "删除稍后再看",
                                modifier = Modifier.height(88.dp),
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
