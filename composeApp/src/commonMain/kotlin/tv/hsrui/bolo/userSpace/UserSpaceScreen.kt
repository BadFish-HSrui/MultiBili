package tv.hsrui.bolo.userSpace

import tv.hsrui.bolo.navigation.openVideoList
import tv.hsrui.bolo.view.video.VideoPlaybackRequest
import tv.hsrui.network.feature.video.list.VideoListType
import tv.hsrui.network.feature.video.list.VideoListSort
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import tv.hsrui.bolo.navigation.BoloRoute
import tv.hsrui.bolo.navigation.Navigator
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.hsrui.bolo.favorite.FavoriteFoldersContent
import tv.hsrui.bolo.favorite.FavoriteFoldersUiState
import tv.hsrui.bolo.ui.common.videosPage.VideosGridPage
import tv.hsrui.bolo.ui.common.videosPage.VideosUiState
import tv.hsrui.bolo.userSpace.collection.UserCollectionsContent
import tv.hsrui.bolo.userSpace.series.UserSeriesContent
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.ui.components.user.ShowUserInfoBar
import tv.hsrui.network.login.storage.LoginStorage
import tv.hsrui.network.feature.user.space.UserSpaceUploadOrder

@Composable
fun UserSpaceScreen(mid: Long, modifier: Modifier = Modifier) {
    val loginStorage: LoginStorage = koinInject()
    val viewModel = viewModel(key = "UserSpace:$mid") { UserSpaceViewModel(mid, loginStorage) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelCollectionPlayback() }
    }
    val currentUserMid by loginStorage.currentUserMidFlow.collectAsStateWithLifecycle(
        initialValue = if (loginStorage.isLoggedIn) loginStorage.cookies.dedeUserID else 0L,
    )
    var loadedUserMid by remember(viewModel) { mutableStateOf(currentUserMid) }
    LaunchedEffect(currentUserMid) {
        if (loadedUserMid != currentUserMid) {
            loadedUserMid = currentUserMid
            viewModel.refreshSpace()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { ShowTopBarWithNavigationButton(title = { Text("用户空间") }) },
    ) { padding ->
        key(mid) { UserSpaceContent(
            mid = mid,
            state = state,
            onRefresh = viewModel::refreshTab,
            onLoadMore = viewModel::loadMoreUploads,
            onUploadOrderSelected = viewModel::setUploadOrder,
            onLoadMoreCollections = viewModel::loadMoreCollections,
            onLoadMoreSeries = viewModel::loadMoreSeries,
            onPlayCollection = viewModel::playCollection,
            canManageFavorites = currentUserMid > 0 && currentUserMid == mid,
            canManageFavoritesNow = { loginStorage.isLoggedIn && loginStorage.cookies.dedeUserID == mid },
            modifier = Modifier.padding(padding),
        ) }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UserSpaceContent(
    mid: Long,
    state: UserSpaceUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onUploadOrderSelected: (UserSpaceUploadOrder) -> Unit,
    onLoadMoreCollections: () -> Unit,
    onLoadMoreSeries: () -> Unit,
    onPlayCollection: (Long) -> Unit,
    canManageFavorites: Boolean,
    canManageFavoritesNow: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val navigator: Navigator = koinInject()
    var selectedTab by rememberSaveable(mid) { mutableStateOf(UserSpaceTab.Uploads) }
    val uploadGrid = rememberLazyGridState()
    val likesGrid = rememberLazyGridState()
    val coinsGrid = rememberLazyGridState()
    val favoritesGrid = rememberLazyGridState()
    val collectionsGrid = rememberLazyGridState()
    val seriesGrid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val tabs = state.visibleTabs
    val currentTab = selectedTab.takeIf { it in tabs } ?: tabs.firstOrNull()

    LaunchedEffect(tabs) { currentTab?.let { selectedTab = it } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxHeaderHeight = maxHeight / 2
        val availableTabsWidth = maxWidth
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeaderHeight)
                    .clipToBounds().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                ShowUserInfoBar(mid = mid, refreshKey = state.refreshGeneration)
            }
            Surface(modifier = Modifier.weight(1f).fillMaxWidth(), tonalElevation = 1.dp) {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (currentTab == null) {
                            Column(
                                modifier = Modifier.fillMaxSize().scrollable(rememberScrollableState { 0f }, Orientation.Vertical),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            ) {
                                Text("暂无公开内容")
                                Button(onClick = onRefresh) { Text("刷新") }
                            }
                        } else key(tabs) {
                            // Tab 集合改变时按类型重建 Pager；列表状态在外层保留，避免索引错位。
                            val pager = rememberPagerState(initialPage = tabs.indexOf(currentTab)) { tabs.size }
                            LaunchedEffect(pager.settledPage) { selectedTab = tabs[pager.settledPage] }
                            val density = LocalDensity.current
                            val tabWidths = remember { mutableStateMapOf<UserSpaceTab, Int>() }
                            val defaultEdgePadding = TabRowDefaults.ScrollableTabRowEdgeStartPadding
                            val edgePadding = if (tabWidths.size == tabs.size) {
                                val tabsWidth = with(density) { tabWidths.values.sum().toDp() }
                                ((availableTabsWidth - tabsWidth) / 2).coerceAtLeast(defaultEdgePadding)
                            } else {
                                defaultEdgePadding
                            }
                            SecondaryScrollableTabRow(
                                selectedTabIndex = pager.currentPage,
                                containerColor = Color.Transparent,
                                edgePadding = edgePadding,
                            ) {
                                tabs.forEachIndexed { index, tab ->
                                    Tab(
                                        selected = pager.currentPage == index,
                                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                                        text = { Text(tab.title) },
                                        modifier = Modifier.height(36.dp).onSizeChanged { tabWidths[tab] = it.width },
                                    )
                                }
                            }
                            HorizontalPager(state = pager, key = { tabs[it] }, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                                when (val tab = tabs[page]) {
                                    UserSpaceTab.Uploads, UserSpaceTab.Likes, UserSpaceTab.Coins -> {
                                        val section = when (tab) {
                                            UserSpaceTab.Uploads -> state.uploads
                                            UserSpaceTab.Likes -> state.likes
                                            else -> state.coins
                                        }
                                        val videoState = when (section) {
                                            is UserSpaceSectionState.Success -> VideosUiState.Success(section.data)
                                            is UserSpaceSectionState.Error -> VideosUiState.Error(section.message)
                                            else -> VideosUiState.Loading
                                        }
                                        Column(Modifier.fillMaxSize()) {
                                            if (tab == UserSpaceTab.Uploads) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    LazyRow(
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    ) {
                                                        items(UserSpaceUploadOrder.entries, key = { it.name }) { order ->
                                                            FilterChip(
                                                                selected = order == state.uploadOrder,
                                                                onClick = {
                                                                    if (order != state.uploadOrder) {
                                                                        uploadGrid.requestScrollToItem(0)
                                                                        onUploadOrderSelected(order)
                                                                    }
                                                                },
                                                                label = { Text(order.title, maxLines = 1) },
                                                            )
                                                        }
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            val sort = when (state.uploadOrder) {
                                                                UserSpaceUploadOrder.Latest -> VideoListSort.Default
                                                                UserSpaceUploadOrder.MostPlayed -> VideoListSort.MostPlayed
                                                                UserSpaceUploadOrder.MostFavorited -> VideoListSort.MostFavorited
                                                            }
                                                            openVideoList(VideoPlaybackRequest.VideoList(VideoListType.Uploads, mid, sort))
                                                        },
                                                        enabled = mid > 0 && !(section is UserSpaceSectionState.Success && section.data.isEmpty()),
                                                    ) {
                                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 4.dp).size(20.dp))
                                                        Text("播放全部")
                                                    }
                                                }
                                            }
                                            VideosGridPage(
                                                uiState = videoState,
                                                isLoading = tab != UserSpaceTab.Uploads || state.isRefreshing || state.isLoadingMore || !state.canLoadMore || state.loadMoreError != null || pager.currentPage != page,
                                                onRefresh = onRefresh,
                                                onLoadMore = { if (tab == UserSpaceTab.Uploads && pager.currentPage == page) onLoadMore() },
                                                videoGridState = when (tab) {
                                                    UserSpaceTab.Uploads -> uploadGrid
                                                    UserSpaceTab.Likes -> likesGrid
                                                    else -> coinsGrid
                                                },
                                                enablePullToRefresh = false,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                if (tab == UserSpaceTab.Uploads) {
                                                    FloatingActionButtonMenuItem(
                                                        onClick = { navigator.navigateTo(BoloRoute.User.UploadsSearch(mid)) },
                                                        text = { Text("搜索内容") },
                                                        icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                                    )
                                                }
                                            }
                                            if (tab == UserSpaceTab.Uploads && state.loadMoreError != null) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                                                    Text(state.loadMoreError, modifier = Modifier.weight(1f), maxLines = 2)
                                                    TextButton(onClick = onLoadMore, enabled = !state.isLoadingMore) { Text("重试") }
                                                }
                                            }
                                        }
                                    }
                                    UserSpaceTab.Favorites -> FavoriteFoldersContent(
                                        uiState = when (val section = state.favorites) {
                                            is UserSpaceSectionState.Success -> FavoriteFoldersUiState.Success(section.data)
                                            is UserSpaceSectionState.Error -> FavoriteFoldersUiState.Error(section.message)
                                            else -> FavoriteFoldersUiState.Loading
                                        },
                                        onRefresh = onRefresh,
                                        canManage = canManageFavorites,
                                        canManageNow = canManageFavoritesNow,
                                        favoriteFoldersGridState = favoritesGrid,
                                        enablePullToRefresh = false,
                                    )
                                    UserSpaceTab.Collections -> UserCollectionsContent(
                                        mid = mid,
                                        state = state,
                                        gridState = collectionsGrid,
                                        onRefresh = onRefresh,
                                        onLoadMore = onLoadMoreCollections,
                                        onPlay = onPlayCollection,
                                        isActive = pager.currentPage == page,
                                    )
                                    UserSpaceTab.Series -> UserSeriesContent(
                                        mid = mid,
                                        state = state,
                                        gridState = seriesGrid,
                                        onRefresh = onRefresh,
                                        onLoadMore = onLoadMoreSeries,
                                        isActive = pager.currentPage == page,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
