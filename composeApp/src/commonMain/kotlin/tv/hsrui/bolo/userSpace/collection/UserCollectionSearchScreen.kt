package tv.hsrui.bolo.userSpace.collection

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.Navigator
import tv.hsrui.bolo.search.ShowSearchInputTopBar
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage
import tv.hsrui.bolo.ui.common.videosPage.VideosUiState

@Composable
fun UserCollectionSearchScreen(
    mid: Long,
    seasonId: Long,
    modifier: Modifier = Modifier,
    viewModel: UserCollectionSearchViewModel = viewModel(key = "UserCollectionSearch:$mid:$seasonId") {
        UserCollectionSearchViewModel(mid, seasonId)
    },
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
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = viewModel::refreshVideos,
                modifier = Modifier.weight(1f),
            ) {
                if (state.keyword.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("请输入关键词搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        VideosGridPage(
                            uiState = when {
                                state.isSearching -> VideosUiState.Loading
                                state.error != null -> VideosUiState.Error(state.error.orEmpty())
                                else -> VideosUiState.Success(state.videos)
                            },
                            isLoading = true,
                            onRefresh = viewModel::refreshVideos,
                            onLoadMore = {},
                            emptyMessage = "未找到相关视频",
                            videoGridState = gridState,
                            enablePullToRefresh = false,
                            modifier = Modifier.weight(1f).then(
                                if (!state.isSearching && state.error == null && state.videos.isEmpty()) {
                                    Modifier.scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
                                } else Modifier,
                            ),
                        )
                    }
                }
            }
        }
    }
}
