package tv.hsrui.network.liveFeature.area

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tv.hsrui.network.model.LiveCard
import tv.hsrui.network.model.LiveRoomsResult
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
    @SerialName("parent_name") val parentTitle: String = "",
) {
    val id: Int
        get() = areaId.toIntOrNull() ?: 0

    val iconUrl: String
        get() = icon.toHttpsUrl()
}

@Serializable
data class LiveSubAreaListResponse(
    val code: Int = -1,
    val message: String = "",
    private val data: List<LiveAreaData>? = null,
) {
    val isSuccess: Boolean
        get() = code == 0 && data != null

    val areas: List<LiveAreaData>
        get() = data.orEmpty()
}

@Serializable
data class LiveAreaRoomsResponse(
    override val code: Int = -1,
    override val message: String = "",
    private val data: LiveAreaRoomsData? = null,
) : LiveRoomsResult {
    override val isSuccess: Boolean
        get() = code == 0 && data?.rooms != null

    override val rooms: List<LiveCard>
        get() = data?.rooms.orEmpty()

    override val hasMore: Boolean
        get() = data?.hasMore == true
}

@Serializable
data class LiveAreaRoomsData(
    @SerialName("list") val rooms: List<LiveCard>? = null,
    @SerialName("has_more") private val hasMoreFlag: Int = 0,
) {
    val hasMore: Boolean
        get() = hasMoreFlag == 1
}
