package tv.hsrui.bolo.search

import tv.hsrui.network.feature.search.SearchUserData
import tv.hsrui.network.model.MediaCard
import tv.hsrui.network.model.VideoCard

sealed class SearchResultsUiState {
    data object Loading : SearchResultsUiState()
    data class Success(
        val videos: List<VideoCard> = emptyList(),
        val media: List<MediaCard> = emptyList(),
        val users: List<SearchUserData> = emptyList(),
        val page: Int,
        val hasMore: Boolean,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : SearchResultsUiState() {
        val hasItems: Boolean get() = videos.isNotEmpty() || media.isNotEmpty() || users.isNotEmpty()
    }
    data class Error(val message: String) : SearchResultsUiState()
}
