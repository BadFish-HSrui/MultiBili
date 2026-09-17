package tv.hsrui.bolo.userSpace.series

import tv.hsrui.network.feature.video.series.VideoSeriesData
import tv.hsrui.network.model.VideoCard

sealed interface UserSeriesUiState {
    data object Loading : UserSeriesUiState
    data class Error(val message: String) : UserSeriesUiState
    data class Success(
        val series: VideoSeriesData,
        val videos: List<VideoCard>,
        val page: Int,
        val canLoadMore: Boolean,
        val isRefreshing: Boolean = false,
        val refreshError: String? = null,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : UserSeriesUiState
}
