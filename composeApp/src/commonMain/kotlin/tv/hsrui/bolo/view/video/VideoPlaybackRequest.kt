package tv.hsrui.bolo.view.video

import kotlinx.serialization.Serializable
import tv.hsrui.bolo.model.Vid
import tv.hsrui.network.feature.video.list.VideoListSort
import tv.hsrui.network.feature.video.list.VideoListType

@Serializable
sealed interface VideoPlaybackRequest {
    val key: String

    @Serializable
    data class Single(val vid: Vid) : VideoPlaybackRequest {
        override val key get() = "video:${vid.key}"
    }

    @Serializable
    data class VideoList(
        val type: VideoListType,
        val id: Long,
        val sort: VideoListSort = VideoListSort.Default,
    ) : VideoPlaybackRequest {
        val isValid get() = id > 0 && (type == VideoListType.Uploads || sort == VideoListSort.Default)
        override val key get() = "list:$type:$id:$sort"
    }
}
