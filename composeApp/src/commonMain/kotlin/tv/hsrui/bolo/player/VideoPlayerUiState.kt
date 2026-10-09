package tv.hsrui.bolo.player

import tv.hsrui.network.feature.player.VideoSource

sealed class VideoPlayerUiState {
    data object Loading : VideoPlayerUiState()
    data class Success(
        val videoSource: VideoSource,
        val avid: Long,
        val cid: Long,
        val episodeId: Long?,
        val videoAspectRatio: Float?,
    ) : VideoPlayerUiState() {
        fun matches(avid: Long, cid: Long, episodeId: Long? = null): Boolean =
            this.avid == avid && this.cid == cid && this.episodeId == episodeId
    }
    data class Error(val message: String) : VideoPlayerUiState()
}
