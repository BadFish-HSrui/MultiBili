package tv.hsrui.bolo.favorite.videos

import tv.hsrui.network.feature.favorite.FavoriteVideoCard

sealed class FavoriteVideosUiState {
    data object Loading : FavoriteVideosUiState()
    data class Success(
        val folderTitle: String,
        val folderIntro: String,
        val isPrivate: Boolean,
        val isDefault: Boolean,
        val videos: List<FavoriteVideoCard>,
        val ownerMid: Long = 0,
    ) : FavoriteVideosUiState()

    data class Error(val message: String) : FavoriteVideosUiState()
}
