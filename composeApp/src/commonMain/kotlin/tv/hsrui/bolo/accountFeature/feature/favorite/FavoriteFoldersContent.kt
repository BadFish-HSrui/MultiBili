package tv.hsrui.bolo.accountFeature.feature.favorite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
    onFolderDeleted: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val favoriteFoldersGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var showCreateFavoriteFolderDialog by rememberSaveable { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
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
                                onDeleted = { onFolderDeleted(folder.id) },
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
                        FloatingActionButtonMenuItem(
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

                    if (showCreateFavoriteFolderDialog) {
                        ShowCreateFavoriteFolderDialog(
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
}
