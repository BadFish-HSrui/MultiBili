package tv.hsrui.network.feature.watchLater

import kotlinx.serialization.Serializable
import tv.hsrui.network.model.ValidVideosData
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.model.VideosResult

@Serializable
data class WatchLaterResponse(
    val code: Int = -1,
    override val message: String = "",
    private val data: WatchLaterData = WatchLaterData()
) : VideosResult {
    override val isSuccess: Boolean get() = (code == 0)
    override val validData: ValidVideosData
        get() = ValidVideosData(data.list, false)

    @Serializable
    data class WatchLaterData(
        val count: Int = 0,
        val list: List<VideoCard> = emptyList()
    )
}