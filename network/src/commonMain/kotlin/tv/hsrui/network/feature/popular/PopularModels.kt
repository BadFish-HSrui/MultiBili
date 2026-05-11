package tv.hsrui.network.feature.popular

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.model.VideosResult
import tv.hsrui.network.model.ValidVideosData

@Serializable
data class PopularResponse(
    private val code: Int = -1,
    override val message: String = "-1",
    private val data: PopularData = PopularData()
) : VideosResult {
    override val isSuccess get() = (code == 0)
    override val validData
        get() = ValidVideosData(data.list, !data.noMore)
}

@Serializable
data class PopularData(
    val list: List<VideoCard> = emptyList(),
    @SerialName("no_more")
    val noMore: Boolean = true
)