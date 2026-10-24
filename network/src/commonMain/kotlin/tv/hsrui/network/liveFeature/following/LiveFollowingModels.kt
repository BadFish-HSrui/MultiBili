package tv.hsrui.network.liveFeature.following

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import tv.hsrui.network.model.LiveCard
import tv.hsrui.network.model.LiveRoomsResult

@Serializable
data class LiveFollowingResponse(
    override val code: Int = -1,
    override val message: String = "",
    private val data: LiveFollowingData? = null,
    @Transient val requestPage: Int = 1,
) : LiveRoomsResult {
    override val isSuccess: Boolean
        get() = code == 0 && data?.rooms != null

    override val hasMore: Boolean
        get() = requestPage < (data?.totalPage ?: 0)

    override val rooms: List<LiveCard>
        get() = data?.rooms.orEmpty().map { room ->
            LiveCard(
                roomId = room.roomId,
                title = room.title,
                userCover = room.cover,
                upMid = room.uid,
                upName = room.name,
                avatar = room.face,
                areaId = room.areaId,
                areaName = room.areaName,
                parentAreaId = room.parentAreaId,
                liveStatus = room.liveStatus,
                recordLiveTime = room.recordLiveTime,
            )
        }
}

@Serializable
data class LiveFollowingData(
    val totalPage: Int = 0,
    @SerialName("list") val rooms: List<LiveFollowingRoom>? = null,
)

@Serializable
data class LiveFollowingRoom(
    @SerialName("roomid") val roomId: Long = 0,
    val title: String = "",
    @SerialName("room_cover") val cover: String = "",
    val uid: Long = 0,
    @SerialName("uname") val name: String = "",
    val face: String = "",
    @SerialName("area_id") val areaId: Int = 0,
    @SerialName("area_name_v2") val areaName: String = "",
    @SerialName("parent_area_id") val parentAreaId: Int = 0,
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("record_live_time") val recordLiveTime: Long = 0,
)