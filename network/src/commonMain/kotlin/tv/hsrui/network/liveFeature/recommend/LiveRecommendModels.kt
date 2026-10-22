package tv.hsrui.network.liveFeature.recommend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.LiveCard

@Serializable
data class LiveRecommendResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: LiveRecommendData? = null,
) {
    val isSuccess: Boolean
        get() = code == 0 && data?.rooms != null

    val rooms: List<LiveCard>
        get() = data?.rooms.orEmpty()
}

@Serializable
data class LiveRecommendData(
    @SerialName("list") val rooms: List<LiveCard>? = null,
)
