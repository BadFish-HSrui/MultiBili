package tv.hsrui.bolo.favorite

import tv.hsrui.network.feature.favorite.FavoriteFolderInfoData

sealed class FavoriteFoldersUiState {
    data object Loading : FavoriteFoldersUiState()
    data class Success(val folders: List<FavoriteFolderInfoData>) : FavoriteFoldersUiState()
    data class Error(val message: String) : FavoriteFoldersUiState()
}
