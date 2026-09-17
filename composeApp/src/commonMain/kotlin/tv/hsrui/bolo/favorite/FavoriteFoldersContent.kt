package tv.hsrui.bolo.favorite

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import tv.hsrui.bolo.ui.components.dialog.ShowCreateFavoriteFolderDialog
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowGridFABMenu
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoriteFoldersContent(
    uiState: FavoriteFoldersUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    canManage: Boolean = false,
    canManageNow: () -> Boolean = { canManage },
    favoriteFoldersGridState: LazyGridState = rememberLazyGridState(),
    enablePullToRefresh: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var showCreateFavoriteFolderDialog by rememberSaveable { mutableStateOf(false) }

    val content: @Composable BoxScope.() -> Unit = {
        when (uiState) {
            is FavoriteFoldersUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is FavoriteFoldersUiState.Error -> {
                ShowErrorContent(
                    message = uiState.message,
                    retry = onRefresh
                )
            }

            is FavoriteFoldersUiState.Success -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (uiState.folders.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无收藏夹",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        ShowHorizontalCardGrid(
                            cards = uiState.folders,
                            keySelector = { it.id },
                            gridState = favoriteFoldersGridState
                        ) { folder ->
                            FavoriteFolderCard(
                                folder = folder,
                            )
                        }
                    }

                    ShowGridFABMenu(
                        onBackToTop = {
                            if (uiState.folders.isNotEmpty()) {
                                scope.launch { favoriteFoldersGridState.animateScrollToItem(0) }
                            }
                        },
                        onRefresh = onRefresh,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    ) {
                        if (canManage) FloatingActionButtonMenuItem(
                            onClick = { showCreateFavoriteFolderDialog = true },
                            text = { Text("新建收藏") },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                )
                            },
                        )
                    }

                    if (showCreateFavoriteFolderDialog && canManage) {
                        ShowCreateFavoriteFolderDialog(
                            canCreate = canManageNow,
                            onCancel = { showCreateFavoriteFolderDialog = false },
                            onCreated = {
                                showCreateFavoriteFolderDialog = false
                                onRefresh()
                            },
                        )
                    }
                }
            }
        }
    }
    if (enablePullToRefresh) {
        PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = modifier.fillMaxSize(), content = content)
    } else {
        Box(
            modifier = modifier.fillMaxSize().then(
                if (uiState !is FavoriteFoldersUiState.Success || uiState.folders.isEmpty()) {
                    Modifier.scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
                } else Modifier
            ),
            content = content,
        )
    }
}
