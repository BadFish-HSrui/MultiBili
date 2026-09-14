package tv.hsrui.bolo.main.media

import tv.hsrui.network.model.MediaCard

sealed class MediaUiState {
    data object Loading : MediaUiState()
    data class Success(
        val media: List<MediaCard>,
        val hasMore: Boolean,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : MediaUiState()
    data class Error(val message: String) : MediaUiState()
}
