package tv.hsrui.bolo.userSpace.collection

import tv.hsrui.network.feature.video.collection.VideoCollectionData

sealed interface UserCollectionUiState {
    data object Loading : UserCollectionUiState
    data class Error(val message: String) : UserCollectionUiState
    data class Success(
        val collection: VideoCollectionData,
        val selectedSectionId: Long? = collection.sections.firstOrNull()?.sectionId,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
    ) : UserCollectionUiState
}
