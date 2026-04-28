package tv.hsrui.network.feature.popular

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.VideoCard
import tv.hsrui.network.model.VideosResult
import tv.hsrui.network.model.ValidVideosData

@Serializable
data class PopularResponse(
    val code: Int = -1,
    override val message: String = "-1",
    val data: PopularData? = null
) : VideosResult {
    override val isSuccess get() = (code == 0 && data!= null)
    override val validData get() = data?.let {
        ValidVideosData(it.list,it.noMore)
    } ?: ValidVideosData(emptyList())
}

@Serializable
data class PopularData(
    val list: List<VideoCard> = emptyList(),
    @SerialName("no_more")
    val noMore: Boolean = true
)