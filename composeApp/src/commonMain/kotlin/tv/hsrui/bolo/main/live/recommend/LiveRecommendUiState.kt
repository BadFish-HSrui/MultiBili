package tv.hsrui.bolo.main.live.recommend

import tv.hsrui.network.model.LiveCard

sealed interface LiveRecommendUiState {
    data object Loading : LiveRecommendUiState
    data class Success(
        val rooms: List<LiveCard>,
        val page: Int,
        val hasMore: Boolean,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : LiveRecommendUiState
    data class Error(val message: String) : LiveRecommendUiState
}
