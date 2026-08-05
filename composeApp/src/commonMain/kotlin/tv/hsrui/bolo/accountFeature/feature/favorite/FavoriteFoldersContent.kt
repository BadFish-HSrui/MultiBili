package tv.hsrui.bolo.accountFeature.feature.favorite

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import tv.hsrui.bolo.ui.components.error.ShowErrorContent
import tv.hsrui.bolo.ui.components.grid.ShowHorizontalCardGrid

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoriteFoldersContent(
    uiState: FavoriteFoldersUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val favoriteFoldersGridState = rememberLazyGridState()

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
                        FavoriteFolderCard(folder = folder)
                    }
                }
            }
        }
    }
}
