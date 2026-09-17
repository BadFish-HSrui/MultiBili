package tv.hsrui.bolo.view.video

import tv.hsrui.network.feature.video.VideoInfoData

sealed class VideoUiState {
    data object Loading : VideoUiState()
    data class Success(
        val video: VideoInfoData,
        val selectedSectionId: Long? = video.collection?.sections?.firstOrNull { section ->
            section.episodes.any { it.avid == video.avid && it.cid == video.cid }
        }?.sectionId ?: video.collection?.sections?.firstOrNull { section ->
            section.episodes.any { it.avid == video.avid }
        }?.sectionId ?: video.collection?.sections?.firstOrNull()?.sectionId,
        val isDescending: Boolean = false,
        val switchingEpisodeKey: String? = null,
        val isSwitchingEpisode: Boolean = false,
        val episodeError: String? = null,
    ) : VideoUiState() {
        val selectedSection get() = video.collection?.sections?.firstOrNull { it.sectionId == selectedSectionId }
        val playingEpisodeKey get() = video.collection?.sections?.flatMap { it.episodes }
            ?.firstOrNull { it.avid == video.avid }?.key
    }
    data class  Error(val message: String): VideoUiState()
}
