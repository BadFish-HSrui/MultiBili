package tv.hsrui.bolo.player

import tv.hsrui.network.feature.player.VideoSource

sealed class VideoPlayerUiState {
    data object Loading : VideoPlayerUiState()
    data class Success(val videoSource: VideoSource): VideoPlayerUiState()
    data class Error(val message: String) : VideoPlayerUiState()
}
