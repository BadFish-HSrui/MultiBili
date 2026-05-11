package tv.hsrui.network.feature.region

import kotlinx.serialization.Serializable
import tv.hsrui.network.model.ValidVideosData
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.model.VideosResult

@Serializable
data class RegionResponse(
    private val code: Int = -1,
    override val message: String = "-1",
    private val data: RegionData = RegionData()
) : VideosResult {
    override val isSuccess: Boolean get() = (code == 0)
    override val validData: ValidVideosData
        get() = ValidVideosData(videosList = data.archives, canLoadMore = true)
}

@Serializable
data class RegionData(
    val archives: List<VideoCard> = emptyList()
)