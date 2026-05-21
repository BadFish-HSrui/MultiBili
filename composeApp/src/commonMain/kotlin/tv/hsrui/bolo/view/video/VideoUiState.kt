package tv.hsrui.bolo.view.video

import tv.hsrui.network.feature.video.VideoInfoData

sealed class VideoUiState {
    data object Loading : VideoUiState()
    data class  Success(val video: VideoInfoData) : VideoUiState()
    data class  Error(val message: String): VideoUiState()
}