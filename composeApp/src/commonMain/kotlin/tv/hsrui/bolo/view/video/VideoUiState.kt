package tv.hsrui.bolo.view.video

import tv.hsrui.network.feature.video.VideoInfoData
import tv.hsrui.network.feature.video.collection.VideoCollectionEpisodeData
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
        val episodeNavigationPrevious: Boolean? = null,
    ) : VideoUiState() {
        val selectedSection get() = video.collection?.sections?.firstOrNull { it.sectionId == selectedSectionId }
        val playingEpisodeKey get() = video.collection?.sections?.flatMap { it.episodes }
            ?.firstOrNull { it.avid == video.avid }?.key

        val episodeNavigationEnabled get() = !isSwitchingEpisode && episodeNavigationPrevious == null && videoList?.isReloading != true
        val hasPreviousEpisode get() = hasAdjacentEpisode(before = true)
        val hasNextEpisode get() = hasAdjacentEpisode(before = false)

        internal fun adjacentPart(before: Boolean): Long? {
            val standalone = videoList == null && (video.collection?.seasonId ?: 0L) <= 0L
            val parts = video.parts.let {
                if (standalone && isDescending) it.reversed() else it
            }
            val index = parts.indexOfFirst { it.cid == video.cid }
            return if (index < 0) null else parts.getOrNull(index + if (before) -1 else 1)?.cid
        }

        internal fun adjacentCollectionEpisode(before: Boolean): VideoCollectionEpisodeData? {
            val episodes = video.collection?.takeIf { it.seasonId > 0 }?.sections.orEmpty().flatMap { section ->
                section.episodes.distinctBy { it.key }.let { if (isDescending) it.reversed() else it }
            }
            val index = episodes.indexOfFirst { it.key == playingEpisodeKey }
            if (index < 0) return null
            val candidates = if (before) episodes.take(index).asReversed() else episodes.drop(index + 1)
            return candidates.firstOrNull { it.isAvailable && it.avid != video.avid }
        }

        internal fun adjacentListVideo(before: Boolean): VideoListItemData? {
            val items = videoList?.items ?: return null
            val index = items.indexOfFirst { it.isVideo && it.id == video.avid }
            if (index < 0) return null
            return (if (before) items.take(index).asReversed() else items.drop(index + 1)).firstOrNull { it.isAvailable }
        }

        private fun hasAdjacentEpisode(before: Boolean): Boolean {
            if (adjacentPart(before) != null) return true
            val list = videoList
            if (list != null) {
                if (list.items.none { it.isVideo && it.id == video.avid }) return false
                return adjacentListVideo(before) != null || if (before) list.hasPrevious else list.hasNext
            }
            return adjacentCollectionEpisode(before) != null
        }
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
