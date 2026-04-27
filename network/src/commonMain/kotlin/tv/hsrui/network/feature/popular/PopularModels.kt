package tv.hsrui.network.feature.popular

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.VideoCard

@Serializable
data class PopularResponse(
    val code: Int = -1,
    val message: String = "-1",
    val data: PopularData? = null
)

@Serializable
data class PopularData(
    val list: List<VideoCard> = emptyList(),
    @SerialName("no_more")
    val noMore: Boolean = true
)

