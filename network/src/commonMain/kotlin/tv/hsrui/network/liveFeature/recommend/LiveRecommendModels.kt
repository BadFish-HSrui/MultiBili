package tv.hsrui.network.liveFeature.recommend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.LiveCard
import tv.hsrui.network.model.LiveRoomsResult

@Serializable
data class LiveRecommendResponse(
    override val code: Int = -1,
    override val message: String = "",
    private val data: LiveRecommendData? = null,
) : LiveRoomsResult {
    override val isSuccess: Boolean
        get() = code == 0 && data?.rooms != null

    override val rooms: List<LiveCard>
        get() = data?.rooms.orEmpty()

    override val hasMore: Boolean
        get() = rooms.isNotEmpty()
}

@Serializable
data class LiveRecommendData(
    @SerialName("list") val rooms: List<LiveCard>? = null,
)
