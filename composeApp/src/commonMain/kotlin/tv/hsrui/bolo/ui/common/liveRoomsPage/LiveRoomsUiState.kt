package tv.hsrui.bolo.ui.common.liveRoomsPage

import tv.hsrui.network.model.LiveCard

sealed interface LiveRoomsUiState {
    data object Loading : LiveRoomsUiState
    data class Success(
        val rooms: List<LiveCard>,
        val page: Int,
        val hasMore: Boolean,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : LiveRoomsUiState
    data class Error(val message: String) : LiveRoomsUiState
}
