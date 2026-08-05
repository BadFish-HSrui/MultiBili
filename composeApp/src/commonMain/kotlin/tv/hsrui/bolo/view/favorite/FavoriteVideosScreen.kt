package tv.hsrui.bolo.view.favorite

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.hsrui.bolo.ui.components.topBar.ShowTopBarWithNavigationButton
import tv.hsrui.bolo.utils.calculateWithoutBottom

@Composable
fun FavoriteVideosScreen(
    mediaId: Long,
    modifier: Modifier = Modifier,
    viewModel: FavoriteVideosViewModel = viewModel(key = mediaId.toString()) {
        FavoriteVideosViewModel(mediaId = mediaId)
    }
) {
    val uiState by viewModel.uiState.collectAsState()
    val folderTitle = (uiState as? FavoriteVideosUiState.Success)
        ?.folderTitle
        ?.ifEmpty { "收藏夹" }
        ?: "收藏夹"

    Scaffold(
        modifier = modifier,
        topBar = {
            ShowTopBarWithNavigationButton(title = { Text(folderTitle) })
        }
    ) { innerPadding ->
        FavoriteVideosContent(
            uiState = uiState,
            isLoading = viewModel.isLoading,
            onLoadMore = viewModel::loadMoreVideos,
            onRefresh = viewModel::refreshVideos,
            modifier = Modifier.padding(innerPadding.calculateWithoutBottom())
        )
    }
}
