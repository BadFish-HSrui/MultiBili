package tv.hsrui.bolo.ui.common.videosPage

import tv.hsrui.network.model.VideoCard

sealed class VideosUiState {
    data object Loading : VideosUiState()
    data class Success(val videos: List<VideoCard>) : VideosUiState()
    data class  Error(val message: String): VideosUiState()
}