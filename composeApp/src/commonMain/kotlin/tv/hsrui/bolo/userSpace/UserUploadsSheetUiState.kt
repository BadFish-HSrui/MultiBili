package tv.hsrui.bolo.userSpace

import tv.hsrui.bolo.ui.common.videosPage.VideosUiState

data class UserUploadsSheetUiState(
    val videos: VideosUiState = VideosUiState.Loading,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val refreshError: String? = null,
    val loadMoreError: String? = null,
    val refreshGeneration: Int = 0,
)
