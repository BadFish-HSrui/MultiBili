package tv.hsrui.bolo.view.favorite

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
import tv.hsrui.bolo.utils.OnGridBottomReached

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FavoriteVideosContent(
    uiState: FavoriteVideosUiState,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

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
                        keySelector = { it.avid },
                        gridState = gridState
                    ) { video ->
                        ShowFavoriteVideoCard(videoInfo = video)
                    }
                }
            }
        }
    }
}
