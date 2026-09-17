package tv.hsrui.bolo.view.video

import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.feature.video.list.VideoListItemData

sealed class VideoUiState {
    data object Loading : VideoUiState()
    data object Empty : VideoUiState()
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
        val videoList: VideoListUiState? = null,
    ) : VideoUiState() {
        val selectedSection get() = video.collection?.sections?.firstOrNull { it.sectionId == selectedSectionId }
        val playingEpisodeKey get() = video.collection?.sections?.flatMap { it.episodes }
            ?.firstOrNull { it.avid == video.avid }?.key
    }
    data class  Error(val message: String): VideoUiState()
}

data class VideoListUiState(
    val title: String,
    val total: Int = 0,
    val items: List<VideoListItemData> = emptyList(),
    val headCursor: VideoListItemData? = null,
    val tailCursor: VideoListItemData? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val isLoadingPrevious: Boolean = false,
    val isLoadingNext: Boolean = false,
    val previousError: String? = null,
    val nextError: String? = null,
    val isReloading: Boolean = false,
    val reloadError: String? = null,
    val pendingDescending: Boolean? = null,
    val isDescending: Boolean = false,
    val revision: Int = 0,
)
