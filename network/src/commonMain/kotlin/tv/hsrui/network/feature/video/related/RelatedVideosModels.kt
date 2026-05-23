package tv.hsrui.network.feature.video.related

import kotlinx.serialization.Serializable
import tv.hsrui.network.model.ValidVideosData
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.model.VideosResult

@Serializable
data class RelatedVideosResponse(
    val code: Int = -1,
    override val message: String = "",
    val data: List<VideoCard> = emptyList()
): VideosResult {
    override val isSuccess get() = (code == 0)
    override val validData: ValidVideosData
        get() = ValidVideosData(data,false)
}
