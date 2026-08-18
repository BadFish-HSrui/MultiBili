package tv.hsrui.bolo.view.favorite

import tv.hsrui.network.feature.favorite.FavoriteVideoCard

sealed class FavoriteVideosUiState {
    data object Loading : FavoriteVideosUiState()
    data class Success(
        val folderTitle: String,
        val isDefault: Boolean,
        val videos: List<FavoriteVideoCard>
    ) : FavoriteVideosUiState()

    data class Error(val message: String) : FavoriteVideosUiState()
}
