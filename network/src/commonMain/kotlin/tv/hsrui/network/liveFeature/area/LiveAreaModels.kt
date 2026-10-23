package tv.hsrui.network.liveFeature.area

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.utils.toHttpsUrl

@Serializable
data class LiveAreaListResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: LiveAreaListData? = null,
) {
    val isSuccess: Boolean
        get() = code == 0 && data?.parentAreas != null

    val parentAreas: List<LiveParentAreaData>
        get() = data?.parentAreas.orEmpty()
}

@Serializable
data class LiveAreaListData(
    @SerialName("data") val parentAreas: List<LiveParentAreaData>? = null,
)

@Serializable
data class LiveParentAreaData(
    val id: Int = 0,
    @SerialName("name") val title: String = "",
    @SerialName("list") val areas: List<LiveAreaData> = emptyList(),
)

@Serializable
data class LiveAreaData(
    @SerialName("id") private val areaId: String = "",
    @SerialName("name") val title: String = "",
    @SerialName("pic") private val icon: String = "",
) {
    val id: Int
        get() = areaId.toIntOrNull() ?: 0

    val iconUrl: String
        get() = icon.toHttpsUrl()
}
