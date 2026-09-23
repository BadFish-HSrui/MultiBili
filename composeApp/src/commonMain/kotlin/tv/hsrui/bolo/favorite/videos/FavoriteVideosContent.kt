package tv.hsrui.bolo.favorite.videos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid
import tv.hsrui.bolo.utils.OnGridBottomReached
import tv.hsrui.network.feature.favorite.FavoriteVideoCard

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoriteVideosContent(
    uiState: FavoriteVideosUiState,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onDeleteFolder: () -> Unit,
    onEditFolder: () -> Unit,
    onSearch: () -> Unit,
    onRemove: suspend (FavoriteVideoCard) -> Unit,
    modifier: Modifier = Modifier,
    canManage: Boolean = false,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        when (uiState) {
            is FavoriteVideosUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is FavoriteVideosUiState.Error -> {
                ShowErrorContent(
                    message = uiState.message,
                    retry = onRefresh
                )
            }

            is FavoriteVideosUiState.Success -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (uiState.videos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无收藏视频",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        gridState.OnGridBottomReached(
                            buffer = 8,
                            isLoading = isLoading,
                            onLoadMore = onLoadMore
                        )

                        ShowHorizontalCardGrid(
                            cards = uiState.videos,
                            keySelector = { it.resourceKey },
                            gridState = gridState
                        ) { video ->
                            ShowFavoriteVideoCard(
                                videoInfo = video,
                                canManage = canManage,
                                onRemove = { onRemove(video) }
                            )
                        }
                    }

                    ShowGridFABMenu(
                        onBackToTop = {
                            if (uiState.videos.isNotEmpty()) {
                                scope.launch { gridState.animateScrollToItem(0) }
                            }
                        },
                        onRefresh = onRefresh,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        FloatingActionButtonMenuItem(
                            onClick = onSearch,
                            text = { Text("搜索内容") },
                            icon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        )
                        if (canManage) {
                            FloatingActionButtonMenuItem(
                                onClick = onEditFolder,
                                text = { Text("编辑信息") },
                                icon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            )
                        }
                        if (canManage && !uiState.isDefault) {
                            FloatingActionButtonMenuItem(
                                onClick = onDeleteFolder,
                                text = { Text("删除收藏") },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteForever,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
