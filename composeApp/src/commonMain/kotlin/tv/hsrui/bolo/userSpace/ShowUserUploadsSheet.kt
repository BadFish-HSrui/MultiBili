package tv.hsrui.bolo.userSpace

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.koinInject
import tv.hsrui.bolo.navigation.openUserSpace
import tv.hsrui.bolo.navigation.openVideo
import tv.hsrui.bolo.ui.common.videosPage.VideosUiState
import tv.hsrui.bolo.ui.components.dropdownMenu.items.WatchLaterMenuItem
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.user.ShowUserInfoBar
import tv.hsrui.bolo.ui.components.video.ShowVerticalVideoCard
import tv.hsrui.bolo.utils.isExpanded
import tv.hsrui.network.login.storage.LoginStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowUserUploadsSheet(mid: Long, onDismissRequest: () -> Unit) {
    key(mid) {
        val loginStorage: LoginStorage = koinInject()
        val owner = remember {
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        }
        DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
        val model = viewModel(viewModelStoreOwner = owner) { UserUploadsSheetViewModel(mid, loginStorage) }
        val state by model.uiState.collectAsStateWithLifecycle()
        val listState = rememberLazyListState()
        val dismiss by rememberUpdatedState(onDismissRequest)
        var closeRequested by remember { mutableStateOf(false) }
        var dismissed by remember { mutableStateOf(false) }
        var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
        val finishDismiss: () -> Unit = {
            if (!dismissed) {
                dismissed = true
                val navigate = pendingNavigation
                pendingNavigation = null
                dismiss()
                navigate?.invoke()
            }
        }
        val navigateAfterDismiss: (() -> Unit) -> Unit = { navigate ->
            if (!closeRequested && !dismissed) {
                pendingNavigation = navigate
                closeRequested = true
            }
        }
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
            pendingNavigation = null
            finishDismiss()
        }
        LaunchedEffect(state.refreshGeneration) { listState.requestScrollToItem(0) }
        LaunchedEffect(listState, state.videos, state.isRefreshing, state.isLoadingMore,
            state.hasMore, state.loadMoreError, state.refreshError) {
            val videos = (state.videos as? VideosUiState.Success)?.videos.orEmpty()
            if (videos.isNotEmpty() && !state.isRefreshing && !state.isLoadingMore &&
                state.hasMore && state.loadMoreError == null && state.refreshError == null
            ) {
                snapshotFlow {
                    val layout = listState.layoutInfo
                    layout.totalItemsCount > 0 &&
                        (layout.visibleItemsInfo.lastOrNull()?.index ?: -1) >= videos.size - 6
                }.distinctUntilChanged().collect { nearBottom ->
                    if (nearBottom) model.loadMoreVideos()
                }
            }
        }
        val content: @Composable (Modifier) -> Unit = { modifier ->
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                UserUploadsSheetContent(
                    mid = mid,
                    state = state,
                    listState = listState,
                    onRefresh = {
                        listState.requestScrollToItem(0)
                        model.refreshVideos()
                    },
                    onLoadMore = model::loadMoreVideos,
                    onOpenVideo = { avid -> navigateAfterDismiss { openVideo(avid) } },
                    onOpenUser = { uid -> navigateAfterDismiss { openUserSpace(uid) } },
                    modifier = modifier,
                )
            }
        }
        if (isExpanded()) {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val layoutDirection = LocalLayoutDirection.current
            var opening by remember { mutableStateOf(true) }
            LaunchedEffect(drawerState) {
                try {
                    if (!closeRequested) drawerState.open()
                } finally {
                    opening = false
                }
            }
            LaunchedEffect(closeRequested) {
                if (closeRequested) {
                    drawerState.close()
                    finishDismiss()
                }
            }
            LaunchedEffect(opening, drawerState.isClosed, drawerState.isAnimationRunning) {
                if (!opening && drawerState.isClosed && !drawerState.isAnimationRunning) finishDismiss()
            }
            Dialog(
                onDismissRequest = { closeRequested = true },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val sheetWidth = (maxWidth * 0.3F).coerceIn(300.dp, 360.dp)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                                    ModalDrawerSheet(
                                        modifier = Modifier.width(sheetWidth).fillMaxHeight(),
                                        drawerShape = AbsoluteRoundedCornerShape(topLeft = 16.dp, bottomLeft = 16.dp),
                                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Right),
                                    ) {
                                        content(Modifier.fillMaxSize())
                                    }
                                }
                            },
                        ) {}
                    }
                }
            }
        } else {
            val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
            LaunchedEffect(closeRequested) {
                if (closeRequested) {
                    sheetState.hide()
                    finishDismiss()
                }
            }
            ModalBottomSheet(onDismissRequest = finishDismiss, sheetState = sheetState) {
                content(Modifier.fillMaxWidth().fillMaxHeight(0.85f))
            }
        }
    }
}

@Composable
private fun UserUploadsSheetContent(
    mid: Long,
    state: UserUploadsSheetUiState,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenVideo: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val maxHeaderHeight = maxHeight / 2
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxWidth().heightIn(max = maxHeaderHeight).clipToBounds().padding(12.dp),
            ) {
                ShowUserInfoBar(
                    mid = mid,
                    refreshKey = state.refreshGeneration,
                    onUserClick = { onOpenUser(mid) },
                )
            }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxSize()) {
                    state.refreshError?.let { message ->
                        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(message, modifier = Modifier.weight(1f), maxLines = 2)
                            TextButton(onClick = onRefresh) { Text("重试") }
                        }
                    }
                    when (val videos = state.videos) {
                        VideosUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        is VideosUiState.Error -> ShowErrorContent(
                            message = videos.message,
                            retry = onRefresh,
                            modifier = Modifier.scrollable(rememberScrollableState { 0f }, Orientation.Vertical),
                        )
                        is VideosUiState.Success -> if (videos.videos.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize().scrollable(rememberScrollableState { 0f }, Orientation.Vertical),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("暂无投稿")
                                TextButton(onClick = onRefresh) { Text("刷新") }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                items(videos.videos, key = { it.avid }) { video ->
                                    ShowVerticalVideoCard(
                                        videoInfo = video,
                                        modifier = Modifier.height(80.dp),
                                        onClick = { onOpenVideo(video.avid) },
                                        onUserClick = { onOpenUser(video.upMid) },
                                    ) { onDismiss ->
                                        WatchLaterMenuItem(avid = video.avid, onDismiss = onDismiss)
                                    }
                                }
                                if (state.isLoadingMore) {
                                    item(key = "loading_more") {
                                        CircularProgressIndicator(Modifier.padding(8.dp))
                                    }
                                }
                                state.loadMoreError?.let { message ->
                                    item(key = "load_more_error") {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(message, modifier = Modifier.weight(1f), maxLines = 2)
                                            TextButton(onClick = onLoadMore) { Text("重试") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
